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
    <h1 class="h4 mb-3">Từ điển của tôi</h1>
    <div class="row g-2 mb-3">
      <div class="col-12 col-sm-8">
        <input v-model="searchInput" type="search" class="form-control" placeholder="Tìm từ..." />
      </div>
      <div class="col-12 col-sm-4">
        <select v-model="dictionaryStore.targetLanguageFilter" class="form-select">
          <option value="">Tất cả ngôn ngữ</option>
          <option v-for="lang in TARGET_LANGUAGES" :key="lang.code" :value="lang.code">{{ lang.label }}</option>
        </select>
      </div>
    </div>

    <div v-if="dictionaryStore.loading" class="text-center py-4">
      <div class="spinner-border text-primary" role="status"></div>
    </div>
    <div v-else-if="dictionaryStore.error" class="alert alert-danger">{{ dictionaryStore.error }}</div>
    <div v-else-if="dictionaryStore.entries.length === 0" class="text-muted text-center py-4">
      Chưa có từ nào được lưu.
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
          :class="p - 1 === dictionaryStore.page ? 'btn-primary' : 'btn-outline-secondary'"
          type="button"
          @click="goToPage(p - 1)"
        >
          {{ p }}
        </button>
      </nav>
    </div>
  </div>
</template>
