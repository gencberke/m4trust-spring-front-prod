from __future__ import annotations

import copy
import hashlib
import json
import threading
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import pytest

from m4trust_mock_worker.contracts import ContractValidator


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture(scope="session")
def contracts() -> ContractValidator:
    return ContractValidator(REPOSITORY_ROOT / "contracts")


@pytest.fixture
def document_bytes() -> bytes:
    return b"deterministic mock contract bytes"


@pytest.fixture
def request_event(document_bytes: bytes):
    with (REPOSITORY_ROOT / "contracts/examples/document-extraction/full-request.json").open(encoding="utf-8") as source:
        request = copy.deepcopy(json.load(source))
    request["occurredAt"] = _future_timestamp(minutes=-1)
    request["payload"]["deadlineAt"] = _future_timestamp(minutes=10)
    request["payload"]["input"]["sizeBytes"] = len(document_bytes)
    request["payload"]["input"]["sha256"] = hashlib.sha256(document_bytes).hexdigest()
    request["payload"]["input"]["download"]["expiresAt"] = _future_timestamp(minutes=10)
    return request


@pytest.fixture
def video_bytes() -> bytes:
    return b"deterministic mock video bytes for advisory analysis"


@pytest.fixture
def video_request_event(video_bytes: bytes):
    with (REPOSITORY_ROOT / "contracts/examples/video-analysis/full-request.json").open(encoding="utf-8") as source:
        request = copy.deepcopy(json.load(source))
    request["occurredAt"] = _future_timestamp(minutes=-1)
    request["payload"]["deadlineAt"] = _future_timestamp(minutes=10)
    request["payload"]["input"]["sizeBytes"] = len(video_bytes)
    request["payload"]["input"]["sha256"] = hashlib.sha256(video_bytes).hexdigest()
    request["payload"]["input"]["download"]["expiresAt"] = _future_timestamp(minutes=10)
    return request


@pytest.fixture
def download_server(document_bytes: bytes):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Type", "application/pdf")
            self.send_header("Content-Length", str(len(document_bytes)))
            self.end_headers()
            self.wfile.write(document_bytes)

        def log_message(self, _format, *_args):
            return

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}/document.pdf"
    finally:
        server.shutdown()
        thread.join(timeout=2)


@pytest.fixture
def video_download_server(video_bytes: bytes):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Content-Length", str(len(video_bytes)))
            self.end_headers()
            self.wfile.write(video_bytes)

        def log_message(self, _format, *_args):
            return

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}/delivery-evidence.mp4"
    finally:
        server.shutdown()
        thread.join(timeout=2)


def _future_timestamp(minutes: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat(timespec="milliseconds").replace("+00:00", "Z")
