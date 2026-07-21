# SecureAI architecture notes

## Request flow

1. React client authenticates via JWT (`/api/auth/*`).
2. Spring Boot stores projects/scans/vulnerabilities (H2 or PostgreSQL).
3. Scan requests create a `PENDING` scan, then an async worker:
   - clones a git URL, copies a local path, uses `samples/`, or accepts a zip upload
   - calls the FastAPI `/scan/path` or `/scan/upload` endpoint
4. The AI service runs Semgrep when available, plus built-in SecureAI rules.
5. Findings are persisted; each finding is enriched via `/explain`.
6. **Generate Fix** calls `/fix` (OpenAI when `OPENAI_API_KEY` is set).

## Engines

| Engine | When used |
| --- | --- |
| Semgrep (`p/owasp-top-ten`, `p/security-audit`) | If `semgrep` is on PATH |
| SecureAI rules | Always (Windows-friendly fallback / supplement) |

## Status

Weeks 1–4 implemented in-repo: async scanning, AI layer, CI, Docker, AWS guide.
