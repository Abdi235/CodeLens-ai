import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api, getRememberMePreference } from '../lib/api'

type AuthResponse = {
  token: string
  email: string | null
  username: string | null
  role: string
}

type SignupMethod = 'email' | 'username'
type EmailProvider = {
  id: string
  label: string
  domain: string | null
}

const REMEMBERED_LOGIN_KEY = 'codelens_remembered_email'

const EMAIL_PROVIDERS: EmailProvider[] = [
  { id: 'gmail', label: 'Gmail', domain: 'gmail.com' },
  { id: 'outlook', label: 'Outlook', domain: 'outlook.com' },
  { id: 'yahoo', label: 'Yahoo', domain: 'yahoo.com' },
  { id: 'icloud', label: 'iCloud', domain: 'icloud.com' },
  { id: 'other', label: 'Other email', domain: null },
]

export function LoginPage() {
  const { isAuthenticated, loginSuccess } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [signupMethod, setSignupMethod] = useState<SignupMethod>('email')
  const [providerId, setProviderId] = useState('gmail')
  const [emailLocal, setEmailLocal] = useState('')
  const [emailFull, setEmailFull] = useState(() => localStorage.getItem(REMEMBERED_LOGIN_KEY) ?? '')
  const [username, setUsername] = useState('')
  const [loginId, setLoginId] = useState(() => localStorage.getItem(REMEMBERED_LOGIN_KEY) ?? '')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(() => getRememberMePreference())
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (isAuthenticated) return <Navigate to="/dashboard" replace />

  const selectedProvider = EMAIL_PROVIDERS.find((p) => p.id === providerId) ?? EMAIL_PROVIDERS[0]

  function composedEmail(): string {
    if (selectedProvider.domain == null) return emailFull.trim()
    const local = emailLocal.trim()
    if (!local) return ''
    return `${local}@${selectedProvider.domain}`
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
      } else {
        const email = composedEmail()
        if (!email) {
          setError('Enter your email address')
          setLoading(false)
          return
        }
        body = { email, password }
        rememberValue = email
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

        {mode === 'register' && (
          <div className="mb-5">
            <p className="mb-2 text-sm font-medium text-slate-700">Create account with</p>
            <div className="mb-4 grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setSignupMethod('email')}
                className={`rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                  signupMethod === 'email'
                    ? 'bg-teal-50 text-teal-900 ring-1 ring-teal-200'
                    : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'
                }`}
              >
                Email
              </button>
              <button
                type="button"
                onClick={() => setSignupMethod('username')}
                className={`rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                  signupMethod === 'username'
                    ? 'bg-teal-50 text-teal-900 ring-1 ring-teal-200'
                    : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'
                }`}
              >
                Username
              </button>
            </div>

            {signupMethod === 'email' && (
              <>
                <p className="mb-2 text-xs text-slate-500">Choose your email service</p>
                <div className="mb-3 flex flex-wrap gap-2">
                  {EMAIL_PROVIDERS.map((provider) => (
                    <button
                      key={provider.id}
                      type="button"
                      onClick={() => setProviderId(provider.id)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                        providerId === provider.id
                          ? 'bg-slate-900 text-white'
                          : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                      }`}
                    >
                      {provider.label}
                    </button>
                  ))}
                </div>

                {selectedProvider.domain ? (
                  <label className="mb-4 block text-sm font-medium text-slate-700">
                    {selectedProvider.label} address
                    <div className="mt-1.5 flex items-stretch overflow-hidden rounded-xl ring-1 ring-slate-200 focus-within:ring-2 focus-within:ring-teal-200">
                      <input
                        type="text"
                        required
                        value={emailLocal}
                        onChange={(e) => setEmailLocal(e.target.value.replace(/@.*$/, ''))}
                        className="cl-input min-w-0 flex-1 rounded-none border-0 px-3.5 py-2.5 focus:shadow-none"
                        placeholder="you"
                        autoComplete="username"
                      />
                      <span className="flex items-center bg-slate-50 px-3 text-sm text-slate-500">
                        @{selectedProvider.domain}
                      </span>
                    </div>
                  </label>
                ) : (
                  <label className="mb-4 block text-sm font-medium text-slate-700">
                    Email
                    <input
                      type="email"
                      required
                      value={emailFull}
                      onChange={(e) => setEmailFull(e.target.value)}
                      className="cl-input mt-1.5 w-full rounded-xl px-3.5 py-2.5"
                      placeholder="you@company.com"
                      autoComplete="email"
                    />
                  </label>
                )}
              </>
            )}

            {signupMethod === 'username' && (
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
                <span className="mt-1 block text-xs text-slate-500">
                  3–32 characters. Letters, numbers, and underscores only — no email required.
                </span>
              </label>
            )}
          </div>
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

        <button type="submit" disabled={loading} className="cl-btn-primary w-full rounded-xl px-4 py-3">
          {loading
            ? 'Connecting to API… (cold start can take up to a minute)'
            : mode === 'login'
              ? 'Sign in'
              : signupMethod === 'username'
                ? 'Create username account'
                : 'Create email account'}
        </button>
      </form>

      <p className="cl-fade-up-delay mt-6 text-center text-xs text-slate-500">
        Free-tier backends may sleep when idle. First sign-in can take longer than usual.
      </p>
    </div>
  )
}
