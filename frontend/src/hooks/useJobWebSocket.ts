import { useEffect, useRef, useState } from 'react'
import { API_BASE, getToken } from '../lib/api'

export type JobStatusMessage = {
  jobId: string
  status: string
  errorMessage?: string | null
  findingCount?: number | null
}

function wsUrl(jobId: string): string {
  const token = getToken()
  const base = API_BASE || window.location.origin
  const wsBase = base.replace(/^http/, 'ws')
  return `${wsBase}/ws/jobs/${jobId}?token=${encodeURIComponent(token ?? '')}`
}

export function useJobWebSocket(jobId: string | null, onUpdate: (msg: JobStatusMessage) => void) {
  const [connected, setConnected] = useState(false)
  const retryRef = useRef(0)
  const onUpdateRef = useRef(onUpdate)
  onUpdateRef.current = onUpdate

  useEffect(() => {
    if (!jobId || !getToken()) return

    let ws: WebSocket | null = null
    let closed = false
    let retryTimer: ReturnType<typeof setTimeout>

    const connect = () => {
      ws = new WebSocket(wsUrl(jobId))
      ws.onopen = () => {
        setConnected(true)
        retryRef.current = 0
      }
      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as JobStatusMessage
          onUpdateRef.current(data)
        } catch {
          /* ignore */
        }
      }
      ws.onclose = () => {
        setConnected(false)
        if (!closed && retryRef.current < 5) {
          retryRef.current += 1
          retryTimer = setTimeout(connect, 2000 * retryRef.current)
        }
      }
      ws.onerror = () => ws?.close()
    }

    connect()
    const ping = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('ping')
    }, 30000)

    return () => {
      closed = true
      clearInterval(ping)
      clearTimeout(retryTimer)
      ws?.close()
    }
  }, [jobId])

  return { connected }
}
