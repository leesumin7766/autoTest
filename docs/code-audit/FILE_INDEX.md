# 코드·설정 파일별 정적 탐색 목록

감사 시작 시 Git 추적 파일 전체는 inventory.json에 경로·분류·해시로 기록했다. 아래는 자체 코드·설정·템플릿과 검증 스크립트의 선언/HTTP 경로 탐색 목록이다. 정규식 탐색 결과이며 언어 문법 전체를 분석한 호출 그래프나 모든 행의 의미 검증을 뜻하지 않는다. 외부 라이브러리·산출물도 전체 인벤토리에 별도 분류해 누락 여부를 확인한다. .env와 인증키를 읽거나 복사하지 않는다.

## [Main.java](../../Main.java)

제품 코드·설정·템플릿 · 8줄 · 152바이트

루트의 출력 예제 파일. Gradle 기본 src/main/java 소스 경로에 있지 않고 현재 제품 흐름에 연결되지 않는다.

- 3행: `Main`
- 4행: `main`

## [ai-service/doc_templates/product-description/content.json](../../ai-service/doc_templates/product-description/content.json)

제품 코드·설정·템플릿 · 173줄 · 5863바이트

제품 설명 템플릿의 내용·표현·버전 manifest 데이터.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/doc_templates/product-description/manifest.json](../../ai-service/doc_templates/product-description/manifest.json)

제품 코드·설정·템플릿 · 7줄 · 149바이트

제품 설명 템플릿의 내용·표현·버전 manifest 데이터.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/doc_templates/product-description/presentation.json](../../ai-service/doc_templates/product-description/presentation.json)

제품 코드·설정·템플릿 · 45줄 · 1555바이트

제품 설명 템플릿의 내용·표현·버전 manifest 데이터.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/execution_contract.py](../../ai-service/execution_contract.py)

제품 코드·설정·템플릿 · 95줄 · 5436바이트

원문 기대 조건 분리·조건 보정·미지원 사전 입력·수행 여부·계획용 관찰 이력.

- 9행: `requirements`
- 16행: `descriptive`
- 20행: `compiled_conditions`
- 40행: `prerequisites`
- 67행: `navigation_record`
- 73행: `performed_test`
- 81행: `planner_history`

## [ai-service/execution_evidence.py](../../ai-service/execution_evidence.py)

제품 코드·설정·템플릿 · 92줄 · 7330바이트

조건별 실제 증거·원문 범위·입력 연결·저장 재확인·P/F 보호 검사.

- 9행: `Condition`
- 15행: `semantic_requirement`
- 18행: `assess`
- 65행: `gate`

## [ai-service/execution_profiles.py](../../ai-service/execution_profiles.py)

제품 코드·설정·템플릿 · 107줄 · 6235바이트

조회/상호작용 구분·대상 도달·코드 메뉴 이동·반복 조작 키·정형 결과 비교.

- 8행: `profile`
- 20행: `target_terms`
- 31행: `target_observed`
- 43행: `verified_read_check`
- 51행: `single_action_done`
- 61행: `literal_assertion`
- 74행: `navigation_plan`
- 85행: `loop_key`
- 100행: `public_trace`

## [ai-service/execution_selection.py](../../ai-service/execution_selection.py)

제품 코드·설정·템플릿 · 66줄 · 4383바이트

명시적 동작 불일치 후보를 Qwen으로 검토한다. EXCLUDED를 N/A 확정으로 바꾸지 않는다.

- 11행: `Selection`
- 19행: `select_with_qwen`
- 35행: `preflight`

## [ai-service/execution_specification.py](../../ai-service/execution_specification.py)

제품 코드·설정·템플릿 · 119줄 · 7420바이트

v2-read의 원본 digest·경로·대상 근거·모든 단계/조건 연결을 검증하고 준비된 조회 경로를 실행기에 제공한다.

- 14행: `PreparedRead`
- 20행: `reached`
- 21행: `route`
- 29행: `navigation_action`
- 35행: `prepare_read`
- 43행: `_prepare_read`
- 50행: `issue`
- 106행: `grounded_oracle`

## [ai-service/functional_menu.py](../../ai-service/functional_menu.py)

제품 코드·설정·템플릿 · 344줄 · 21507바이트

과거/내부 자동 메뉴 생성·진행·checkpoint·prompt 경로. 현재 UI의 외부 LLM 방식과 구별한다.

- 29행: `checkpoint_path`
- 32행: `save_checkpoint`
- 48행: `ground_proposal`
- 83행: `extract_source`
- 125행: `Proposal`
- 135행: `Request`
- 142행: `validate_rows`
- 167행: `baseline`
- 187행: `prompt`
- 196행: `merge`
- 211행: `call_spec`
- 239행: `request_size`
- 243행: `budget_segments`
- 259행: `HTTP /prompt`
- 260행: `external_prompt`
- 264행: `HTTP /progress/{job_id}`
- 265행: `progress`
- 268행: `HTTP /checkpoint/{job_id}`
- 269행: `checkpoint`
- 274행: `generate`
- 331행: `HTTP /generate`
- 332행: `generate_http`

## [ai-service/initial_entry.py](../../ai-service/initial_entry.py)

제품 코드·설정·템플릿 · 18줄 · 1451바이트

최초 화면 복구에 RESET_SESSION/STOP 결정만 요청한다.

- 6행: `plan_initial_entry`

## [ai-service/login_execution.py](../../ai-service/login_execution.py)

제품 코드·설정·템플릿 · 28줄 · 2223바이트

저장 계정 정상 로그인 결정을 Qwen에 요청하고 입력·제출·인증 기록을 확인한다.

- 6행: `login_attempt`
- 9행: `login_submitted`
- 13행: `login_authenticated`
- 17행: `plan_saved_login`

## [ai-service/main.py](../../ai-service/main.py)

제품 코드·설정·템플릿 · 207줄 · 8846바이트

FastAPI 앱·router 연결, 필수 2역할/선택 합의서 접수·중복 digest·사전 점검·AI 접수 DB 저장.

- 33행: `log_validation_failure`
- 40행: `IntakeDocument`
- 51행: `DocumentIntake`
- 59행: `validate_documents`
- 72행: `database_connection`
- 80행: `initialize_intake_store`
- 96행: `HTTP /`
- 97행: `health`
- 101행: `HTTP /api/v1/document-intakes`
- 102행: `receive_documents`
- 150행: `_evaluate_once`
- 157행: `_blocked_receipt`
- 169행: `_find_intake`
- 178행: `_ensure_preflight`
- 195행: `_receipt`

## [ai-service/menu_templates/functional-v1/examples.json](../../ai-service/menu_templates/functional-v1/examples.json)

제품 코드·설정·템플릿 · 8줄 · 826바이트

내부 자동 메뉴 생성용 규칙·스키마·예시. 현재 self-llm-v4 공통 MD와 별개다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/menu_templates/functional-v1/output.schema.json](../../ai-service/menu_templates/functional-v1/output.schema.json)

제품 코드·설정·템플릿 · 17줄 · 1132바이트

내부 자동 메뉴 생성용 규칙·스키마·예시. 현재 self-llm-v4 공통 MD와 별개다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/menu_templates/functional-v1/rules.json](../../ai-service/menu_templates/functional-v1/rules.json)

제품 코드·설정·템플릿 · 23줄 · 1949바이트

내부 자동 메뉴 생성용 규칙·스키마·예시. 현재 self-llm-v4 공통 MD와 별개다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/menu_tree.py](../../ai-service/menu_tree.py)

제품 코드·설정·템플릿 · 59줄 · 4535바이트

기존 TC 생성 경로의 메뉴트리 계약·외부 응답/진행 기능.

- 13행: `MenuTreePromptRequest`
- 19행: `build_menu_tree_prompt`
- 56행: `HTTP /prompt-preview`
- 57행: `menu_tree_prompt_preview`

## [ai-service/preflight.py](../../ai-service/preflight.py)

제품 코드·설정·템플릿 · 360줄 · 17816바이트

결정적 텍스트 검사·제품/문서 역할 Jev 평가·경고/차단·동시 요청 한도.

- 41행: `JevOverloaded`
- 48행: `evaluate_preflight`
- 245행: `_evaluate_with_jev`
- 301행: `call`
- 326행: `_request_jev`
- 342행: `_jev_excerpt`
- 349행: `_is_suspicious_character`
- 354행: `_warning`

## [ai-service/preflight_store.py](../../ai-service/preflight_store.py)

제품 코드·설정·템플릿 · 122줄 · 5347바이트

사전 점검 결과 DB claim·lease·중복 요청 방지. 이 저장소에는 문서 원문을 보관하지 않는다.

- 33행: `Claim`
- 39행: `PreflightBusy`
- 43행: `initialize`
- 50행: `claim`
- 76행: `complete`
- 90행: `release`
- 98행: `run_once`

## [ai-service/product_description/__init__.py](../../ai-service/product_description/__init__.py)

제품 코드·설정·템플릿 · 1줄 · 0바이트

제품 설명 생성 패키지 __init__.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [ai-service/product_description/api.py](../../ai-service/product_description/api.py)

제품 코드·설정·템플릿 · 155줄 · 6466바이트

제품 설명 생성 패키지 api.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 17행: `require_internal_token`
- 30행: `SourceDocument`
- 40행: `PreflightWarning`
- 49행: `ContentRequest`
- 61행: `three_roles`
- 69행: `RenderRequest`
- 77행: `_error`
- 81행: `HTTP /content`
- 82행: `generate_content`
- 125행: `HTTP /content/progress`
- 126행: `content_progress`
- 145행: `HTTP /render`
- 146행: `render_document`

## [ai-service/product_description/builder.py](../../ai-service/product_description/builder.py)

제품 코드·설정·템플릿 · 139줄 · 7094바이트

제품 설명 생성 패키지 builder.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 10행: `InvalidLlmOutput`
- 14행: `build_llm_request`
- 38행: `assemble_document`
- 73행: `_llm_section`
- 96행: `_resolve_sources`
- 109행: `_role_label`
- 113행: `_system_section`
- 137행: `now_utc`

## [ai-service/product_description/document_model.py](../../ai-service/product_description/document_model.py)

제품 코드·설정·템플릿 · 93줄 · 2585바이트

제품 설명 생성 패키지 document_model.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 10행: `Strict`
- 14행: `ParagraphBlock`
- 19행: `BulletsBlock`
- 24행: `TableBlock`
- 31행: `rows_match_columns`
- 42행: `SourceRef`
- 48행: `Section`
- 55행: `TemplateRef`
- 61행: `GenerationInfo`
- 69행: `GeneratedDocument`
- 78행: `unique_section_ids`
- 85행: `LlmSection`
- 91행: `LlmOutput`

## [ai-service/product_description/llm.py](../../ai-service/product_description/llm.py)

제품 코드·설정·템플릿 · 104줄 · 4447바이트

제품 설명 생성 패키지 llm.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 8행: `LlmConfigurationError`
- 12행: `LlmProviderNotImplemented`
- 17행: `LlmRequest`
- 25행: `input_payload`
- 29행: `LlmProvider`
- 34행: `generate`
- 39행: `MockLlmProvider`
- 44행: `__init__`
- 47행: `generate`
- 71행: `ExternalLlmProvider`
- 76행: `__init__`
- 80행: `generate`
- 86행: `provider_from_env`

## [ai-service/product_description/ollama_provider.py](../../ai-service/product_description/ollama_provider.py)

제품 코드·설정·템플릿 · 301줄 · 16037바이트

제품 설명 생성 패키지 ollama_provider.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 19행: `Fact`
- 26행: `Facts`
- 31행: `split_document`
- 56행: `OllamaLlmProvider`
- 60행: `__init__`
- 75행: `input_budget_for`
- 80행: `configure_quality`
- 88행: `_identity`
- 93행: `_progress`
- 111행: `progress`
- 118행: `_call`
- 146행: `_save`
- 151행: `generate`
- 158행: `_generate`
- 286행: `block_schema`

## [ai-service/product_description/pdf_renderer.py](../../ai-service/product_description/pdf_renderer.py)

제품 코드·설정·템플릿 · 170줄 · 8649바이트

제품 설명 생성 패키지 pdf_renderer.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 22행: `PdfRenderer`
- 26행: `render`
- 39행: `make_canvas`
- 55행: `_story`
- 80행: `_block`
- 90행: `_table`
- 113행: `_register_fonts`
- 127행: `_styles`
- 139행: `_NumberedCanvas`
- 140행: `__init__`
- 145행: `showPage`
- 149행: `save`
- 157행: `_draw_decor`

## [ai-service/product_description/rendering.py](../../ai-service/product_description/rendering.py)

제품 코드·설정·템플릿 · 25줄 · 737바이트

제품 설명 생성 패키지 rendering.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 5행: `RenderError`
- 9행: `DocumentRenderer`
- 13행: `render`
- 17행: `get_renderer`

## [ai-service/product_description/template_store.py](../../ai-service/product_description/template_store.py)

제품 코드·설정·템플릿 · 129줄 · 7409바이트

제품 설명 생성 패키지 template_store.py: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.

- 17행: `TemplateError`
- 21행: `load_template`
- 39행: `_read_json`
- 47행: `_validate`
- 116행: `_validate_blocks`

## [ai-service/tc_generation.py](../../ai-service/tc_generation.py)

제품 코드·설정·템플릿 · 399줄 · 23502바이트

기존 문서 기반 LLM TC 생성·청크·근거·스키마·진행 처리. 현재 메뉴 템플릿 생성 API가 호출하는 코드가 아니다.

- 43행: `TcGenerationRequest`
- 55행: `required_tc_documents`
- 65행: `Draft`
- 87행: `matching_quality_pair`
- 99행: `Batch`
- 104행: `llm_schema`
- 117행: `compact`
- 126행: `parse_batch`
- 139행: `source_spans`
- 153행: `prompt_warning_summary`
- 158행: `approved_feature_paths`
- 160행: `visit`
- 172행: `validate_feature_paths`
- 179행: `cache_directory`
- 190행: `make_plan`
- 206행: `build_external_prompt`
- 226행: `materialize`
- 242행: `generate`
- 318행: `document_availability_cases`
- 356행: `HTTP /generate`
- 357행: `generate_endpoint`
- 380행: `HTTP /progress`
- 381행: `progress`
- 388행: `HTTP /prompt-preview`
- 389행: `prompt_preview`

## [ai-service/test_execution.py](../../ai-service/test_execution.py)

검증·보조 코드 · 435줄 · 41243바이트

검증/보조 파일 test_execution.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 22행: `Action`
- 30행: `Plan`
- 39행: `Execute`
- 95행: `compact_case`
- 98행: `plan_with_qwen`
- 124행: `judge_with_jev`
- 149행: `judgments`
- 172행: `pending`
- 188행: `_execute_case`
- 203행: `active`
- 208행: `progress`
- 389행: `execute_feature`
- 419행: `HTTP /feature`
- 420행: `feature`
- 423행: `HTTP /capabilities`
- 424행: `capabilities`

## [ai-service/test_execution_evidence.py](../../ai-service/test_execution_evidence.py)

검증·보조 코드 · 71줄 · 5850바이트

검증/보조 파일 test_execution_evidence.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 4행: `EvidenceTest`
- 5행: `test_planner_history_preserves_failure_without_repeating_unchanged_pages`
- 13행: `test_numbered_expectations_remain_two_complete_conditions`
- 25행: `test_positive_literal_cannot_be_compiled_as_absence`
- 29행: `evidence`
- 31행: `verdict`
- 34행: `test_explicit_matching_output_can_pass`
- 37행: `test_false_pass_injections`
- 47행: `test_whole_requirement_quote_does_not_turn_one_word_into_full_coverage`
- 53행: `test_save_toast_does_not_prove_persistence`
- 57행: `test_bound_value_must_survive_reload_and_not_preexist`
- 65행: `test_download_event_does_not_prove_contents`

## [ai-service/test_execution_profiles.py](../../ai-service/test_execution_profiles.py)

검증·보조 코드 · 72줄 · 5934바이트

