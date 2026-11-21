import http from '@/api/http'
import type {
  AdminDashboardOverviewResponse,
  AdminDashboardRange,
  AdminJobItemDto,
  AdminJobPageResponse,
  AdminSystemHealthResponse,
  AdminUserDetailResponse,
  AdminUserPageResponse,
  AdminUserSummaryDto,
  BulkRetryResponse,
  FailureReason,
  JobStatus,
  JobType,
  ProcessingJobResponse,
  PurgeDlqResponse,
  RabbitQueueStatsResponse,
  RevokeSessionsResponse,
  SendPasswordResetResponse,
  UserRole,
  UserStatus,
} from '@/types/api'

export interface AdminUserListParams {
  search?: string
  role?: UserRole | ''
  status?: UserStatus | ''
  page?: number
  size?: number
}

export interface AdminJobListParams {
  status?: JobStatus | ''
  jobType?: JobType | ''
  failureReason?: FailureReason | ''
  videoId?: number | string
  search?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export const adminApi = {
  getDashboardOverview(range: AdminDashboardRange = '7d') {
    return http
      .get<AdminDashboardOverviewResponse>('/api/admin/dashboard/overview', { params: { range } })
      .then((r) => r.data)
  },

  getSystemHealth() {
    return http.get<AdminSystemHealthResponse>('/api/admin/dashboard/health').then((r) => r.data)
  },

  listUsers(params: AdminUserListParams = {}) {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    }
    if (params.search?.trim()) query.search = params.search.trim()
    if (params.role) query.role = params.role
    if (params.status) query.status = params.status
    return http.get<AdminUserPageResponse>('/api/admin/users', { params: query }).then((r) => r.data)
  },

  getUserDetail(userId: number) {
    return http.get<AdminUserDetailResponse>(`/api/admin/users/${userId}`).then((r) => r.data)
  },

  updateUserStatus(userId: number, status: UserStatus) {
    return http.patch<AdminUserSummaryDto>(`/api/admin/users/${userId}/status`, { status }).then((r) => r.data)
  },

  updateUserRole(userId: number, role: UserRole) {
    return http.patch<AdminUserSummaryDto>(`/api/admin/users/${userId}/role`, { role }).then((r) => r.data)
  },

  revokeUserSessions(userId: number) {
    return http.post<RevokeSessionsResponse>(`/api/admin/users/${userId}/revoke-sessions`).then((r) => r.data)
  },

  sendPasswordReset(userId: number) {
    return http.post<SendPasswordResetResponse>(`/api/admin/users/${userId}/send-password-reset`).then((r) => r.data)
  },

  listJobs(params: AdminJobListParams = {}) {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    }
    if (params.status) query.status = params.status
    if (params.jobType) query.jobType = params.jobType
    if (params.failureReason) query.failureReason = params.failureReason
    if (params.videoId !== undefined && params.videoId !== '') query.videoId = Number(params.videoId)
    if (params.search?.trim()) query.search = params.search.trim()
    if (params.from) query.from = params.from
    if (params.to) query.to = params.to
    return http.get<AdminJobPageResponse>('/api/admin/jobs', { params: query }).then((r) => r.data)
  },

  retryJob(jobId: number) {
    return http.post<ProcessingJobResponse>(`/api/admin/jobs/${jobId}/retry`).then((r) => r.data)
  },

  bulkRetryJobs(jobIds: number[]) {
    return http.post<BulkRetryResponse>('/api/admin/jobs/bulk-retry', { jobIds }).then((r) => r.data)
  },

  forceFailJob(jobId: number, reason?: string) {
    return http.post<AdminJobItemDto>(`/api/admin/jobs/${jobId}/force-fail`, reason ? { reason } : {}).then((r) => r.data)
  },

  getQueueStats() {
    return http.get<RabbitQueueStatsResponse>('/api/admin/queues').then((r) => r.data)
  },

  purgeDlq() {
    return http.post<PurgeDlqResponse>('/api/admin/queues/dlq/purge').then((r) => r.data)
  },
}
