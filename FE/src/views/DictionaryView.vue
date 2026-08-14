<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useDictionaryStore } from '@/stores/dictionary.store'
import DictionaryEntryCard from '@/components/dictionary/DictionaryEntryCard.vue'
import FolderSidebar from '@/components/dictionary/FolderSidebar.vue'
import { SOURCE_LANGUAGES, TARGET_LANGUAGES } from '@/utils/languages'

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

function selectSourceLanguage(code: string) {
  dictionaryStore.sourceLanguageFilter = code
  dictionaryStore.fetchList(true)
  // Folder list refresh is handled by FolderSidebar's watcher on this same value.
}

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

    <div class="language-tabs">
      <button
        type="button"
        class="language-tab"
        :class="{ 'language-tab--active': dictionaryStore.sourceLanguageFilter === '' }"
        @click="selectSourceLanguage('')"
      >
        Tất cả ngôn ngữ
      </button>
      <button
        v-for="lang in SOURCE_LANGUAGES"
        :key="lang.code"
        type="button"
        class="language-tab"
        :class="{ 'language-tab--active': dictionaryStore.sourceLanguageFilter === lang.code }"
        @click="selectSourceLanguage(lang.code)"
      >
        {{ lang.label }}
      </button>
    </div>

    <div class="dictionary-layout">
      <aside class="sidebar-panel">
        <FolderSidebar :source-language="dictionaryStore.sourceLanguageFilter" />
      </aside>

      <div class="main-panel">
        <div class="filters">
          <input v-model="searchInput" type="search" class="form-control" placeholder="Tìm từ..." />
          <select v-model="dictionaryStore.targetLanguageFilter" class="form-select">
            <option value="">Dịch sang: tất cả</option>
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
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 1.5rem;
  font-weight: 800;
  margin-bottom: 1.25rem;
}

.language-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1.25rem;
}

.language-tab {
  border: 1px solid var(--brand-border);
  background: #fff;
  border-radius: 999px;
  padding: 0.4rem 1rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--brand-muted);
}

.language-tab--active {
  background: var(--brand-green);
  border-color: var(--brand-green);
  color: #fff;
}

.dictionary-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 1.5rem;
  align-items: start;
}

.sidebar-panel {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  padding: 0.75rem;
  position: sticky;
  top: 1rem;
}

.main-panel {
  min-width: 0;
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

@media (max-width: 768px) {
  .dictionary-layout {
    grid-template-columns: 1fr;
  }

  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
