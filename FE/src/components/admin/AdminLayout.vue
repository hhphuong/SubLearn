<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import AppLogo from '@/components/layout/AppLogo.vue'
import AppModal from '@/components/common/AppModal.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isSidebarCollapsed = ref(false)
const showLogoutModal = ref(false)

const pageTitle = computed(() => {
  if (route.name === 'admin-dashboard' || route.path.endsWith('/dashboard')) {
    return 'Tổng quan & Sức khỏe hệ thống'
  }
  if (route.name === 'admin-users' || route.path === '/admin/users') {
    return 'Quản lý người dùng'
  }
  if (route.name === 'admin-user-detail' || route.path.includes('/admin/users/')) {
    return 'Chi tiết người dùng'
  }
  if (route.name === 'admin-jobs' || route.path.endsWith('/jobs')) {
    return 'Job & Hàng đợi'
  }
  return 'Bảng điều khiển quản trị'
})

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

async function onConfirmLogout() {
  showLogoutModal.value = false
  await authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell" :class="{ 'admin-shell--collapsed': isSidebarCollapsed }">
    <!-- Sidebar -->
    <aside class="admin-sidebar">
      <div class="admin-sidebar__header">
        <div class="admin-brand">
          <AppLogo :size="30" />
          <div class="admin-brand__text">
            <span class="admin-brand__name">SubLearn</span>
            <span class="admin-badge">ADMIN</span>
          </div>
        </div>
        <div class="admin-sidebar__subtitle">Trang Quản Trị Hệ Thống</div>
      </div>

      <nav class="admin-nav" aria-label="Menu quản trị">
        <RouterLink class="admin-nav__item" to="/admin/dashboard">
          <span class="admin-nav__icon">📊</span>
          <span class="admin-nav__label">Tổng quan &amp; Hệ thống</span>
        </RouterLink>
        <RouterLink class="admin-nav__item" to="/admin/users">
          <span class="admin-nav__icon">👥</span>
          <span class="admin-nav__label">Quản lý người dùng</span>
        </RouterLink>
        <RouterLink class="admin-nav__item" to="/admin/jobs">
          <span class="admin-nav__icon">⚙️</span>
          <span class="admin-nav__label">Job &amp; Hàng đợi</span>
        </RouterLink>
      </nav>

      <div class="admin-sidebar__footer">
        <RouterLink class="admin-back-btn" to="/">
          <span class="admin-back-btn__icon">←</span>
          <span>Về trang học tập</span>
        </RouterLink>
      </div>
    </aside>

    <!-- Main Container -->
    <div class="admin-main-container">
      <!-- Admin Topbar -->
      <header class="admin-topbar">
        <div class="admin-topbar__left">
          <button
            class="admin-menu-toggle"
            type="button"
            aria-label="Thu gọn thanh điều hướng"
            @click="toggleSidebar"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="12" x2="21" y2="12"></line>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <line x1="3" y1="18" x2="21" y2="18"></line>
            </svg>
          </button>
          <div class="admin-breadcrumb">
            <span class="admin-breadcrumb__root">Quản trị</span>
            <span class="admin-breadcrumb__separator">/</span>
            <span class="admin-breadcrumb__current">{{ pageTitle }}</span>
          </div>
        </div>

        <div class="admin-topbar__right">
          <div class="admin-user-profile">
            <span class="admin-role-tag">ADMIN</span>
            <span class="admin-user-name">
              {{ authStore.user?.displayName || authStore.user?.email }}
            </span>
          </div>

          <button
            class="btn-admin-logout"
            type="button"
            title="Đăng xuất khỏi trang quản trị"
            @click="showLogoutModal = true"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
            <span>Đăng xuất</span>
          </button>
        </div>
      </header>

      <!-- Content View -->
      <main class="admin-content">
        <div class="admin-content__inner">
          <RouterView />
        </div>
      </main>
    </div>

    <!-- Modal Xác nhận Đăng xuất -->
    <AppModal
      :show="showLogoutModal"
      title="Xác nhận đăng xuất"
      message="Bạn có chắc chắn muốn đăng xuất khỏi trang quản trị hệ thống?"
      type="confirm"
      variant="warning"
      confirm-text="Đăng xuất"
      cancel-text="Hủy"
      @confirm="onConfirmLogout"
      @cancel="showLogoutModal = false"
      @close="showLogoutModal = false"
    />
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
  background-color: #f8fafc;
  color: var(--brand-ink, #0f172a);
}

/* Sidebar */
.admin-sidebar {
  width: 260px;
  min-height: 100vh;
  background: #ffffff;
  border-right: 1px solid var(--brand-border, #e2e8f0);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 1010;
  transition: width 0.2s ease, transform 0.2s ease;
  flex-shrink: 0;
}

.admin-sidebar__header {
  padding: 1.25rem 1.25rem 1rem;
  border-bottom: 1px solid var(--brand-border, #e2e8f0);
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.admin-brand__text {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.admin-brand__name {
  font-weight: 800;
  font-size: 1.15rem;
  color: var(--brand-ink, #0f172a);
  letter-spacing: -0.02em;
}

.admin-badge {
  display: inline-block;
  font-size: 0.68rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  background: #dcfce7;
  color: #15803d;
  border: 1px solid #bbf7d0;
}

.admin-sidebar__subtitle {
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: var(--brand-muted, #64748b);
  font-weight: 500;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1rem 0.75rem;
  flex: 1;
  overflow-y: auto;
}

.admin-nav__item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  border-radius: 8px;
  color: #475569;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.92rem;
  transition: all 0.15s ease;
}

.admin-nav__icon {
  font-size: 1.1rem;
  line-height: 1;
}

.admin-nav__item:hover {
  background: #f1f5f9;
  color: var(--brand-green-dark, #15803d);
}

.admin-nav__item.router-link-active {
  background: var(--brand-green-light, #ecfdf5);
  color: var(--brand-green-dark, #15803d);
  font-weight: 700;
  border-left: 3px solid var(--brand-green, #16a34a);
}

.admin-sidebar__footer {
  padding: 1rem 0.75rem;
  border-top: 1px solid var(--brand-border, #e2e8f0);
}

.admin-back-btn {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.85rem;
  border-radius: 8px;
  color: #64748b;
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 600;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  transition: all 0.15s ease;
}

.admin-back-btn:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.admin-back-btn__icon {
  font-size: 1rem;
  font-weight: bold;
}

/* Main Container */
.admin-main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* Topbar */
.admin-topbar {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid var(--brand-border, #e2e8f0);
  padding: 0 1.75rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.admin-topbar__left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.admin-menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #475569;
  cursor: pointer;
}

.admin-breadcrumb {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.95rem;
}

.admin-breadcrumb__root {
  color: #94a3b8;
  font-weight: 500;
}

.admin-breadcrumb__separator {
  color: #cbd5e1;
}

.admin-breadcrumb__current {
  color: #0f172a;
  font-weight: 700;
}

.admin-topbar__right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.admin-user-profile {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
}

.admin-role-tag {
  font-size: 0.65rem;
  font-weight: 800;
  background: #16a34a;
  color: #ffffff;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
  letter-spacing: 0.05em;
}

.admin-user-name {
  font-size: 0.88rem;
  font-weight: 600;
  color: #334155;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-admin-logout {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.85rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-admin-logout:hover {
  background: #dc2626;
  color: #ffffff;
  border-color: #dc2626;
}

/* Content */
.admin-content {
  flex: 1;
  padding: 1.75rem;
  overflow-y: auto;
}

.admin-content__inner {
  max-width: 1440px;
  margin: 0 auto;
}

/* Responsive adjustments */
@media (max-width: 992px) {
  .admin-menu-toggle {
    display: flex;
  }

  .admin-shell--collapsed .admin-sidebar {
    transform: translateX(-100%);
    position: fixed;
  }

  .admin-sidebar {
    position: fixed;
    transform: translateX(0);
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
  }

  .admin-topbar {
    padding: 0 1rem;
  }

  .admin-content {
    padding: 1rem;
  }
}
</style>
