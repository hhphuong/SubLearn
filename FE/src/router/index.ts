import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('../views/ResetPasswordView.vue'),
    },
    {
      path: '/watch/:videoId',
      name: 'watch',
      component: () => import('../views/WatchView.vue'),
      meta: { requiresAuth: true },
      props: true,
    },
    {
      path: '/dictionary',
      name: 'dictionary',
      component: () => import('../views/DictionaryView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/library',
      name: 'library',
      component: () => import('../views/LibraryView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: () => import('../components/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-root',
          redirect: { name: 'admin-dashboard' },
        },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('../views/admin/AdminDashboardView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('../views/admin/AdminUsersView.vue'),
        },
        {
          path: 'users/:userId',
          name: 'admin-user-detail',
          component: () => import('../views/admin/AdminUserDetailView.vue'),
        },
        {
          path: 'jobs',
          name: 'admin-jobs',
          component: () => import('../views/admin/AdminJobsView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && authStore.isAuthenticated) {
    if (!authStore.user?.role) {
      try {
        await authStore.fetchMe()
      } catch {
        // ignore
      }
    }
    return authStore.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' }
  }
  if (to.meta.requiresAdmin) {
    if (!authStore.user?.role) {
      try {
        await authStore.fetchMe()
      } catch {
        authStore.clear()
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }
    if (!authStore.isAdmin) {
      return { name: 'home' }
    }
  }
})

export default router
