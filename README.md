# SecureAI — AI-Powered Code Security Platform

Scan repositories for vulnerabilities, explain findings with AI, and generate secure fixes.

## Stack

| Layer | Tech |
| --- | --- |
| Frontend | React + TypeScript + Tailwind + React Query + Chart.js |
| Backend | Spring Boot 4 + Security (JWT/BCrypt) + JPA + async scans |
| Database | H2 (local/dev) · PostgreSQL (prod/docker) |
| AI / Scanner | FastAPI + built-in OWASP rules + optional Semgrep + optional OpenAI |
| CI | GitHub Actions |

## Monorepo layout

```
SecureAI/
├── frontend/
├── backend/
├── ai-service/
├── samples/            # intentionally vulnerable demo code
├── docs/
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## Quick start (local)

Run **three** terminals from the repo root.

### 1. AI + scanner service

```bash
cd ai-service
python -m venv .venv
# Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Optional: set `OPENAI_API_KEY` for real LLM explanations/fixes.

Optional: `pip install semgrep` (best on Linux/macOS/Docker; Windows uses built-in rules).

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

API: http://localhost:8080

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

UI: http://localhost:5173

## Demo flow

1. Register / sign in
2. Create a project (leave repository URL empty to scan `samples/`)
3. Click **Run scan**
4. Wait for status `COMPLETED` (UI polls automatically)
5. Open a finding → **Generate Fix** → Accept/Reject for metrics

You can also **Upload & scan** a `.zip` of source code.

## Core APIs

- `POST /api/auth/register` · `POST /api/auth/login`
- `POST /api/projects`
- `POST /api/projects/{id}/scan` (async)
- `POST /api/projects/{id}/scan/upload` (multipart zip/source)
- `GET /api/projects/{id}/scans`
- `GET /api/projects/{id}/vulnerabilities`
- `GET /api/reports/{id}`
- `POST /api/fix/generate`
- `POST /api/fix/{id}/feedback`
- `GET /api/metrics/ai`

## Security features

- JWT authentication + BCrypt password hashing
- Roles: `USER`, `SECURITY_ADMIN`
- Input validation on auth/project DTOs
- Fixed-window API rate limiting
- Multipart size limits

## AI evaluation metrics

Tracked by the AI service (`GET /metrics` or `/api/metrics/ai`):

- Explanations generated
- Fixes generated
- Fix acceptance rate (from Accept/Reject feedback)
- Whether LLM mode is enabled

## Roadmap status

- **Week 1** ✅ Auth, projects, React dashboard
- **Week 2** ✅ Static analysis engine, async scans, uploads, sample vulnerable repos
- **Week 3** ✅ FastAPI explain/fix + OpenAI optional + metrics
- **Week 4** ✅ Docker Compose, GitHub Actions CI, **Render** deployment

## Deploy on Render

Use the Blueprint in `render.yaml`:

1. [Render Dashboard](https://dashboard.render.com/) → **New** → **Blueprint**
2. Select `Abdi235/SecureAI`
3. Deploy (Postgres + AI + API + static frontend)

Full steps: [docs/render-deployment.md](docs/render-deployment.md)

## Docker (local)

```bash
docker compose up --build
```

## Docs

- [Architecture](docs/architecture.md)
- [Render deployment](docs/render-deployment.md)
