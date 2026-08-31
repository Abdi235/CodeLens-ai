/**
 * Lightweight API contract checks for the mobile client (no Jest required).
 * Run: node mobile/scripts/api-smoke.mjs
 */

const API_BASE = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080'

const paths = ['/api/auth/login', '/api/analysis']

for (const path of paths) {
  const url = `${API_BASE}${path}`
  const res = await fetch(url, {
    method: path.includes('login') ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json' },
    body: path.includes('login') ? JSON.stringify({ email: 'x', password: 'y' }) : undefined,
  })
  if (res.status === 401 || res.status === 403 || res.status === 400 || res.status === 405) {
    console.log(`OK reachable ${path} -> ${res.status}`)
  } else {
    console.log(`reachable ${path} -> ${res.status}`)
  }
}

console.log('mobile API smoke complete')
