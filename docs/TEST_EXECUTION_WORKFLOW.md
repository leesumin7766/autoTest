# 시험 실행 골격 (execution-v1)

## 현재 범위

생성된 TC 세트 → 5depth 기능별 실행 그룹 → Playwright 수행·증거 수집 → LLM P/F/N/A 판정 → 사용자 검토의 구조다.

이번 구현은 **준비, 범위 선택, 실행 요청, 중단, 원본 고정, 실행 이력·미판정 결과 조회**까지다. 실제 브라우저 조작과 LLM 호출은 아직 연결하지 않는다. 미실행 항목을 임의로 통과·실패·N/A 처리하지 않는다. 화면에서 두 연결 상태를 명시한다.

## 책임과 저장

- test-management-service: 제출 소유권, 현재 TC 검증, V23 `test_execution_runs`, 상태 전이와 조회. 기존 TC 소유권과 DB를 재사용한다.
- test-execution-service: 후속 실행 워커·오케스트레이션 연결 지점. 현재는 빈 서비스이며 이번 단계에서 실행했다고 가장하지 않는다.
- crawler-service / Playwright: 후속에 TC·메뉴 경로·시험 조건을 받아 실제 동작과 증거 수집.
- ai-service / LLM: 후속에 TC 기대 결과와 관찰 증거를 받아 판정·사유·관련 증거 참조 반환. TC 문서 내용은 지시가 아닌 데이터다.

실행마다 TC 원문, 메뉴 버전, 템플릿 버전, URL, 시험 메모와 선택 범위를 JSON snapshot에 고정한다. TC id로 항목을 식별하고 featureId로 묶는다. 경로 길이가 짧아도 원본 기능을 유지하고, `scope:` 공통·문서 항목은 COMMON 그룹으로 보존한다. 표시용 경로 문자열로 병합하지 않는다. 원본 TC 결과는 변경하지 않는다.

로그인 비밀번호는 메모에 저장하지 않는다. 기존 Playwright 설정·암호화 자격정보를 실행 워커에 안전하게 연결하는 작업은 후속이다. 현재 URL은 실행 대상을 기록할 뿐 실제로 접속하지 않는다.

시험 준비의 `Test connection` 팝업에서는 화면 수집과 같은 `/playwright-crawls`의 CONNECTION 작업을 별도로 호출한다. 현재 서버 또는 등록된 원격 PC를 선택하고 접속 권한 확인 후 수행한다. 로그인·TC 실행은 시작하지 않으며 저장된 화면 수집 설정을 변경하지 않는다. 성공(HTTP 2xx/3xx)은 초록색 표시, HTTP 오류 응답은 확인 필요로 표시한다. URL·실행 위치·PC 변경 시 이전 확인을 무효화한다. 연결 확인은 현재 화면 세션에 한하며 새로고침 후 재확인한다.

## API

기본 경로 `/api/submissions/{submission}/executions`, 헤더 `X-Member-Id`. 응답은 `Cache-Control: no-store`.

| 요청 | 동작 |
| --- | --- |
| GET `?run={runId}` | 선택 실행 또는 최근 실행·이력 조회 |
| POST `{tcSetId,targetUrl,notes,caseIds?}` | 실행 준비. caseIds 생략 시 전체, 빈 배열은 거부 |
| POST `/{run}/start` | PREPARED → WAITING_EXECUTOR. 실제 브라우저 실행은 하지 않음 |
| POST `/{run}/cancel` | 준비·대기 실행 → CANCELED. 재실행은 새 준비로 별도 이력 생성 |

준비·시작 시 소유권과 원본의 최신 상태를 검사한다. 과거 이력은 원본 변경 후에도 조회할 수 있다. 제출 행 잠금과 부분 유니크 인덱스로 제출당 대기 요청을 하나로 제한한다. 동일 실행의 시작·중단 재요청은 멱등이다. 자동 소비 워커가 없으므로 대기 상태는 서버 재시작 후에도 유지된다.

## 후속 실행·판정 계약

각 items 항목은 `caseId`, `featureId`, `tc`, `executionStatus`, `judgmentStatus`, `result`, `actualResult`, `reason`, `evidence`를 가진다. 현재 수행·판정 상태는 PENDING, result는 null, 증거는 빈 배열이다.

후속 구현은 다음을 추가한다. 아직 이 상태나 콜백을 API가 처리한다고 가정하면 안 된다.

1. 워커의 원자적 claim, attempt fencing, heartbeat/lease, 재시작 복구, 취소 확인.
2. 실행 조건·데이터·로그인 설정과 기능별 이동·TC 절차를 Playwright에 전달.
3. 실제 수행 결과와 증거 참조(스크린샷, DOM, 관련 네트워크)를 저장. 자동화 오류는 제품 F와 분리.
4. LLM이 기대 결과와 증거를 비교해 P/F/N/A·사유·인용 증거를 반환. 근거 부족은 미판정 유지.
5. 사용자 결과 검토·수정·재시험, F의 결함 후보 연결. 실행 실패를 곧바로 제품 결함으로 확정하지 않음.

## 검증

`MenuTcIntegrationTest`는 격리된 `_test` DB에서 실제 업로드→TC 생성→실행 준비, 권한, 잘못된 URL/TC 선택, 원본 고정, 중복 대기 방지, 시작/중단, 원본 변경 시 시작 차단을 검증한다. `scripts/verify-test-execution-ui.cjs`는 격리 API fixture로 브라우저 흐름을 검증한다. 운영 V23 적용 전에는 AGENT.md의 DB 백업 규칙을 따른다.
