import { ref } from 'vue'
import { defineStore } from 'pinia'
import { videoApi } from '@/api/video.api'
import type { FailureReason, VideoStatus } from '@/types/api'

const POLL_INTERVAL_MS = 1500
const MAX_POLL_DURATION_MS = 5 * 60 * 1000

export const useVideoStore = defineStore('video', () => {
  const videoId = ref<number | null>(null)
  const status = ref<VideoStatus | null>(null)
  const failureReason = ref<FailureReason | null>(null)
  const failureMessage = ref<string | null>(null)
  const title = ref<string | null>(null)
  const sourceLanguage = ref<string | null>(null)
  const durationSeconds = ref<number | null>(null)
  const youtubeVideoId = ref<string | null>(null)
  const totalLines = ref<number | null>(null)
  const processedLines = ref<number | null>(null)
  const progressPercent = ref<number | null>(null)
  const isPolling = ref(false)
  const clientTimedOut = ref(false)

  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let pollStartedAt = 0

  function applyStatus(response: {
    status: VideoStatus
    failureReason: FailureReason | null
    failureMessage: string | null
    title: string | null
    sourceLanguage: string | null
    durationSeconds: number | null
    youtubeVideoId: string | null
    totalLines: number | null
    processedLines: number | null
    progressPercent: number | null
  }) {
    status.value = response.status
    failureReason.value = response.failureReason
    failureMessage.value = response.failureMessage
    title.value = response.title
    sourceLanguage.value = response.sourceLanguage
    durationSeconds.value = response.durationSeconds
    youtubeVideoId.value = response.youtubeVideoId
    totalLines.value = response.totalLines
    processedLines.value = response.processedLines
    progressPercent.value = response.progressPercent
  }

  async function fetchStatus(id: number) {
    const response = await videoApi.getStatus(id)
    applyStatus(response)
    return response.status
  }

  function stopPolling() {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
    isPolling.value = false
  }

  function startPolling(id: number) {
    stopPolling()
    isPolling.value = true
    clientTimedOut.value = false
    pollStartedAt = Date.now()

    const tick = async () => {
      try {
        const currentStatus = await fetchStatus(id)
        if (currentStatus === 'READY' || currentStatus === 'FAILED') {
          stopPolling()
          return
        }
        if (Date.now() - pollStartedAt > MAX_POLL_DURATION_MS) {
          clientTimedOut.value = true
          stopPolling()
          return
        }
        pollTimer = setTimeout(tick, POLL_INTERVAL_MS)
      } catch {
        stopPolling()
      }
    }

    void tick()
  }

  async function submit(youtubeUrl: string) {
    reset()
    const response = await videoApi.submit(youtubeUrl)
    videoId.value = response.videoId
    status.value = response.status
    if (response.status === 'READY' || response.status === 'FAILED') {
      await fetchStatus(response.videoId)
    } else {
      startPolling(response.videoId)
    }
  }

  function reset() {
    stopPolling()
    videoId.value = null
    status.value = null
    failureReason.value = null
    failureMessage.value = null
    title.value = null
    sourceLanguage.value = null
    durationSeconds.value = null
    youtubeVideoId.value = null
    totalLines.value = null
    processedLines.value = null
    progressPercent.value = null
    clientTimedOut.value = false
  }

  return {
    videoId,
    status,
    failureReason,
    failureMessage,
    title,
    sourceLanguage,
    durationSeconds,
    youtubeVideoId,
    totalLines,
    processedLines,
    progressPercent,
    isPolling,
    clientTimedOut,
    submit,
    fetchStatus,
    startPolling,
    stopPolling,
    reset,
  }
})
