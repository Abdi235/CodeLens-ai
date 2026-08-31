export const API_BASE = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080'

const TOKEN_KEY = 'secureai_token'

export async function getToken(): Promise<string | null> {
  const AsyncStorage = (await import('@react-native-async-storage/async-storage')).default
  return AsyncStorage.getItem(TOKEN_KEY)
}

export async function setToken(token: string) {
  const AsyncStorage = (await import('@react-native-async-storage/async-storage')).default
  await AsyncStorage.setItem(TOKEN_KEY, token)
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getToken()
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error((body as { message?: string }).message ?? `Request failed (${res.status})`)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
