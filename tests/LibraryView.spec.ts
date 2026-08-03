import { flushPromises, mount } from '@vue/test-utils'
import LibraryView from '../FE/src/views/LibraryView.vue'
import { videoApi } from '../FE/src/api/video.api'

vi.mock('../FE/src/api/video.api', () => ({
  videoApi: {
    getLibrary: vi.fn(),
  },
}))

describe('LibraryView', () => {
  it('shows an empty state when the user has no ready videos', async () => {
    vi.mocked(videoApi.getLibrary).mockResolvedValue({
      page: 0,
      size: 20,
      totalElements: 0,
      videos: [],
    })

    const wrapper = mount(LibraryView, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Thư viện đang trống')
    expect(wrapper.text()).toContain('Học với video đầu tiên')
  })
})
