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

DOCUMENT_CONTENT = b"m4trust real rabbit smoke document"
VIDEO_CONTENT = b"m4trust real rabbit smoke video"
ROOT = Path(__file__).resolve().parents[3]


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        content = VIDEO_CONTENT if self.path.endswith(".mp4") else DOCUMENT_CONTENT
        self.send_response(200)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, _format, *_args):
        return


def timestamp(minutes: int = 0) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def request_template(example_path: str, url: str, file_name: str, content: bytes) -> dict:
    with (ROOT / example_path).open(encoding="utf-8") as source:
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
        sizeBytes=len(content),
        sha256=hashlib.sha256(content).hexdigest(),
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
        for routing_key in (
            "ai.document-extraction.completed.v1",
            "ai.document-extraction.failed.v1",
            "ai.video-analysis.completed.v1",
            "ai.video-analysis.failed.v1",
        ):
            channel.queue_bind(queue, EVENTS_EXCHANGE, routing_key)
        validator = ContractValidator(ROOT / "contracts")
        expected = {}
        scenarios = (
            (
                "contracts/examples/document-extraction/full-request.json",
                f"http://{host_for_worker}:{server.server_port}/document.pdf",
                "smoke-success.pdf",
                DOCUMENT_CONTENT,
                "ai.document-extraction.requested.v1",
                "ai.document-extraction.completed.v1",
            ),
            (
                "contracts/examples/document-extraction/full-request.json",
                f"http://{host_for_worker}:{server.server_port}/document.pdf",
                "fail-retryable-smoke.pdf",
                DOCUMENT_CONTENT,
                "ai.document-extraction.requested.v1",
                "ai.document-extraction.failed.v1",
            ),
            (
                "contracts/examples/video-analysis/full-request.json",
                f"http://{host_for_worker}:{server.server_port}/delivery.mp4",
                "smoke-success.mp4",
                VIDEO_CONTENT,
                "ai.video-analysis.requested.v1",
                "ai.video-analysis.completed.v1",
            ),
            (
                "contracts/examples/video-analysis/full-request.json",
                f"http://{host_for_worker}:{server.server_port}/delivery.mp4",
                "fail-retryable-smoke.mp4",
                VIDEO_CONTENT,
                "ai.video-analysis.requested.v1",
                "ai.video-analysis.failed.v1",
            ),
        )
        for example_path, url, file_name, content, publish_key, expected_routing_key in scenarios:
            request = request_template(example_path, url, file_name, content)
            validator.validate_request(request)
            expected[request["jobId"]] = expected_routing_key
            channel.basic_publish(
                COMMANDS_EXCHANGE,
                publish_key,
                json.dumps(request).encode(),
                pika.BasicProperties(content_type="application/json", delivery_mode=2),
                mandatory=True,
            )
        deadline = time.monotonic() + 30
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
        print("RabbitMQ smoke PASS: document and video download plus completed and failed publishes")
    finally:
        server.shutdown()


if __name__ == "__main__":
    main()
