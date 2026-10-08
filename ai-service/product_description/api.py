"""HTTP contract for product description generation. This service keeps no document text or job state."""
import asyncio
import logging
import os
import secrets
from typing import Literal

from fastapi import APIRouter, Request, Depends, HTTPException
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, ConfigDict, field_validator

from .builder import InvalidLlmOutput, assemble_document, build_llm_request, now_utc
from .llm import LlmConfigurationError, LlmProviderNotImplemented, provider_from_env
from .rendering import RenderError, get_renderer
from .template_store import DEFAULT_TEMPLATE_ID, TemplateError, load_template

def require_internal_token(request: Request):
    expected = os.environ.get("PRODUCT_DESCRIPTION_INTERNAL_TOKEN", "")
    if not expected:
        raise HTTPException(503, "Internal authentication is not configured")
    supplied = request.headers.get("X-Internal-Token", "")
    if not secrets.compare_digest(supplied.encode(), expected.encode()):
        raise HTTPException(403, "Internal authentication required")


router = APIRouter(prefix="/api/v1/product-descriptions", dependencies=[Depends(require_internal_token)])
logger = logging.getLogger(__name__)


class SourceDocument(BaseModel):
    model_config = ConfigDict(extra="forbid")

    fileId: str
    role: Literal["AGREEMENT", "FUNCTION_LIST", "MANUAL"]
    originalFilename: str
    format: str
    extractedText: str


class PreflightWarning(BaseModel):
    model_config = ConfigDict(extra="ignore")

    code: str = ""
    role: str | None = None
    severity: str = "WARNING"
    message: str = ""


class ContentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    submissionId: str
    productId: int
    templateId: str = DEFAULT_TEMPLATE_ID
    decision: Literal["READY", "READY_WITH_WARNINGS"]
    warnings: list[PreflightWarning] = []
    documents: list[SourceDocument]

    @field_validator("documents")
    @classmethod
    def three_roles(cls, documents):
        if sorted(d.role for d in documents) != ["AGREEMENT", "FUNCTION_LIST", "MANUAL"]:
            raise ValueError("Exactly one document for each required role is required")
        if any(not d.extractedText.strip() for d in documents):
            raise ValueError("Extracted text must not be empty")
        return documents


class RenderRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    format: str = "PDF"
    document: dict
    presentation: dict


def _error(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"detail": {"code": code, "message": message}})


@router.post("/content")
async def generate_content(body: ContentRequest, request: Request):
    if os.environ.get("PRODUCT_DESCRIPTION_GENERATION_ENABLED", "false").lower() != "true":
        return _error(503, "PRODUCT_DESCRIPTION_DISABLED", "제품 설명 생성은 품질 개선 전까지 비활성화되어 있습니다.")
    try:
        provider = provider_from_env()
        snapshot = load_template(body.templateId)
    except LlmConfigurationError as error:
        return _error(503, error.code, str(error))
    except TemplateError as error:
        return _error(500, "TEMPLATE_INVALID", str(error))

    documents = [d.model_dump() for d in body.documents]
    warnings = [w.model_dump() for w in body.warnings]
    if hasattr(provider, "configure_quality"):
        provider.configure_quality(snapshot["content"].get("quality", {}))
    llm_request = build_llm_request(snapshot, documents, warnings)
    task = asyncio.create_task(provider.generate(llm_request))
    try:
        while not task.done():
            await asyncio.wait({task}, timeout=0.5)
            if not task.done() and await request.is_disconnected():
                task.cancel()
                return Response(status_code=499)
        llm_raw = task.result()
    except asyncio.CancelledError:
        task.cancel()
        raise
    except LlmProviderNotImplemented as error:
        return _error(503, error.code, str(error))
    except Exception as error:
        code = str(error) if str(error).startswith("LLM_") else "LLM_CALL_FAILED"
        logger.warning("LLM call failed (%s)", code)
        return _error(502, code, "LLM 호출 또는 응답 검증에 실패했습니다.")

    context = {"documents": documents, "warnings": warnings, "decision": body.decision,
               "productId": body.productId, "submissionId": body.submissionId, "generatedAt": now_utc()}
    try:
        document = assemble_document(snapshot, provider, llm_raw, context)
    except InvalidLlmOutput as error:
        return _error(502, error.code, str(error))
    return {"template": snapshot, "document": document, "generation": document["generation"]}


@router.post("/content/progress")
async def content_progress(body: ContentRequest):
    """Return checkpoint counts only; intermediate document facts remain private."""
    try:
        provider = provider_from_env()
        snapshot = load_template(body.templateId)
    except LlmConfigurationError as error:
        return _error(503, error.code, str(error))
    except TemplateError as error:
        return _error(500, "TEMPLATE_INVALID", str(error))
    documents = [d.model_dump() for d in body.documents]
    warnings = [w.model_dump() for w in body.warnings]
    if hasattr(provider, "configure_quality"):
        provider.configure_quality(snapshot["content"].get("quality", {}))
    llm_request = build_llm_request(snapshot, documents, warnings)
    if hasattr(provider, "progress"):
        return await provider.progress(llm_request)
    return {"completedChunks": 0, "totalChunks": 0}


@router.post("/render")
async def render_document(body: RenderRequest):
    if os.environ.get("PRODUCT_DESCRIPTION_GENERATION_ENABLED", "false").lower() != "true":
        return _error(503, "PRODUCT_DESCRIPTION_DISABLED", "제품 설명 생성은 품질 개선 전까지 비활성화되어 있습니다.")
    try:
        renderer = get_renderer(body.format)
        content = await asyncio.to_thread(renderer.render, body.document, body.presentation)
    except RenderError as error:
        return _error(500, error.code, str(error))
    return Response(content=content, media_type=renderer.media_type)
