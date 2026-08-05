import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * Tracks the video the user is actively watching so Home can offer to resume it exactly
 * where they left off, instead of showing a blank "waiting for link" form just because they
 * briefly visited another page. Deliberately separate from video.store's PENDING/PROCESSING
 * ingestion-tracking state - this is about "what was I watching", not "what am I submitting
 * right now", and the two must not clobber each other if a new submission happens while an
 * older video still has an active watch session.
 *
 * In-memory only (like all Pinia state here) - a hard refresh clears it, which is the
 * intended way to fully return to the blank "waiting for link" Home screen.
 */
export const useWatchSessionStore = defineStore('watchSession', () => {
  const videoId = ref<number | null>(null)
  const title = ref<string | null>(null)
  const targetLanguage = ref<string | null>(null)
  const lastPlaybackTimeMs = ref(0)

  function setActive(id: number, videoTitle: string | null, lang: string) {
    videoId.value = id
    title.value = videoTitle
    targetLanguage.value = lang
    lastPlaybackTimeMs.value = 0
  }

  function updatePlaybackTime(ms: number) {
    lastPlaybackTimeMs.value = ms
  }

  return { videoId, title, targetLanguage, lastPlaybackTimeMs, setActive, updatePlaybackTime }
})
