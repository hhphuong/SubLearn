<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { videoApi } from '@/api/video.api'
import { jobApi } from '@/api/job.api'
import { useSubtitlesStore } from '@/stores/subtitles.store'
import { useWatchSessionStore } from '@/stores/watchSession.store'
import { useSubtitleSync } from '@/composables/useSubtitleSync'
import YoutubePlayer from '@/components/watch/YoutubePlayer.vue'
import SubtitleLine from '@/components/watch/SubtitleLine.vue'
import WordPopup from '@/components/watch/WordPopup.vue'
import AppModal from '@/components/common/AppModal.vue'
import { toApiError } from '@/api/errors'
import { DEFAULT_TARGET_LANGUAGE_CODE, TARGET_LANGUAGES, languageLabel } from '@/utils/languages'
import type { ProcessingJobResponse, SubtitleLineDto, SubtitleTokenDto } from '@/types/api'

const props = defineProps<{ videoId: string }>()
const route = useRoute()
const router = useRouter()
const subtitlesStore = useSubtitlesStore()
const watchSessionStore = useWatchSessionStore()

const numericVideoId = computed(() => Number(props.videoId))
const targetLanguage = computed(() => (typeof route.query.lang === 'string' ? route.query.lang : DEFAULT_TARGET_LANGUAGE_CODE))

const youtubeVideoId = ref<string | null>(null)
const title = ref<string | null>(null)
const loadError = ref<string | null>(null)
const isLoading = ref(true)

const currentTimeMs = ref(0)
const resumePositionMs = ref(0)
const linesRef = computed(() => subtitlesStore.lines)
const { currentLine } = useSubtitleSync(currentTimeMs, linesRef, () => subtitlesStore.fetchNextPage())

const playerRef = ref<InstanceType<typeof YoutubePlayer> | null>(null)
const activeToken = ref<SubtitleTokenDto | null>(null)
const activeTokenLine = ref<SubtitleLineDto | null>(null)

const PROGRESS_SAVE_INTERVAL_MS = 5000
let progressSaveTimer: ReturnType<typeof setTimeout> | null = null
let lastPersistedPositionMs = 0

function onTimeUpdate(ms: number) {
  currentTimeMs.value = ms
  watchSessionStore.updatePlaybackTime(ms)
  if (Math.abs(ms - lastPersistedPositionMs) < 1000 || progressSaveTimer) return
  progressSaveTimer = setTimeout(() => {
    progressSaveTimer = null
    void persistProgress()
  }, PROGRESS_SAVE_INTERVAL_MS)
}

// Fires from the real YouTube iframe's own ready signal (see YoutubePlayer's `ready` emit) -
// more reliable than guessing with a nextTick after mount, since it fires exactly when a seek
// is actually safe to issue rather than merely "the component's been created."
function onPlayerReady() {
  if (resumePositionMs.value > 0) {
    playerRef.value?.seekTo(resumePositionMs.value)
  }
}

async function persistProgress() {
  const positionMs = currentTimeMs.value
  if (Math.abs(positionMs - lastPersistedPositionMs) < 1000) return
  const previousPosition = lastPersistedPositionMs
  lastPersistedPositionMs = positionMs
  try {
    await videoApi.updateProgress(numericVideoId.value, positionMs)
  } catch {
    lastPersistedPositionMs = previousPosition
  }
}

function onTokenClick(line: SubtitleLineDto, token: SubtitleTokenDto) {
  playerRef.value?.pause()
  activeToken.value = token
  activeTokenLine.value = line
}

function closePopup() {
  activeToken.value = null
  activeTokenLine.value = null
}

// --- Modal Dialog state (replaces native alert/confirm and inline alert banners) ---
const modalState = ref<{
  show: boolean
  title: string
  message: string
  type: 'confirm' | 'alert'
  variant: 'warning' | 'danger' | 'info'
  confirmText?: string
  cancelText?: string
  onConfirm?: () => void | Promise<void>
}>({
  show: false,
  title: 'Cảnh báo',
  message: '',
  type: 'alert',
  variant: 'warning',
})

