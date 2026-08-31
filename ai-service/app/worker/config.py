"""Environment configuration for the analysis worker."""

from __future__ import annotations

import os


def env(key: str, default: str | None = None) -> str:
    value = os.getenv(key, default)
    if value is None:
        raise RuntimeError(f"Missing required environment variable: {key}")
    return value


RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "guest")
RABBITMQ_PREFETCH = int(os.getenv("RABBITMQ_PREFETCH", "1"))
MAX_RETRIES = int(os.getenv("WORKER_MAX_RETRIES", "3"))
FILE_WORKERS = int(os.getenv("FILE_WORKERS", "4"))
WORKSPACE_DIR = os.getenv("WORKSPACE_DIR", "/tmp/secureai-workspace")

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5432"))
DB_NAME = os.getenv("DB_NAME", "secureai")
DB_USER = os.getenv("DB_USER", "secureai")
DB_PASSWORD = os.getenv("DB_PASSWORD", "secureai")

EXCHANGE = "secureai.analysis.exchange"
QUEUE = "secureai.analysis.queue"
STATUS_ROUTING_KEY = "status"
ROUTING_KEY = "analysis"

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
