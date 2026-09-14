import { Navigate, Outlet, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const links = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/analysis', label: 'Analysis' },
  { to: '/search', label: 'Search' },
  { to: '/ops-agent', label: 'Ops Agent' },
  { to: '/projects', label: 'Projects' },
  { to: '/vulnerabilities', label: 'Vulnerabilities' },
  { to: '/settings', label: 'Settings' },
]

export function ProtectedLayout() {
  const { isAuthenticated, user, logout } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-20 border-b border-slate-200/70 bg-white/75 backdrop-blur-xl">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3.5">
          <div className="flex min-w-0 items-center gap-7">
            <NavLink
              to="/dashboard"
              className="font-[family-name:var(--font-display)] text-xl font-semibold tracking-tight text-[var(--color-ink)]"
            >
              CodeLens
            </NavLink>
            <nav className="hidden items-center gap-0.5 lg:flex">
              {links.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                      isActive
                        ? 'bg-teal-50 text-teal-900 ring-1 ring-teal-100'
                        : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900'
                    }`
                  }
                >
                  {link.label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3 text-sm text-slate-600">
            <span className="hidden items-center gap-2 md:inline-flex">
              <span className="cl-status-dot" aria-hidden />
              <span className="max-w-[200px] truncate">{user?.username || user?.email}</span>
            </span>
            <button
              type="button"
              onClick={logout}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 font-medium text-slate-700 transition hover:bg-slate-50"
            >
              Sign out
            </button>
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto border-t border-slate-100 px-3 py-2 lg:hidden">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-medium ${
                  isActive ? 'bg-teal-50 text-teal-900' : 'text-slate-600'
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="cl-fade-up mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
