import os
import threading
import unittest
import uuid
from unittest.mock import patch

import psycopg2

import preflight_store

DATABASE_URL = os.environ.get("AI_TEST_DATABASE_URL")


@unittest.skipUnless(DATABASE_URL, "AI_TEST_DATABASE_URL is not set")
class PreflightStorePostgresTests(unittest.TestCase):
    def setUp(self):
        self.submission_id = str(uuid.uuid4())
        self.payload_hash = "a" * 64
        with self.connect() as connection, connection.cursor() as cursor:
            preflight_store.initialize(cursor)

    def tearDown(self):
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("DELETE FROM ai_preflight_attempts WHERE submission_id = %s", (self.submission_id,))

    def connect(self):
        return psycopg2.connect(DATABASE_URL, connect_timeout=5)

    def row(self):
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("""
                SELECT status, attempt_count, preflight FROM ai_preflight_attempts
                WHERE submission_id = %s AND payload_hash = %s
                """, (self.submission_id, self.payload_hash))
            return cursor.fetchone()

    def test_claim_lease_completion_and_reuse(self):
        first = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        second = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)

        self.assertEqual("OWNER", first.kind)
        self.assertEqual("BUSY", second.kind)

        verdict = {"decision": "BLOCKED", "warnings": [], "diagnostics": {}}
        preflight_store.complete(self.connect, self.submission_id, self.payload_hash, first.token, verdict)
        reused = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)

        self.assertEqual("COMPLETED", reused.kind)
        self.assertEqual(verdict, reused.preflight)

    def test_expired_lease_is_reclaimed_and_stale_owner_cannot_complete(self):
        stale = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("UPDATE ai_preflight_attempts SET lease_expires_at = now() - interval '1 second' "
                           "WHERE submission_id = %s", (self.submission_id,))

        fresh = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        preflight_store.complete(self.connect, self.submission_id, self.payload_hash, stale.token,
                                 {"decision": "READY"})

        self.assertEqual("OWNER", fresh.kind)
        self.assertEqual(("IN_PROGRESS", 2, None), self.row())

    def test_expired_but_unclaimed_lease_can_still_complete(self):
        owner = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        self.expire_lease()
        verdict = {"decision": "READY", "warnings": [], "diagnostics": {}}

        self.assertTrue(preflight_store.complete(
            self.connect, self.submission_id, self.payload_hash, owner.token, verdict))
        self.assertEqual("COMPLETED", self.row()[0])

    def test_complete_reports_lost_ownership_and_keeps_current_owners_verdict(self):
        stale = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        self.expire_lease()
        current = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        blocked = {"decision": "BLOCKED", "warnings": [], "diagnostics": {}}

        self.assertFalse(preflight_store.complete(
            self.connect, self.submission_id, self.payload_hash, stale.token, {"decision": "READY"}))
        self.assertTrue(preflight_store.complete(
            self.connect, self.submission_id, self.payload_hash, current.token, blocked))
        self.assertFalse(preflight_store.complete(
            self.connect, self.submission_id, self.payload_hash, current.token, {"decision": "READY"}))
        self.assertEqual(blocked, self.row()[2])

    def expire_lease(self):
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("UPDATE ai_preflight_attempts SET lease_expires_at = now() - interval '1 second' "
                           "WHERE submission_id = %s", (self.submission_id,))

    def test_stale_ready_owner_loses_to_blocked_owner_and_no_body_is_stored(self):
        import main

        intake = main.DocumentIntake(
            submissionId=self.submission_id, productId=1,
            documents=[{
                "fileId": str(uuid.uuid4()), "role": role, "originalFilename": f"{role}.docx", "format": "DOCX",
                "storedPath": f"s3://autotest-docs/{role}.docx", "extractedText": "confidential body text",
            } for role in ("AGREEMENT", "FUNCTION_LIST", "MANUAL")])
        payload_hash = self.payload_hash_of(intake)
        blocked = {"decision": "BLOCKED", "warnings": [
            {"code": "PRODUCT_MISMATCH", "role": None, "severity": "BLOCKER", "message": "m"}], "diagnostics": {}}
        ready = {"decision": "READY", "warnings": [], "diagnostics": {}}

        def stale_evaluation(documents):
            self.expire_lease_for(payload_hash)
            new_owner = preflight_store.claim(self.connect, self.submission_id, payload_hash)
            self.assertEqual("OWNER", new_owner.kind)
            self.assertTrue(preflight_store.complete(
                self.connect, self.submission_id, payload_hash, new_owner.token, blocked))
            return ready

        try:
            with patch.dict(os.environ, {"AI_DATABASE_URL": DATABASE_URL}):
                main.initialize_intake_store()
                with patch("main.evaluate_preflight", side_effect=stale_evaluation):
                    receipt = main.receive_documents(intake)

            self.assertTrue(receipt["blocked"])
            self.assertFalse(receipt["accepted"])
            self.assertEqual("BLOCKED", receipt["preflight"]["decision"])
            with self.connect() as connection, connection.cursor() as cursor:
                cursor.execute("SELECT count(*) FROM ai_document_intakes WHERE submission_id = %s",
                               (self.submission_id,))
                self.assertEqual(0, cursor.fetchone()[0])
                cursor.execute("SELECT preflight::text FROM ai_preflight_attempts WHERE submission_id = %s",
                               (self.submission_id,))
                stored = cursor.fetchall()
            self.assertEqual(1, len(stored))
            self.assertNotIn("confidential body text", stored[0][0])
        finally:
            with self.connect() as connection, connection.cursor() as cursor:
                cursor.execute("DELETE FROM ai_document_intakes WHERE submission_id = %s", (self.submission_id,))

    def expire_lease_for(self, payload_hash):
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("UPDATE ai_preflight_attempts SET lease_expires_at = now() - interval '1 second' "
                           "WHERE submission_id = %s AND payload_hash = %s", (self.submission_id, payload_hash))

    def payload_hash_of(self, intake):
        import hashlib
        import json

        canonical = json.dumps(intake.model_dump(mode="json"), ensure_ascii=False, sort_keys=True,
                               separators=(",", ":"))
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    def test_release_allows_immediate_retry(self):
        owner = preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        preflight_store.release(self.connect, self.submission_id, self.payload_hash, owner.token)

        self.assertEqual("FAILED", self.row()[0])
        self.assertEqual("OWNER", preflight_store.claim(self.connect, self.submission_id, self.payload_hash).kind)

    def test_different_hash_is_independent(self):
        preflight_store.claim(self.connect, self.submission_id, self.payload_hash)

        other = preflight_store.claim(self.connect, self.submission_id, "b" * 64)

        self.assertEqual("OWNER", other.kind)

    def test_concurrent_connections_evaluate_exactly_once(self):
        evaluations = []
        results = []
        barrier = threading.Barrier(8)

        def evaluate():
            evaluations.append(1)
            threading.Event().wait(0.3)
            return {"decision": "BLOCKED", "warnings": [], "diagnostics": {}}

        def worker():
            barrier.wait()
            results.append(preflight_store.run_once(
                self.connect, self.submission_id, self.payload_hash, evaluate))

        original = preflight_store.POLL_SECONDS
        preflight_store.POLL_SECONDS = 0.05
        try:
            threads = [threading.Thread(target=worker) for _ in range(8)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join(20)
        finally:
            preflight_store.POLL_SECONDS = original

        self.assertEqual(1, len(evaluations))
        self.assertEqual(8, len(results))
        self.assertTrue(all(result["decision"] == "BLOCKED" for result in results))

    def test_prunes_only_old_rows(self):
        preflight_store.claim(self.connect, self.submission_id, self.payload_hash)
        with self.connect() as connection, connection.cursor() as cursor:
            preflight_store.initialize(cursor)
        self.assertIsNotNone(self.row())

        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("UPDATE ai_preflight_attempts SET updated_at = now() - interval '8 days' "
                           "WHERE submission_id = %s", (self.submission_id,))
            preflight_store.initialize(cursor)
        self.assertIsNone(self.row())


if __name__ == "__main__":
    unittest.main()
