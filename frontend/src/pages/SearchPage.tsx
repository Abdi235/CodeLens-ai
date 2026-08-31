import { FormEvent, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

type SearchResponse = {
  jobId: string
  jobStatus: string
  query: string
  resultCount: number
  results: Array<{
    filePath: string
    startLine?: number
    endLine?: number
    language?: string
    snippet: string
    score: number
  }>
}

type AnalysisJob = { jobId: string; repository: string; status: string }

export function SearchPage() {
  const [jobId, setJobId] = useState('')
  const [query, setQuery] = useState('authentication login')
  const [submitted, setSubmitted] = useState<{ jobId: string; q: string } | null>(null)

  const jobs = useQuery({
    queryKey: ['analysis-jobs'],
    queryFn: () => api<AnalysisJob[]>('/api/analysis'),
  })

  const search = useQuery({
    queryKey: ['code-search', submitted?.jobId, submitted?.q],
    queryFn: () =>
      api<SearchResponse>(
        `/api/search?jobId=${encodeURIComponent(submitted!.jobId)}&q=${encodeURIComponent(submitted!.q)}&limit=12`,
      ),
    enabled: Boolean(submitted?.jobId && submitted?.q),
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitted({ jobId, q: query })
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Code Search</h1>
        <p className="mt-1 text-slate-600">
          Natural-language search over indexed repositories using BM25 ranking.
        </p>
      </div>

      <form onSubmit={onSubmit} className="space-y-3 rounded-2xl border border-slate-200 bg-white/80 p-5">
        <label className="block text-sm font-medium text-slate-700">
          Analysis job
          <select
            value={jobId}
            onChange={(e) => setJobId(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            required
          >
            <option value="">Select a completed job…</option>
            {(jobs.data ?? [])
              .filter((j) => j.status === 'COMPLETED')
              .map((j) => (
                <option key={j.jobId} value={j.jobId}>
                  {j.repository} ({j.jobId.slice(0, 8)}…)
                </option>
              ))}
          </select>
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Query
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Where is authentication handled?"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            required
          />
        </label>
        <button type="submit" className="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white hover:bg-indigo-800">
          Search code
        </button>
      </form>

      {search.error && <p className="text-sm text-red-700">{(search.error as Error).message}</p>}

      {search.data && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">
            {search.data.resultCount} result{search.data.resultCount === 1 ? '' : 's'} for “{search.data.query}”
          </h2>
          {search.data.results.map((r, idx) => (
            <article key={`${r.filePath}-${idx}`} className="rounded-2xl border border-slate-200 bg-white/80 p-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-mono text-sm text-slate-700">
                  {r.filePath}:{r.startLine}-{r.endLine}
                </p>
                <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs font-semibold text-indigo-800">
                  score {r.score}
                </span>
              </div>
              <pre className="mt-3 max-h-48 overflow-auto rounded-lg bg-slate-900 p-3 text-xs text-slate-100">{r.snippet}</pre>
            </article>
          ))}
        </section>
      )}
    </div>
  )
}
