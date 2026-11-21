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
          <RouterLink
            v-if="authStore.isAdmin"
            class="app-navbar__admin-btn"
            to="/admin"
            title="Vào trang quản trị"
          >
            <svg class="btn-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="7" height="7"></rect>
              <rect x="14" y="3" width="7" height="7"></rect>
              <rect x="14" y="14" width="7" height="7"></rect>
              <rect x="3" y="14" width="7" height="7"></rect>
            </svg>
            <span>Quản trị</span>
          </RouterLink>

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

.app-navbar__link.router-link-active {
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
  font-weight: 700;
}

.app-navbar__actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.app-navbar__admin-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
  border: 1px solid rgba(22, 163, 74, 0.25);
  font-size: 0.85rem;
  font-weight: 700;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  text-decoration: none;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}

.app-navbar__admin-btn:hover {
  background: var(--brand-green);
  color: #fff;
}

.app-navbar__user {
  font-size: 0.9rem;
  color: var(--brand-ink);
  font-weight: 500;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-logout {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.75rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--brand-muted);
  background: transparent;
  border: 1px solid var(--brand-border);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-logout:hover {
  color: #dc3545;
  border-color: #dc3545;
  background: #fff5f5;
}

.btn-icon {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .app-navbar__inner {
    flex-wrap: wrap;
    gap: 0.75rem;
    padding: 0.6rem 1rem;
  }

  .app-navbar__links {
    order: 3;
    width: 100%;
    overflow-x: auto;
    padding-bottom: 0.25rem;
  }

  .app-navbar__actions {
    margin-left: auto;
  }

  .app-navbar__user {
    display: none;
  }
}
</style>
