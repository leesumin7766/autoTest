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
        for path in ("content", "render"):
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
                     [{"fileId": str(i), "role": role, "text": "근거"} for i, role in enumerate(
                         ("AGREEMENT", "FUNCTION_LIST", "MANUAL"))], [], "확인되지 않음",
                     {"extract": "extract", "write": "write"})
                calls = []
                fail = True
                async def call(instructions, payload, schema):
                    nonlocal fail
                    calls.append(payload)
                    if "text" in payload:
                        if payload["role"] == "MANUAL" and fail:
                            raise RuntimeError("interrupted")
                        return {"facts": [{"sectionId": "overview", "text": "설명", "quote": "근거"}]}
                    return {"type": "paragraph", "text": "설명"}
                provider._call = call
                with self.assertRaises(RuntimeError):
                    await provider.generate(request)
                fail = False
                calls.clear()
                result = await provider.generate(request)
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
                request = LlmRequest("i", [{"id": "overview", "guideline": "g"}],
                    [{"fileId": "1", "role": "MANUAL", "text": "actual"}], [], "unknown", {"extract": "e", "write": "w"})
                with self.assertRaisesRegex(ValueError, "EVIDENCE"):
                    await provider.generate(request)
        asyncio.run(run())
