<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useVideoStore } from '@/stores/video.store'
import { useWatchSessionStore } from '@/stores/watchSession.store'
import { toApiError } from '@/api/errors'
import RecentlyWatchedSection from '@/components/home/RecentlyWatchedSection.vue'
import {
  DEFAULT_SOURCE_LANGUAGE_CODE,
  DEFAULT_TARGET_LANGUAGE_CODE,
  SOURCE_LANGUAGES,
  TARGET_LANGUAGES,
} from '@/utils/languages'
import { failureMessage } from '@/utils/failureMessages'

const router = useRouter()
const videoStore = useVideoStore()
const watchSessionStore = useWatchSessionStore()

function resumeWatching() {
  router.push({
    name: 'watch',
    params: { videoId: watchSessionStore.videoId as number },
    query: { lang: watchSessionStore.targetLanguage ?? undefined },
  })
}

const youtubeUrl = ref('')
const sourceLanguage = ref(DEFAULT_SOURCE_LANGUAGE_CODE)
const targetLanguage = ref(DEFAULT_TARGET_LANGUAGE_CODE)
const submitError = ref<string | null>(null)
const isSubmitting = ref(false)

async function onSubmit() {
  submitError.value = null
  isSubmitting.value = true
  try {
    // submit() resets any previous video's state before starting the new one - the only
    // place this store is meant to be cleared from besides a hard refresh (see note below).
    await videoStore.submit(youtubeUrl.value, sourceLanguage.value)
  } catch (error) {
    submitError.value = toApiError(error).message
  } finally {
    isSubmitting.value = false
  }
}

function onCancelStuckSubmit() {
  videoStore.reset()
  submitError.value = null
}

// progressPercent comes straight from the backend, driven by real milestones (yt-dlp's
// own log output while it fetches, then actual subtitle lines saved) - never a fake timer.
const progressPercent = computed(() => videoStore.progressPercent ?? 0)

const progressLabel = computed(() => {
  if (videoStore.totalLines) {
    return `Đang xử lý phụ đề: ${videoStore.processedLines ?? 0}/${videoStore.totalLines} dòng (${progressPercent.value}%)`
  }
  const percent = progressPercent.value
  if (percent >= 50) return `Đang tải phụ đề từ YouTube... (${percent}%)`
  if (percent >= 20) return `Đang tải thông tin video... (${percent}%)`
  if (percent >= 10) return `Đang kết nối tới YouTube... (${percent}%)`
  return `Đang khởi tạo... (${percent}%)`
})

const isSubmittingInThisSession = computed(
  () => videoStore.isPolling && (videoStore.status === 'PENDING' || videoStore.status === 'PROCESSING'),
)

watch(
  () => videoStore.status,
  (status) => {
    if (status === 'READY' && videoStore.videoId) {
      router.push({ name: 'watch', params: { videoId: videoStore.videoId }, query: { lang: targetLanguage.value } })
    }
  },
)

// Deliberately no onMounted reset here: videoStore is a Pinia store, so it persists across
// route changes by design. Landing back on Home after Watch -> Dictionary -> Home should NOT
// look like nothing was ever submitted - the form below is always shown regardless of any
// leftover status, and an in-flight submission's progress bar (isSubmittingInThisSession)
// stays visible too. The store only actually clears on a new submission (submit() resets
// first) or a hard refresh (Pinia state is in-memory only) - never just from navigating here.
onUnmounted(() => {
  videoStore.stopPolling()
})
</script>

<template>
  <div class="home">
    <button v-if="watchSessionStore.videoId !== null" type="button" class="resume-banner" @click="resumeWatching">
      <span class="resume-banner__icon">▶</span>
      <span class="resume-banner__text">
        Tiếp tục xem<template v-if="watchSessionStore.title">: <strong>{{ watchSessionStore.title }}</strong></template>
      </span>
      <span class="resume-banner__arrow">›</span>
    </button>

    <div class="submit-bar">
      <form class="submit-form" @submit.prevent="onSubmit">
        <fieldset class="submit-fieldset" :disabled="isSubmittingInThisSession">
        <div class="submit-field submit-field--url">
          <label class="submit-label" for="youtubeUrl">Link YouTube</label>
          <input
            id="youtubeUrl"
            v-model="youtubeUrl"
            type="url"
            class="form-control"
            placeholder="Dán link YouTube..."
            required
          />
        </div>
        <div class="submit-field">
          <label class="submit-label" for="sourceLanguage">Ngôn ngữ video</label>
          <select id="sourceLanguage" v-model="sourceLanguage" class="form-select">
            <option v-for="lang in SOURCE_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
          </select>
        </div>
        <div class="submit-field">
          <label class="submit-label" for="targetLanguage">Dịch sang</label>
          <select id="targetLanguage" v-model="targetLanguage" class="form-select">
            <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
          </select>
        </div>
        <button class="btn btn-primary submit-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Đang gửi...' : '+ Tạo video' }}
        </button>
        </fieldset>
      </form>

      <div v-if="submitError" class="alert alert-danger py-2 mt-2 mb-0">{{ submitError }}</div>
      <div v-if="videoStore.status === 'FAILED'" class="alert alert-warning py-2 mt-2 mb-0">
        {{ failureMessage(videoStore.failureReason) }}
      </div>

      <div v-if="isSubmittingInThisSession" class="submit-progress">
        <p class="fw-semibold small mb-1">Đang xử lý video{{ videoStore.title ? `: ${videoStore.title}` : '' }}...</p>
        <div class="progress-track" role="progressbar" :aria-valuenow="progressPercent" aria-valuemin="0" aria-valuemax="100">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <p class="text-muted small mt-1 mb-0">{{ progressLabel }}</p>

        <div v-if="videoStore.clientTimedOut" class="alert alert-warning py-2 mt-2 mb-0">
          Quá trình xử lý đang mất nhiều thời gian hơn dự kiến.
          <button class="btn btn-sm btn-link" type="button" @click="onCancelStuckSubmit">Hủy và thử lại</button>
        </div>
      </div>
    </div>

    <RecentlyWatchedSection />
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.resume-banner {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  background: var(--brand-green-light);
  border: 1px solid var(--brand-green);
  border-radius: var(--brand-radius-md);
  padding: 0.65rem 1rem;
  color: var(--brand-green-dark);
  font-weight: 600;
  text-align: left;
}

.resume-banner:hover {
  background: var(--brand-green);
  color: #fff;
}

.resume-banner__icon {
  font-size: 0.85rem;
}

.resume-banner__text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-banner__arrow {
  font-size: 1.1rem;
  font-weight: 800;
}

.submit-bar {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  box-shadow: var(--brand-shadow);
  padding: 1rem 1.25rem;
}

.submit-form {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.submit-fieldset {
  display: contents;
  border: none;
  padding: 0;
  margin: 0;
}

.submit-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 160px;
}

.submit-field--url {
  flex: 1 1 320px;
}

.submit-label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--brand-muted);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.submit-button {
  white-space: nowrap;
  height: 38px;
}

.submit-progress {
  margin-top: 0.85rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--brand-border);
}

.progress-track {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: var(--brand-green-light);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--brand-green);
  transition: width 0.4s ease;
}

@media (max-width: 640px) {
  .submit-form {
    flex-direction: column;
    align-items: stretch;
  }

  .submit-field {
    min-width: 0;
  }
}
</style>
