# 메뉴트리 기반 TC 생성기

## 생성 방식

사용자의 최신 요청에 따라 새 생성기는 LLM 호출 없이 프로그램 코드와 버전 관리된 시험 템플릿으로 동작한다. 기존 LLM TC API는 보존하며 새 흐름과 구별한다.

`test-management-service/src/main/resources/tc-templates/menu-tc-v1.json`은 사용자 제공 TC 예시의 104개 시험 기준이다. 제품 경로·P/F/N/A·실제 결함 기록은 가져오지 않고 시험 항목·입력·기대 결과·품질 분류·결함 작성 예시만 관리한다. 적용 표준 적합성을 보증하는 목록으로 취급하지 않는다.

- 로그인: 기능당 27건.
- 주요기능·부가기능·CRUD: 기능당 9건. 주요·부가의 첫 기능정확성 항목과 결함 예시 정도를 구분한다.
- 제품별 공통 39건, 제품설명서 7건, 사용자취급설명서 22건을 한 번씩 추가한다.
- CRUD는 원본 분류를 유지한다. 모든 CRUD 동작이 존재한다고 가정하거나 한 행을 네 기능으로 임의 분리하지 않는다.
- 비적용 가능 항목도 모두 생성하며 실행 단계에서 사유·증거와 함께 N/A 판정한다.
- 결과는 JSON `null`, Excel 빈 셀이다. 과거 결과나 결함 예시를 실제 시험 결과로 복사하지 않는다.
- 원본 예시의 마지막 FAQ 항목은 입력·기대 결과가 비어 있어, 시험 문구에 명시된 문서와 정보 제공 기준으로 보완했다. 제품의 실제 동작을 추정해 채우지 않았다.
- 경로가 같아도 행 ID·기능 설명이 다른 기능은 따로 생성한다. 고유 TC ID는 입력 출처·기능 ID·규칙 ID로 결정한다.

## 화면과 API

04 TC 생성 화면에서 승인 메뉴트리를 선택하고 기능구분을 확인한 뒤 생성한다. 원본에 구분이 없으면 로그인 이름은 로그인, 나머지는 주요기능으로 제안한다. 로그인·주요기능·부가기능·CRUD로 변경 가능하며 선택값은 생성 입력에 보존한다. 원본 메뉴트리를 변경하지 않는다.

서버는 소유권·사전 점검·문서 digest·메뉴 승인·완전성·버전을 확인한다. 제외 기능과 `MENU_OBSERVATION` 구조 행은 대상이 아니다. 제출 행 잠금으로 메뉴 편집과 생성을 조정하고 같은 입력 digest 재요청은 기존 생성 결과를 재사용한다.

V21 `menu_tc_sets`에 메뉴 작업 ID·revision·입력 digest·템플릿 버전·TC를 보존한다. 원본 변경 후 이전 결과는 조회 가능하나 변경됨으로 표시하고 다운로드를 차단한다.

기본 경로는 `/api/submissions/{submission}/menu-test-cases`, 헤더는 `X-Member-Id`다.

| 요청 | 기능 |
|---|---|
| GET `?set={setId}` | 이력·선택 결과·원본 변경 여부 |
| POST `{menuJobId,featureTypes:{기능행ID:기능구분}}` | 승인 메뉴에서 TC 생성·저장·동일 입력 재사용 |
| GET `/{setId}/download` | 저장 결과 Excel 출력 |

UI는 검색·25건 페이지 나누기·상세·이력·Excel·실행 입력 JSON 다운로드를 제공한다. TC 수정·최종 승인 및 Playwright 실행·판정은 다음 단계다.

Excel의 `TestCase` 시트는 사용자 예시의 16열 형식이며, `실행 정보`에는 전체 경로·기능 ID·규칙 ID·설명·절차·필요 정보·적용 조건·대상 URL·근거를 기록한다. `생성 정보`에는 출처·템플릿 버전·주의사항을 기록한다. 모든 값은 수식이 아닌 문자열이며 결과는 빈 셀과 P/F/N/A 선택 목록이다. Excel 편집은 서버에 자동 반영하지 않는다.