검증/보조 파일 test_execution_profiles.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `ProfilesTest`
- 9행: `request`
- 12행: `answers`
- 15행: `test_view_check_does_not_require_repeated_menu_clicks`
- 16행: `planner`
- 18행: `transport`
- 25행: `test_sidebar_alone_cannot_pass_a_read_test`
- 26행: `transport`
- 28행: `planner`
- 29행: `judge`
- 33행: `test_recorded_route_is_matched_against_current_elements`
- 35행: `transport`
- 40행: `planner`
- 49행: `test_live_clock_changes_cannot_defeat_navigation_loop_guard`
- 51행: `transport`
- 58행: `planner`
- 64행: `test_interaction_is_not_reclassified_as_a_view`

## [ai-service/test_execution_selection.py](../../ai-service/test_execution_selection.py)

검증·보조 코드 · 48줄 · 3413바이트

검증/보조 파일 test_execution_selection.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 7행: `SelectionTests`
- 8행: `case`
- 12행: `test_confirmed_mismatch_preserves_case_and_never_opens_browser_or_grants_na`
- 14행: `selector`
- 15행: `planner`
- 23행: `test_unknown_low_confidence_wrong_quote_and_failure_do_not_exclude`
- 25행: `selector`
- 28행: `broken`
- 31행: `test_applicable_or_combined_scope_cannot_be_excluded_by_model`
- 32행: `selector`
- 40행: `test_missing_internal_evidence_is_not_browser_test_or_na`

## [ai-service/test_execution_specification.py](../../ai-service/test_execution_specification.py)

검증·보조 코드 · 104줄 · 7750바이트

검증/보조 파일 test_execution_specification.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `sample_case`
- 28행: `ReadSpecificationTests`
- 29행: `request`
- 34행: `answers`
- 38행: `no_planner`
- 41행: `test_prepared_recipe_navigates_and_checks_without_model_planning`
- 45행: `transport`
- 60행: `test_unbound_and_stale_recipes_never_open_browser_or_call_models`
- 73행: `transport`
- 74행: `judge`
- 82행: `test_same_label_with_other_url_and_sidebar_only_cannot_pass`
- 88행: `transport`
- 92행: `judge`
- 97행: `test_legacy_recipe_remains_on_existing_path`

## [ai-service/test_execution_test.py](../../ai-service/test_execution_test.py)

검증·보조 코드 · 155줄 · 13404바이트

검증/보조 파일 test_execution_test.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 7행: `ExecutionTest`
- 8행: `test_blocked_request_preserves_the_failure_screen`
- 9행: `transport`
- 13행: `planner`
- 19행: `request`
- 22행: `test_real_observations_only_and_close`
- 24행: `transport`
- 28행: `planner`
- 31행: `judge`
- 37행: `test_action_error_never_becomes_product_failure`
- 38행: `transport`
- 42행: `planner`
- 43행: `judge`
- 47행: `test_cancellation_after_inference_prevents_action`
- 49행: `transport`
- 56행: `planner`
- 60행: `test_confidence_and_insufficient_evidence`
- 65행: `test_one_success_cannot_pass_another_tc`
- 67행: `transport`
- 73행: `planner`
- 77행: `judge`
- 83행: `test_mislabeled_menu_click_cannot_finish_a_test`
- 85행: `transport`
- 90행: `planner`
- 94행: `judge`
- 103행: `test_navigation_only_never_calls_judge`
- 104행: `transport`
- 108행: `planner`
- 109행: `judge`
- 113행: `test_file_prerequisite_skips_browser_and_model`
- 114행: `planner`
- 119행: `test_initial_entry_redirect_is_recovered_before_any_test_plan`
- 121행: `observation`
- 122행: `transport`
- 130행: `planner`
- 138행: `test_persistent_initial_redirect_never_runs_test_or_grants_pass`
- 139행: `transport`
- 142행: `planner`
- 147행: `test_verified_login_redirect_does_not_trigger_recovery`
- 148행: `transport`
- 149행: `planner`

## [ai-service/test_functional_menu.py](../../ai-service/test_functional_menu.py)

검증·보조 코드 · 113줄 · 8709바이트

검증/보조 파일 test_functional_menu.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 13행: `FunctionalMenuTest`
- 14행: `test_web_hierarchy_owns_groups_and_optional_bars`
- 25행: `test_selected_menu_gets_program_evidence_without_semantic_confirmation`
- 39행: `test_invalid_row_does_not_discard_valid_rows_and_checkpoint`
- 40행: `MixedModel`
- 41행: `__init__`
- 42행: `input_budget_for`
- 43행: `_call`
- 54행: `test_long_korean_sources_fit_model_budget_without_losing_text`
- 68행: `setUp`
- 73행: `test_exact_quotes_and_menu_match_are_required`
- 80행: `test_schema_examples_and_mock_are_explicit`
- 88행: `test_internal_authentication_and_safe_errors`
- 96행: `test_missing_documented_action_is_recovered_with_grounded_span_ids`
- 97행: `SmallModel`
- 98행: `__init__`
- 99행: `input_budget_for`
- 100행: `_call`

## [ai-service/test_login_execution.py](../../ai-service/test_login_execution.py)

검증·보조 코드 · 79줄 · 6575바이트

검증/보조 파일 test_login_execution.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 10행: `LoginExecutionTest`
- 11행: `request`
- 16행: `run_login`
- 18행: `transport`
- 31행: `forbidden`
- 32행: `small`
- 34행: `judge`
- 39행: `test_saved_account_is_submitted_once_then_evidence_is_judged`
- 43행: `test_even_positive_judge_cannot_pass_unconfirmed_authentication`
- 46행: `test_missing_account_does_not_call_browser_or_model`
- 47행: `forbidden`
- 51행: `test_plain_login_click_is_not_normal_login_execution_evidence`
- 55행: `test_unimplemented_auth_actions_stop_before_browser_or_inference`
- 56행: `forbidden`
- 57행: `transport`
- 63행: `test_planner_receives_authentication_and_active_layer_state`
- 64행: `transport`
- 67행: `planner`
- 74행: `test_one_action_per_observation_is_enforced`

## [ai-service/test_main.py](../../ai-service/test_main.py)

검증·보조 코드 · 409줄 · 17115바이트

검증/보조 파일 test_main.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 16행: `FakeIntakeDatabase`
- 17행: `__init__`
- 22행: `connect`
- 26행: `FakeConnection`
- 27행: `__init__`
- 30행: `__enter__`
- 33행: `__exit__`
- 36행: `cursor`
- 40행: `FakeCursor`
- 41행: `__init__`
- 45행: `__enter__`
- 48행: `__exit__`
- 51행: `execute`
- 106행: `fetchone`
- 112행: `DocumentIntakeTests`
- 113행: `make_document`
- 123행: `test_accepts_required_core_documents_without_optional_agreement`
- 135행: `test_accepts_all_three_documents`
- 139행: `test_rejects_missing_core_or_duplicate_roles`
- 148행: `test_rejects_empty_extracted_text`
- 158행: `create_intake`
- 169행: `receive`
- 175행: `test_receives_all_documents_once`
- 188행: `test_same_submission_and_payload_returns_duplicate_receipt`
- 200행: `test_same_submission_with_different_payload_returns_409`
- 218행: `blocked_preflight`
- 225행: `test_blocked_result_is_not_stored_and_replacement_can_be_received`
- 252행: `test_concurrent_identical_requests_call_jev_once`
- 260행: `slow_evaluation`
- 275행: `test_completed_blocked_verdict_is_reused_without_new_jev_call_or_body_storage`
- 295행: `test_in_progress_attempt_of_another_instance_prevents_duplicate_jev_call`
- 313행: `test_waiting_request_returns_verdict_completed_by_another_instance`
- 322행: `finish_elsewhere`
- 336행: `test_expired_lease_is_reclaimed_and_failed_evaluation_releases_immediately`
- 355행: `test_changed_documents_use_a_new_hash_and_are_evaluated_again`
- 374행: `test_owner_that_lost_its_lease_uses_the_stored_verdict_and_stores_no_body`
- 383행: `slow_stale_evaluation`
- 399행: `payload_hash`

## [ai-service/test_menu_tree.py](../../ai-service/test_menu_tree.py)

검증·보조 코드 · 33줄 · 1830바이트

검증/보조 파일 test_menu_tree.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 7행: `MenuTreePromptTests`
- 8행: `test_dom_prompt_contains_elements_and_requires_verifiable_labels`
- 18행: `test_screenshot_prompt_requires_attached_capture_and_avoids_hidden_screen_inference`
- 25행: `test_dom_is_treated_as_untrusted_data_and_hidden_elements_are_excluded`

## [ai-service/test_ollama.py](../../ai-service/test_ollama.py)

검증·보조 코드 · 128줄 · 7401바이트

검증/보조 파일 test_ollama.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 14행: `OllamaTests`
- 15행: `test_auth_is_fail_closed_on_content_and_render`
- 28행: `test_local_provider_requires_no_api_key`
- 32행: `test_split_covers_every_character_with_utf8_budget`
- 39행: `test_failed_chunk_never_completes_and_retry_reuses_validated_chunks`
- 40행: `run`
- 50행: `call`
- 70행: `test_invented_quote_rejected`
- 71행: `run`
- 74행: `call`
- 84행: `test_quote_minimum_and_overlap_are_template_configurable`
- 92행: `test_writer_receives_template_output_token_limit`
- 93행: `run`
- 98행: `call`
- 112행: `test_invalid_evidence_is_discarded_without_trusting_model_quote`
- 113행: `run`
- 116행: `call`

## [ai-service/test_preflight.py](../../ai-service/test_preflight.py)

검증·보조 코드 · 444줄 · 20290바이트

검증/보조 파일 test_preflight.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `PreflightTests`
- 9행: `setUp`
- 16행: `test_unconfigured_jev_allows_documents_with_warning`
- 24행: `test_missing_agreement_is_warning_and_core_documents_are_still_checked`
- 35행: `test_small_amount_of_unreadable_characters_warns`
- 48행: `test_heavy_unreadable_characters_block`
- 58행: `test_missing_test_code_warns_without_blocking`
- 67행: `test_high_confidence_product_mismatch_blocks`
- 78행: `test_uncertain_product_identity_warns_and_proceeds`
- 93행: `test_partial_readability_warning_keeps_jev_label_and_confidence`
- 104행: `test_confident_wrong_role_blocks`
- 115행: `test_lower_role_mismatch_threshold_blocks_single_clear_mismatch`
- 126행: `test_repeated_moderate_role_mismatches_block_cross_assigned_roles`
- 139행: `test_weak_single_role_mismatch_remains_warning`
- 148행: `test_jev_failure_fails_open_with_warning`
- 156행: `test_long_document_truncation_is_reported`
- 172행: `test_evaluate_with_jev_matches_real_sdk_contract`
- 181행: `handler`
- 200행: `client_with_mock_transport`
- 218행: `test_jev_timing_layers_do_not_overlap`
- 235행: `test_sdk_call_abandoned_at_deadline_still_ends_within_its_bound`
- 243행: `Silent`
- 244행: `do_POST`
- 248행: `log_message`
- 258행: `local_client`
- 261행: `sdk_threads`
- 284행: `test_client_receives_explicit_timeout_and_retry_policy`
- 291행: `FakeClient`
- 292행: `__init__`
- 295행: `__enter__`
- 298행: `__exit__`
- 301행: `system_one`
- 315행: `test_jev_call_beyond_deadline_fails_open`
- 322행: `HangingClient`
- 323행: `__init__`
- 326행: `__enter__`
- 329행: `__exit__`
- 332행: `system_one`
- 346행: `test_abandoned_sdk_calls_from_many_submissions_are_capped`
- 357행: `StuckClient`
- 358행: `__init__`
- 361행: `__enter__`
- 364행: `__exit__`
- 367행: `system_one`
- 378행: `evaluate`
- 407행: `test_overloaded_jev_fails_open_without_blocking`
- 422행: `document`
- 425행: `warning_codes`
- 428행: `jev_answers`

## [ai-service/test_preflight_store_pg.py](../../ai-service/test_preflight_store_pg.py)

검증·보조 코드 · 208줄 · 10090바이트

검증/보조 파일 test_preflight_store_pg.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 15행: `PreflightStorePostgresTests`
- 16행: `setUp`
- 22행: `tearDown`
- 26행: `connect`
- 29행: `row`
- 37행: `test_claim_lease_completion_and_reuse`
- 51행: `test_expired_lease_is_reclaimed_and_stale_owner_cannot_complete`
- 64행: `test_expired_but_unclaimed_lease_can_still_complete`
- 73행: `test_complete_reports_lost_ownership_and_keeps_current_owners_verdict`
- 87행: `expire_lease`
- 92행: `test_stale_ready_owner_loses_to_blocked_owner_and_no_body_is_stored`
- 106행: `stale_evaluation`
- 136행: `expire_lease_for`
- 141행: `payload_hash_of`
- 149행: `test_release_allows_immediate_retry`
- 156행: `test_different_hash_is_independent`
- 163행: `test_concurrent_connections_evaluate_exactly_once`
- 168행: `evaluate`
- 173행: `worker`
- 193행: `test_prunes_only_old_rows`

## [ai-service/test_product_description.py](../../ai-service/test_product_description.py)

검증·보조 코드 · 283줄 · 14700바이트

검증/보조 파일 test_product_description.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 23행: `documents`
- 28행: `content_body`
- 33행: `build`
- 43행: `pdf_text`
- 48행: `TemplateTest`
- 49행: `test_shipped_template_is_valid_and_has_initial_sections`
- 57행: `test_quality_configuration_is_required_and_validated`
- 67행: `test_invalid_template_is_rejected`
- 78행: `ProviderConfigTest`
- 79행: `test_mode_must_be_explicit`
- 83행: `test_real_mode_rejects_missing_or_placeholder_key`
- 88행: `test_real_mode_never_falls_back_to_mock`
- 94행: `test_mock_mode`
- 98행: `AssembleTest`
- 99행: `setUp`
- 102행: `test_mock_document_is_labeled_and_sources_are_real_documents`
- 112행: `test_preflight_warnings_are_given_to_llm_and_listed_in_document`
- 121행: `test_document_text_stays_out_of_instructions`
- 128행: `test_invalid_llm_output_is_rejected`
- 153행: `RenderTest`
- 154행: `setUp`
- 157행: `test_pdf_has_korean_text_banner_and_page_numbers`
- 165행: `test_long_table_and_long_text_paginate_with_repeated_header`
- 177행: `test_template_only_change_is_applied_to_new_generation`
- 200행: `test_stored_snapshot_renders_same_after_template_changes`
- 206행: `test_invalid_document_or_format_raises_render_error`
- 213행: `FakeRequest`
- 214행: `__init__`
- 218행: `is_disconnected`
- 223행: `EndpointTest`
- 224행: `setUp`
- 229행: `call`
- 233행: `test_mock_generation`
- 239행: `test_real_mode_without_key_returns_configuration_error`
- 245행: `test_real_mode_with_key_reports_unimplemented_provider`
- 250행: `test_blocked_decision_is_rejected`
- 254행: `test_client_disconnect_cancels_the_provider_call`
- 257행: `Slow`
- 258행: `generate`
- 272행: `test_render_endpoint_returns_pdf`

## [ai-service/test_tc_generation.py](../../ai-service/test_tc_generation.py)

검증·보조 코드 · 240줄 · 15754바이트

