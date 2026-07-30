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
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (error) {
    errorMessage.value = toApiError(error).message
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="row justify-content-center">
    <div class="col-12 col-sm-8 col-md-6 col-lg-4">
      <h1 class="h3 mb-4">Đăng nhập</h1>
      <form @submit.prevent="onSubmit">
        <div class="mb-3">
          <label class="form-label" for="email">Email</label>
          <input id="email" v-model="email" type="email" class="form-control" required autocomplete="email" />
        </div>
        <div class="mb-3">
          <label class="form-label" for="password">Mật khẩu</label>
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
