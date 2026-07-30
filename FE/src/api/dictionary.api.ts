import http from '@/api/http'
import type {
  DictionaryEntryDetailResponse,
  DictionaryPageResponse,
  SaveDictionaryRequest,
  SaveDictionaryResponse,
} from '@/types/api'

export const dictionaryApi = {
  save(request: SaveDictionaryRequest) {
    return http.post<SaveDictionaryResponse>('/api/dictionary', request).then((r) => r.data)
  },
  list(page: number, size: number, targetLanguage?: string, search?: string) {
    return http
      .get<DictionaryPageResponse>('/api/dictionary', {
        params: { page, size, targetLanguage: targetLanguage || undefined, search: search || undefined },
      })
      .then((r) => r.data)
  },
  getDetail(id: number) {
    return http.get<DictionaryEntryDetailResponse>(`/api/dictionary/${id}`).then((r) => r.data)
  },
  remove(id: number) {
    return http.delete<void>(`/api/dictionary/${id}`).then((r) => r.data)
  },
}
