import http.client
import json
import threading
import unittest

from m4trust_moka_emulator.config import Settings
from m4trust_moka_emulator.server import MokaEmulatorServer


class EmulatorServerTest(unittest.TestCase):
    def setUp(self):
        settings = Settings(
            True, "local", "127.0.0.1", 0, ("timeout_then_late_success",), 8192, 2
        )
        self.server = MokaEmulatorServer(settings)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def request(self, path, payload=None, raw=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=1)
        body = raw if raw is not None else (json.dumps(payload).encode() if payload is not None else None)
        headers = {"Content-Type": "application/json"} if body is not None else {}
        connection.request("POST" if body is not None else "GET", path, body=body, headers=headers)
        response = connection.getresponse()
        result = response.status, response.read()
        connection.close()
        return result

    def test_repeated_identity_and_query_first_late_recovery_are_deterministic(self):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=1)
        connection.request(
            "POST",
            "/PaymentDealer/DoDirectPayment",
            body=b'{"OtherTrxCode":"late-key"}',
            headers={"Content-Type": "application/json"},
        )
        with self.assertRaises(http.client.RemoteDisconnected):
            connection.getresponse()
        connection.close()

        repeated = http.client.HTTPConnection(
            "127.0.0.1", self.server.server_port, timeout=1
        )
        repeated.request(
            "POST",
            "/PaymentDealer/DoDirectPayment",
            body=b'{"OtherTrxCode":"late-key"}',
            headers={"Content-Type": "application/json"},
        )
        with self.assertRaises(http.client.RemoteDisconnected):
            repeated.getresponse()
        repeated.close()

        operations, next_scenario = self.server.state.snapshot()
        self.assertEqual((set(operations), next_scenario), ({"late-key"}, 1))
        status, first_query = self.request(
            "/PaymentDealer/GetDealerPaymentTrxDetailList", {"OtherTrxCode": "late-key"}
        )
        self.assertEqual((status, json.loads(first_query)["ResultCode"]), (200, "UNCONFIRMED"))
        status, second_query = self.request(
            "/PaymentDealer/GetDealerPaymentTrxDetailList", {"OtherTrxCode": "late-key"}
        )
        self.assertEqual((status, json.loads(second_query)["ResultCode"]), (200, "SUCCESS"))

    def test_request_bounds_and_identity_validation_are_safe(self):
        status, body = self.request("/PaymentDealer/DoDirectPayment", raw=b"x" * 8193)
        self.assertEqual((status, json.loads(body)), (413, {"code": "REQUEST_SIZE_INVALID"}))
        status, body = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "unsafe message\n"})
        self.assertEqual((status, json.loads(body)), (400, {"code": "INVALID_OTHER_TRX_CODE"}))


class ConfigurationTest(unittest.TestCase):
    def test_production_like_or_disabled_startup_is_refused(self):
        with self.assertRaisesRegex(RuntimeError, "forbidden"):
            Settings(True, "production", "127.0.0.1", 0, ("success",), 8192, 2).validate_startup()
        with self.assertRaisesRegex(RuntimeError, "requires"):
            Settings(False, "local", "127.0.0.1", 0, ("success",), 8192, 2).validate_startup()
