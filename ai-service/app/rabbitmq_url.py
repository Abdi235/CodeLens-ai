"""Parse CloudAMQP / AMQP connection URLs for pika."""

from __future__ import annotations

import os
from urllib.parse import urlparse, unquote

import pika


def connection_parameters() -> pika.ConnectionParameters:
    url = os.getenv("RABBITMQ_URL", "").strip()
    if url:
        return pika.URLParameters(url)

    host = os.getenv("RABBITMQ_HOST", "localhost")
    port = int(os.getenv("RABBITMQ_PORT", "5672"))
    user = os.getenv("RABBITMQ_USER", "guest")
    password = os.getenv("RABBITMQ_PASSWORD", "guest")
    vhost = os.getenv("RABBITMQ_VHOST", "/")

    credentials = pika.PlainCredentials(user, password)
    return pika.ConnectionParameters(
        host=host,
        port=port,
        virtual_host=vhost,
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300,
    )


def describe_connection() -> str:
    url = os.getenv("RABBITMQ_URL", "").strip()
    if url:
        parsed = urlparse(url)
        return f"{parsed.hostname}:{parsed.port or 5672} (from RABBITMQ_URL)"
    host = os.getenv("RABBITMQ_HOST", "localhost")
    port = os.getenv("RABBITMQ_PORT", "5672")
    return f"{host}:{port}"
