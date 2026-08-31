import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
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
    <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-4 py-12">
      <div className="mb-8 text-center">
        <h1 className="font-[family-name:var(--font-display)] text-4xl font-semibold tracking-tight text-[var(--color-ink)]">
          CodeLens
        </h1>
        <p className="mt-2 text-slate-600">Distributed code intelligence &amp; security analysis.</p>
      </div>

      <form onSubmit={onSubmit} className="rounded-2xl border border-slate-200 bg-white/80 p-6 shadow-sm backdrop-blur">
        <div className="mb-5 flex rounded-lg bg-slate-100 p-1 text-sm font-medium">
          <button
            type="button"
            className={`flex-1 rounded-md py-2 ${mode === 'login' ? 'bg-white shadow-sm' : 'text-slate-600'}`}
            onClick={() => setMode('login')}
          >
            Sign in
          </button>
          <button
            type="button"
            className={`flex-1 rounded-md py-2 ${mode === 'register' ? 'bg-white shadow-sm' : 'text-slate-600'}`}
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
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
          />
        </label>

        <label className="mb-4 block text-sm font-medium text-slate-700">
          Password
          <input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
          />
        </label>

        {error && <p className="mb-3 text-sm text-red-700">{error}</p>}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-lg bg-teal-700 px-4 py-2.5 font-semibold text-white hover:bg-teal-800 disabled:opacity-60"
        >
          {loading ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-slate-500">
        Week 1 foundation — JWT auth + project APIs ready.
        <Link className="ml-1 text-teal-700 hover:underline" to="/login">
          Local H2 by default
        </Link>
      </p>
    </div>
  )
}
