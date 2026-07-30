import http from '@/api/http'
import type { VideoStatusResponse, VideoSubmitResponse, VideoSummaryResponse } from '@/types/api'

export const videoApi = {
  submit(youtubeUrl: string) {
    return http.post<VideoSubmitResponse>('/api/videos', { youtubeUrl }).then((r) => r.data)
  },
  getStatus(videoId: number) {
    return http.get<VideoStatusResponse>(`/api/videos/${videoId}/status`).then((r) => r.data)
  },
  getSummary(videoId: number) {
    return http.get<VideoSummaryResponse>(`/api/videos/${videoId}`).then((r) => r.data)
  },
}
