import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'

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

export function VulnerabilitiesPage() {
  const queryClient = useQueryClient()
  const vulns = useQuery({
    queryKey: ['vulnerabilities'],
    queryFn: () => api<Vulnerability[]>('/api/vulnerabilities'),
  })

  const generateFix = useMutation({
    mutationFn: (vulnerabilityId: number) =>
      api('/api/fix/generate', {
        method: 'POST',
        body: JSON.stringify({ vulnerabilityId }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['vulnerabilities'] }),
  })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Vulnerabilities</h1>
        <p className="mt-1 text-slate-600">All findings across your projects.</p>
      </div>

      <div className="space-y-3">
        {(vulns.data ?? []).map((v) => (
          <article key={v.id} className="rounded-2xl border border-slate-200 bg-white/80 p-5">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-800">{v.severity}</span>
              <h2 className="font-semibold">{v.type}</h2>
            </div>
            <p className="mt-1 text-sm text-slate-500">
              {v.fileLocation}
              {v.lineNumber != null ? `:${v.lineNumber}` : ''}
            </p>
            <p className="mt-2 text-sm">{v.description}</p>
            {v.suggestedFix && <pre className="mt-3 overflow-x-auto rounded-lg bg-slate-900 p-3 text-xs text-slate-100">{v.suggestedFix}</pre>}
            <button
              type="button"
              onClick={() => generateFix.mutate(v.id)}
              className="mt-3 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-50"
            >
              Generate Fix
            </button>
          </article>
        ))}
        {(vulns.data?.length ?? 0) === 0 && <p className="text-sm text-slate-500">No vulnerabilities yet.</p>}
      </div>
    </div>
  )
}
