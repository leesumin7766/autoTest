import unittest
import json
import threading
import time
from datetime import datetime, timezone
from unittest.mock import patch
from uuid import uuid4

from pydantic import ValidationError
from fastapi import HTTPException

import preflight_store
from main import DocumentIntake, receive_documents


class FakeIntakeDatabase:
    def __init__(self):
        self.rows = {}
        self.attempts = {}
        self.lock = threading.Lock()

    def connect(self):
        return FakeConnection(self)


class FakeConnection:
    def __init__(self, database):
        self.database = database

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception, traceback):
        return False

    def cursor(self):
        return FakeCursor(self.database)


class FakeCursor:
    def __init__(self, database):
        self.database = database
        self.row = None

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception, traceback):
        return False

    def execute(self, query, parameters):
        query = query.lstrip()
        if query.startswith("INSERT INTO ai_preflight_attempts"):
            submission_id, payload_hash, token, lease_seconds = parameters
            with self.database.lock:
                key = (submission_id, payload_hash)
                attempt = self.database.attempts.get(key)
                if attempt is None:
                    self.database.attempts[key] = {
                        "status": "IN_PROGRESS", "token": token, "lease": time.monotonic() + lease_seconds,
                        "preflight": None, "count": 1}
                    self.row = (token,)
                elif attempt["status"] != "COMPLETED" and attempt["lease"] <= time.monotonic():
                    attempt.update(status="IN_PROGRESS", token=token, lease=time.monotonic() + lease_seconds,
                                   count=attempt["count"] + 1)
                    self.row = (token,)
                else:
                    self.row = None
        elif query.startswith("SELECT status, preflight FROM ai_preflight_attempts"):
            attempt = self.database.attempts.get(parameters)
            self.row = None if attempt is None else (attempt["status"], attempt["preflight"])
        elif query.startswith("UPDATE ai_preflight_attempts SET status = 'COMPLETED'"):
            preflight, submission_id, payload_hash, token = parameters
            attempt = self.database.attempts[(submission_id, payload_hash)]
            self.rowcount = 0
            if attempt["token"] == token and attempt["status"] == "IN_PROGRESS":
                attempt.update(status="COMPLETED", preflight=json.loads(preflight), lease=time.monotonic())
                self.rowcount = 1
        elif query.startswith("UPDATE ai_preflight_attempts SET status = 'FAILED'"):
            submission_id, payload_hash, token = parameters
            attempt = self.database.attempts[(submission_id, payload_hash)]
            if attempt["token"] == token and attempt["status"] == "IN_PROGRESS":
                attempt.update(status="FAILED", lease=time.monotonic())
        elif query.startswith("SELECT payload_hash"):
            row = self.database.rows.get(parameters[0])
            self.row = None if row is None else (row[1], row[4], row[3])
        elif query.startswith("SELECT preflight"):
            self.row = (self.database.rows[parameters[0]][3],)
        elif query.startswith("UPDATE ai_document_intakes SET preflight"):
            preflight, submission_id = parameters
            row = self.database.rows[submission_id]
            if row[3] is None:
                self.database.rows[submission_id] = (*row[:3], json.loads(preflight), row[4])
        elif query.startswith("INSERT"):
            submission_id, product_id, payload_hash, documents, preflight = parameters
            if submission_id not in self.database.rows:
                received_at = datetime.now(timezone.utc)
                self.database.rows[submission_id] = (
                    product_id, payload_hash, documents, json.loads(preflight), received_at)
                self.row = (received_at,)
            else:
                self.row = None
        else:
            raise AssertionError("Unexpected intake SQL")

    def fetchone(self):
        row = self.row
        self.row = None
        return row


