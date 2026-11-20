import type { FailureReason } from '@/types/api'

export const VIDEO_CREATE_ERROR_MESSAGE = 'có lỗi khi tạo video! vui lòng thử lại.'

export function failureMessage(_reason?: FailureReason | null): string {
  return VIDEO_CREATE_ERROR_MESSAGE
}
