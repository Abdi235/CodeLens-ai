import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'

type AuthResponse = { token: string; email: string; role: string }

export function LoginPage() {
  const { isAuthenticated, loginSuccess } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (isAuthenticated) return <Navigate to="/dashboard" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const path = mode === 'login' ? '/api/auth/login' : '/api/auth/register'
      const data = await api<AuthResponse>(path, {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      loginSuccess(data)
      navigate('/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative mx-auto flex min-h-screen max-w-lg flex-col justify-center px-4 py-12">
      <div className="cl-fade-up mb-10 text-center">
        <p className="mb-3 text-xs font-semibold uppercase tracking-[0.22em] text-teal-800/80">Code intelligence</p>
        <h1 className="font-[family-name:var(--font-display)] text-5xl font-semibold tracking-tight text-[var(--color-ink)]">
          CodeLens
        </h1>
        <p className="mx-auto mt-3 max-w-sm text-slate-600">
          Distributed analysis, BM25 search, and an autonomous ops agent for live service health.
        </p>
      </div>

      <form onSubmit={onSubmit} className="cl-fade-up-delay cl-panel rounded-2xl p-7">
        <div className="mb-6 flex rounded-xl bg-slate-100/90 p-1 text-sm font-medium">
          <button
            type="button"
            className={`flex-1 rounded-lg py-2.5 transition ${mode === 'login' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
            onClick={() => setMode('login')}
          >
            Sign in
          </button>
          <button
            type="button"
            className={`flex-1 rounded-lg py-2.5 transition ${mode === 'register' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
            onClick={() => setMode('register')}
          >
            Register
          </button>
        </div>

        <label className="mb-4 block text-sm font-medium text-slate-700">
          Email
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="cl-input mt-1.5 w-full rounded-xl px-3.5 py-2.5"
            autoComplete="email"
          />
        </label>

        <label className="mb-5 block text-sm font-medium text-slate-700">
          Password
          <input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="cl-input mt-1.5 w-full rounded-xl px-3.5 py-2.5"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          />
        </label>

        {error && (
          <p className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{error}</p>
        )}

        <button type="submit" disabled={loading} className="cl-btn-primary w-full rounded-xl px-4 py-3">
          {loading
            ? 'Connecting to API… (cold start can take up to a minute)'
            : mode === 'login'
              ? 'Sign in'
              : 'Create account'}
        </button>
      </form>

      <p className="cl-fade-up-delay mt-6 text-center text-xs text-slate-500">
        Free-tier backends may sleep when idle. First sign-in can take longer than usual.
      </p>
    </div>
  )
}