검증/보조 파일 test_tc_generation.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 13행: `body`
- 21행: `draft`
- 28행: `TcTests`
- 29행: `test_agreement_is_optional_but_function_list_and_manual_are_required`
- 37행: `test_complete_coverage_and_prompt_budget`
- 54행: `test_request_output_reserve_controls_input_budget`
- 61행: `test_external_prompt_preview_matches_one_manual_model_call`
- 78행: `test_large_approved_menu_tree_fits_increased_context_without_truncation`
- 99행: `test_max_context_and_larger_chunks_fit_actual_tc_request_budget`
- 131행: `test_span_selection_preserves_original_and_rejects_unknown_id`
- 143행: `test_invented_evidence_is_rejected`
- 147행: `test_product_behavior_path_must_match_approved_tree`
- 154행: `test_three_realistic_warnings_fit_and_korean_quality_is_restored`
- 179행: `test_mock_result_has_no_verdict_and_is_marked`
- 193행: `test_real_provider_preserves_sources_and_forces_security_manual`
- 207행: `test_invalid_quality_pair_is_rejected`
- 213행: `test_document_availability_cases_are_policy_based_and_separate`
- 221행: `test_cache_is_invalidated_for_changed_source_or_model`
- 229행: `test_product_description_is_disabled_without_explicit_opt_in`

## [ai-service/verify_execution_live.py](../../ai-service/verify_execution_live.py)

검증·보조 코드 · 40줄 · 3312바이트

검증/보조 파일 verify_execution_live.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 10행: `main`
- 26행: `judge`

## [ai-service/verify_execution_neoflow.py](../../ai-service/verify_execution_neoflow.py)

검증·보조 코드 · 54줄 · 2849바이트

검증/보조 파일 verify_execution_neoflow.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 16행: `main`

## [ai-service/verify_execution_v3.py](../../ai-service/verify_execution_v3.py)

검증·보조 코드 · 58줄 · 4268바이트

검증/보조 파일 verify_execution_v3.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 13행: `Site`
- 14행: `log_message`
- 15행: `do_GET`
- 27행: `main`
- 40행: `judge`

## [ai-service/verify_read_spec_browser.py](../../ai-service/verify_read_spec_browser.py)

검증·보조 코드 · 42줄 · 2348바이트

검증/보조 파일 verify_read_spec_browser.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 9행: `main`
- 21행: `planner`
- 23행: `judge`

## [ai-service/web_menu.py](../../ai-service/web_menu.py)

제품 코드·설정·템플릿 · 38줄 · 2015바이트

관찰된 웹 구조를 자동 메뉴 생성 입력/출력 처리에 사용한다.

- 6행: `web_path`
- 15행: `structure`
- 24행: `anchor`

## [api-gateway/build.gradle.kts](../../api-gateway/build.gradle.kts)

제품 코드·설정·템플릿 · 13줄 · 346바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [api-gateway/src/main/java/com/autotest/api_gateway/Application.java](../../api-gateway/src/main/java/com/autotest/api_gateway/Application.java)

제품 코드·설정·템플릿 · 15줄 · 729바이트

api-gateway Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.

- 10행: `Application`
- 11행: `main`

## [api-gateway/src/main/java/com/autotest/api_gateway/ContainerDnsConfiguration.java](../../api-gateway/src/main/java/com/autotest/api_gateway/ContainerDnsConfiguration.java)

제품 코드·설정·템플릿 · 19줄 · 692바이트

컨테이너 재생성 이후 Gateway DNS 연결을 위한 설정.

- 10행: `ContainerDnsConfiguration`

## [api-gateway/src/main/java/com/autotest/api_gateway/GatewayExceptionHandler.java](../../api-gateway/src/main/java/com/autotest/api_gateway/GatewayExceptionHandler.java)

제품 코드·설정·템플릿 · 36줄 · 2162바이트

Reactive Gateway의 오류 응답·traceId 처리.

- 15행: `GatewayExceptionHandler`
- 18행: `handle`
- 31행: `hasCause`

## [api-gateway/src/main/resources/application.yml](../../api-gateway/src/main/resources/application.yml)

제품 코드·설정·템플릿 · 27줄 · 840바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [api-gateway/src/test/java/com/autotest/api_gateway/GatewayExceptionHandlerTest.java](../../api-gateway/src/test/java/com/autotest/api_gateway/GatewayExceptionHandlerTest.java)

검증·보조 코드 · 18줄 · 1096바이트

검증/보조 파일 GatewayExceptionHandlerTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `GatewayExceptionHandlerTest`

## [auth-service/build.gradle.kts](../../auth-service/build.gradle.kts)

제품 코드·설정·템플릿 · 8줄 · 357바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [auth-service/src/main/java/com/autotest/auth_service/Application.java](../../auth-service/src/main/java/com/autotest/auth_service/Application.java)

제품 코드·설정·템플릿 · 13줄 · 439바이트

auth-service Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.

- 8행: `Application`
- 9행: `main`

## [auth-service/src/main/resources/application.yml](../../auth-service/src/main/resources/application.yml)

제품 코드·설정·템플릿 · 14줄 · 266바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [build.gradle.kts](../../build.gradle.kts)

제품 코드·설정·템플릿 · 26줄 · 775바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [common-lib/build.gradle.kts](../../common-lib/build.gradle.kts)

제품 코드·설정·템플릿 · 10줄 · 229바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [common-lib/src/main/java/com/autotest/common_lib/Application.java](../../common-lib/src/main/java/com/autotest/common_lib/Application.java)

제품 코드·설정·템플릿 · 12줄 · 325바이트

common-lib Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.

- 7행: `Application`
- 8행: `main`

## [common-lib/src/main/java/com/autotest/common_lib/exception/BaseErrorCode.java](../../common-lib/src/main/java/com/autotest/common_lib/exception/BaseErrorCode.java)

제품 코드·설정·템플릿 · 10줄 · 208바이트

서비스 오류 코드·메시지·HTTP 상태 인터페이스.

- 5행: `BaseErrorCode`

## [common-lib/src/main/java/com/autotest/common_lib/exception/CommonErrorCode.java](../../common-lib/src/main/java/com/autotest/common_lib/exception/CommonErrorCode.java)

제품 코드·설정·템플릿 · 50줄 · 2838바이트

HTTP 상태별 공통 오류 코드.

- 5행: `CommonErrorCode`
- 42행: `getCode`
- 43행: `getMessage`
- 44행: `getStatus`
- 45행: `forStatus`

## [common-lib/src/main/java/com/autotest/common_lib/exception/CommonException.java](../../common-lib/src/main/java/com/autotest/common_lib/exception/CommonException.java)

제품 코드·설정·템플릿 · 13줄 · 495바이트

BaseErrorCode를 보관하는 공통 예외.

- 5행: `CommonException`
- 11행: `getErrorCode`

## [common-lib/src/main/java/com/autotest/common_lib/exception/ErrorResponse.java](../../common-lib/src/main/java/com/autotest/common_lib/exception/ErrorResponse.java)

제품 코드·설정·템플릿 · 8줄 · 282바이트

code/message/traceId 오류 응답.

- 3행: `ErrorResponse`
- 4행: `of`

## [common-lib/src/main/java/com/autotest/common_lib/exception/GlobalExceptionHandler.java](../../common-lib/src/main/java/com/autotest/common_lib/exception/GlobalExceptionHandler.java)

제품 코드·설정·템플릿 · 35줄 · 2105바이트

Servlet 서비스의 공통 예외·검증/HTTP 오류 변환·안전 응답과 traceId.

- 12행: `GlobalExceptionHandler`
- 15행: `handleCommon`
- 19행: `handleUnexpected`
- 28행: `respond`

## [common-lib/src/test/java/com/autotest/common_lib/exception/GlobalExceptionHandlerTest.java](../../common-lib/src/test/java/com/autotest/common_lib/exception/GlobalExceptionHandlerTest.java)

검증·보조 코드 · 30줄 · 2324바이트

검증/보조 파일 GlobalExceptionHandlerTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `GlobalExceptionHandlerTest`
- 12행: `Controller`
- 13행: `HTTP /unexpected`
- 14행: `HTTP /business`
- 15행: `HTTP /invalid`
- 16행: `HTTP /size`
- 17행: `HTTP /typed`

## [crawler-service/index.js](../../crawler-service/index.js)

제품 코드·설정·템플릿 · 50줄 · 2844바이트

로컬 브라우저 작업 API·수집/시험 공용 점유와 내부 토큰. 별도 과거 /crawl 라우트도 남아 있다.

- 8행: `internalAuth`
- 15행: `HTTP /api/v1/browser-jobs`
- 24행: `send`
- 38행: `HTTP /`
- 39행: `HTTP /crawl`

## [crawler-service/package-lock.json](../../crawler-service/package-lock.json)

제품 코드·설정·템플릿 · 865줄 · 31348바이트

Node 패키지 선언/잠금 파일. 잠금 파일은 설치 버전 근거이며 실행 코드가 아니다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/package.json](../../crawler-service/package.json)

제품 코드·설정·템플릿 · 9줄 · 338바이트

Node 패키지 선언/잠금 파일. 잠금 파일은 설치 버전 근거이며 실행 코드가 아니다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/Install.ps1](../../crawler-service/remote-agent/Install.ps1)

제품 코드·설정·템플릿 · 23줄 · 1167바이트

Windows 원격 실행기의 설치 또는 기동 스크립트. 서버 TC 실행은 현재 이 원격 채널을 사용하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/Start-Agent.ps1](../../crawler-service/remote-agent/Start-Agent.ps1)

제품 코드·설정·템플릿 · 9줄 · 385바이트

Windows 원격 실행기의 설치 또는 기동 스크립트. 서버 TC 실행은 현재 이 원격 채널을 사용하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/agent.js](../../crawler-service/remote-agent/agent.js)

제품 코드·설정·템플릿 · 63줄 · 3879바이트

원격 설정 읽기·heartbeat·작업 claim·probe/crawl 실행·결과 이벤트·중단 신호.

- 8행: `runJob`
- 42행: `main`

## [crawler-service/remote-agent/engine.js](../../crawler-service/remote-agent/engine.js)

제품 코드·설정·템플릿 · 97줄 · 5642바이트

수집 공통 URL 범위·위험 동작 제한·폼 로그인, walker를 통한 실제 수집 진입.

- 4행: `normalizedUrl`
- 12행: `glob`
- 16행: `scope`
- 31행: `visible`
- 38행: `failure`
- 40행: `login`
- 95행: `crawl`

## [crawler-service/remote-agent/engine.test.js](../../crawler-service/remote-agent/engine.test.js)

검증·보조 코드 · 74줄 · 6102바이트

검증/보조 파일 engine.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 6행: `options`

## [crawler-service/remote-agent/language.js](../../crawler-service/remote-agent/language.js)

제품 코드·설정·템플릿 · 47줄 · 3597바이트

한국어 우선 locale와 확인 가능한 언어 선택 UI, 이어 수집 언어 일관성.

- 2행: `inspectLanguage`
- 11행: `languagePolicy`
- 17행: `preferKorean`

## [crawler-service/remote-agent/language.test.js](../../crawler-service/remote-agent/language.test.js)

검증·보조 코드 · 43줄 · 4016바이트

검증/보조 파일 language.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 34행: `render`

## [crawler-service/remote-agent/navigation-failure.test.js](../../crawler-service/remote-agent/navigation-failure.test.js)

검증·보조 코드 · 63줄 · 5593바이트

검증/보조 파일 navigation-failure.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/navigation.js](../../crawler-service/remote-agent/navigation.js)

제품 코드·설정·템플릿 · 121줄 · 9858바이트

메뉴 관찰·계층·탭/그룹 펼치기·메뉴 이동 조작.

- 4행: `navigation`
- 26행: `finish`
- 89행: `clickNavigation`
- 108행: `expandNavigation`

## [crawler-service/remote-agent/probe.js](../../crawler-service/remote-agent/probe.js)

제품 코드·설정·템플릿 · 63줄 · 3919바이트

CONNECTION/LOGIN 검사와 인증서·HTTP·로그인 확인.

- 4행: `probe`
- 10행: `stop`

## [crawler-service/remote-agent/probe.test.js](../../crawler-service/remote-agent/probe.test.js)

검증·보조 코드 · 46줄 · 3305바이트

검증/보조 파일 probe.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/protocol.js](../../crawler-service/remote-agent/protocol.js)

제품 코드·설정·템플릿 · 36줄 · 1886바이트

원격 실행기 HTTP 요청과 암호화 채널 처리.

- 3행: `seal`
- 12행: `open`
- 19행: `request`

## [crawler-service/remote-agent/protocol.test.js](../../crawler-service/remote-agent/protocol.test.js)

검증·보조 코드 · 37줄 · 2069바이트

검증/보조 파일 protocol.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/resume.test.js](../../crawler-service/remote-agent/resume.test.js)

검증·보조 코드 · 21줄 · 1900바이트

검증/보조 파일 resume.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/retry.js](../../crawler-service/remote-agent/retry.js)

제품 코드·설정·템플릿 · 11줄 · 981바이트

SELF/SUBTREE 선택과 이동/캡처/실패 항목 포함 범위를 계산한다.

- 2행: `prefix`
- 3행: `retryPlan`

## [crawler-service/remote-agent/retry.test.js](../../crawler-service/remote-agent/retry.test.js)

검증·보조 코드 · 28줄 · 2838바이트

검증/보조 파일 retry.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/screen.js](../../crawler-service/remote-agent/screen.js)

제품 코드·설정·템플릿 · 77줄 · 5295바이트

프레임 관찰·본문/요소·로딩 상태·화면 식별값.

- 5행: `visibleFrames`
- 21행: `observe`
- 46행: `waitForScreen`
- 66행: `screenState`

## [crawler-service/remote-agent/screen.test.js](../../crawler-service/remote-agent/screen.test.js)

검증·보조 코드 · 90줄 · 7394바이트

검증/보조 파일 screen.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/smoke-e2e.js](../../crawler-service/remote-agent/smoke-e2e.js)

제품 코드·설정·템플릿 · 81줄 · 5385바이트

구성/보조 소스 smoke-e2e.js. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 7행: `main`
- 13행: `admin`

## [crawler-service/remote-agent/tls.test.js](../../crawler-service/remote-agent/tls.test.js)

검증·보조 코드 · 38줄 · 2415바이트

검증/보조 파일 tls.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/remote-agent/walker.js](../../crawler-service/remote-agent/walker.js)

제품 코드·설정·템플릿 · 258줄 · 19640바이트

수집 queue·메뉴 이동 재현·페이지 상태/캡처·언어·시간/개수 한도·이어 수집.

- 8행: `crawl`
- 15행: `stop`

## [crawler-service/tc-execution-fixture.js](../../crawler-service/tc-execution-fixture.js)

제품 코드·설정·템플릿 · 17줄 · 1777바이트

구성/보조 소스 tc-execution-fixture.js. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 6행: `auth`
- 8행: `HTTP /fixture/:mode`
- 15행: `HTTP /stop`

## [crawler-service/tc-execution.js](../../crawler-service/tc-execution.js)

제품 코드·설정·템플릿 · 239줄 · 21905바이트

TC별 Chromium 세션·관찰·활성 팝업 범위·단일/기본 조작·저장 계정 로그인·다운로드·범위 제한.

- 9행: `redact`
- 10행: `sameEntry`
- 11행: `publicUrl`
- 12행: `ready`
- 23행: `observe`
- 69행: `safeUrl`
- 74행: `open`
- 138행: `step`
- 213행: `register`
- 214행: `HTTP /api/v1/tc-browser/capabilities`
- 215행: `HTTP /api/v1/tc-browser/open`
- 219행: `HTTP /api/v1/tc-browser/:session/:operation`

## [crawler-service/tc-execution.test.js](../../crawler-service/tc-execution.test.js)

검증·보조 코드 · 127줄 · 13425바이트

검증/보조 파일 tc-execution.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/tc-login.test.js](../../crawler-service/tc-login.test.js)

검증·보조 코드 · 46줄 · 5140바이트

검증/보조 파일 tc-login.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [crawler-service/tc-session-state.test.js](../../crawler-service/tc-session-state.test.js)

검증·보조 코드 · 42줄 · 3675바이트

검증/보조 파일 tc-session-state.test.js. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [docker-compose.yml](../../docker-compose.yml)

제품 코드·설정·템플릿 · 187줄 · 6229바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/.oxlintrc.json](../../frontend/.oxlintrc.json)

제품 코드·설정·템플릿 · 9줄 · 253바이트

