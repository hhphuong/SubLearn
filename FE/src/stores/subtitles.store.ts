import { ref } from 'vue'
import { defineStore } from 'pinia'
import { subtitleApi } from '@/api/subtitle.api'
import type { ProcessingJobResponse, SubtitleLineDto } from '@/types/api'

const PAGE_SIZE = 50

export const useSubtitlesStore = defineStore('subtitles', () => {
  const videoId = ref<number | null>(null)
  const targetLanguage = ref<string | null>(null)
  const sourceLanguage = ref<string | null>(null)
  const sameLanguage = ref(false)
  const translationAvailable = ref(true)
  const activeTranslationJob = ref<ProcessingJobResponse | null>(null)
  const totalLines = ref(0)
  const lines = ref<SubtitleLineDto[]>([])
  const loadedPages = ref(new Set<number>())
  const loadingPage = ref<number | null>(null)
  const error = ref<string | null>(null)

  function reset() {
    videoId.value = null
    targetLanguage.value = null
    sourceLanguage.value = null
    sameLanguage.value = false
    translationAvailable.value = true
    activeTranslationJob.value = null
    totalLines.value = 0
    lines.value = []
    loadedPages.value = new Set()
    loadingPage.value = null
    error.value = null
  }

  async function fetchPage(page: number) {
    if (videoId.value === null || targetLanguage.value === null) return
    if (loadedPages.value.has(page) || loadingPage.value === page) return

    loadingPage.value = page
    try {
      const response = await subtitleApi.getSubtitles(videoId.value, targetLanguage.value, page, PAGE_SIZE)
      sourceLanguage.value = response.sourceLanguage
      sameLanguage.value = response.sameLanguage
      totalLines.value = response.totalLines
      translationAvailable.value = response.translationAvailable
      activeTranslationJob.value = response.activeTranslationJob

      const merged = [...lines.value, ...response.lines]
      merged.sort((a, b) => a.sequence - b.sequence)
      lines.value = merged
      loadedPages.value = new Set(loadedPages.value).add(page)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Không tải được phụ đề'
    } finally {
      loadingPage.value = null
    }
  }

  async function init(newVideoId: number, newTargetLanguage: string) {
    reset()
    videoId.value = newVideoId
    targetLanguage.value = newTargetLanguage
    await fetchPage(0)
  }

  function fetchNextPage() {
    const totalPages = Math.ceil(totalLines.value / PAGE_SIZE)
    for (let page = 0; page < totalPages; page++) {
      if (!loadedPages.value.has(page)) {
        void fetchPage(page)
        return
      }
    }
  }

  /**
   * Loads every remaining page in order - used before building a full-transcript download.
   * Subtitle pages are now pure DB reads, so no artificial rate-limit sleep is needed.
   */
  async function loadAllRemaining() {
    const totalPages = Math.ceil(totalLines.value / PAGE_SIZE)
    for (let page = 0; page < totalPages; page++) {
      if (!loadedPages.value.has(page)) {
        await fetchPage(page)
      }
    }
  }

  return {
    videoId,
    targetLanguage,
    sourceLanguage,
    sameLanguage,
    translationAvailable,
    activeTranslationJob,
    totalLines,
    lines,
    loadedPages,
    loadingPage,
    error,
    init,
    fetchPage,
    fetchNextPage,
    loadAllRemaining,
    reset,
  }
})
