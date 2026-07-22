# Deploy SecureAI backend on Render (frontend on Vercel)

## Blueprint (recommended)

1. Open [Render → New Blueprint](https://dashboard.render.com/select-repo?type=blueprint)
2. Connect **Abdi235/SecureAI** (branch `master`)
3. Apply `render.yaml` — creates:
   - `secureai-db` (Postgres)
   - `secureai-ai` (FastAPI scanner)
   - `secureai-api` (Spring Boot)
4. Deploy and wait until both web services are **Live**

First Docker builds can take 5–15 minutes on free tier.

## Wire Vercel → Render

1. Copy the **secureai-api** URL, e.g. `https://secureai-api.onrender.com`
2. In Vercel → Project → Settings → Environment Variables:
   - `VITE_API_URL` = `https://secureai-api.onrender.com` (no trailing slash)
3. Redeploy the Vercel frontend

CORS already allows `https://*.vercel.app`.

## Optional

On `secureai-ai` → Environment, set `OPENAI_API_KEY` for real LLM explanations.

## Smoke test

```bash
curl https://YOUR-API.onrender.com/actuator/health
```

Then open your Vercel site, register, create a project, Run scan.

## Notes

- Free services sleep when idle; cold start may take ~30–60s
- Health checks: API `/actuator/health`, AI `/health`
