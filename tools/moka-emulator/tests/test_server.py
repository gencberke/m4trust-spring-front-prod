import http.client
import json
import queue
import sys
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from m4trust_moka_emulator.config import Settings
from m4trust_moka_emulator.server import MokaEmulatorServer


class EmulatorServerTest(unittest.TestCase):
    def setUp(self):
        self.server = self.start_server("success,decline,timeout_then_late_success,malformed_error,not_found")

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def start_server(self, scenarios, late_result_after_queries=2):
        settings = Settings(True, "local", "127.0.0.1", 0, tuple(scenarios.split(",")), 8192, late_result_after_queries)
        server = MokaEmulatorServer(settings)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        return server

    def request(self, path, payload=None, raw=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=1)
        body = raw if raw is not None else (json.dumps(payload).encode() if payload is not None else None)
        headers = {"Content-Type": "application/json"} if body is not None else {}
        connection.request("POST" if body is not None else "GET", path, body=body, headers=headers)
        response = connection.getresponse()
        result = response.status, response.read()
        connection.close()
        return result

    def test_health_and_only_bounded_routes_are_exposed(self):
        status, body = self.request("/health")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), {"status": "UP", "service": "moka-emulator"})
        status, body = self.request("/unbounded-route")
        self.assertEqual(status, 404)
        self.assertEqual(json.loads(body), {"code": "ROUTE_NOT_FOUND"})

    def test_scenario_sequence_duplicate_identity_and_decline_are_deterministic(self):
        status, first = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "operation-1"})
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(first)["IsSuccessful"])
        status, duplicate = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "operation-1"})
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(duplicate)["Duplicate"])
        _, next_scenario = self.server.state.snapshot()
        self.assertEqual(next_scenario, 1)
        status, declined = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "operation-2"})
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(declined)["ResultCode"], "DECLINED")

    def test_timeout_restart_and_late_query_resolution_are_ephemeral_and_deterministic(self):
        self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "consume-success"})
        self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "consume-decline"})
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=1)
        connection.request("POST", "/PaymentDealer/DoDirectPayment", body=b'{"OtherTrxCode":"late-key"}', headers={"Content-Type": "application/json"})
        with self.assertRaises(http.client.RemoteDisconnected):
            connection.getresponse()
        connection.close()
        operations, _ = self.server.state.snapshot()
        self.assertIn("late-key", operations)
        status, unresolved = self.request("/PaymentDealer/GetDealerPaymentTrxDetailList", {"OtherTrxCode": "late-key"})
        self.assertEqual((status, json.loads(unresolved)["ResultCode"]), (200, "UNCONFIRMED"))
        status, resolved = self.request("/PaymentDealer/GetDealerPaymentTrxDetailList", {"OtherTrxCode": "late-key"})
        self.assertEqual((status, json.loads(resolved)["ResultCode"]), (200, "SUCCESS"))
        restarted = self.start_server("success,decline,timeout_then_late_success")
        try:
            operations, next_scenario = restarted.state.snapshot()
            self.assertEqual(operations, {})
            self.assertEqual(next_scenario, 0)
        finally:
            restarted.shutdown()
            restarted.server_close()

    def test_malformed_not_found_and_pool_probe_are_transport_only(self):
        self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "success"})
        self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "decline"})
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=1)
        connection.request("POST", "/PaymentDealer/DoDirectPayment", body=b'{"OtherTrxCode":"timeout"}', headers={"Content-Type": "application/json"})
        with self.assertRaises(http.client.RemoteDisconnected):
            connection.getresponse()
        connection.close()
        status, malformed = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "malformed"})
        self.assertEqual(status, 502)
        with self.assertRaises(json.JSONDecodeError):
            json.loads(malformed)
        status, missing = self.request("/PaymentDealer/GetDealerPaymentTrxDetailList", {"OtherTrxCode": "missing"})
        self.assertEqual((status, json.loads(missing)["ResultCode"]), (200, "NOT_FOUND"))
        status, probe = self.request("/PaymentDealer/DoApprovePoolPayment", {"OtherTrxCode": "success"})
        self.assertEqual((status, json.loads(probe)["ResultCode"]), (200, "EMULATOR_PROBE_ONLY"))

    def test_request_body_is_bounded_and_invalid_input_is_sanitized(self):
        status, body = self.request("/PaymentDealer/DoDirectPayment", raw=b"x" * 8193)
        self.assertEqual((status, json.loads(body)), (413, {"code": "REQUEST_SIZE_INVALID"}))
        status, body = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": ""})
        self.assertEqual((status, json.loads(body)), (400, {"code": "INVALID_OTHER_TRX_CODE"}))
        status, body = self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "unsafe message\n"})
        self.assertEqual((status, json.loads(body)), (400, {"code": "INVALID_OTHER_TRX_CODE"}))

    def test_concurrent_same_identity_consumes_one_scenario_and_returns_one_canonical_result(self):
        request_count = 12
        start = threading.Barrier(request_count)
        results = queue.Queue()

        def initiate():
            try:
                start.wait(timeout=2)
                results.put(self.request("/PaymentDealer/DoDirectPayment", {"OtherTrxCode": "concurrent-key"}))
            except Exception as error:
                results.put(error)

        threads = [threading.Thread(target=initiate) for _ in range(request_count)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join(timeout=3)

        observed = [results.get_nowait() for _ in range(request_count)]
        self.assertFalse(any(isinstance(result, Exception) for result in observed))
        payloads = [json.loads(body) for status, body in observed]
        self.assertEqual({status for status, _ in observed}, {200})
        self.assertEqual(sum(not payload["Duplicate"] for payload in payloads), 1)
        self.assertEqual(sum(payload["Duplicate"] for payload in payloads), request_count - 1)
        self.assertEqual({payload["ResultCode"] for payload in payloads}, {"SUCCESS"})
        self.assertEqual({payload["VirtualPosOrderId"] for payload in payloads}, {"emulator-concurrent-key"})
        operations, next_scenario = self.server.state.snapshot()
        self.assertEqual(set(operations), {"concurrent-key"})
        self.assertEqual(next_scenario, 1)


class ConfigurationTest(unittest.TestCase):
    def test_production_like_environment_and_invalid_startup_selection_are_refused(self):
        with self.assertRaisesRegex(RuntimeError, "forbidden"):
            Settings(True, "production", "127.0.0.1", 0, ("success",), 8192, 2).validate_startup()
        with self.assertRaisesRegex(RuntimeError, "requires"):
            Settings(False, "local", "127.0.0.1", 0, ("success",), 8192, 2).validate_startup()
        with self.assertRaisesRegex(RuntimeError, "supported"):
            Settings(True, "local", "127.0.0.1", 0, ("success", "runtime-selected"), 8192, 2).validate_startup()

    def test_core_production_bootstrap_does_not_package_or_select_the_emulator(self):
        root = Path(__file__).parents[3]
        core_dockerfile = (root / "services" / "core-api" / "Dockerfile").read_text(encoding="utf-8")
        core_pom = (root / "services" / "core-api" / "pom.xml").read_text(encoding="utf-8")

        self.assertNotIn("moka-emulator", core_dockerfile)
        self.assertNotIn("moka-emulator", core_pom)
