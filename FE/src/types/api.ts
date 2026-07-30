export type VideoStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export type FailureReason =
  | 'NO_CAPTIONS_AVAILABLE'
  | 'INVALID_URL'
  | 'VIDEO_UNAVAILABLE'
  | 'VIDEO_PRIVATE'
  | 'VIDEO_REGION_LOCKED'
  | 'VIDEO_AGE_RESTRICTED'
  | 'SOURCE_LANGUAGE_UNDETECTABLE'
  | 'TOKENIZATION_FAILED'
  | 'PROCESS_TIMEOUT'
  | 'UNKNOWN_ERROR'

export type TokenType = 'WORD' | 'PHRASE'

export interface RegisterRequest {
  email: string
  password: string
  displayName?: string
}

export interface RegisterResponse {
  userId: number
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface MeResponse {
  userId: number
  email: string
  displayName: string | null
}

export interface VideoSubmitResponse {
  videoId: number
  status: VideoStatus
}

export interface VideoStatusResponse {
  status: VideoStatus
  failureReason: FailureReason | null
  failureMessage: string | null
  title: string | null
  sourceLanguage: string | null
  durationSeconds: number | null
  youtubeVideoId: string | null
}

export interface VideoSummaryResponse {
  videoId: number
  title: string | null
  sourceLanguage: string | null
  durationSeconds: number | null
  status: VideoStatus
  youtubeVideoId: string | null
}

export interface SubtitleTokenDto {
  sequence: number
  startCharOffset: number
  endCharOffset: number
  surfaceText: string
  tokenType: TokenType
}

export interface SubtitleLineDto {
  id: number
  sequence: number
  startMs: number
  endMs: number
  sourceText: string
  targetText: string
  tokens: SubtitleTokenDto[]
}

export interface SubtitlePageResponse {
  page: number
  size: number
  totalLines: number
  sourceLanguage: string
  targetLanguage: string
  sameLanguage: boolean
  lines: SubtitleLineDto[]
}

export interface WordLookupRequest {
  word: string
  sourceLanguage: string
  targetLanguage: string
  sentenceContext: string
  videoId?: number
  subtitleLineId?: number
}

export interface WordLookupResponse {
  word: string
  contextualMeaning: string | null
  dictionaryMeaning: string | null
  examples: string[]
  cached: boolean
  error: string | null
}

export interface SaveDictionaryRequest {
  word: string
  sourceLanguage: string
  targetLanguage: string
  contextualMeaning: string | null
  dictionaryMeaning: string | null
  examples: string[]
  videoId: number
  subtitleLineId?: number
  sentenceSnapshot: string
}

export interface SaveDictionaryResponse {
  dictionaryEntryId: number
  wasNew: boolean
}

export interface DictionaryEntrySummaryResponse {
  id: number
  displayWord: string
  sourceLanguage: string
  targetLanguage: string
  contextualMeaning: string | null
  dictionaryMeaning: string | null
  examples: string[]
  contextCount: number
  createdAt: string
  updatedAt: string
}

export interface DictionaryContextDto {
  id: number
  videoId: number
  subtitleLineId: number | null
  sentenceSnapshot: string
  savedAt: string
}

export interface DictionaryEntryDetailResponse extends Omit<DictionaryEntrySummaryResponse, 'contextCount'> {
  contexts: DictionaryContextDto[]
}

export interface DictionaryPageResponse {
  page: number
  size: number
  totalElements: number
  entries: DictionaryEntrySummaryResponse[]
}
