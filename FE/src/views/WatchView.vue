<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { videoApi } from '@/api/video.api'
import { useSubtitlesStore } from '@/stores/subtitles.store'
import { useVideoStore } from '@/stores/video.store'
import { useWatchSessionStore } from '@/stores/watchSession.store'
import { useSubtitleSync } from '@/composables/useSubtitleSync'
import YoutubePlayer from '@/components/watch/YoutubePlayer.vue'
import SubtitleLine from '@/components/watch/SubtitleLine.vue'
import WordPopup from '@/components/watch/WordPopup.vue'
import { toApiError } from '@/api/errors'
import { DEFAULT_TARGET_LANGUAGE_CODE, TARGET_LANGUAGES } from '@/utils/languages'
import type { SubtitleLineDto, SubtitleTokenDto } from '@/types/api'

const props = defineProps<{ videoId: string }>()
const route = useRoute()
const router = useRouter()
const subtitlesStore = useSubtitlesStore()
const videoStore = useVideoStore()
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

// --- Translation display: hidden by default, toggled on demand. ---
const showTranslation = ref(false)

async function onTargetLanguageChange(event: Event) {
  const newLang = (event.target as HTMLSelectElement).value
  if (newLang === targetLanguage.value) return
  await router.replace({ query: { ...route.query, lang: newLang } })
  watchSessionStore.setActive(numericVideoId.value, title.value, newLang)
  await subtitlesStore.init(numericVideoId.value, newLang)
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

// --- Reprocess: re-runs subtitle ingestion for this video so it picks up parser/chunking
// fixes shipped after it was originally ingested (ingestion only ever runs once otherwise).
// Reuses videoStore's existing polling + HomeView's redirect-on-READY, same as a fresh submit. ---
const isReprocessing = ref(false)
const reprocessError = ref<string | null>(null)

async function onReprocess() {
  if (!window.confirm('Xử lý lại phụ đề cho video này bằng thuật toán mới nhất? Bạn sẽ được chuyển về trang chủ để theo dõi tiến trình.')) {
    return
  }
  reprocessError.value = null
  isReprocessing.value = true
  try {
    await videoStore.reprocess(numericVideoId.value)
    router.push({ name: 'home' })
  } catch (error) {
    reprocessError.value = toApiError(error).message
    isReprocessing.value = false
  }
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
            <div class="d-flex align-items-center gap-2 flex-wrap">
              <button
                class="btn btn-sm text-nowrap"
                :class="showTranslation ? 'btn-primary' : 'btn-outline-primary'"
                type="button"
                :disabled="subtitlesStore.sameLanguage"
                @click="showTranslation = !showTranslation"
              >
                {{ showTranslation ? '✓ Bản dịch' : 'Bản dịch' }}
              </button>

              <select
                :value="targetLanguage"
                class="form-select form-select-sm language-select"
                aria-label="Chọn ngôn ngữ dịch"
                @change="onTargetLanguageChange"
              >
                <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">
                  Dịch: {{ lang.label }}
                </option>
              </select>
            </div>

            <div class="download-menu">
              <button
                class="btn btn-outline-primary btn-sm"
                type="button"
                :disabled="isPreparingDownload"
                @click="isDownloadMenuOpen = !isDownloadMenuOpen"
              >
                {{ isPreparingDownload ? 'Đang chuẩn bị...' : '⬇ Tải xuống' }}
              </button>
              <div v-if="isDownloadMenuOpen" class="download-menu__list">
                <button class="download-menu__item" type="button" @click="onDownload('original')">Tải phụ đề</button>
                <button
                  class="download-menu__item"
                  type="button"
                  :disabled="subtitlesStore.sameLanguage"
                  @click="onDownload('translation')"
                >
                  Tải bản dịch
                </button>
                <button
                  class="download-menu__item"
                  type="button"
                  :disabled="subtitlesStore.sameLanguage"
                  @click="onDownload('bilingual')"
                >
                  Tải phụ đề song ngữ
                </button>
              </div>
            </div>

            <button
              class="btn btn-outline-secondary btn-sm"
              type="button"
              :disabled="isReprocessing"
              title="Cắt lại phụ đề bằng thuật toán mới nhất (dùng khi phụ đề bị mất dấu câu hoặc cắt dòng chưa hợp lý)"
              @click="onReprocess"
            >
              {{ isReprocessing ? 'Đang gửi yêu cầu...' : '⟳ Xử lý lại phụ đề' }}
            </button>
          </div>

          <div v-if="reprocessError" class="alert alert-danger py-1 px-2 small mx-2 mb-0">{{ reprocessError }}</div>

          <div v-if="showTranslation && subtitlesStore.translationUnavailable" class="translation-warning">
            ⚠ Dịch vụ dịch tạm thời không khả dụng (có thể do vượt giới hạn sử dụng). Đang hiển thị tạm bản gốc thay cho bản dịch.
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
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0.75rem 0.75rem 0;
}

.language-select {
  width: auto;
}

.translation-warning {
  margin: 0.5rem 0.75rem 0;
  padding: 0.5rem 0.75rem;
  background: #fff7e6;
  border: 1px solid #f5c065;
  border-radius: var(--brand-radius-sm);
  color: #7a5200;
  font-size: 0.8rem;
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
  border-radius: var(--brand-radius-sm);
  box-shadow: var(--brand-shadow);
  z-index: 10;
  min-width: 180px;
  overflow: hidden;
}

.download-menu__item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.5rem 0.85rem;
  background: none;
  border: none;
  font-size: 0.9rem;
}

.download-menu__item:hover:not(:disabled) {
  background: var(--brand-bg);
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
  border-radius: 999px;
}

@media (max-width: 900px) {
  .watch-stage {
    grid-template-columns: 1fr;
  }
}
</style>
