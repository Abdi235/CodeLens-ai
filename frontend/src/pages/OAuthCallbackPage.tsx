import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'

type AuthResponse = {
  token: string
  email: string | null
  username: string | null
  role: string
}

export function OAuthCallbackPage() {
  const { isAuthenticated, loginSuccess } = useAuth()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = params.get('code')
    if (!code) {
      setError('Missing sign-in code from your email provider.')
      return
    }

    let cancelled = false
    ;(async () => {
      try {
        const data = await api<AuthResponse>('/api/auth/oauth/exchange', {
          method: 'POST',
          body: JSON.stringify({ code }),
        })
        if (cancelled) return
        loginSuccess(
          {
            token: data.token,
            email: data.email ?? null,
            username: data.username ?? null,
            role: data.role,
          },
          true,
        )
        navigate('/dashboard', { replace: true })
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'OAuth sign-in failed')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [params, loginSuccess, navigate])

  if (isAuthenticated && !error) return <Navigate to="/dashboard" replace />

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-4 text-center">
      <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">CodeLens</h1>
      {error ? (
        <>
          <p className="mt-4 text-sm text-rose-700">{error}</p>
          <a href="/login" className="mt-6 text-sm font-medium text-teal-800 hover:underline">
            Back to sign in
          </a>
        </>
      ) : (
        <p className="mt-4 text-sm text-slate-600">Finishing sign-in and opening your dashboard…</p>
      )}
    </div>
  )
}
