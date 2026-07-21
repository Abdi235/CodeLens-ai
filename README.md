# SecureAI — AI-Powered Code Security Platform

Scan repositories for vulnerabilities, explain findings with AI, and generate secure fixes.

## Stack

| Layer | Tech |
| --- | --- |
| Frontend | React + TypeScript + Tailwind + React Query + Chart.js |
| Backend | Spring Boot 4 + Security (JWT) + JPA |
| Database | H2 (local/dev) · PostgreSQL (prod/docker) |
| AI service | FastAPI (LLM hooks in Week 3) |
| Scanner | Semgrep (Week 2) |

## Monorepo layout

```
SecureAI/
├── frontend/       # React app (Vite)
├── backend/        # Spring Boot API
├── ai-service/     # FastAPI AI assistant
├── docker-compose.yml
└── docs/
```

## Quick start (local, no Docker)

### 1. Backend

```bash
cd backend
./mvnw spring-boot:run
```

API: http://localhost:8080  
Uses H2 file DB under `backend/data/` (`spring.profiles.active=dev`).

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

UI: http://localhost:5173

### 3. AI service (optional for Week 1)

```bash
cd ai-service
python -m venv .venv
# Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## Core APIs

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/projects`
- `GET /api/projects`
- `POST /api/projects/{id}/scan`
- `GET /api/projects/{id}/vulnerabilities`
- `GET /api/reports/{id}`
- `POST /api/fix/generate`

## Roadmap

- **Week 1** ✅ Auth, projects, scan stubs, React dashboard
- **Week 2** Semgrep static analysis + vulnerability persistence
- **Week 3** FastAPI ↔ LLM explanations & fix generation
- **Week 4** Docker/AWS deploy, CI/CD, monitoring

## Docker

Requires Docker Desktop:

```bash
docker compose up --build
```

## Roles

- `USER`
- `SECURITY_ADMIN`
