import http from './http'
import type { ProcessingJobResponse } from '@/types/api'

export const jobApi = {
  getJob(jobId: number) {
    return http.get<ProcessingJobResponse>(`/api/jobs/${jobId}`).then((r) => r.data)
  },
  retryJob(jobId: number) {
    return http.post<ProcessingJobResponse>(`/api/jobs/${jobId}/retry`).then((r) => r.data)
  },
}