function showAlert(message: string, title = 'Cảnh báo', variant: 'warning' | 'danger' | 'info' = 'warning') {
  modalState.value = {
    show: true,
    title,
    message,
    type: 'alert',
    variant,
    confirmText: 'Đóng',
  }
}

function showConfirm(
  message: string,
  onConfirm: () => void | Promise<void>,
  title = 'Cảnh báo',
  variant: 'warning' | 'danger' = 'warning',
) {
  modalState.value = {
    show: true,
    title,
    message,
    type: 'confirm',
    variant,
    confirmText: 'Xác nhận',
    cancelText: 'Hủy',
    onConfirm,
  }
}

function closeModal() {
  modalState.value.show = false
}

async function handleModalConfirm() {
  const cb = modalState.value.onConfirm
  modalState.value.show = false
  if (cb) {
    await cb()
  }
}

// --- Translation display & async translation/reprocess job tracking ---
const showTranslation = ref(false)
const translationJob = ref<ProcessingJobResponse | null>(null)
const isSubmittingTranslationJob = ref(false)
let translationJobPollTimer: ReturnType<typeof setTimeout> | null = null

const isTranslationJobActive = computed(
  () => translationJob.value?.status === 'PENDING' || translationJob.value?.status === 'PROCESSING',
)

function stopTranslationJobPolling() {
  if (translationJobPollTimer) {
    clearTimeout(translationJobPollTimer)
    translationJobPollTimer = null
  }
}

function startTranslationJobPolling(job: ProcessingJobResponse) {
  stopTranslationJobPolling()
  translationJob.value = job
  if (job.status !== 'PENDING' && job.status !== 'PROCESSING') return

  const poll = async () => {
    try {
      const latest = await jobApi.getJob(job.jobId)
      translationJob.value = latest
      if (latest.status === 'COMPLETED') {
        stopTranslationJobPolling()
        await subtitlesStore.init(numericVideoId.value, targetLanguage.value)
        showTranslation.value = true
        translationJob.value = null
        return
      }
      if (latest.status === 'FAILED') {
        stopTranslationJobPolling()
        showAlert(`Có lỗi khi dịch phụ đề sang ${languageLabel(targetLanguage.value)}! Vui lòng thử lại.`, 'Cảnh báo', 'danger')
        return
      }
      translationJobPollTimer = setTimeout(poll, 1500)
    } catch {
      stopTranslationJobPolling()
    }
  }

  translationJobPollTimer = setTimeout(poll, 1500)
}

function syncActiveJobFromSubtitles() {
  stopTranslationJobPolling()
  const active = subtitlesStore.activeTranslationJob
  translationJob.value = active
  if (active && (active.status === 'PENDING' || active.status === 'PROCESSING')) {
    startTranslationJobPolling(active)
  }
}

async function onRequestTranslation() {
  isSubmittingTranslationJob.value = true
  try {
    const job = await videoApi.requestTranslation(numericVideoId.value, targetLanguage.value)
    startTranslationJobPolling(job)
  } catch (error) {
    showAlert(toApiError(error).message, 'Cảnh báo lỗi', 'danger')
  } finally {
    isSubmittingTranslationJob.value = false
  }
}

async function onRetryTranslationJob() {
  if (!translationJob.value) return
  isSubmittingTranslationJob.value = true
  try {
    const job = await jobApi.retryJob(translationJob.value.jobId)
    startTranslationJobPolling(job)
  } catch (error) {
    showAlert(toApiError(error).message, 'Cảnh báo lỗi', 'danger')
  } finally {
    isSubmittingTranslationJob.value = false
  }
}

async function onTargetLanguageChange(event: Event) {
  const newLang = (event.target as HTMLSelectElement).value
  if (newLang === targetLanguage.value) return
  stopTranslationJobPolling()
  closeModal()
  await router.replace({ query: { ...route.query, lang: newLang } })
  watchSessionStore.setActive(numericVideoId.value, title.value, newLang)
  await subtitlesStore.init(numericVideoId.value, newLang)
  syncActiveJobFromSubtitles()
}

