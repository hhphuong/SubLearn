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
  <nav class="app-navbar">
    <div class="app-navbar__inner">
      <RouterLink class="app-navbar__brand" to="/">
        <span class="app-navbar__logo">📚</span>
        <span>SubLearn</span>
      </RouterLink>

      <div class="app-navbar__links">
        <template v-if="authStore.isAuthenticated">
          <RouterLink class="app-navbar__link" to="/">Trang chủ</RouterLink>
          <RouterLink class="app-navbar__link" to="/dictionary">Từ điển</RouterLink>
        </template>
      </div>

      <div class="app-navbar__actions">
        <template v-if="authStore.isAuthenticated">
          <span class="app-navbar__user">{{ authStore.user?.displayName || authStore.user?.email }}</span>
          <button class="btn btn-outline-primary btn-sm" type="button" @click="onLogout">Đăng xuất</button>
        </template>
        <template v-else>
          <RouterLink class="app-navbar__link" to="/login">Đăng nhập</RouterLink>
          <RouterLink class="btn btn-primary btn-sm" to="/register">Đăng ký</RouterLink>
        </template>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.app-navbar {
  background: #fff;
  border-bottom: 1px solid var(--brand-border);
  box-shadow: var(--brand-shadow);
  position: sticky;
  top: 0;
  z-index: 1020;
}

.app-navbar__inner {
  max-width: 1140px;
  margin: 0 auto;
  padding: 0.75rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.app-navbar__brand {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 800;
  font-size: 1.15rem;
  color: var(--brand-ink);
  text-decoration: none;
}

.app-navbar__logo {
  font-size: 1.4rem;
  line-height: 1;
}

.app-navbar__links {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex: 1;
}

.app-navbar__link {
  color: var(--brand-muted);
  text-decoration: none;
  font-weight: 600;
  padding: 0.4rem 0.75rem;
  border-radius: 999px;
  transition: background-color 0.15s, color 0.15s;
}

.app-navbar__link:hover {
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
}

.app-navbar__link.router-link-exact-active {
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
}

.app-navbar__actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-left: auto;
}

.app-navbar__user {
  color: var(--brand-muted);
  font-size: 0.875rem;
  font-weight: 600;
}

@media (max-width: 640px) {
  .app-navbar__links {
    display: none;
  }
}
</style>
