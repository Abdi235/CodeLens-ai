import { useAuth } from '../auth/AuthContext'

export function SettingsPage() {
  const { user } = useAuth()

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold tracking-tight">Settings</h1>
        <p className="mt-1 text-slate-600">Account and platform configuration.</p>
      </div>

      <section className="cl-panel max-w-lg rounded-2xl p-6">
        <h2 className="text-lg font-semibold">Profile</h2>
        <dl className="mt-4 space-y-3 text-sm">
          <div className="flex justify-between gap-4 border-b border-slate-100 pb-3">
            <dt className="text-slate-500">Email</dt>
            <dd className="font-medium">{user?.email}</dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-slate-100 pb-3">
            <dt className="text-slate-500">Role</dt>
            <dd className="font-medium">{user?.role}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Auth</dt>
            <dd className="font-medium">JWT + BCrypt</dd>
          </div>
        </dl>
      </section>

      <section className="cl-panel max-w-lg rounded-2xl p-6">
        <h2 className="text-lg font-semibold">Platform</h2>
        <p className="mt-2 text-sm leading-relaxed text-slate-600">
          Analysis jobs run on CloudAMQP-backed workers. Ops Agent remediations use live service health
          from the API. Optional OpenAI credentials enable tool-calling explanations and agent brains.
        </p>
      </section>
    </div>
  )
}
