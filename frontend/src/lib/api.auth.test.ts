import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

function makeStorage() {
  const map = new Map<string, string>()
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => {
      map.set(key, value)
    },
    removeItem: (key: string) => {
      map.delete(key)
    },
    clear: () => {
      map.clear()
    },
  }
}

vi.stubGlobal('localStorage', makeStorage())
vi.stubGlobal('sessionStorage', makeStorage())

const { clearAuth, getRememberMePreference, getStoredUser, getToken, setAuth } = await import('./api')

beforeAll(() => {
  clearAuth()
})

afterEach(() => {
  clearAuth()
  localStorage.clear()
  sessionStorage.clear()
})

describe('auth remember me storage', () => {
  it('persists to localStorage when rememberMe is true', () => {
    setAuth({ email: 'a@example.com', username: null, role: 'USER', token: 'tok-local' }, true)
    expect(localStorage.getItem('codelens_token')).toBe('tok-local')
    expect(sessionStorage.getItem('codelens_token')).toBeNull()
    expect(getToken()).toBe('tok-local')
    expect(getStoredUser()?.email).toBe('a@example.com')
    expect(getRememberMePreference()).toBe(true)
  })

  it('uses sessionStorage when rememberMe is false', () => {
    setAuth({ email: 'b@example.com', username: null, role: 'USER', token: 'tok-session' }, false)
    expect(sessionStorage.getItem('codelens_token')).toBe('tok-session')
    expect(localStorage.getItem('codelens_token')).toBeNull()
    expect(getToken()).toBe('tok-session')
    expect(getRememberMePreference()).toBe(false)
  })

  it('clears both stores on logout', () => {
    setAuth({ email: 'c@example.com', username: null, role: 'USER', token: 'tok' }, true)
    clearAuth()
    expect(getToken()).toBeNull()
    expect(getStoredUser()).toBeNull()
  })
})
