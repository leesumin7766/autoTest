# 조회 실행 명세 1차 보완 — 2026-10-27

## 범위

TestCase 16열과 기존 시험 내용은 그대로 두고 실행 정보와 내부 `executionPlan`을 보완한다. 첫 적용은 조건 입력이 없는 단순 조회의 FEATURE-01·CRUD-01·CRUD-08이다. 기능명에서 동작이 READ로 확인되고 설명에 검색·필터·변경 등의 다른 동작이 없는 경우만 새 명세를 생성한다. 다른 규칙과 기존 저장본은 execution-spec-v1 경로를 유지한다.

## 생성 내용

- 실행 명세 버전: `execution-spec-v2-read`.
- TestCase 내용 해시, 기능 전체 경로, 수집 메뉴 키, 시작 URL과 저장 접속 설정 참조를 기록한다.
- 화면 이동 → 조회 전제 확인 → 로딩 확인 → 결과 비교의 단계 코드를 기록한다. 사람이 읽는 기존 절차 문장은 보존한다.
- 각 기대 조건의 원문을 그대로 연결하고 화면 대상·조회 전제·조건별 비교 근거가 빠진 위치와 사유를 `missingBindings`에 기록한다.
- 수집 메뉴 키가 있더라도 제목·주소까지 실제 관찰과 연결된 것으로 확정하지 않는다. 기대값과 선택자를 만들어 넣지 않는다.
- 새 명세 버전은 생성 digest에 포함되어 같은 메뉴라도 이전 TC 세트를 재사용하지 않는다. TC ID와 TestCase 셀 값은 유지한다.

## 준비·실행

TMS의 시험 준비에서 새 명세가 미완성이면 `NEEDS_CONTEXT/NOT_EXECUTED`와 구체적인 사유를 표시한다. AI 실행기에서도 내용을 다시 검증한다. READY라는 표시만으로 실행하지 않으며 원본 해시·기능 경로·단계·수집 메뉴 연결·근거·기대 조건 전체 범위를 확인한다. 검증을 통과하지 못한 새 명세는 브라우저·Qwen·Jev를 호출하지 않는다. 준비 검사 증거를 최초 화면 진입 실패로 오인하지 않는다.

AI capabilities가 지원 명세 버전을 제공하고 TMS는 실행 시작 전에 이를 확인한다. 이전 AI 서비스에 새 명세를 전달해 Qwen 일반 계획으로 처리하는 상황을 막는다. 새 명세를 수행하려면 TMS와 AI 소스를 함께 반영해야 한다.

이번 실행기가 지원하는 완료 명세는 수집 근거의 메뉴 키·주소·제목에 연결된 NO_FILTER 조회와 명시적 TEXT_PRESENT 조건이다. 정확히 일치하는 현재 링크로 한 번 이동하고 대상 URL·본문·제목을 확인한다. Qwen으로 경로를 추측하거나 같은 메뉴를 반복 클릭하지 않는다. 읽기 명세는 변경 허용 체크와 관계없이 브라우저의 쓰기 차단을 유지한다.

명세의 확인 조건을 기존 증거 검사와 Jev 판정 경로에 전달한다. 조건 미충족·잘못된 대상·증거 부족을 P로 확정하지 않는다. 정형 기대 문구 비교와 Jev 제안이 충돌하면 검토 대상으로 남긴다. 일반화된 데이터 값 비교·부정 조건·상태 불변 검증은 아직 지원 범위가 아니다.

## 검증

- Java 18건 통과: 조회 명세 생성·사유·동작 유형 구분·원본 내용 유지·내용 변경 감지·실행기 버전 호환·시험 준비 API와 기존 생성/절차 회귀. 통합 검사는 격리 autotest_test DB에서 수행했다.
- Python 37건 통과: 미완성 명세 차단, 미지원 버전·변경된 TC·누락 조건·위조 근거·동명 다른 링크·사이드바 단독 관찰 거부, 기존 로그인·선택·판정 회귀. 제목 일치 검사를 추가한 뒤 해당 명세 검사 4건을 다시 통과했다.
- 실제 Chromium 합성 시험: 정상 화면 P, 잘못된 화면 F, 잘못된 화면에 stub 판정기가 P를 제안하면 미판정. Qwen과 실제 Jev는 호출하지 않았으며 실제 제품 품질 검증이 아니다.
- 기존 4개 제품 샘플 4,466 TC의 TestCase 셀을 변경 전후 모두 비교해 동일함을 확인했다. 새 조회 명세 54개는 자동으로 READY 처리하지 않았고 연결 필요 216건을 표시했다.
- Node 24.21.0의 프런트엔드 빌드와 공통 오류 메시지 146개 매핑 검사를 통과했다.

재현:

```powershell
.\gradlew.bat :test-management-service:test --tests '*TcReadSpecificationTest' --tests '*TcProcedureWriterTest' --tests '*MenuTcGeneratorTest' --tests '*ExecutionFeatureClassificationTest'
# 기존 ai-service 이미지의 의존성으로 읽기 전용 소스를 마운트해 단위 검사를 수행한다.
# 실제 Chromium 합성 검사는 Docker와 crawler-service의 Node 의존성이 필요하다.
node scripts/verify-read-spec-browser.cjs
```

## 근거 연결 UI/API — 2026-11-04

