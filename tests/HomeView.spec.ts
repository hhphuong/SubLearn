import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import HomeView from '../FE/src/views/HomeView.vue'

describe('HomeView', () => {
  it('renders the video submission form without throwing', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: HomeView },
        { path: '/watch/:videoId', name: 'watch', component: { template: '<div />' } },
      ],
    })
    router.push('/')
    await router.isReady()

    const wrapper = mount(HomeView, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn }), router],
      },
    })

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('SubLearn')
    expect(wrapper.find('#youtubeUrl').exists()).toBe(true)
    expect(wrapper.find('#targetLanguage').exists()).toBe(true)
  })
})