// --- Hold (long-press) a line to reveal a small repeat icon in its top-right corner;
// hold the icon itself to toggle looping that line until turned off. A short/plain
// click on a line still just seeks to it. ---
const HOLD_THRESHOLD_MS = 450
const revealedIconLineId = ref<number | null>(null)
const repeatingLineId = ref<number | null>(null)
const repeatLabel = ref<{ lineId: number; text: string } | null>(null)

let linePressTimer: ReturnType<typeof setTimeout> | null = null
let linePressWasHold = false
let linePressLineId: number | null = null
let iconPressTimer: ReturnType<typeof setTimeout> | null = null
let repeatLabelTimeout: ReturnType<typeof setTimeout> | null = null

function onLinePointerDown(line: SubtitleLineDto) {
  linePressLineId = line.id
  linePressWasHold = false
  if (linePressTimer) clearTimeout(linePressTimer)
  linePressTimer = setTimeout(() => {
    linePressWasHold = true
    revealedIconLineId.value = line.id
  }, HOLD_THRESHOLD_MS)
}

function onLinePointerRelease() {
  if (linePressTimer) clearTimeout(linePressTimer)
  linePressTimer = null
}

function onLineClick(line: SubtitleLineDto) {
  if (linePressWasHold && linePressLineId === line.id) {
    // this click is just the tail end of the hold gesture that revealed the icon -
    // don't also hide the icon it just revealed or seek the video.
    return
  }
  revealedIconLineId.value = null
  playerRef.value?.seekTo(line.startMs)
}

function onIconPointerDown(line: SubtitleLineDto) {
  if (iconPressTimer) clearTimeout(iconPressTimer)
  iconPressTimer = setTimeout(() => {
    toggleRepeat(line)
  }, HOLD_THRESHOLD_MS)
}

function onIconPointerRelease() {
  if (iconPressTimer) clearTimeout(iconPressTimer)
  iconPressTimer = null
}

function toggleRepeat(line: SubtitleLineDto) {
  const turningOn = repeatingLineId.value !== line.id
  repeatingLineId.value = turningOn ? line.id : null
  repeatLabel.value = { lineId: line.id, text: turningOn ? 'Lặp lại' : 'Dừng lặp lại' }
  if (repeatLabelTimeout) clearTimeout(repeatLabelTimeout)
  repeatLabelTimeout = setTimeout(() => {
    repeatLabel.value = null
  }, 1500)
}

watch(currentTimeMs, (ms) => {
  const id = repeatingLineId.value
  if (id === null) return
  const line = subtitlesStore.lines.find((l) => l.id === id)
  if (!line) return
  if (ms < line.startMs || ms >= line.endMs) {
    playerRef.value?.seekTo(line.startMs)
  }
})

// --- Transcript panel: keep every line visible, auto-scroll the active one into view,
// but back off while the user is deliberately scrolling around to read context. ---
const transcriptEl = ref<HTMLElement | null>(null)
const lineEls = new Map<number, HTMLElement>()
const userIsScrolling = ref(false)
let userScrollTimeout: ReturnType<typeof setTimeout> | null = null
let programmaticScroll = false

function setLineEl(id: number, el: unknown) {
  if (el instanceof HTMLElement) {
    lineEls.set(id, el)
  } else {
    lineEls.delete(id)
  }
}

function onTranscriptScroll() {
  if (programmaticScroll) return
  userIsScrolling.value = true
  if (userScrollTimeout) clearTimeout(userScrollTimeout)
  userScrollTimeout = setTimeout(() => {
    userIsScrolling.value = false
  }, 4000)

  const el = transcriptEl.value
  if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 400) {
    subtitlesStore.fetchNextPage()
  }
}

