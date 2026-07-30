import { ref } from 'vue'
import { defineStore } from 'pinia'
import { dictionaryApi } from '@/api/dictionary.api'
import type { DictionaryEntryDetailResponse, DictionaryEntrySummaryResponse } from '@/types/api'

const PAGE_SIZE = 20

export const useDictionaryStore = defineStore('dictionary', () => {
  const entries = ref<DictionaryEntrySummaryResponse[]>([])
  const page = ref(0)
  const size = ref(PAGE_SIZE)
  const totalElements = ref(0)
  const search = ref('')
  const targetLanguageFilter = ref('')
  const loading = ref(false)
  const error = ref<string | null>(null)
  const detailCache = ref(new Map<number, DictionaryEntryDetailResponse>())

  async function fetchList(reset = false) {
    if (reset) page.value = 0
    loading.value = true
    error.value = null
    try {
      const response = await dictionaryApi.list(page.value, size.value, targetLanguageFilter.value, search.value)
      entries.value = response.entries
      totalElements.value = response.totalElements
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Không tải được từ điển'
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: number) {
    const cached = detailCache.value.get(id)
    if (cached) return cached
    const detail = await dictionaryApi.getDetail(id)
    detailCache.value.set(id, detail)
    return detail
  }

  async function remove(id: number) {
    await dictionaryApi.remove(id)
    entries.value = entries.value.filter((e) => e.id !== id)
    totalElements.value = Math.max(0, totalElements.value - 1)
    detailCache.value.delete(id)
  }

  return {
    entries,
    page,
    size,
    totalElements,
    search,
    targetLanguageFilter,
    loading,
    error,
    fetchList,
    fetchDetail,
    remove,
  }
})
