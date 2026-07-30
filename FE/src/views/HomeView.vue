<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useVideoStore } from '@/stores/video.store'
import { toApiError } from '@/api/errors'
import { DEFAULT_TARGET_LANGUAGE_CODE, TARGET_LANGUAGES } from '@/utils/languages'
import { failureMessage } from '@/utils/failureMessages'

const router = useRouter()
const videoStore = useVideoStore()

const youtubeUrl = ref('')
const targetLanguage = ref(DEFAULT_TARGET_LANGUAGE_CODE)
const submitError = ref<string | null>(null)
const isSubmitting = ref(false)

async function onSubmit() {
  submitError.value = null
  isSubmitting.value = true
  try {
    await videoStore.submit(youtubeUrl.value)
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
  <div class="row justify-content-center">
    <div class="col-12 col-md-8 col-lg-6">
      <h1 class="h3 mb-4">SubLearn — Học từ vựng qua video YouTube</h1>

      <form v-if="!videoStore.videoId || videoStore.status === 'FAILED'" @submit.prevent="onSubmit">
        <div class="mb-3">
          <label class="form-label" for="youtubeUrl">Link video YouTube</label>
          <input
            id="youtubeUrl"
            v-model="youtubeUrl"
            type="url"
            class="form-control"
            placeholder="https://www.youtube.com/watch?v=..."
            required
          />
        </div>
        <div class="mb-3">
          <label class="form-label" for="targetLanguage">Ngôn ngữ muốn học</label>
          <select id="targetLanguage" v-model="targetLanguage" class="form-select">
            <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
          </select>
        </div>
        <div v-if="submitError" class="alert alert-danger py-2">{{ submitError }}</div>
        <div v-if="videoStore.status === 'FAILED'" class="alert alert-warning py-2">
          {{ failureMessage(videoStore.failureReason) }}
        </div>
        <button class="btn btn-primary w-100" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Đang gửi...' : 'Bắt đầu' }}
        </button>
      </form>

      <div v-else class="text-center py-4">
        <div v-if="videoStore.status === 'PENDING' || videoStore.status === 'PROCESSING'">
          <div class="spinner-border text-primary mb-3" role="status"></div>
          <p>Đang xử lý video{{ videoStore.title ? `: ${videoStore.title}` : '' }}...</p>
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
