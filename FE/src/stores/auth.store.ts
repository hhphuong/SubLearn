import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth.api'
import type { MeResponse } from '@/types/api'

const STORAGE_KEY = 'sublearn.auth'

interface PersistedAuth {
  accessToken: string | null
  refreshToken: string | null
  expiresAt: number | null
  user: MeResponse | null
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const expiresAt = ref<number | null>(null)
  const user = ref<MeResponse | null>(null)

  const isAuthenticated = computed(() => !!accessToken.value)

  function persist() {
    const payload: PersistedAuth = {
      accessToken: accessToken.value,
      refreshToken: refreshToken.value,
      expiresAt: expiresAt.value,
      user: user.value,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  }

  function hydrate() {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    try {
      const parsed = JSON.parse(raw) as PersistedAuth
      accessToken.value = parsed.accessToken
      refreshToken.value = parsed.refreshToken
      expiresAt.value = parsed.expiresAt
      user.value = parsed.user
    } catch {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  function setTokens(tokens: { accessToken: string; refreshToken: string; expiresIn: number }) {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    expiresAt.value = Date.now() + tokens.expiresIn * 1000
    persist()
  }

  function clear() {
    accessToken.value = null
    refreshToken.value = null
    expiresAt.value = null
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  async function fetchMe() {
    user.value = await authApi.me()
    persist()
  }

  async function login(email: string, password: string) {
    const response = await authApi.login({ email, password })
    setTokens(response)
    await fetchMe()
  }

  async function register(email: string, password: string, displayName?: string) {
    await authApi.register({ email, password, displayName })
    // register only returns { userId }, no tokens - log in immediately for a seamless UX
    await login(email, password)
  }

  let refreshInFlight: Promise<void> | null = null

  function refreshTokens(): Promise<void> {
    if (!refreshInFlight) {
      refreshInFlight = (async () => {
        if (!refreshToken.value) {
          throw new Error('No refresh token available')
        }
        const response = await authApi.refresh(refreshToken.value)
        setTokens(response)
      })().finally(() => {
        refreshInFlight = null
      })
    }
    return refreshInFlight
  }

  async function logout() {
    if (refreshToken.value) {
      try {
        await authApi.logout(refreshToken.value)
      } catch {
        // best-effort - clear local state regardless
      }
    }
    clear()
  }

  return {
    accessToken,
    refreshToken,
    expiresAt,
    user,
    isAuthenticated,
    hydrate,
    login,
    register,
    refreshTokens,
    logout,
    fetchMe,
    clear,
  }
})
