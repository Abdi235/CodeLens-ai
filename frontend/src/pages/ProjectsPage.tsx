import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'

type Project = { id: number; name: string; repositoryUrl?: string; createdAt: string }

export function ProjectsPage() {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')

  const projects = useQuery({
    queryKey: ['projects'],
    queryFn: () => api<Project[]>('/api/projects'),
  })

  const create = useMutation({
    mutationFn: () =>
      api<Project>('/api/projects', {
        method: 'POST',
        body: JSON.stringify({ name, repositoryUrl: repositoryUrl || null }),
      }),
    onSuccess: () => {
      setName('')
      setRepositoryUrl('')
      queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold">Projects</h1>
        <p className="mt-1 text-slate-600">Register repositories to scan for vulnerabilities.</p>
      </div>

      <form onSubmit={onSubmit} className="cl-panel grid gap-3 rounded-2xl p-5 sm:grid-cols-[1fr_1fr_auto]">
        <input
          required
          placeholder="Project name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="cl-input rounded-xl px-3.5 py-2.5"
        />
        <input
          placeholder="Repository URL (optional — empty uses samples/)"
          value={repositoryUrl}
          onChange={(e) => setRepositoryUrl(e.target.value)}
          className="cl-input rounded-xl px-3.5 py-2.5"
        />
        <button type="submit" disabled={create.isPending} className="cl-btn-primary rounded-xl px-4 py-2.5">
          Add project
        </button>
      </form>

      <ul className="space-y-3">
        {(projects.data ?? []).map((p) => (
          <li key={p.id} className="cl-panel rounded-xl px-4 py-3.5 transition hover:bg-white">
            <Link to={`/project/${p.id}`} className="flex items-center justify-between gap-3">
              <div>
                <div className="font-semibold">{p.name}</div>
                <div className="text-sm text-slate-500">{p.repositoryUrl || 'No URL'} · created {new Date(p.createdAt).toLocaleString()}</div>
              </div>
              <span className="text-sm font-medium text-teal-800">Open →</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
