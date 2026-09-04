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

type SystemMetrics = {
  uptimeSeconds: number
  requestCount: number
  errorCount: number
  errorRatePercent: number
  avgLatencyMs: number
  p95LatencyMs: number
  dependencies: Record<string, string>
  pipeline: {
    queued: number
    processing: number
    completed: number
    failed: number
    avgProcessingDurationMs: number | null
  }
}

function formatUptime(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const mins = Math.floor(seconds / 60)
  if (mins < 60) return `${mins}m ${seconds % 60}s`
  const hours = Math.floor(mins / 60)
  const remMins = mins % 60
  if (hours < 48) return `${hours}h ${remMins}m`
  const days = Math.floor(hours / 24)
  return `${days}d ${hours % 24}h`
}

function depTone(status: string | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'UP':
      return 'bg-emerald-50 text-emerald-800 ring-emerald-200'
    case 'DISABLED':
      return 'bg-slate-100 text-slate-600 ring-slate-200'
    default:
      return 'bg-rose-50 text-rose-800 ring-rose-200'
  }
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
  const system = useQuery({
    queryKey: ['system-metrics'],
    queryFn: () => api<SystemMetrics>('/api/metrics/system'),
    refetchInterval: 20_000,
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

  const deps = system.data?.dependencies ?? {}
  const pipeline = system.data?.pipeline

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Dashboard</h1>
        <p className="mt-1 text-slate-600">
          Service health (uptime, latency, errors) and security posture across scanned repositories.
        </p>
      </div>

      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold">Service health</h2>
          <p className="text-sm text-slate-500">API monitoring — distinct from vulnerability analytics below.</p>
        </div>

        {system.isError ? (
          <p className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
            Unable to load system metrics. Confirm the API is reachable and you are signed in.
          </p>
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Stat
                label="Uptime"
                value={system.data ? formatUptime(system.data.uptimeSeconds) : '—'}
              />
              <Stat
                label="Error rate"
                value={system.data ? `${system.data.errorRatePercent}%` : '—'}
              />
              <Stat
                label="Avg latency"
                value={system.data ? `${system.data.avgLatencyMs} ms` : '—'}
              />
              <Stat
                label="p95 latency"
                value={system.data ? `${system.data.p95LatencyMs} ms` : '—'}
              />
            </div>

            <div className="flex flex-wrap gap-2">
              {(['api', 'database', 'rabbitmq', 'ai'] as const).map((key) => (
                <span
                  key={key}
                  className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ring-1 ring-inset ${depTone(deps[key])}`}
                >
                  <span className="uppercase tracking-wide">{key}</span>
                  <span>{deps[key] ?? '…'}</span>
                </span>
              ))}
              {system.data && (
                <span className="inline-flex items-center rounded-full bg-slate-50 px-3 py-1 text-xs text-slate-600 ring-1 ring-inset ring-slate-200">
                  {system.data.requestCount} requests · {system.data.errorCount} server errors
                </span>
              )}
            </div>

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              <PipelineStat label="Queued" value={pipeline?.queued} />
              <PipelineStat label="Processing" value={pipeline?.processing} />
              <PipelineStat label="Completed" value={pipeline?.completed} />
              <PipelineStat label="Failed" value={pipeline?.failed} />
              <PipelineStat
                label="Avg job duration"
                value={
                  pipeline?.avgProcessingDurationMs != null
                    ? `${pipeline.avgProcessingDurationMs} ms`
                    : '—'
                }
              />
            </div>
          </>
        )}
      </section>

      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold">Security posture</h2>
          <p className="text-sm text-slate-500">Product analytics from scans and AI remediation.</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Stat label="Projects" value={projects.data?.length ?? 0} />
          <Stat label="Vulnerabilities" value={vulns.data?.length ?? 0} />
          <Stat label="High / Critical" value={counts.HIGH + counts.CRITICAL} />
          <Stat label="Fix accept %" value={metrics.data?.fix_acceptance_rate ?? 0} />
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-2xl border border-slate-200 bg-white/80 p-5">
            <h3 className="mb-4 text-lg font-semibold">Severity mix</h3>
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
              <h3 className="text-lg font-semibold">Recent projects</h3>
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
      </section>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white/80 px-5 py-4">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-3xl font-semibold tabular-nums">{value}</div>
    </div>
  )
}

function PipelineStat({ label, value }: { label: string; value: string | number | undefined }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white/70 px-4 py-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-0.5 text-xl font-semibold tabular-nums text-slate-800">{value ?? '—'}</div>
    </div>
  )
}
