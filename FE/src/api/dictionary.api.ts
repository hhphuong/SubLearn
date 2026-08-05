import http from '@/api/http'
import type {
  CreateDictionaryFolderRequest,
  DictionaryEntryDetailResponse,
  DictionaryFolderResponse,
  DictionaryPageResponse,
  SaveDictionaryRequest,
  SaveDictionaryResponse,
} from '@/types/api'

export const dictionaryApi = {
  save(request: SaveDictionaryRequest) {
    return http.post<SaveDictionaryResponse>('/api/dictionary', request).then((r) => r.data)
  },
  list(
    page: number,
    size: number,
    sourceLanguage?: string,
    targetLanguage?: string,
    folderId?: number | null,
    search?: string,
  ) {
    return http
      .get<DictionaryPageResponse>('/api/dictionary', {
        params: {
          page,
          size,
          sourceLanguage: sourceLanguage || undefined,
          targetLanguage: targetLanguage || undefined,
          folderId: folderId || undefined,
          search: search || undefined,
        },
      })
      .then((r) => r.data)
  },
  getDetail(id: number) {
    return http.get<DictionaryEntryDetailResponse>(`/api/dictionary/${id}`).then((r) => r.data)
  },
  remove(id: number) {
    return http.delete<void>(`/api/dictionary/${id}`).then((r) => r.data)
  },
  listFolders(sourceLanguage: string) {
    return http
      .get<DictionaryFolderResponse[]>('/api/dictionary/folders', { params: { sourceLanguage } })
      .then((r) => r.data)
  },
  createFolder(request: CreateDictionaryFolderRequest) {
    return http.post<DictionaryFolderResponse>('/api/dictionary/folders', request).then((r) => r.data)
  },
  removeFolder(id: number) {
    return http.delete<void>(`/api/dictionary/folders/${id}`).then((r) => r.data)
  },
}
