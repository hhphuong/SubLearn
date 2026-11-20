import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import HomeView from '../FE/src/views/HomeView.vue'

describe('HomeView', () => {
  function createTestRouter() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: HomeView },
        { path: '/watch/:videoId', name: 'watch', component: { template: '<div />' } },
      ],
    })
    router.push('/')
    return router
  }

  it('renders the video submission form without throwing', async () => {
    const router = createTestRouter()
    await router.isReady()

    const wrapper = mount(HomeView, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn }), router],
      },
    })

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('Tạo video')
    expect(wrapper.find('#youtubeUrl').exists()).toBe(true)
    expect(wrapper.find('#targetLanguage').exists()).toBe(true)
  })

  it('shows the unified error message when video creation fails', async () => {
    const router = createTestRouter()
    await router.isReady()

    const wrapper = mount(HomeView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              video: {
                status: 'FAILED',
                failureReason: 'TRANSLATION_FAILED',
              },
            },
          }),
          router,
        ],
      },
    })

    expect(wrapper.text()).toContain('có lỗi khi tạo video! vui lòng thử lại.')
  })
})
