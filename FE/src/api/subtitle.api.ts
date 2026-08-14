import http from '@/api/http'
import type { SubtitlePageResponse } from '@/types/api'

export const subtitleApi = {
  getSubtitles(videoId: number, targetLanguage: string, page: number, size: number) {
    return http
      .get<SubtitlePageResponse>(`/api/videos/${videoId}/subtitles`, {
        params: { targetLanguage, page, size },
      })
      .then((r) => r.data)
  },
}
