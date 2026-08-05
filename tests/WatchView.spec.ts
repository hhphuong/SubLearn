import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import WatchView from '../FE/src/views/WatchView.vue'

vi.mock('../FE/src/api/video.api', () => ({
  videoApi: {
    getSummary: vi.fn(),
    recordWatch: vi.fn(),
  },
}))
vi.mock('../FE/src/api/subtitle.api', () => ({
  subtitleApi: { getSubtitles: vi.fn() },
}))

import { videoApi } from '../FE/src/api/video.api'
import { subtitleApi } from '../FE/src/api/subtitle.api'

const seekToSpy = vi.fn()
const YoutubePlayerStub = defineComponent({
  props: ['videoId'],
  emits: ['ready', 'timeupdate'],
  setup(_, { expose }) {
    expose({ play: vi.fn(), pause: vi.fn(), seekTo: seekToSpy })
    return () => null
  },
})

function mountView(initialState: Record<string, unknown> = {}) {
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
      plugins: [createTestingPinia({ createSpy: vi.fn, stubActions: false, initialState }), router],
      stubs: { YoutubePlayer: YoutubePlayerStub },
    },
  })
}

describe('WatchView resume-to-exact-position', () => {
  beforeEach(() => {
    seekToSpy.mockClear()
    vi.mocked(videoApi.getSummary).mockResolvedValue({
      videoId: 13,
      title: 'Culture shock podcast',
      sourceLanguage: 'en',
      durationSeconds: 500,
      status: 'READY',
      youtubeVideoId: 'cs4AREZXkgE',
    })
    vi.mocked(videoApi.recordWatch).mockResolvedValue(undefined)
    vi.mocked(subtitleApi.getSubtitles).mockResolvedValue({
      page: 0,
      size: 50,
      totalLines: 0,
      sourceLanguage: 'en',
      targetLanguage: 'vi',
      sameLanguage: false,
      lines: [],
      translationUnavailable: false,
    })
  })

  it('seeks to the resumed position once the player actually mounts', async () => {
    // Regression test: the player used to stay unmounted (isLoading never flipped to false
    // before the seek attempt), so playerRef was always null and the seek silently no-op'd.
    const wrapper = mountView({
      watchSession: { videoId: 13, title: null, targetLanguage: 'vi', lastPlaybackTimeMs: 45000 },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.findComponent(YoutubePlayerStub).exists()).toBe(true)
    expect(seekToSpy).toHaveBeenCalledWith(45000)
  })

  it('does not attempt a seek when there is no prior session for this video', async () => {
    const wrapper = mountView({
      watchSession: { videoId: null, title: null, targetLanguage: null, lastPlaybackTimeMs: 0 },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.findComponent(YoutubePlayerStub).exists()).toBe(true)
    expect(seekToSpy).not.toHaveBeenCalled()
  })

  it('ignores a stale playback time left over from watching a different video', async () => {
    const wrapper = mountView({
      watchSession: { videoId: 99, title: null, targetLanguage: 'vi', lastPlaybackTimeMs: 45000 },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.findComponent(YoutubePlayerStub).exists()).toBe(true)
    expect(seekToSpy).not.toHaveBeenCalled()
  })
})