구성/보조 소스 .oxlintrc.json. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/index.html](../../frontend/index.html)

제품 코드·설정·템플릿 · 14줄 · 373바이트

구성/보조 소스 index.html. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/package-lock.json](../../frontend/package-lock.json)

제품 코드·설정·템플릿 · 2020줄 · 69714바이트

Node 패키지 선언/잠금 파일. 잠금 파일은 설치 버전 근거이며 실행 코드가 아니다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/package.json](../../frontend/package.json)

제품 코드·설정·템플릿 · 35줄 · 796바이트

Node 패키지 선언/잠금 파일. 잠금 파일은 설치 버전 근거이며 실행 코드가 아니다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/src/App.css](../../frontend/src/App.css)

제품 코드·설정·템플릿 · 387줄 · 26242바이트

화면 스타일·레이아웃·상태 색상/애니메이션 정의. 서버 수행 로직은 포함하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/src/App.tsx](../../frontend/src/App.tsx)

제품 코드·설정·템플릿 · 491줄 · 29190바이트

현재 01~07 화면 진입, 문서 업로드·조회·전달 상태와 선택한 TC 세트 전달을 관리한다.

- 78행: `App`
- 81행: `navigate`
- 115행: `updateAiDelivery`
- 119행: `updateDocument`
- 173행: `uploadRoles`
- 198행: `requestUpload`
- 214행: `continueUploadWithMissingDocuments`
- 221행: `loadSubmission`
- 249행: `retryAiDelivery`
- 266행: `startNewSet`
- 284행: `isReplaceable`

## [frontend/src/CapturedMenuTreePanel.tsx](../../frontend/src/CapturedMenuTreePanel.tsx)

제품 코드·설정·템플릿 · 57줄 · 4635바이트

수집 메뉴 구조를 화면에서 표시하는 보조 컴포넌트.

- 10행: `CapturedMenuTreePanel`
- 17행: `load`
- 27행: `render`

## [frontend/src/ExecutionConnectionTest.tsx](../../frontend/src/ExecutionConnectionTest.tsx)

제품 코드·설정·템플릿 · 135줄 · 13684바이트

로그인 필요 선택과 계정·TLS 설정을 포함한 연결/로그인 검사 팝업. 성공한 설정을 저장한다.

- 8행: `ExecutionConnectionTest`
- 24행: `request`
- 32행: `invalidate`
- 33행: `finish`
- 55행: `poll`
- 74행: `start`
- 93행: `open`

## [frontend/src/ExecutionReviewPanel.tsx](../../frontend/src/ExecutionReviewPanel.tsx)

제품 코드·설정·템플릿 · 57줄 · 9982바이트

종료한 실행의 사용자 판정·결함 상태·사유 저장, 선택 재시험 준비, 보고서 다운로드. TC 본문 편집과 다르다.

- 6행: `ExecutionReviewPanel`
- 24행: `action`
- 30행: `inspect`
- 31행: `download`

## [frontend/src/ExecutorStatus.tsx](../../frontend/src/ExecutorStatus.tsx)

제품 코드·설정·템플릿 · 59줄 · 5044바이트

실행기 사용 가능/사용 중 버튼과 점유 작업 조회·중단 팝업.

- 6행: `title`
- 8행: `ExecutorStatus`
- 30행: `stop`

## [frontend/src/FunctionalMenuPanel.tsx](../../frontend/src/FunctionalMenuPanel.tsx)

제품 코드·설정·템플릿 · 143줄 · 24823바이트

외부 LLM 제품 프롬프트·JSON 가져오기·행 포함/배치/분리/편집·승인·근거와 캡처 조회.

- 19행: `observedBranches`
- 20행: `renderBranches`
- 22행: `FunctionalMenuPanel`
- 32행: `needsPlacement`
- 36행: `includeAll`
- 47행: `apply`
- 55행: `action`
- 56행: `makePrompt`
- 57행: `start`
- 58행: `copyPrompt`
- 59행: `savePrompt`
- 60행: `save`
- 61행: `update`
- 62행: `pathChange`
- 63행: `download`
- 64행: `showEvidence`

## [frontend/src/MenuTcPanel.tsx](../../frontend/src/MenuTcPanel.tsx)

제품 코드·설정·템플릿 · 136줄 · 18340바이트

메뉴 업로드·승인 메뉴 선택·기능구분 선택·템플릿 TC 생성·검색·상세·Excel/JSON 다운로드. TC 본문 저장용 편집 API는 호출하지 않는다.

- 18행: `MenuTcPanel`
- 62행: `action`
- 77행: `saveBlob`

## [frontend/src/PlaywrightSetupPanel.tsx](../../frontend/src/PlaywrightSetupPanel.tsx)

제품 코드·설정·템플릿 · 290줄 · 25043바이트

화면 수집 접속·자동 로그인 설정과 저장·조회. 원격 수집 패널을 연결한다.

- 36행: `PlaywrightSetupPanel`
- 51행: `invalidate`
- 104행: `draft`
- 108행: `runCheck`
- 114행: `report`
- 124행: `save`

## [frontend/src/PreflightWarningDetails.tsx](../../frontend/src/PreflightWarningDetails.tsx)

제품 코드·설정·템플릿 · 151줄 · 8846바이트

사전 점검 경고·추출 진단 상세 표시.

- 36행: `diagnosticFor`
- 40행: `detailsFor`
- 130행: `PreflightWarningDetails`

## [frontend/src/ProductDescriptionPanel.tsx](../../frontend/src/ProductDescriptionPanel.tsx)

제품 코드·설정·템플릿 · 271줄 · 13213바이트

제품 설명 생성·중단·이력 UI. 현재 App.tsx는 이 컴포넌트를 import/render하지 않는다.

- 43행: `readError`
- 52행: `ProductDescriptionPanel`
- 92행: `tick`
- 107행: `check`
- 122행: `post`
- 139행: `start`
- 144행: `download`

## [frontend/src/RemoteCrawlPanel.tsx](../../frontend/src/RemoteCrawlPanel.tsx)

제품 코드·설정·템플릿 · 213줄 · 23386바이트

로컬/원격 수집 작업·실행기 등록·진행·캡처·실패 사유·이어 수집 UI.

- 22행: `storedClock`
- 25행: `storeClock`
- 27행: `RemoteCrawlPanel`
- 106행: `action`
- 112행: `register`
- 120행: `downloadConfig`
- 126행: `showPage`

## [frontend/src/RunProgress.tsx](../../frontend/src/RunProgress.tsx)

제품 코드·설정·템플릿 · 35줄 · 3478바이트

단조 증가 소요 시간, 상태·진행 막대·지표 표시. 서버 수집/시험 동작 자체를 수행하지 않는다.

- 3행: `duration`
- 8행: `RunProgress`

## [frontend/src/SkippedMenuRetry.tsx](../../frontend/src/SkippedMenuRetry.tsx)

제품 코드·설정·템플릿 · 41줄 · 4350바이트

미수집 메뉴를 SELF/SUBTREE 범위와 체크박스로 선택해 재수집을 요청한다.

- 3행: `prefix`
- 5행: `Choice`
- 10행: `SkippedMenuRetry`
- 16행: `toggle`

## [frontend/src/TcGenerationPanel.tsx](../../frontend/src/TcGenerationPanel.tsx)

제품 코드·설정·템플릿 · 484줄 · 36612바이트

과거 LLM TC·메뉴트리 검토 UI. 현재 App.tsx는 이 컴포넌트를 import/render하지 않는다.

- 46행: `TcGenerationPanel`
- 125행: `generateMenuTree`
- 154행: `updateMenuNode`
- 156행: `editMenuTitle`
- 161행: `toggleMenuNode`
- 166행: `moveMenuNode`
- 188행: `approveMenuTree`
- 202행: `copyMenuPrompt`
- 209행: `importMenuResponse`
- 226행: `uploadMenuTree`
- 243행: `loadExternalPrompt`
- 256행: `copyExternalPrompt`
- 265행: `cancel`
- 276행: `download`
- 438행: `flattenMenuNodes`
- 439행: `recalculateMenuPaths`
- 445행: `findMenuNode`
- 451행: `MenuTreeEditor`
- 473행: `isMenuDescendant`
- 476행: `findMenuParentSection`

## [frontend/src/TestExecutionPanel.tsx](../../frontend/src/TestExecutionPanel.tsx)

제품 코드·설정·템플릿 · 166줄 · 28467바이트

TC 세트 선택·접속·메모·실행 범위·준비/시작/중단/이어 준비·진행·결과 상세.

- 26행: `TestExecutionPanel`
- 73행: `action`
- 93행: `bucket`
- 108행: `showRun`

## [frontend/src/apiFetch.ts](../../frontend/src/apiFetch.ts)

제품 코드·설정·템플릿 · 43줄 · 2919바이트

네트워크/HTTP 실패를 ApiError와 승인된 사용자 메시지로 변환한다.

- 14행: `ApiError`
- 20행: `responseError`
- 28행: `apiFetch`

## [frontend/src/browserCheckMessages.ts](../../frontend/src/browserCheckMessages.ts)

제품 코드·설정·템플릿 · 21줄 · 2217바이트

연결·로그인·수집 오류 코드의 사용자 메시지.

- 19행: `browserCheckMessage`

## [frontend/src/components/ui/tabs.tsx](../../frontend/src/components/ui/tabs.tsx)

제품 코드·설정·템플릿 · 17줄 · 982바이트

Radix Tabs를 감싼 화면 탭 컴포넌트.

- 5행: `Tabs`
- 8행: `TabsList`
- 11행: `TabsTrigger`
- 14행: `TabsContent`

## [frontend/src/index.css](../../frontend/src/index.css)

제품 코드·설정·템플릿 · 21줄 · 659바이트

화면 스타일·레이아웃·상태 색상/애니메이션 정의. 서버 수행 로직은 포함하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/src/main.tsx](../../frontend/src/main.tsx)

제품 코드·설정·템플릿 · 16줄 · 459바이트

React root에 App을 마운트하고 전역 스타일·폰트 파일을 불러온다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/src/serviceErrorMessages.ts](../../frontend/src/serviceErrorMessages.ts)

제품 코드·설정·템플릿 · 150줄 · 15087바이트

서버 오류 코드별 프런트 사용자 메시지 목록.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/src/tcClassification.ts](../../frontend/src/tcClassification.ts)

제품 코드·설정·템플릿 · 11줄 · 604바이트

사용자 선택·기존 구분 우선, 로그인/CRUD/주요기능 제안. Java 분류와 별도 구현이다.

- 4행: `classifyTcFeature`

## [frontend/src/useExecutionEvidence.ts](../../frontend/src/useExecutionEvidence.ts)

제품 코드·설정·템플릿 · 21줄 · 1341바이트

실행 상세의 evidenceUrl을 요청하고 선택 변경 시 이전 요청을 취소한다.

- 5행: `useExecutionEvidence`

## [frontend/src/workspace.css](../../frontend/src/workspace.css)

제품 코드·설정·템플릿 · 238줄 · 21425바이트

화면 스타일·레이아웃·상태 색상/애니메이션 정의. 서버 수행 로직은 포함하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/tsconfig.app.json](../../frontend/tsconfig.app.json)

제품 코드·설정·템플릿 · 27줄 · 681바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/tsconfig.json](../../frontend/tsconfig.json)

제품 코드·설정·템플릿 · 8줄 · 126바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/tsconfig.node.json](../../frontend/tsconfig.node.json)

제품 코드·설정·템플릿 · 24줄 · 581바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [frontend/vite.config.ts](../../frontend/vite.config.ts)

제품 코드·설정·템플릿 · 13줄 · 249바이트

구성/보조 소스 vite.config.ts. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [gradle/wrapper/gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties)

제품 코드·설정·템플릿 · 8줄 · 260바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [gradlew.bat](../../gradlew.bat)

제품 코드·설정·템플릿 · 95줄 · 2937바이트

구성/보조 소스 gradlew.bat. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [report-service/build.gradle.kts](../../report-service/build.gradle.kts)

제품 코드·설정·템플릿 · 8줄 · 357바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [report-service/src/main/java/com/autotest/report_service/Application.java](../../report-service/src/main/java/com/autotest/report_service/Application.java)

제품 코드·설정·템플릿 · 13줄 · 441바이트

report-service Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.

- 8행: `Application`
- 9행: `main`

## [report-service/src/main/resources/application.yml](../../report-service/src/main/resources/application.yml)

제품 코드·설정·템플릿 · 14줄 · 268바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/audit-sample3-artifacts.py](../../scripts/audit-sample3-artifacts.py)

검증·보조 코드 · 45줄 · 3069바이트

검증/보조 파일 audit-sample3-artifacts.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/build-tc-template-v3.py](../../scripts/build-tc-template-v3.py)

검증·보조 코드 · 41줄 · 4346바이트

검증/보조 파일 build-tc-template-v3.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 14행: `row`

## [scripts/compare-tc-read-spec.py](../../scripts/compare-tc-read-spec.py)

검증·보조 코드 · 31줄 · 1752바이트

검증/보조 파일 compare-tc-read-spec.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/crawl-diagnostic-runtime.cjs](../../scripts/crawl-diagnostic-runtime.cjs)

검증·보조 코드 · 18줄 · 2229바이트

검증/보조 파일 crawl-diagnostic-runtime.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/diagnose-crawl.cjs](../../scripts/diagnose-crawl.cjs)

검증·보조 코드 · 30줄 · 2874바이트

검증/보조 파일 diagnose-crawl.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/neoflow-program-1-4.cjs](../../scripts/neoflow-program-1-4.cjs)

검증·보조 코드 · 57줄 · 5531바이트

검증/보조 파일 neoflow-program-1-4.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 9행: `api`
- 13행: `save`

## [scripts/package-crawl-agent.ps1](../../scripts/package-crawl-agent.ps1)

검증·보조 코드 · 17줄 · 1283바이트

검증/보조 파일 package-crawl-agent.ps1. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/package-execution-v3.cjs](../../scripts/package-execution-v3.cjs)

검증·보조 코드 · 29줄 · 3484바이트

검증/보조 파일 package-execution-v3.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/package-sample3-validation.cjs](../../scripts/package-sample3-validation.cjs)

검증·보조 코드 · 130줄 · 17203바이트

검증/보조 파일 package-sample3-validation.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `main`
- 122행: `walk`

## [scripts/prepare-sample3-reviewed-menu.cjs](../../scripts/prepare-sample3-reviewed-menu.cjs)

검증·보조 코드 · 32줄 · 5590바이트

검증/보조 파일 prepare-sample3-reviewed-menu.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 13행: `ref`
- 14행: `objectParticle`

## [scripts/regenerate-neoflow-menu-audit.cjs](../../scripts/regenerate-neoflow-menu-audit.cjs)

검증·보조 코드 · 82줄 · 9291바이트

검증/보조 파일 regenerate-neoflow-menu-audit.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 57행: `normalized`
- 63행: `source`

## [scripts/run-sample3-pipeline.cjs](../../scripts/run-sample3-pipeline.cjs)

검증·보조 코드 · 79줄 · 7266바이트

검증/보조 파일 run-sample3-pipeline.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 9행: `api`
- 10행: `main`
- 14행: `save`

## [scripts/summarize-current-validation.py](../../scripts/summarize-current-validation.py)

검증·보조 코드 · 38줄 · 2730바이트

검증/보조 파일 summarize-current-validation.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-api-errors.mjs](../../scripts/verify-api-errors.mjs)

검증·보조 코드 · 29줄 · 2676바이트

검증/보조 파일 verify-api-errors.mjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-dev6-artifacts.py](../../scripts/verify-dev6-artifacts.py)

검증·보조 코드 · 43줄 · 2767바이트

검증/보조 파일 verify-dev6-artifacts.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-dev6-selection.py](../../scripts/verify-dev6-selection.py)

검증·보조 코드 · 21줄 · 902바이트

검증/보조 파일 verify-dev6-selection.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `main`

## [scripts/verify-execution-live.py](../../scripts/verify-execution-live.py)

검증·보조 코드 · 76줄 · 3596바이트

