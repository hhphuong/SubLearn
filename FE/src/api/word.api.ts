import http from '@/api/http'
import type { WordLookupRequest, WordLookupResponse } from '@/types/api'

export const wordApi = {
  lookup(request: WordLookupRequest) {
    return http.post<WordLookupResponse>('/api/words/lookup', request).then((r) => r.data)
  },
}
