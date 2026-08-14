<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { wordApi } from '@/api/word.api'
import { dictionaryApi } from '@/api/dictionary.api'
import { toApiError } from '@/api/errors'
import type { DictionaryFolderResponse } from '@/types/api'

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

const NEW_FOLDER_OPTION = '__new__'
const folders = ref<DictionaryFolderResponse[]>([])
const selectedFolderId = ref<string>('')
const isCreatingFolder = ref(false)
const newFolderName = ref('')
const folderError = ref<string | null>(null)

async function loadFolders() {
  try {
    folders.value = await dictionaryApi.listFolders(props.sourceLanguage)
    // Suggest the most-recently-created folder by default - the backend already returns
    // folders newest-first, so this is just "pick the first one" with no extra sorting.
    selectedFolderId.value = folders.value[0] ? String(folders.value[0].id) : ''
  } catch {
    folders.value = []
  }
}

function onFolderSelectChange() {
  isCreatingFolder.value = selectedFolderId.value === NEW_FOLDER_OPTION
  folderError.value = null
}

async function confirmNewFolder() {
  const name = newFolderName.value.trim()
  if (!name) return
  folderError.value = null
  try {
    const folder = await dictionaryApi.createFolder({ sourceLanguage: props.sourceLanguage, name })
    folders.value = [folder, ...folders.value]
    selectedFolderId.value = String(folder.id)
    isCreatingFolder.value = false
    newFolderName.value = ''
  } catch (error) {
    folderError.value = toApiError(error).message
  }
}

function cancelNewFolder() {
  isCreatingFolder.value = false
  newFolderName.value = ''
  folderError.value = null
  selectedFolderId.value = folders.value[0] ? String(folders.value[0].id) : ''
}

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
    const folderId =
      selectedFolderId.value && selectedFolderId.value !== NEW_FOLDER_OPTION ? Number(selectedFolderId.value) : null
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
      folderId,
    })
    saveResult.value = response.wasNew ? 'new' : 'updated'
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  load()
  loadFolders()
})
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
          <div v-else class="save-block">
            <label class="fw-semibold small text-uppercase text-muted mb-1 d-block">Lưu vào folder</label>

            <div v-if="isCreatingFolder" class="d-flex gap-1 mb-2">
              <input
                v-model="newFolderName"
                type="text"
                class="form-control form-control-sm"
                placeholder="Tên folder mới..."
                autofocus
                @keyup.enter="confirmNewFolder"
              />
              <button class="btn btn-sm btn-primary" type="button" @click="confirmNewFolder">Tạo</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" @click="cancelNewFolder">Hủy</button>
            </div>
            <select
              v-else
              v-model="selectedFolderId"
              class="form-select form-select-sm mb-2"
              @change="onFolderSelectChange"
            >
              <option value="">Chưa phân loại</option>
              <option v-for="folder in folders" :key="folder.id" :value="String(folder.id)">
                {{ folder.name }} ({{ folder.entryCount }})
              </option>
              <option :value="NEW_FOLDER_OPTION">+ Tạo folder mới...</option>
            </select>
            <div v-if="folderError" class="text-danger small mb-2">{{ folderError }}</div>

            <button class="btn btn-primary btn-sm" type="button" :disabled="isSaving" @click="onSave">
              {{ isSaving ? 'Đang lưu...' : 'Lưu vào từ điển' }}
            </button>
          </div>
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