검증/보조 파일 verify-execution-live.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 17행: `Site`
- 18행: `log_message`
- 21행: `do_GET`
- 31행: `main`
- 44행: `recorded_judge`

## [scripts/verify-execution-preparation-ui.cjs](../../scripts/verify-execution-preparation-ui.cjs)

검증·보조 코드 · 42줄 · 2990바이트

검증/보조 파일 verify-execution-preparation-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-executor-status-ui.cjs](../../scripts/verify-executor-status-ui.cjs)

검증·보조 코드 · 27줄 · 2541바이트

검증/보조 파일 verify-executor-status-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-frontend.cjs](../../scripts/verify-frontend.cjs)

검증·보조 코드 · 168줄 · 15374바이트

검증/보조 파일 verify-frontend.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 18행: `view`
- 19행: `submission`

## [scripts/verify-initial-entry-live.cjs](../../scripts/verify-initial-entry-live.cjs)

검증·보조 코드 · 19줄 · 1534바이트

검증/보조 파일 verify-initial-entry-live.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-login-route-ui.cjs](../../scripts/verify-login-route-ui.cjs)

검증·보조 코드 · 33줄 · 3406바이트

검증/보조 파일 verify-login-route-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 10행: `row`

## [scripts/verify-menu-selection-ui.cjs](../../scripts/verify-menu-selection-ui.cjs)

검증·보조 코드 · 58줄 · 5901바이트

검증/보조 파일 verify-menu-selection-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 12행: `row`

## [scripts/verify-menu-tc-samples.py](../../scripts/verify-menu-tc-samples.py)

검증·보조 코드 · 63줄 · 3866바이트

검증/보조 파일 verify-menu-tc-samples.py. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-menu-tc-ui.cjs](../../scripts/verify-menu-tc-ui.cjs)

검증·보조 코드 · 99줄 · 8568바이트

검증/보조 파일 verify-menu-tc-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-pipeline-runtime.cjs](../../scripts/verify-pipeline-runtime.cjs)

검증·보조 코드 · 42줄 · 3915바이트

검증/보조 파일 verify-pipeline-runtime.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-read-spec-browser.cjs](../../scripts/verify-read-spec-browser.cjs)

검증·보조 코드 · 41줄 · 2609바이트

검증/보조 파일 verify-read-spec-browser.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 13행: `HTTP /fixture/:mode/:screen`

## [scripts/verify-run-progress.cjs](../../scripts/verify-run-progress.cjs)

검증·보조 코드 · 70줄 · 9939바이트

검증/보조 파일 verify-run-progress.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 27행: `view`
- 38행: `seconds`

## [scripts/verify-sample3-dropdown.cjs](../../scripts/verify-sample3-dropdown.cjs)

검증·보조 코드 · 18줄 · 2142바이트

검증/보조 파일 verify-sample3-dropdown.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-sample3-reference.cjs](../../scripts/verify-sample3-reference.cjs)

검증·보조 코드 · 36줄 · 4483바이트

검증/보조 파일 verify-sample3-reference.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 7행: `main`

## [scripts/verify-sample3-result-ui.cjs](../../scripts/verify-sample3-result-ui.cjs)

검증·보조 코드 · 14줄 · 1650바이트

검증/보조 파일 verify-sample3-result-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-targeted-retry-ui.cjs](../../scripts/verify-targeted-retry-ui.cjs)

검증·보조 코드 · 11줄 · 2597바이트

검증/보조 파일 verify-targeted-retry-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 4행: `App`

## [scripts/verify-tc-classification.mjs](../../scripts/verify-tc-classification.mjs)

검증·보조 코드 · 21줄 · 1083바이트

검증/보조 파일 verify-tc-classification.mjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [scripts/verify-test-execution-ui.cjs](../../scripts/verify-test-execution-ui.cjs)

검증·보조 코드 · 182줄 · 17761바이트

검증/보조 파일 verify-test-execution-ui.cjs. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 15행: `view`

## [settings.gradle.kts](../../settings.gradle.kts)

제품 코드·설정·템플릿 · 3줄 · 156바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [setup.ps1](../../setup.ps1)

제품 코드·설정·템플릿 · 115줄 · 3473바이트

과거 초기 생성 스크립트. 현재 소스를 덮어쓰고 MinIO 구성을 덧붙일 수 있으므로 현재 설치 절차로 사용하지 않는다.

- 24행: `HTTP /`
- 56행: `HTTP /`
- 57행: `HTTP /crawl`

## [sites/postman-ui-preview/dist/index.html](../../sites/postman-ui-preview/dist/index.html)

제품 코드·설정·템플릿 · 2770줄 · 108015바이트

구성/보조 소스 index.html. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 1890행: `selectNode`
- 1891행: `render`
- 2508행: `parseState`
- 2564행: `persistSize`

## [test-execution-service/build.gradle.kts](../../test-execution-service/build.gradle.kts)

제품 코드·설정·템플릿 · 8줄 · 357바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-execution-service/src/main/java/com/autotest/test_execution_service/Application.java](../../test-execution-service/src/main/java/com/autotest/test_execution_service/Application.java)

제품 코드·설정·템플릿 · 13줄 · 449바이트

test-execution-service Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.

- 8행: `Application`
- 9행: `main`

## [test-execution-service/src/main/resources/application.yml](../../test-execution-service/src/main/resources/application.yml)

제품 코드·설정·템플릿 · 14줄 · 276바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/build.gradle.kts](../../test-management-service/build.gradle.kts)

제품 코드·설정·템플릿 · 66줄 · 3216바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/java/com/autotest/test_management_service/Application.java](../../test-management-service/src/main/java/com/autotest/test_management_service/Application.java)

제품 코드·설정·템플릿 · 14줄 · 425바이트

test-management-service Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.

- 9행: `Application`
- 10행: `main`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/CapturedMenuTree.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/CapturedMenuTree.java)

제품 코드·설정·템플릿 · 87줄 · 5648바이트

수집된 메뉴 관찰을 표시용 계층 구조로 조립한다.

- 13행: `CapturedMenuTree`
- 15행: `Page`
- 20행: `build`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/CrawlAgentWire.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/CrawlAgentWire.java)

제품 코드·설정·템플릿 · 73줄 · 4233바이트

원격 에이전트 채널 데이터의 암호화/인증 처리.

- 20행: `CrawlAgentWire`
- 28행: `Envelope`
- 29행: `Session`
- 30행: `toString`
- 32행: `requestAad`
- 35행: `seal`
- 42행: `unseal`
- 50행: `open`
- 65행: `reply`
- 69행: `unauthorized`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/CrawlAutomation.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/CrawlAutomation.java)

제품 코드·설정·템플릿 · 96줄 · 5901바이트

수집 기본값과 URL/범위/자동 로그인/화면 수·깊이·시간 한도 검증.

- 11행: `CrawlAutomation`
- 19행: `defaults`
- 24행: `normalized`
- 32행: `validate`
- 66행: `webUrl`
- 81행: `origin`
- 88행: `text`
- 89행: `list`
- 92행: `invalid`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/LocalCrawlDispatcher.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/LocalCrawlDispatcher.java)

제품 코드·설정·템플릿 · 31줄 · 1686바이트

로컬 작업을 crawler API로 전달하고 기존 수집 작업 채널과 연결한다.

- 14행: `LocalCrawlDispatcher`
- 19행: `dispatch`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/PlaywrightCredentialCipher.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/PlaywrightCredentialCipher.java)

제품 코드·설정·템플릿 · 76줄 · 4103바이트

저장 비밀번호의 암복호화와 키 파일 관리.

- 21행: `PlaywrightCredentialCipher`
- 28행: `key`
- 46행: `encrypt`
- 61행: `decrypt`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/PlaywrightSetupService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/PlaywrightSetupService.java)

제품 코드·설정·템플릿 · 281줄 · 20217바이트

문서·사전 점검·소유권을 확인하고 접속 설정·암호화 계정·버전·대상별 TLS 예외를 관리한다.

- 23행: `PlaywrightSetupService`
- 48행: `error`
- 52행: `Verified`
- 56행: `Prepared`
- 57행: `toString`
- 87행: `verifyReadySubmission`
- 121행: `trustedTargetCertificate`
- 131행: `configurationVersion`
- 135행: `get`
- 152행: `save`
- 213행: `clean`
- 215행: `SavedLogin`
- 217행: `loginOrigin`
- 221행: `readAutomation`
- 229행: `writeAutomation`
- 234행: `validate`
- 264행: `length`
- 266행: `SetupRequest`
- 274행: `toString`
- 276행: `SetupView`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/RemoteCrawlService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/crawler/RemoteCrawlService.java)

제품 코드·설정·템플릿 · 407줄 · 38376바이트

실행기 등록·작업 발행/선점/lease·수집 이벤트·페이지 보존·중단·SELF/SUBTREE 이어 수집.

- 25행: `RemoteCrawlService`
- 42행: `error`
- 45행: `Agent`
- 46행: `Provisioned`
- 47행: `toString`
- 49행: `agents`
- 58행: `provision`
- 67행: `revoke`
- 74행: `seen`
- 75행: `owner`
- 80행: `recover`
- 86행: `start`
- 89행: `start`
- 92행: `resume`
- 95행: `retry`
- 101행: `start`
- 104행: `start`
- 170행: `status`
- 200행: `PageSummary`
- 201행: `menuTree`
- 210행: `cancel`
- 214행: `screenshot`
- 223행: `evidence`
- 236행: `claim`
- 239행: `claim`
- 242행: `claim`
- 258행: `claimJob`
- 280행: `Pending`
- 281행: `toString`
- 283행: `worker`
- 340행: `Active`
- 341행: `resumeIdentity`
- 342행: `elapsed`
- 343행: `storePage`
- 385행: `permitted`
- 401행: `glob`
- 404행: `json`
- 405행: `parse`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionException.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionException.java)

제품 코드·설정·템플릿 · 23줄 · 643바이트

제품 설명 작업 계층 ProductDescriptionException.java: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.

- 5행: `ProductDescriptionException`
- 15행: `status`
- 19행: `code`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionGateway.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionGateway.java)

제품 코드·설정·템플릿 · 44줄 · 1516바이트

제품 설명 작업 계층 ProductDescriptionGateway.java: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.

- 9행: `ProductDescriptionGateway`
- 17행: `SourceDocument`
- 20행: `ContentCall`
- 24행: `ContentResult`
- 28행: `ChunkProgress`
- 31행: `GatewayException`
- 39행: `code`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionJob.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionJob.java)

제품 코드·설정·템플릿 · 47줄 · 1621바이트

제품 설명 작업 계층 ProductDescriptionJob.java: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.

- 7행: `ProductDescriptionJob`
- 39행: `active`
- 43행: `resumable`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionJobRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionJobRepository.java)

제품 코드·설정·템플릿 · 198줄 · 10871바이트

제품 설명 작업 계층 ProductDescriptionJobRepository.java: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.

- 17행: `ProductDescriptionJobRepository`
- 43행: `insert`
- 51행: `find`
- 55행: `findActive`
- 60행: `findLatest`
- 65행: `findLatestCompleted`
- 70행: `findActiveForMember`
- 75행: `findResumable`
- 81행: `markContentReady`
- 91행: `markCompleted`
- 100행: `markFailed`
- 109행: `cancel`
- 118행: `updateProgress`
- 125행: `resumeContent`
- 134행: `touch`
- 141행: `beginRerender`
- 152행: `DeliveryVerdict`
- 155행: `findDeliveryVerdict`
- 163행: `StoredContent`
- 166행: `loadContent`
- 174행: `recoverStale`
- 179행: `recoverAllActive`
- 183행: `recover`
- 194행: `first`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionService.java)

제품 코드·설정·템플릿 · 314줄 · 16544바이트

제품 설명 작업 계층 ProductDescriptionService.java: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.

- 39행: `ProductDescriptionService`
- 43행: `requireGenerationEnabled`
- 89행: `start`
- 126행: `status`
- 132행: `activeForMember`
- 137행: `cancel`
- 147행: `rerender`
- 169행: `download`
- 185행: `view`
- 192행: `ownedJob`
- 198행: `authorize`
- 206행: `lockOwned`
- 214행: `requireOwner`
- 221행: `verify`
- 261행: `decisionOf`
- 272행: `generationGateAllows`
- 282행: `Verified`
- 285행: `StartResult`
- 288행: `Download`
- 291행: `StatusView`
- 294행: `JobView`
- 299행: `from`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionWorker.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionWorker.java)

제품 코드·설정·템플릿 · 262줄 · 13467바이트

제품 설명 작업 계층 ProductDescriptionWorker.java: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.

- 41행: `ProductDescriptionWorker`
- 93행: `submit`
- 122행: `done`
- 141행: `abort`
- 208행: `generateContent`
- 218행: `contentCall`
- 230행: `preflightWarnings`
- 243행: `readTree`
- 251행: `fail`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/service/AiDocumentDeliveryService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/service/AiDocumentDeliveryService.java)

제품 코드·설정·템플릿 · 212줄 · 11183바이트

문서 접수 요청·Jev 결과 수신·digest 저장·전달 성공/실패/차단·재시도 상태.

- 27행: `AiDocumentDeliveryService`
- 57행: `deliverIfReady`
- 119행: `getStatus`
- 142행: `DeliveryResult`
- 161행: `AiDocumentRequest`
- 164행: `AiDocument`
- 166행: `from`
- 172행: `AiDocumentReceipt`
- 176행: `recordBlocked`
- 200행: `parsePreflight`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/service/DocumentDigest.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/service/DocumentDigest.java)

제품 코드·설정·템플릿 · 35줄 · 1357바이트

문서 입력 버전 동일성 확인용 digest.

- 13행: `DocumentDigest`
- 17행: `of`
- 26행: `sha256`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/service/FileTypeResolver.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/service/FileTypeResolver.java)

제품 코드·설정·템플릿 · 44줄 · 2480바이트

파일 확장자·MIME 기반 형식 확인.

- 11행: `FileTypeResolver`
- 18행: `resolve`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/service/S3CleanupOutboxWorker.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/service/S3CleanupOutboxWorker.java)

제품 코드·설정·템플릿 · 42줄 · 1786바이트

삭제 예약 원본을 참조 여부 확인 후 비동기로 정리한다.

- 12행: `S3CleanupOutboxWorker`
- 27행: `processPending`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/service/SubmissionPersistenceService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/service/SubmissionPersistenceService.java)

제품 코드·설정·템플릿 · 212줄 · 10727바이트

세트/문서 DB 트랜잭션·필수 2역할·교체 조건·전달 claim과 45초 lease.

- 24행: `SubmissionPersistenceService`
- 48행: `saveUploaded`
- 53행: `findById`
- 58행: `saveDocument`
- 65행: `replaceFailedDocument`
- 99행: `scheduleCleanup`
- 104행: `findDocumentsBySubmissionId`
- 109행: `hasDocumentForRole`
- 114행: `refreshSetStatus`
- 121행: `claimAiDelivery`
- 146행: `refreshSetStatus`
- 163행: `hasAllReadyDocuments`
- 180행: `deliveryDocuments`
- 187행: `isAiDeliveryBlocked`
- 195행: `isReplacementClosed`
- 206행: `DeliveryClaim`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/service/SubmissionService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/service/SubmissionService.java)

제품 코드·설정·템플릿 · 273줄 · 13418바이트

파일 형식·크기 검사, S3 원본 저장·파싱·역할별 추가/실패 교체·실패 정리.

- 37행: `SubmissionService`
- 49행: `submit`
- 53행: `submit`
- 84행: `uploadAdditional`
- 122행: `replaceFailedDocument`
- 178행: `findDocumentsBySubmissionId`
- 182행: `prepare`
- 206행: `store`
- 216행: `document`
- 233행: `cleanupStoredFile`
- 248행: `findById`
- 252행: `safeFailureReason`
- 264행: `PreparedFile`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/storage/FileStoragePort.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/storage/FileStoragePort.java)

