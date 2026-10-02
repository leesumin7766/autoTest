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

app = FastAPI()
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
                received_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """)


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
        with database_connection() as connection, connection.cursor() as cursor:
            cursor.execute("""
                INSERT INTO ai_document_intakes (submission_id, product_id, payload_hash, documents)
                VALUES (%s, %s, %s, %s::jsonb)
                ON CONFLICT (submission_id) DO NOTHING
                RETURNING received_at
                """, (str(intake.submissionId), intake.productId, payload_hash, documents_json))
            inserted = cursor.fetchone()
            duplicate = inserted is None
            if duplicate:
                cursor.execute("""
                    SELECT payload_hash, received_at FROM ai_document_intakes WHERE submission_id = %s
                    """, (str(intake.submissionId),))
                stored_hash, received_at = cursor.fetchone()
                if stored_hash.strip() != payload_hash:
                    raise HTTPException(status_code=409, detail="Submission ID was already received with different content")
            else:
                received_at = inserted[0]
    except HTTPException:
        raise
    except psycopg2.Error as error:
        raise HTTPException(status_code=503, detail="Document intake store is unavailable") from error

    if received_at.tzinfo is None:
        received_at = received_at.replace(tzinfo=timezone.utc)
    return {
        "accepted": True,
        "duplicate": duplicate,
        "submissionId": str(intake.submissionId),
        "documentCount": len(intake.documents),
        "receivedAt": received_at.isoformat(),
    }
