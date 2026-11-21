import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import router from '../FE/src/router'
import AppNavbar from '../FE/src/components/layout/AppNavbar.vue'
import LoginView from '../FE/src/views/LoginView.vue'
import { useAuthStore } from '../FE/src/stores/auth.store'
import { authApi } from '../FE/src/api/auth.api'

vi.mock('../FE/src/api/auth.api', () => ({
  authApi: {
    me: vi.fn(),
    logout: vi.fn(),
    login: vi.fn(),
  },
}))

describe('Admin router guard & AppNavbar', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('blocks non-admin user from /admin/dashboard and allows ADMIN user after role update', async () => {
    const pinia = createTestingPinia({
      createSpy: vi.fn,
      stubActions: false,
      initialState: {
        auth: {
          accessToken: 'user-jwt',
          refreshToken: 'user-refresh',
          expiresAt: Date.now() + 60000,
          user: {
            userId: 2,
            email: 'user@example.com',
            displayName: 'User',
            role: 'USER',
            status: 'ACTIVE',
          },
        },
      },
    })

    const navbar = mount(AppNavbar, {
      global: { plugins: [pinia, router] },
    })
    expect(navbar.text()).not.toContain('Quản trị')

    await router.push('/admin/dashboard')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('home')

    const store = useAuthStore(pinia)
    store.$patch({
      user: {
        userId: 1,
        email: 'admin@example.com',
        displayName: 'Admin',
        role: 'ADMIN',
        status: 'ACTIVE',
      },
    })
    await flushPromises()

    expect(store.isAdmin).toBe(true)
    expect(navbar.text()).toContain('Quản trị')

    vi.mocked(authApi.me).mockResolvedValue({
      userId: 1,
      email: 'admin@example.com',
      displayName: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    })

    await router.push('/admin/dashboard')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('admin-dashboard')

    // Guest-only routes like /login should also redirect logged-in admin to admin-dashboard
    await router.push('/login')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('admin-dashboard')

    navbar.unmount()
  })

  it('redirects admin to admin-dashboard upon login in LoginView', async () => {
    await router.push('/login')
    await flushPromises()

    const pinia = createTestingPinia({
      createSpy: vi.fn,
      stubActions: false,
    })
    const store = useAuthStore(pinia)
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'admin-jwt',
      refreshToken: 'admin-ref',
      expiresIn: 3600,
    })
    vi.mocked(authApi.me).mockResolvedValue({
      userId: 1,
      email: 'admin@example.com',
      displayName: 'Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
    })

    const wrapper = mount(LoginView, {
      global: { plugins: [pinia, router] },
    })

    await wrapper.find('#email').setValue('admin@example.com')
    await wrapper.find('#password').setValue('Password123!')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(store.isAdmin).toBe(true)
    expect(router.currentRoute.value.name).toBe('admin-dashboard')
  })
})
