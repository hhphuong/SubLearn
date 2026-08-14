import { ref } from 'vue'
import { defineStore } from 'pinia'
import { dictionaryApi } from '@/api/dictionary.api'
import type { DictionaryEntryDetailResponse, DictionaryEntrySummaryResponse, DictionaryFolderResponse } from '@/types/api'

const PAGE_SIZE = 20

export const useDictionaryStore = defineStore('dictionary', () => {
  const entries = ref<DictionaryEntrySummaryResponse[]>([])
  const page = ref(0)
  const size = ref(PAGE_SIZE)
  const totalElements = ref(0)
  const search = ref('')
  const sourceLanguageFilter = ref('')
  const targetLanguageFilter = ref('')
  const folderIdFilter = ref<number | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const detailCache = ref(new Map<number, DictionaryEntryDetailResponse>())

  const folders = ref<DictionaryFolderResponse[]>([])
  const foldersLoading = ref(false)
  const foldersError = ref<string | null>(null)

  async function fetchList(reset = false) {
    if (reset) page.value = 0
    loading.value = true
    error.value = null
    try {
      const response = await dictionaryApi.list(
        page.value,
        size.value,
        sourceLanguageFilter.value,
        targetLanguageFilter.value,
        folderIdFilter.value,
        search.value,
      )
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

  /** Folders are scoped to one source language - pass '' to clear the list (e.g. "all languages" view). */
  async function fetchFolders(sourceLanguage: string) {
    if (!sourceLanguage) {
      folders.value = []
      return
    }
    foldersLoading.value = true
    foldersError.value = null
    try {
      folders.value = await dictionaryApi.listFolders(sourceLanguage)
    } catch (err) {
      foldersError.value = err instanceof Error ? err.message : 'Không tải được danh sách folder'
    } finally {
      foldersLoading.value = false
    }
  }

  /** Newly-created folders sort first (backend orders newest-first), matching the "suggest the newest folder" UX. */
  async function createFolder(sourceLanguage: string, name: string) {
    const folder = await dictionaryApi.createFolder({ sourceLanguage, name })
    folders.value = [folder, ...folders.value]
    return folder
  }

  async function removeFolder(id: number) {
    await dictionaryApi.removeFolder(id)
    folders.value = folders.value.filter((f) => f.id !== id)
    if (folderIdFilter.value === id) {
      folderIdFilter.value = null
    }
  }

  return {
    entries,
    page,
    size,
    totalElements,
    search,
    sourceLanguageFilter,
    targetLanguageFilter,
    folderIdFilter,
    loading,
    error,
    fetchList,
    fetchDetail,
    remove,
    folders,
    foldersLoading,
    foldersError,
    fetchFolders,
    createFolder,
    removeFolder,
  }
})
