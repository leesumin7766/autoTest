import asyncio
import json
import tempfile
import unittest
from unittest.mock import patch

from product_description.api import ContentRequest, generate_content
from product_description.ollama_provider import OllamaLlmProvider
from tc_generation import Batch, Draft, cache_directory, generate, make_plan, materialize, source_spans, llm_schema, parse_batch


def body():
    return ContentRequest(submissionId="test-submission", productId=1, decision="READY",
        documents=[{"fileId": role, "role": role, "originalFilename": "synthetic.txt", "format": "PDF",
                    "extractedText": "The export button downloads a CSV file. This is a synthetic test document."}
                   for role in ["AGREEMENT", "FUNCTION_LIST", "MANUAL"]])


def draft(quote="The export button downloads a CSV file."):
    return Draft(featurePath=["Exports"], qualityCharacteristic="기능적합성", subCharacteristic="기능정확성",
        scenario="CSV 다운로드 확인", preconditions=[], steps=["내보내기 클릭"], inputs="",
        expectedResult="CSV 파일 다운로드", reviewNotes=[], defectExampleSummary="다운로드 오류",
        defectExampleSeverity="M", defectExampleContent="작성 예시", quote=quote)


class TcTests(unittest.TestCase):
    def test_complete_coverage_and_prompt_budget(self):
        request = body()
        request.documents[2].extractedText = "한글🙂 " * 4000
        with patch.dict("os.environ", {"OLLAMA_NUM_CTX": "8192", "OLLAMA_NUM_PREDICT": "2048"}):
            plan = make_plan(request)
            self.assertEqual(request.documents[2].extractedText,
                             "".join(text for doc, _, _, text in plan if doc.role == "MANUAL"))
            provider = OllamaLlmProvider({"OLLAMA_NUM_CTX": "8192", "OLLAMA_NUM_PREDICT": "2048"})
            from tc_generation import INSTRUCTIONS, llm_schema
            for doc, _, _, text in plan:
                payload = {"role": doc.role, "spans": source_spans(text), "preflightWarnings": []}
                messages = [{"role": "system", "content": INSTRUCTIONS},
                            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}]
                estimated = len(json.dumps(messages, ensure_ascii=False).encode()) + len(json.dumps(llm_schema([s["id"] for s in source_spans(text)])).encode())
                self.assertLessEqual(estimated, provider.input_budget)

    def test_span_selection_preserves_original_and_rejects_unknown_id(self):
        spans = source_spans(body().documents[0].extractedText)
        data = draft().model_dump()
        data.pop("quote")
        data["sourceSpanId"] = spans[0]["id"]
        restored = parse_batch({"testCases": [dict(data)]}, spans)
        self.assertEqual(spans[0]["text"], restored.testCases[0].quote)
        self.assertEqual(spans[0]["text"], materialize(restored, body().documents[0], 0, 100)[0]["source"]["quote"])
        data["sourceSpanId"] = 999
        with self.assertRaisesRegex(ValueError, "TC_EVIDENCE_INVALID"):
            parse_batch({"testCases": [data]}, spans)

    def test_invented_evidence_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "TC_EVIDENCE_INVALID"):
            materialize(Batch(testCases=[draft("There is no such sentence.")]), body().documents[0], 0, 70)

    def test_three_realistic_warnings_fit_and_korean_quality_is_restored(self):
        from tc_generation import llm_schema, parse_batch, INSTRUCTIONS
        request = body()
        request_data = request.model_dump()
        request_data["warnings"] = [
            {"code": "DOCUMENT_PARTIALLY_READABLE", "role": "MANUAL", "message": "문서에 손상되었거나 불확실한 추출 내용이 있을 수 있습니다."},
            {"code": "PRODUCT_IDENTITY_UNCERTAIN", "message": "AGREEMENT 문서와 MANUAL 문서의 제품 일치 여부가 불확실합니다."},
            {"code": "JEV_INPUT_TRUNCATED", "role": "MANUAL", "message": "긴 문서의 앞부분과 뒷부분만 Jev 판정에 사용했습니다."}]
        request = ContentRequest.model_validate(request_data)
        request.documents[2].extractedText = "한글 문서 데이터🙂 " * 5000
        plan = make_plan(request)
        self.assertGreater(len(plan), 3)
        for doc, _, _, text in plan:
            payload = {"role": doc.role, "spans": source_spans(text), "preflightWarnings": [w.model_dump() for w in request.warnings]}
            messages = [{"role": "system", "content": INSTRUCTIONS}, {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}]
            self.assertLessEqual(len(json.dumps(messages, ensure_ascii=False).encode()) + len(json.dumps(llm_schema([s["id"] for s in source_spans(text)])).encode()), 8192 - 2048 - 512)
        data = draft().model_dump(); data.pop("qualityCharacteristic"); data.pop("subCharacteristic")
        data["qualityPair"] = "functional_correctness"
        restored = parse_batch({"testCases": [data]}).testCases[0]
        self.assertEqual("기능적합성", restored.qualityCharacteristic)
        self.assertEqual("기능정확성", restored.subCharacteristic)

    def test_mock_result_has_no_verdict_and_is_marked(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict("os.environ", {
            "LLM_MODE": "mock", "GENERATION_CACHE_DIR": directory}):
            result = asyncio.run(generate(body()))
            self.assertEqual("MOCK", result["generationMode"])
            self.assertEqual(3, result["completedChunks"])
            self.assertEqual(1, len(result["testCases"]))
            self.assertIsNone(result["testCases"][0]["result"])
            self.assertTrue(result["testCases"][0]["reviewRequired"])
            self.assertEqual(3, len(result["testCases"][0]["sources"]))
            self.assertEqual(result, asyncio.run(generate(body())))

    def test_real_provider_preserves_sources_and_forces_security_manual(self):
        data = draft().model_dump()
        data["qualityCharacteristic"] = "보안성"
        data["subCharacteristic"] = "기밀성"
        with tempfile.TemporaryDirectory() as directory, patch.dict("os.environ", {
            "LLM_MODE": "real", "LLM_PROVIDER": "ollama", "GENERATION_CACHE_DIR": directory}), \
                patch.object(OllamaLlmProvider, "_call", return_value={"testCases": [data]}):
            result = asyncio.run(generate(body()))
            self.assertEqual("REAL", result["generationMode"])
            self.assertEqual("MANUAL", result["testCases"][0]["executionMode"])
            self.assertIsNone(result["testCases"][0]["result"])

    def test_invalid_quality_pair_is_rejected(self):
        data = draft().model_dump()
        data["qualityCharacteristic"] = "보안성"
        with self.assertRaises(ValueError):
            Draft(**data)

    def test_cache_is_invalidated_for_changed_source_or_model(self):
        with patch.dict("os.environ", {"LLM_MODE": "real", "LLM_MODEL": "model-a"}):
            original = cache_directory(body())
            changed = body(); changed.documents[0].extractedText += " changed"
            self.assertNotEqual(original, cache_directory(changed))
            with patch.dict("os.environ", {"LLM_MODEL": "model-b"}):
                self.assertNotEqual(original, cache_directory(body()))

    def test_product_description_is_disabled_without_explicit_opt_in(self):
        with patch.dict("os.environ", {"PRODUCT_DESCRIPTION_GENERATION_ENABLED": "false"}), \
                patch("product_description.api.provider_from_env") as provider:
            response = asyncio.run(generate_content(body(), None))
            self.assertEqual(503, response.status_code)
            self.assertIn(b"PRODUCT_DESCRIPTION_DISABLED", response.body)
            provider.assert_not_called()


if __name__ == "__main__":
    unittest.main()
