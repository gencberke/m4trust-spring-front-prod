from __future__ import annotations

import json
import logging
import sys
import time
from typing import Any

import pika

from .config import Settings
from .contracts import ContractValidator, ContractViolation
from .processing import DocumentDownloader, Processor, ScenarioSelector

COMMANDS_EXCHANGE = "m4trust.ai.commands"
EVENTS_EXCHANGE = "m4trust.ai.events"
DEAD_LETTER_EXCHANGE = "m4trust.ai.dead-letter"
REQUEST_QUEUE = "m4trust.ai.document-extraction.v1"
RESULTS_QUEUE = "m4trust.core.ai-results.v1"
DEAD_LETTER_QUEUE = "m4trust.ai.dead-letter.v1"

LOGGER = logging.getLogger("m4trust.mock_ai_worker")


def declare_topology(channel: pika.channel.Channel) -> None:
    for exchange in (COMMANDS_EXCHANGE, EVENTS_EXCHANGE, DEAD_LETTER_EXCHANGE):
        channel.exchange_declare(exchange=exchange, exchange_type="topic", durable=True)
    arguments = {"x-dead-letter-exchange": DEAD_LETTER_EXCHANGE}
    channel.queue_declare(queue=REQUEST_QUEUE, durable=True, arguments=arguments)
    channel.queue_declare(queue=RESULTS_QUEUE, durable=True, arguments=arguments)
    channel.queue_declare(queue=DEAD_LETTER_QUEUE, durable=True)
    channel.queue_bind(REQUEST_QUEUE, COMMANDS_EXCHANGE, "ai.document-extraction.requested.v1")
    channel.queue_bind(RESULTS_QUEUE, EVENTS_EXCHANGE, "ai.document-extraction.completed.v1")
    channel.queue_bind(RESULTS_QUEUE, EVENTS_EXCHANGE, "ai.document-extraction.failed.v1")
    channel.queue_bind(DEAD_LETTER_QUEUE, DEAD_LETTER_EXCHANGE, "#")


class RabbitWorker:
    def __init__(self, settings: Settings, processor: Processor):
        self._settings = settings
        self._processor = processor

    def run(self) -> None:
        credentials = pika.PlainCredentials(self._settings.rabbitmq_user, self._settings.rabbitmq_password)
        parameters = pika.ConnectionParameters(
            host=self._settings.rabbitmq_host,
            port=self._settings.rabbitmq_port,
            credentials=credentials,
            heartbeat=30,
            blocked_connection_timeout=30,
        )
        connection = pika.BlockingConnection(parameters)
        channel = connection.channel()
        declare_topology(channel)
        channel.confirm_delivery()
        channel.basic_qos(prefetch_count=1)
        channel.basic_consume(queue=REQUEST_QUEUE, on_message_callback=self._handle)
        LOGGER.info("Mock worker ready queue=%s", REQUEST_QUEUE)
        channel.start_consuming()

    def _handle(self, channel: Any, method: Any, properties: Any, body: bytes) -> None:
        identifiers: dict[str, Any] = {}
        try:
            request = json.loads(body)
            if isinstance(request, dict):
                identifiers = {key: request.get(key) for key in ("eventId", "jobId", "correlationId")}
            messages = self._processor.process(request)
            for routing_key, event in messages:
                # BlockingChannel publisher confirms raise on nack/unroutable;
                # successful confirmed publishes have no meaningful return value.
                channel.basic_publish(
                    exchange=EVENTS_EXCHANGE,
                    routing_key=routing_key,
                    body=json.dumps(event, separators=(",", ":")).encode("utf-8"),
                    properties=pika.BasicProperties(
                        content_type="application/json",
                        content_encoding="utf-8",
                        delivery_mode=pika.DeliveryMode.Persistent,
                        message_id=event["eventId"],
                        correlation_id=event["correlationId"],
                    ),
                    mandatory=True,
                )
            channel.basic_ack(delivery_tag=method.delivery_tag)
            LOGGER.info("Processed eventId=%s jobId=%s outputs=%s", identifiers.get("eventId"), identifiers.get("jobId"), len(messages))
        except (json.JSONDecodeError, ContractViolation, TypeError, KeyError, ValueError):
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            LOGGER.warning("Rejected contract-invalid request eventId=%s jobId=%s", identifiers.get("eventId"), identifiers.get("jobId"))
        except Exception:
            # One broker redelivery is enough for an uncertain publish/connection
            # failure. A repeatedly failing delivery is poison and goes to the DLQ.
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=not method.redelivered)
            LOGGER.error("Processing failed eventId=%s jobId=%s", identifiers.get("eventId"), identifiers.get("jobId"))


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    settings = Settings.from_environment()
    try:
        settings.validate_startup()
        contracts = ContractValidator(settings.contracts_dir)
        processor = Processor(
            contracts,
            DocumentDownloader(settings.download_timeout_seconds, settings.download_max_attempts,
                               host_override=settings.download_host_override),
            ScenarioSelector(settings.scenario),
        )
        while True:
            try:
                RabbitWorker(settings, processor).run()
            except (pika.exceptions.AMQPConnectionError, pika.exceptions.ConnectionClosedByBroker):
                LOGGER.warning("RabbitMQ unavailable; retrying")
                time.sleep(2)
    except (RuntimeError, FileNotFoundError) as error:
        LOGGER.error("Startup refused: %s", error)
        sys.exit(2)
