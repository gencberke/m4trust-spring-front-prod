import hashlib
import json
from pathlib import Path
import unittest


MATRIX = Path(__file__).parents[1] / "fixtures" / "transport-matrix.json"


def load_matrix():
    return json.loads(MATRIX.read_text(encoding="utf-8"))


class TransportMatrixTest(unittest.TestCase):
    def test_check_key_vectors_preserve_documented_input_order_and_exact_hashes(self):
        matrix = load_matrix()
        check_key = matrix["authentication"]["checkKey"]

        self.assertEqual(check_key["inputOrder"], ["DealerCode", "MK", "Username", "PD", "Password"])
        for example in check_key["examples"]:
            source = f'{example["dealerCode"]}MK{example["username"]}PD{example["password"]}'
            self.assertEqual(source, example["input"])
            self.assertEqual(hashlib.sha256(source.encode()).hexdigest(), example["sha256LowerHex"])

    def test_money_bounds_timeout_redaction_and_unknown_gaps_are_explicit(self):
        matrix = load_matrix()

        self.assertEqual(matrix["money"]["examples"], [
            {"amountMinor": 0, "currency": "TRY", "providerAmount": "0.00"},
            {"amountMinor": 1, "currency": "TRY", "providerAmount": "0.01"},
            {"amountMinor": 2750, "currency": "TRY", "providerAmount": "27.50"},
            {"amountMinor": 123456789, "currency": "USD", "providerAmount": "1234567.89"},
        ])
        self.assertEqual(matrix["limits"]["maxRequestBytes"], 8192)
        self.assertEqual(matrix["limits"]["maxResponseBytes"], 16384)
        self.assertTrue({"CONNECT_TIMEOUT", "READ_TIMEOUT", "MALFORMED_RESPONSE", "SANITIZED_PROVIDER_ERROR", "NOT_FOUND"} <= set(matrix["limits"].values()))
        self.assertTrue({"Password", "CheckKey", "raw request body", "raw response body", "unsafe provider message"} <= set(matrix["redaction"]["neverLogOrReturn"]))
        self.assertTrue(any("duplicate-OtherTrxCode" in gap for gap in matrix["unknown"]))
        self.assertTrue(any("finality" in gap for gap in matrix["unknown"]))

    def test_matrix_covers_the_required_emulator_scenario_categories_without_g1_claims(self):
        matrix = load_matrix()
        categories = matrix["scenarios"]["categories"]

        self.assertTrue(matrix["scenarios"]["startupConfigurationOnly"])
        self.assertTrue({"success", "decline", "duplicate", "not_found", "timeout", "malformed_sanitized_error", "late_query_resolution"} <= set(categories))
        self.assertIn("not Moka evidence", matrix["purpose"])
