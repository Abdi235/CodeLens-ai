const TOKEN_KEY = 'codelens_token'
const USER_KEY = 'codelens_user'
const REMEMBER_KEY = 'codelens_remember'
const LEGACY_TOKEN_KEY = 'secureai_token'
const LEGACY_USER_KEY = 'secureai_user'

/** Empty in local/dev (Vite proxy). Set VITE_API_URL on Render to the API service URL. */
export const API_BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

export type AuthUser = {
  email: string
  role: string
  token: string
}

type AuthStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function rememberPreference(): boolean {
  // Default true so existing localStorage sessions keep working after upgrade.
  const raw = localStorage.getItem(REMEMBER_KEY)
  if (raw === null) return true
  return raw === '1'
}

function activeStore(remember = rememberPreference()): AuthStore {
  return remember ? localStorage : sessionStorage
}

function migrateLegacyAuth() {
  if (typeof localStorage === 'undefined') return
  const legacyToken = localStorage.getItem(LEGACY_TOKEN_KEY)
  if (!legacyToken) return
  if (!localStorage.getItem(TOKEN_KEY)) {
    localStorage.setItem(TOKEN_KEY, legacyToken)
    const legacyUser = localStorage.getItem(LEGACY_USER_KEY)
    if (legacyUser) localStorage.setItem(USER_KEY, legacyUser)
    localStorage.setItem(REMEMBER_KEY, '1')
  }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(LEGACY_USER_KEY)
}

migrateLegacyAuth()

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY) ?? localStorage.getItem(TOKEN_KEY)
}

export function setAuth(user: AuthUser, rememberMe = true) {
  const primary = activeStore(rememberMe)
  const secondary = rememberMe ? sessionStorage : localStorage

  // Keep a single source of truth so logout / refresh stay consistent.
  secondary.removeItem(TOKEN_KEY)
  secondary.removeItem(USER_KEY)

  primary.setItem(TOKEN_KEY, user.token)
  primary.setItem(USER_KEY, JSON.stringify({ email: user.email, role: user.role }))
  localStorage.setItem(REMEMBER_KEY, rememberMe ? '1' : '0')
}

export function clearAuth() {
  for (const store of [localStorage, sessionStorage]) {
    store.removeItem(TOKEN_KEY)
    store.removeItem(USER_KEY)
    store.removeItem(LEGACY_TOKEN_KEY)
    store.removeItem(LEGACY_USER_KEY)
  }
  localStorage.removeItem(REMEMBER_KEY)
}

export function getStoredUser(): { email: string; role: string } | null {
  const raw = sessionStorage.getItem(USER_KEY) ?? localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as { email: string; role: string }
  } catch {
    return null
  }
}

export function getRememberMePreference(): boolean {
  return rememberPreference()
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const body = (await res.json()) as { message?: string }
      if (body.message) message = body.message
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
