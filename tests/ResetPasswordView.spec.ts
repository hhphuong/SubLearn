import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ResetPasswordView from '../FE/src/views/ResetPasswordView.vue'
import { authApi } from '../FE/src/api/auth.api'

vi.mock('../FE/src/api/auth.api', () => ({
  authApi: {
    resetPassword: vi.fn(),
  },
}))

describe('ResetPasswordView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  async function mountWithToken(token?: string) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/reset-password', name: 'reset-password', component: ResetPasswordView },
        { path: '/login', name: 'login', component: { template: '<div />' } },
      ],
    })
    await router.push(token ? `/reset-password?token=${token}` : '/reset-password')
    await router.isReady()
    return mount(ResetPasswordView, {
      global: { plugins: [router] },
    })
  }

  it('validates matching password and submits token with new password', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue(undefined)
    const wrapper = await mountWithToken('valid-reset-token')

    await wrapper.find('#newPassword').setValue('NewPass123')
    await wrapper.find('#confirmPassword').setValue('Mismatch123')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.text()).toContain('Mật khẩu xác nhận không khớp.')
    expect(authApi.resetPassword).not.toHaveBeenCalled()

    await wrapper.find('#confirmPassword').setValue('NewPass123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authApi.resetPassword).toHaveBeenCalledWith('valid-reset-token', 'NewPass123')
    expect(wrapper.text()).toContain('cập nhật thành công')
  })

  it('shows error notice when token query param is missing', async () => {
    const wrapper = await mountWithToken()
    expect(wrapper.text()).toContain('thiếu mã xác thực')
  })
})
