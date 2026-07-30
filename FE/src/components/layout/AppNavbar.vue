<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'

const authStore = useAuthStore()
const router = useRouter()

async function onLogout() {
  await authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <nav class="navbar navbar-expand navbar-dark bg-dark px-3">
    <RouterLink class="navbar-brand" to="/">SubLearn</RouterLink>
    <div class="d-flex ms-auto align-items-center gap-3">
      <template v-if="authStore.isAuthenticated">
        <RouterLink class="nav-link text-white-50" to="/">Trang chủ</RouterLink>
        <RouterLink class="nav-link text-white-50" to="/dictionary">Từ điển</RouterLink>
        <span class="text-white-50 small">{{ authStore.user?.displayName || authStore.user?.email }}</span>
        <button class="btn btn-sm btn-outline-light" type="button" @click="onLogout">Đăng xuất</button>
      </template>
      <template v-else>
        <RouterLink class="nav-link text-white-50" to="/login">Đăng nhập</RouterLink>
        <RouterLink class="nav-link text-white-50" to="/register">Đăng ký</RouterLink>
      </template>
    </div>
  </nav>
</template>
