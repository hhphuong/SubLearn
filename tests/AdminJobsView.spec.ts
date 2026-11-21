import { flushPromises, mount } from '@vue/test-utils'
import AdminJobsView from '../FE/src/views/admin/AdminJobsView.vue'
import { adminApi } from '../FE/src/api/admin.api'

vi.mock('../FE/src/api/admin.api', () => ({
  adminApi: {
    listJobs: vi.fn(),
    getQueueStats: vi.fn(),
    retryJob: vi.fn(),
    bulkRetryJobs: vi.fn(),
    forceFailJob: vi.fn(),
    purgeDlq: vi.fn(),
  },
}))

describe('AdminJobsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('warns via AppModal when bulk retry exceeds limit and purges DLQ after confirmation', async () => {
    vi.mocked(adminApi.getQueueStats).mockResolvedValue({
      reachable: true,
      videoJobs: 1,
      retryJobs: 0,
      dlqJobs: 2,
      bulkRetryMaxBatch: 1,
    })
    vi.mocked(adminApi.listJobs).mockResolvedValue({
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      jobs: [
        {
          jobId: 10,
          videoId: 5,
          youtubeVideoId: 'yt1',
          videoTitle: 'Video 1',
          requestedByUserId: 2,
          requestedByEmail: 'u@example.com',
          jobType: 'INGEST_VIDEO',
          status: 'FAILED',
          sourceLanguage: 'en',
          targetLanguage: 'vi',
          retryCount: 5,
          maxRetries: 5,
          progressPercent: 20,
          failureReason: 'TRANSLATION_FAILED',
          failureMessage: 'Gemini 429',
          createdAt: '2026-11-20T00:00:00Z',
          updatedAt: '2026-11-20T00:00:00Z',
        },
        {
          jobId: 11,
          videoId: 6,
          youtubeVideoId: 'yt2',
          videoTitle: 'Video 2',
          requestedByUserId: 2,
          requestedByEmail: 'u@example.com',
          jobType: 'TRANSLATE_SUBTITLES',
          status: 'FAILED',
          sourceLanguage: 'en',
          targetLanguage: 'vi',
          retryCount: 5,
          maxRetries: 5,
          progressPercent: 10,
          failureReason: 'PROCESS_TIMEOUT',
          failureMessage: 'Timeout',
          createdAt: '2026-11-20T00:00:00Z',
          updatedAt: '2026-11-20T00:00:00Z',
        },
      ],
    })
    vi.mocked(adminApi.purgeDlq).mockResolvedValue({
      queueName: 'sublearn.video.jobs.dlq',
      purgedCount: 2,
    })

    const wrapper = mount(AdminJobsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Video 1')
    expect(wrapper.text()).toContain('dlq: 2')

    const selectAllCheckbox = wrapper.find('thead input[type="checkbox"]')
    await selectAllCheckbox.setValue(true)

    const bulkBtn = wrapper.findAll('button').find((b) => b.text().includes('Retry hàng loạt'))
    expect(bulkBtn).toBeDefined()
    await bulkBtn!.trigger('click')

    expect(wrapper.text()).toContain('Vượt giới hạn Bulk Retry')
    expect(adminApi.bulkRetryJobs).not.toHaveBeenCalled()

    await wrapper.find('.app-modal-footer .btn-primary').trigger('click')

    const purgeBtn = wrapper.findAll('button').find((b) => b.text().includes('Dọn DLQ'))
    await purgeBtn!.trigger('click')
    expect(wrapper.text()).toContain('Xác nhận dọn Dead Letter Queue')

    await wrapper.find('.app-modal-footer .btn-primary').trigger('click')
    await flushPromises()
    expect(adminApi.purgeDlq).toHaveBeenCalledTimes(1)
  })
})
