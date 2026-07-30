import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import DictionaryView from '../FE/src/views/DictionaryView.vue'

vi.mock('../FE/src/api/dictionary.api', () => ({
  dictionaryApi: {
    list: vi.fn(),
    getDetail: vi.fn(),
    remove: vi.fn(),
  },
}))

import { dictionaryApi } from '../FE/src/api/dictionary.api'

const sampleEntry = {
  id: 1,
  displayWord: 'run',
  sourceLanguage: 'en',
  targetLanguage: 'vi',
  contextualMeaning: 'to jog',
  dictionaryMeaning: 'to move fast on foot',
  examples: ['She runs daily.'],
  contextCount: 1,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function mountView() {
  return mount(DictionaryView, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn, stubActions: false })],
      stubs: { RouterLink: true },
    },
  })
}

describe('DictionaryView', () => {
  beforeEach(() => {
    vi.mocked(dictionaryApi.list).mockResolvedValue({
      page: 0,
      size: 20,
      totalElements: 1,
      entries: [sampleEntry],
    })
  })

  it('renders saved dictionary entries returned by the API', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('run')
    expect(wrapper.text()).toContain('to jog')
    expect(dictionaryApi.list).toHaveBeenCalled()
  })

  it('deletes an entry via the API and removes it from the list', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(dictionaryApi.remove).mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button.btn-outline-danger').trigger('click')
    await flushPromises()

    expect(dictionaryApi.remove).toHaveBeenCalledWith(1)
    expect(wrapper.text()).not.toContain('run')
  })
})
