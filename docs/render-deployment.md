# Deploy CodeLens on Render + Vercel

## Current live URLs (as of ops check)

| Piece | URL |
| --- | --- |
| Frontend | `https://code-lens-ai-ruby.vercel.app` |
| API | `https://codelens-api-wym7.onrender.com` |
| Vercel env | `VITE_API_URL=https://codelens-api-wym7.onrender.com` |

## Blueprint

1. Open [Render → New Blueprint](https://dashboard.render.com/select-repo?type=blueprint)
2. Connect **Abdi235/CodeLens-ai** (branch `master`)
3. Apply `render.yaml` — creates:
   - `codelens-db` (Postgres)
   - `codelens-ai` (FastAPI — Dockerfile `ai-service/Dockerfile`)
   - `codelens-api` (Spring Boot)
   - `codelens-worker` (Python analysis worker)
4. Set **manual** env vars in the Render dashboard:
   - `RABBITMQ_URL` on `codelens-api` and `codelens-worker` (CloudAMQP)
   - Optional `OPENAI_API_KEY` on `codelens-ai` / worker
5. Deploy and wait until web services are **Live**

First Docker builds can take 5–15 minutes on free tier.

## Wire Vercel → Render

1. Copy the **codelens-api** URL from Render (includes the random suffix), e.g. `https://codelens-api-wym7.onrender.com`
2. Vercel → Project → Settings → Environment Variables:
   - `VITE_API_URL` = that URL (**Config/public**, no trailing slash)
3. Redeploy the Vercel frontend (Vite bakes env at build time)

CORS allows `https://*.vercel.app` via Spring `allowedOriginPatterns`.

## Critical checks if something is broken

### `codelens-ai` must be FastAPI, not a frontend

```bash
curl -sS https://YOUR-AI.onrender.com/health
# expect: {"status":"ok",...}
```

If you get an HTML Vite/React page (`x-powered-by: Express`), the service was created with the wrong runtime/root. Fix in Render:

- **Runtime:** Docker
- **Dockerfile path:** `ai-service/Dockerfile`
- **Docker context:** `./ai-service`
- **Health check path:** `/health`

Then set `AI_SERVICE_URL` on `codelens-api` to that service’s external URL and redeploy the API.

### Analysis jobs stuck in `QUEUED`

The API can publish to CloudAMQP (`rabbitmq: UP`), but a **worker** must consume:

1. Ensure `codelens-worker` exists and is **running** (Background Worker, not a web service)
2. Same `DATABASE_URL` + `RABBITMQ_URL` as the API
3. Dockerfile: `ai-service/Dockerfile.worker`

Without a worker, jobs stay `QUEUED` forever.

## Smoke test

```bash
API=https://codelens-api-wym7.onrender.com

curl -sS "$API/actuator/health"
# {"status":"UP",...}

curl -sS -X POST "$API/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"YourPass123!"}'
```

Then open the Vercel site, register/sign in, open **Dashboard → Service health**.

Expect dependencies: `api` / `database` / `rabbitmq` **UP**. `ai` UP only after the FastAPI service is fixed.

## Notes

- Free web services sleep when idle; cold start may take ~30–60s (sometimes longer)
- Health checks: API `/actuator/health`, AI `/health`
- Dashboard service monitoring: `GET /api/metrics/system` (JWT)
