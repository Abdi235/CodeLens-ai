import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { shouldPollResults, statusLabel } from '../lib/analysisStatus'
import { useJobWebSocket } from '../hooks/useJobWebSocket'

type AnalysisJob = {
  jobId: string
  repository: string
  status: string
  createdAt: string
  startedAt?: string
  completedAt?: string
  errorMessage?: string
  findingCount?: number
}

type Finding = {
  id: number
  vulnerabilityType: string
  normalizedType?: string
  severity: string
  filePath?: string
  lineNumber?: number
  description?: string
  remediation?: string
  retrievedContext?: string
  aiExplanation?: string
  classificationConfidence?: number
}

type Results = {
  jobId: string
  status: string
  findingCount?: number
  findings: Finding[]
}

export function AnalysisPage() {
  const queryClient = useQueryClient()
  const [repository, setRepository] = useState('samples')
  const [activeJobId, setActiveJobId] = useState<string | null>(null)

  const jobs = useQuery({
    queryKey: ['analysis-jobs'],
    queryFn: () => api<AnalysisJob[]>('/api/analysis'),
  })

  const results = useQuery({
    queryKey: ['analysis-results', activeJobId],
    queryFn: () => api<Results>(`/api/analysis/${activeJobId}/results`),
    enabled: Boolean(activeJobId),
    refetchInterval: (q) => (shouldPollResults(q.state.data?.status) ? 4000 : false),
  })

  useJobWebSocket(activeJobId, () => {
    queryClient.invalidateQueries({ queryKey: ['analysis-results', activeJobId] })
    queryClient.invalidateQueries({ queryKey: ['analysis-jobs'] })
  })

  const create = useMutation({
    mutationFn: () =>
      api<AnalysisJob>('/api/analysis', {
        method: 'POST',
        body: JSON.stringify({ repository }),
      }),
    onSuccess: (job) => {
      setActiveJobId(job.jobId)
      queryClient.invalidateQueries({ queryKey: ['analysis-jobs'] })
    },
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  const activeStatus = results.data?.status ?? jobs.data?.find((j) => j.jobId === activeJobId)?.status

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Repository Analysis</h1>
        <p className="mt-1 text-slate-600">
          Submit a GitHub repository for distributed analysis via CloudAMQP workers.
        </p>
      </div>

      <form onSubmit={onSubmit} className="cl-panel flex flex-wrap gap-3 rounded-2xl p-5">
        <input
          required
          value={repository}
          onChange={(e) => setRepository(e.target.value)}
          placeholder="Repository URL or 'samples'"
          className="cl-input min-w-[280px] flex-1 rounded-xl px-3.5 py-2.5"
        />
        <button type="submit" disabled={create.isPending} className="cl-btn-primary rounded-xl px-5 py-2.5">
          {create.isPending ? 'Submitting…' : 'Analyze repository'}
        </button>
      </form>

      {create.error && <p className="text-sm text-red-700">{(create.error as Error).message}</p>}

      {activeJobId && (
        <section className="cl-panel rounded-2xl p-5">
          <h2 className="text-lg font-semibold">Active job</h2>
          <p className="mt-1 font-mono text-sm text-slate-600">{activeJobId}</p>
          <p className="mt-2">
            Status: <StatusBadge status={activeStatus ?? 'QUEUED'} />
          </p>
          {(activeStatus === 'QUEUED' || activeStatus === 'PROCESSING') && (
            <p className="mt-2 text-sm text-slate-500">Worker processing… updates via WebSocket + polling.</p>
          )}
          {results.data?.status === 'FAILED' && (
            <p className="mt-2 text-sm text-red-700">{jobs.data?.find((j) => j.jobId === activeJobId)?.errorMessage}</p>
          )}
        </section>
      )}

      {results.data && results.data.status === 'COMPLETED' && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Findings ({results.data.findingCount ?? 0})</h2>
          {(results.data.findingCount ?? 0) === 0 && (
            <p className="cl-panel rounded-2xl p-5 text-sm text-slate-600">
              Completed with 0 findings — the pipeline ran successfully; no rule matched in this repository.
              Zero findings does not mean the job failed. Try <code className="rounded bg-slate-100 px-1">samples</code> to
              see example vulnerabilities.
            </p>
          )}
          {results.data.findings.map((f) => (
            <article key={f.id} className="cl-panel rounded-2xl p-5">
              <div className="flex flex-wrap items-center gap-2">
                <SeverityBadge severity={f.severity} />
                <h3 className="font-semibold">{f.vulnerabilityType}</h3>
                {f.normalizedType && f.normalizedType !== f.vulnerabilityType && (
                  <span className="text-xs text-slate-500">→ {f.normalizedType}</span>
                )}
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {f.filePath}
                {f.lineNumber != null ? `:${f.lineNumber}` : ''}
              </p>
              <p className="mt-2 text-sm">{f.description}</p>
              {f.aiExplanation && (
                <p className="mt-2 rounded-lg bg-teal-50 px-3 py-2 text-sm text-teal-950">
                  <span className="font-medium">AI:</span> {f.aiExplanation}
                </p>
              )}
              {f.retrievedContext && (
                <pre className="mt-2 max-h-40 overflow-auto rounded-lg bg-slate-900 p-3 text-xs text-slate-100">
                  {f.retrievedContext}
                </pre>
              )}
              <p className="mt-2 text-sm">
                <span className="font-medium">Remediation:</span> {f.remediation}
              </p>
            </article>
          ))}
        </section>
      )}

      <section className="cl-panel rounded-2xl p-5">
        <h2 className="mb-3 text-lg font-semibold">Recent jobs</h2>
        <ul className="space-y-2 text-sm">
          {(jobs.data ?? []).map((j) => (
            <li
              key={j.jobId}
              className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 py-2.5 last:border-0"
            >
              <button
                type="button"
                className="rounded-lg px-2 py-1 text-left hover:bg-slate-50"
                onClick={() => setActiveJobId(j.jobId)}
              >
                <span className="font-mono text-xs text-slate-500">{j.jobId.slice(0, 8)}…</span>
                <span className="ml-2 font-medium text-slate-800">{j.repository}</span>
              </button>
              <StatusBadge status={j.status} />
            </li>
          ))}
          {(jobs.data?.length ?? 0) === 0 && <li className="text-slate-500">No analysis jobs yet.</li>}
        </ul>
        <p className="mt-3 text-xs text-slate-500">
          Legacy project scans still available on{' '}
          <Link to="/projects" className="text-teal-700 hover:underline">
            Projects
          </Link>
          .
        </p>
      </section>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    QUEUED: 'bg-slate-100 text-slate-700 ring-slate-200',
    PROCESSING: 'bg-sky-50 text-sky-900 ring-sky-200',
    COMPLETED: 'bg-emerald-50 text-emerald-900 ring-emerald-200',
    FAILED: 'bg-rose-50 text-rose-900 ring-rose-200',
  }
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${colors[status] ?? colors.QUEUED}`}>
      {statusLabel(status)}
    </span>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const colors: Record<string, string> = {
    CRITICAL: 'bg-red-100 text-red-800 ring-red-200',
    HIGH: 'bg-orange-100 text-orange-800 ring-orange-200',
    MEDIUM: 'bg-amber-100 text-amber-900 ring-amber-200',
    LOW: 'bg-blue-100 text-blue-800 ring-blue-200',
    INFO: 'bg-slate-100 text-slate-700 ring-slate-200',
  }
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${colors[severity] ?? colors.INFO}`}>
      {severity}
    </span>
  )
}
