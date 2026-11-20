import { defineComponent, onMounted } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import WatchView from '../FE/src/views/WatchView.vue'

vi.mock('../FE/src/api/video.api', () => ({
  videoApi: {
    getSummary: vi.fn(),
    recordWatch: vi.fn(),
    updateProgress: vi.fn(),
  },
}))
vi.mock('../FE/src/api/subtitle.api', () => ({
  subtitleApi: { getSubtitles: vi.fn() },
}))

import { videoApi } from '../FE/src/api/video.api'
import { subtitleApi } from '../FE/src/api/subtitle.api'

const seekToSpy = vi.fn()
// Emits 'ready' as soon as it mounts, mirroring the real YoutubePlayer's own `ready` event
// (see YoutubePlayer.vue's `watch(isReady, ...)`) that WatchView's onPlayerReady listens for.
const YoutubePlayerStub = defineComponent({
  props: ['videoId'],
  emits: ['ready', 'timeupdate'],
  setup(_, { expose, emit }) {
    expose({ play: vi.fn(), pause: vi.fn(), seekTo: seekToSpy })
    onMounted(() => emit('ready'))
    return () => null
  },
})

function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/watch/:videoId', name: 'watch', component: WatchView, props: true },
    ],
  })
  router.push({ name: 'watch', params: { videoId: '13' }, query: { lang: 'vi' } })

  return mount(WatchView, {
    props: { videoId: '13' },
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn, stubActions: false }), router],
      stubs: { YoutubePlayer: YoutubePlayerStub },
    },
  })
}

describe('WatchView resume-to-exact-position', () => {
  beforeEach(() => {
    seekToSpy.mockClear()
    vi.mocked(videoApi.recordWatch).mockResolvedValue(undefined)
    vi.mocked(videoApi.updateProgress).mockResolvedValue(undefined)
    vi.mocked(subtitleApi.getSubtitles).mockResolvedValue({
      page: 0,
      size: 50,
      totalLines: 0,
      sourceLanguage: 'en',
      targetLanguage: 'vi',
      sameLanguage: false,
      lines: [],
      translationAvailable: true,
      activeTranslationJob: null,
    })
  })

  function mockSummary(lastPositionMs: number) {
    vi.mocked(videoApi.getSummary).mockResolvedValue({
      videoId: 13,
      title: 'Culture shock podcast',
      sourceLanguage: 'en',
      durationSeconds: 500,
      status: 'READY',
      youtubeVideoId: 'cs4AREZXkgE',
      lastPositionMs,
    })
  }

  it('seeks to the resumed position once the player actually mounts and fires ready', async () => {
    // Regression test: the player used to stay unmounted (isLoading never flipped to false
    // before the seek attempt), so playerRef was always null and the seek silently no-op'd.
    mockSummary(45000)
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(wrapper.findComponent(YoutubePlayerStub).exists()).toBe(true)
    expect(seekToSpy).toHaveBeenCalledWith(45000)
  })

  it('does not attempt a seek when the server has no prior position for this video', async () => {
    mockSummary(0)
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(wrapper.findComponent(YoutubePlayerStub).exists()).toBe(true)
    expect(seekToSpy).not.toHaveBeenCalled()
  })

  it('always resumes from whatever position the server reports for this video, not a leftover client value', async () => {
    // The resume position now comes fresh from getSummary (per videoId, server-side) on every
    // mount rather than a client-cached value, so there's no longer a way for a stale position
    // from a different video to bleed in - this confirms a second, updated fetch is honored.
    mockSummary(12000)
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()
    expect(seekToSpy).toHaveBeenCalledWith(12000)

    seekToSpy.mockClear()
    mockSummary(98000)
    wrapper.unmount()
    mountView()
    await flushPromises()
    await flushPromises()
    expect(seekToSpy).toHaveBeenCalledWith(98000)
  })
})
