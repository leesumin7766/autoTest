"""LLM provider boundary. Swap providers by implementing LlmProvider and registering it in provider_from_env."""
import asyncio
import os
from dataclasses import dataclass
from typing import Literal, Protocol


class LlmConfigurationError(Exception):
    code = "LLM_NOT_CONFIGURED"


class LlmProviderNotImplemented(Exception):
    code = "LLM_PROVIDER_NOT_IMPLEMENTED"


@dataclass(frozen=True)
class LlmRequest:
    instructions: str
    sections: list[dict]  # llm-generated section specs: id, title, guideline, blocks
    documents: list[dict]  # fileId, role, originalFilename, text (analysis data only)
    warnings: list[dict]
    unknown_text: str
    pipeline: dict | None = None

    def input_payload(self) -> dict:
        return {"sections": self.sections, "documents": self.documents, "preflightWarnings": self.warnings}


class LlmProvider(Protocol):
    mode: Literal["MOCK", "REAL"]
    name: str
    model: str

    async def generate(self, request: LlmRequest) -> dict:
        """Return {"sections": [{"id", "blocks", "sources"}]}; raising or being cancelled must have no side effects."""
        ...


class MockLlmProvider:
    mode = "MOCK"
    name = "mock"
    model = "mock-1"

    def __init__(self, delay_seconds: float = 0.0):
        self.delay_seconds = max(0.0, delay_seconds)

    async def generate(self, request: LlmRequest) -> dict:
        if self.delay_seconds:
            await asyncio.sleep(self.delay_seconds)
        sources = [{"fileId": document["fileId"]} for document in request.documents]
        roles = ", ".join(document["role"] for document in request.documents)
        sections = []
        for spec in request.sections:
            blocks = []
            for block in spec["blocks"]:
                if block["type"] == "paragraph":
                    blocks.append({"type": "paragraph", "text": (
                        f"[{spec['title']}] 개발용 모의 생성 문단입니다. 입력 문서({roles})를 분석한 실제 결과가 아니며, "
                        f"{request.unknown_text}.")})
                elif block["type"] == "bullets":
                    blocks.append({"type": "bullets", "items": [
                        f"모의 항목 1: {request.unknown_text}", f"모의 항목 2: {request.unknown_text}"]})
                else:
                    columns = block["columns"]
                    rows = [[f"모의 항목 {number}"] + [request.unknown_text] * (len(columns) - 1) for number in (1, 2)]
                    blocks.append({"type": "table", "columns": columns, "rows": rows})
            sections.append({"id": spec["id"], "blocks": blocks, "sources": sources})
        return {"sections": sections}


class ExternalLlmProvider:
    """Connection point for the real vendor; provider and model are chosen after development."""
    mode = "REAL"
    name = "external"

    def __init__(self, api_key: str, model: str):
        self._api_key = api_key
        self.model = model or "unspecified"

    async def generate(self, request: LlmRequest) -> dict:
        # Implement the vendor call here using request.instructions and request.input_payload().
        # Use an async HTTP client so task cancellation aborts the outbound request.
        raise LlmProviderNotImplemented("실제 LLM 제공업체 연동이 아직 구현되지 않았습니다.")


def provider_from_env(env=None) -> LlmProvider:
    env = os.environ if env is None else env
    mode = (env.get("LLM_MODE") or "").strip().lower()
    if mode == "mock":
        try:
            delay = float(env.get("MOCK_LLM_DELAY_SECONDS") or 0)
        except ValueError as error:
            raise LlmConfigurationError("MOCK_LLM_DELAY_SECONDS must be a number") from error
        return MockLlmProvider(delay)
    if mode == "real":
        if env.get("LLM_PROVIDER", "external").lower() == "ollama":
            from .ollama_provider import OllamaLlmProvider
            return OllamaLlmProvider(env)
        api_key = (env.get("EX_API") or "").strip()
        if not api_key or api_key.lower() == "inputlater":
            raise LlmConfigurationError("LLM_MODE=real 이지만 EX_API 키가 설정되지 않았습니다.")
        return ExternalLlmProvider(api_key, (env.get("LLM_MODEL") or "").strip())
    raise LlmConfigurationError("LLM_MODE 는 mock 또는 real 이어야 합니다.")
