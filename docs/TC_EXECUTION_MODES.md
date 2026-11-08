# TC 수행 방식: 자동 · 수동 · 제외 (2026-11-08)

TC 마다 "누가 어떻게 시험하는지"를 정한다. TC 본문(TestCase 16열)과 실행 명세는 바꾸지 않고, TC 옆에 `executionDecision` 으로만 저장한다.

| 방식 | 뜻 | 05 시험 실행에서 | 결과 |
|---|---|---|---|
| 자동 | 프로그램이 브라우저로 수행(Qwen 계획·Playwright·Jev 판정) | 실행기로 보냄 | 자동 판정 → 07 에서 검토 |
| 수동 | 시험원이 직접 확인(문서 대조·도구·DB·네트워크 등) | 실행기로 보내지 않음(`MANUAL`) | 07 '판정 검토'에서 판정·근거 입력 |
| 제외 | 이 제품에 해당하지 않음 | 기본 선택에서 빠짐. 골라 넣으면 `EXCLUDED`(사유 포함) | 판정 없음, 보고서에 '제외'와 사유 |

## 기본값

저장한 적 없는 TC 는 템플릿 표시로 정한다.

- `automationMode` 가 `MANUAL_OR_TOOL`(공통·문서 시험) 또는 `ADDITIONAL_EVIDENCE`(예: 비밀번호 암호화 저장·전송 확인) → **수동**
- 나머지 → **자동**

예) 기능 7개 제품: 181개 중 자동 105 · 수동 76(공통 68 + 추가 증거 8).

## 화면

- **04 TC 생성**: 요약(자동·수동·제외 수, 자동 중 '정보 필요' 수), 보기 필터(자동/수동/제외/자동·정보 필요), 일괄 지정(지금 보이는 TC 전체 또는 기능구분별), 행별 수행 방식 선택. 제외는 사유를 받아야 저장된다. 일괄 지정은 사유를 적어 제외한 TC 를 덮어쓰지 않는다(일괄 '제외'만 예외). 조회 TC 근거 연결 편집기도 이 화면에서 바로 쓸 수 있다(05 에도 그대로 있음).
- **05 시험 실행**: 시험 범위 요약에 자동·수동 수와 빠진 제외 수를 표시한다. 기본 선택은 자동+수동(제외 빠짐). 실행 상태에 '수동 · 결과 입력 대기' 지표와 필터가 있다.
- **07 결함 검토**: 수동 TC 결과 입력 대기 안내, '수동 TC만 보기', 행에 '수동' 표시와 '입력 대기'. 판정 검토에서 판정(P/F/N/A)과 근거를 입력하면 기존 검토 이력(변경 전후·작성자·시각)에 남는다.

수동·제외 TC 만 고른 실행은 실행기 없이 바로 `COMPLETED` 로 닫혀 결과를 입력할 수 있다. 자동 TC 가 하나라도 있으면(사전 정보가 부족해도) 기존처럼 준비 상태로 두고 시작 때 사유를 알린다.

## API

```text
GET /api/submissions/{submission}/menu-test-cases[?set=]
  → set.output.testCases[].decision {mode, defaultMode, source: USER|DEFAULT, reason, decidedAt}
  → set.output.decisionSummary {AUTO, MANUAL, EXCLUDED}, decisionRevision
PUT /api/submissions/{submission}/menu-test-cases/{set}/decisions
  {"revision": 0, "decisions": [{"caseId": "...", "mode": "AUTO|MANUAL|EXCLUDED", "reason": "제외면 필수, 500자 이내"}]}
```

- 일부 TC 만 보내도 된다(최대 3,000개). `revision` 이 다르면 409 `TC_DECISION_CONFLICT`.
- 기본값과 같은 방식을 사유 없이 저장하면 기록을 지워 기본값으로 되돌린다.
- 원본(메뉴·문서)이 바뀐 TC 세트는 409 `TC_SOURCE_CHANGED`. 소유자만 바꿀 수 있다.
- 실행 조회의 `run` 에 `manualCount`·`manualDoneCount` 가 추가됐다. 보고서 각 TC 에 `수행 방식: 자동 / 수동(시험원 입력) / 제외` 줄이 붙는다.

## 함께 고친 것

- 실행기가 공통 그룹을 처리할 때 그룹의 모든 TC 를 '정보 필요'로 덮어쓰던 부분을, 자동 수행 대기 TC 만 표시하도록 바꿨다(수동 TC 가 덮이지 않게).

## 검증

- Java: `TcExecutionDecisionsTest`(규칙 4건), `TcExecutionDecisionsIntegrationTest`(저장·충돌·소유권·저장본 무결성·시험 준비·수동만 실행·결과 입력·보고서), `MenuTcIntegrationTest` 기대값 갱신(공통 TC 기본 수동, 자동으로 바꾸면 기존 사전 정보 차단 유지). 전체 184건 중 181 통과(2건 기존 문제, 1건 건너뜀).
- 이 PC 운영 화면(가짜 업무 사이트 작업): 기본 105/76, 행 제외(사유 입력)·일괄 지정(제외 유지 안내)·05 범위 요약·수동만 실행 즉시 완료·07 결과 입력(P 1, 대기 3→2)·보고서 '수행 방식' 줄 확인.

