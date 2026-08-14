<script setup lang="ts">
import { ref } from 'vue'
import { useDictionaryStore } from '@/stores/dictionary.store'
import { languageLabel } from '@/utils/languages'
import type { DictionaryContextDto, DictionaryEntrySummaryResponse } from '@/types/api'

const props = defineProps<{ entry: DictionaryEntrySummaryResponse }>()
const emit = defineEmits<{ delete: [id: number] }>()

const dictionaryStore = useDictionaryStore()
const isExpanded = ref(false)
const contexts = ref<DictionaryContextDto[]>([])
const isLoadingContexts = ref(false)

async function toggleExpand() {
  isExpanded.value = !isExpanded.value
  if (isExpanded.value && contexts.value.length === 0) {
    isLoadingContexts.value = true
    try {
      const detail = await dictionaryStore.fetchDetail(props.entry.id)
      contexts.value = detail.contexts
    } finally {
      isLoadingContexts.value = false
    }
  }
}

function onDelete() {
  if (confirm(`Xóa "${props.entry.displayWord}" khỏi từ điển?`)) {
    emit('delete', props.entry.id)
  }
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString()
}
</script>

<template>
  <div class="card mb-3">
    <div class="card-body">
      <div class="d-flex justify-content-between align-items-start">
        <div>
          <h3 class="h5 mb-1">{{ entry.displayWord }}</h3>
          <span class="badge entry-badge me-2"
            >{{ languageLabel(entry.sourceLanguage) }} → {{ languageLabel(entry.targetLanguage) }}</span
          >
          <span v-if="entry.folderName" class="badge entry-badge-folder me-2">📁 {{ entry.folderName }}</span>
          <span class="badge entry-badge-muted">{{ entry.contextCount }} ngữ cảnh</span>
        </div>
        <button class="btn btn-sm btn-outline-danger" type="button" @click="onDelete">Xóa</button>
      </div>

      <p v-if="entry.contextualMeaning" class="mb-1 mt-2"><strong>Ngữ cảnh:</strong> {{ entry.contextualMeaning }}</p>
      <p v-if="entry.dictionaryMeaning" class="mb-1"><strong>Nghĩa:</strong> {{ entry.dictionaryMeaning }}</p>
      <ul v-if="entry.examples.length" class="mb-2 ps-3">
        <li v-for="(example, index) in entry.examples" :key="index">{{ example }}</li>
      </ul>

      <button class="btn btn-sm btn-link px-0" type="button" @click="toggleExpand">
        {{ isExpanded ? 'Ẩn ngữ cảnh đã lưu' : 'Xem ngữ cảnh đã lưu' }}
      </button>

      <div v-if="isExpanded" class="mt-2 border-top pt-2">
        <div v-if="isLoadingContexts" class="text-muted small">Đang tải...</div>
        <ul v-else class="list-unstyled small mb-0">
          <li v-for="context in contexts" :key="context.id" class="mb-2">
            <RouterLink :to="{ name: 'watch', params: { videoId: context.videoId } }">{{
              context.sentenceSnapshot
            }}</RouterLink>
            <div class="text-muted">{{ formatDate(context.savedAt) }}</div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.entry-badge {
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
  font-weight: 600;
}

.entry-badge-muted {
  background: var(--brand-bg);
  color: var(--brand-muted);
  font-weight: 600;
}

.entry-badge-folder {
  background: #fff3cd;
  color: #664d03;
  font-weight: 600;
}
</style>
