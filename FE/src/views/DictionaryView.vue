<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useDictionaryStore } from '@/stores/dictionary.store'
import DictionaryEntryCard from '@/components/dictionary/DictionaryEntryCard.vue'
import { TARGET_LANGUAGES } from '@/utils/languages'

const dictionaryStore = useDictionaryStore()
const searchInput = ref('')
let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch(searchInput, (value) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    dictionaryStore.search = value
    dictionaryStore.fetchList(true)
  }, 400)
})

watch(
  () => dictionaryStore.targetLanguageFilter,
  () => {
    dictionaryStore.fetchList(true)
  },
)

function onDelete(id: number) {
  dictionaryStore.remove(id)
}

function goToPage(newPage: number) {
  dictionaryStore.page = newPage
  dictionaryStore.fetchList()
}

const totalPages = computed(() => Math.max(1, Math.ceil(dictionaryStore.totalElements / dictionaryStore.size)))

onMounted(() => {
  dictionaryStore.fetchList(true)
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<template>
  <div>
    <h1 class="page-title">📖 Từ điển của tôi</h1>
    <div class="filters">
      <input v-model="searchInput" type="search" class="form-control" placeholder="Tìm từ..." />
      <select v-model="dictionaryStore.targetLanguageFilter" class="form-select">
        <option value="">Tất cả ngôn ngữ</option>
        <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
      </select>
    </div>

    <div v-if="dictionaryStore.loading" class="text-center py-4">
      <div class="spinner-border" role="status"></div>
    </div>
    <div v-else-if="dictionaryStore.error" class="alert alert-danger">{{ dictionaryStore.error }}</div>
    <div v-else-if="dictionaryStore.entries.length === 0" class="empty-state">
      <div class="empty-state__icon">🗂️</div>
      <p class="text-muted mb-0">Chưa có từ nào được lưu.</p>
    </div>
    <div v-else>
      <DictionaryEntryCard
        v-for="entry in dictionaryStore.entries"
        :key="entry.id"
        :entry="entry"
        @delete="onDelete"
      />

      <nav v-if="totalPages > 1" class="d-flex justify-content-center gap-2 mt-3">
        <button
          v-for="p in totalPages"
          :key="p"
          class="btn btn-sm"
          :class="p - 1 === dictionaryStore.page ? 'btn-primary' : 'btn-outline-primary'"
          type="button"
          @click="goToPage(p - 1)"
        >
          {{ p }}
        </button>
      </nav>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 1.5rem;
  font-weight: 800;
  margin-bottom: 1.25rem;
}

.filters {
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.spinner-border {
  color: var(--brand-green);
}

.empty-state {
  background: #fff;
  border: 1px dashed var(--brand-border);
  border-radius: var(--brand-radius-md);
  padding: 3rem 1rem;
  text-align: center;
}

.empty-state__icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}

@media (max-width: 640px) {
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
