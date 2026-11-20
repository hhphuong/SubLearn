<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import AppLogo from '@/components/layout/AppLogo.vue'

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
        <AppLogo :size="30" />
        <span>SubLearn</span>
      </RouterLink>

      <div class="app-navbar__links">
        <template v-if="authStore.isAuthenticated">
          <RouterLink class="app-navbar__link" to="/">Trang chủ</RouterLink>
          <RouterLink class="app-navbar__link" to="/library">Thư viện</RouterLink>
          <RouterLink class="app-navbar__link" to="/dictionary">Từ điển</RouterLink>
        </template>
      </div>

      <div class="app-navbar__actions">
        <template v-if="authStore.isAuthenticated">
          <span class="app-navbar__user">{{ authStore.user?.displayName || authStore.user?.email }}</span>
          <button class="btn-logout" type="button" @click="onLogout">
            <svg class="btn-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
            <span>Đăng xuất</span>
          </button>
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

.btn-logout {
  height: 34px;
  padding: 0 0.75rem;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #dcdfe4;
  color: #4a5568;
  font-size: 0.85rem;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.btn-logout:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #1f2937;
}

.btn-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .app-navbar__links {
    display: none;
  }
}
</style>
