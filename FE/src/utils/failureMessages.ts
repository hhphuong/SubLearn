import type { FailureReason } from '@/types/api'

const MESSAGES: Record<FailureReason, string> = {
  NO_CAPTIONS_AVAILABLE: 'Video này không có phụ đề khả dụng (cả thủ công lẫn tự động).',
  INVALID_URL: 'Đường dẫn không hợp lệ. Vui lòng kiểm tra lại link YouTube.',
  VIDEO_UNAVAILABLE: 'Video này không còn tồn tại hoặc đã bị gỡ bỏ.',
  VIDEO_PRIVATE: 'Video này ở chế độ riêng tư, không thể truy cập.',
  VIDEO_REGION_LOCKED: 'Video này bị giới hạn ở khu vực của bạn.',
  VIDEO_AGE_RESTRICTED: 'Video này bị giới hạn độ tuổi.',
  SOURCE_LANGUAGE_UNDETECTABLE: 'Không thể xác định ngôn ngữ gốc của phụ đề.',
  TOKENIZATION_FAILED: 'Có lỗi khi xử lý ngôn ngữ của phụ đề. Vui lòng thử lại.',
  PROCESS_TIMEOUT: 'Quá trình xử lý video mất quá nhiều thời gian. Vui lòng thử lại.',
  UNKNOWN_ERROR: 'Đã xảy ra lỗi không xác định. Vui lòng thử lại.',
}

export function failureMessage(reason: FailureReason | null | undefined): string {
  if (!reason) return MESSAGES.UNKNOWN_ERROR
  return MESSAGES[reason] ?? MESSAGES.UNKNOWN_ERROR
}
