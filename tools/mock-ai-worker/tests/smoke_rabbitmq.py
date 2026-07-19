"""Optional real-broker smoke test; not part of the fast pytest suite."""
from __future__ import annotations

import copy
import hashlib
import json
import os
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import pika

from m4trust_mock_worker.contracts import ContractValidator
from m4trust_mock_worker.worker import COMMANDS_EXCHANGE, EVENTS_EXCHANGE, declare_topology

CONTENT = b"m4trust real rabbit smoke document"
ROOT = Path(__file__).resolve().parents[3]


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Length", str(len(CONTENT)))
        self.end_headers()
        self.wfile.write(CONTENT)

    def log_message(self, _format, *_args):
        return


def timestamp(minutes: int = 0) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def request_template(url: str, file_name: str) -> dict:
    with (ROOT / "contracts/examples/document-extraction/full-request.json").open(encoding="utf-8") as source:
        event = copy.deepcopy(json.load(source))
    event_id = str(uuid.uuid4())
    event.update(
        eventId=event_id,
        occurredAt=timestamp(),
        correlationId=str(uuid.uuid4()),
        jobId=str(uuid.uuid4()),
        idempotencyKey=f"mock-smoke:{event_id}",
    )
    event["payload"]["deadlineAt"] = timestamp(5)
    event["payload"]["input"].update(
        fileName=file_name,
        sizeBytes=len(CONTENT),
        sha256=hashlib.sha256(CONTENT).hexdigest(),
        download={"url": url, "expiresAt": timestamp(5)},
    )
    return event


def main() -> None:
    host_for_worker = os.getenv("M4TRUST_SMOKE_DOWNLOAD_HOST", "127.0.0.1")
    server = ThreadingHTTPServer(("0.0.0.0", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        credentials = pika.PlainCredentials(
            os.getenv("M4TRUST_RABBITMQ_USER", "m4trust_local"),
            os.getenv("M4TRUST_RABBITMQ_PASSWORD", "m4trust_local_password"),
        )
        connection = pika.BlockingConnection(pika.ConnectionParameters(
            os.getenv("M4TRUST_RABBITMQ_HOST", "127.0.0.1"),
            int(os.getenv("M4TRUST_RABBITMQ_PORT", "5672")),
            credentials=credentials,
        ))
        channel = connection.channel()
        declare_topology(channel)
        queue = channel.queue_declare(queue="", exclusive=True, auto_delete=True).method.queue
        channel.queue_bind(queue, EVENTS_EXCHANGE, "ai.document-extraction.completed.v1")
        channel.queue_bind(queue, EVENTS_EXCHANGE, "ai.document-extraction.failed.v1")
        validator = ContractValidator(ROOT / "contracts")
        expected = {}
        for file_name, routing_key in (
            ("smoke-success.pdf", "ai.document-extraction.completed.v1"),
            ("fail-retryable-smoke.pdf", "ai.document-extraction.failed.v1"),
        ):
            request = request_template(f"http://{host_for_worker}:{server.server_port}/document.pdf", file_name)
            validator.validate_request(request)
            expected[request["jobId"]] = routing_key
            channel.basic_publish(
                COMMANDS_EXCHANGE,
                "ai.document-extraction.requested.v1",
                json.dumps(request).encode(),
                pika.BasicProperties(content_type="application/json", delivery_mode=2),
                mandatory=True,
            )
        deadline = time.monotonic() + 20
        received = {}
        while time.monotonic() < deadline and len(received) < len(expected):
            method, _, body = channel.basic_get(queue, auto_ack=True)
            if method is None:
                time.sleep(0.1)
                continue
            event = json.loads(body)
            if event.get("jobId") not in expected:
                continue
            if event["eventType"] == "ai.job.completed.v1":
                validator.validate_completed(event)
            else:
                validator.validate_failed(event)
            received[event["jobId"]] = method.routing_key
        assert received == expected, f"expected {expected}, received {received}"
        connection.close()
        print("RabbitMQ smoke PASS: real download plus completed and failed publishes")
    finally:
        server.shutdown()


if __name__ == "__main__":
    main()
