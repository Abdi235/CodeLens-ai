# SecureAI — AI-Powered Code Security Platform

SecureAI combines **static analysis** with **LLM-based reasoning** to detect vulnerability patterns across multiple programming languages and produce prioritized remediation reports.

## Architecture

```
React (Web) / React Native (Mobile)
        ↓ REST + WebSocket
Spring Boot API
        ↓ publish
RabbitMQ (analysis queue)
        ↓ consume
Python Worker(s)
        ↓
Repository clone/prepare → Static analysis → Code retrieval (TF-IDF) → NLP classification → LLM explanation
        ↓
PostgreSQL
        ↓
Spring Boot API → React / Mobile
```

| Component | Technology |
| --- | --- |
| Web UI | React + TypeScript + Vite + Tailwind + React Query |
| Mobile | React Native (Expo) |
| API | Spring Boot 4, JWT, JPA, WebSocket |
| Queue | RabbitMQ (management UI on port 15672) |
| Workers | Python 3.13, pika, scikit-learn |
| Database | PostgreSQL (H2 for local dev without Docker) |
| LLM | OpenAI (optional; template fallbacks when unset) |

## Distributed processing

1. **Producer** — Spring Boot `POST /api/analysis` creates a `QUEUED` job in PostgreSQL and publishes `AnalysisJobMessage` to RabbitMQ (`secureai.analysis.queue`).
2. **Consumer** — One or more Python workers consume messages with manual ack, prefetch=1, and idempotent `job_id` claiming (`QUEUED` → `PROCESSING`).
3. **Status channel** — Workers publish `JobStatusMessage` to `secureai.job.status.queue`. Spring updates the DB and broadcasts over WebSocket.
4. **Retries** — Failed jobs requeue up to `WORKER_MAX_RETRIES` (default 3); final failures go to the DLQ (`secureai.analysis.dlq`) and are marked `FAILED` in PostgreSQL.
5. **Multiple workers** — Run `worker` and `worker-2` services in Docker Compose, or start additional worker containers/processes pointing at the same queue.

### Job lifecycle

`QUEUED` → `PROCESSING` → `COMPLETED` | `FAILED`

Invalid transitions are rejected in both Spring Boot and the worker.

## Code retrieval

The worker does **not** send entire repositories to the LLM.

1. **Parse** — Walk source files (`.java`, `.py`, `.js`, `.ts`, etc.), skip `node_modules` / `.git`.
2. **Chunk** — Split files into ~80-line chunks with file path and line ranges.
3. **Index** — Build a TF-IDF matrix over chunk text (scikit-learn).
4. **Retrieve** — For each finding, query with vulnerability type + description; top chunks (cosine similarity > 0.05) become `retrieved_context`.
5. **LLM** — Only retrieved context (up to 3 KB) is passed to the explain step.

## Language processing (beyond raw LLM calls)

| Module | Purpose |
| --- | --- |
| `nlp/classifier.py` | TF-IDF + cosine similarity maps findings to canonical OWASP-style categories |
| `nlp/normalizer.py` | Normalizes descriptions and remediation text |
| `retrieval/indexer.py` | Semantic (TF-IDF) retrieval of relevant code for each finding |

## Parallel / concurrent file analysis

Static regex scanning is **I/O-bound** (read files, run patterns). The worker uses a **`ThreadPoolExecutor`** (`FILE_WORKERS`, default 4) to scan files concurrently within a single job.

- **Why threads, not multiprocessing** — Work is dominated by file I/O and regex; threads avoid pickling overhead and share the in-memory chunk index.
- **Why not asyncio** — Existing scanner modules are synchronous; threads integrate without rewriting the rule engine.
- **Failure isolation** — A single file scan failure is logged; other files continue.

### Measured performance (samples repo, Windows, Python 3.13)

Run: `cd ai-service && PYTHONPATH=. python scripts/benchmark_parallel.py`

| Mode | Findings | Time (s) |
| --- | ---: | ---: |
| Sequential per-file scan | 4 | 0.002 |
| Concurrent thread pool (4 workers) | 4 | 0.003 |
| Full pipeline scan (rules + Semgrep) | 9 | 0.008 |