function scrollActiveLineIntoView() {
  const line = currentLine.value
  if (!line) return
  const el = lineEls.get(line.id)
  if (!el) return
  programmaticScroll = true
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  window.setTimeout(() => {
    programmaticScroll = false
  }, 600)
}

function resumeAutoScroll() {
  userIsScrolling.value = false
  scrollActiveLineIntoView()
}

watch(currentLine, () => {
  if (userIsScrolling.value) return
  void nextTick(scrollActiveLineIntoView)
})

// --- Download menu: builds an .srt file client-side from whatever's in the store,
// loading any not-yet-fetched pages first so the download always covers the whole video. ---
const isDownloadMenuOpen = ref(false)
const isPreparingDownload = ref(false)

function formatSrtTimestamp(ms: number): string {
  const totalMs = Math.max(0, Math.round(ms))
  const hours = Math.floor(totalMs / 3_600_000)
  const minutes = Math.floor((totalMs % 3_600_000) / 60_000)
  const seconds = Math.floor((totalMs % 60_000) / 1000)
  const millis = totalMs % 1000
  const pad = (n: number, len = 2) => n.toString().padStart(len, '0')
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)},${pad(millis, 3)}`
}

function buildSrt(mode: 'original' | 'translation' | 'bilingual'): string {
  return subtitlesStore.lines
    .map((line, index) => {
      const text =
        mode === 'original' ? line.sourceText : mode === 'translation' ? line.targetText : `${line.sourceText}\n${line.targetText}`
      return `${index + 1}\n${formatSrtTimestamp(line.startMs)} --> ${formatSrtTimestamp(line.endMs)}\n${text}\n`
    })
    .join('\n')
}

function triggerFileDownload(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

async function onDownload(mode: 'original' | 'translation' | 'bilingual') {
  isDownloadMenuOpen.value = false
  isPreparingDownload.value = true
  try {
    await subtitlesStore.loadAllRemaining()
    const suffix = mode === 'original' ? 'goc' : mode === 'translation' ? 'ban-dich' : 'song-ngu'
    const baseName = (title.value ?? 'subtitle').replace(/[\\/:*?"<>|]/g, '').trim() || 'subtitle'
    triggerFileDownload(buildSrt(mode), `${baseName}-${suffix}.srt`)
  } finally {
    isPreparingDownload.value = false
  }
}

// --- Reprocess: re-translates only the currently selected target language asynchronously
// via RabbitMQ without re-fetching subtitles from YouTube. Keeps the user right here on
// WatchView and refreshes translations automatically once COMPLETED. ---
const isReprocessing = ref(false)

function onReprocess() {
  if (subtitlesStore.sameLanguage || !subtitlesStore.translationAvailable) return
  const langName = languageLabel(targetLanguage.value)
  showConfirm(
    `Dịch lại toàn bộ bản dịch sang ${langName} cho video này?\nQuá trình dịch có thể mất một vài phút.`,
    async () => {
      isReprocessing.value = true
      try {
        const job = await videoApi.reprocess(numericVideoId.value, targetLanguage.value)
        startTranslationJobPolling(job)
      } catch (error) {
        showAlert(toApiError(error).message, 'Cảnh báo lỗi', 'danger')
      } finally {
        isReprocessing.value = false
      }
    },
    'Xác nhận dịch lại',
    'warning',
  )
}

function onDocumentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (isDownloadMenuOpen.value && target && !target.closest('.download-menu')) {
    isDownloadMenuOpen.value = false
  }
  if (revealedIconLineId.value !== null && target && !target.closest('.transcript-line')) {
    revealedIconLineId.value = null
  }
}

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  try {
    const summary = await videoApi.getSummary(numericVideoId.value)
    if (summary.status !== 'READY' || !summary.youtubeVideoId) {
      router.replace({ name: 'home' })
      return
    }
    youtubeVideoId.value = summary.youtubeVideoId
    title.value = summary.title
    // Fire-and-forget: recently-watched history is a nice-to-have, never worth failing or
    // delaying the actual watch experience over.
    void videoApi.recordWatch(numericVideoId.value).catch(() => {})
    watchSessionStore.setActive(numericVideoId.value, summary.title, targetLanguage.value)

    const durationMs = summary.durationSeconds ? summary.durationSeconds * 1000 : 0
    const isNearEnd = durationMs > 0 && summary.lastPositionMs >= durationMs - 5000
    resumePositionMs.value = isNearEnd ? 0 : summary.lastPositionMs
    lastPersistedPositionMs = resumePositionMs.value
    await subtitlesStore.init(numericVideoId.value, targetLanguage.value)
    syncActiveJobFromSubtitles()

    // Must flip this before onPlayerReady can ever fire: <YoutubePlayer> is gated behind
    // `v-else-if="youtubeVideoId"`, which never renders while `isLoading` is still true (the
    // `v-if="isLoading"` branch wins first) - so the real YouTube iframe (and the `ready`
    // event onPlayerReady listens for to issue the resume seek) would never even get created.
    isLoading.value = false
  } catch (error) {
    loadError.value = toApiError(error).message
  } finally {
    isLoading.value = false
  }
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  if (userScrollTimeout) clearTimeout(userScrollTimeout)
  if (linePressTimer) clearTimeout(linePressTimer)
  if (iconPressTimer) clearTimeout(iconPressTimer)
  if (repeatLabelTimeout) clearTimeout(repeatLabelTimeout)
  if (progressSaveTimer) clearTimeout(progressSaveTimer)
  stopTranslationJobPolling()
  if (Math.abs(currentTimeMs.value - lastPersistedPositionMs) >= 1000) {
    void videoApi.updateProgress(numericVideoId.value, currentTimeMs.value).catch(() => undefined)
  }
  subtitlesStore.reset()
})
</script>

<template>
  <div v-if="isLoading" class="text-center py-5">
    <div class="spinner-border text-primary" role="status"></div>
  </div>

  <div v-else-if="loadError" class="alert alert-danger">
    {{ loadError }}
    <RouterLink to="/">Về trang chủ</RouterLink>
  </div>

  <div v-else-if="youtubeVideoId">
    <h1 class="watch-title">{{ title }}</h1>

    <div class="watch-stage">
      <div class="video-col">
        <div class="player-frame">
          <YoutubePlayer ref="playerRef" :video-id="youtubeVideoId" @ready="onPlayerReady" @timeupdate="onTimeUpdate" />
        </div>
      </div>

      <div class="transcript-col">
        <div class="transcript-panel">
          <div class="transcript-toolbar">
            <div class="transcript-toolbar__row">
              <div class="transcript-toolbar__controls">
                <button
                  class="btn-toggle-translation"
                  :class="{ 'btn-toggle-translation--active': showTranslation }"
                  type="button"
                  :disabled="subtitlesStore.sameLanguage || !subtitlesStore.translationAvailable"
                  @click="showTranslation = !showTranslation"
                >
                  <span class="icon-bilingual" aria-hidden="true">文A</span>
                  <span>Bản dịch</span>
                </button>

                <select
                  :value="targetLanguage"
                  class="form-select-lang"
                  aria-label="Chọn ngôn ngữ dịch"
                  @change="onTargetLanguageChange"
                >
                  <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">
                    {{ lang.label }}
                  </option>
                </select>
              </div>

              <div class="download-menu">
                <button
                  class="btn-download-primary"
                  type="button"
                  :disabled="isPreparingDownload"
                  @click="isDownloadMenuOpen = !isDownloadMenuOpen"
                >
                  <svg class="btn-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 3v13m0 0l-4-4m4 4l4-4M4 20h16"/>
                  </svg>
                  <span>{{ isPreparingDownload ? 'Đang chuẩn bị...' : 'Tải xuống' }}</span>
                </button>
                <div v-if="isDownloadMenuOpen" class="download-menu__list">
                  <button class="download-menu__item" type="button" @click="onDownload('original')">Tải phụ đề</button>
                  <button
                    class="download-menu__item"
                    type="button"
                    :disabled="subtitlesStore.sameLanguage || !subtitlesStore.translationAvailable"
                    @click="onDownload('translation')"
                  >
                    Tải bản dịch
                  </button>
                  <button
                    class="download-menu__item"
                    type="button"
                    :disabled="subtitlesStore.sameLanguage || !subtitlesStore.translationAvailable"
                    @click="onDownload('bilingual')"
                  >
                    Tải phụ đề song ngữ
                  </button>
                </div>
              </div>
            </div>

            <div class="transcript-toolbar__row transcript-toolbar__row--secondary">
              <button
                class="btn-ghost-action"
                type="button"
                :disabled="isReprocessing || isTranslationJobActive || subtitlesStore.sameLanguage || !subtitlesStore.translationAvailable"
                title="Dịch lại bản dịch cho ngôn ngữ đang chọn (không tải lại phụ đề gốc)"
                @click="onReprocess"
              >
                <svg class="btn-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
                  <path d="M21 3v5h-5"/>
                  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
                  <path d="M3 21v-5h5"/>
                </svg>
                <span>{{ isReprocessing || isTranslationJobActive ? 'Đang dịch...' : 'Dịch lại phụ đề' }}</span>
              </button>
            </div>
          </div>

          <div v-if="isTranslationJobActive && translationJob" class="translation-banner translation-banner--progress">
            <div class="d-flex justify-content-between align-items-center small fw-semibold mb-1">
              <span>
                Đang dịch phụ đề sang {{ languageLabel(targetLanguage) }}...
                <template v-if="translationJob.retryCount > 0">
                  (thử lại {{ translationJob.retryCount }}/{{ translationJob.maxRetries }})
                </template>
              </span>
              <span>{{ translationJob.progressPercent }}%</span>
            </div>
            <div class="translation-progress-track">
              <div class="translation-progress-fill" :style="{ width: `${translationJob.progressPercent}%` }"></div>
            </div>
          </div>

          <div
            v-else-if="!subtitlesStore.sameLanguage && translationJob?.status === 'FAILED'"
            class="translation-banner translation-banner--error"
          >
            <span>Có lỗi khi dịch phụ đề sang {{ languageLabel(targetLanguage) }}! Vui lòng thử lại.</span>
            <button
              class="btn btn-sm btn-outline-danger text-nowrap"
              type="button"
              :disabled="isSubmittingTranslationJob"
              @click="onRetryTranslationJob"
            >
              {{ isSubmittingTranslationJob ? 'Đang gửi...' : '⟳ Thử dịch lại' }}
            </button>
          </div>

          <div
            v-else-if="!subtitlesStore.sameLanguage && !subtitlesStore.translationAvailable"
            class="translation-banner translation-banner--missing"
          >
            <span>Chưa có bản dịch cho <strong>{{ languageLabel(targetLanguage) }}</strong>.</span>
            <button
              class="btn btn-sm btn-primary text-nowrap"
              type="button"
              :disabled="isSubmittingTranslationJob"
              @click="onRequestTranslation"
            >
              {{ isSubmittingTranslationJob ? 'Đang gửi...' : `Dịch sang ${languageLabel(targetLanguage)}` }}
            </button>
          </div>

          <div ref="transcriptEl" class="transcript-scroll" @scroll="onTranscriptScroll">
            <button
              v-if="userIsScrolling"
              class="btn btn-primary btn-sm resume-scroll-btn"
              type="button"
              @click="resumeAutoScroll"
            >
              ↓ Về vị trí đang phát
            </button>

            <div v-if="subtitlesStore.lines.length === 0" class="text-muted small py-3 text-center">
              Đang chờ phụ đề...
            </div>
            <div
              v-for="line in subtitlesStore.lines"
              :key="line.id"
              :ref="(el) => setLineEl(line.id, el)"
              class="transcript-line"
              :class="{
                'transcript-line--active': currentLine?.id === line.id,
                'transcript-line--held': revealedIconLineId === line.id,
                'transcript-line--repeating': repeatingLineId === line.id,
              }"
              @pointerdown="onLinePointerDown(line)"
              @pointerup="onLinePointerRelease"
              @pointerleave="onLinePointerRelease"
              @click="onLineClick(line)"
            >
              <button
                v-if="revealedIconLineId === line.id"
                type="button"
                class="repeat-icon"
                :class="{ 'repeat-icon--active': repeatingLineId === line.id }"
                :title="repeatingLineId === line.id ? 'Đang lặp lại - giữ để dừng' : 'Giữ để lặp lại câu này'"
                @pointerdown.stop="onIconPointerDown(line)"
                @pointerup.stop="onIconPointerRelease"
                @pointerleave.stop="onIconPointerRelease"
                @click.stop
              >
                🔁
              </button>
              <div v-if="repeatLabel?.lineId === line.id" class="repeat-label">{{ repeatLabel.text }}</div>

              <SubtitleLine :line="line" :show-translation="showTranslation" @token-click="(token) => onTokenClick(line, token)" />
            </div>
            <div v-if="subtitlesStore.loadingPage !== null" class="text-center text-muted small py-2">
              Đang tải thêm phụ đề...
            </div>
          </div>
        </div>
      </div>
    </div>

    <WordPopup
      v-if="activeToken && activeTokenLine"
      :word="activeToken.surfaceText"
      :sentence-context="activeTokenLine.sourceText"
      :source-language="subtitlesStore.sourceLanguage ?? ''"
      :target-language="targetLanguage"
      :video-id="numericVideoId"
      :subtitle-line-id="activeTokenLine.id"
      @close="closePopup"
    />

    <AppModal
      :show="modalState.show"
      :title="modalState.title"
      :message="modalState.message"
      :type="modalState.type"
      :variant="modalState.variant"
      :confirm-text="modalState.confirmText"
      :cancel-text="modalState.cancelText"
      :is-processing="isReprocessing || isSubmittingTranslationJob"
      @confirm="handleModalConfirm"
      @cancel="closeModal"
      @close="closeModal"
    />
  </div>
</template>

<style scoped>
.watch-title {
  font-size: 1.4rem;
  font-weight: 800;
  margin-bottom: 1.25rem;
}

.watch-stage {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: 1.5rem;
  align-items: start;
}

.player-frame {
  border-radius: var(--brand-radius-md);
  overflow: hidden;
  box-shadow: var(--brand-shadow);
}

.transcript-panel {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  box-shadow: var(--brand-shadow);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.transcript-toolbar {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem 0.85rem;
  border-bottom: 1px solid var(--brand-border);
}

.transcript-toolbar__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.transcript-toolbar__row--secondary {
  justify-content: flex-start;
}

.transcript-toolbar__controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.btn-toggle-translation {
  height: 36px;
  padding: 0 0.75rem;
  border-radius: 8px;
  border: 1px solid #dcdfe4;
  background: #ffffff;
  color: #4a5568;
  font-size: 0.875rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.btn-toggle-translation:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.btn-toggle-translation--active {
  background: var(--brand-green-light);
  border: 1px solid var(--brand-green);
  color: var(--brand-green-dark);
}

.btn-toggle-translation--active:hover:not(:disabled) {
  background: #dbf4e7;
  border-color: var(--brand-green);
}

.btn-toggle-translation:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.icon-bilingual {
  font-size: 0.75rem;
  font-weight: 700;
  background: rgba(31, 178, 122, 0.18);
  color: var(--brand-green-dark);
  padding: 2px 5px;
  border-radius: 4px;
  line-height: 1;
  letter-spacing: -0.5px;
}

.form-select-lang {
  height: 36px;
  border-radius: 8px;
  border: 1px solid #dcdfe4;
  background-color: #ffffff;
  color: #2d3748;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0 2rem 0 0.75rem;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3e%3cpath fill='none' stroke='%234a5568' stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='m2 5 6 6 6-6'/%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 0.65rem center;
  background-size: 11px 9px;
  appearance: none;
  -webkit-appearance: none;
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.form-select-lang:focus {
  border-color: var(--brand-green);
  box-shadow: 0 0 0 2px rgba(31, 178, 122, 0.18);
}

.btn-download-primary {
  height: 36px;
  padding: 0 0.85rem;
  border-radius: 8px;
  background: var(--brand-green);
  border: 1px solid var(--brand-green);
  color: #ffffff;
  font-size: 0.875rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
  user-select: none;
}

.btn-download-primary:hover:not(:disabled) {
  background: var(--brand-green-dark);
  border-color: var(--brand-green-dark);
}

.btn-download-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-ghost-action {
  height: 34px;
  padding: 0 0.65rem;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #dcdfe4;
  color: #4a5568;
  font-size: 0.825rem;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.btn-ghost-action:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #1f2937;
}

.btn-ghost-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}

.language-select {
  width: auto;
}

.translation-banner {
  margin: 0.5rem 0.75rem 0;
  padding: 0.6rem 0.75rem;
  border-radius: 8px;
  font-size: 0.84rem;
}

.translation-banner .btn {
  border-radius: 8px;
}

.translation-banner--progress {
  background: var(--brand-green-light);
  border: 1px solid var(--brand-green);
  color: var(--brand-green-dark);
}

.translation-banner--missing {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  flex-wrap: wrap;
  background: #f8fafc;
  border: 1px solid var(--brand-border);
}

.translation-banner--error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  flex-wrap: wrap;
  background: #fff5f5;
  border: 1px solid #f5b5b5;
  color: #9b1c1c;
}

.translation-progress-track {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: rgba(31, 178, 122, 0.2);
  overflow: hidden;
}

.translation-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--brand-green);
  transition: width 0.3s ease;
}

.download-menu {
  position: relative;
}

.download-menu__list {
  position: absolute;
  right: 0;
  top: calc(100% + 0.25rem);
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(31, 45, 61, 0.12);
  z-index: 10;
  min-width: 180px;
  padding: 4px;
  overflow: hidden;
}

.download-menu__item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.5rem 0.85rem;
  background: none;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  color: #2d3748;
  cursor: pointer;
}

.download-menu__item:hover:not(:disabled) {
  background: var(--brand-bg);
  color: var(--brand-green-dark);
}

.download-menu__item:disabled {
  color: var(--brand-muted);
  cursor: not-allowed;
}

.transcript-scroll {
  position: relative;
  height: min(70vh, 560px);
  overflow-y: auto;
  padding: 0.5rem;
  width: 100%;
}

.transcript-line {
  position: relative;
  padding: 0.65rem 0.85rem;
  border-radius: var(--brand-radius-sm);
  cursor: pointer;
  transition: background-color 0.15s;
  user-select: none;
}

.transcript-line:hover {
  background: var(--brand-bg);
}

.transcript-line--active {
  background: var(--brand-green-light);
}

.transcript-line--held {
  background: var(--brand-border);
  box-shadow: inset 0 0 0 1px var(--brand-green);
}

.transcript-line--repeating {
  box-shadow: inset 0 0 0 2px var(--brand-green);
}

.repeat-icon {
  position: absolute;
  top: -10px;
  right: -8px;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid var(--brand-border);
  background: #fff;
  box-shadow: var(--brand-shadow);
  font-size: 1rem;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 6;
}

.repeat-icon--active {
  background: var(--brand-green);
  border-color: var(--brand-green);
}

.repeat-label {
  position: absolute;
  top: -34px;
  right: -8px;
  background: #1f2d3d;
  color: #fff;
  font-size: 0.72rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  white-space: nowrap;
  z-index: 6;
}

.resume-scroll-btn {
  position: sticky;
  top: 0.5rem;
  z-index: 5;
  margin: 0 auto 0.5rem;
  display: block;
  border-radius: 8px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(31, 178, 122, 0.3);
}

@media (max-width: 900px) {
  .watch-stage {
    grid-template-columns: 1fr;
  }
}
</style>
