import { mount } from '@vue/test-utils'
import HomeView from '../FE/src/views/HomeView.vue'

describe('HomeView', () => {
  it('renders without throwing and shows the success confirmation', () => {
    const wrapper = mount(HomeView)
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('SubLearn')
  })
})
