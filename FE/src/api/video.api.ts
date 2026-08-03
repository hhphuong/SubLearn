import http from '@/api/http'
import type {
  VideoLibraryPageResponse,
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
  getLibrary(page = 0, size = 20) {
    return http
      .get<VideoLibraryPageResponse>('/api/videos/library', { params: { page, size } })
      .then((r) => r.data)
  },
  updateProgress(videoId: number, positionMs: number) {
    return http.put<void>(`/api/videos/${videoId}/progress`, { positionMs })
  },
}
