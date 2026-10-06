"""Local inference with complete input coverage and resumable, validated checkpoints.

Checkpoints are scoped to the full input, template instructions, model and settings.
They are private intermediate artifacts, not completed documents. A cancelled call
cannot publish a checkpoint until the HTTP await and validation have finished.
"""
import asyncio
import hashlib
import json
import os
from pathlib import Path

import httpx
from pydantic import BaseModel, ConfigDict

from .llm import LlmConfigurationError


class Fact(BaseModel):
    model_config = ConfigDict(extra="forbid")
    sectionId: str
    text: str
    quote: str


class Facts(BaseModel):
    model_config = ConfigDict(extra="forbid")
    facts: list[Fact]


def split_document(text: str, max_bytes: int):
    """UTF-8 byte bound is conservative for the Qwen byte-level tokenizer.

    No strip/filter: every character is covered, including whitespace. Prefer a
    paragraph boundary but split an oversized paragraph rather than truncating it.
    """
    start = 0
    while start < len(text):
        end, size = start, 0
        while end < len(text):
            width = len(text[end].encode("utf-8"))
            if size + width > max_bytes:
                break
            size += width
            end += 1
        if end == start:
            raise ValueError("Input budget is too small")
        if end < len(text):
            boundary = text.rfind("\n", start + (end - start) // 2, end)
            if boundary >= start:
                end = boundary + 1
        yield start, end, text[start:end]
        start = end


class OllamaLlmProvider:
    mode = "REAL"
    name = "ollama"

    def __init__(self, env):
        self.model = env.get("LLM_MODEL") or "qwen3:4b"
        self.base_url = env.get("OLLAMA_BASE_URL") or "http://ollama:11434"
        self.num_ctx = int(env.get("OLLAMA_NUM_CTX") or 8192)
        self.num_predict = int(env.get("OLLAMA_NUM_PREDICT") or 1024)
        self.timeout = float(env.get("OLLAMA_TIMEOUT_SECONDS") or 270)
        self.cache_root = Path(env.get("GENERATION_CACHE_DIR") or "/generation-cache")
        # Budget includes instructions, schema, payload and output, not just source text.
        self.input_budget = self.num_ctx - self.num_predict - 512
        if self.input_budget < 2048:
            raise LlmConfigurationError("Ollama context has insufficient input budget")

    async def _call(self, instructions, payload, schema):
        messages = [
            {"role": "system", "content": instructions},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
        ]
        estimated = len(json.dumps(messages, ensure_ascii=False).encode()) + len(json.dumps(schema).encode())
        if estimated > self.input_budget:
            raise ValueError("LLM_INPUT_BUDGET_EXCEEDED: input was not truncated")
        async with httpx.AsyncClient(timeout=httpx.Timeout(self.timeout, connect=10)) as client:
            response = await client.post(self.base_url.rstrip("/") + "/api/chat", json={
                "model": self.model, "messages": messages, "format": schema,
                "stream": False, "think": False,
                "options": {"num_ctx": self.num_ctx, "num_predict": self.num_predict, "temperature": 0},
            })
            response.raise_for_status()
            result = response.json()
        if not result.get("done") or result.get("done_reason") == "length":
            raise ValueError("LLM_OUTPUT_INCOMPLETE")
        return json.loads(result["message"]["content"])

    @staticmethod
    def _save(path, value):
        temporary = path.with_suffix(".tmp")
        temporary.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
        os.replace(temporary, path)

    async def generate(self, request):
        if not request.pipeline:
            raise LlmConfigurationError("Template pipeline instructions are required")
        # A single local model and cache writer at a time, including across HTTP requests.
        async with _LOCK:
            return await self._generate(request)

    async def _generate(self, request):
        identity = {"input": request.input_payload(), "instructions": request.instructions,
                    "pipeline": request.pipeline, "model": self.model, "ctx": self.num_ctx,
                    "predict": self.num_predict, "version": 1}
        key = hashlib.sha256(json.dumps(identity, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        directory = self.cache_root / key
        directory.mkdir(parents=True, exist_ok=True)
        facts = []
        coverage = []
        ids = {s["id"] for s in request.sections}
        specs = [{"id": s["id"], "guideline": s["guideline"]} for s in request.sections]
        # Leave room for source identifiers and section extraction instructions.
        overhead = len(json.dumps(specs, ensure_ascii=False).encode()) + len(json.dumps(Facts.model_json_schema()).encode())
        overhead += len(request.pipeline["extract"].encode()) + 1000
        chunk_budget = self.input_budget - overhead
        if chunk_budget < 256:
            raise LlmConfigurationError("Template leaves no source text budget")
        for document in request.documents:
            for number, (start, end, text) in enumerate(split_document(document["text"], chunk_budget)):
                chunk_id = f"{document['fileId']}:{number}"
                path = directory / (hashlib.sha256(chunk_id.encode()).hexdigest() + ".json")
                if path.exists():
                    parsed = Facts.model_validate_json(path.read_text(encoding="utf-8"))
                else:
                    raw = await self._call(request.pipeline["extract"], {
                        "sections": specs, "role": document["role"], "text": text,
                    }, Facts.model_json_schema())
                    parsed = Facts.model_validate(raw)
                for fact in parsed.facts:
                    if fact.sectionId not in ids or not fact.quote.strip() or fact.quote not in text or not fact.text.strip():
                        raise ValueError("LLM_EVIDENCE_INVALID")
                self._save(path, parsed.model_dump())
                for fact in parsed.facts:
                    facts.append({**fact.model_dump(), "fileId": document["fileId"], "chunkId": chunk_id})
                coverage.append({"fileId": document["fileId"], "chunkId": chunk_id, "start": start, "end": end})
                self._save(directory / "coverage.json", coverage)
                await asyncio.sleep(0)

        # Consolidation is lossless: deduplicate identical facts, retain conflicting values.
        unique = {json.dumps(f, sort_keys=True, ensure_ascii=False): f for f in facts}
        sections = []
        for spec in request.sections:
            evidence = [f for f in unique.values() if f["sectionId"] == spec["id"]]
            blocks = []
            for block in spec["blocks"]:
                # Write bounded groups and concatenate; never drop tail evidence to fit.
                batches, batch = [], []
                for fact in evidence:
                    candidate = batch + [fact]
                    if len(json.dumps(candidate, ensure_ascii=False).encode()) > max(256, self.input_budget - 3000):
                        if not batch:
                            raise ValueError("Single evidence item exceeds input budget")
                        batches.append(batch)
                        batch = [fact]
                    else:
                        batch = candidate
                batches.append(batch)
                generated = []
                for batch in batches:
                    if not batch:
                        value = request.unknown_text
                        result = ({"type": "paragraph", "text": value} if block["type"] == "paragraph" else
                                  {"type": "bullets", "items": [value]} if block["type"] == "bullets" else
                                  {"type": "table", "columns": block["columns"], "rows": [[value] * len(block["columns"])]})
                    else:
                        schema = block_schema(block)
                        payload = {"section": spec["title"], "guideline": spec["guideline"], "evidence": batch,
                                   "warnings": request.warnings, "unknownText": request.unknown_text}
                        cache_key = hashlib.sha256(json.dumps([payload, schema], sort_keys=True, ensure_ascii=False).encode()).hexdigest()
                        path = directory / ("section-" + cache_key + ".json")
                        if path.exists():
                            result = json.loads(path.read_text(encoding="utf-8"))
                        else:
                            result = await self._call(request.pipeline["write"], payload, schema)
                            from .document_model import ParagraphBlock, BulletsBlock, TableBlock
                            cls = {"paragraph": ParagraphBlock, "bullets": BulletsBlock, "table": TableBlock}[block["type"]]
                            cls.model_validate(result)
                            self._save(path, result)
                    generated.append(result)
                merged = generated[0]
                for extra in generated[1:]:
                    field = {"paragraph": "text", "bullets": "items", "table": "rows"}[block["type"]]
                    merged[field] = (merged[field] + "\n\n" + extra[field]) if field == "text" else merged[field] + extra[field]
                blocks.append(merged)
            sections.append({"id": spec["id"], "blocks": blocks,
                             "sources": [{"fileId": file_id} for file_id in sorted({f["fileId"] for f in evidence})]})
        self._save(directory / "evidence.json", list(unique.values()))
        return {"sections": sections}


def block_schema(block):
    kind = block["type"]
    properties = {"type": {"type": "string", "enum": [kind]}}
    if kind == "paragraph":
        properties["text"] = {"type": "string"}
    elif kind == "bullets":
        properties["items"] = {"type": "array", "items": {"type": "string"}}
    else:
        properties["columns"] = {"type": "array", "items": {"type": "string"}, "const": block["columns"]}
        properties["rows"] = {"type": "array", "items": {"type": "array", "items": {"type": "string"},
                              "minItems": len(block["columns"]), "maxItems": len(block["columns"])}}
    return {"type": "object", "properties": properties, "required": list(properties), "additionalProperties": False}


_LOCK = asyncio.Lock()
