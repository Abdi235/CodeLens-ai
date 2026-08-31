"""PostgreSQL access for the worker (shared schema with Spring Boot JPA)."""

from __future__ import annotations

import logging
from contextlib import contextmanager
from typing import Any, Iterator
from app.worker.config import DATABASE_URL, DB_HOST, DB_NAME, DB_PASSWORD, DB_PORT, DB_USER

log = logging.getLogger(__name__)


def _connect():
    if DATABASE_URL:
        return psycopg2.connect(DATABASE_URL)
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD,
    )


@contextmanager
def connection() -> Iterator[psycopg2.extensions.connection]:
    conn = _connect()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def get_job_status(job_id: str) -> str | None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT status FROM analysis_jobs WHERE job_id = %s", (job_id,))
            row = cur.fetchone()
            return row[0] if row else None


def try_claim_job(job_id: str) -> bool:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE analysis_jobs
                SET status = 'PROCESSING',
                    started_at = COALESCE(started_at, NOW()),
                    processing_attempts = COALESCE(processing_attempts, 0) + 1
                WHERE job_id = %s AND status = 'QUEUED'
                RETURNING job_id
                """,
                (job_id,),
            )
            return cur.fetchone() is not None


def set_worker_id(job_id: str, worker_id: str) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE analysis_jobs SET worker_id = %s WHERE job_id = %s",
                (worker_id[:255], job_id),
            )


def mark_failed(job_id: str, error_message: str) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE analysis_jobs
                SET status = 'FAILED',
                    completed_at = NOW(),
                    error_message = %s
                WHERE job_id = %s AND status IN ('QUEUED', 'PROCESSING')
                """,
                (error_message[:4000], job_id),
            )


def mark_completed(job_id: str, finding_count: int, duration_ms: int) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE analysis_jobs
                SET status = 'COMPLETED',
                    completed_at = NOW(),
                    finding_count = %s,
                    processing_duration_ms = %s,
                    error_message = NULL
                WHERE job_id = %s AND status = 'PROCESSING'
                """,
                (finding_count, duration_ms, job_id),
            )


def delete_findings(job_id: str) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM analysis_findings WHERE job_id = %s", (job_id,))


def delete_index(job_id: str) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM code_index_entries WHERE job_id = %s", (job_id,))
            cur.execute("DELETE FROM code_symbols WHERE job_id = %s", (job_id,))


def insert_finding(job_id: str, finding: dict[str, Any]) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO analysis_findings (
                    job_id, vulnerability_type, normalized_type, severity, file_path,
                    line_number, description, remediation, retrieved_context,
                    ai_explanation, classification_confidence, rule_id
                ) VALUES (
                    %(job_id)s, %(vulnerability_type)s, %(normalized_type)s, %(severity)s,
                    %(file_path)s, %(line_number)s, %(description)s, %(remediation)s,
                    %(retrieved_context)s, %(ai_explanation)s, %(classification_confidence)s,
                    %(rule_id)s
                )
                """,
                {
                    "job_id": job_id,
                    "vulnerability_type": finding["vulnerability_type"],
                    "normalized_type": finding.get("normalized_type"),
                    "severity": finding["severity"],
                    "file_path": finding.get("file_path"),
                    "line_number": finding.get("line_number"),
                    "description": finding.get("description"),
                    "remediation": finding.get("remediation"),
                    "retrieved_context": finding.get("retrieved_context"),
                    "ai_explanation": finding.get("ai_explanation"),
                    "classification_confidence": finding.get("classification_confidence"),
                    "rule_id": finding.get("rule_id"),
                },
            )


def persist_index_entries(job_id: str, entries: list[dict[str, Any]]) -> None:
    if not entries:
        return
    with connection() as conn:
        with conn.cursor() as cur:
            for entry in entries:
                cur.execute(
                    """
                    INSERT INTO code_index_entries (
                        job_id, file_path, start_line, end_line, language, chunk_text, token_count
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        job_id,
                        entry["file_path"],
                        entry["start_line"],
                        entry["end_line"],
                        entry["language"],
                        entry["chunk_text"][:8000],
                        entry.get("token_count", 0),
                    ),
                )


def persist_symbols(job_id: str, symbols: list[dict[str, Any]]) -> None:
    if not symbols:
        return
    with connection() as conn:
        with conn.cursor() as cur:
            for sym in symbols:
                cur.execute(
                    """
                    INSERT INTO code_symbols (job_id, file_path, name, kind, line_number, language)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    (
                        job_id,
                        sym["file_path"],
                        sym["name"],
                        sym["kind"],
                        sym["line_number"],
                        sym["language"],
                    ),
                )


def upsert_repository_record(
    job_id: str,
    repository_url: str,
    user_id: int,
    file_count: int,
    chunk_count: int,
    primary_language: str,
) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO repository_records (
                    job_id, repository_url, user_id, file_count, indexed_chunk_count,
                    primary_language, created_at
                ) VALUES (%s, %s, %s, %s, %s, %s, NOW())
                ON CONFLICT (job_id) DO UPDATE SET
                    file_count = EXCLUDED.file_count,
                    indexed_chunk_count = EXCLUDED.indexed_chunk_count,
                    primary_language = EXCLUDED.primary_language
                """,
                (job_id, repository_url, user_id, file_count, chunk_count, primary_language),
            )


def get_repository(job_id: str) -> str | None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT repository FROM analysis_jobs WHERE job_id = %s", (job_id,))
            row = cur.fetchone()
            return row[0] if row else None


def get_user_id(job_id: str) -> int | None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT user_id FROM analysis_jobs WHERE job_id = %s", (job_id,))
            row = cur.fetchone()
            return int(row[0]) if row else None
