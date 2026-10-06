import os
import unittest
from unittest.mock import patch

from preflight import evaluate_preflight


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.documents = [
            self.document("AGREEMENT", "TTA-26-00872 시험합의서의 시험 대상과 범위를 설명합니다. " * 8),
            self.document("FUNCTION_LIST", "PrintChaser의 인쇄 설정, 작업 관리와 사용자 기능을 설명합니다. " * 8),
            self.document("MANUAL", "PrintChaser 관리자 매뉴얼의 인쇄 작업과 설정 방법입니다. " * 8),
        ]

    def test_unconfigured_jev_allows_documents_with_warning(self):
        with patch.dict(os.environ, {}, clear=True):
            result = evaluate_preflight(self.documents)

        self.assertEqual("READY_WITH_WARNINGS", result["decision"])
        self.assertIn("JEV_NOT_CONFIGURED", self.warning_codes(result))
        self.assertFalse(result["diagnostics"]["jevEvaluated"])

    def test_small_amount_of_unreadable_characters_warns(self):
        damaged = self.documents.copy()
        damaged[1] = self.document("FUNCTION_LIST", "A" * 99 + "\ufffd")

        with patch.dict(os.environ, {}, clear=True):
            result = evaluate_preflight(damaged)

        self.assertNotEqual("BLOCKED", result["decision"])
        self.assertIn("TEXT_DAMAGE_SUSPECTED", self.warning_codes(result))
        warning = next(w for w in result["warnings"] if w["code"] == "TEXT_DAMAGE_SUSPECTED")
        self.assertEqual(1, warning["evidence"]["suspiciousCharacterCount"])
        self.assertEqual(0.01, warning["evidence"]["warningThreshold"])

    def test_heavy_unreadable_characters_block(self):
        damaged = self.documents.copy()
        damaged[1] = self.document("FUNCTION_LIST", "A" * 80 + "\ufffd" * 20)

        with patch.dict(os.environ, {}, clear=True):
            result = evaluate_preflight(damaged)

        self.assertEqual("BLOCKED", result["decision"])
        self.assertIn("SEVERE_TEXT_DAMAGE", self.warning_codes(result))

    def test_missing_test_code_warns_without_blocking(self):
        documents = [self.document("AGREEMENT", "시험합의서의 제품 범위와 일정을 충분히 설명합니다. " * 8), *self.documents[1:]]

        with patch.dict(os.environ, {}, clear=True):
            result = evaluate_preflight(documents)

        self.assertNotEqual("BLOCKED", result["decision"])
        self.assertIn("AGREEMENT_TEST_CODE_NOT_FOUND", self.warning_codes(result))

    def test_high_confidence_product_mismatch_blocks(self):
        answers = self.jev_answers()
        answers["same_product_AGREEMENT_MANUAL"] = {"choice": "different", "confidence": 0.95}

        with patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}), \
             patch("preflight._evaluate_with_jev", return_value=answers):
            result = evaluate_preflight(self.documents)

        self.assertEqual("BLOCKED", result["decision"])
        self.assertIn("PRODUCT_MISMATCH", self.warning_codes(result))

    def test_uncertain_product_identity_warns_and_proceeds(self):
        answers = self.jev_answers()
        answers["same_product_AGREEMENT_MANUAL"] = {"choice": "uncertain", "confidence": 0.55}

        with patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}), \
             patch("preflight._evaluate_with_jev", return_value=answers):
            result = evaluate_preflight(self.documents)

        self.assertEqual("READY_WITH_WARNINGS", result["decision"])
        self.assertIn("PRODUCT_IDENTITY_UNCERTAIN", self.warning_codes(result))
        warning = next(w for w in result["warnings"] if w["code"] == "PRODUCT_IDENTITY_UNCERTAIN")
        self.assertEqual("uncertain", warning["evidence"]["choice"])
        self.assertEqual(0.55, warning["evidence"]["confidence"])
        self.assertFalse(warning["evidence"]["explanationCaptured"])

    def test_partial_readability_warning_keeps_jev_label_and_confidence(self):
        answers = self.jev_answers()
        answers["readability_MANUAL"] = {"choice": "partially_readable", "confidence": 0.74}
        with patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}), \
             patch("preflight._evaluate_with_jev", return_value=answers):
            result = evaluate_preflight(self.documents)
        warning = next(w for w in result["warnings"] if w["code"] == "DOCUMENT_PARTIALLY_READABLE")
        self.assertEqual("partially_readable", warning["evidence"]["choice"])
        self.assertEqual(0.74, warning["evidence"]["confidence"])
        self.assertEqual(0.8, warning["evidence"]["warningConfidenceThreshold"])

    def test_confident_wrong_role_blocks(self):
        answers = self.jev_answers()
        answers["role_match_FUNCTION_LIST"] = {"choice": "mismatch", "confidence": 0.9}

        with patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}), \
             patch("preflight._evaluate_with_jev", return_value=answers):
            result = evaluate_preflight(self.documents)

        self.assertEqual("BLOCKED", result["decision"])
        self.assertIn("DOCUMENT_ROLE_MISMATCH", self.warning_codes(result))

    def test_jev_failure_fails_open_with_warning(self):
        with patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}), \
             patch("preflight._evaluate_with_jev", side_effect=RuntimeError("offline")):
            result = evaluate_preflight(self.documents)

        self.assertEqual("READY_WITH_WARNINGS", result["decision"])
        self.assertIn("JEV_UNAVAILABLE", self.warning_codes(result))

    def test_long_document_truncation_is_reported(self):
        documents = [self.document("AGREEMENT", "TTA-26-00872 " + "agreement details " * 900), *self.documents[1:]]
        answers = self.jev_answers()

        with patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}), \
             patch("preflight._evaluate_with_jev", return_value=answers):
            result = evaluate_preflight(documents)

        self.assertIn("JEV_INPUT_TRUNCATED", self.warning_codes(result))
        warning = next(w for w in result["warnings"] if w["code"] == "JEV_INPUT_TRUNCATED")
        self.assertGreater(warning["evidence"]["jevOmittedCharacterCount"], 0)
        self.assertEqual(12_000, warning["evidence"]["jevSelectedCharacterCount"])
        agreement_diagnostics = next(
            item for item in result["diagnostics"]["documents"] if item["role"] == "AGREEMENT")
        self.assertTrue(agreement_diagnostics["jevInputTruncated"])

    def test_evaluate_with_jev_matches_real_sdk_contract(self):
        import json

        import httpx2
        import typesafe_sdk
        from preflight import _evaluate_with_jev

        captured = {}

        def handler(request):
            captured["body"] = json.loads(request.content)
            answers = {
                name: {
                    "type": "choice",
                    "choice": next(iter(question["criteria"])),
                    "confidence": 0.9,
                    "probabilities": {label: 0.1 for label in question["criteria"]},
                }
                for name, question in captured["body"]["questions"].items()
            }
            return httpx2.Response(200, json={
                "model": "test-model",
                "usage": {"input_tokens": 1, "output_tokens": 1},
                "answers": answers,
            })

        real_client = typesafe_sdk.TypeSafeClient

        def client_with_mock_transport(**kwargs):
            return real_client(api_key="test-key", transport=httpx2.MockTransport(handler), **kwargs)

        state = {"documents": [{"role": "AGREEMENT", "text": "sample"}]}
        with patch("typesafe_sdk.TypeSafeClient", client_with_mock_transport):
            result = _evaluate_with_jev(state)

        self.assertEqual(state, captured["body"]["state"])
        self.assertEqual(9, len(captured["body"]["questions"]))
        self.assertTrue(all(q["type"] == "choice" for q in captured["body"]["questions"].values()))
        self.assertEqual(set(captured["body"]["questions"]), set(result))
        self.assertEqual({"choice": "readable", "confidence": 0.9}, result["readability_AGREEMENT"])
        self.assertIsInstance(result["role_match_MANUAL"]["confidence"], float)

    def test_jev_timing_layers_do_not_overlap(self):
        import preflight
        import preflight_store

        tms_read_timeout = 20.0
        tms_delivery_lease = 45.0
        self.assertLess(preflight.JEV_DEADLINE_SECONDS, preflight_store.WAIT_SECONDS)
        self.assertLess(preflight_store.WAIT_SECONDS, tms_read_timeout)
        self.assertLess(tms_read_timeout, preflight_store.LEASE_SECONDS)
        self.assertLess(preflight_store.LEASE_SECONDS, tms_delivery_lease)
        self.assertEqual(10.0, preflight.JEV_ATTEMPT_MAX_SECONDS)
        self.assertEqual(18.0, preflight.JEV_SDK_MAX_LIFETIME_SECONDS)
        self.assertLess(preflight.JEV_BACKOFF_MAX_SECONDS, preflight.JEV_RETRY_BUDGET_SECONDS)
        # An abandoned SDK call can outlive the deadline but never the DB lease.
        self.assertLess(preflight.JEV_SDK_MAX_LIFETIME_SECONDS, preflight_store.LEASE_SECONDS)
        self.assertLessEqual(preflight.JEV_MAX_RETRIES, 1)

    def test_sdk_call_abandoned_at_deadline_still_ends_within_its_bound(self):
        import http.server
        import threading
        import time

        import typesafe_sdk
        import preflight

        class Silent(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                self.rfile.read(int(self.headers.get("content-length", 0)))
                time.sleep(5)

            def log_message(self, *args):
                pass

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Silent)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        real_client = typesafe_sdk.TypeSafeClient
        phase = 0.3
        budget = 1.0
        lifetime_bound = budget + 4 * phase

        def local_client(**kwargs):
            return real_client(api_key="test-key", base_url=f"http://127.0.0.1:{server.server_address[1]}", **kwargs)

        def sdk_threads():
            return [t for t in threading.enumerate() if t.name.startswith("ThreadPoolExecutor")]

        started = time.monotonic()
        try:
            with patch("typesafe_sdk.TypeSafeClient", local_client), \
                 patch("preflight.JEV_CONNECT_TIMEOUT_SECONDS", phase), \
                 patch("preflight.JEV_READ_TIMEOUT_SECONDS", phase), \
                 patch("preflight.JEV_WRITE_TIMEOUT_SECONDS", phase), \
                 patch("preflight.JEV_POOL_TIMEOUT_SECONDS", phase), \
                 patch("preflight.JEV_RETRY_BUDGET_SECONDS", budget), \
                 patch("preflight.JEV_DEADLINE_SECONDS", 0.1):
                with self.assertRaises(TimeoutError):
                    preflight._evaluate_with_jev({"documents": []})
                self.assertEqual(1, len(sdk_threads()))
                while sdk_threads() and time.monotonic() - started < lifetime_bound + 1:
                    time.sleep(0.05)
        finally:
            server.shutdown()

        self.assertEqual([], sdk_threads())
        self.assertLessEqual(time.monotonic() - started, lifetime_bound + 0.5)

    def test_client_receives_explicit_timeout_and_retry_policy(self):
        import httpx2
        import typesafe_sdk
        import preflight

        captured = {}

        class FakeClient:
            def __init__(self, **kwargs):
                captured.update(kwargs)

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

            def system_one(self, state, questions):
                raise RuntimeError("stop")

        with patch("typesafe_sdk.TypeSafeClient", FakeClient), self.assertRaises(RuntimeError):
            preflight._evaluate_with_jev({"documents": []})

        self.assertEqual(httpx2.Timeout(
            connect=preflight.JEV_CONNECT_TIMEOUT_SECONDS, read=preflight.JEV_READ_TIMEOUT_SECONDS,
            write=preflight.JEV_WRITE_TIMEOUT_SECONDS, pool=preflight.JEV_POOL_TIMEOUT_SECONDS), captured["timeout"])
        self.assertIsInstance(captured["retry"], typesafe_sdk.RetryPolicy)
        self.assertEqual(preflight.JEV_MAX_RETRIES, captured["retry"].max_retries)
        self.assertEqual(preflight.JEV_RETRY_BUDGET_SECONDS, captured["retry"].timeout)
        self.assertEqual(preflight.JEV_BACKOFF_MAX_SECONDS, captured["retry"].backoff_max)

    def test_jev_call_beyond_deadline_fails_open(self):
        import threading

        import preflight

        release = threading.Event()

        class HangingClient:
            def __init__(self, **kwargs):
                pass

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

            def system_one(self, state, questions):
                release.wait(5)

        try:
            with patch("typesafe_sdk.TypeSafeClient", HangingClient), \
                 patch("preflight.JEV_DEADLINE_SECONDS", 0.1), \
                 patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}):
                result = evaluate_preflight(self.documents)
        finally:
            release.set()

        self.assertIn("JEV_UNAVAILABLE", self.warning_codes(result))
        self.assertNotEqual("BLOCKED", result["decision"])

    def test_abandoned_sdk_calls_from_many_submissions_are_capped(self):
        import threading
        import time

        import preflight

        limit = 2
        release = threading.Event()
        lock = threading.Lock()
        running = {"now": 0, "max": 0}

        class StuckClient:
            def __init__(self, **kwargs):
                pass

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

            def system_one(self, state, questions):
                with lock:
                    running["now"] += 1
                    running["max"] = max(running["max"], running["now"])
                release.wait(10)
                with lock:
                    running["now"] -= 1
                raise RuntimeError("stuck call ended")

        errors = []

        def evaluate():
            try:
                preflight._evaluate_with_jev({"documents": []})
            except Exception as error:
                errors.append(type(error).__name__)

        try:
            with patch("typesafe_sdk.TypeSafeClient", StuckClient), \
                 patch("preflight._JEV_SLOTS", threading.BoundedSemaphore(limit)), \
                 patch("preflight.JEV_DEADLINE_SECONDS", 0.1), \
                 patch("preflight.JEV_SLOT_WAIT_SECONDS", 0.05):
                threads = [threading.Thread(target=evaluate) for _ in range(8)]
                for thread in threads:
                    thread.start()
                for thread in threads:
                    thread.join(5)
                time.sleep(0.1)

                self.assertEqual(limit, running["max"])
                self.assertEqual(limit, errors.count("TimeoutError"))
                self.assertEqual(8 - limit, errors.count("JevOverloaded"))
                release.set()
                time.sleep(0.2)
                # Slots are returned once the abandoned calls end.
                with patch("preflight.JEV_DEADLINE_SECONDS", 2.0), self.assertRaises(RuntimeError):
                    preflight._evaluate_with_jev({"documents": []})
        finally:
            release.set()

    def test_overloaded_jev_fails_open_without_blocking(self):
        import threading

        import preflight

        exhausted = threading.BoundedSemaphore(1)
        exhausted.acquire()
        with patch("preflight._JEV_SLOTS", exhausted), \
             patch("preflight.JEV_SLOT_WAIT_SECONDS", 0.01), \
             patch.dict(os.environ, {"TYPESAFE_API_KEY": "test-key"}):
            result = evaluate_preflight(self.documents)

        self.assertIn("JEV_UNAVAILABLE", self.warning_codes(result))
        self.assertNotEqual("BLOCKED", result["decision"])

    def document(self, role, text):
        return {"role": role, "extractedText": text}

    def warning_codes(self, result):
        return {warning["code"] for warning in result["warnings"]}

    def jev_answers(self):
        answers = {}
        for role in ("AGREEMENT", "FUNCTION_LIST", "MANUAL"):
            answers[f"readability_{role}"] = {"choice": "readable", "confidence": 0.95}
            answers[f"role_match_{role}"] = {"choice": "match", "confidence": 0.95}
        for left, right in (
            ("AGREEMENT", "FUNCTION_LIST"),
            ("AGREEMENT", "MANUAL"),
            ("FUNCTION_LIST", "MANUAL"),
        ):
            answers[f"same_product_{left}_{right}"] = {"choice": "same", "confidence": 0.95}
        return answers


if __name__ == "__main__":
    unittest.main()
