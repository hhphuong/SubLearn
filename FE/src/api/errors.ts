import { isAxiosError } from 'axios'

export interface ApiError {
  code: string
  message: string
}

export function toApiError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    const body = error.response?.data as Partial<ApiError> | undefined
    if (body?.code && body?.message) {
      return { code: body.code, message: body.message }
    }
    if (error.response) {
      return { code: 'HTTP_ERROR', message: `Request failed with status ${error.response.status}` }
    }
    return { code: 'NETWORK_ERROR', message: 'Could not reach the server. Please check your connection.' }
  }
  return { code: 'UNKNOWN_ERROR', message: 'Something went wrong, please try again.' }
}
