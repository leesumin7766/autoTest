import unittest
from datetime import datetime, timezone
from unittest.mock import patch
from uuid import uuid4

from pydantic import ValidationError
from fastapi import HTTPException

from main import DocumentIntake, receive_documents


class FakeIntakeDatabase:
    def __init__(self):
        self.rows = {}

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
        if query.lstrip().startswith("INSERT"):
            submission_id, product_id, payload_hash, documents = parameters
            if submission_id not in self.database.rows:
                received_at = datetime.now(timezone.utc)
                self.database.rows[submission_id] = (product_id, payload_hash, documents, received_at)
                self.row = (received_at,)
            else:
                self.row = None
        elif query.lstrip().startswith("SELECT payload_hash"):
            self.row = (self.database.rows[parameters[0]][1], self.database.rows[parameters[0]][3])
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
        with patch("main.database_connection", side_effect=database.connect):
            return receive_documents(intake)

    def test_receives_all_documents_once(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()

        receipt = self.receive(database, intake)

        self.assertTrue(receipt["accepted"])
        self.assertFalse(receipt["duplicate"])
        self.assertEqual(str(intake.submissionId), receipt["submissionId"])
        self.assertEqual(3, receipt["documentCount"])
        self.assertEqual(1, len(database.rows))

    def test_same_submission_and_payload_returns_duplicate_receipt(self):
        database = FakeIntakeDatabase()
        intake = self.create_intake()

        first_receipt = self.receive(database, intake)
        duplicate_receipt = self.receive(database, intake)

        self.assertFalse(first_receipt["duplicate"])
        self.assertTrue(duplicate_receipt["duplicate"])
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


if __name__ == "__main__":
    unittest.main()