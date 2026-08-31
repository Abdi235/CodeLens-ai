"""RabbitMQ consumer for repository analysis jobs."""

from __future__ import annotations

import json
import logging
import signal
import sys
import time

import pika

from app.rabbitmq_url import connection_parameters, describe_connection
from app.worker import db
from app.worker.config import (
    DLQ,
    EXCHANGE,
    FILE_WORKERS,
    MAX_RETRIES,
    QUEUE,
    RABBITMQ_PREFETCH,
    ROUTING_KEY,
    STATUS_QUEUE,
    STATUS_ROUTING_KEY,
    WORKER_ID,
)
from app.worker.pipeline import run_analysis

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("codelens.worker")

_shutdown = False


def _handle_signal(signum, frame):  # noqa: ANN001
    global _shutdown
    log.info("Shutdown signal received (%s), finishing current job...", signum)
    _shutdown = True


def _publish_status(channel: pika.adapters.blocking_connection.BlockingChannel, job_id: str, status: str, **kwargs) -> None:
    payload = {
        "jobId": job_id,
        "status": status,
        "errorMessage": kwargs.get("error_message"),
        "findingCount": kwargs.get("finding_count"),
    }
    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key=STATUS_ROUTING_KEY,
        body=json.dumps(payload),
        properties=pika.BasicProperties(content_type="application/json", delivery_mode=2),
    )
    log.info("job_id=%s published status=%s", job_id, status)


def _process_message(channel, method, properties, body: bytes) -> None:
    delivery_tag = method.delivery_tag
    try:
        message = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError:
        log.error("Invalid JSON message, sending to DLQ")
        channel.basic_nack(delivery_tag=delivery_tag, requeue=False)
        return

    job_id = message.get("jobId")
    attempt = int(message.get("attempt", 1))
    repository = message.get("repository") or db.get_repository(job_id)

    if not job_id:
        log.error("Missing jobId in message")
        channel.basic_nack(delivery_tag=delivery_tag, requeue=False)
        return

    status = db.get_job_status(job_id)
    if status == "COMPLETED":
        log.info("job_id=%s already COMPLETED, acking duplicate", job_id)
        channel.basic_ack(delivery_tag=delivery_tag)
        return

    if not db.try_claim_job(job_id):
        status = db.get_job_status(job_id)
        if status in {"PROCESSING", "COMPLETED", "FAILED"}:
            log.info("job_id=%s already claimed or done (status=%s), acking", job_id, status)
            channel.basic_ack(delivery_tag=delivery_tag)
            return
        log.warning("job_id=%s could not be claimed, nacking for retry", job_id)
        channel.basic_nack(delivery_tag=delivery_tag, requeue=True)
        return

    _publish_status(channel, job_id, "PROCESSING")
    db.set_worker_id(job_id, WORKER_ID)

    try:
        if not repository:
            raise ValueError("Repository not found for job")
        start = time.perf_counter()
        finding_count = run_analysis(job_id, repository)
        duration_ms = int((time.perf_counter() - start) * 1000)
        db.mark_completed(job_id, finding_count, duration_ms)
        _publish_status(channel, job_id, "COMPLETED", finding_count=finding_count)
        channel.basic_ack(delivery_tag=delivery_tag)
    except Exception as exc:  # noqa: BLE001
        error = str(exc)
        log.exception("job_id=%s failed attempt=%s: %s", job_id, attempt, error)
        if attempt < MAX_RETRIES:
            log.info("job_id=%s requeueing attempt %s", job_id, attempt + 1)
            retry_message = {**message, "attempt": attempt + 1}
            channel.basic_publish(
                exchange=EXCHANGE,
                routing_key=ROUTING_KEY,
                body=json.dumps(retry_message),
                properties=pika.BasicProperties(content_type="application/json", delivery_mode=2),
            )
            db.mark_failed(job_id, f"Retry scheduled: {error}")
            _publish_status(channel, job_id, "QUEUED", error_message=error)
            channel.basic_ack(delivery_tag=delivery_tag)
        else:
            db.mark_failed(job_id, error)
            _publish_status(channel, job_id, "FAILED", error_message=error)
            channel.basic_nack(delivery_tag=delivery_tag, requeue=False)


def _declare_topology(channel: pika.adapters.blocking_connection.BlockingChannel) -> None:
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="direct", durable=True)
    channel.queue_declare(
        queue=QUEUE,
        durable=True,
        arguments={
            "x-dead-letter-exchange": "",
            "x-dead-letter-routing-key": DLQ,
        },
    )
    channel.queue_declare(queue=DLQ, durable=True)
    channel.queue_declare(queue=STATUS_QUEUE, durable=True)
    channel.queue_bind(queue=QUEUE, exchange=EXCHANGE, routing_key=ROUTING_KEY)
    channel.queue_bind(queue=STATUS_QUEUE, exchange=EXCHANGE, routing_key=STATUS_ROUTING_KEY)
    channel.basic_qos(prefetch_count=RABBITMQ_PREFETCH)


def main() -> None:
    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    params = connection_parameters()

    log.info(
        "Connecting to RabbitMQ %s prefetch=%s file_workers=%s worker_id=%s",
        describe_connection(),
        RABBITMQ_PREFETCH,
        FILE_WORKERS,
        WORKER_ID,
    )

    while not _shutdown:
        try:
            connection = pika.BlockingConnection(params)
            channel = connection.channel()
            _declare_topology(channel)

            def callback(ch, method, properties, body):  # noqa: ANN001
                if _shutdown:
                    ch.basic_nack(method.delivery_tag, requeue=True)
                    return
                _process_message(ch, method, properties, body)

            channel.basic_consume(queue=QUEUE, on_message_callback=callback, auto_ack=False)
            log.info("Worker ready, consuming queue=%s", QUEUE)

            while not _shutdown:
                connection.process_data_events(time_limit=1)

            channel.stop_consuming()
            connection.close()
            break
        except pika.exceptions.AMQPConnectionError as exc:
            log.error("RabbitMQ connection failed: %s — retrying in 5s", exc)
            time.sleep(5)

    log.info("Worker shutdown complete")
    sys.exit(0)


if __name__ == "__main__":
    main()