class DocumentIntakeTests(unittest.TestCase):
    def make_document(self, role, extracted_text="document text"):
        return {
            "fileId": str(uuid4()),
            "role": role,
            "originalFilename": f"{role.lower()}.docx",
            "format": "DOCX",
            "storedPath": f"s3://autotest-docs/{role.lower()}.docx",
            "extractedText": extracted_text,
        }

    def test_accepts_exactly_the_three_required_document_roles(self):
        intake = DocumentIntake(
            submissionId=uuid4(),
            productId=12,
            documents=[
                self.make_document("AGREEMENT"),
                self.make_document("FUNCTION_LIST"),
                self.make_document("MANUAL"),
            ],
        )

        self.assertEqual(3, len(intake.documents))

    def test_rejects_missing_or_duplicate_roles(self):
        documents = [
            self.make_document("AGREEMENT"),
            self.make_document("FUNCTION_LIST"),
            self.make_document("FUNCTION_LIST"),
        ]

        with self.assertRaises(ValidationError):
            DocumentIntake(submissionId=uuid4(), productId=12, documents=documents)

    def test_rejects_empty_extracted_text(self):
        documents = [
            self.make_document("AGREEMENT", " "),
            self.make_document("FUNCTION_LIST"),
            self.make_document("MANUAL"),
        ]

        with self.assertRaises(ValidationError):
            DocumentIntake(submissionId=uuid4(), productId=12, documents=documents)

    def create_intake(self):
        return DocumentIntake(
            submissionId=uuid4(),
            productId=12,
            documents=[
                self.make_document("AGREEMENT"),
                self.make_document("FUNCTION_LIST"),
                self.make_document("MANUAL"),
            ],
        )

    def receive(self, database, intake):
        preflight = {"decision": "READY_WITH_WARNINGS", "warnings": [], "diagnostics": {}}
        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", return_value=preflight):
            return receive_documents(intake)

    def test_receives_all_documents_once(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()

        receipt = self.receive(database, intake)

        self.assertTrue(receipt["accepted"])
        self.assertFalse(receipt["duplicate"])
        self.assertEqual(str(intake.submissionId), receipt["submissionId"])
        self.assertEqual(3, receipt["documentCount"])
        self.assertEqual("READY_WITH_WARNINGS", receipt["preflight"]["decision"])
        self.assertEqual(1, len(database.rows))

    def test_same_submission_and_payload_returns_duplicate_receipt(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()

        first_receipt = self.receive(database, intake)
        duplicate_receipt = self.receive(database, intake)

        self.assertFalse(first_receipt["duplicate"])
        self.assertTrue(duplicate_receipt["duplicate"])
        self.assertEqual(first_receipt["preflight"], duplicate_receipt["preflight"])
        self.assertEqual(1, len(database.rows))

    def test_same_submission_with_different_payload_returns_409(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        changed_intake = intake.model_copy(update={
            "documents": [
                intake.documents[0].model_copy(update={"extractedText": "changed body"}),
                intake.documents[1],
                intake.documents[2],
            ]
        })

        self.receive(database, intake)
        with self.assertRaises(HTTPException) as raised:
            self.receive(database, changed_intake)

        self.assertEqual(409, raised.exception.status_code)
        self.assertEqual(1, len(database.rows))

    def blocked_preflight(self):
        return {
            "decision": "BLOCKED",
            "warnings": [{"code": "PRODUCT_MISMATCH", "role": None, "severity": "BLOCKER", "message": "m"}],
            "diagnostics": {},
        }

    def test_blocked_result_is_not_stored_and_replacement_can_be_received(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", return_value=self.blocked_preflight()):
            blocked = receive_documents(intake)

        self.assertFalse(blocked["accepted"])
        self.assertTrue(blocked["blocked"])
        self.assertIsNone(blocked["receivedAt"])
        self.assertEqual("BLOCKED", blocked["preflight"]["decision"])
        self.assertEqual({}, database.rows)

        replaced = intake.model_copy(update={
            "documents": [
                intake.documents[0].model_copy(update={"extractedText": "corrected body"}),
                intake.documents[1],
                intake.documents[2],
            ]
        })
        receipt = self.receive(database, replaced)

        self.assertTrue(receipt["accepted"])
        self.assertFalse(receipt["blocked"])
        self.assertEqual(1, len(database.rows))

    def test_concurrent_identical_requests_call_jev_once(self):
        from concurrent.futures import ThreadPoolExecutor
        import time

        database = FakeIntakeDatabase()
        intake = self.create_intake()
        calls = []

        def slow_evaluation(documents):
            calls.append(1)
            time.sleep(0.2)
            return self.blocked_preflight()

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", side_effect=slow_evaluation), \
             patch.object(preflight_store, "POLL_SECONDS", 0.01), \
             ThreadPoolExecutor(max_workers=4) as pool:
            receipts = list(pool.map(lambda _: receive_documents(intake), range(4)))

        self.assertEqual(1, len(calls))
        self.assertTrue(all(receipt["blocked"] for receipt in receipts))
        self.assertEqual({}, database.rows)

    def test_completed_blocked_verdict_is_reused_without_new_jev_call_or_body_storage(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        unavailable = {
            "decision": "BLOCKED",
            "warnings": [{"code": "JEV_UNAVAILABLE", "role": None, "severity": "WARNING", "message": "m"}],
            "diagnostics": {},
        }

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", return_value=unavailable) as evaluate:
            first = receive_documents(intake)
            second = receive_documents(intake)

        self.assertEqual(1, evaluate.call_count)
        self.assertEqual(first, second)
        self.assertEqual({}, database.rows)
        stored = json.dumps(list(database.attempts.values()), ensure_ascii=False)
        self.assertNotIn("document text", stored)

    def test_in_progress_attempt_of_another_instance_prevents_duplicate_jev_call(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        payload_hash = self.payload_hash(intake)
        database.attempts[(str(intake.submissionId), payload_hash)] = {
            "status": "IN_PROGRESS", "token": "other", "lease": time.monotonic() + 60,
            "preflight": None, "count": 1}

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight") as evaluate, \
             patch.object(preflight_store, "WAIT_SECONDS", 0.05), \
             patch.object(preflight_store, "POLL_SECONDS", 0.01):
            with self.assertRaises(HTTPException) as raised:
                receive_documents(intake)

        self.assertEqual(503, raised.exception.status_code)
        evaluate.assert_not_called()

    def test_waiting_request_returns_verdict_completed_by_another_instance(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        key = (str(intake.submissionId), self.payload_hash(intake))
        database.attempts[key] = {
            "status": "IN_PROGRESS", "token": "other", "lease": time.monotonic() + 60,
            "preflight": None, "count": 1}
        verdict = self.blocked_preflight()

        def finish_elsewhere():
            time.sleep(0.05)
            database.attempts[key].update(status="COMPLETED", preflight=verdict)

        threading.Thread(target=finish_elsewhere).start()
        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight") as evaluate, \
             patch.object(preflight_store, "POLL_SECONDS", 0.01):
            receipt = receive_documents(intake)

        evaluate.assert_not_called()
        self.assertTrue(receipt["blocked"])
        self.assertEqual({}, database.rows)

    def test_expired_lease_is_reclaimed_and_failed_evaluation_releases_immediately(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        key = (str(intake.submissionId), self.payload_hash(intake))
        database.attempts[key] = {
            "status": "IN_PROGRESS", "token": "crashed", "lease": time.monotonic() - 1,
            "preflight": None, "count": 1}

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", side_effect=RuntimeError("boom")):
            with self.assertRaises(RuntimeError):
                receive_documents(intake)

        self.assertEqual("FAILED", database.attempts[key]["status"])
        self.assertEqual(2, database.attempts[key]["count"])
        receipt = self.receive(database, intake)
        self.assertTrue(receipt["accepted"])
        self.assertEqual(3, database.attempts[key]["count"])

    def test_changed_documents_use_a_new_hash_and_are_evaluated_again(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        changed = intake.model_copy(update={
            "documents": [
                intake.documents[0].model_copy(update={"extractedText": "corrected body"}),
                intake.documents[1],
                intake.documents[2],
            ]
        })

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", return_value=self.blocked_preflight()) as evaluate:
            receive_documents(intake)
            receive_documents(changed)

        self.assertEqual(2, evaluate.call_count)
        self.assertEqual(2, len(database.attempts))

    def test_owner_that_lost_its_lease_uses_the_stored_verdict_and_stores_no_body(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()
        submission_id = str(intake.submissionId)
        payload_hash = self.payload_hash(intake)
        key = (submission_id, payload_hash)
        blocked = self.blocked_preflight()
        ready = {"decision": "READY", "warnings": [], "diagnostics": {}}

        def slow_stale_evaluation(documents):
            database.attempts[key]["lease"] = time.monotonic() - 1
            new_owner = preflight_store.claim(database.connect, submission_id, payload_hash)
            self.assertEqual("OWNER", new_owner.kind)
            self.assertTrue(preflight_store.complete(database.connect, submission_id, payload_hash,
                                                     new_owner.token, blocked))
            return ready

        with patch("main.database_connection", side_effect=database.connect), \
             patch("main.evaluate_preflight", side_effect=slow_stale_evaluation):
            receipt = receive_documents(intake)

        self.assertTrue(receipt["blocked"])
        self.assertEqual("BLOCKED", receipt["preflight"]["decision"])
        self.assertEqual({}, database.rows)

    def payload_hash(self, intake):
        import hashlib

        canonical = json.dumps(intake.model_dump(mode="json"), ensure_ascii=False, sort_keys=True,
                               separators=(",", ":"))
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


if __name__ == "__main__":
    unittest.main()
