import { onMounted, onUnmounted, ref, shallowRef } from 'vue'

interface YoutubePlayerInstance {
  getCurrentTime(): number
  playVideo(): void
  pauseVideo(): void
  seekTo(seconds: number, allowSeekAhead: boolean): void
  destroy(): void
}

interface YoutubePlayerConstructor {
  new (
    elementId: string,
    options: {
      videoId: string
      events: {
        onReady: () => void
      }
    },
  ): YoutubePlayerInstance
}

declare global {
  interface Window {
    YT?: { Player: YoutubePlayerConstructor }
    onYouTubeIframeAPIReady?: () => void
  }
}

const IFRAME_API_SRC = 'https://www.youtube.com/iframe_api'
const CURRENT_TIME_POLL_MS = 250

let apiReadyPromise: Promise<void> | null = null

function loadYoutubeIframeApi(): Promise<void> {
  if (window.YT?.Player) {
    return Promise.resolve()
  }
  if (!apiReadyPromise) {
    apiReadyPromise = new Promise((resolve) => {
      const previous = window.onYouTubeIframeAPIReady
      window.onYouTubeIframeAPIReady = () => {
        previous?.()
        resolve()
      }
      if (!document.querySelector(`script[src="${IFRAME_API_SRC}"]`)) {
        const script = document.createElement('script')
        script.src = IFRAME_API_SRC
        document.head.appendChild(script)
      }
    })
  }
  return apiReadyPromise
}

export function useYoutubePlayer(elementId: string, videoId: string) {
  const isReady = ref(false)
  const currentTimeMs = ref(0)
  const player = shallowRef<YoutubePlayerInstance | null>(null)
  let pollTimer: ReturnType<typeof setInterval> | null = null
  // The IFrame API returns a Player object synchronously, but its methods (pauseVideo,
  // seekTo, ...) aren't safe to call until onReady fires - calling them earlier throws
  // "X is not a function". A user can click a transcript line/word before that happens
  // (subtitles load independently of - and often faster than - the video iframe), so a
  // seek requested that early is queued and replayed once the player is actually ready.
  let pendingSeekMs: number | null = null

  async function init() {
    await loadYoutubeIframeApi()
    if (!window.YT) return
    player.value = new window.YT.Player(elementId, {
      videoId,
      events: {
        onReady: () => {
          isReady.value = true
          if (pendingSeekMs !== null) {
            player.value?.seekTo(pendingSeekMs / 1000, true)
            pendingSeekMs = null
          }
          pollTimer = setInterval(() => {
            const current = player.value
            if (current) {
              currentTimeMs.value = Math.round(current.getCurrentTime() * 1000)
            }
          }, CURRENT_TIME_POLL_MS)
        },
      },
    })
  }

  onMounted(() => {
    void init()
  })

  function play() {
    if (!isReady.value) return
    player.value?.playVideo()
  }

  function pause() {
    if (!isReady.value) return
    player.value?.pauseVideo()
  }

  function seekTo(ms: number) {
    if (!isReady.value) {
      pendingSeekMs = ms
      return
    }
    player.value?.seekTo(ms / 1000, true)
  }

  onUnmounted(() => {
    if (pollTimer) clearInterval(pollTimer)
    player.value?.destroy()
  })

  return { isReady, currentTimeMs, play, pause, seekTo }
}
