<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/admin.api'
import { toApiError } from '@/api/errors'
import AppModal from '@/components/common/AppModal.vue'
import type {
  AdminDashboardOverviewResponse,
  AdminDashboardRange,
  AdminSystemHealthResponse,
  DailyCountDto,
} from '@/types/api'

const range = ref<AdminDashboardRange>('7d')
const isLoading = ref(true)
const overview = ref<AdminDashboardOverviewResponse | null>(null)
const health = ref<AdminSystemHealthResponse | null>(null)

const alertModal = ref({ show: false, title: 'Cảnh báo', message: '' })

const ranges: { value: AdminDashboardRange; label: string }[] = [
  { value: '24h', label: '24 giờ' },
  { value: '7d', label: '7 ngày' },
  { value: '30d', label: '30 ngày' },
  { value: 'all', label: 'Toàn thời gian' },
]

const maxDailyVideos = computed(() => maxOfSeries(overview.value?.dailyVideos ?? []))
const maxDailyFailedJobs = computed(() => maxOfSeries(overview.value?.dailyFailedJobs ?? []))

function maxOfSeries(items: DailyCountDto[]): number {
  let max = 0
  for (const item of items) {
    if (item.count > max) max = item.count
  }
  return Math.max(max, 1)
}

function barPercent(count: number, max: number): number {
  if (count <= 0) return 0
  return Math.max(6, Math.round((count / max) * 100))
}

function shortDate(isoDate: string): string {
  const parts = isoDate.split('-')
  if (parts.length !== 3) return isoDate
  return `${parts[2]}/${parts[1]}`
}

function statusBadgeClass(status: string): string {
  if (status === 'UP') return 'health-badge--up'
  if (status === 'DEGRADED') return 'health-badge--degraded'
  return 'health-badge--down'
}

function jobTypeLabel(type: string): string {
  switch (type) {
    case 'INGEST_VIDEO':
      return 'Nạp video (yt-dlp + NLP)'
    case 'TRANSLATE_SUBTITLES':
      return 'Dịch phụ đề'
    case 'RETRANSLATE_SUBTITLES':
      return 'Dịch lại phụ đề'
    default:
      return type
  }
}

async function loadData() {
  isLoading.value = true
  try {
    const [overviewData, healthData] = await Promise.all([
      adminApi.getDashboardOverview(range.value),
      adminApi.getSystemHealth(),
    ])
    overview.value = overviewData
    health.value = healthData
  } catch (error) {
    alertModal.value = {
      show: true,
      title: 'Không tải được Dashboard',
      message: toApiError(error).message,
    }
  } finally {
    isLoading.value = false
  }
}

