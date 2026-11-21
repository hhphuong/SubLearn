<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { adminApi } from '@/api/admin.api'
import { toApiError } from '@/api/errors'
import { useAuthStore } from '@/stores/auth.store'
import AppModal from '@/components/common/AppModal.vue'
import type { AdminUserDetailResponse, UserRole, UserStatus } from '@/types/api'

const route = useRoute()
const authStore = useAuthStore()

const userId = computed(() => Number(route.params.userId))
const detail = ref<AdminUserDetailResponse | null>(null)
const isLoading = ref(true)
const isActionBusy = ref(false)

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

const isSelf = computed(() => authStore.user?.userId === detail.value?.id)

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

async function loadDetail() {
  isLoading.value = true
  try {
    detail.value = await adminApi.getUserDetail(userId.value)
  } catch (error) {
    openAlert(toApiError(error).message, 'Không tải được chi tiết người dùng', 'danger')
  } finally {
    isLoading.value = false
  }
}

function promptToggleStatus() {
  if (!detail.value) return
  const user = detail.value
  const locking = user.status === 'ACTIVE'
  const nextStatus: UserStatus = locking ? 'LOCKED' : 'ACTIVE'
  openConfirm(
    locking ? 'Xác nhận khóa tài khoản' : 'Xác nhận mở khóa tài khoản',
    locking
      ? `Khóa tài khoản ${user.email}? Toàn bộ phiên đăng nhập của người dùng này sẽ bị thu hồi.`
      : `Mở khóa tài khoản ${user.email}?`,
    locking ? 'Khóa tài khoản' : 'Mở khóa',
    locking ? 'danger' : 'warning',
    async () => {
      await adminApi.updateUserStatus(user.id, nextStatus)
      modal.value.show = false
      await loadDetail()
    },
  )
}

function promptToggleRole() {
  if (!detail.value) return
  const user = detail.value
  const nextRole: UserRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  openConfirm(
    'Xác nhận thay đổi vai trò',
    `Đổi vai trò của ${user.email} từ ${user.role} sang ${nextRole}?`,
    'Xác nhận',
    'warning',
    async () => {
      await adminApi.updateUserRole(user.id, nextRole)
      modal.value.show = false
      await loadDetail()
    },
  )
}

function promptRevokeSessions() {
  if (!detail.value) return
  const user = detail.value
  openConfirm(
    'Đăng xuất toàn bộ thiết bị',
    `Thu hồi toàn bộ phiên đăng nhập của ${user.email}?`,
    'Thu hồi phiên',
    'warning',
    async () => {
      const res = await adminApi.revokeUserSessions(user.id)
      modal.value.show = false
      await loadDetail()
      openAlert(`Đã thu hồi ${res.revokedCount} phiên đăng nhập.`, 'Thành công', 'info')
    },
  )
}

function promptSendPasswordReset() {
  if (!detail.value) return
  const user = detail.value
  openConfirm(
    'Gửi link đặt lại mật khẩu',
    `Gửi email đặt lại mật khẩu tới ${user.email}?`,
    'Gửi email',
    'warning',
    async () => {
      await adminApi.sendPasswordReset(user.id)
      modal.value.show = false
      openAlert(`Đã gửi email đặt lại mật khẩu tới ${user.email}.`, 'Thành công', 'info')
    },
  )
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN')
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <div class="admin-user-detail">
    <div class="mb-3">
      <RouterLink class="text-decoration-none small fw-semibold" to="/admin/users">
        ← Quay lại danh sách người dùng
      </RouterLink>
    </div>

    <div v-if="isLoading" class="text-center py-5">
      <div class="spinner-border text-primary" role="status"></div>
    </div>

    <template v-else-if="detail">
      <div class="card mb-3">
        <div class="card-body d-flex flex-wrap justify-content-between align-items-start gap-3">
          <div>
            <div class="d-flex align-items-center gap-2 flex-wrap mb-1">
              <h1 class="h4 fw-bold mb-0">{{ detail.email }}</h1>
              <span class="badge" :class="detail.role === 'ADMIN' ? 'text-bg-primary' : 'text-bg-secondary'">
                {{ detail.role }}
              </span>
              <span class="badge" :class="detail.status === 'ACTIVE' ? 'text-bg-success' : 'text-bg-danger'">
                {{ detail.status }}
              </span>
            </div>
            <div class="text-muted small">
              Tên hiển thị: <strong>{{ detail.displayName || '—' }}</strong>
              · Tạo lúc: {{ formatDate(detail.createdAt) }}
              · Cập nhật: {{ formatDate(detail.updatedAt) }}
            </div>
          </div>

          <div class="d-flex gap-2 flex-wrap">
            <button
              type="button"
              class="btn btn-sm"
              :class="detail.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-outline-success'"
              :disabled="isSelf && detail.status === 'ACTIVE'"
              @click="promptToggleStatus"
            >
              {{ detail.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa' }}
            </button>
            <button
              type="button"
              class="btn btn-sm btn-outline-primary"
              :disabled="isSelf"
              @click="promptToggleRole"
            >
              {{ detail.role === 'ADMIN' ? 'Hạ xuống USER' : 'Nâng lên ADMIN' }}
            </button>
            <button type="button" class="btn btn-sm btn-outline-secondary" @click="promptRevokeSessions">
              Đăng xuất thiết bị
            </button>
            <button type="button" class="btn btn-sm btn-outline-warning" @click="promptSendPasswordReset">
              Gửi link đặt lại MK
            </button>
          </div>
        </div>
      </div>

      <div class="stat-grid mb-3">
        <div class="card">
          <div class="card-body">
            <div class="small text-muted fw-semibold">Phiên đang hoạt động</div>
            <div class="h4 fw-bold mb-0">{{ detail.activeSessionCount }}</div>
          </div>
        </div>
        <div class="card">
          <div class="card-body">
            <div class="small text-muted fw-semibold">Video trong thư viện</div>
            <div class="h4 fw-bold mb-0">{{ detail.libraryVideoCount }}</div>
          </div>
        </div>
        <div class="card">
          <div class="card-body">
            <div class="small text-muted fw-semibold">Video đã submit lần đầu</div>
            <div class="h4 fw-bold mb-0">{{ detail.submittedVideoCount }}</div>
          </div>
        </div>
        <div class="card">
          <div class="card-body">
            <div class="small text-muted fw-semibold">Từ vựng / Folder</div>
            <div class="h4 fw-bold mb-0">{{ detail.dictionaryEntryCount }} / {{ detail.dictionaryFolderCount }}</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <h2 class="h6 fw-bold mb-3">Lịch sử xem gần đây (tối đa 10 video)</h2>
          <div v-if="detail.recentVideos.length === 0" class="text-muted small">Người dùng chưa xem video nào.</div>
          <div v-else class="table-responsive">
            <table class="table table-sm align-middle mb-0 small">
              <thead>
                <tr>
                  <th>Video</th>
                  <th>YouTube ID</th>
                  <th>Ngôn ngữ</th>
                  <th>Xem gần nhất</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="v in detail.recentVideos" :key="v.videoId">
                  <td>{{ v.title || `Video #${v.videoId}` }}</td>
                  <td><code>{{ v.youtubeVideoId }}</code></td>
                  <td>{{ v.sourceLanguage || '—' }}</td>
                  <td>{{ formatDate(v.lastWatchedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </template>

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

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}
</style>
