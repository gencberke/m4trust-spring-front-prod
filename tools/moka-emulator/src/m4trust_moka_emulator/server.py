import json
import re
import threading
from dataclasses import dataclass, replace
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .config import Settings


@dataclass
class Operation:
    other_trx_code: str
    scenario: str
    query_count: int = 0
    pool_approved: bool = False


class EmulatorState:
    """Ephemeral per-process identity state; it intentionally disappears on restart."""

    def __init__(self, settings: Settings):
        self._settings = settings
        self._operations = {}
        self._next_scenario = 0
        self._lock = threading.Lock()

    def initiate(self, other_trx_code: str):
        with self._lock:
            operation = self._operations.get(other_trx_code)
            if operation is not None:
                return replace(operation), True
            scenario = self._settings.scenarios[self._next_scenario % len(self._settings.scenarios)]
            self._next_scenario += 1
            operation = Operation(other_trx_code, scenario)
            self._operations[other_trx_code] = operation
            return replace(operation), False

    def query(self, other_trx_code: str):
        with self._lock:
            operation = self._operations.get(other_trx_code)
            if operation is not None:
                operation.query_count += 1
                return replace(operation)
            return None

    def approve_pool_probe(self, other_trx_code: str):
        with self._lock:
            operation = self._operations.get(other_trx_code)
            if operation is not None:
                operation.pool_approved = True
                return replace(operation)
            return None

    def snapshot(self):
        with self._lock:
            return {key: replace(operation) for key, operation in self._operations.items()}, self._next_scenario


class MokaEmulatorServer(ThreadingHTTPServer):
    def __init__(self, settings: Settings):
        self.settings = settings
        self.state = EmulatorState(settings)
        super().__init__((settings.host, settings.port), MokaEmulatorHandler)


class MokaEmulatorHandler(BaseHTTPRequestHandler):
    server: MokaEmulatorServer
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/health":
            self._json(HTTPStatus.OK, {"status": "UP", "service": "moka-emulator"})
            return
        self._json(HTTPStatus.NOT_FOUND, {"code": "ROUTE_NOT_FOUND"})

    def do_POST(self):
        payload = self._read_json()
        if payload is None:
            return
        if self.path == "/PaymentDealer/DoDirectPayment":
            self._initiate(payload)
        elif self.path == "/PaymentDealer/GetDealerPaymentTrxDetailList":
            self._query(payload)
        elif self.path == "/PaymentDealer/DoApprovePoolPayment":
            self._approve_pool_probe(payload)
        else:
            self._json(HTTPStatus.NOT_FOUND, {"code": "ROUTE_NOT_FOUND"})

    def _initiate(self, payload):
        other_trx_code = self._other_trx_code(payload)
        if other_trx_code is None:
            return
        operation, duplicate = self.server.state.initiate(other_trx_code)
        if operation.scenario == "timeout_then_late_success":
            self.close_connection = True
            return
        if operation.scenario == "malformed_error":
            body = b'{"IsSuccessful":false,"ResultCode":"EMULATOR_MALFORMED"'
            self.send_response(HTTPStatus.BAD_GATEWAY)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if operation.scenario == "decline":
            self._json(HTTPStatus.OK, self._result(False, "DECLINED", operation, duplicate))
            return
        self._json(HTTPStatus.OK, self._result(True, "SUCCESS", operation, duplicate))

    def _query(self, payload):
        other_trx_code = self._other_trx_code(payload)
        if other_trx_code is None:
            return
        operation = self.server.state.query(other_trx_code)
        if operation is None or operation.scenario == "not_found":
            self._json(HTTPStatus.OK, {"IsSuccessful": False, "ResultCode": "NOT_FOUND"})
            return
        if operation.scenario == "timeout_then_late_success" and operation.query_count < self.server.settings.late_result_after_queries:
            self._json(HTTPStatus.OK, {"IsSuccessful": False, "ResultCode": "UNCONFIRMED"})
            return
        if operation.scenario == "decline":
            self._json(HTTPStatus.OK, self._result(False, "DECLINED", operation, False))
            return
        self._json(HTTPStatus.OK, self._result(True, "SUCCESS", operation, False))

    def _approve_pool_probe(self, payload):
        other_trx_code = self._other_trx_code(payload)
        if other_trx_code is None:
            return
        operation = self.server.state.approve_pool_probe(other_trx_code)
        if operation is None:
            self._json(HTTPStatus.OK, {"IsSuccessful": False, "ResultCode": "NOT_FOUND"})
            return
        self._json(HTTPStatus.OK, {"IsSuccessful": True, "ResultCode": "EMULATOR_PROBE_ONLY", "OtherTrxCode": other_trx_code})

    def _other_trx_code(self, payload):
        candidate = payload.get("OtherTrxCode")
        if not isinstance(candidate, str) or not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", candidate):
            self._json(HTTPStatus.BAD_REQUEST, {"code": "INVALID_OTHER_TRX_CODE"})
            return None
        return candidate

    def _read_json(self):
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._json(HTTPStatus.BAD_REQUEST, {"code": "INVALID_CONTENT_LENGTH"})
            return None
        if content_length <= 0 or content_length > self.server.settings.max_request_bytes:
            self._json(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"code": "REQUEST_SIZE_INVALID"})
            return None
        try:
            payload = json.loads(self.rfile.read(content_length))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._json(HTTPStatus.BAD_REQUEST, {"code": "INVALID_JSON"})
            return None
        if not isinstance(payload, dict):
            self._json(HTTPStatus.BAD_REQUEST, {"code": "INVALID_JSON_OBJECT"})
            return None
        return payload

    @staticmethod
    def _result(success, code, operation, duplicate):
        return {
            "IsSuccessful": success,
            "ResultCode": code,
            "OtherTrxCode": operation.other_trx_code,
            "VirtualPosOrderId": f"emulator-{operation.other_trx_code}",
            "Duplicate": duplicate,
        }

    def _json(self, status, body):
        encoded = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, _format, *_args):
        return


def serve(settings: Settings):
    settings.validate_startup()
    server = MokaEmulatorServer(settings)
    try:
        server.serve_forever()
    finally:
        server.server_close()
