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

    def __init__(self, env, quality=None):
        quality = quality or {}
        self.model = env.get("LLM_MODEL") or "qwen3:4b"
        self.base_url = env.get("OLLAMA_BASE_URL") or "http://ollama:11434"
        self.num_ctx = int(env.get("OLLAMA_NUM_CTX") or 8192)
        self.num_predict = int(env.get("OLLAMA_NUM_PREDICT") or 2048)
        self.timeout = float(env.get("OLLAMA_TIMEOUT_SECONDS") or 1200)
        self.cache_root = Path(env.get("GENERATION_CACHE_DIR") or "/generation-cache")
        self.quality = quality
        self.configure_quality(quality)
        # Budget includes instructions, schema, payload and output, not just source text.
        self.input_budget = self.num_ctx - self.num_predict - 512
        if self.input_budget < 2048:
            raise LlmConfigurationError("Ollama context has insufficient input budget")

    def configure_quality(self, quality):
        self.quality = quality
        evidence_settings = quality.get("evidence", {})
        self.require_exact_quote = evidence_settings.get("requireExactQuote", True)
        self.minimum_quote_characters = evidence_settings.get("minimumQuoteCharacters", 3)
        self.chunk_overlap_characters = quality.get("chunking", {}).get("overlapCharacters", 0)
        self.max_chunk_bytes = quality.get("chunking", {}).get("maxChunkBytes", 10000)

    def _identity(self, request):
        return {"input": request.input_payload(), "instructions": request.instructions,
                "pipeline": request.pipeline, "quality": self.quality, "model": self.model,
                "ctx": self.num_ctx, "predict": self.num_predict, "version": 1}

    def _progress(self, directory):
        """Private polling view; contains identifiers and counts only, never source text."""
        total = completed = 0
        if not directory.exists():
            return {"completedChunks": 0, "totalChunks": 0}
        coverage_path = directory / "coverage.json"
        try:
            coverage = json.loads(coverage_path.read_text(encoding="utf-8"))
            completed = len(coverage)
        except (OSError, json.JSONDecodeError):
            pass
        pipeline_path = directory / "plan.json"
        try:
            total = json.loads(pipeline_path.read_text(encoding="utf-8"))["totalChunks"]
        except (OSError, json.JSONDecodeError, KeyError, TypeError):
            pass
        return {"completedChunks": completed, "totalChunks": total}

    async def progress(self, request):
        """Look up checkpoint progress using exactly the same identity as generation."""
        if not request.pipeline:
            return {"completedChunks": 0, "totalChunks": 0}
        key = hashlib.sha256(json.dumps(self._identity(request), sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        return self._progress(self.cache_root / key)

    async def _call(self, instructions, payload, schema, num_predict=None):
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
                "options": {"num_ctx": self.num_ctx,
                            "num_predict": num_predict or self.quality.get("output", {}).get("extractionTokens", self.num_predict),
                            "temperature": 0},
            })
            if response.status_code >= 500:
                raise ValueError("LLM_PROVIDER_UNAVAILABLE")
            response.raise_for_status()
            result = response.json()
        if not result.get("done") or result.get("done_reason") == "length":
            raise ValueError("LLM_OUTPUT_INCOMPLETE")
        try:
            return json.loads(result["message"]["content"])
        except (KeyError, TypeError, json.JSONDecodeError) as error:
            raise ValueError("LLM_RESPONSE_INVALID_JSON") from error

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
        key = hashlib.sha256(json.dumps(self._identity(request), sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        directory = self.cache_root / key
        directory.mkdir(parents=True, exist_ok=True)
        chunk_plan = []
        chunks_by_file = {}
        overhead = len(json.dumps([{"id": s["id"], "guideline": s["guideline"]} for s in request.sections], ensure_ascii=False).encode())
        overhead += len(json.dumps(Facts.model_json_schema()).encode()) + len(request.pipeline["extract"].encode()) + 1000
        chunk_budget = min(self.input_budget - overhead, self.max_chunk_bytes)
        if chunk_budget < 256:
            raise LlmConfigurationError("Template leaves no source text budget")
        for document in request.documents:
            chunks = list(split_document(document["text"], chunk_budget))
            chunks_by_file[document["fileId"]] = chunks
            for number, (start, end, text) in enumerate(chunks):
                if self.chunk_overlap_characters and number:
                    overlap_start = max(chunks[number - 1][0], start - self.chunk_overlap_characters)
                    text = document["text"][overlap_start:end]
                chunk_plan.append((document, number, start, end, text))
        self._save(directory / "plan.json", {"totalChunks": len(chunk_plan)})
        facts = []
        try:
            coverage = json.loads((directory / "coverage.json").read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            coverage = []
        covered = {item.get("chunkId") for item in coverage}
        ids = {s["id"] for s in request.sections}
        specs = [{"id": s["id"], "guideline": s["guideline"]} for s in request.sections]
        # Leave room for source identifiers and section extraction instructions.
        for document, number, start, end, text in chunk_plan:
            if self.chunk_overlap_characters and number:
                chunks = chunks_by_file[document["fileId"]]
                overlap_start = max(chunks[number - 1][0], start - self.chunk_overlap_characters)
                text = document["text"][overlap_start:end]
            chunk_id = f"{document['fileId']}:{number}"
            path = directory / (hashlib.sha256(chunk_id.encode()).hexdigest() + ".json")
            if path.exists():
                parsed = Facts.model_validate_json(path.read_text(encoding="utf-8"))
            else:
                raw = await self._call(request.pipeline["extract"], {
                    "sections": specs, "role": document["role"], "text": text,
                }, Facts.model_json_schema())
                try:
                    parsed = Facts.model_validate(raw)
                except Exception as error:
                    raise ValueError("LLM_FACTS_SCHEMA_INVALID") from error
            valid_facts = []
            for fact in parsed.facts:
                if fact.sectionId not in ids or not fact.quote.strip() or not fact.text.strip():
                    continue
                if self.require_exact_quote and fact.quote not in text:
                    continue
                if len(fact.quote.strip()) < self.minimum_quote_characters:
                    continue
                valid_facts.append(fact)
            parsed = Facts(facts=valid_facts)
            self._save(path, parsed.model_dump())
            for fact in parsed.facts:
                facts.append({**fact.model_dump(), "fileId": document["fileId"], "chunkId": chunk_id})
            if chunk_id not in covered:
                coverage.append({"fileId": document["fileId"], "chunkId": chunk_id, "start": start, "end": end})
                covered.add(chunk_id)
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
                    if batch and len(json.dumps(candidate, ensure_ascii=False).encode()) > max(256, self.input_budget - 4500):
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
                            from .document_model import ParagraphBlock, BulletsBlock, TableBlock
                            cls = {"paragraph": ParagraphBlock, "bullets": BulletsBlock, "table": TableBlock}[block["type"]]
                            cls.model_validate(result)
                        else:
                            instruction = request.pipeline["write"]
                            attempts = self.quality.get("output", {}).get("validationRetries", 1)
                            generation_tokens = self.quality.get("output", {}).get("generationTokens", self.num_predict)
                            for attempt in range(attempts + 1):
                                try:
                                    result = await self._call(instruction, payload, schema, generation_tokens)
                                    from .document_model import ParagraphBlock, BulletsBlock, TableBlock
                                    cls = {"paragraph": ParagraphBlock, "bullets": BulletsBlock, "table": TableBlock}[block["type"]]
                                    cls.model_validate(result)
                                    break
                                except Exception as invalid:
                                    if attempt >= attempts:
                                        raise
                                    instruction += "\n이전 응답이 JSON 또는 요청 구조 검증에 실패했다. 출력 스키마에 맞는 JSON 객체 하나만 반환하라."
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