function onRangeChange(nextRange: AdminDashboardRange) {
  if (range.value === nextRange) return
  range.value = nextRange
  void loadData()
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <div class="admin-dashboard">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
      <div>
        <h1 class="h4 fw-bold mb-1">Tổng quan &amp; Sức khỏe hệ thống</h1>
        <p class="text-muted small mb-0">Theo dõi hoạt động người dùng, pipeline xử lý video và kết nối hạ tầng.</p>
      </div>

      <div class="d-flex align-items-center gap-2 flex-wrap">
        <div class="btn-group btn-group-sm" role="group" aria-label="Khoảng thời gian">
          <button
            v-for="item in ranges"
            :key="item.value"
            type="button"
            class="btn"
            :class="range === item.value ? 'btn-primary' : 'btn-outline-secondary'"
            @click="onRangeChange(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
        <button class="btn btn-sm btn-outline-primary" type="button" :disabled="isLoading" @click="loadData">
          {{ isLoading ? 'Đang tải...' : 'Làm mới' }}
        </button>
      </div>
    </div>

    <div v-if="isLoading && !overview" class="text-center py-5">
      <div class="spinner-border text-primary" role="status"></div>
    </div>

    <template v-else>
      <div v-if="overview" class="kpi-grid mb-4">
        <div class="card kpi-card">
          <div class="card-body">
            <div class="kpi-label">Người dùng</div>
            <div class="kpi-value">{{ overview.users.total }}</div>
            <div class="kpi-meta">
              <span class="text-success fw-semibold">{{ overview.users.active }} hoạt động</span>
              · <span class="text-danger fw-semibold">{{ overview.users.locked }} bị khóa</span>
              · <span>{{ overview.users.admins }} Admin</span>
            </div>
            <div class="kpi-sub mt-1">
              Mới: <strong>{{ overview.users.newInRange }}</strong> · Hoạt động: <strong>{{ overview.users.activeInRange }}</strong>
            </div>
          </div>
        </div>

        <div class="card kpi-card">
          <div class="card-body">
            <div class="kpi-label">Video hệ thống</div>
            <div class="kpi-value">{{ overview.videos.total }}</div>
            <div class="kpi-meta">
              <span class="text-success fw-semibold">{{ overview.videos.ready }} sẵn sàng</span>
              · <span class="text-warning fw-semibold">{{ overview.videos.pending + overview.videos.processing }} đang xử lý</span>
              · <span class="text-danger fw-semibold">{{ overview.videos.failed }} lỗi</span>
            </div>
            <div class="kpi-sub mt-1">Tạo mới trong kỳ: <strong>{{ overview.videos.createdInRange }}</strong></div>
          </div>
        </div>

        <div class="card kpi-card">
          <div class="card-body">
            <div class="kpi-label">Processing Jobs</div>
            <div class="kpi-value">{{ overview.jobs.totalInRange }}</div>
            <div class="kpi-meta">
              <span class="text-success fw-semibold">{{ overview.jobs.completedInRange }} thành công</span>
              · <span class="text-danger fw-semibold">{{ overview.jobs.failedInRange }} lỗi</span>
              · <span>{{ overview.jobs.pending + overview.jobs.processing }} đang chạy</span>
            </div>
            <div class="kpi-sub mt-1">Tỉ lệ thành công: <strong>{{ overview.jobs.successRatePercent }}%</strong></div>
          </div>
        </div>

        <div class="card kpi-card">
          <div class="card-body">
            <div class="kpi-label">Cache AI &amp; Từ điển</div>
            <div class="kpi-value">{{ overview.cacheAndDictionary.totalDictionaryEntries }}</div>
            <div class="kpi-meta">
              <span>Từ vựng người dùng đã lưu</span>
            </div>
            <div class="kpi-sub mt-1">
              Cache READY: <strong class="text-success">{{ overview.cacheAndDictionary.wordCacheReady }}</strong>
              · FAILED: <strong class="text-danger">{{ overview.cacheAndDictionary.wordCacheFailed }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div v-if="health" class="card mb-4">
        <div class="card-body">
          <h2 class="h6 fw-bold mb-3">Sức khỏe hạ tầng &amp; dịch vụ phụ thuộc</h2>
          <div class="health-grid">
            <div class="health-item">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="fw-bold">MySQL Database</span>
                <span class="health-badge" :class="statusBadgeClass(health.mysql.status)">{{ health.mysql.status }}</span>
              </div>
              <div class="small text-muted">{{ health.mysql.message }}</div>
            </div>

            <div class="health-item">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="fw-bold">RabbitMQ</span>
                <span class="health-badge" :class="statusBadgeClass(health.rabbitmq.status)">{{ health.rabbitmq.status }}</span>
              </div>
              <div class="small text-muted mb-1">{{ health.rabbitmq.message }}</div>
              <div class="d-flex gap-2 flex-wrap small">
                <span class="badge text-bg-light border">jobs: {{ health.rabbitmq.videoJobs }}</span>
                <span class="badge text-bg-light border">retry: {{ health.rabbitmq.retryJobs }}</span>
                <span class="badge text-bg-light border">dlq: {{ health.rabbitmq.dlqJobs }}</span>
              </div>
            </div>

            <div class="health-item">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="fw-bold">yt-dlp</span>
                <span class="health-badge" :class="statusBadgeClass(health.ytDlp.status)">{{ health.ytDlp.status }}</span>
              </div>
              <div class="small text-muted">{{ health.ytDlp.message }}</div>
              <div class="small mt-1">
                Phiên bản: <code>{{ health.ytDlp.version || 'N/A' }}</code>
                · Cookies: <strong>{{ health.ytDlp.cookiesConfigured ? 'Đã nạp' : 'Chưa nạp' }}</strong>
              </div>
            </div>

            <div class="health-item">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="fw-bold">Google Gemini API</span>
                <span class="health-badge" :class="statusBadgeClass(health.gemini.status)">{{ health.gemini.status }}</span>
              </div>
              <div class="small text-muted">{{ health.gemini.message }}</div>
              <div class="small mt-1">
                Model: <code>{{ health.gemini.model }}</code>
                · Lỗi 24h: <strong>{{ health.gemini.translationFailuresLast24h }}</strong>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="overview" class="charts-grid mb-4">
        <div class="card">
          <div class="card-body">
            <h2 class="h6 fw-bold mb-3">Video tạo mới theo ngày (UTC)</h2>
            <div class="bar-chart">
              <div
                v-for="point in overview.dailyVideos"
                :key="point.date"
                class="bar-chart__col"
                :title="`${point.date}: ${point.count} video`"
              >
                <div class="bar-chart__value">{{ point.count }}</div>
                <div class="bar-chart__track">
                  <div
                    class="bar-chart__fill bar-chart__fill--green"
                    :style="{ height: `${barPercent(point.count, maxDailyVideos)}%` }"
                  ></div>
                </div>
                <div class="bar-chart__label">{{ shortDate(point.date) }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h2 class="h6 fw-bold mb-3">Job thất bại theo ngày (UTC)</h2>
            <div class="bar-chart">
              <div
                v-for="point in overview.dailyFailedJobs"
                :key="point.date"
                class="bar-chart__col"
                :title="`${point.date}: ${point.count} job lỗi`"
              >
                <div class="bar-chart__value">{{ point.count }}</div>
                <div class="bar-chart__track">
                  <div
                    class="bar-chart__fill bar-chart__fill--red"
                    :style="{ height: `${barPercent(point.count, maxDailyFailedJobs)}%` }"
                  ></div>
                </div>
                <div class="bar-chart__label">{{ shortDate(point.date) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="overview" class="row g-3">
        <div class="col-lg-6">
          <div class="card h-100">
            <div class="card-body">
              <h2 class="h6 fw-bold mb-3">Số lượng Job theo loại</h2>
              <table class="table table-sm mb-0">
                <thead>
                  <tr>
                    <th>Loại Job</th>
                    <th class="text-end">Số lượng</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(count, type) in overview.jobs.byTypeInRange" :key="type">
                    <td>{{ jobTypeLabel(String(type)) }}</td>
                    <td class="text-end fw-semibold">{{ count }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="col-lg-6">
          <div class="card h-100">
            <div class="card-body">
              <h2 class="h6 fw-bold mb-3">Phân bố nguyên nhân lỗi (FailureReason)</h2>
              <div v-if="overview.failureReasons.length === 0" class="text-muted small py-2">
                Không có job lỗi trong khoảng thời gian đã chọn.
              </div>
              <table v-else class="table table-sm mb-0">
                <thead>
                  <tr>
                    <th>Mã lỗi</th>
                    <th class="text-end">Số lần</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in overview.failureReasons" :key="item.reason">
                    <td><code>{{ item.reason }}</code></td>
                    <td class="text-end fw-semibold text-danger">{{ item.count }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </template>

    <AppModal
      :show="alertModal.show"
      :title="alertModal.title"
      :message="alertModal.message"
      type="alert"
      variant="danger"
      @close="alertModal.show = false"
    />
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.kpi-label {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--brand-muted);
  letter-spacing: 0.03em;
}

.kpi-value {
  font-size: 1.85rem;
  font-weight: 800;
  color: var(--brand-ink);
  line-height: 1.2;
  margin: 0.2rem 0;
}

.kpi-meta,
.kpi-sub {
  font-size: 0.82rem;
  color: var(--brand-muted);
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 1rem;
}

.health-item {
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-sm);
  padding: 0.85rem;
  background: var(--brand-bg);
}

.health-badge {
  font-size: 0.72rem;
  font-weight: 800;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
}

.health-badge--up {
  background: #dcfce7;
  color: #15803d;
}

.health-badge--degraded {
  background: #fef3c7;
  color: #b45309;
}

.health-badge--down {
  background: #fee2e2;
  color: #b91c1c;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1rem;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 150px;
  padding-top: 0.5rem;
  overflow-x: auto;
}

.bar-chart__col {
  flex: 1 0 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.bar-chart__value {
  font-size: 0.68rem;
  color: var(--brand-muted);
  margin-bottom: 2px;
}

.bar-chart__track {
  flex: 1;
  width: 100%;
  max-width: 24px;
  background: #f1f5f9;
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}

.bar-chart__fill {
  width: 100%;
  border-radius: 4px 4px 0 0;
  transition: height 0.2s ease;
}

.bar-chart__fill--green {
  background: var(--brand-green);
}

.bar-chart__fill--red {
  background: #ef4444;
}

.bar-chart__label {
  font-size: 0.64rem;
  color: var(--brand-muted);
  margin-top: 4px;
  white-space: nowrap;
}
</style>
