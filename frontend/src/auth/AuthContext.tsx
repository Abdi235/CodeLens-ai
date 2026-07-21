import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { clearAuth, getStoredUser, getToken, setAuth, type AuthUser } from '../lib/api'

type AuthContextValue = {
  user: { email: string; role: string } | null
  isAuthenticated: boolean
  loginSuccess: (auth: AuthUser) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState(() => (getToken() ? getStoredUser() : null))

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: Boolean(user && getToken()),
      loginSuccess: (auth) => {
        setAuth(auth)
        setUser({ email: auth.email, role: auth.role })
      },
      logout: () => {
        clearAuth()
        setUser(null)
      },
    }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
