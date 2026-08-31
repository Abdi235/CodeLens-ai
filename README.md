# CodeLens — Distributed Code Intelligence & Security Platform

CodeLens helps developers submit a GitHub repository and receive **code indexing**, **natural-language code search**, **security vulnerability detection**, **AI explanations**, and **remediation recommendations**.

Built on the SecureAI foundation — extended with BM25 retrieval, AST-aware parsing, CloudAMQP messaging, and a search API.

## Architecture

```
React + TypeScript
       │ REST / WebSocket
       ▼
Spring Boot API
       ├──────────────┐
       ▼              ▼
 PostgreSQL    CloudAMQP (RabbitMQ)
                      │
           ┌──────────┴──────────┐
           ▼                     ▼
    Python Worker 1        Python Worker 2
           └──────────┬──────────┘
                      ▼
         Clone → Parse → Index → Retrieve → Scan → LLM → PostgreSQL
```

| Layer | Technology |
| --- | --- |
| Web | React 19, TypeScript, Vite, Tailwind |
| API | Spring Boot 4, JWT, JPA, WebSocket |
| Queue | **CloudAMQP** (hosted RabbitMQ) — `RABBITMQ_URL` |
| Workers | Python 3.13, pika, scikit-learn |
| Database | PostgreSQL |
| LLM | OpenAI (optional) |

**No AWS.** No local RabbitMQ container in the default Docker Compose stack.

## Features

- Async repository analysis (`POST /api/analysis`)
- Job lifecycle: `QUEUED` → `PROCESSING` → `COMPLETED` / `FAILED`
- Multiple independent Python workers (concurrent job processing)
- Code parsing (Python AST, Java/JS heuristics)
- BM25 + inverted-index code search (`GET /api/search`)
- Security static analysis + TF-IDF context retrieval + LLM reasoning
- WebSocket job status updates
- Repository metadata API (`GET /api/repositories/{id}`)

## Information retrieval

| Stage | Implementation |
| --- | --- |
| Parse | `parser/source_parser.py` — symbols, imports, line numbers |
| Chunk | 80-line chunks with file paths |
| Index | **Inverted index** (hash map + sets) for O(1) term lookup |
| Rank | **BM25** scoring over candidate documents |
| Search API | Spring `Bm25SearchEngine` queries persisted `code_index_entries` |

### Complexity (documented)

| Operation | Time | Space |
| --- | --- | --- |
| Build inverted index | O(n × t) per chunk tokens t | O(V + D) vocabulary + postings |
| Candidate lookup | O(k) query terms | — |
| BM25 score | O(c × k) candidates c, query terms k | O(D) documents in memory |
| Naive scan | O(n × t) every query | O(1) extra |

Indexing avoids re-scanning the full repository on every query.

### Measured benchmark (`samples/`, Windows, Python 3.13)

Run: `cd ai-service && PYTHONPATH=. python scripts/benchmark_search.py`

| Method | Hits | Time (ms) |
| --- | ---: | ---: |
| Naive linear scan | 1 | 0.084 |
| Inverted index candidates | 1 | 0.040 |
| BM25 ranked search | 1 | 0.086 |

On tiny repos, differences are negligible; indexing benefits grow with file count.

## Distributed processing

1. Spring Boot publishes `AnalysisJobMessage` to `codelens.analysis.queue`
2. Workers consume via **CloudAMQP** (`RABBITMQ_URL`)
3. Manual ack, prefetch=1, retries (max 3), DLQ, idempotent `job_id` claim
4. Status updates via `codelens.job.status.queue` → WebSocket broadcast

### Multiple workers

```bash
# Docker Compose (set RABBITMQ_URL in .env first)
docker compose up --build worker worker-2

# Or scale
docker compose up --scale worker=3
```

Set distinct `WORKER_ID` per process.

## Setup

### 1. CloudAMQP (required for job queue)

1. Create a free instance at [cloudamqp.com](https://www.cloudamqp.com/)
2. Copy the AMQP URL (starts with `amqps://`)
3. Set `RABBITMQ_URL` in `.env` — **never commit this**

### 2. Local with Docker

```bash
cp .env.example .env
# Edit .env — set RABBITMQ_URL from CloudAMQP

docker compose up --build
```

| Service | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| API | http://localhost:8080 |

### 3. Environment variables

| Variable | Required | Description |
| --- | --- | --- |
| `RABBITMQ_URL` | Yes (queue) | CloudAMQP connection URL |
| `DATABASE_URL` | Prod | PostgreSQL connection string |
| `OPENAI_API_KEY` | No | LLM explanations |
| `JWT_SECRET` | Yes | API auth |
| `WORKER_ID` | No | Worker identifier in logs/DB |

## API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/analysis` | Create analysis job |
| `GET` | `/api/analysis/{jobId}` | Job status |
| `GET` | `/api/analysis/{jobId}/results` | Security findings |
| `GET` | `/api/search?jobId=&q=` | BM25 code search |
| `GET` | `/api/repositories/{id}` | Indexed repository metadata |

## Testing

```bash
cd backend && ./mvnw test          # 12 tests
cd ai-service && PYTHONPATH=. python -m pytest tests/ -v   # 11 tests
cd frontend && npm run test && npm run build
```

## Concurrency model

- **Between jobs**: multiple worker processes via RabbitMQ (distributed)
- **Within a job**: `ThreadPoolExecutor` for I/O-bound file scanning (concurrent, not multi-core parallel)

## Known limitations

- Semantic/embedding search not implemented (lexical BM25 only)
- Java package remains `com.secureai` internally (user-facing brand is CodeLens)
- Render deployment requires manual `RABBITMQ_URL` + `VITE_API_URL` on Vercel
- Free-tier CloudAMQP/Render/Postgres have usage limits

## License

MIT