`dev-document`에 `dev-manual-review-next`를 fast-forward 통합한 후 `dev-read-spec-bindings`에서 구현했다. 원격 참조를 갱신해 다른 로컬·원격 브랜치의 커밋이 통합 기준에 모두 포함된 것을 확인했다. 이번 변경의 commit/push와 운영 API 배포는 수행하지 않았다.

05 시험 실행 → 시험 준비 → **조회 실행 명세 근거 연결**에서 `execution-spec-v2-read` TC를 선택한다.

1. TC의 메뉴 키에 연결된 고정 수집 근거에서 대상 화면을 선택한다. 주소·제목을 임의로 입력하지 않는다. 화면 근거가 없으면 수집·메뉴 연결과 TC 생성을 먼저 보완한다.
2. TC 입력·사전조건 또는 TC에 포함된 문서 근거를 선택하고 실제 원문 인용을 연결한다. 사용자가 조건 입력 없는 조회임을 확인한다.
3. 기대 조건별로 TC 기대 결과·입력 또는 문서 근거를 연결한다. 원래 기대 조건을 일부 문구로 축소하거나 TC 본문을 수정하지 않는다.
4. 미완성 연결도 저장할 수 있다. 전체 근거가 연결되고 기존 실행기가 지원하는 명시적 TEXT_PRESENT 조건인 경우만 READY다. 설명형·복합 조건은 근거를 저장해도 NEEDS_CONTEXT로 유지한다.
5. 저장 후 **시험 실행 준비**로 새 실행을 만든다. 이전 준비·실행 이력은 저장 당시 명세 스냅샷을 유지하며 자동으로 다시 실행하지 않는다. 엑셀 수정·재가져오기는 지원하지 않는다.

API는 기존 `X-Member-Id` 소유권 검사 아래 제공한다.

```text
GET /api/submissions/{submission}/menu-test-cases/{set}/cases/{caseId}/read-bindings
PUT /api/submissions/{submission}/menu-test-cases/{set}/cases/{caseId}/read-bindings
```

GET은 `tc`, `targets[{menuKey,href,label}]`, `stale`을 반환한다. PUT은 다음 필드를 받는다. 상태·단계·누락 사유는 서버에서 계산한다.

```json
{
  "revision": 0,
  "sourceTcDigest": "GET에서 받은 원본 TC 해시",
  "target": {"menuKey": "reports", "href": "https://example.test/reports", "heading": "Reports"},
  "readContext": {"mode": "NO_FILTER", "confirmed": true, "oracle": {"kind": "TC_INPUT", "quote": "실제 입력 원문의 일부"}},
  "checks": [{"id": "C1", "expected": "원래 requirementQuote 전체", "oracle": {"kind": "TC_EXPECTED", "quote": "실제 기대 결과 원문의 일부"}}]
}
```

문서 근거는 `kind=DOCUMENT_EVIDENCE`, TC `evidence` 배열의 정수 `index`, 실제 `quote`로 지정한다. 미연결 target/readContext는 null, 미연결 check는 배열에서 생략한다. 저장은 제출 행 잠금과 TC별 `bindingRevision`으로 오래된 수정의 덮어쓰기를 막는다. `boundBy`, `boundAt`은 마지막 저장자·시각이며 전체 변경 이력은 아니다. 기존 `menu_tc_sets.output` JSON에 저장하므로 새 migration은 없다. 원본 변경·다른 세트·다른 회원 접근과 위조 근거를 거부한다.

### 이번 검증과 한계

- 새 Java 단위 검사 및 격리 `autotest_test` API 검사: 원문·수집 근거 검증, 부분 저장, 설명형 조건 차단, 원본 보존, 소유권, revision 충돌, stale 거부, 새 시험 준비에 저장 명세 반영, 기존 실행 스냅샷 보존.
- Java 저장 JSON → 실제 Python `prepare_read` 계약 검사 통과. 네트워크 없는 임시 컨테이너로 실행했다.
- 관련 Python 회귀 22건 통과. 실제 Qwen/Jev나 제품에 접속하지 않았다.
- 5173에서 합성 API로 근거 선택·저장·재조회·409 안내 UI, 기존 시험 실행 UI, 저장 작업 복원 검사 통과. 프론트 빌드와 오류 메시지 155개 매핑 검사 통과.
- 기존 `TcReadSpecificationTest.readRecipeHasStableStepsAndSpecificMissingBindingsWithoutInventedData`는 TC 개수 기대 84 / 실제 85로 실패한다. 해당 생성기·템플릿·테스트는 이번 변경에서 수정하지 않았다. 나머지 해당 클래스 3건은 통과했다.
- 운영 API 이미지 재빌드·반영, 실제 제품의 연결·수행·판정 품질은 후속이다. 이 UI는 일반화된 선택자 편집이나 데이터 값·건수 비교 기능이 아니다.

재현 명령:

```powershell
.\gradlew.bat :test-management-service:test --tests '*ReadSpecificationBindingsTest' --tests '*ReadBindingsIntegrationTest'
$env:FRONTEND_QA_URL = 'http://127.0.0.1:5173'
node scripts/verify-read-bindings-ui.cjs
node scripts/verify-test-execution-ui.cjs
node scripts/verify-saved-workspaces.cjs
node scripts/verify-api-errors.mjs
# AI 의존성이 있는 환경, PYTHONPATH=ai-service:
python scripts/verify-read-bindings-contract.py test-management-service/build/test-fixtures/read-bindings.json
```
