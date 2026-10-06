import asyncio
import tempfile
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from fastapi import FastAPI

from product_description.api import router
from product_description.llm import LlmRequest, provider_from_env
from product_description.ollama_provider import OllamaLlmProvider, split_document


class OllamaTests(unittest.TestCase):
    def test_auth_is_fail_closed_on_content_and_render(self):
        app = FastAPI()
        app.include_router(router)
        client = TestClient(app)
        for path in ("content", "content/progress", "render"):
            with patch.dict("os.environ", {"PRODUCT_DESCRIPTION_INTERNAL_TOKEN": "secret"}):
                response = client.post("/api/v1/product-descriptions/" + path, json={})
                self.assertEqual(403, response.status_code)
                self.assertEqual(422, client.post("/api/v1/product-descriptions/" + path, json={},
                    headers={"X-Internal-Token": "secret"}).status_code)
            with patch.dict("os.environ", {"PRODUCT_DESCRIPTION_INTERNAL_TOKEN": ""}):
                self.assertEqual(503, client.post("/api/v1/product-descriptions/" + path, json={}).status_code)

    def test_local_provider_requires_no_api_key(self):
        self.assertIsInstance(provider_from_env({"LLM_MODE": "real", "LLM_PROVIDER": "ollama",
                                                "EX_API": "inputlater"}), OllamaLlmProvider)

    def test_split_covers_every_character_with_utf8_budget(self):
        text = "가나다🙂\n" * 100 + "마지막 중요 사양"
        parts = list(split_document(text, 80))
        self.assertEqual(text, "".join(p[2] for p in parts))
        self.assertTrue(all(len(p[2].encode()) <= 80 for p in parts))
        self.assertEqual(len(text), parts[-1][1])

    def test_failed_chunk_never_completes_and_retry_reuses_validated_chunks(self):
        async def run():
            with tempfile.TemporaryDirectory() as root:
                provider = OllamaLlmProvider({"GENERATION_CACHE_DIR": root})
                request = LlmRequest("instructions", [{"id": "overview", "title": "개요", "guideline": "설명",
                     "blocks": [{"type": "paragraph"}]}],
                     [{"fileId": str(i), "role": role, "text": "근거문구"} for i, role in enumerate(
                        ("AGREEMENT", "FUNCTION_LIST", "MANUAL"))], [], "확인되지 않음",
                     {"extract": "extract", "write": "write"})
                calls = []
                fail = True
                async def call(instructions, payload, schema, num_predict=None):
                    nonlocal fail
                    calls.append(payload)
                    if "text" in payload:
                        if payload["role"] == "MANUAL" and fail:
                            raise RuntimeError("interrupted")
                        return {"facts": [{"sectionId": "overview", "text": "설명", "quote": "근거문구"}]}
                    return {"type": "paragraph", "text": "설명"}
                provider._call = call
                with self.assertRaises(RuntimeError):
                    await provider.generate(request)
                self.assertEqual({"completedChunks": 2, "totalChunks": 3}, await provider.progress(request))
                fail = False
                calls.clear()
                result = await provider.generate(request)
                self.assertEqual({"completedChunks": 3, "totalChunks": 3}, await provider.progress(request))
                self.assertEqual(1, sum("text" in c for c in calls))
                self.assertEqual(3, len(result["sections"][0]["sources"]))
        asyncio.run(run())

    def test_invented_quote_rejected(self):
        async def run():
            with tempfile.TemporaryDirectory() as root:
                provider = OllamaLlmProvider({"GENERATION_CACHE_DIR": root})
                async def call(*args):
                    return {"facts": [{"sectionId": "overview", "text": "made up", "quote": "absent"}]}
                provider._call = call
                request = LlmRequest("i", [{"id": "overview", "guideline": "g", "title": "개요",
                    "blocks": [{"type": "paragraph"}]}],
                    [{"fileId": "1", "role": "MANUAL", "text": "actual"}], [], "unknown", {"extract": "e", "write": "w"})
                result = await provider.generate(request)
                self.assertEqual("unknown", result["sections"][0]["blocks"][0]["text"])
        asyncio.run(run())

    def test_quote_minimum_and_overlap_are_template_configurable(self):
        provider = OllamaLlmProvider({}, {"evidence": {"requireExactQuote": True,
            "minimumQuoteCharacters": 5}, "chunking": {"overlapCharacters": 17,
                "maxChunkBytes": 321}})
        self.assertEqual(5, provider.minimum_quote_characters)
        self.assertEqual(17, provider.chunk_overlap_characters)
        self.assertEqual(321, provider.max_chunk_bytes)

    def test_writer_receives_template_output_token_limit(self):
        async def run():
            with tempfile.TemporaryDirectory() as root:
                provider = OllamaLlmProvider({"GENERATION_CACHE_DIR": root}, {"output": {
                    "generationTokens": 384, "validationRetries": 1}})
                captured = []
                async def call(instructions, payload, schema, num_predict=None):
                    captured.append(num_predict)
                    if "text" in payload:
                        return {"facts": [{"sectionId": "overview", "text": "설명 사실", "quote": "근거문구"}]}
                    return {"type": "paragraph", "text": "생성 설명"}
                provider._call = call
                request = LlmRequest("i", [{"id": "overview", "title": "개요", "guideline": "g",
                    "blocks": [{"type": "paragraph"}]}],
                    [{"fileId": "1", "role": "MANUAL", "text": "근거문구"}], [], "확인되지 않음",
                    {"extract": "e", "write": "w"})
                await provider.generate(request)
                self.assertEqual([None, 384], captured)
        asyncio.run(run())

    def test_invalid_evidence_is_discarded_without_trusting_model_quote(self):
        async def run():
            with tempfile.TemporaryDirectory() as root:
                provider = OllamaLlmProvider({"GENERATION_CACHE_DIR": root})
                async def call(instructions, payload, schema):
                    if "text" in payload:
                        return {"facts": [{"sectionId": "overview", "text": "추측", "quote": "원문에 없는 값"}]}
                    return {"type": "paragraph", "text": payload["unknownText"]}
                provider._call = call
                request = LlmRequest("i", [{"id": "overview", "title": "개요", "guideline": "g",
                    "blocks": [{"type": "paragraph"}]}],
                    [{"fileId": "1", "role": "MANUAL", "text": "실제 원문"}], [], "확인되지 않음",
                    {"extract": "e", "write": "w"})
                result = await provider.generate(request)
                self.assertEqual("확인되지 않음", result["sections"][0]["blocks"][0]["text"])
        asyncio.run(run())
