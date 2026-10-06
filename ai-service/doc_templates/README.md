# 제품 설명 문서 템플릿 패키지

제품 설명 문서의 구조·문구·표현은 이 디렉터리의 **버전 있는 템플릿 패키지**에서만 정의한다.
API, 작업 상태 관리(test-management-service), LLM 어댑터에는 템플릿 내용을 넣지 않는다.

## 위치와 구성

`ai-service/doc_templates/product-description/`

| 파일 | 역할 |
|---|---|
| `manifest.json` | `templateId`, `version`, 지원 출력 형식 |
| `content.json` | 문서 제목, 섹션 ID·제목·순서, 섹션별 작성 지침, 블록 구성(문단/목록/표), 안내 문구, LLM 공통 지시문, 시스템 생성 표의 문구 |
| `presentation.json` | PDF 표현: 글꼴, 여백, 용지, 스타일(크기·줄 간격·색), 표 스타일, 머리글·바닥글, 섹션 뒤 쪽 나눔 |

`ai-service`는 `/app`에 bind mount되므로 파일을 저장하면 **재시작·재빌드 없이** 다음 생성 요청부터 적용된다.
템플릿은 요청마다 디스크에서 다시 읽는다(캐시 없음).

## 동작 방식

1. 생성 요청 시 `content`·`presentation`·`manifest`와 SHA-256 `digest`를 묶은 **스냅샷**을 만든다.
2. LLM은 스냅샷의 섹션 지침과 블록 명세에 맞는 **구조화 데이터**(섹션 ID, 블록, 출처 fileId)만 반환한다.
   PDF/HTML을 직접 만들지 않는다.
3. 구조화 문서는 범용 문서 모델(`product_description/document_model.py`)로 검증된 뒤 저장된다.
4. 출력 전략(`rendering.py`의 `DocumentRenderer`, 현재 `PdfRenderer`)이 **저장된 스냅샷의 `presentation`**으로 파일을 만든다.
5. test-management-service는 작업 행에 스냅샷(`template_snapshot`)과 문서 데이터(`content`)를 저장한다.
   완료된 PDF는 보존되며 다운로드 때 재생성하지 않는다. 최신 템플릿 적용은 **새 생성 작업**("다시 생성")으로만 이뤄진다.
6. PDF 출력만 실패하면(`RENDER_FAILED`) 저장된 스냅샷·내용으로 LLM 호출 없이 "PDF 다시 출력"을 한다.

## 템플릿만 수정해서 되는 변경

- 제목·안내 문구·작성 지침·역할 표시명 변경: `content.json`
- 모델 교체: `.env`의 `LLM_PROVIDER`·`LLM_MODEL`만 변경. 생성·렌더링·작업 API는 변경하지 않는다.
- 프롬프트 품질 보완: `content.json`의 `pipeline.extract`, `pipeline.write`, 섹션별 `guideline`, `llmInstructions`를 수정한다.
- 근거 검증 및 청크 경계 문맥: `content.json`의 `quality.evidence`, `quality.chunking`을 조정한다.
- 섹션 추가/삭제/순서 변경: `content.json`의 `sections` 배열 수정. 섹션 `id`는 `^[a-z][a-z0-9_]{0,39}$`이며 고유해야 한다.
- 표 열 이름·열 너비 변경, 문단/목록/표 블록 구성 변경: 섹션의 `blocks` 수정 (LLM 출력은 이 구성과 같은 순서·유형이어야 한다).
- 글꼴·여백·글자 크기·색·표 스타일·번호 매기기·쪽 나눔·머리글/바닥글: `presentation.json`
- 변경 후에는 `manifest.json`의 `version`을 올린다.

### 예시 1: "제한 사항" 섹션을 "구성 및 동작 흐름" 뒤에 추가

`content.json`의 `sections`에서 `architecture` 항목 뒤에 추가한다.

```json
{
  "id": "limits",
  "title": "제한 사항",
  "generator": "llm",
  "guideline": "문서에서 확인되는 제한 사항을 목록으로 적는다. 없으면 '{unknownText}'라고 쓴다.",
  "blocks": [{ "type": "bullets" }]
}
```

### 예시 2: 표 열 너비와 글자 크기 변경

`content.json`: `"widths": [20, 80]` / `presentation.json`: `"styles": { "body": { "size": 11, ... } }`

## 코드 변경이 필요한 경우

다음은 템플릿만으로 되지 않는다. 코드와 테스트를 함께 수정한다.

- 새 블록 유형(이미지, 코드 블록 등): `document_model.py`, `template_store.BLOCK_TYPES`, 각 렌더러
- 새 시스템 생성 섹션(문서 정보·참고 문서 외): `template_store.SYSTEM_SECTIONS`, `builder._system_section`
- 새 출력 형식(DOCX 등): `DocumentRenderer`를 구현하고 `rendering.get_renderer`에 등록. 같은 저장 데이터로 출력된다.
- 시스템이 제공하지 않는 새 입력 데이터를 문서에 넣는 변경

