<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useVideoStore } from '@/stores/video.store'
import { toApiError } from '@/api/errors'
import {
  DEFAULT_SOURCE_LANGUAGE_CODE,
  DEFAULT_TARGET_LANGUAGE_CODE,
  SOURCE_LANGUAGES,
  TARGET_LANGUAGES,
} from '@/utils/languages'
import { failureMessage } from '@/utils/failureMessages'

const router = useRouter()
const videoStore = useVideoStore()

const youtubeUrl = ref('')
const sourceLanguage = ref(DEFAULT_SOURCE_LANGUAGE_CODE)
const targetLanguage = ref(DEFAULT_TARGET_LANGUAGE_CODE)
const submitError = ref<string | null>(null)
const isSubmitting = ref(false)

async function onSubmit() {
  submitError.value = null
  isSubmitting.value = true
  try {
    await videoStore.submit(youtubeUrl.value, sourceLanguage.value)
  } catch (error) {
    submitError.value = toApiError(error).message
  } finally {
    isSubmitting.value = false
  }
}

function onReset() {
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

watch(
  () => videoStore.status,
  (status) => {
    if (status === 'READY' && videoStore.videoId) {
      router.push({ name: 'watch', params: { videoId: videoStore.videoId }, query: { lang: targetLanguage.value } })
    }
  },
)

onUnmounted(() => {
  videoStore.stopPolling()
})
</script>

<template>
  <div class="home">
    <span class="visually-hidden">SubLearn</span>
    <section class="hero">
      <div class="hero__text">
        <h1 class="hero__title">Học ngoại ngữ dễ dàng qua bất kỳ video YouTube nào</h1>
        <p class="hero__subtitle">
          Dán link YouTube, đọc phụ đề song ngữ và bấm vào từ bất kỳ để tra nghĩa và lưu vào từ điển của bạn.
        </p>
      </div>
      <div class="hero__icon" aria-hidden="true">🎬</div>
    </section>

    <div class="panel">
      <form v-if="!videoStore.videoId || videoStore.status === 'FAILED'" @submit.prevent="onSubmit">
        <div class="mb-3">
          <label class="form-label fw-semibold" for="youtubeUrl">Link video YouTube</label>
          <input
            id="youtubeUrl"
            v-model="youtubeUrl"
            type="url"
            class="form-control form-control-lg"
            placeholder="https://www.youtube.com/watch?v=..."
            required
          />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" for="sourceLanguage">Ngôn ngữ của video</label>
          <select id="sourceLanguage" v-model="sourceLanguage" class="form-select form-select-lg">
            <option v-for="lang in SOURCE_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
          </select>
          <div class="form-text">Chọn đúng ngôn ngữ video đang nói để lấy phụ đề chính xác.</div>
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" for="targetLanguage">Ngôn ngữ muốn dịch sang</label>
          <select id="targetLanguage" v-model="targetLanguage" class="form-select form-select-lg">
            <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
          </select>
        </div>
        <div v-if="submitError" class="alert alert-danger py-2">{{ submitError }}</div>
        <div v-if="videoStore.status === 'FAILED'" class="alert alert-warning py-2">
          {{ failureMessage(videoStore.failureReason) }}
        </div>
        <button class="btn btn-primary btn-lg w-100" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Đang gửi...' : 'Tạo video' }}
        </button>
      </form>

      <div v-else class="text-center py-4">
        <div v-if="videoStore.status === 'PENDING' || videoStore.status === 'PROCESSING'">
          <p class="fw-semibold mb-1">Đang xử lý video{{ videoStore.title ? `: ${videoStore.title}` : '' }}...</p>

          <div class="progress-track" role="progressbar" :aria-valuenow="progressPercent" aria-valuemin="0" aria-valuemax="100">
            <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <p class="text-muted small mt-2 mb-0">{{ progressLabel }}</p>
          <p class="text-muted small">Việc này có thể mất một vài phút tùy độ dài video.</p>

          <div v-if="videoStore.clientTimedOut" class="alert alert-warning py-2 mt-3">
            Quá trình xử lý đang mất nhiều thời gian hơn dự kiến.
            <button class="btn btn-sm btn-link" type="button" @click="onReset">Hủy và thử lại</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.hero {
  background: linear-gradient(135deg, var(--brand-green) 0%, var(--brand-green-dark) 100%);
  border-radius: var(--brand-radius-lg);
  color: #fff;
  padding: 2.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
}

.hero__title {
  font-size: 1.8rem;
  font-weight: 800;
  margin-bottom: 0.6rem;
}

.hero__subtitle {
  color: rgba(255, 255, 255, 0.9);
  max-width: 46ch;
  margin-bottom: 0;
}

.hero__icon {
  font-size: 4rem;
  line-height: 1;
  flex-shrink: 0;
}

.panel {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  box-shadow: var(--brand-shadow);
  padding: 2rem;
  max-width: 560px;
  width: 100%;
  margin: 0 auto;
}

.progress-track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: var(--brand-green-light);
  overflow: hidden;
  margin-top: 1rem;
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--brand-green);
  transition: width 0.4s ease;
}

@media (max-width: 640px) {
  .hero {
    flex-direction: column;
    text-align: center;
  }
}
</style>
