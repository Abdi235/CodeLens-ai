"""Environment configuration for the CodeLens analysis worker."""

from __future__ import annotations

import os
import socket

WORKER_ID = os.getenv("WORKER_ID", socket.gethostname())

RABBITMQ_PREFETCH = int(os.getenv("RABBITMQ_PREFETCH", "1"))
MAX_RETRIES = int(os.getenv("WORKER_MAX_RETRIES", "3"))
FILE_WORKERS = int(os.getenv("FILE_WORKERS", "4"))
WORKSPACE_DIR = os.getenv("WORKSPACE_DIR", "/tmp/codelens-workspace")

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5432"))
DB_NAME = os.getenv("DB_NAME", os.getenv("POSTGRES_DB", "codelens"))
DB_USER = os.getenv("DB_USER", os.getenv("POSTGRES_USER", "codelens"))
DB_PASSWORD = os.getenv("DB_PASSWORD", os.getenv("POSTGRES_PASSWORD", "codelens"))
DATABASE_URL = os.getenv("DATABASE_URL", "").strip()

EXCHANGE = "codelens.analysis.exchange"
QUEUE = "codelens.analysis.queue"
DLQ = "codelens.analysis.dlq"
STATUS_QUEUE = "codelens.job.status.queue"
STATUS_ROUTING_KEY = "status"
ROUTING_KEY = "analysis"

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
