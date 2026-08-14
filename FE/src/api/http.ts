import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth.store'
import router from '@/router'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    config.headers.set('Authorization', `Bearer ${authStore.accessToken}`)
  }
  return config
})

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

let refreshPromise: Promise<void> | null = null

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined
    const isRefreshCall = config?.url?.includes('/auth/refresh')

    if (error.response?.status === 401 && config && !config._retried && !isRefreshCall) {
      const authStore = useAuthStore()
      if (!authStore.refreshToken) {
        authStore.clear()
        router.push({ name: 'login' })
        return Promise.reject(error)
      }

      config._retried = true
      try {
        refreshPromise ??= authStore.refreshTokens().finally(() => {
          refreshPromise = null
        })
        await refreshPromise
        return http(config)
      } catch (refreshError) {
        authStore.clear()
        router.push({ name: 'login' })
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  },
)

export default http
