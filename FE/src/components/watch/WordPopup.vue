<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { wordApi } from '@/api/word.api'
import { dictionaryApi } from '@/api/dictionary.api'
import { toApiError } from '@/api/errors'

const props = defineProps<{
  word: string
  sentenceContext: string
  sourceLanguage: string
  targetLanguage: string
  videoId: number
  subtitleLineId: number
}>()
const emit = defineEmits<{ close: [] }>()

type Status = 'loading' | 'success' | 'error'

const status = ref<Status>('loading')
const contextualMeaning = ref<string | null>(null)
const dictionaryMeaning = ref<string | null>(null)
const examples = ref<string[]>([])
const errorMessage = ref<string | null>(null)

const isSaving = ref(false)
const saveResult = ref<'new' | 'updated' | null>(null)

async function load() {
  status.value = 'loading'
  errorMessage.value = null
  try {
    const response = await wordApi.lookup({
      word: props.word,
      sourceLanguage: props.sourceLanguage,
      targetLanguage: props.targetLanguage,
      sentenceContext: props.sentenceContext,
      videoId: props.videoId,
      subtitleLineId: props.subtitleLineId,
    })
    // Gemini failures come back as HTTP 200 with an `error` field - must check
    // this explicitly rather than relying on the response status.
    if (response.error) {
      status.value = 'error'
      errorMessage.value = 'Không thể tra nghĩa từ này lúc này. Vui lòng thử lại.'
      return
    }
    contextualMeaning.value = response.contextualMeaning
    dictionaryMeaning.value = response.dictionaryMeaning
    examples.value = response.examples
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = toApiError(error).message
  }
}

async function onSave() {
  isSaving.value = true
  try {
    const response = await dictionaryApi.save({
      word: props.word,
      sourceLanguage: props.sourceLanguage,
      targetLanguage: props.targetLanguage,
      contextualMeaning: contextualMeaning.value,
      dictionaryMeaning: dictionaryMeaning.value,
      examples: examples.value,
      videoId: props.videoId,
      subtitleLineId: props.subtitleLineId,
      sentenceSnapshot: props.sentenceContext,
    })
    saveResult.value = response.wasNew ? 'new' : 'updated'
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    isSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="word-popup-backdrop" @click.self="emit('close')">
    <div class="word-popup card shadow">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-start mb-2">
          <h2 class="h5 mb-0">{{ word }}</h2>
          <button type="button" class="btn-close" aria-label="Đóng" @click="emit('close')"></button>
        </div>

        <div v-if="status === 'loading'" class="text-center py-3">
          <div class="spinner-border spinner-border-sm text-primary" role="status"></div>
        </div>

        <div v-else-if="status === 'error'">
          <p class="text-danger small mb-2">{{ errorMessage || 'Nghĩa của từ này hiện không khả dụng.' }}</p>
          <button class="btn btn-sm btn-outline-secondary" type="button" @click="load">Thử lại</button>
        </div>

        <div v-else>
          <div class="mb-2">
            <div class="fw-semibold small text-uppercase text-muted">Nghĩa theo ngữ cảnh</div>
            <div>{{ contextualMeaning }}</div>
          </div>
          <div class="mb-2">
            <div class="fw-semibold small text-uppercase text-muted">Nghĩa thông thường</div>
            <div>{{ dictionaryMeaning }}</div>
          </div>
          <div v-if="examples.length" class="mb-3">
            <div class="fw-semibold small text-uppercase text-muted">Ví dụ</div>
            <ul class="mb-0 ps-3">
              <li v-for="(example, index) in examples" :key="index">{{ example }}</li>
            </ul>
          </div>

          <div v-if="saveResult" class="alert alert-success py-1 small">
            {{ saveResult === 'new' ? 'Đã lưu vào từ điển.' : 'Đã cập nhật trong từ điển.' }}
          </div>
          <button v-else class="btn btn-primary btn-sm" type="button" :disabled="isSaving" @click="onSave">
            {{ isSaving ? 'Đang lưu...' : 'Lưu vào từ điển' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.word-popup-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1050;
}
.word-popup {
  width: min(420px, 90vw);
}
</style>
