"""PostgreSQL access for the worker (shared schema with Spring Boot JPA)."""

from __future__ import annotations

import logging
from contextlib import contextmanager
from typing import Any, Iterator

import psycopg2
import psycopg2.extras

from app.worker.config import DB_HOST, DB_NAME, DB_PASSWORD, DB_PORT, DB_USER

log = logging.getLogger(__name__)


def _dsn() -> str:
    return f"host={DB_HOST} port={DB_PORT} dbname={DB_NAME} user={DB_USER} password={DB_PASSWORD}"


@contextmanager
def connection() -> Iterator[psycopg2.extensions.connection]:
    conn = psycopg2.connect(_dsn())
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
    """Atomically transition QUEUED -> PROCESSING. Returns False if already claimed or done."""
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


def mark_completed(job_id: str, finding_count: int) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE analysis_jobs
                SET status = 'COMPLETED',
                    completed_at = NOW(),
                    finding_count = %s,
                    error_message = NULL
                WHERE job_id = %s AND status = 'PROCESSING'
                """,
                (finding_count, job_id),
            )


def delete_findings(job_id: str) -> None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM analysis_findings WHERE job_id = %s", (job_id,))


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


def get_repository(job_id: str) -> str | None:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT repository FROM analysis_jobs WHERE job_id = %s", (job_id,))
            row = cur.fetchone()
            return row[0] if row else None
