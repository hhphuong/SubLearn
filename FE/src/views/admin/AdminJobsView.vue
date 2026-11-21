<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/admin.api'
import { toApiError } from '@/api/errors'
import AppModal from '@/components/common/AppModal.vue'
import type {
  AdminJobItemDto,
  FailureReason,
  JobStatus,
  JobType,
  RabbitQueueStatsResponse,
} from '@/types/api'

const statusFilter = ref<JobStatus | ''>('')
const jobTypeFilter = ref<JobType | ''>('')
const failureReasonFilter = ref<FailureReason | ''>('')
const videoIdFilter = ref('')
const searchFilter = ref('')
const fromDate = ref('')
const toDate = ref('')

const page = ref(0)
const size = 20

const jobs = ref<AdminJobItemDto[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const queueStats = ref<RabbitQueueStatsResponse | null>(null)
const selectedJobIds = ref<number[]>([])

const isLoading = ref(false)
const isActionBusy = ref(false)

const JOB_STATUSES: JobStatus[] = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']
const JOB_TYPES: JobType[] = ['INGEST_VIDEO', 'TRANSLATE_SUBTITLES', 'RETRANSLATE_SUBTITLES']
const FAILURE_REASONS: FailureReason[] = [
  'NO_CAPTIONS_AVAILABLE',
  'INVALID_URL',
  'VIDEO_UNAVAILABLE',
  'VIDEO_PRIVATE',
  'VIDEO_REGION_LOCKED',
  'VIDEO_AGE_RESTRICTED',
  'SOURCE_LANGUAGE_UNDETECTABLE',
  'TOKENIZATION_FAILED',
  'TRANSLATION_FAILED',
  'PROCESS_TIMEOUT',
  'UNKNOWN_ERROR',
]

interface ModalState {
  show: boolean
  title: string
  message: string
  type: 'confirm' | 'alert'
  variant: 'warning' | 'danger' | 'info'
  confirmText?: string
  cancelText?: string
  onConfirm?: () => Promise<void>
}

const modal = ref<ModalState>({
  show: false,
  title: 'Cảnh báo',
  message: '',
  type: 'alert',
  variant: 'warning',
})

const failedJobsOnPage = computed(() => jobs.value.filter((j) => j.status === 'FAILED'))

const allFailedOnPageSelected = computed(() => {
  if (failedJobsOnPage.value.length === 0) return false
  return failedJobsOnPage.value.every((j) => selectedJobIds.value.includes(j.jobId))
})

function openAlert(message: string, title = 'Thông báo', variant: 'warning' | 'danger' | 'info' = 'info') {
  modal.value = { show: true, title, message, type: 'alert', variant, confirmText: 'Đóng' }
}

function openConfirm(
  title: string,
  message: string,
  confirmText: string,
  variant: 'warning' | 'danger',
  onConfirm: () => Promise<void>,
) {
  modal.value = { show: true, title, message, type: 'confirm', variant, confirmText, cancelText: 'Hủy', onConfirm }
}

function closeModal() {
  if (!isActionBusy.value) modal.value.show = false
}

async function handleConfirm() {
  const cb = modal.value.onConfirm
  if (!cb) {
    modal.value.show = false
    return
  }
  isActionBusy.value = true
  try {
    await cb()
  } catch (error) {
    modal.value.show = false
    openAlert(toApiError(error).message, 'Cảnh báo lỗi', 'danger')
  } finally {
    isActionBusy.value = false
  }
}

async function loadQueues() {
  try {
    queueStats.value = await adminApi.getQueueStats()
  } catch {
    // Queue stats error will still leave previous state or null
  }
}

async function fetchJobs() {
  isLoading.value = true
  selectedJobIds.value = []
  try {
    const res = await adminApi.listJobs({
      status: statusFilter.value,
      jobType: jobTypeFilter.value,
      failureReason: failureReasonFilter.value,
      videoId: videoIdFilter.value,
      search: searchFilter.value,
      from: fromDate.value ? `${fromDate.value}T00:00:00Z` : undefined,
      to: toDate.value ? `${toDate.value}T23:59:59.999Z` : undefined,
      page: page.value,
      size,
    })
    jobs.value = res.jobs
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
  } catch (error) {
    openAlert(toApiError(error).message, 'Không tải được danh sách Job', 'danger')
  } finally {
    isLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([fetchJobs(), loadQueues()])
}

function onFilterSubmit() {
  page.value = 0
  void fetchJobs()
}

function onResetFilters() {
  statusFilter.value = ''
  jobTypeFilter.value = ''
  failureReasonFilter.value = ''
  videoIdFilter.value = ''
  searchFilter.value = ''
  fromDate.value = ''
  toDate.value = ''
  page.value = 0
  void fetchJobs()
}

function goToPage(nextPage: number) {
  if (nextPage < 0 || (totalPages.value > 0 && nextPage >= totalPages.value)) return
  page.value = nextPage
  void fetchJobs()
}

function toggleSelectAllFailed(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    selectedJobIds.value = failedJobsOnPage.value.map((j) => j.jobId)
  } else {
    selectedJobIds.value = []
  }
}

function toggleSelectJob(jobId: number) {
  if (selectedJobIds.value.includes(jobId)) {
    selectedJobIds.value = selectedJobIds.value.filter((id) => id !== jobId)
  } else {
    selectedJobIds.value = [...selectedJobIds.value, jobId]
  }
}

function promptRetry(job: AdminJobItemDto) {
  openConfirm(
    'Xác nhận thử lại Job',
    `Thử lại job #${job.jobId} (${job.jobType}) cho video "${job.videoTitle || job.youtubeVideoId}"?\nHệ thống sẽ giữ job cũ làm lịch sử và tạo một job mới đưa vào RabbitMQ.`,
    'Thử lại',
    'warning',
    async () => {
      const created = await adminApi.retryJob(job.jobId)
      modal.value.show = false
      await refreshAll()
      openAlert(`Đã tạo job mới #${created.jobId} và đưa vào hàng đợi xử lý.`, 'Thành công', 'info')
    },
  )
}

function promptBulkRetry() {
  const count = selectedJobIds.value.length
  if (count === 0) return
  const maxBatch = queueStats.value?.bulkRetryMaxBatch ?? 20
  if (count > maxBatch) {
    openAlert(
      `Bạn đang chọn ${count} job, vượt quá giới hạn tối đa ${maxBatch} job mỗi lượt Bulk Retry. Vui lòng bỏ chọn bớt để tránh quá tải API dịch thuật.`,
      'Vượt giới hạn Bulk Retry',
      'warning',
    )
    return
  }

  openConfirm(
    'Xác nhận Retry hàng loạt',
    `Thử lại ${count} job FAILED đã chọn? Mỗi job sẽ tạo một bản ghi mới trong hàng đợi.`,
    `Retry ${count} job`,
    'warning',
    async () => {
      const res = await adminApi.bulkRetryJobs(selectedJobIds.value)
      modal.value.show = false
      await refreshAll()
      openAlert(
        `Đã thử lại thành công ${res.retriedCount}/${res.requestedCount} job.`,
        'Kết quả Bulk Retry',
        'info',
      )
    },
  )
}

function promptForceFail(job: AdminJobItemDto) {
  openConfirm(
    'Xác nhận hủy Job đang chạy',
    `Đánh dấu FAILED thủ công cho job #${job.jobId} đang ở trạng thái ${job.status}?`,
    'Đánh dấu FAILED',
    'danger',
    async () => {
      await adminApi.forceFailJob(job.jobId)
      modal.value.show = false
      await refreshAll()
    },
  )
}

function promptPurgeDlq() {
  const count = queueStats.value?.dlqJobs ?? 0
  openConfirm(
    'Xác nhận dọn Dead Letter Queue',
    `Xóa toàn bộ ${count} message đang tồn trong hàng đợi sublearn.video.jobs.dlq?\nThao tác này không làm mất dữ liệu trong bảng processing_job.`,
    'Dọn DLQ',
    'danger',
    async () => {
      const res = await adminApi.purgeDlq()
      modal.value.show = false
      await loadQueues()
      openAlert(`Đã dọn ${res.purgedCount} message khỏi ${res.queueName}.`, 'Đã dọn DLQ', 'info')
    },
  )
}

function showFailureDetail(job: AdminJobItemDto) {
  openAlert(
    `Mã lỗi: ${job.failureReason || 'N/A'}\n\nChi tiết:\n${job.failureMessage || 'Không có thông báo lỗi.'}`,
    `Chi tiết lỗi Job #${job.jobId}`,
    'danger',
  )
}

function statusBadgeClass(status: JobStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'text-bg-success'
    case 'FAILED':
      return 'text-bg-danger'
    case 'PROCESSING':
      return 'text-bg-primary'
    default:
      return 'text-bg-warning'
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN')
}

onMounted(() => {
  void refreshAll()
})
</script>

<template>
  <div class="admin-jobs">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
      <div>
        <h1 class="h4 fw-bold mb-1">Quản lý Job &amp; Hàng đợi RabbitMQ</h1>
        <p class="text-muted small mb-0">
          Tổng số: <strong>{{ totalElements }}</strong> job · Quản trị DB-driven và giám sát tồn queue RabbitMQ.
        </p>
      </div>
      <button class="btn btn-sm btn-outline-primary" type="button" :disabled="isLoading" @click="refreshAll">
        Làm mới
      </button>
    </div>

    <div v-if="queueStats" class="card mb-3">
      <div class="card-body d-flex flex-wrap justify-content-between align-items-center gap-3">
        <div class="d-flex gap-3 flex-wrap align-items-center small">
          <span class="badge" :class="queueStats.reachable ? 'text-bg-success' : 'text-bg-danger'">
            RabbitMQ: {{ queueStats.reachable ? 'ONLINE' : 'OFFLINE' }}
          </span>
          <span>jobs: <strong>{{ queueStats.videoJobs }}</strong></span>
          <span>retry: <strong>{{ queueStats.retryJobs }}</strong></span>
          <span>dlq: <strong class="text-danger">{{ queueStats.dlqJobs }}</strong></span>
          <span class="text-muted">(Giới hạn Bulk Retry: tối đa {{ queueStats.bulkRetryMaxBatch }} job/lượt)</span>
        </div>

        <div class="d-flex gap-2">
          <button
            v-if="selectedJobIds.length > 0"
            type="button"
            class="btn btn-sm btn-primary"
            @click="promptBulkRetry"
          >
            Retry hàng loạt ({{ selectedJobIds.length }})
          </button>
          <button
            type="button"
            class="btn btn-sm btn-outline-danger"
            :disabled="!queueStats.reachable"
            @click="promptPurgeDlq"
          >
            Dọn DLQ (Purge)
          </button>
        </div>
      </div>
    </div>

    <div class="card mb-3">
      <div class="card-body">
        <form class="row g-2 align-items-end" @submit.prevent="onFilterSubmit">
          <div class="col-md-3">
            <label class="form-label small fw-semibold mb-1" for="jobSearch">Tìm kiếm</label>
            <input
              id="jobSearch"
              v-model="searchFilter"
              type="text"
              class="form-control form-control-sm"
              placeholder="Tiêu đề, YouTube ID, email..."
            />
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="jobStatus">Trạng thái</label>
            <select id="jobStatus" v-model="statusFilter" class="form-select form-select-sm">
              <option value="">Tất cả</option>
              <option v-for="st in JOB_STATUSES" :key="st" :value="st">{{ st }}</option>
            </select>
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="jobType">Loại Job</label>
            <select id="jobType" v-model="jobTypeFilter" class="form-select form-select-sm">
              <option value="">Tất cả</option>
              <option v-for="jt in JOB_TYPES" :key="jt" :value="jt">{{ jt }}</option>
            </select>
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="jobReason">Mã lỗi</label>
            <select id="jobReason" v-model="failureReasonFilter" class="form-select form-select-sm">
              <option value="">Tất cả</option>
              <option v-for="fr in FAILURE_REASONS" :key="fr" :value="fr">{{ fr }}</option>
            </select>
          </div>
          <div class="col-md-1">
            <label class="form-label small fw-semibold mb-1" for="jobVideoId">Video ID</label>
            <input
              id="jobVideoId"
              v-model="videoIdFilter"
              type="number"
              min="1"
              class="form-control form-control-sm"
              placeholder="#"
            />
          </div>
          <div class="col-md-2 d-flex gap-1">
            <button class="btn btn-sm btn-primary flex-fill" type="submit">Lọc</button>
            <button class="btn btn-sm btn-outline-secondary" type="button" @click="onResetFilters">Reset</button>
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="fromDate">Từ ngày</label>
            <input id="fromDate" v-model="fromDate" type="date" class="form-control form-control-sm" />
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="toDate">Đến ngày</label>
            <input id="toDate" v-model="toDate" type="date" class="form-control form-control-sm" />
          </div>
        </form>
      </div>
    </div>

    <div class="card">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0 small">
          <thead class="table-light">
            <tr>
              <th style="width: 34px">
                <input
                  type="checkbox"
                  class="form-check-input"
                  aria-label="Chọn tất cả job FAILED trên trang"
                  :disabled="failedJobsOnPage.length === 0"
                  :checked="allFailedOnPageSelected"
                  @change="toggleSelectAllFailed"
                />
              </th>
              <th>Job</th>
              <th>Video / Ngôn ngữ</th>
              <th>Trạng thái</th>
              <th>Lỗi</th>
              <th>Thời gian</th>
              <th class="text-end">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="isLoading">
              <td colspan="7" class="text-center py-4 text-muted">Đang tải danh sách job...</td>
            </tr>
            <tr v-else-if="jobs.length === 0">
              <td colspan="7" class="text-center py-4 text-muted">Không có job nào khớp điều kiện lọc.</td>
            </tr>
            <tr v-for="j in jobs" :key="j.jobId">
              <td>
                <input
                  type="checkbox"
                  class="form-check-input"
                  :disabled="j.status !== 'FAILED'"
                  :checked="selectedJobIds.includes(j.jobId)"
                  :aria-label="`Chọn job ${j.jobId}`"
                  @change="toggleSelectJob(j.jobId)"
                />
              </td>
              <td>
                <div class="fw-semibold">#{{ j.jobId }} · {{ j.jobType }}</div>
                <div class="text-muted">{{ j.requestedByEmail || 'System' }}</div>
              </td>
              <td>
                <div class="text-truncate" style="max-width: 240px" :title="j.videoTitle || j.youtubeVideoId">
                  {{ j.videoTitle || j.youtubeVideoId }}
                </div>
                <div class="text-muted">
                  #{{ j.videoId }} · <code>{{ j.youtubeVideoId }}</code>
                  <span v-if="j.targetLanguage"> → {{ j.targetLanguage }}</span>
                </div>
              </td>
              <td>
                <span class="badge" :class="statusBadgeClass(j.status)">{{ j.status }}</span>
                <div class="text-muted mt-1">{{ j.progressPercent }}% · retry {{ j.retryCount }}/{{ j.maxRetries }}</div>
              </td>
              <td>
                <template v-if="j.failureReason || j.failureMessage">
                  <div v-if="j.failureReason"><code>{{ j.failureReason }}</code></div>
                  <button
                    type="button"
                    class="btn btn-link btn-sm p-0 text-danger text-start"
                    @click="showFailureDetail(j)"
                  >
                    Xem chi tiết lỗi
                  </button>
                </template>
                <span v-else class="text-muted">—</span>
              </td>
              <td class="text-nowrap">{{ formatDate(j.createdAt) }}</td>
              <td class="text-end text-nowrap">
                <button
                  v-if="j.status === 'FAILED'"
                  type="button"
                  class="btn btn-sm btn-outline-primary"
                  @click="promptRetry(j)"
                >
                  Retry
                </button>
                <button
                  v-else-if="j.status === 'PENDING' || j.status === 'PROCESSING'"
                  type="button"
                  class="btn btn-sm btn-outline-danger"
                  @click="promptForceFail(j)"
                >
                  Force Fail
                </button>
                <span v-else class="text-muted">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 1" class="card-footer bg-white d-flex justify-content-between align-items-center">
        <span class="small text-muted">Trang {{ page + 1 }} / {{ totalPages }}</span>
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-secondary" type="button" :disabled="page === 0" @click="goToPage(page - 1)">
            Trước
          </button>
          <button
            class="btn btn-outline-secondary"
            type="button"
            :disabled="page + 1 >= totalPages"
            @click="goToPage(page + 1)"
          >
            Sau
          </button>
        </div>
      </div>
    </div>

    <AppModal
      :show="modal.show"
      :title="modal.title"
      :message="modal.message"
      :type="modal.type"
      :variant="modal.variant"
      :confirm-text="modal.confirmText"
      :cancel-text="modal.cancelText"
      :is-processing="isActionBusy"
      @confirm="handleConfirm"
      @cancel="closeModal"
      @close="closeModal"
    />
  </div>
</template>
