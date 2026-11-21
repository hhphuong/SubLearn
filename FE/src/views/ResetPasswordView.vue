<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { authApi } from '@/api/auth.api'
import { toApiError } from '@/api/errors'

const route = useRoute()

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token.trim() : ''))
const newPassword = ref('')
const confirmPassword = ref('')
const isSubmitting = ref(false)
const errorMessage = ref<string | null>(null)
const isSuccess = ref(false)

async function onSubmit() {
  errorMessage.value = null
  if (!token.value) {
    errorMessage.value = 'Liên kết đặt lại mật khẩu không hợp lệ hoặc bị thiếu token.'
    return
  }
  if (newPassword.value.length < 8) {
    errorMessage.value = 'Mật khẩu mới phải có ít nhất 8 ký tự.'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Mật khẩu xác nhận không khớp.'
    return
  }

  isSubmitting.value = true
  try {
    await authApi.resetPassword(token.value, newPassword.value)
    isSuccess.value = true
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__icon">🔐</div>
      <h1 class="auth-card__title">Đặt lại mật khẩu</h1>

      <div v-if="isSuccess" class="text-start">
        <div class="alert alert-success py-2 small mb-3">
          Mật khẩu của bạn đã được cập nhật thành công. Toàn bộ phiên đăng nhập cũ đã được đăng xuất.
        </div>
        <RouterLink class="btn btn-primary w-100" to="/login">Đăng nhập ngay</RouterLink>
      </div>

      <div v-else-if="!token" class="text-start">
        <div class="alert alert-danger py-2 small mb-3">
          Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu mã xác thực. Vui lòng liên hệ quản trị viên để nhận liên kết mới.
        </div>
        <RouterLink class="btn btn-outline-primary w-100" to="/login">Về trang đăng nhập</RouterLink>
      </div>

      <form v-else @submit.prevent="onSubmit">
        <div class="mb-3">
          <label class="form-label fw-semibold" for="newPassword">Mật khẩu mới</label>
          <input
            id="newPassword"
            v-model="newPassword"
            type="password"
            class="form-control"
            required
            minlength="8"
            autocomplete="new-password"
            placeholder="Tối thiểu 8 ký tự"
          />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" for="confirmPassword">Xác nhận mật khẩu mới</label>
          <input
            id="confirmPassword"
            v-model="confirmPassword"
            type="password"
            class="form-control"
            required
            minlength="8"
            autocomplete="new-password"
            placeholder="Nhập lại mật khẩu mới"
          />
        </div>

        <div v-if="errorMessage" class="alert alert-danger py-2 small">{{ errorMessage }}</div>

        <button class="btn btn-primary w-100" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Đang cập nhật...' : 'Cập nhật mật khẩu' }}
        </button>

        <p class="mt-3 text-center small mb-0">
          <RouterLink to="/login">Quay lại đăng nhập</RouterLink>
        </p>
      </form>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 1rem;
}

.auth-card {
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  box-shadow: var(--brand-shadow);
  padding: 2.5rem;
  width: 100%;
  max-width: 420px;
  text-align: center;
}

.auth-card__icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}

.auth-card__title {
  font-size: 1.4rem;
  font-weight: 800;
  margin-bottom: 1.5rem;
}

.auth-card form {
  text-align: left;
}
</style>