## 반영·검증 절차

1. 템플릿 파일을 수정하고 `manifest.json`의 `version`을 올린다. `pipeline.extract`는 문서에서 수집할 사실, `pipeline.write`는 근거를 최종 설명으로 바꾸는 규칙이다. 출력 스키마·필수 섹션/블록·출처 검증은 코드에서 강제한다.
2. 템플릿 검증과 렌더링 테스트:
   `docker compose run --rm --no-deps ai-service python -m unittest test_product_description -v`
   (섹션 ID 중복, 알 수 없는 블록 유형, 표 너비 불일치, 필수 스타일 누락 등은 `TemplateError`로 거부된다.)
3. 새 제출 또는 "다시 생성"으로 신규 작업을 만든다. 기존 완료 PDF는 바뀌지 않는다.
4. 생성된 PDF를 PNG 등으로 렌더링해 한글 글꼴, 줄바꿈, 긴 표의 쪽 넘김, 머리글/바닥글을 확인하고 원문 근거를 대조한다.

`quality.chunking.maxChunkBytes`는 한 번에 모델에 전달하는 최대 UTF-8 바이트 수이며 실제 값은 모델 문맥 예산에 맞춰 추가로 낮아질 수 있다. `overlapCharacters`는 청크 경계에서 함께 보는 문자 수다. 모든 입력 문자는 청크에 포함되고, 이 값은 문서를 자르지 않고 요청 크기만 제한한다. `quality.output.extractionTokens`와 `generationTokens`는 각각 사실 추출과 문장 작성의 JSON 출력 상한이다. `quality.evidence.requireExactQuote`와 `minimumQuoteCharacters`는 인용 근거 확인 규칙이다. 결과를 원문과 대조한 뒤 JSON 지침부터 조정한다. 품질 설정 변경은 체크포인트 키에도 반영된다.

글꼴은 `presentation.json`의 `fonts.*.candidates`에서 존재하는 첫 경로를 사용한다.
컨테이너는 `fonts-nanum`(NanumGothic)을 설치한다(`ai-service/Dockerfile`).
글꼴 경로는 실행 환경에 의존하므로 스냅샷은 경로 목록을 기록하되 글꼴 파일 자체는 기록하지 않는다.

## LLM 설정과 연결 지점

| 환경변수 | 설명 |
|---|---|
| `LLM_MODE` | `mock` 또는 `real`. 없으면 설정 오류. compose 기본값은 `mock` |
| `MOCK_LLM_DELAY_SECONDS` | mock 지연(진행 상태·중단 확인용). compose 기본값 3 |
| `EX_API` | 실제 모드의 API 키. 비어 있거나 `inputlater`면 `LLM_NOT_CONFIGURED` |
| `LLM_MODEL` | 실제 모드의 모델명 |
| `LLM_PROVIDER` | 실제 제공업체(`ollama` 또는 외부 API 어댑터) |
| `OLLAMA_BASE_URL` | Ollama API 주소. Compose 네트워크의 기본값은 `http://ollama:11434` |

- mock 모드는 외부 API를 호출하지 않으며 결과와 PDF에 템플릿의 `mockLabel`("개발용 모의 생성")을 표시한다.
- 실제 모드는 mock으로 자동 대체하지 않는다. 키가 없으면 `LLM_NOT_CONFIGURED`, 키가 있어도 제공업체 구현 전에는
  `LLM_PROVIDER_NOT_IMPLEMENTED`를 반환한다.
- 제공업체 선택은 `llm.py`의 `provider_from_env()`가 담당한다. 새 API는 `LlmProvider` 계약의 어댑터를 추가해 연결한다. `generate()`는 저장 전 구조 검증을 받는 `{"sections": [...]}` 구조를 반환한다.
- Ollama의 모델명은 `.env`에서 교체한다. 템플릿 JSON에는 제공업체와 무관한 작성 지침, JSON 출력 구조, 근거·청크 품질 설정을 둔다.
- Ollama 로컬 추론은 CPU에서 문서 분량에 따라 수십 분이 걸릴 수 있다. 실제 서비스 경유 검증은 전체 입력 청크와 결과 PDF 완성을 기준으로 한다.

## 서비스 간 API (ai-service)

- `POST /api/v1/product-descriptions/content`: 문서·검증 결과·경고를 받아 `{template, document, generation}` 반환.
  `decision`은 `READY` 또는 `READY_WITH_WARNINGS`만 허용한다. 클라이언트 연결이 끊기면 LLM 호출을 취소한다.
- `POST /api/v1/product-descriptions/render`: `{format, document, presentation}`을 받아 PDF 바이트 반환.
- 오류: `{"detail": {"code", "message"}}` (`LLM_NOT_CONFIGURED`, `LLM_PROVIDER_NOT_IMPLEMENTED`, `LLM_RESPONSE_INVALID`,
  `TEMPLATE_INVALID`, `RENDER_FAILED`).
