import http from './http'
import type {
  ProcessingJobResponse,
  VideoHistoryPageResponse,
  VideoLibraryPageResponse,
  VideoStatusResponse,
  VideoSubmitResponse,
  VideoSummaryResponse,
} from '@/types/api'

export const videoApi = {
  submit(youtubeUrl: string, sourceLanguage: string, targetLanguage?: string) {
    return http.post<VideoSubmitResponse>('/api/videos', { youtubeUrl, sourceLanguage, targetLanguage }).then((r) => r.data)
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
  requestTranslation(videoId: number, targetLanguage: string) {
    return http
      .post<ProcessingJobResponse>(`/api/videos/${videoId}/translations`, { targetLanguage })
      .then((r) => r.data)
  },
  reprocess(videoId: number, targetLanguage: string) {
    return http
      .post<ProcessingJobResponse>(`/api/videos/${videoId}/reprocess`, { targetLanguage })
      .then((r) => r.data)
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
