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

## 다음 단계(미구현)

- 자동 TC 의 '정보 필요'(시험 데이터·기대값·계정 역할·파일)를 TC 별로 입력해 Qwen 계획·Jev 판정에 넘기는 입력란.
- 수동 TC 의 증거 파일 첨부와 결과 Excel 반영.