제품 코드·설정·템플릿 · 16줄 · 594바이트

원본 저장/조회/삭제의 애플리케이션 포트.

- 7행: `FileStoragePort`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/AuthenticationTcContext.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/AuthenticationTcContext.java)

제품 코드·설정·템플릿 · 77줄 · 5149바이트

문서/수집/접속 설정으로 인증 필요·잠금 정책 후보를 구성하고 로그인 기능이 없으면 합성 로그인 기능을 추가한다.

- 9행: `AuthenticationTcContext`
- 11행: `loginFeature`
- 14행: `applySetup`
- 25행: `detect`
- 61행: `prepare`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/ExecutionReadinessService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/ExecutionReadinessService.java)

제품 코드·설정·템플릿 · 57줄 · 5890바이트

AI/브라우저 프로토콜·설치 모델·Jev 설정·점유·지원 명세 버전을 검사한다. 모델 품질 시험이나 추론 성공 검사는 아니다.

- 16행: `ExecutionReadinessService`
- 22행: `probe`
- 27행: `status`
- 46행: `requireReady`
- 47행: `requireSpecifications`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/ExecutionReviewService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/ExecutionReviewService.java)

제품 코드·설정·템플릿 · 83줄 · 9268바이트

자동 판정 보존·사용자 판정 이력·결함 상태·선택 재시험/이어 준비·Markdown 초안.

- 14행: `ExecutionReviewService`
- 17행: `invalid`
- 18행: `closed`
- 25행: `review`
- 41행: `retry`
- 51행: `resume`
- 66행: `report`
- 81행: `clean`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/FunctionalMenuExcel.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/FunctionalMenuExcel.java)

제품 코드·설정·템플릿 · 79줄 · 8544바이트

포함된 구조/기능 행을 메뉴트리 workbook으로 출력한다.

- 12행: `FunctionalMenuExcel`
- 13행: `Group`
- 14행: `flatten`
- 15행: `export`
- 76행: `cell`
- 77행: `lines`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/FunctionalMenuService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/FunctionalMenuService.java)

제품 코드·설정·템플릿 · 250줄 · 31723바이트

문서·수집 증거 고정, 웹 구조·메뉴 작업·프롬프트·가져오기·검토 저장·승인·Excel·인증 문맥.

- 27행: `FunctionalMenuService`
- 41행: `recover`
- 42행: `close`
- 43행: `error`
- 44행: `owner`
- 45행: `digest`
- 46행: `parse`
- 47행: `snapshot`
- 103행: `chunks`
- 109행: `start`
- 129행: `request`
- 130행: `validateGenerated`
- 137행: `checkpoint`
- 145행: `post`
- 154행: `ensureMenus`
- 174행: `stored`
- 179행: `status`
- 198행: `authentication`
- 215행: `validationSnapshot`
- 218행: `prompt`
- 219행: `imported`
- 224행: `evidence`
- 225행: `imported`
- 230행: `edit`
- 243행: `cancel`
- 244행: `download`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/FunctionalMenuValidator.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/FunctionalMenuValidator.java)

제품 코드·설정·템플릿 · 127줄 · 12297바이트

메뉴 경로·행·근거·기능명/설명 품질과 포함·배치 상태를 검사·정규화한다.

- 13행: `FunctionalMenuValidator`
- 16행: `invalid`
- 17행: `validate`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuPlacementRecovery.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuPlacementRecovery.java)

제품 코드·설정·템플릿 · 39줄 · 2358바이트

검증 가능한 관찰/문서 경로 근거로 배치 후보를 복원한다. 의미적 기능 정확성 확정과 다르다.

- 8행: `MenuPlacementRecovery`
- 9행: `Match`
- 10행: `find`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcCli.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcCli.java)

제품 코드·설정·템플릿 · 33줄 · 2599바이트

샘플 메뉴 workbook의 시트별 생성 결과를 파일로 출력하는 명령행 진입점.

- 9행: `MenuTcCli`
- 10행: `main`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcGenerator.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcGenerator.java)

제품 코드·설정·템플릿 · 142줄 · 12268바이트

버전 관리 템플릿을 코드로 확장한다. 분류·원본 템플릿·안정 ID·내용 digest·초안 결과를 생성한다. 생성 시 LLM을 호출하지 않는다.

- 15행: `MenuTcGenerator`
- 26행: `hash`
- 30행: `invalid`
- 33행: `classify`
- 45행: `generate`
- 85행: `expand`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcService.java)

제품 코드·설정·템플릿 · 110줄 · 9592바이트

소유권·승인·문서/메뉴 revision 확인, 기능/인증 입력 준비, TC 세트 저장·재사용·조회·다운로드.

- 15행: `MenuTcService`
- 27행: `conflict`
- 28행: `owner`
- 33행: `parse`
- 34행: `features`
- 51행: `source`
- 56행: `requireApproved`
- 63행: `create`
- 67행: `create`
- 91행: `status`
- 104행: `download`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcWorkbook.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTcWorkbook.java)

제품 코드·설정·템플릿 · 121줄 · 11790바이트

샘플 CLI의 다중 메뉴 시트 읽기와 TestCase 16열/실행 정보 20열/생성 정보 Excel 출력.

- 14행: `MenuTcWorkbook`
- 17행: `text`
- 20행: `read`
- 70행: `export`
- 106행: `join`
- 107행: `cell`
- 108행: `fitHeight`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTreeService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/MenuTreeService.java)

제품 코드·설정·템플릿 · 328줄 · 23176바이트

기존 메뉴트리 승인/외부 응답 경로. 현재 functional-menu 경로와 구별한다.

- 36행: `MenuTreeService`
- 65행: `error`
- 69행: `readyDocuments`
- 96행: `status`
- 127행: `externalPrompt`
- 135행: `importExternalDraft`
- 171행: `validateSource`
- 183행: `postAi`
- 201행: `approve`
- 236행: `StoredTree`
- 238행: `parseStoredTree`
- 243행: `collect`
- 251행: `normalizeUi`
- 286행: `normalizeApproved`
- 309행: `approvedTree`
- 322행: `authorize`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/SelfLlmMenuContract.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/SelfLlmMenuContract.java)

제품 코드·설정·템플릿 · 80줄 · 7196바이트

self-llm-v4 requestId·E번호 목록 생성, 응답 참조를 원문으로 복원, 중복/다른 요청 차단·참조 범위 보고.

- 13행: `SelfLlmMenuContract`
- 17행: `invalid`
- 18행: `describe`
- 42행: `restore`
- 67행: `coverage`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcExcelExporter.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcExcelExporter.java)

제품 코드·설정·템플릿 · 126줄 · 9302바이트

기존 LLM TC 생성 결과 Excel 출력. MenuTcWorkbook과 출력 계약이 다르다.

- 19행: `TcExcelExporter`
- 25행: `export`
- 113행: `lines`
- 119행: `text`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcExecutionSpecification.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcExecutionSpecification.java)

제품 코드·설정·템플릿 · 70줄 · 4878바이트

엄격한 단순 조회만 v2-read로 전환한다. 대상·조회 전제·각 조건 근거를 미연결 상태로 생성하고 준비 검사를 한다.

- 9행: `TcExecutionSpecification`
- 15행: `upgradeRead`
- 57행: `preparationReason`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcGenerationService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcGenerationService.java)

제품 코드·설정·템플릿 · 369줄 · 23712바이트

기존 문서 기반 LLM TC 작업·진행·저장·중단·다운로드를 관리한다. 메뉴 템플릿 생성기와 별개다.

- 46행: `TcGenerationService`
- 83행: `daemon`
- 92행: `recoverStale`
- 97행: `error`
- 101행: `authorize`
- 109행: `Input`
- 111행: `verifiedInput`
- 157행: `start`
- 183행: `externalPrompt`
- 201행: `post`
- 209행: `submit`
- 252행: `done`
- 259행: `validateResult`
- 311행: `collectPaths`
- 318행: `pathKey`
- 324행: `fail`
- 328행: `status`
- 344행: `cancel`
- 353행: `storedJob`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcProcedureWriter.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcProcedureWriter.java)

제품 코드·설정·템플릿 · 116줄 · 18058바이트

유형별 번호가 있는 시험 절차·입력·기대 출력과 실행 절차를 구성한다.

- 7행: `TcProcedureWriter`
- 9행: `write`
- 89행: `login`
- 114행: `number`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcTemplateBinding.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TcTemplateBinding.java)

제품 코드·설정·템플릿 · 93줄 · 8785바이트

템플릿을 기능명·동작 유형과 연결하고 적용 제외 후보·필요 정보·초기 실행 명세를 구성한다.

- 9행: `TcTemplateBinding`
- 10행: `bind`
- 76행: `operation`
- 88행: `substitute`
- 91행: `join`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TestExecutionService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TestExecutionService.java)

제품 코드·설정·템플릿 · 139줄 · 14927바이트

선택 TC를 실행 스냅샷에 고정하고 사전 분류·이력·상태 전이·증거 조회·진행 갱신을 관리한다.

- 16행: `TestExecutionService`
- 25행: `error`
- 26행: `prerequisite`
- 33행: `owner`
- 38행: `current`
- 42행: `prepare`
- 46행: `prepare`
- 90행: `status`
- 113행: `evidence`
- 121행: `transition`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TestExecutionWorker.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/TestExecutionWorker.java)

제품 코드·설정·템플릿 · 171줄 · 15860바이트

WAITING 작업 선점·TC별 AI 호출·저장 계정 전달·문서 발췌/메뉴 이동 힌트·결과 보호·별도 증거 저장.

- 23행: `TestExecutionWorker`
- 34행: `close`
- 35행: `recover`
- 38행: `tick`
- 42행: `active`
- 43행: `save`
- 47행: `pendingGroup`
- 51행: `documents`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/UploadedMenuService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/UploadedMenuService.java)

제품 코드·설정·템플릿 · 158줄 · 11396바이트

UI 업로드 XLSX 첫 시트 또는 JSON rows를 승인된 업로드 메뉴 작업으로 저장한다. 화면 검증 완료로 취급하지 않는다.

- 22행: `UploadedMenuService`
- 37행: `error`
- 41행: `upload`
- 98행: `readJson`
- 112행: `readExcel`
- 147행: `value`

## [test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/WebMenuStructure.java](../../test-management-service/src/main/java/com/autotest/test_management_service/application/testcase/WebMenuStructure.java)

제품 코드·설정·템플릿 · 42줄 · 2656바이트

수집 근거로 관찰된 웹 메뉴 구조를 구성한다.

- 9행: `WebMenuStructure`
- 11행: `path`
- 23행: `build`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/event/SubmissionUploadedEvent.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/event/SubmissionUploadedEvent.java)

제품 코드·설정·템플릿 · 15줄 · 605바이트

도메인 파일 SubmissionUploadedEvent.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 9행: `SubmissionUploadedEvent`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/port/TestCaseRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/port/TestCaseRepository.java)

제품 코드·설정·템플릿 · 9줄 · 247바이트

도메인 파일 TestCaseRepository.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 7행: `TestCaseRepository`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/service/SubmissionDomainService.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/service/SubmissionDomainService.java)

제품 코드·설정·템플릿 · 15줄 · 615바이트

도메인 파일 SubmissionDomainService.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 11행: `SubmissionDomainService`
- 12행: `create`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/FileFormat.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/FileFormat.java)

제품 코드·설정·템플릿 · 35줄 · 1031바이트

도메인 파일 FileFormat.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `FileFormat`
- 14행: `fromExtension`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/FileMetadata.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/FileMetadata.java)

제품 코드·설정·템플릿 · 24줄 · 899바이트

도메인 파일 FileMetadata.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `FileMetadata`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/FileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/FileParser.java)

제품 코드·설정·템플릿 · 9줄 · 244바이트

도메인 파일 FileParser.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `FileParser`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/ParsedContent.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/ParsedContent.java)

제품 코드·설정·템플릿 · 23줄 · 775바이트

도메인 파일 ParsedContent.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `ParsedContent`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/ProductId.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/ProductId.java)

제품 코드·설정·템플릿 · 12줄 · 271바이트

도메인 파일 ProductId.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 8행: `ProductId`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/StoredPath.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/StoredPath.java)

제품 코드·설정·템플릿 · 20줄 · 505바이트

도메인 파일 StoredPath.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `StoredPath`
- 13행: `isS3`
- 17행: `isLocal`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/Submission.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/Submission.java)

제품 코드·설정·템플릿 · 109줄 · 3900바이트

도메인 파일 Submission.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 11행: `Submission`
- 38행: `create`
- 43행: `reconstitute`
- 54행: `draft`
- 59행: `markAsUploaded`
- 66행: `withProcessingStatus`
- 76행: `submissionId`
- 80행: `productId`
- 84행: `memberId`
- 88행: `testCases`
- 92행: `status`
- 96행: `uploadedAt`
- 100행: `domainEvents`
- 104행: `requireStatus`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionFactory.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionFactory.java)

제품 코드·설정·템플릿 · 25줄 · 789바이트

도메인 파일 SubmissionFactory.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 7행: `SubmissionFactory`
- 11행: `draft`
- 17행: `create`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionFile.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionFile.java)

제품 코드·설정·템플릿 · 84줄 · 3028바이트

도메인 파일 SubmissionFile.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 6행: `SubmissionFile`
- 27행: `create`
- 44행: `create`
- 49행: `assignStoredPath`
- 57행: `id`
- 61행: `fileMetadata`
- 65행: `fileFormat`
- 69행: `submissionType`
- 73행: `storedPath`
- 77행: `detectFormat`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionFileId.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionFileId.java)

제품 코드·설정·템플릿 · 14줄 · 369바이트

도메인 파일 SubmissionFileId.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 6행: `SubmissionFileId`
- 11행: `generate`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionId.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionId.java)

제품 코드·설정·템플릿 · 18줄 · 452바이트

도메인 파일 SubmissionId.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 6행: `SubmissionId`
- 11행: `generate`
- 15행: `of`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionRepository.java)

제품 코드·설정·템플릿 · 11줄 · 303바이트

도메인 파일 SubmissionRepository.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `SubmissionRepository`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionStatus.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionStatus.java)

제품 코드·설정·템플릿 · 9줄 · 167바이트

도메인 파일 SubmissionStatus.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 3행: `SubmissionStatus`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionType.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmissionType.java)

제품 코드·설정·템플릿 · 7줄 · 146바이트

도메인 파일 SubmissionType.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 3행: `SubmissionType`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmittedDocument.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmittedDocument.java)

제품 코드·설정·템플릿 · 22줄 · 654바이트

도메인 파일 SubmittedDocument.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 8행: `SubmittedDocument`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmittedDocumentRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/SubmittedDocumentRepository.java)

제품 코드·설정·템플릿 · 16줄 · 600바이트

도메인 파일 SubmittedDocumentRepository.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 6행: `SubmittedDocumentRepository`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/TestCase.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/TestCase.java)

제품 코드·설정·템플릿 · 27줄 · 1054바이트

도메인 파일 TestCase.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 5행: `TestCase`
- 16행: `createParsed`
- 23행: `withSubmissionId`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/TestCaseId.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/submission/TestCaseId.java)

제품 코드·설정·템플릿 · 14줄 · 345바이트

도메인 파일 TestCaseId.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 6행: `TestCaseId`
- 11행: `generate`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/vo/MemberId.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/vo/MemberId.java)

제품 코드·설정·템플릿 · 12줄 · 261바이트

도메인 파일 MemberId.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 8행: `MemberId`

## [test-management-service/src/main/java/com/autotest/test_management_service/domain/vo/SubmissionType.java](../../test-management-service/src/main/java/com/autotest/test_management_service/domain/vo/SubmissionType.java)

제품 코드·설정·템플릿 · 9줄 · 146바이트

도메인 파일 SubmissionType.java: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.

- 3행: `SubmissionType`

## [test-management-service/src/main/java/com/autotest/test_management_service/exception/TestManagementErrorCode.java](../../test-management-service/src/main/java/com/autotest/test_management_service/exception/TestManagementErrorCode.java)

