import { ref } from 'vue'
import { defineStore } from 'pinia'
import { videoApi } from '@/api/video.api'
import type { VideoHistoryEntryResponse } from '@/types/api'

const PAGE_SIZE = 12

export const useVideoHistoryStore = defineStore('videoHistory', () => {
  const entries = ref<VideoHistoryEntryResponse[]>([])
  const page = ref(0)
  const size = ref(PAGE_SIZE)
  const totalElements = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchPage(newPage: number) {
    page.value = newPage
    loading.value = true
    error.value = null
    try {
      const response = await videoApi.getHistory(page.value, size.value)
      entries.value = response.entries
      totalElements.value = response.totalElements
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Không tải được lịch sử xem'
    } finally {
      loading.value = false
    }
  }

  return {
    entries,
    page,
    size,
    totalElements,
    loading,
    error,
    fetchPage,
  }
})
