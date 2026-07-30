import http from '@/api/http'
import type { AuthResponse, LoginRequest, MeResponse, RegisterRequest, RegisterResponse } from '@/types/api'

export const authApi = {
  register(request: RegisterRequest) {
    return http.post<RegisterResponse>('/api/auth/register', request).then((r) => r.data)
  },
  login(request: LoginRequest) {
    return http.post<AuthResponse>('/api/auth/login', request).then((r) => r.data)
  },
  refresh(refreshToken: string) {
    return http.post<AuthResponse>('/api/auth/refresh', { refreshToken }).then((r) => r.data)
  },
  logout(refreshToken: string) {
    return http.post<void>('/api/auth/logout', { refreshToken }).then((r) => r.data)
  },
  me() {
    return http.get<MeResponse>('/api/auth/me').then((r) => r.data)
  },
}
