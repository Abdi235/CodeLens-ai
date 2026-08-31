import { Navigate, Outlet, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const links = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/analysis', label: 'Analysis' },
  { to: '/search', label: 'Search' },
  { to: '/projects', label: 'Projects' },
  { to: '/vulnerabilities', label: 'Vulnerabilities' },
  { to: '/settings', label: 'Settings' },
]

export function ProtectedLayout() {
  const { isAuthenticated, user, logout } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200/80 bg-white/70 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-4">
          <div className="flex items-center gap-8">
            <NavLink to="/dashboard" className="font-[family-name:var(--font-display)] text-xl font-semibold tracking-tight text-[var(--color-ink)]">
              CodeLens
            </NavLink>
            <nav className="hidden gap-1 sm:flex">
              {links.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  className={({ isActive }) =>
                    `rounded-md px-3 py-1.5 text-sm font-medium transition ${
                      isActive ? 'bg-teal-50 text-teal-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                    }`
                  }
                >
                  {link.label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3 text-sm text-slate-600">
            <span className="hidden md:inline">{user?.email}</span>
            <button
              type="button"
              onClick={logout}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