제품 코드·설정·템플릿 · 174줄 · 19135바이트

TMS 업무별 오류 enum, HTTP 번호별 구분.

- 7행: `TestManagementErrorCode`
- 170행: `getCode`
- 171행: `getMessage`
- 172행: `getStatus`

## [test-management-service/src/main/java/com/autotest/test_management_service/exception/TestManagementException.java](../../test-management-service/src/main/java/com/autotest/test_management_service/exception/TestManagementException.java)

제품 코드·설정·템플릿 · 19줄 · 1224바이트

TMS 오류를 공통 핸들러와 연결하는 예외.

- 5행: `TestManagementException`
- 11행: `resolve`
- 17행: `LegacyCode`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/ai/AiServiceProductDescriptionGateway.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/ai/AiServiceProductDescriptionGateway.java)

제품 코드·설정·템플릿 · 167줄 · 8746바이트

구성/보조 소스 AiServiceProductDescriptionGateway.java. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 25행: `AiServiceProductDescriptionGateway`
- 51행: `generateContent`
- 69행: `contentProgress`
- 77행: `contentBody`
- 94행: `render`
- 108행: `post`
- 146행: `errorFrom`
- 159행: `parse`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/config/S3Config.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/config/S3Config.java)

제품 코드·설정·템플릿 · 35줄 · 1464바이트

S3 endpoint·인증·path-style 클라이언트 설정.

- 15행: `S3Config`
- 17행: `s3Client`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/AbstractStubFileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/AbstractStubFileParser.java)

제품 코드·설정·템플릿 · 51줄 · 2045바이트

구성/보조 소스 AbstractStubFileParser.java. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 14행: `AbstractStubFileParser`
- 22행: `supports`
- 27행: `parse`
- 44행: `detectFormat`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/DocumentFileParserFactory.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/DocumentFileParserFactory.java)

제품 코드·설정·템플릿 · 25줄 · 897바이트

등록된 FileParser 중 문서 형식을 지원하는 구현을 선택한다.

- 11행: `DocumentFileParserFactory`
- 18행: `getParser`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/DocumentParsingException.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/DocumentParsingException.java)

제품 코드·설정·템플릿 · 12줄 · 340바이트

구성/보조 소스 DocumentParsingException.java. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.

- 3행: `DocumentParsingException`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/DocxFileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/DocxFileParser.java)

제품 코드·설정·템플릿 · 66줄 · 2737바이트

DOC와 DOCX 텍스트를 POI extractor로 읽는다.

- 20행: `DocxFileParser`
- 23행: `supports`
- 28행: `parse`
- 57행: `readAllBytes`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/ExcelFileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/ExcelFileParser.java)

제품 코드·설정·템플릿 · 95줄 · 4131바이트

XLS/XLSX의 시트·셀 텍스트를 추출한다.

- 23행: `ExcelFileParser`
- 26행: `supports`
- 31행: `parse`
- 86행: `readAllBytes`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/HwpFileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/HwpFileParser.java)

제품 코드·설정·템플릿 · 65줄 · 2627바이트

HWP 텍스트를 hwplib로 읽는다.

- 20행: `HwpFileParser`
- 23행: `supports`
- 28행: `parse`
- 56행: `readAllBytes`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/HwpxFileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/HwpxFileParser.java)

제품 코드·설정·템플릿 · 72줄 · 2973바이트

HWPX 문서 텍스트를 읽는다.

- 20행: `HwpxFileParser`
- 23행: `supports`
- 28행: `parse`
- 62행: `readAllBytes`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/PdfFileParser.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/parser/PdfFileParser.java)

제품 코드·설정·템플릿 · 66줄 · 2695바이트

PDF 텍스트 추출. OCR 수행 엔진으로 간주하지 않는다.

- 19행: `PdfFileParser`
- 22행: `supports`
- 27행: `parse`
- 57행: `readAllBytes`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/S3CleanupOutboxRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/S3CleanupOutboxRepository.java)

제품 코드·설정·템플릿 · 83줄 · 3256바이트

영속성 파일 S3CleanupOutboxRepository.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 15행: `S3CleanupOutboxRepository`
- 23행: `schedule`
- 32행: `claimDue`
- 56행: `isReferenced`
- 64행: `markCompleted`
- 73행: `markFailed`
- 81행: `CleanupJob`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SpringDataSubmissionRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SpringDataSubmissionRepository.java)

제품 코드·설정·템플릿 · 16줄 · 684바이트

영속성 파일 SpringDataSubmissionRepository.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 12행: `SpringDataSubmissionRepository`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SpringDataSubmittedDocumentRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SpringDataSubmittedDocumentRepository.java)

제품 코드·설정·템플릿 · 28줄 · 1373바이트

영속성 파일 SpringDataSubmittedDocumentRepository.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 14행: `SpringDataSubmittedDocumentRepository`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmissionEntity.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmissionEntity.java)

제품 코드·설정·템플릿 · 62줄 · 2144바이트

영속성 파일 SubmissionEntity.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 23행: `SubmissionEntity`
- 54행: `fromDomain`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmissionRepositoryAdapter.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmissionRepositoryAdapter.java)

제품 코드·설정·템플릿 · 33줄 · 1195바이트

영속성 파일 SubmissionRepositoryAdapter.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 12행: `SubmissionRepositoryAdapter`
- 20행: `findById`
- 25행: `findByIdForUpdate`
- 30행: `save`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmittedDocumentEntity.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmittedDocumentEntity.java)

제품 코드·설정·템플릿 · 101줄 · 3604바이트

영속성 파일 SubmittedDocumentEntity.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 21행: `SubmittedDocumentEntity`
- 81행: `fromDomain`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmittedDocumentRepositoryAdapter.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/SubmittedDocumentRepositoryAdapter.java)

제품 코드·설정·템플릿 · 59줄 · 2421바이트

영속성 파일 SubmittedDocumentRepositoryAdapter.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 13행: `SubmittedDocumentRepositoryAdapter`
- 21행: `save`
- 26행: `findBySubmissionId`
- 33행: `existsBySubmissionIdAndRole`
- 38행: `replaceFailedDocument`
- 44행: `replaceDocument`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/memory/InMemoryTestCaseRepository.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/persistence/memory/InMemoryTestCaseRepository.java)

제품 코드·설정·템플릿 · 20줄 · 822바이트

영속성 파일 InMemoryTestCaseRepository.java: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.

- 13행: `InMemoryTestCaseRepository`
- 17행: `saveAll`

## [test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/storage/S3FileStorageAdapter.java](../../test-management-service/src/main/java/com/autotest/test_management_service/infrastructure/storage/S3FileStorageAdapter.java)

제품 코드·설정·템플릿 · 211줄 · 8891바이트

AWS SDK S3Client로 원본·생성 파일 저장/조회/삭제.

- 33행: `S3FileStorageAdapter`
- 57행: `store`
- 62행: `storeUnderPrefix`
- 70행: `storeWithKey`
- 95행: `load`
- 116행: `delete`
- 141행: `ensureBucketExists`
- 152행: `storeLocally`
- 166행: `localFilePath`
- 175행: `parseS3Location`
- 184행: `openLocalFile`
- 192행: `readContent`
- 200행: `createObjectKey`
- 209행: `S3Location`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/ActiveProductDescriptionController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/ActiveProductDescriptionController.java)

제품 코드·설정·템플릿 · 23줄 · 867바이트

ActiveProductDescriptionController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 13행: `HTTP /api/product-descriptions`
- 15행: `ActiveProductDescriptionController`
- 18행: `HTTP /active`
- 19행: `activeForMember`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/ExecutorStatusController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/ExecutorStatusController.java)

제품 코드·설정·템플릿 · 39줄 · 2057바이트

ExecutorStatusController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 13행: `ExecutorStatusController`
- 17행: `HTTP /api/crawl-agents/local/status`
- 18행: `status`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/FunctionalMenuController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/FunctionalMenuController.java)

제품 코드·설정·템플릿 · 30줄 · 4064바이트

FunctionalMenuController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 11행: `HTTP /api/submissions/{submission}/functional-menu`
- 12행: `FunctionalMenuController`
- 15행: `Start`
- 16행: `Edit`
- 17행: `ok`
- 18행: `status`
- 19행: `start`
- 20행: `upload`
- 21행: `prompt`
- 21행: `HTTP /{job}/prompt`
- 22행: `evidence`
- 22행: `HTTP /{job}/evidence`
- 23행: `imported`
- 23행: `HTTP /{job}/import`
- 24행: `save`
- 24행: `HTTP /{job}/draft`
- 25행: `approve`
- 25행: `HTTP /{job}/approval`
- 26행: `cancel`
- 26행: `HTTP /{job}/cancel`
- 27행: `download`
- 27행: `HTTP /{job}/download`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/GlobalExceptionHandler.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/GlobalExceptionHandler.java)

제품 코드·설정·템플릿 · 7줄 · 273바이트

공통 Servlet 예외 핸들러를 TMS 패키지에 연결한다.

- 6행: `GlobalExceptionHandler`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/MenuTcController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/MenuTcController.java)

제품 코드·설정·템플릿 · 21줄 · 1787바이트

MenuTcController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 10행: `HTTP /api/submissions/{submission}/menu-test-cases`
- 11행: `MenuTcController`
- 14행: `Generate`
- 15행: `ok`
- 16행: `status`
- 17행: `generate`
- 18행: `download`
- 18행: `HTTP /{set}/download`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/MenuTreeController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/MenuTreeController.java)

제품 코드·설정·템플릿 · 57줄 · 2670바이트

MenuTreeController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 22행: `HTTP /api/submissions/{submissionId}/menu-tree`
- 24행: `MenuTreeController`
- 26행: `Approval`
- 27행: `ExternalPrompt`
- 28행: `ExternalDraft`
- 31행: `status`
- 35행: `HTTP /external-prompt`
- 36행: `externalPrompt`
- 42행: `HTTP /external-draft`
- 43행: `importExternalDraft`
- 49행: `HTTP /approval`
- 50행: `approve`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/PlaywrightSetupController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/PlaywrightSetupController.java)

제품 코드·설정·템플릿 · 47줄 · 2432바이트

PlaywrightSetupController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 20행: `HTTP /api/submissions/{submissionId}/playwright-setup`
- 22행: `PlaywrightSetupController`
- 24행: `SetupResponse`
- 26행: `HTTP /tls-policy`
- 27행: `tlsPolicy`
- 33행: `get`
- 39행: `save`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/ProductDescriptionController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/ProductDescriptionController.java)

제품 코드·설정·템플릿 · 71줄 · 3362바이트

ProductDescriptionController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 26행: `HTTP /api/submissions/{submissionId}/product-description`
- 28행: `ProductDescriptionController`
- 32행: `start`
- 40행: `status`
- 44행: `HTTP /{jobId}/cancel`
- 45행: `cancel`
- 50행: `HTTP /{jobId}/rerender`
- 51행: `rerender`
- 57행: `HTTP /{jobId}/download`
- 58행: `download`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/RemoteCrawlController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/RemoteCrawlController.java)

제품 코드·설정·템플릿 · 92줄 · 6935바이트

RemoteCrawlController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 18행: `RemoteCrawlController`
- 23행: `AgentRequest`
- 24행: `RetryRequest`
- 25행: `hasCapability`
- 26행: `StartRequest`
- 28행: `response`
- 30행: `HTTP /api/crawl-agents`
- 31행: `agents`
- 34행: `HTTP /api/crawl-agents`
- 35행: `create`
- 38행: `HTTP /api/crawl-agents/{agent}/revoke`
- 39행: `revoke`
- 42행: `HTTP /api/crawl-agents/{agent}/channel/{action}`
- 43행: `channel`
- 54행: `HTTP /api/submissions/{submission}/playwright-crawls`
- 55행: `status`
- 58행: `HTTP /api/submissions/{submission}/playwright-crawls`
- 59행: `start`
- 63행: `HTTP /api/submissions/{submission}/playwright-crawls/{job}/resume`
- 64행: `resume`
- 65행: `HTTP /api/submissions/{submission}/playwright-crawls/{job}/retry`
- 66행: `retry`
- 67행: `HTTP /api/internal/playwright-crawls/events`
- 68행: `localEvent`
- 73행: `HTTP /api/submissions/{submission}/playwright-crawls/{job}/cancel`
- 74행: `cancel`
- 77행: `HTTP /api/submissions/{submission}/playwright-crawls/{job}/pages/{index}`
- 78행: `evidence`
- 81행: `HTTP /api/submissions/{submission}/playwright-crawls/{job}/menu-tree`
- 82행: `menuTree`
- 85행: `HTTP /api/submissions/{submission}/playwright-crawls/{job}/pages/{index}/screenshot`
- 86행: `screenshot`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/SubmissionController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/SubmissionController.java)

제품 코드·설정·템플릿 · 176줄 · 9061바이트

SubmissionController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 30행: `HTTP /api/submissions`
- 32행: `SubmissionController`
- 38행: `submit`
- 58행: `uploadAdditional`
- 72행: `replaceFailedDocument`
- 99행: `HTTP /{id}`
- 100행: `findById`
- 110행: `HTTP /{id}/ai-delivery/retry`
- 111행: `retryAiDelivery`
- 125행: `SubmissionResponse`
- 132행: `from`
- 153행: `UploadedDocumentResponse`
- 165행: `from`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/TcGenerationController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/TcGenerationController.java)

제품 코드·설정·템플릿 · 52줄 · 2970바이트

TcGenerationController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 18행: `HTTP /api/submissions/{submissionId}/test-cases`
- 20행: `TcGenerationController`
- 23행: `Approval`
- 25행: `start`
- 29행: `status`
- 32행: `externalPrompt`
- 32행: `HTTP /external-prompt`
- 37행: `cancel`
- 37행: `HTTP /{jobId}/cancel`
- 41행: `download`
- 41행: `HTTP /{jobId}/download`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/TestExecutionController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/TestExecutionController.java)

제품 코드·설정·템플릿 · 37줄 · 4980바이트

TestExecutionController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 11행: `HTTP /api/submissions/{submission}/executions`
- 12행: `TestExecutionController`
- 17행: `Prepare`
- 18행: `Review`
- 19행: `Retry`
- 20행: `ok`
- 21행: `status`
- 22행: `evidence`
- 22행: `HTTP /{run}/evidence`
- 23행: `prepare`
- 24행: `review`
- 24행: `HTTP /{run}/review`
- 25행: `retry`
- 25행: `HTTP /{run}/retry`
- 26행: `resume`
- 26행: `HTTP /{run}/resume`
- 27행: `report`
- 27행: `HTTP /{run}/report`
- 28행: `start`
- 28행: `HTTP /{run}/start`
- 34행: `cancel`
- 34행: `HTTP /{run}/cancel`

## [test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/TestExecutionInternalController.java](../../test-management-service/src/main/java/com/autotest/test_management_service/presentation/controller/TestExecutionInternalController.java)

제품 코드·설정·템플릿 · 32줄 · 2976바이트

TestExecutionInternalController의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.

- 10행: `TestExecutionInternalController`
- 12행: `Progress`
- 13행: `HTTP /api/internal/test-executions/{run}/progress`
- 14행: `progress`
- 25행: `HTTP /api/internal/test-executions/{run}/active`
- 26행: `active`

## [test-management-service/src/main/resources/application.yml](../../test-management-service/src/main/resources/application.yml)

제품 코드·설정·템플릿 · 33줄 · 910바이트

빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V10__add_product_description_progress.sql](../../test-management-service/src/main/resources/db/migration/V10__add_product_description_progress.sql)

제품 코드·설정·템플릿 · 12줄 · 458바이트

스키마 마이그레이션 V10__add_product_description_progress.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V11__limit_active_product_descriptions_per_member.sql](../../test-management-service/src/main/resources/db/migration/V11__limit_active_product_descriptions_per_member.sql)

제품 코드·설정·템플릿 · 4줄 · 227바이트

