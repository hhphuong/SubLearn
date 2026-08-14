<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { toApiError } from '@/api/errors'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const email = ref('')
const password = ref('')
const displayName = ref('')
const errorMessage = ref<string | null>(null)
const registeredButLoginFailed = ref(false)
const isSubmitting = ref(false)

async function onSubmit() {
  errorMessage.value = null
  registeredButLoginFailed.value = false
  isSubmitting.value = true
  try {
    await authStore.register(email.value, password.value, displayName.value || undefined)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (error) {
    const apiError = toApiError(error)
    if (apiError.code === 'EMAIL_ALREADY_REGISTERED') {
      errorMessage.value = apiError.message
    } else if (authStore.isAuthenticated) {
      // registration succeeded but the immediate auto-login call failed
      registeredButLoginFailed.value = true
    } else {
      errorMessage.value = apiError.message
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__icon">🌱</div>
      <h1 class="auth-card__title">Đăng ký</h1>
      <form @submit.prevent="onSubmit">
        <div class="mb-3">
          <label class="form-label fw-semibold" for="displayName">Tên hiển thị</label>
          <input id="displayName" v-model="displayName" type="text" class="form-control" autocomplete="name" />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" for="email">Email</label>
          <input id="email" v-model="email" type="email" class="form-control" required autocomplete="email" />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" for="password">Mật khẩu (tối thiểu 8 ký tự)</label>
          <input
            id="password"
            v-model="password"
            type="password"
            class="form-control"
            required
            minlength="8"
            autocomplete="new-password"
          />
        </div>
        <div v-if="errorMessage" class="alert alert-danger py-2">{{ errorMessage }}</div>
        <div v-if="registeredButLoginFailed" class="alert alert-success py-2">
          Đăng ký thành công! <RouterLink to="/login">Đăng nhập ngay</RouterLink>
        </div>
        <button class="btn btn-primary w-100" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Đang đăng ký...' : 'Đăng ký' }}
        </button>
      </form>
      <p class="mt-3 text-center small">
        Đã có tài khoản? <RouterLink to="/login">Đăng nhập</RouterLink>
      </p>
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
  max-width: 400px;
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
