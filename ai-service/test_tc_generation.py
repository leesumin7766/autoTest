import asyncio
import json
import tempfile
import unittest
from unittest.mock import patch
from pydantic import ValidationError

from product_description.api import ContentRequest, PreflightWarning, generate_content
from product_description.ollama_provider import OllamaLlmProvider
from tc_generation import Batch, Draft, TcGenerationRequest, build_external_prompt, cache_directory, generate, make_plan, materialize, source_spans, llm_schema, parse_batch, validate_feature_paths


def body():
    return TcGenerationRequest(submissionId="test-submission", productId=1, decision="READY",
        menuTree={"status":"APPROVED", "nodes":[{"featurePath":["Exports"],"children":[]}]},
        documents=[{"fileId": role, "role": role, "originalFilename": "synthetic.txt", "format": "PDF",
                    "extractedText": "The export button downloads a CSV file. This is a synthetic test document."}
                   for role in ["FUNCTION_LIST", "MANUAL"]])


def draft(quote="The export button downloads a CSV file."):
    return Draft(testType="PRODUCT_BEHAVIOR", featurePath=["Exports"], qualityCharacteristic="기능적합성", subCharacteristic="기능정확성",
        scenario="CSV 다운로드 확인", preconditions=[], steps=["내보내기 클릭"], inputs="",
        expectedResult="CSV 파일 다운로드", reviewNotes=[], defectExampleSummary="다운로드 오류",
        defectExampleSeverity="M", defectExampleContent="작성 예시", quote=quote)


