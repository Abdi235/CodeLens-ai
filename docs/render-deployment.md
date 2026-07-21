# Deploy SecureAI on Render

Render replaces AWS for this project: managed Postgres + Docker web services + a static frontend.

## One-click Blueprint

1. Push this repo to GitHub (already at https://github.com/Abdi235/SecureAI).
2. Open [Render Dashboard](https://dashboard.render.com/) → **New** → **Blueprint**.
3. Connect the `SecureAI` repository.
4. Confirm `render.yaml` and create the stack.

Blueprint creates:

| Service | Name | Role |
| --- | --- | --- |
| PostgreSQL | `secureai-db` | App database |
| Web (Docker) | `secureai-ai` | FastAPI scanner + AI |
| Web (Docker) | `secureai-api` | Spring Boot API |
| Static site | `secureai-web` | React UI |

## After first deploy

1. In **secureai-ai** → Environment, optionally set `OPENAI_API_KEY`.
2. Wait for all services to go live (free tier cold starts can take ~1 minute).
3. Open the `secureai-web` URL and register a user.
4. Create a project with an empty repo URL and click **Run scan** (uses bundled `samples/`).

## Local vs Render URLs

| Env | Frontend | API |
| --- | --- | --- |
| Local | http://localhost:5173 (Vite proxy) | http://localhost:8080 |
| Render | `https://secureai-web.onrender.com` | `VITE_API_URL` baked at build time |

## Notes

- Free web services sleep after idle; first request may be slow.
- CORS allows `https://*.onrender.com` and local Vite.
- Health checks: API `/actuator/health`, AI `/health`.
- Rebuild the static site if you recreate the API service (so `VITE_API_URL` updates).

## Manual deploy (without Blueprint)

Create the four resources above, then set:

**secureai-api**

- `SPRING_PROFILES_ACTIVE=prod`
- `JWT_SECRET` (long random string)
- `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` from the Render DB
- `AI_SERVICE_URL` = AI service public URL
- `WORKSPACE_DIR=/tmp/secureai-workspace`

**secureai-web** build env

- `VITE_API_URL` = API public URL
