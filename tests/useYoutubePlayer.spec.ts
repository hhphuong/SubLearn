import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useYoutubePlayer } from '../FE/src/composables/useYoutubePlayer'

// YT.PlayerState values, per YouTube's IFrame API docs.
const STATE_BUFFERING = 3
const STATE_PAUSED = 2
const STATE_PLAYING = 1

class MockYoutubePlayer {
  static instances: MockYoutubePlayer[] = []
  seekCalls: number[] = []
  currentTime = 0
  onReadyCb: () => void
  onStateChangeCb: (event: { data: number }) => void

  constructor(_elementId: string, options: { events: { onReady: () => void; onStateChange: (e: { data: number }) => void } }) {
    this.onReadyCb = options.events.onReady
    this.onStateChangeCb = options.events.onStateChange
    MockYoutubePlayer.instances.push(this)
  }

  getCurrentTime() {
    return this.currentTime
  }
  playVideo() {}
  pauseVideo() {}
  seekTo(seconds: number) {
    this.seekCalls.push(seconds)
    this.currentTime = seconds
  }
  destroy() {}

  triggerReady() {
    this.onReadyCb()
  }
  triggerStateChange(data: number) {
    this.onStateChangeCb({ data })
  }
}

function mountHarness(videoId = 'abc123') {
  let api!: ReturnType<typeof useYoutubePlayer>
  const Harness = defineComponent({
    setup() {
      api = useYoutubePlayer('yt-player', videoId)
      return () => null
    },
  })
  const wrapper = mount(Harness)
  return { wrapper, api }
}

describe('useYoutubePlayer resume-seek reliability', () => {
  beforeEach(() => {
    MockYoutubePlayer.instances = []
    ;(window as unknown as { YT: unknown }).YT = { Player: MockYoutubePlayer }
  })

  it('re-applies a seek requested before ready once real playback actually starts', async () => {
    const { api } = mountHarness()
    await nextTick()
    await nextTick()

    const player = MockYoutubePlayer.instances[0]
    expect(player).toBeDefined()

    // WatchView calls this as soon as it knows the resume position - before the player has
    // even been constructed/become ready.
    api.seekTo(45000)
    expect(player.seekCalls).toEqual([])

    player.triggerReady()
    // onReady applies the pending seek immediately once...
    expect(player.seekCalls).toEqual([45])

    // ...but that seek can be silently dropped by the real IFrame API before the video has
    // actually started producing frames - simulate the real player's position not having
    // actually moved despite the call above.
    player.currentTime = 0

    // A non-PLAYING transition (e.g. buffering) must not re-trigger anything.
    player.triggerStateChange(STATE_BUFFERING)
    expect(player.seekCalls).toEqual([45])

    // The first genuine PLAYING transition re-applies the still-pending seek - this is what
    // makes "continue watching" actually land on the right spot once the user presses play.
    player.triggerStateChange(STATE_PLAYING)
    expect(player.seekCalls).toEqual([45, 45])
    expect(player.currentTime).toBe(45)

    // Once confirmed, later play/pause cycles must not replay a stale resume seek.
    player.triggerStateChange(STATE_PAUSED)
    player.triggerStateChange(STATE_PLAYING)
    expect(player.seekCalls).toEqual([45, 45])
  })

  it('applies seekTo immediately when the player is already ready', async () => {
    const { api } = mountHarness()
    await nextTick()
    await nextTick()
    const player = MockYoutubePlayer.instances[0]
    player.triggerReady()

    api.seekTo(12000)
    expect(player.seekCalls).toEqual([12])
  })

  it('a normal line-click seek is unaffected by the pending-confirmation mechanism', async () => {
    const { api } = mountHarness()
    await nextTick()
    await nextTick()
    const player = MockYoutubePlayer.instances[0]
    player.triggerReady()

    api.seekTo(5000)
    api.seekTo(9000)
    expect(player.seekCalls).toEqual([5, 9])

    player.triggerStateChange(STATE_PLAYING)
    expect(player.seekCalls).toEqual([5, 9, 9])
  })
})
