import hashlib
import json
import logging
import os
from datetime import datetime, timezone
from typing import Literal
from uuid import UUID

import psycopg2
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, model_validator

from preflight import evaluate_preflight
import preflight_store
from product_description.api import router as product_description_router
from tc_generation import router as tc_router

app = FastAPI()
app.include_router(product_description_router)
app.include_router(tc_router)
logger = logging.getLogger(__name__)


@app.exception_handler(RequestValidationError)
async def log_validation_failure(request: Request, error: RequestValidationError):
    safe_errors = [{"loc": issue["loc"], "type": issue["type"]} for issue in error.errors()]
    if request.url.path == "/api/v1/document-intakes":
        logger.warning("Document intake validation failed: %s", safe_errors)
    return JSONResponse(status_code=422, content={"detail": safe_errors})


class IntakeDocument(BaseModel):
    model_config = ConfigDict(extra="forbid")

    fileId: UUID
    role: Literal["AGREEMENT", "FUNCTION_LIST", "MANUAL"]
    originalFilename: str
    format: Literal["PDF", "XLS", "XLSX", "HWP", "HWPX", "DOC", "DOCX"]
    storedPath: str
    extractedText: str


class DocumentIntake(BaseModel):
    model_config = ConfigDict(extra="forbid")

    submissionId: UUID
    productId: int
    documents: list[IntakeDocument]

    @model_validator(mode="after")
    def validate_documents(self):
        required_roles = {"AGREEMENT", "FUNCTION_LIST", "MANUAL"}
        roles = [document.role for document in self.documents]
        if len(roles) != 3 or set(roles) != required_roles:
            raise ValueError("Exactly one document for each required role is required")
        if any(not document.extractedText.strip() for document in self.documents):
            raise ValueError("Extracted text must not be empty")
        return self


def database_connection():
    database_url = os.environ.get("AI_DATABASE_URL")
    if not database_url:
        raise RuntimeError("AI_DATABASE_URL is not configured")
    return psycopg2.connect(database_url, connect_timeout=5)


@app.on_event("startup")
def initialize_intake_store():
    with database_connection() as connection, connection.cursor() as cursor:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS ai_document_intakes (
                submission_id UUID PRIMARY KEY,
                product_id BIGINT NOT NULL,
                payload_hash CHAR(64) NOT NULL,
                documents JSONB NOT NULL,
                preflight JSONB,
                received_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """)
        cursor.execute("ALTER TABLE ai_document_intakes ADD COLUMN IF NOT EXISTS preflight JSONB")
        preflight_store.initialize(cursor)


@app.get("/")
def health():
    return {"status": "ai-service running", "python": "3.13"}


@app.post("/api/v1/document-intakes")
def receive_documents(intake: DocumentIntake):
    payload = intake.model_dump(mode="json")
    canonical_payload = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    payload_hash = hashlib.sha256(canonical_payload.encode("utf-8")).hexdigest()
    documents_json = json.dumps([document.model_dump(mode="json") for document in intake.documents], ensure_ascii=False)

    try:
        existing = _find_intake(intake.submissionId)
        if existing is not None:
            stored_hash, received_at, stored_preflight = existing
            if stored_hash.strip() != payload_hash:
                raise HTTPException(status_code=409, detail="Submission ID was already received with different content")
            preflight = _ensure_preflight(intake, stored_preflight, payload_hash)
            return _receipt(intake, True, received_at, preflight)

        preflight = _evaluate_once(intake, payload_hash)
        if preflight["decision"] == "BLOCKED":
            return _blocked_receipt(intake, preflight)
        with database_connection() as connection, connection.cursor() as cursor:
            cursor.execute("""
                INSERT INTO ai_document_intakes (submission_id, product_id, payload_hash, documents, preflight)
                VALUES (%s, %s, %s, %s::jsonb, %s::jsonb)
                ON CONFLICT (submission_id) DO NOTHING
                RETURNING received_at
                """, (str(intake.submissionId), intake.productId, payload_hash, documents_json,
                      json.dumps(preflight, ensure_ascii=False)))
            inserted = cursor.fetchone()
            duplicate = inserted is None
            if duplicate:
                cursor.execute("""
                    SELECT payload_hash, received_at, preflight FROM ai_document_intakes WHERE submission_id = %s
                    """, (str(intake.submissionId),))
                stored_hash, received_at, stored_preflight = cursor.fetchone()
                if stored_hash.strip() != payload_hash:
                    raise HTTPException(status_code=409, detail="Submission ID was already received with different content")
                preflight = _ensure_preflight(intake, stored_preflight, payload_hash)
            else:
                received_at = inserted[0]
    except HTTPException:
        raise
    except preflight_store.PreflightBusy as error:
        raise HTTPException(status_code=503, detail="Document preflight is already in progress") from error
    except psycopg2.Error as error:
        raise HTTPException(status_code=503, detail="Document intake store is unavailable") from error

    return _receipt(intake, duplicate, received_at, preflight)


def _evaluate_once(intake: DocumentIntake, payload_hash: str) -> dict:
    """Same submission and payload hash share one Jev evaluation across processes."""
    return preflight_store.run_once(
        database_connection, str(intake.submissionId), payload_hash,
        lambda: evaluate_preflight([document.model_dump(mode="json") for document in intake.documents]))


def _blocked_receipt(intake: DocumentIntake, preflight: dict):
    return {
        "accepted": False,
        "blocked": True,
        "duplicate": False,
        "submissionId": str(intake.submissionId),
        "documentCount": len(intake.documents),
        "receivedAt": None,
        "preflight": preflight,
    }


def _find_intake(submission_id: UUID):
    with database_connection() as connection, connection.cursor() as cursor:
        cursor.execute("""
            SELECT payload_hash, received_at, preflight
            FROM ai_document_intakes WHERE submission_id = %s
            """, (str(submission_id),))
        return cursor.fetchone()


def _ensure_preflight(intake: DocumentIntake, stored_preflight, payload_hash: str):
    if stored_preflight is not None:
        return stored_preflight if isinstance(stored_preflight, dict) else json.loads(stored_preflight)

    preflight = _evaluate_once(intake, payload_hash)
    with database_connection() as connection, connection.cursor() as cursor:
        cursor.execute("""
            UPDATE ai_document_intakes SET preflight = %s::jsonb
            WHERE submission_id = %s AND preflight IS NULL
            """, (json.dumps(preflight, ensure_ascii=False), str(intake.submissionId)))
        cursor.execute("""
            SELECT preflight FROM ai_document_intakes WHERE submission_id = %s
            """, (str(intake.submissionId),))
        saved = cursor.fetchone()[0]
    return saved if isinstance(saved, dict) else json.loads(saved)


def _receipt(intake: DocumentIntake, duplicate: bool, received_at, preflight: dict):
    if received_at.tzinfo is None:
        received_at = received_at.replace(tzinfo=timezone.utc)
    return {
        "accepted": True,
        "blocked": False,
        "duplicate": duplicate,
        "submissionId": str(intake.submissionId),
        "documentCount": len(intake.documents),
        "receivedAt": received_at.isoformat(),
        "preflight": preflight,
    }
