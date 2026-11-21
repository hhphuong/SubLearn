<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { adminApi } from '@/api/admin.api'
import { toApiError } from '@/api/errors'
import { useAuthStore } from '@/stores/auth.store'
import AppModal from '@/components/common/AppModal.vue'
import type { AdminUserSummaryDto, UserRole, UserStatus } from '@/types/api'

const authStore = useAuthStore()

const search = ref('')
const roleFilter = ref<UserRole | ''>('')
const statusFilter = ref<UserStatus | ''>('')
const page = ref(0)
const size = 20

const users = ref<AdminUserSummaryDto[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const isLoading = ref(false)
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

function openAlert(message: string, title = 'Thông báo', variant: 'warning' | 'danger' | 'info' = 'info') {
  modal.value = {
    show: true,
    title,
    message,
    type: 'alert',
    variant,
    confirmText: 'Đóng',
  }
}

function openConfirm(
  title: string,
  message: string,
  confirmText: string,
  variant: 'warning' | 'danger',
  onConfirm: () => Promise<void>,
) {
  modal.value = {
    show: true,
    title,
    message,
    type: 'confirm',
    variant,
    confirmText,
    cancelText: 'Hủy',
    onConfirm,
  }
}

function closeModal() {
  if (isActionBusy.value) return
  modal.value.show = false
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

function isSelf(user: AdminUserSummaryDto): boolean {
  return authStore.user?.userId === user.id
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN')
}

async function fetchUsers() {
  isLoading.value = true
  try {
    const res = await adminApi.listUsers({
      search: search.value,
      role: roleFilter.value,
      status: statusFilter.value,
      page: page.value,
      size,
    })
    users.value = res.users
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
  } catch (error) {
    openAlert(toApiError(error).message, 'Không tải được danh sách người dùng', 'danger')
  } finally {
    isLoading.value = false
  }
}

function onFilterSubmit() {
  page.value = 0
  void fetchUsers()
}

function onResetFilters() {
  search.value = ''
  roleFilter.value = ''
  statusFilter.value = ''
  page.value = 0
  void fetchUsers()
}

function goToPage(nextPage: number) {
  if (nextPage < 0 || (totalPages.value > 0 && nextPage >= totalPages.value)) return
  page.value = nextPage
  void fetchUsers()
}

function promptToggleStatus(user: AdminUserSummaryDto) {
  const locking = user.status === 'ACTIVE'
  const nextStatus: UserStatus = locking ? 'LOCKED' : 'ACTIVE'
  openConfirm(
    locking ? 'Xác nhận khóa tài khoản' : 'Xác nhận mở khóa tài khoản',
    locking
      ? `Khóa tài khoản ${user.email}?\nNgười dùng này sẽ bị thu hồi toàn bộ phiên đăng nhập và không thể đăng nhập lại.`
      : `Mở khóa tài khoản ${user.email}?`,
    locking ? 'Khóa tài khoản' : 'Mở khóa',
    locking ? 'danger' : 'warning',
    async () => {
      await adminApi.updateUserStatus(user.id, nextStatus)
      modal.value.show = false
      await fetchUsers()
    },
  )
}

function promptToggleRole(user: AdminUserSummaryDto) {
  const nextRole: UserRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  openConfirm(
    'Xác nhận thay đổi vai trò',
    `Đổi vai trò của ${user.email} từ ${user.role} sang ${nextRole}?`,
    'Xác nhận',
    'warning',
    async () => {
      await adminApi.updateUserRole(user.id, nextRole)
      modal.value.show = false
      await fetchUsers()
    },
  )
}

function promptRevokeSessions(user: AdminUserSummaryDto) {
  openConfirm(
    'Đăng xuất toàn bộ thiết bị',
    `Thu hồi toàn bộ phiên Refresh Token của ${user.email}?`,
    'Thu hồi phiên',
    'warning',
    async () => {
      const res = await adminApi.revokeUserSessions(user.id)
      modal.value.show = false
      await fetchUsers()
      openAlert(`Đã thu hồi ${res.revokedCount} phiên đăng nhập của ${user.email}.`, 'Thành công', 'info')
    },
  )
}

function promptSendPasswordReset(user: AdminUserSummaryDto) {
  openConfirm(
    'Gửi link đặt lại mật khẩu',
    `Gửi email chứa liên kết đặt lại mật khẩu (hiệu lực 30 phút, dùng 1 lần) tới ${user.email}?`,
    'Gửi email',
    'warning',
    async () => {
      await adminApi.sendPasswordReset(user.id)
      modal.value.show = false
      openAlert(`Đã gửi email đặt lại mật khẩu tới ${user.email}.`, 'Thành công', 'info')
    },
  )
}

onMounted(() => {
  void fetchUsers()
})
</script>

<template>
  <div class="admin-users">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
      <div>
        <h1 class="h4 fw-bold mb-1">Quản lý người dùng</h1>
        <p class="text-muted small mb-0">
          Tổng số: <strong>{{ totalElements }}</strong> tài khoản (chỉ hỗ trợ khóa/mở khóa, không xóa dữ liệu người dùng).
        </p>
      </div>
    </div>

    <div class="card mb-3">
      <div class="card-body">
        <form class="row g-2 align-items-end" @submit.prevent="onFilterSubmit">
          <div class="col-md-5">
            <label class="form-label small fw-semibold mb-1" for="userSearch">Tìm kiếm</label>
            <input
              id="userSearch"
              v-model="search"
              type="text"
              class="form-control form-control-sm"
              placeholder="Email hoặc tên hiển thị..."
            />
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="userRole">Vai trò</label>
            <select id="userRole" v-model="roleFilter" class="form-select form-select-sm">
              <option value="">Tất cả</option>
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
          <div class="col-md-2">
            <label class="form-label small fw-semibold mb-1" for="userStatus">Trạng thái</label>
            <select id="userStatus" v-model="statusFilter" class="form-select form-select-sm">
              <option value="">Tất cả</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="LOCKED">LOCKED</option>
            </select>
          </div>
          <div class="col-md-3 d-flex gap-2">
            <button class="btn btn-sm btn-primary flex-fill" type="submit">Lọc</button>
            <button class="btn btn-sm btn-outline-secondary" type="button" @click="onResetFilters">Đặt lại</button>
          </div>
        </form>
      </div>
    </div>

    <div class="card">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0 small">
          <thead class="table-light">
            <tr>
              <th>Người dùng</th>
              <th>Vai trò</th>
              <th>Trạng thái</th>
              <th>Hoạt động</th>
              <th>Ngày tạo</th>
              <th class="text-end">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="isLoading">
              <td colspan="6" class="text-center py-4 text-muted">Đang tải danh sách người dùng...</td>
            </tr>
            <tr v-else-if="users.length === 0">
              <td colspan="6" class="text-center py-4 text-muted">Không tìm thấy người dùng phù hợp.</td>
            </tr>
            <tr v-for="u in users" :key="u.id">
              <td>
                <div class="fw-semibold">
                  {{ u.email }}
                  <span v-if="isSelf(u)" class="badge text-bg-info ms-1">Bạn</span>
                </div>
                <div class="text-muted">{{ u.displayName || '—' }}</div>
              </td>
              <td>
                <span class="badge" :class="u.role === 'ADMIN' ? 'text-bg-primary' : 'text-bg-secondary'">
                  {{ u.role }}
                </span>
              </td>
              <td>
                <span class="badge" :class="u.status === 'ACTIVE' ? 'text-bg-success' : 'text-bg-danger'">
                  {{ u.status }}
                </span>
              </td>
              <td>
                <div>Phiên: <strong>{{ u.activeSessionCount }}</strong></div>
                <div class="text-muted">Video: {{ u.libraryVideoCount }} · Từ: {{ u.dictionaryEntryCount }}</div>
              </td>
              <td class="text-nowrap">{{ formatDate(u.createdAt) }}</td>
              <td class="text-end">
                <div class="d-inline-flex gap-1 flex-wrap justify-content-end">
                  <RouterLink
                    class="btn btn-sm btn-outline-secondary"
                    :to="{ name: 'admin-user-detail', params: { userId: u.id } }"
                  >
                    Chi tiết
                  </RouterLink>
                  <button
                    type="button"
                    class="btn btn-sm"
                    :class="u.status === 'ACTIVE' ? 'btn-outline-danger' : 'btn-outline-success'"
                    :disabled="isSelf(u) && u.status === 'ACTIVE'"
                    @click="promptToggleStatus(u)"
                  >
                    {{ u.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa' }}
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm btn-outline-primary"
                    :disabled="isSelf(u)"
                    @click="promptToggleRole(u)"
                  >
                    {{ u.role === 'ADMIN' ? 'Hạ USER' : 'Lên ADMIN' }}
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm btn-outline-secondary"
                    @click="promptRevokeSessions(u)"
                  >
                    Đăng xuất
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm btn-outline-warning"
                    @click="promptSendPasswordReset(u)"
                  >
                    Reset MK
                  </button>
                </div>
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
