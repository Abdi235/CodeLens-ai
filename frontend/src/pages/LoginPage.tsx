import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { API_BASE, api, getRememberMePreference } from '../lib/api'

type AuthResponse = {
  token: string
  email: string | null
  username: string | null
  role: string
}

type SignupMethod = 'email-service' | 'username' | 'password-email'
type OAuthProviders = { google: boolean; microsoft: boolean; outlook: boolean; mailEnabled: boolean }

const REMEMBERED_LOGIN_KEY = 'codelens_remembered_email'

export function LoginPage() {
  const { isAuthenticated, loginSuccess } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [signupMethod, setSignupMethod] = useState<SignupMethod>('email-service')
  const [providers, setProviders] = useState<OAuthProviders | null>(null)
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [loginId, setLoginId] = useState(() => localStorage.getItem(REMEMBERED_LOGIN_KEY) ?? '')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(() => getRememberMePreference())
  const [error, setError] = useState<string | null>(() => searchParams.get('oauth_error'))
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api<OAuthProviders>('/api/auth/oauth/providers')
      .then(setProviders)
      .catch(() => setProviders({ google: false, microsoft: false, outlook: false, mailEnabled: false }))
  }, [])

  if (isAuthenticated) return <Navigate to="/dashboard" replace />

  function startOAuth(provider: 'google' | 'microsoft') {
    setError(null)
    const enabled = provider === 'google' ? providers?.google : providers?.microsoft
    if (!enabled) {
      setError(
        `${provider === 'google' ? 'Gmail / Google' : 'Outlook / Microsoft'} sign-in is not configured yet. Add OAuth client credentials on the API, or use username / email + password.`,
      )
      return
    }
    window.location.href = `${API_BASE}/api/auth/oauth/${provider}/start`
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      let body: Record<string, string>
      let rememberValue = loginId

      if (mode === 'login') {
        body = { login: loginId.trim(), password }
        rememberValue = loginId.trim()
      } else if (signupMethod === 'username') {
        body = { username: username.trim(), password }
        rememberValue = username.trim()
      } else if (signupMethod === 'password-email') {
        body = { email: email.trim(), password }
        rememberValue = email.trim()
      } else {
        setError('Choose Gmail or Outlook to continue with an email service.')
        setLoading(false)
        return
      }

      const path = mode === 'login' ? '/api/auth/login' : '/api/auth/register'
      const data = await api<AuthResponse>(path, {
        method: 'POST',
        body: JSON.stringify(body),
      })
      loginSuccess(
        {
          token: data.token,
          email: data.email ?? null,
          username: data.username ?? null,
          role: data.role,
        },
        mode === 'login' ? rememberMe : true,
      )
      if (mode === 'login' && rememberMe) {
        localStorage.setItem(REMEMBERED_LOGIN_KEY, rememberValue)
      } else if (mode === 'login') {
        localStorage.removeItem(REMEMBERED_LOGIN_KEY)
      } else {
        localStorage.setItem(REMEMBERED_LOGIN_KEY, rememberValue)
      }
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

        {(mode === 'register' || mode === 'login') && (
          <div className="mb-5">
            <p className="mb-2 text-sm font-medium text-slate-700">
              {mode === 'register' ? 'Sign up with an email service' : 'Sign in with an email service'}
            </p>
            <p className="mb-3 text-xs text-slate-500">
              Choose your provider — you will authenticate there, then return to the CodeLens dashboard.
            </p>
            <div className="grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => startOAuth('google')}
                className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-50"
              >
                Gmail / Google
                <span className="mt-0.5 block text-xs font-normal text-slate-500">
                  {providers?.google ? 'Opens Google account picker' : 'Needs OAuth setup'}
                </span>
              </button>
              <button
                type="button"
                onClick={() => startOAuth('microsoft')}
                className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-left text-sm font-semibold text-slate-800 transition hover:bg-slate-50"
              >
                Outlook / Microsoft
                <span className="mt-0.5 block text-xs font-normal text-slate-500">
                  {providers?.microsoft ? 'Opens Microsoft account picker' : 'Needs OAuth setup'}
                </span>
              </button>
            </div>
          </div>
        )}

        <div className="mb-5 flex items-center gap-3 text-xs uppercase tracking-wide text-slate-400">
          <span className="h-px flex-1 bg-slate-200" />
          or
          <span className="h-px flex-1 bg-slate-200" />
        </div>

        {mode === 'register' && (
          <div className="mb-4 grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setSignupMethod('password-email')}
              className={`rounded-xl px-3 py-2 text-sm font-medium transition ${
                signupMethod === 'password-email'
                  ? 'bg-teal-50 text-teal-900 ring-1 ring-teal-200'
                  : 'bg-white text-slate-600 ring-1 ring-slate-200'
              }`}
            >
              Email + password
            </button>
            <button
              type="button"
              onClick={() => setSignupMethod('username')}
              className={`rounded-xl px-3 py-2 text-sm font-medium transition ${
                signupMethod === 'username'
                  ? 'bg-teal-50 text-teal-900 ring-1 ring-teal-200'
                  : 'bg-white text-slate-600 ring-1 ring-slate-200'
              }`}
            >
              Username
            </button>
          </div>
        )}

        {mode === 'register' && signupMethod === 'password-email' && (
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
        )}

        {mode === 'register' && signupMethod === 'username' && (
          <label className="mb-4 block text-sm font-medium text-slate-700">
            Username
            <input
              type="text"
              required
              minLength={3}
              maxLength={32}
              pattern="[A-Za-z0-9_]{3,32}"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="cl-input mt-1.5 w-full rounded-xl px-3.5 py-2.5"
              placeholder="codelens_user"
              autoComplete="username"
            />
          </label>
        )}

        {mode === 'login' && (
          <label className="mb-4 block text-sm font-medium text-slate-700">
            Email or username
            <input
              type="text"
              required
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              className="cl-input mt-1.5 w-full rounded-xl px-3.5 py-2.5"
              placeholder="you@gmail.com or codelens_user"
              autoComplete="username"
            />
          </label>
        )}

        {(mode === 'login' || signupMethod === 'password-email' || signupMethod === 'username') && (
          <label className="mb-4 block text-sm font-medium text-slate-700">
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
        )}

        {mode === 'login' && (
          <label className="mb-5 flex items-start gap-2.5 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-teal-700 focus:ring-teal-600"
            />
            <span>
              <span className="font-medium">Remember me</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                Stay signed in on this device. Uncheck to clear the session when you close the browser.
              </span>
            </span>
          </label>
        )}

        {error && (
          <p className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{error}</p>
        )}

        {(mode === 'login' || signupMethod === 'password-email' || signupMethod === 'username') && (
          <button type="submit" disabled={loading} className="cl-btn-primary w-full rounded-xl px-4 py-3">
            {loading
              ? 'Connecting to API… (cold start can take up to a minute)'
              : mode === 'login'
                ? 'Sign in with password'
                : 'Create account'}
          </button>
        )}

        {providers?.mailEnabled === false && mode === 'register' && (
          <p className="mt-3 text-xs text-slate-500">
            Welcome emails send when SMTP is configured on the API (`MAIL_*` env vars).
          </p>
        )}
      </form>

      <p className="cl-fade-up-delay mt-6 text-center text-xs text-slate-500">
        Free-tier backends may sleep when idle. First sign-in can take longer than usual.
      </p>
    </div>
  )
}
