import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

type OpsStep = {
  stepIndex: number
  role: string
  toolName?: string | null
  toolArgs?: Record<string, unknown> | null
  toolResult?: Record<string, unknown> | null
  content?: string | null
}

type OpsRun = {
  runId: string
  mode: string
  goal: string
  status: string
  brainType: string
  dryRun: boolean
  scenario?: string | null
  success?: boolean | null
  summary?: string | null
  durationMs?: number | null
  steps: OpsStep[]
}

type OpsEval = {
  evaluatedRuns: number
  successfulRuns: number
  successRatePercent: number
  avgDurationMs: number
  avgSteps: number
}

type SystemMetrics = {
  uptimeSeconds: number
  errorRatePercent: number
  avgLatencyMs: number
  p95LatencyMs: number
  dependencies: Record<string, string>
  pipeline: { queued: number; processing: number; completed: number; failed: number }
}

const SCENARIOS = [
  { id: 'stuck_queued_job', label: 'Stuck queued job' },
  { id: 'worker_down', label: 'Worker down / asleep' },
  { id: 'elevated_error_rate', label: 'Elevated error rate' },
  { id: 'healthy', label: 'Healthy baseline' },
] as const

export function OpsAgentPage() {
  const queryClient = useQueryClient()
  const [goal, setGoal] = useState(
    'Inspect CodeLens health. Remediate stuck jobs or sleeping workers if needed, then resolve.',
  )
  const [dryRun, setDryRun] = useState(false)
  const [active, setActive] = useState<OpsRun | null>(null)

  const health = useQuery({
    queryKey: ['system-metrics'],
    queryFn: () => api<SystemMetrics>('/api/metrics/system'),
    refetchInterval: 15_000,
  })
  const evalMetrics = useQuery({
    queryKey: ['ops-agent-eval'],
    queryFn: () => api<OpsEval>('/api/ops-agent/eval'),
  })
  const runs = useQuery({
    queryKey: ['ops-agent-runs'],
    queryFn: () => api<OpsRun[]>('/api/ops-agent/runs'),
  })

  const runAgent = useMutation({
    mutationFn: () => api<OpsRun>('/api/ops-agent/run', { method: 'POST', body: JSON.stringify({ goal, dryRun }) }),
    onSuccess: (data) => {
      setActive(data)
      void queryClient.invalidateQueries({ queryKey: ['ops-agent-runs'] })
      void queryClient.invalidateQueries({ queryKey: ['ops-agent-eval'] })
      void queryClient.invalidateQueries({ queryKey: ['system-metrics'] })
    },
  })

  const simulate = useMutation({
    mutationFn: (scenario: string) =>
      api<OpsRun>('/api/ops-agent/simulate', {
        method: 'POST',
        body: JSON.stringify({ scenario, dryRun }),
      }),
    onSuccess: (data) => {
      setActive(data)
      void queryClient.invalidateQueries({ queryKey: ['ops-agent-runs'] })
      void queryClient.invalidateQueries({ queryKey: ['ops-agent-eval'] })
      void queryClient.invalidateQueries({ queryKey: ['system-metrics'] })
    },
  })

  const busy = runAgent.isPending || simulate.isPending

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold tracking-tight">Ops Agent</h1>
        <p className="mt-1 max-w-3xl text-slate-600">
          Observe live service health and choose remediations — requeue jobs, wake workers, page a human —
          then score outcomes with the incident simulator.
        </p>
      </div>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Brain" value={active?.brainType ?? (health.isSuccess ? 'ready' : '…')} />
        <Stat
          label="Eval success"
          value={
            evalMetrics.data
              ? `${evalMetrics.data.successRatePercent}% (${evalMetrics.data.successfulRuns}/${evalMetrics.data.evaluatedRuns})`
              : '—'
          }
        />
        <Stat label="Avg steps" value={evalMetrics.data?.avgSteps ?? '—'} />
        <Stat label="Avg resolve ms" value={evalMetrics.data?.avgDurationMs ?? '—'} />
      </section>

      <section className="cl-panel rounded-2xl p-5">
        <h2 className="mb-3 text-lg font-semibold">Live health snapshot</h2>
        {health.data ? (
          <div className="flex flex-wrap gap-2 text-sm">
            {Object.entries(health.data.dependencies).map(([k, v]) => (
              <span
                key={k}
                className={`rounded-lg px-3 py-1.5 ring-1 ring-inset ${
                  v === 'UP' ? 'bg-emerald-50 text-emerald-800 ring-emerald-200' : 'bg-rose-50 text-rose-800 ring-rose-200'
                }`}
              >
                {k.toUpperCase()} {v}
              </span>
            ))}
            <span className="rounded-lg bg-slate-50 px-3 py-1.5 text-slate-600 ring-1 ring-slate-200">
              errors {health.data.errorRatePercent}% · p95 {health.data.p95LatencyMs}ms · queue{' '}
              {health.data.pipeline.queued}/{health.data.pipeline.processing}
            </span>
          </div>
        ) : (
          <p className="text-sm text-slate-500">Loading health…</p>
        )}
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <div className="cl-panel rounded-2xl p-5">
          <h2 className="mb-3 text-lg font-semibold">Run agent</h2>
          <label className="mb-3 block text-sm font-medium text-slate-700">
            Goal
            <textarea
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
              rows={4}
              className="cl-input mt-1.5 w-full rounded-xl px-3.5 py-2.5 text-sm"
            />
          </label>
          <label className="mb-4 flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={dryRun} onChange={(e) => setDryRun(e.target.checked)} />
            Dry run (plan tools without mutating jobs)
          </label>
          <button
            type="button"
            disabled={busy || !goal.trim()}
            onClick={() => runAgent.mutate()}
            className="cl-btn-primary rounded-xl px-4 py-2.5 text-sm"
          >
            {runAgent.isPending ? 'Agent running…' : 'Run ops agent'}
          </button>
          {runAgent.isError && (
            <p className="mt-2 text-sm text-rose-700">{(runAgent.error as Error).message}</p>
          )}
        </div>

        <div className="cl-panel rounded-2xl p-5">
          <h2 className="mb-2 text-lg font-semibold">Incident simulator</h2>
          <p className="mb-4 text-sm text-slate-500">
            Inject a known incident, let the agent choose tools, and score whether expected remediations ran.
          </p>
          <div className="flex flex-wrap gap-2">
            {SCENARIOS.map((s) => (
              <button
                key={s.id}
                type="button"
                disabled={busy}
                onClick={() => simulate.mutate(s.id)}
                className="rounded-xl border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-800 transition hover:bg-slate-50 disabled:opacity-60"
              >
                {simulate.isPending ? 'Running…' : s.label}
              </button>
            ))}
          </div>
          {simulate.isError && (
            <p className="mt-2 text-sm text-rose-700">{(simulate.error as Error).message}</p>
          )}
        </div>
      </section>

      {active && <Transcript run={active} />}

      <section className="cl-panel rounded-2xl p-5">
        <h2 className="mb-3 text-lg font-semibold">Recent runs</h2>
        <ul className="divide-y divide-slate-100 text-sm">
          {(runs.data ?? []).map((r) => (
            <li key={r.runId}>
              <button
                type="button"
                className="flex w-full items-center justify-between gap-3 rounded-lg py-3 text-left transition hover:bg-slate-50"
                onClick={() => setActive(r)}
              >
                <span>
                  <span className="font-medium">{r.scenario || r.mode}</span>
                  <span className="ml-2 text-slate-500">{r.brainType}</span>
                </span>
                <span className="tabular-nums text-slate-600">
                  {r.success == null ? r.status : r.success ? 'PASS' : 'FAIL'} · {r.durationMs ?? '—'}ms
                </span>
              </button>
            </li>
          ))}
          {(runs.data?.length ?? 0) === 0 && (
            <li className="py-3 text-slate-500">No agent runs yet. Start with a simulated incident.</li>
          )}
        </ul>
      </section>
    </div>
  )
}

