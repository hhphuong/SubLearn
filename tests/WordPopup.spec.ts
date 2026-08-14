import { mount, flushPromises } from '@vue/test-utils'
import WordPopup from '../FE/src/components/watch/WordPopup.vue'

vi.mock('../FE/src/api/word.api', () => ({
  wordApi: { lookup: vi.fn() },
}))
vi.mock('../FE/src/api/dictionary.api', () => ({
  dictionaryApi: { save: vi.fn() },
}))

import { wordApi } from '../FE/src/api/word.api'

const baseProps = {
  word: 'run',
  sentenceContext: 'I run every morning.',
  sourceLanguage: 'en',
  targetLanguage: 'vi',
  videoId: 1,
  subtitleLineId: 10,
}

describe('WordPopup', () => {
  it('shows a loading spinner before the meaning arrives, then renders the meaning on success', async () => {
    let resolveLookup: (value: unknown) => void = () => {}
    vi.mocked(wordApi.lookup).mockReturnValue(
      new Promise((resolve) => {
        resolveLookup = resolve
      }) as never,
    )

    const wrapper = mount(WordPopup, { props: baseProps })

    expect(wrapper.find('.spinner-border').exists()).toBe(true)

    resolveLookup({
      word: 'run',
      contextualMeaning: 'to jog',
      dictionaryMeaning: 'to move fast on foot',
      examples: ['She runs daily.'],
      cached: false,
      error: null,
    })
    await flushPromises()

    expect(wrapper.find('.spinner-border').exists()).toBe(false)
    expect(wrapper.text()).toContain('to jog')
    expect(wrapper.text()).toContain('to move fast on foot')
    expect(wrapper.text()).toContain('She runs daily.')
  })

  it('shows an error state when the response has HTTP 200 but an error field (Gemini unavailable)', async () => {
    vi.mocked(wordApi.lookup).mockResolvedValue({
      word: 'run',
      contextualMeaning: null,
      dictionaryMeaning: null,
      examples: [],
      cached: false,
      error: 'TRANSLATION_SERVICE_UNAVAILABLE',
    })

    const wrapper = mount(WordPopup, { props: baseProps })
    await flushPromises()

    expect(wrapper.find('.spinner-border').exists()).toBe(false)
    expect(wrapper.text()).toContain('Thử lại')
    expect(wrapper.text()).not.toContain('null')
  })
})
