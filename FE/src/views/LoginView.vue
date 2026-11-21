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
const errorMessage = ref<string | null>(null)
const isSubmitting = ref(false)

async function onSubmit() {
  errorMessage.value = null
  isSubmitting.value = true
  try {
    await authStore.login(email.value, password.value)
    const redirectQuery = typeof route.query.redirect === 'string' ? route.query.redirect : null
    if (redirectQuery && redirectQuery !== '/') {
      router.push(redirectQuery)
    } else if (authStore.isAdmin) {
      router.push({ name: 'admin-dashboard' })
    } else {
      router.push('/')
    }
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
      <div class="auth-card__icon">👋</div>
      <h1 class="auth-card__title">Đăng nhập</h1>
      <form @submit.prevent="onSubmit">
        <div class="mb-3">
          <label class="form-label fw-semibold" for="email">Email</label>
          <input id="email" v-model="email" type="email" class="form-control" required autocomplete="email" />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" for="password">Mật khẩu</label>
          <input
            id="password"
            v-model="password"
            type="password"
            class="form-control"
            required
            autocomplete="current-password"
          />
        </div>
        <div v-if="errorMessage" class="alert alert-danger py-2">{{ errorMessage }}</div>
        <button class="btn btn-primary w-100" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập' }}
        </button>
      </form>
      <p class="mt-3 text-center small">
        Chưa có tài khoản? <RouterLink to="/register">Đăng ký</RouterLink>
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
