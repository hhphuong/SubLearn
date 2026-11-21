export type VideoStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

export type JobType = 'INGEST_VIDEO' | 'TRANSLATE_SUBTITLES' | 'RETRANSLATE_SUBTITLES'

export type JobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface ProcessingJobResponse {
  jobId: number
  videoId: number
  jobType: JobType
  status: JobStatus
  sourceLanguage: string | null
  targetLanguage: string | null
  retryCount: number
  maxRetries: number
  progressPercent: number
  failureReason: FailureReason | null
  failureMessage: string | null
}

export type FailureReason =
  | 'NO_CAPTIONS_AVAILABLE'
  | 'INVALID_URL'
  | 'VIDEO_UNAVAILABLE'
  | 'VIDEO_PRIVATE'
  | 'VIDEO_REGION_LOCKED'
  | 'VIDEO_AGE_RESTRICTED'
  | 'SOURCE_LANGUAGE_UNDETECTABLE'
  | 'TOKENIZATION_FAILED'
  | 'TRANSLATION_FAILED'
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

export type UserRole = 'USER' | 'ADMIN'

export type UserStatus = 'ACTIVE' | 'LOCKED'

export interface MeResponse {
  userId: number
  email: string
  displayName: string | null
  role: UserRole
  status: UserStatus
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export type AdminDashboardRange = '24h' | '7d' | '30d' | 'all'

export interface DailyCountDto {
  date: string
  count: number
}

export interface FailureReasonCountDto {
  reason: FailureReason
  count: number
}

export interface AdminDashboardOverviewResponse {
  range: string
  users: {
    total: number
    active: number
    locked: number
    admins: number
    newInRange: number
    activeInRange: number
  }
  videos: {
    total: number
    ready: number
    processing: number
    pending: number
    failed: number
    createdInRange: number
  }
  jobs: {
    totalInRange: number
    pending: number
    processing: number
    completedInRange: number
    failedInRange: number
    successRatePercent: number
    byTypeInRange: Record<string, number>
  }
  cacheAndDictionary: {
    wordCacheReady: number
    wordCacheFailed: number
    totalDictionaryEntries: number
  }
  dailyVideos: DailyCountDto[]
  dailyFailedJobs: DailyCountDto[]
  failureReasons: FailureReasonCountDto[]
}

export interface AdminSystemHealthResponse {
  mysql: {
    status: string
    message: string
  }
  rabbitmq: {
    status: string
    message: string
    videoJobs: number
    retryJobs: number
    dlqJobs: number
  }
  ytDlp: {
    status: string
    version: string | null
    cookiesConfigured: boolean
    message: string
  }
  gemini: {
    status: string
    apiKeyConfigured: boolean
    model: string
    translationFailuresLast24h: number
    message: string
  }
}

export interface RabbitQueueStatsResponse {
  reachable: boolean
  videoJobs: number
  retryJobs: number
  dlqJobs: number
  bulkRetryMaxBatch: number
}

export interface AdminUserSummaryDto {
  id: number
  email: string
  displayName: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
  updatedAt: string
  activeSessionCount: number
  libraryVideoCount: number
  dictionaryEntryCount: number
}

export interface AdminUserPageResponse {
  page: number
  size: number
  totalElements: number
  totalPages: number
  users: AdminUserSummaryDto[]
}

export interface AdminUserRecentVideoDto {
  videoId: number
  youtubeVideoId: string
  title: string | null
  sourceLanguage: string | null
  lastWatchedAt: string
}

export interface AdminUserDetailResponse {
  id: number
  email: string
  displayName: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
  updatedAt: string
  activeSessionCount: number
  libraryVideoCount: number
  submittedVideoCount: number
  dictionaryEntryCount: number
  dictionaryFolderCount: number
  recentVideos: AdminUserRecentVideoDto[]
}

export interface RevokeSessionsResponse {
  userId: number
  revokedCount: number
}

export interface SendPasswordResetResponse {
  userId: number
  email: string
  expiresAt: string
}

export interface AdminJobItemDto {
  jobId: number
  videoId: number
  youtubeVideoId: string
  videoTitle: string | null
  requestedByUserId: number | null
  requestedByEmail: string | null
  jobType: JobType
  status: JobStatus
  sourceLanguage: string | null
  targetLanguage: string | null
  retryCount: number
  maxRetries: number
  progressPercent: number
  failureReason: FailureReason | null
  failureMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminJobPageResponse {
  page: number
  size: number
  totalElements: number
  totalPages: number
  jobs: AdminJobItemDto[]
}

export interface BulkRetryResponse {
  requestedCount: number
  retriedCount: number
  skippedIds: number[]
  createdJobIds: number[]
}

export interface PurgeDlqResponse {
  queueName: string
  purgedCount: number
}

export interface VideoSubmitResponse {
  videoId: number
  status: VideoStatus
  jobId?: number | null
}

export interface VideoStatusResponse {
  status: VideoStatus
  failureReason: FailureReason | null
  failureMessage: string | null
  title: string | null
  sourceLanguage: string | null
  durationSeconds: number | null
  youtubeVideoId: string | null
  totalLines: number | null
  processedLines: number | null
  progressPercent: number | null
  jobId?: number | null
  retryCount?: number | null
  maxRetries?: number | null
}

export interface VideoSummaryResponse {
  videoId: number
  title: string | null
  sourceLanguage: string | null
  durationSeconds: number | null
  status: VideoStatus
  youtubeVideoId: string | null
  lastPositionMs: number
}

export interface VideoLibraryItemResponse {
  videoId: number
  youtubeVideoId: string
  title: string | null
  sourceLanguage: string | null
  durationSeconds: number | null
  lastPositionMs: number
  addedAt: string
  lastWatchedAt: string
}

export interface VideoLibraryPageResponse {
  page: number
  size: number
  totalElements: number
  videos: VideoLibraryItemResponse[]
}

export interface VideoHistoryEntryResponse {
  videoId: number
  youtubeVideoId: string
  title: string | null
  sourceLanguage: string | null
  durationSeconds: number | null
  lastWatchedAt: string
}

export interface VideoHistoryPageResponse {
  page: number
  size: number
  totalElements: number
  entries: VideoHistoryEntryResponse[]
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
  translationAvailable: boolean
  activeTranslationJob: ProcessingJobResponse | null
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
  folderId?: number | null
}

export interface SaveDictionaryResponse {
  dictionaryEntryId: number
  wasNew: boolean
}

export interface DictionaryFolderResponse {
  id: number
  name: string
  sourceLanguage: string
  entryCount: number
  createdAt: string
}

export interface CreateDictionaryFolderRequest {
  name: string
  sourceLanguage: string
}

export interface DictionaryEntrySummaryResponse {
  id: number
  displayWord: string
  sourceLanguage: string
  targetLanguage: string
  folderId: number | null
  folderName: string | null
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
