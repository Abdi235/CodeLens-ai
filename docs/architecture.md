# SecureAI architecture notes

## Request flow

1. React client authenticates via JWT (`/api/auth/*`).
2. Spring Boot stores projects/scans/vulnerabilities in PostgreSQL (or H2 in `dev`).
3. Scan requests (Week 2) invoke Semgrep and persist findings.
4. Fix/explain requests (Week 3) call the FastAPI AI service, which calls an LLM.

## Week 1 status

- Auth + project CRUD + scan stub + fix stub are implemented end-to-end.
- Scan currently seeds one sample SQL injection finding so the UI/API path is demoable.
