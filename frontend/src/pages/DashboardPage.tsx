import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import { Doughnut } from 'react-chartjs-2'
import { api } from '../lib/api'

ChartJS.register(ArcElement, Tooltip, Legend)

type Project = { id: number; name: string; repositoryUrl?: string; createdAt: string }
type Vulnerability = { id: number; severity: string; type: string }
type AiMetrics = {
  explanations_generated?: number
  fixes_generated?: number
  fix_acceptance_rate?: number
  llm_enabled?: boolean
  model?: string | null
  error?: string
}

export function DashboardPage() {
  const projects = useQuery({
    queryKey: ['projects'],
    queryFn: () => api<Project[]>('/api/projects'),
  })
  const vulns = useQuery({
    queryKey: ['vulnerabilities'],
    queryFn: () => api<Vulnerability[]>('/api/vulnerabilities'),
  })
  const metrics = useQuery({
    queryKey: ['ai-metrics'],
    queryFn: () => api<AiMetrics>('/api/metrics/ai'),
    retry: false,
  })

  const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 }
  for (const v of vulns.data ?? []) {
    if (v.severity in counts) counts[v.severity as keyof typeof counts] += 1
  }

  const chartData = {
    labels: Object.keys(counts),
    datasets: [
      {
        data: Object.values(counts),
        backgroundColor: ['#b91c1c', '#c2410c', '#a16207', '#1d4ed8', '#64748b'],
        borderWidth: 0,
      },
    ],
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Dashboard</h1>
        <p className="mt-1 text-slate-600">Security posture across your scanned repositories.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Projects" value={projects.data?.length ?? 0} />
        <Stat label="Vulnerabilities" value={vulns.data?.length ?? 0} />
        <Stat label="High / Critical" value={counts.HIGH + counts.CRITICAL} />
        <Stat label="Fix accept %" value={metrics.data?.fix_acceptance_rate ?? 0} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-2xl border border-slate-200 bg-white/80 p-5">
          <h2 className="mb-4 text-lg font-semibold">Severity mix</h2>
          {(vulns.data?.length ?? 0) === 0 ? (
            <p className="text-sm text-slate-500">Run a project scan to populate this chart.</p>
          ) : (
            <div className="mx-auto max-w-xs">
              <Doughnut data={chartData} />
            </div>
          )}
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white/80 p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Recent projects</h2>
            <Link to="/projects" className="text-sm font-medium text-teal-700 hover:underline">
              View all
            </Link>
          </div>
          <ul className="space-y-3">
            {(projects.data ?? []).slice(0, 5).map((p) => (
              <li key={p.id}>
                <Link to={`/project/${p.id}`} className="block rounded-lg border border-slate-100 px-3 py-2 hover:bg-slate-50">
                  <div className="font-medium">{p.name}</div>
                  <div className="truncate text-xs text-slate-500">{p.repositoryUrl || 'No repository URL'}</div>
                </Link>
              </li>
            ))}
            {(projects.data?.length ?? 0) === 0 && (
              <li className="text-sm text-slate-500">No projects yet. Create one to start scanning.</li>
            )}
          </ul>

          <div className="mt-6 border-t border-slate-100 pt-4 text-sm text-slate-600">
            <div className="font-medium text-slate-800">AI evaluation</div>
            {metrics.data?.error ? (
              <p className="mt-1">AI service offline — start it on :8000 to track metrics.</p>
            ) : (
              <ul className="mt-1 space-y-1">
                <li>Explanations: {metrics.data?.explanations_generated ?? 0}</li>
                <li>Fixes generated: {metrics.data?.fixes_generated ?? 0}</li>
                <li>LLM: {metrics.data?.llm_enabled ? metrics.data.model : 'template fallback'}</li>
              </ul>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white/80 px-5 py-4">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-3xl font-semibold tabular-nums">{value}</div>
    </div>
  )
}