On this small sample set, concurrency does **not** improve wall time (overhead dominates). Expect benefit on larger repositories with many files.

## Networking (WebSocket)

- Endpoint: `ws://<api>/ws/jobs/{jobId}?token=<JWT>`
- Spring `JobStatusListener` pushes status updates when workers publish to the status queue.
- React hook `useJobWebSocket` reconnects with backoff (max 5 attempts) and sends periodic pings.
- Vite dev proxy forwards `/ws` to the backend.

## Mobile client

Expo app in `mobile/`:

- Register / login (JWT)
- Submit repository analysis
- Poll job status and display findings, severity, remediation

See [mobile/README.md](mobile/README.md).

## Quick start (Docker — recommended)

```bash
cp .env.example .env
# Optional: set OPENAI_API_KEY in .env

docker compose up --build
```

| Service | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| API | http://localhost:8080 |
| RabbitMQ management | http://localhost:15672 (user/pass from `.env`) |

### Multiple workers locally

Docker Compose includes `worker` and `worker-2`. To add more:

```bash
docker compose up --scale worker=3
```

Or run a standalone worker:

```bash
cd ai-service
pip install -r requirements.txt
export RABBITMQ_HOST=localhost DB_HOST=localhost
python -m app.worker.consumer
```

## Local development (without Docker)

Requires PostgreSQL and RabbitMQ running locally (or use Docker only for infra):

```bash
docker compose up database rabbitmq -d
```

**Backend**

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
# Set DB_* and RABBITMQ_* env vars (see .env.example)
```

**Worker**

```bash
cd ai-service
pip install -r requirements.txt
export PYTHONPATH=.
export DB_HOST=localhost RABBITMQ_HOST=localhost
python -m app.worker.consumer
```

**Frontend**

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 → **Analysis** → submit `samples`.

**Mobile**

```bash
cd mobile
npm install
export EXPO_PUBLIC_API_URL=http://localhost:8080
npx expo start
```

## Analysis API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/analysis` | Create job (returns immediately with `jobId`, `status`) |
| `GET` | `/api/analysis` | List your jobs |
| `GET` | `/api/analysis/{jobId}` | Job status |
| `GET` | `/api/analysis/{jobId}/results` | Findings (when complete) |

Legacy project/scan APIs remain for backward compatibility.

## Testing

```bash
# Backend (JUnit)
cd backend && ./mvnw test

# Python worker, retrieval, NLP
cd ai-service && PYTHONPATH=. python -m pytest tests/ -v

# Frontend build
cd frontend && npm run build
```

Tests cover job creation, validation, state transitions, repository URL rules, scanner rules, TF-IDF retrieval, NLP classification, and parallel scan smoke tests.

## Security

- Secrets via environment variables (never commit `.env`)
- Repository URL validation before enqueue
- Git clone uses `--depth 1` with timeout; workspace cleaned after each job
- JWT required for analysis endpoints
- RabbitMQ and PostgreSQL credentials configurable

## Project layout

```
SecureAI/
├── frontend/          # React web app
├── mobile/            # Expo React Native client
├── backend/           # Spring Boot API + RabbitMQ producer + WebSocket
├── ai-service/        # FastAPI (legacy HTTP) + Python worker
├── samples/           # Intentionally vulnerable demo code
├── docker-compose.yml
└── .env.example
```

## Limitations

- **Render/Vercel deployment** — The queue-based architecture requires RabbitMQ; the previous Render blueprint does not include RabbitMQ. Use Docker Compose locally or add a managed RabbitMQ service for cloud deployment.
- **LLM** — Without `OPENAI_API_KEY`, explanations use template text.
- **Semgrep** — Optional; built-in regex rules always run.
- **Scale** — Tested on small sample repos; not load-tested at high job volume. Throughput scales horizontally by adding worker instances.

## Demo flow

1. Start the stack (`docker compose up --build`)
2. Register at http://localhost:3000
3. Go to **Analysis**
4. Submit `samples`
5. Watch status: `QUEUED` → `PROCESSING` → `COMPLETED`
6. Review findings with severity, file locations, retrieved context, and remediation

## License

MIT (see repository).