class TcTests(unittest.TestCase):
    def test_agreement_is_optional_but_function_list_and_manual_are_required(self):
        request = body()
        self.assertEqual({"FUNCTION_LIST", "MANUAL"}, {document.role for document in request.documents})
        payload = request.model_dump()
        payload["documents"] = [document for document in payload["documents"] if document["role"] != "MANUAL"]
        with self.assertRaises(ValidationError):
            TcGenerationRequest.model_validate(payload)

    def test_complete_coverage_and_prompt_budget(self):
        request = body()
        request.documents[1].extractedText = "한글🙂 " * 4000
        with patch.dict("os.environ", {"OLLAMA_NUM_CTX": "8192", "OLLAMA_NUM_PREDICT": "2048"}):
            plan = make_plan(request)
            self.assertEqual(request.documents[1].extractedText,
                             "".join(text for doc, _, _, text in plan if doc.role == "MANUAL"))
            provider = OllamaLlmProvider({"OLLAMA_NUM_CTX": "8192", "OLLAMA_NUM_PREDICT": "1024"})
            from tc_generation import INSTRUCTIONS, llm_schema
            for doc, _, _, text in plan:
                payload = {"role": doc.role, "spans": source_spans(text), "preflightWarnings": [],
                           "approvedFeaturePaths": [["Exports"]]}
                messages = [{"role": "system", "content": INSTRUCTIONS},
                            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}]
                estimated = len(json.dumps(messages, ensure_ascii=False).encode()) + len(json.dumps(llm_schema([s["id"] for s in source_spans(text)])).encode())
                self.assertLessEqual(estimated, provider.input_budget)

    def test_request_output_reserve_controls_input_budget(self):
        provider = OllamaLlmProvider({"OLLAMA_NUM_CTX": "12288", "OLLAMA_NUM_PREDICT": "2048"})
        self.assertEqual(9728, provider.input_budget)
        # TC requests reserve 1024 output tokens, even though product-description
        # requests use the larger global reserve.
        self.assertEqual(10752, provider.input_budget_for(1024))

    def test_external_prompt_preview_matches_one_manual_model_call(self):
        request = body()
        request.documents[0].extractedText = "function-list-only-marker"
        request.documents[1].extractedText = "manual-only-marker"
        with patch.dict("os.environ", {"OLLAMA_NUM_CTX": "16384", "TC_OLLAMA_NUM_PREDICT": "1024",
                                       "TC_MAX_CHUNK_BYTES": "8000"}):
            preview = build_external_prompt(request, 0)
            self.assertEqual(1, preview["totalChunks"])
            self.assertEqual(len("manual-only-marker"), preview["characterCount"])
            self.assertIn("manual-only-marker", preview["prompt"])
            self.assertNotIn("function-list-only-marker", preview["prompt"])
            self.assertIn('"approvedFeaturePaths"', preview["prompt"])
            self.assertIn('"sourceSpanId"', preview["prompt"])
            self.assertIn("JSON Schema", preview["prompt"])
            with self.assertRaisesRegex(ValueError, "TC_PROMPT_CHUNK_NOT_FOUND"):
                build_external_prompt(request, 1)

    def test_large_approved_menu_tree_fits_increased_context_without_truncation(self):
        request = body()
        children = [{"manualSection": f"1.{index}", "title": f"관리 기능 메뉴 {index:02d}",
                     "featurePath": ["제품 관리", f"관리 기능 메뉴 {index:02d}"], "children": []}
                    for index in range(1, 45)]
        request.menuTree["nodes"] = [{"manualSection": "1", "title": "제품 관리",
                                       "featurePath": ["제품 관리"], "children": children}]
        request.warnings = [
            PreflightWarning(code="DOCUMENT_PARTIALLY_READABLE", role="MANUAL", message="readability"),
            PreflightWarning(code="AGREEMENT_DOCUMENT_MISSING", role="AGREEMENT", message="optional"),
            PreflightWarning(code="JEV_INPUT_TRUNCATED", role="MANUAL", message="truncated"),
        ]
        with patch.dict("os.environ", {"OLLAMA_NUM_CTX": "8192", "TC_OLLAMA_NUM_PREDICT": "1024"}):
            with self.assertRaisesRegex(ValueError, "TC_INPUT_BUDGET_INSUFFICIENT"):
                make_plan(request)
        with patch.dict("os.environ", {"OLLAMA_NUM_CTX": "12288", "TC_OLLAMA_NUM_PREDICT": "1024"}):
            plan = make_plan(request)
        self.assertTrue(plan)
        self.assertEqual(request.documents[1].extractedText,
                         "".join(text for doc, _, _, text in plan if doc.role == "MANUAL"))

    def test_max_context_and_larger_chunks_fit_actual_tc_request_budget(self):
        from tc_generation import INSTRUCTIONS, approved_feature_paths, prompt_warning_summary
        request = body()
        children = [{"manualSection": f"1.{index}", "title": f"관리 기능 메뉴 {index:02d}",
                     "featurePath": ["제품 관리", f"관리 기능 메뉴 {index:02d}"], "children": []}
                    for index in range(1, 45)]
        request.menuTree["nodes"] = [{"manualSection": "1", "title": "제품 관리",
                                        "featurePath": ["제품 관리"], "children": children}]
        request.documents[1].extractedText = "한글 기능 설명과 설정 안내입니다. " * 5000
        request.warnings = [
            PreflightWarning(code="DOCUMENT_PARTIALLY_READABLE", role="MANUAL", message="readability"),
            PreflightWarning(code="AGREEMENT_DOCUMENT_MISSING", role="AGREEMENT", message="optional"),
            PreflightWarning(code="JEV_INPUT_TRUNCATED", role="MANUAL", message="truncated"),
        ]
        settings = {"OLLAMA_NUM_CTX": "16384", "OLLAMA_NUM_PREDICT": "2048",
                    "TC_OLLAMA_NUM_PREDICT": "1024", "TC_MAX_CHUNK_BYTES": "8000"}
        with patch.dict("os.environ", settings):
            plan = make_plan(request)
            provider = OllamaLlmProvider(settings)
            manual_chunks = [text for doc, _, _, text in plan if doc.role == "MANUAL"]
            self.assertGreater(max(len(text.encode("utf-8")) for text in manual_chunks), 5000)
            for text in manual_chunks:
                spans = source_spans(text)
                payload = {"role": "MANUAL", "spans": spans,
                           "preflightWarnings": prompt_warning_summary(request),
                           "approvedFeaturePaths": [list(path) for path in sorted(approved_feature_paths(request.menuTree))]}
                messages = [{"role": "system", "content": INSTRUCTIONS},
                            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}]
                estimated = len(json.dumps(messages, ensure_ascii=False).encode())
                estimated += len(json.dumps(llm_schema([span["id"] for span in spans])).encode())
                self.assertLessEqual(estimated, provider.input_budget_for(1024))

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

    def test_product_behavior_path_must_match_approved_tree(self):
        validate_feature_paths(Batch(testCases=[draft()]), body().menuTree)
        invalid = draft()
        invalid.featurePath = ["Wrong menu"]
        with self.assertRaisesRegex(ValueError, "TC_FEATURE_PATH_INVALID"):
            validate_feature_paths(Batch(testCases=[invalid]), body().menuTree)

    def test_three_realistic_warnings_fit_and_korean_quality_is_restored(self):
        from tc_generation import llm_schema, parse_batch, INSTRUCTIONS, prompt_warning_summary
        request = body()
        request_data = request.model_dump()
        request_data["warnings"] = [
            {"code": "DOCUMENT_PARTIALLY_READABLE", "role": "MANUAL", "message": "문서에 손상되었거나 불확실한 추출 내용이 있을 수 있습니다."},
            {"code": "PRODUCT_IDENTITY_UNCERTAIN", "message": "AGREEMENT 문서와 MANUAL 문서의 제품 일치 여부가 불확실합니다."},
            {"code": "JEV_INPUT_TRUNCATED", "role": "MANUAL", "message": "긴 문서의 앞부분과 뒷부분만 Jev 판정에 사용했습니다."}]
        request = TcGenerationRequest.model_validate(request_data)
        request.documents[1].extractedText = "한글 문서 데이터🙂 " * 5000
        with patch.dict("os.environ", {"OLLAMA_NUM_CTX": "16384", "TC_OLLAMA_NUM_PREDICT": "1024",
                                       "TC_MAX_CHUNK_BYTES": "8000"}):
            plan = make_plan(request)
        self.assertGreater(len(plan), 2)
        for doc, _, _, text in plan:
            payload = {"role": doc.role, "spans": source_spans(text), "preflightWarnings": prompt_warning_summary(request),
                       "approvedFeaturePaths": [["Exports"]]}
            messages = [{"role": "system", "content": INSTRUCTIONS}, {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}]
            self.assertLessEqual(len(json.dumps(messages, ensure_ascii=False).encode()) + len(json.dumps(llm_schema([s["id"] for s in source_spans(text)])).encode()), 16384 - 1024 - 512)
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
            self.assertEqual(2, result["completedChunks"])
            self.assertEqual(3, len(result["testCases"]))
            self.assertEqual(2, sum(tc["testType"] == "DOCUMENT_CONFORMANCE" for tc in result["testCases"]))
            self.assertIsNone(result["testCases"][0]["result"])
            self.assertTrue(result["testCases"][0]["reviewRequired"])
            mock_case = next(tc for tc in result["testCases"] if tc["testType"] == "PRODUCT_BEHAVIOR")
            self.assertEqual(["MANUAL"], [source["role"] for source in mock_case["sources"]])
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
            product_case = next(tc for tc in result["testCases"] if tc["testType"] == "PRODUCT_BEHAVIOR")
            self.assertEqual("MANUAL", product_case["executionMode"])
            self.assertEqual(["MANUAL"], [source["role"] for source in product_case["sources"]])
            self.assertIsNone(product_case["result"])

    def test_invalid_quality_pair_is_rejected(self):
        data = draft().model_dump()
        data["qualityCharacteristic"] = "보안성"
        with self.assertRaises(ValueError):
            Draft(**data)

    def test_document_availability_cases_are_policy_based_and_separate(self):
        from tc_generation import document_availability_cases
        cases = document_availability_cases()
        self.assertEqual(["product-description", "user-manual"], [case["source"]["criterionId"] for case in cases])
        self.assertTrue(all(case["testType"] == "DOCUMENT_CONFORMANCE" for case in cases))
        self.assertTrue(all(case["result"] is None and case["reviewRequired"] for case in cases))
        self.assertTrue(all("업로드된 분석용 매뉴얼" in case["reviewNotes"][1] for case in cases))

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
