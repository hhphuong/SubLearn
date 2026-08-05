import http from '@/api/http'
import type {
  VideoHistoryPageResponse,
  VideoStatusResponse,
  VideoSubmitResponse,
  VideoSummaryResponse,
} from '@/types/api'

export const videoApi = {
  submit(youtubeUrl: string, sourceLanguage: string) {
    return http.post<VideoSubmitResponse>('/api/videos', { youtubeUrl, sourceLanguage }).then((r) => r.data)
  },
  getStatus(videoId: number) {
    return http.get<VideoStatusResponse>(`/api/videos/${videoId}/status`).then((r) => r.data)
  },
  getSummary(videoId: number) {
    return http.get<VideoSummaryResponse>(`/api/videos/${videoId}`).then((r) => r.data)
  },
  recordWatch(videoId: number) {
    return http.post<void>(`/api/videos/${videoId}/watch`).then((r) => r.data)
  },
  getHistory(page: number, size: number) {
    return http.get<VideoHistoryPageResponse>('/api/videos/history', { params: { page, size } }).then((r) => r.data)
  },
  reprocess(videoId: number) {
    return http.post<VideoSubmitResponse>(`/api/videos/${videoId}/reprocess`).then((r) => r.data)
  },
}