스키마 마이그레이션 V11__limit_active_product_descriptions_per_member.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V12__create_tc_generation_jobs.sql](../../test-management-service/src/main/resources/db/migration/V12__create_tc_generation_jobs.sql)

제품 코드·설정·템플릿 · 17줄 · 881바이트

스키마 마이그레이션 V12__create_tc_generation_jobs.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V13__create_menu_tree_approvals.sql](../../test-management-service/src/main/resources/db/migration/V13__create_menu_tree_approvals.sql)

제품 코드·설정·템플릿 · 10줄 · 401바이트

스키마 마이그레이션 V13__create_menu_tree_approvals.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V14__create_playwright_crawl_setups.sql](../../test-management-service/src/main/resources/db/migration/V14__create_playwright_crawl_setups.sql)

제품 코드·설정·템플릿 · 14줄 · 640바이트

스키마 마이그레이션 V14__create_playwright_crawl_setups.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V15__extend_playwright_setup_for_auto_login.sql](../../test-management-service/src/main/resources/db/migration/V15__extend_playwright_setup_for_auto_login.sql)

제품 코드·설정·템플릿 · 8줄 · 410바이트

스키마 마이그레이션 V15__extend_playwright_setup_for_auto_login.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V16__create_remote_crawl_agents_and_jobs.sql](../../test-management-service/src/main/resources/db/migration/V16__create_remote_crawl_agents_and_jobs.sql)

제품 코드·설정·템플릿 · 49줄 · 1991바이트

스키마 마이그레이션 V16__create_remote_crawl_agents_and_jobs.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V17__add_local_browser_checks.sql](../../test-management-service/src/main/resources/db/migration/V17__add_local_browser_checks.sql)

제품 코드·설정·템플릿 · 10줄 · 502바이트

스키마 마이그레이션 V17__add_local_browser_checks.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V18__store_captured_navigation.sql](../../test-management-service/src/main/resources/db/migration/V18__store_captured_navigation.sql)

제품 코드·설정·템플릿 · 2줄 · 94바이트

스키마 마이그레이션 V18__store_captured_navigation.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V19__store_screen_state_and_navigation_transitions.sql](../../test-management-service/src/main/resources/db/migration/V19__store_screen_state_and_navigation_transitions.sql)

제품 코드·설정·템플릿 · 4줄 · 260바이트

스키마 마이그레이션 V19__store_screen_state_and_navigation_transitions.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V1__create_submissions.sql](../../test-management-service/src/main/resources/db/migration/V1__create_submissions.sql)

제품 코드·설정·템플릿 · 13줄 · 454바이트

스키마 마이그레이션 V1__create_submissions.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V20__create_functional_menu_jobs.sql](../../test-management-service/src/main/resources/db/migration/V20__create_functional_menu_jobs.sql)

제품 코드·설정·템플릿 · 20줄 · 947바이트

스키마 마이그레이션 V20__create_functional_menu_jobs.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V21__create_menu_tc_sets.sql](../../test-management-service/src/main/resources/db/migration/V21__create_menu_tc_sets.sql)

제품 코드·설정·템플릿 · 14줄 · 559바이트

스키마 마이그레이션 V21__create_menu_tc_sets.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V22__allow_uploaded_menu_jobs.sql](../../test-management-service/src/main/resources/db/migration/V22__allow_uploaded_menu_jobs.sql)

제품 코드·설정·템플릿 · 2줄 · 75바이트

스키마 마이그레이션 V22__allow_uploaded_menu_jobs.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V23__create_test_execution_runs.sql](../../test-management-service/src/main/resources/db/migration/V23__create_test_execution_runs.sql)

제품 코드·설정·템플릿 · 13줄 · 680바이트

스키마 마이그레이션 V23__create_test_execution_runs.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V24__enable_test_execution_worker.sql](../../test-management-service/src/main/resources/db/migration/V24__enable_test_execution_worker.sql)

제품 코드·설정·템플릿 · 7줄 · 515바이트

스키마 마이그레이션 V24__enable_test_execution_worker.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V25__separate_execution_evidence.sql](../../test-management-service/src/main/resources/db/migration/V25__separate_execution_evidence.sql)

제품 코드·설정·템플릿 · 9줄 · 306바이트

스키마 마이그레이션 V25__separate_execution_evidence.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V2__add_failure_reason.sql](../../test-management-service/src/main/resources/db/migration/V2__add_failure_reason.sql)

제품 코드·설정·템플릿 · 2줄 · 57바이트

스키마 마이그레이션 V2__add_failure_reason.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V3__create_submission_files.sql](../../test-management-service/src/main/resources/db/migration/V3__create_submission_files.sql)

제품 코드·설정·템플릿 · 17줄 · 657바이트

스키마 마이그레이션 V3__create_submission_files.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V4__create_ai_delivery.sql](../../test-management-service/src/main/resources/db/migration/V4__create_ai_delivery.sql)

제품 코드·설정·템플릿 · 8줄 · 322바이트

스키마 마이그레이션 V4__create_ai_delivery.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V5__normalize_submission_file_storage.sql](../../test-management-service/src/main/resources/db/migration/V5__normalize_submission_file_storage.sql)

제품 코드·설정·템플릿 · 97줄 · 3045바이트

스키마 마이그레이션 V5__normalize_submission_file_storage.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V6__single_source_submission_file_status.sql](../../test-management-service/src/main/resources/db/migration/V6__single_source_submission_file_status.sql)

제품 코드·설정·템플릿 · 28줄 · 966바이트

스키마 마이그레이션 V6__single_source_submission_file_status.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V7__store_document_preflight_result.sql](../../test-management-service/src/main/resources/db/migration/V7__store_document_preflight_result.sql)

제품 코드·설정·템플릿 · 3줄 · 78바이트

스키마 마이그레이션 V7__store_document_preflight_result.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V8__store_ai_delivery_block_reasons.sql](../../test-management-service/src/main/resources/db/migration/V8__store_ai_delivery_block_reasons.sql)

제품 코드·설정·템플릿 · 4줄 · 115바이트

스키마 마이그레이션 V8__store_ai_delivery_block_reasons.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/db/migration/V9__create_product_description_jobs.sql](../../test-management-service/src/main/resources/db/migration/V9__create_product_description_jobs.sql)

제품 코드·설정·템플릿 · 45줄 · 1920바이트

스키마 마이그레이션 V9__create_product_description_jobs.sql; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/tc-templates/menu-tc-v1.json](../../test-management-service/src/main/resources/tc-templates/menu-tc-v1.json)

제품 코드·설정·템플릿 · 1369줄 · 70060바이트

버전 관리 TC 템플릿 데이터. 생성기에서 선택한 버전과 과거 보존 버전을 구분한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/main/resources/tc-templates/menu-tc-v3.json](../../test-management-service/src/main/resources/tc-templates/menu-tc-v3.json)

제품 코드·설정·템플릿 · 1832줄 · 92368바이트

버전 관리 TC 템플릿 데이터. 생성기에서 선택한 버전과 과거 보존 버전을 구분한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.

## [test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/CapturedMenuTreeTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/CapturedMenuTreeTest.java)

검증·보조 코드 · 40줄 · 2717바이트

검증/보조 파일 CapturedMenuTreeTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 9행: `CapturedMenuTreeTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/PlaywrightCredentialCipherTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/PlaywrightCredentialCipherTest.java)

검증·보조 코드 · 38줄 · 1775바이트

검증/보조 파일 PlaywrightCredentialCipherTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `PlaywrightCredentialCipherTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/PlaywrightSetupIntegrationTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/PlaywrightSetupIntegrationTest.java)

검증·보조 코드 · 371줄 · 33231바이트

검증/보조 파일 PlaywrightSetupIntegrationTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 31행: `PlaywrightSetupIntegrationTest`
- 33행: `database`
- 162행: `channel`
- 175행: `onlineAgent`
- 180행: `saveCrawlSetup`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/PlaywrightSetupServiceTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/crawler/PlaywrightSetupServiceTest.java)

검증·보조 코드 · 60줄 · 3282바이트

검증/보조 파일 PlaywrightSetupServiceTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 18행: `PlaywrightSetupServiceTest`
- 19행: `service`
- 25행: `request`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionDisabledTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionDisabledTest.java)

검증·보조 코드 · 28줄 · 1544바이트

검증/보조 파일 ProductDescriptionDisabledTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 13행: `ProductDescriptionDisabledTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionIntegrationTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionIntegrationTest.java)

검증·보조 코드 · 527줄 · 26454바이트

검증/보조 파일 ProductDescriptionIntegrationTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 78행: `ProductDescriptionIntegrationTest`
- 84행: `datasourceProperties`
- 145행: `cannedContent`
- 153행: `seed`
- 180행: `ready`
- 184행: `await`
- 195행: `jobCount`
- 200행: `rejects`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionWorkerTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/productdescription/ProductDescriptionWorkerTest.java)

검증·보조 코드 · 48줄 · 2189바이트

검증/보조 파일 ProductDescriptionWorkerTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 14행: `ProductDescriptionWorkerTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/service/AiDocumentDeliveryServiceTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/service/AiDocumentDeliveryServiceTest.java)

검증·보조 코드 · 429줄 · 19753바이트

검증/보조 파일 AiDocumentDeliveryServiceTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 43행: `AiDocumentDeliveryServiceTest`
- 257행: `service`
- 262행: `handleRequest`
- 283행: `receipt`
- 289행: `documents`
- 296행: `document`
- 309행: `roles`
- 315행: `DeliveryJdbcTemplate`
- 331행: `update`
- 378행: `query`
- 383행: `claim`
- 405행: `claims`
- 409행: `blockReasons`
- 413행: `attempts`
- 417행: `resetForRetry`
- 422행: `setPendingAt`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/service/SubmissionServiceTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/service/SubmissionServiceTest.java)

검증·보조 코드 · 365줄 · 23718바이트

검증/보조 파일 SubmissionServiceTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 35행: `SubmissionServiceTest`
- 259행: `submittedDocument`
- 274행: `createPdfContent`
- 295행: `submitWithStoredPath`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/AuthenticationTcContextTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/AuthenticationTcContextTest.java)

검증·보조 코드 · 67줄 · 5497바이트

검증/보조 파일 AuthenticationTcContextTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `AuthenticationTcContextTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/ExecutionFeatureClassificationTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/ExecutionFeatureClassificationTest.java)

검증·보조 코드 · 14줄 · 928바이트

검증/보조 파일 ExecutionFeatureClassificationTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 5행: `ExecutionFeatureClassificationTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/ExecutionReadinessServiceTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/ExecutionReadinessServiceTest.java)

검증·보조 코드 · 53줄 · 4118바이트

검증/보조 파일 ExecutionReadinessServiceTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `ExecutionReadinessServiceTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/FunctionalMenuExcelTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/FunctionalMenuExcelTest.java)

검증·보조 코드 · 41줄 · 3989바이트

검증/보조 파일 FunctionalMenuExcelTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 10행: `FunctionalMenuExcelTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/FunctionalMenuHttpTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/FunctionalMenuHttpTest.java)

검증·보조 코드 · 40줄 · 2563바이트

검증/보조 파일 FunctionalMenuHttpTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 17행: `FunctionalMenuHttpTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/FunctionalMenuValidatorTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/FunctionalMenuValidatorTest.java)

검증·보조 코드 · 55줄 · 4411바이트

검증/보조 파일 FunctionalMenuValidatorTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 9행: `FunctionalMenuValidatorTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuPlacementRecoveryTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuPlacementRecoveryTest.java)

검증·보조 코드 · 71줄 · 6213바이트

검증/보조 파일 MenuPlacementRecoveryTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `MenuPlacementRecoveryTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuTcGeneratorTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuTcGeneratorTest.java)

검증·보조 코드 · 135줄 · 11405바이트

검증/보조 파일 MenuTcGeneratorTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 14행: `MenuTcGeneratorTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuTcIntegrationTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuTcIntegrationTest.java)

검증·보조 코드 · 277줄 · 33855바이트

검증/보조 파일 MenuTcIntegrationTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 25행: `MenuTcIntegrationTest`
- 26행: `database`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuTcServiceTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/MenuTcServiceTest.java)

검증·보조 코드 · 56줄 · 4185바이트

검증/보조 파일 MenuTcServiceTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 16행: `MenuTcServiceTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/NeoFlowMenuClassificationAuditTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/NeoFlowMenuClassificationAuditTest.java)

검증·보조 코드 · 50줄 · 4236바이트

검증/보조 파일 NeoFlowMenuClassificationAuditTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `NeoFlowMenuClassificationAuditTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/SelfLlmMenuContractTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/SelfLlmMenuContractTest.java)

검증·보조 코드 · 47줄 · 4119바이트

검증/보조 파일 SelfLlmMenuContractTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `SelfLlmMenuContractTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcExcelExporterTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcExcelExporterTest.java)

검증·보조 코드 · 66줄 · 5018바이트

검증/보조 파일 TcExcelExporterTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 11행: `TcExcelExporterTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcGenerationServiceTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcGenerationServiceTest.java)

검증·보조 코드 · 86줄 · 5938바이트

검증/보조 파일 TcGenerationServiceTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 16행: `TcGenerationServiceTest`
- 76행: `addPolicyCase`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcProcedureWriterTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcProcedureWriterTest.java)

검증·보조 코드 · 47줄 · 3663바이트

검증/보조 파일 TcProcedureWriterTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 9행: `TcProcedureWriterTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcReadSpecificationTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/application/testcase/TcReadSpecificationTest.java)

검증·보조 코드 · 77줄 · 4709바이트

검증/보조 파일 TcReadSpecificationTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `TcReadSpecificationTest`
- 11행: `generate`
- 21행: `rule`

## [test-management-service/src/test/java/com/autotest/test_management_service/domain/submission/SubmissionTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/domain/submission/SubmissionTest.java)

검증·보조 코드 · 52줄 · 2272바이트

검증/보조 파일 SubmissionTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 14행: `SubmissionTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/exception/TestManagementExceptionTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/exception/TestManagementExceptionTest.java)

검증·보조 코드 · 21줄 · 1303바이트

검증/보조 파일 TestManagementExceptionTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 8행: `TestManagementExceptionTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/infrastructure/parser/FileParserFactoryTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/infrastructure/parser/FileParserFactoryTest.java)

검증·보조 코드 · 182줄 · 8272바이트

검증/보조 파일 FileParserFactoryTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 40행: `FileParserTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/infrastructure/persistence/SubmissionDatabaseIntegrationTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/infrastructure/persistence/SubmissionDatabaseIntegrationTest.java)

검증·보조 코드 · 458줄 · 24782바이트

검증/보조 파일 SubmissionDatabaseIntegrationTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 67행: `SubmissionDatabaseIntegrationTest`
- 71행: `datasourceProperties`
- 75행: `isolatedDatabaseUrl`
- 370행: `upload`
- 382행: `uploadAdditional`
- 391행: `stubStorage`
- 412행: `replace`
- 420행: `replaceSafely`
- 428행: `createPdf`
- 447행: `createWorkbook`

## [test-management-service/src/test/java/com/autotest/test_management_service/infrastructure/storage/S3FileStorageAdapterTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/infrastructure/storage/S3FileStorageAdapterTest.java)

검증·보조 코드 · 75줄 · 3290바이트

검증/보조 파일 S3FileStorageAdapterTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 23행: `S3FileStorageAdapterTest`

## [test-management-service/src/test/java/com/autotest/test_management_service/presentation/controller/SubmissionControllerTest.java](../../test-management-service/src/test/java/com/autotest/test_management_service/presentation/controller/SubmissionControllerTest.java)

검증·보조 코드 · 219줄 · 11992바이트

검증/보조 파일 SubmissionControllerTest.java. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 39행: `SubmissionControllerTest`
- 171행: `Case`
- 201행: `submission`
- 211행: `document`

## [test-management-service/src/test/resources/documents/SampleDoc.doc](../../test-management-service/src/test/resources/documents/SampleDoc.doc)

검증·보조 코드 · 30줄 · 27136바이트

검증/보조 파일 SampleDoc.doc. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.

- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.
