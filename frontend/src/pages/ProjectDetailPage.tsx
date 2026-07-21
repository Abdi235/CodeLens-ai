import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'

type Project = { id: number; name: string; repositoryUrl?: string; createdAt: string }
type Scan = { id: number; status: string; startedAt: string; completedAt?: string; vulnerabilityCount: number }
type Vulnerability = {
  id: number
  severity: string
  type: string
  fileLocation?: string
  lineNumber?: number
  description?: string
  recommendation?: string
  suggestedFix?: string
}

export function ProjectDetailPage() {
  const { id } = useParams()
  const projectId = Number(id)
  const queryClient = useQueryClient()

  const project = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => api<Project>(`/api/projects/${projectId}`),
    enabled: Number.isFinite(projectId),
  })

  const scans = useQuery({
    queryKey: ['scans', projectId],
    queryFn: () => api<Scan[]>(`/api/projects/${projectId}/scans`),
    enabled: Number.isFinite(projectId),
  })

  const vulns = useQuery({
    queryKey: ['project-vulns', projectId],
    queryFn: () => api<Vulnerability[]>(`/api/projects/${projectId}/vulnerabilities`),
    enabled: Number.isFinite(projectId),
  })

  const runScan = useMutation({
    mutationFn: () => api<Scan>(`/api/projects/${projectId}/scan`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scans', projectId] })
      queryClient.invalidateQueries({ queryKey: ['project-vulns', projectId] })
      queryClient.invalidateQueries({ queryKey: ['vulnerabilities'] })
    },
  })

  const generateFix = useMutation({
    mutationFn: (vulnerabilityId: number) =>
      api('/api/fix/generate', {
        method: 'POST',
        body: JSON.stringify({ vulnerabilityId }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-vulns', projectId] })
      queryClient.invalidateQueries({ queryKey: ['vulnerabilities'] })
    },
  })

  if (project.isLoading) return <p>Loading project…</p>
  if (project.isError || !project.data) {
    return (
      <p className="text-red-700">
        Project not found. <Link to="/projects">Back to projects</Link>
      </p>
    )
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/projects" className="text-sm text-teal-700 hover:underline">
            ← Projects
          </Link>
          <h1 className="mt-2 font-[family-name:var(--font-display)] text-3xl font-semibold">{project.data.name}</h1>
          <p className="mt-1 text-slate-600">{project.data.repositoryUrl || 'No repository URL configured'}</p>
        </div>
        <button
          type="button"
          onClick={() => runScan.mutate()}
          disabled={runScan.isPending}
          className="rounded-lg bg-teal-700 px-4 py-2.5 font-semibold text-white hover:bg-teal-800 disabled:opacity-60"
        >
          {runScan.isPending ? 'Scanning…' : 'Run scan'}
        </button>
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white/80 p-5">
        <h2 className="mb-3 text-lg font-semibold">Scan history</h2>
        <ul className="space-y-2 text-sm">
          {(scans.data ?? []).map((s) => (
            <li key={s.id} className="flex flex-wrap justify-between gap-2 border-b border-slate-100 py-2 last:border-0">
              <span>
                Scan #{s.id} · <span className="font-medium">{s.status}</span>
              </span>
              <span className="text-slate-500">
                {s.vulnerabilityCount} findings · {new Date(s.startedAt).toLocaleString()}
              </span>
            </li>
          ))}
          {(scans.data?.length ?? 0) === 0 && <li className="text-slate-500">No scans yet.</li>}
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Vulnerabilities</h2>
        {(vulns.data ?? []).map((v) => (
          <article key={v.id} className="rounded-2xl border border-slate-200 bg-white/80 p-5">
            <div className="flex flex-wrap items-center gap-2">
              <SeverityBadge severity={v.severity} />
              <h3 className="font-semibold">{v.type}</h3>
            </div>
            <p className="mt-2 text-sm text-slate-600">
              {v.fileLocation}
              {v.lineNumber != null ? `:${v.lineNumber}` : ''}
            </p>
            <p className="mt-2 text-sm">{v.description}</p>
            <p className="mt-2 text-sm text-slate-700">
              <span className="font-medium">Fix:</span> {v.recommendation}
            </p>
            {v.suggestedFix && (
              <pre className="mt-3 overflow-x-auto rounded-lg bg-slate-900 p-3 text-xs text-slate-100">{v.suggestedFix}</pre>
            )}
            <button
              type="button"
              onClick={() => generateFix.mutate(v.id)}
              disabled={generateFix.isPending}
              className="mt-3 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-50"
            >
              Generate Fix
            </button>
          </article>
        ))}
        {(vulns.data?.length ?? 0) === 0 && <p className="text-sm text-slate-500">No vulnerabilities yet. Run a scan to seed a sample finding.</p>}
      </section>
    </div>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const colors: Record<string, string> = {
    CRITICAL: 'bg-red-100 text-red-800',
    HIGH: 'bg-orange-100 text-orange-800',
    MEDIUM: 'bg-amber-100 text-amber-900',
    LOW: 'bg-blue-100 text-blue-800',
    INFO: 'bg-slate-100 text-slate-700',
  }
  return <span className={`rounded px-2 py-0.5 text-xs font-semibold ${colors[severity] ?? colors.INFO}`}>{severity}</span>
}