function Transcript({ run }: { run: OpsRun }) {
  return (
    <section className="cl-panel rounded-2xl p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Agent transcript</h2>
        <div className="text-sm text-slate-600">
          {run.brainType} · {run.status}
          {run.success != null && (
            <span className={`ml-2 font-medium ${run.success ? 'text-emerald-700' : 'text-rose-700'}`}>
              {run.success ? 'PASS' : 'FAIL'}
            </span>
          )}
        </div>
      </div>
      {run.summary && <p className="mb-4 text-sm text-slate-700">{run.summary}</p>}
      <ol className="space-y-3">
        {run.steps.map((s) => (
          <li key={`${run.runId}-${s.stepIndex}`} className="rounded-xl border border-slate-100 bg-slate-50/80 px-4 py-3 text-sm">
            <div className="mb-1 flex flex-wrap gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <span>#{s.stepIndex}</span>
              <span>{s.role}</span>
              {s.toolName && <span className="text-teal-800">{s.toolName}</span>}
            </div>
            {s.content && <p className="text-slate-800">{s.content}</p>}
            {s.toolArgs && (
              <pre className="mt-2 overflow-x-auto rounded-lg bg-white p-2 text-xs text-slate-700">
                args: {JSON.stringify(s.toolArgs, null, 2)}
              </pre>
            )}
            {s.toolResult && (
              <pre className="mt-2 overflow-x-auto rounded-lg bg-white p-2 text-xs text-slate-700">
                result: {JSON.stringify(s.toolResult, null, 2)}
              </pre>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="cl-panel rounded-2xl px-5 py-4">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums tracking-tight">{value}</div>
    </div>
  )
}