## 시험 자료: 자동 TC 의 '실행 시 필요한 정보' 입력 (2단계, 2026-11-08)

자동 TC 는 생성 때 `requiredContext`(예: "대상 기능의 구체적인 입력값·기대값과 독립 비교 근거", "저장된 접속 설정과 시험 계정의 역할·권한")를 갖는다. 이전에는 적을 곳이 없어 실행하면 모델이 추측했다. 이제 04 의 TC 상세 → **시험 자료**에서 항목마다 답(입력값·대상 데이터·기대 데이터·계정 역할)과 추가 메모를 적는다.

- 저장 위치: TC 옆 `executionContext {items:[{need,value}], note, revision, updatedBy, updatedAt}`. TC 본문·명세는 그대로.
- 상태: 필요 정보 없음 / 정보 필요 / 정보 일부 입력 / 시험 자료 입력됨. 조건 입력 없는 조회 TC 는 기존 '조회 실행 명세 근거 연결'을 쓰며 여기서 받지 않는다.
- 요약의 "자동 중 정보 필요" 수와 '자동 · 정보 필요' 필터는 이 상태(조회 TC 는 근거 연결 완료 여부)로 센다.
- 실행: 시험 준비 스냅샷에 실리고, 실행기가 AI 요청의 각 TC 에 `userContext {source: TESTER_SUPPLIED, items, note}` 로 보낸다(작성자·시각 제외).
- AI(ai-service `test_execution.py`):
  - Qwen 계획: userContext 를 "시험원이 준 시험 자료"로 안내. 입력값·대상 선택·기대값에 쓰되 **관찰 증거가 아니며** expectedResult 요구사항을 대신하지 않는다. 값이 없으면 지어내지 말고 NEEDS_INPUT.
  - 조건의 기대값 후보(enum)에 userContext 값이 추가된다(없으면 추가되지 않음 — 테스트로 확인).
  - Jev 판정: userContext 는 "무엇이 관찰돼야 하는지"를 알려 주는 자료이고 관찰됐다는 근거로 쓰지 말라고 질문에 명시.
- 비밀값 차단: `비밀번호: …`, `password=…`, `token=…`, `Bearer …` 같은 값은 `TC_CONTEXT_SECRET` 으로 거절한다('비밀번호'라는 단어 자체는 허용). 시험 계정 비밀번호는 화면 수집의 로그인 정보(암호화 저장)에만 둔다.

API:

```text
PUT /api/submissions/{submission}/menu-test-cases/{set}/cases/{caseId}/context
  {"revision": 0, "items": [{"need": "requiredContext 의 문구 그대로", "value": "1,000자 이내"}], "note": "2,000자 이내"}
```

오류 코드(화면 문구 등록): `TC_CONTEXT_INVALID`(요구하지 않는 항목), `TC_CONTEXT_TOO_LONG`, `TC_CONTEXT_SECRET`, `TC_CONTEXT_CONFLICT`(revision), `TC_CONTEXT_READ_BINDING`, 수행 방식의 `TC_DECISION_INVALID`·`TC_DECISION_REASON_REQUIRED`·`TC_DECISION_CONFLICT`. 화면은 서버 문구를 그대로 보여 주지 않고 코드별 등록 문구만 보여 주므로, 코드를 추가하면 `TestManagementErrorCode` 와 `frontend/src/serviceErrorMessages.ts` 에 함께 넣는다(`scripts/verify-api-errors.mjs`, 전체 테스트의 frontend 단계에서 검사). 이번에 11-06 허브 코드 2개(`HUB_VERSION_REQUIRED`, `HUB_UPDATE_REQUIRED`)가 화면 쪽에 빠져 있던 것도 채웠다.

검증:
- Java: `TcExecutionContextTest`(상태·검증·비밀값·조회 TC·전송 형태), 통합 테스트(저장·소유권·비밀값 거절·충돌·준비 스냅샷·저장본 무결성), `MenuTcIntegrationTest` 의 실제 실행기 → 가짜 AI 전송에서 `userContext` 수신 확인. 전체 188건 중 185 통과(2건 기존, 1건 건너뜀).
- Python: `test_user_context.py` 4건(전달·문구·기대값 후보 포함/미포함). 전체 147건 통과. 테스트는 이제 배포 이미지가 아니라 **작업 폴더의 현재 소스**로 실행한다.
- 이 PC 운영 화면: TC-007-002 에 시험 자료 저장 → '시험 자료 입력됨', 정보 필요 110 → 109, 비밀값 입력 시 전용 안내, 다시 열면 저장값 유지.
- 실제 모델(Qwen)·Jev 로 실행해 품질이 나아지는지는 확인하지 않았다(모델 실행 제외 범위).

## 다음 단계(미구현)

- 같은 기능의 여러 TC 에 같은 시험 자료를 한 번에 적용(기능별 공통 자료).
- 수동 TC 의 증거 파일 첨부와 결과 Excel 반영.