각 JSON TC는 기능 경로·설명·시험 기준·입력·기대 결과·근거와 `executionPlan`을 가진다. `executionReadiness=NEEDS_CONTEXT`, `executionMode=REVIEW_REQUIRED`이며 시험을 실행한 상태가 아니다. 메뉴트리만으로 실제 계정·테스트 데이터·선택자·정확한 기대값은 확정할 수 없어 이후 문서·수집 화면으로 구체화한다. LLM은 필요할 때 이 구체화에 사용할 수 있다. 이후 실행기는 실행 오류와 최종 판정을 구분해야 한다.

## 샘플 검증

`sample/menu sample/menu sample.xlsx`의 4개 시트를 독립 입력으로 처리한다. 첫 열을 무조건 제품명으로 가정하지 않는다. 1~5depth 및 `프로그램` 열을 읽고 상위 경로를 이어받되 부모 변경 시 이전 하위 경로를 초기화한다. `-`는 경로 이름이 아니다. 설명이 없어도 기능구분이 있는 행은 설명 미기재 표시와 함께 보존한다. 빈 행·구분 행은 행 번호와 사유를 기록한다. 원본 수식·지시문은 실행하지 않는다.

| 원본 시트 | 기능 | 로그인 / 주요 / 부가 / CRUD | TC |
|---|---:|---|---:|
| 메뉴트리 | 133 | 1 / 61 / 71 / 0 | 1,283 |
| 메그메뉴트리의 사본 | 86 | 0 / 45 / 41 / 0 | 842 |
| 나일소프트-메뉴트리의 사본 | 172 | 3 / 94 / 75 / 0 | 1,670 |
| 메뉴트리-에잇바이트의 사본 | 44 | 1 / 8 / 8 / 27 | 482 |
| 합계 | 435 | | 4,277 |

두 번째 시트의 음영기복도·도움말·설정은 설명이 비어 있지만 모두 포함했다.

저장소 루트에서 재현:

```powershell
.\gradlew.bat :test-management-service:generateMenuTcSamples
.\gradlew.bat :test-management-service:test --tests '*MenuTc*Test' --tests '*FunctionalMenu*Test' --tests '*TcGenerationServiceTest'
```

CLI와 서버는 같은 `MenuTcGenerator`·`MenuTcWorkbook`을 사용한다. 결과는 `output/menu-tc-samples/product-1~4.xlsx` 및 `.json`, `generation-report.json`에 있다.

별도 검증 `scripts/verify-menu-tc-samples.py`는 번들 Python/openpyxl로 원본의 기능 수·분류·예상 TC 수를 다시 계산한다. 모든 기능 커버리지·설명 없는 기능 보존·미기록 결과·Excel/JSON 일치·해시·수식 없음·P/F/N/A 선택 목록을 확인하고 `verification-report.json`에 기록한다.

Vite 실행 후 `node scripts/verify-menu-tc-ui.cjs` 및 `node scripts/verify-frontend.cjs`로 새 화면과 기존 기능의 브라우저 검사를 수행한다. 실제 생성 JSON·Excel을 모의 API 응답으로 사용한다. 별도의 `MenuTcIntegrationTest`는 실제 PostgreSQL `autotest_test`에서 HTTP→V21→저장·멱등 재사용·권한·다운로드·원본 변경 차단을 검증하고 트랜잭션을 rollback한다. 운영 DB를 사용하지 않는다.

기존 운영 서버에는 이 브랜치의 백엔드 빌드·재시작과 V21 반영이 필요하다. 운영 DB 마이그레이션 전에는 기존 지침대로 백업한다. 이번 작업의 샘플 생성·검증은 운영 DB를 변경하거나 제품 시험을 실행하지 않는다.
