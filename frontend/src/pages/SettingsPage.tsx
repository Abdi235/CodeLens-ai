import { useAuth } from '../auth/AuthContext'

export function SettingsPage() {
  const { user } = useAuth()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Settings</h1>
        <p className="mt-1 text-slate-600">Account and platform configuration.</p>
      </div>

      <section className="max-w-lg rounded-2xl border border-slate-200 bg-white/80 p-5">
        <h2 className="text-lg font-semibold">Profile</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <div className="flex justify-between gap-4 border-b border-slate-100 pb-2">
            <dt className="text-slate-500">Email</dt>
            <dd className="font-medium">{user?.email}</dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-slate-100 pb-2">
            <dt className="text-slate-500">Role</dt>
            <dd className="font-medium">{user?.role}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Auth</dt>
            <dd className="font-medium">JWT + BCrypt</dd>
          </div>
        </dl>
      </section>
    </div>
  )
}
