import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AdminUsersView from '../FE/src/views/admin/AdminUsersView.vue'
import { adminApi } from '../FE/src/api/admin.api'

vi.mock('../FE/src/api/admin.api', () => ({
  adminApi: {
    listUsers: vi.fn(),
    updateUserStatus: vi.fn(),
    updateUserRole: vi.fn(),
    revokeUserSessions: vi.fn(),
    sendPasswordReset: vi.fn(),
  },
}))

describe('AdminUsersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders users, confirms lock via AppModal, and shows API errors via AppModal', async () => {
    vi.mocked(adminApi.listUsers).mockResolvedValue({
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      users: [
        {
          id: 1,
          email: 'admin@example.com',
          displayName: 'Admin',
          role: 'ADMIN',
          status: 'ACTIVE',
          createdAt: '2026-11-20T00:00:00Z',
          updatedAt: '2026-11-20T00:00:00Z',
          activeSessionCount: 1,
          libraryVideoCount: 0,
          dictionaryEntryCount: 0,
        },
        {
          id: 2,
          email: 'learner@example.com',
          displayName: 'Learner',
          role: 'USER',
          status: 'ACTIVE',
          createdAt: '2026-11-20T00:00:00Z',
          updatedAt: '2026-11-20T00:00:00Z',
          activeSessionCount: 2,
          libraryVideoCount: 3,
          dictionaryEntryCount: 5,
        },
      ],
    })
    vi.mocked(adminApi.sendPasswordReset).mockRejectedValue(new Error('SMTP not configured'))

    const wrapper = mount(AdminUsersView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              auth: {
                accessToken: 'jwt',
                user: { userId: 1, email: 'admin@example.com', displayName: 'Admin', role: 'ADMIN', status: 'ACTIVE' },
              },
            },
          }),
        ],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('learner@example.com')

    const lockButtons = wrapper.findAll('button').filter((b) => b.text() === 'Khóa')
    expect(lockButtons[0].attributes('disabled')).toBeDefined()
    expect(lockButtons[1].attributes('disabled')).toBeUndefined()

    await lockButtons[1].trigger('click')
    expect(wrapper.text()).toContain('Xác nhận khóa tài khoản')
    expect(adminApi.updateUserStatus).not.toHaveBeenCalled()

    const confirmBtn = wrapper.find('.app-modal-footer .btn-primary')
    await confirmBtn.trigger('click')
    await flushPromises()
    expect(adminApi.updateUserStatus).toHaveBeenCalledWith(2, 'LOCKED')

    const resetBtn = wrapper.findAll('button').filter((b) => b.text() === 'Reset MK')[1]
    await resetBtn.trigger('click')
    await wrapper.find('.app-modal-footer .btn-primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Cảnh báo lỗi')
  })
})
