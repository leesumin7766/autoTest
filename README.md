# autoTest — 메뉴트리 기반 TC 생성과 시험 실행

기업이 제공한 기능리스트·매뉴얼과 실제 화면을 바탕으로 메뉴트리를 작성하고, TC 생성·시험 수행·결함 검토를 지원하는 프로젝트입니다.

2026-10-27 `dev7` 작업 기준입니다. 로그인 27개·주요/부가 9개·CRUD 16개 TC를 모두 생성하고, 원본 보존과 Playwright 실행 선택을 분리합니다. [템플릿 규칙](docs/TC_TEMPLATE_DEV6.md)을 참고하세요. 최근에는 메뉴 포함·배치 검토, 한국어 우선 수집, 미완료 메뉴 선택 재수집, 실행기 점유 현황, 진행 표시, 최초 URL 확인과 저장 계정 정상 로그인 실행을 보완했습니다.

**개발 원칙:** `TestCase`는 사람이 검토하는 시험 정의, `실행 정보`는 프로그램이 읽는 수행 명세입니다. TestCase 16열과 기존 시험 내용은 유지하고 실행 정보와 실행기를 단계별로 연결합니다. 단순 조회부터 `execution-spec-v2-read`의 단계·부족 정보·준비 검사와 수행 경로를 연결했습니다. 현재 생성본의 누락 정보를 실제 근거에 연결하는 기능은 다음 단위이며, 전체 자동 실행 완료를 뜻하지 않습니다. [1차 구현·검증 범위](docs/READ_EXECUTION_SPECIFICATION.md)와 [현재 개발 계획](docs/DEVELOPMENT_PLAN.md)을 따릅니다. 이번 1차 보완의 운영 서버 반영은 별도입니다.

실제 NeoFlow 정상 로그인 한 건은 저장 계정 입력·한 번 제출·대시보드 진입까지 확인했습니다. 권한 등 TC의 나머지 기대 조건은 확인되지 않아 전체 판정은 미판정으로 보존했습니다. 한 건의 인증 성공을 전체 제품 자동 시험 성공으로 집계하지 않습니다. [정상 로그인 실행 계약](docs/SAVED_ACCOUNT_LOGIN_EXECUTION.md), [최초 URL 확인](docs/EXECUTION_INITIAL_ENTRY.md)을 참고하세요.

문서 접수, Jev 사전 점검, 로컬·원격 화면 수집, 통합 메뉴트리, 템플릿 TC 생성, **코드·로컬 Qwen 계획 → Playwright 수행 → Jev 판정 → 결과 저장** 프로토타입이 구현돼 있습니다. 아래 2026-10-22 검증 기록은 당시 입력·버전의 결과이며, 현재 버전의 전체 기능 품질을 보증하지 않습니다.

## 현재 구현 상태

sample3의 Server 자료로 새 01~05 흐름을 실제 검증한 [산출물·실행 보고서](docs/SAMPLE3_E2E_2026-10-22.md)를 우선 참고하세요. AUTO 메뉴 생성은 완주하지 못했고 검토 보완본으로 64기능/662TC를 생성했습니다. 선택 72건은 조회 자동화 오류 3건·사전 조건 미수행 69건으로 종료됐습니다. 이번 조회는 Jev 판정 전에 막혔으며, 완전 자동 시험 성공으로 집계하지 않습니다. 실제 드롭다운 선택지 수집과 오류 직후 화면 보존은 추가 수정·검증해 서버에 반영했습니다.

실행 품질 보강: 메뉴 탐색만으로 판정하지 않고 실제 조작 후 명시적 종료와 요구 조건별 증거를 확인합니다. 비동기 화면 대기, iframe 조작, 미수행/미판정 구분, 접속 설정 버전 검사, 증거 별도 저장·상세 조회를 반영했습니다. 이전 한 건 검증은 통신·저장 검증이며 제품 기능의 충분한 수행 검증과 구별합니다. 최신 수정 범위와 제약은 아래 문제 목록의 재수정 현황을 따릅니다.

현재 실행 실패의 HTTP 통신 수정과 우선순위별 남은 문제는 [문제 목록](docs/KNOWN_ISSUES_2026-10-22.md)에 기록합니다. 이번에는 실제 조회 TC 한 건의 로그인부터 결과 저장·UI·보고서까지 검증했으며, 선택된 530개 전체의 자동 시험 품질 검증은 남아 있습니다.

2026-10-22 단계 간 연결 점검에서 탭의 이전 데이터 유지, 실행 준비 상태 오표시, 오래된 AI 소스, Playwright/Chromium 불일치와 Gateway DNS 캐시를 수정해 서버에 반영했습니다. 실제 653개 TC 표시와 연결 검사·준비 버튼을 확인했습니다. 검증 범위와 남은 품질 과제는 [단계 간 연결 점검](docs/PIPELINE_AUDIT_2026-10-22.md)을 참고하세요.

| 화면 | 구현된 기능 | 남은 부분 |
| --- | --- | --- |
| 01 자료 등록 | 역할별 파일 선택, 업로드·파싱·원본 저장, Jev 진행·판정, 실패/차단 문서 교체·전달 복구 | 추출 품질·Jev 판정 보정 |
| 02 화면 수집 | 접속·로그인 테스트, 설정 저장, 현재 서버/원격 PC Playwright 수집, 화면·텍스트·메뉴 구조·이동 근거 | 수집 누락·이동 실패 상세 진단 개선 |
| 03 메뉴트리 | 문서·화면 고정, self-llm-v4 요청 ID·근거 번호 복원, 포함 행 강조·전체 포함·확실한 경로 복원, 기능 편집·분리·제외·승인·6열 Excel | 근거와 기능 사이 의미 연결의 사용자 검토 |
| 04 TC 생성 | 승인 메뉴 또는 기존 메뉴 업로드, 기능구분, 템플릿 생성·저장·검색·페이지·상세·Excel/실행 JSON | TC 직접 수정·최종 승인·버전별 검토 |
| 05 시험 실행 | TC별 독립 세션·확인 조건·증거, P 안전장치, 반복 쓰기 차단·재조회·텍스트 다운로드 확인 | 실제 제품 품질, 생성·삭제 데이터 정리와 격리 |
| 06 결함 검토 | 증거 조회, 사유를 남긴 판정 수정, 후보 확정·기각·추가 시험, 변경 이력 | 업무별 결함 분류·재현 절차 편집 |
| 07 리포트·재시험 | 제품·패치 버전, 선택/F·미판정 재시험 준비, 이전 실패 보존, Markdown 보고서 초안 | 기업용 서식·버전 간 비교 화면 |

UI는 Postman을 참고한 좌측 단계 탐색과 접기·펼치기, 표 페이지 나누기를 사용합니다. Inter 기본 13px, 코드 영역 JetBrains Mono, 주황색 주요 버튼과 한국어 안내를 사용합니다.

## 업무 흐름과 모델 역할

```text
기능리스트 + 매뉴얼(필수), 시험합의서(선택)
  → 업로드·파싱·저장 → 프로그램 검사 + Jev 사전 점검
  → 접속·로그인 확인 → Playwright 화면·구조 수집
  → 통합 메뉴트리 검토·승인 또는 기존 메뉴트리 업로드
  → 프로그램 코드 + 버전 관리 템플릿으로 TC 생성
  → 시험 준비 → TC별 Qwen 계획 → 독립 Playwright 세션·증거 수집
  → TC별 Jev 판정 → 사용자 결과 검토
  → 결함 검토 → 보고서 초안 → 패치 후 선택 재시험
```

| 역할 | 현재 사용 방식 |
| --- | --- |
| 문서 사전 점검 | 프로그램의 추출 품질 검사 + Jev의 역할·가독성·제품 일치 판단 |
| 메뉴트리 작성 | 공통 지침을 외부 LLM에 한 번 등록 → 제품별 프롬프트 전달 → JSON 가져오기·검토. 로컬 자동 생성 버튼은 숨김 |
| 현재 메뉴트리 TC 생성 | LLM 없이 Java 생성기와 menu-tc-v3.json 템플릿 |
| 시험 계획 | 코드 기반 메뉴 이동·조회/단일 조작 완료 기준 + 로컬 qwen3:8b 보완 계획 |
| 실제 수행 | Playwright, 관찰 요소 클릭·입력·선택·허용 링크 이동 |
| 시험 판정 | Jev가 실제 텍스트·수행 기록과 TC 기대 결과를 비교 |

시험 실행은 외부 생성형 LLM을 호출하거나 자동 대체하지 않습니다. **Jev 자체는 외부 TypeSafe API이므로 완전한 오프라인 구성은 아닙니다.** 비밀번호는 Qwen/Jev에 전달하지 않습니다. 기존 LLM TC API와 제품 설명 PDF 코드는 별도로 보존하며 PDF 신규 생성·재출력은 기본 비활성화합니다.

## 주요 기능과 현재 제약

### 문서와 사전 점검

- PDF, Excel(xls/xlsx), HWP/HWPX, Word(doc/docx)의 확장자·MIME을 검사하며 파일당 최대 100 MiB입니다. PDF OCR은 현재 수행하지 않습니다.
- 역할은 FUNCTION_LIST, MANUAL, AGREEMENT입니다. 앞의 두 역할이 필수이며 합의서 누락은 경고입니다. 같은 제출 ID 아래 역할별로 저장합니다.
- 원본은 SeaweedFS의 autotest-docs 버킷, 문서별 상태·추출 내용은 submission_files에 저장합니다. 문서 세트 상태와 AI 전달 상태는 별도입니다.
- AI 전달은 NOT_READY/PENDING/FAILED/DELIVERED/BLOCKED, 사전 점검은 READY/READY_WITH_WARNINGS/BLOCKED를 구분합니다. 명백한 제품 불일치·심한 손상은 차단하고 불확실성은 경고로 처리합니다.
- 전달 실패·만료 PENDING은 재업로드 없이 복구합니다. 파싱 실패/차단 문서는 정책에 따라 교체하고 기존 자료는 성공 전까지 보존합니다. 소유권·시도 번호·lease로 늦은 응답을 차단합니다.
- Jev 키 누락·장애 시 문서 사전 점검은 경고와 함께 규칙 검사 결과로 진행할 수 있습니다. 실행 판정의 근거 부족을 P로 처리한다는 뜻은 아닙니다.

### 화면 수집과 메뉴트리

- 현재 서버 실행을 기본으로 연결 확인 → 로그인 확인 → 설정 저장 → 화면 수집 순서로 사용합니다. 다른 PC에서만 대상에 접근할 수 있으면 원격 실행기를 사용합니다.
- 수집은 조회 중심이며 위험 동작을 제한합니다. 메뉴 경로·동일 URL의 다른 화면 상태·iframe 로딩·캡처·이동 근거를 구분하며 부분 수집을 전체 확인으로 취급하지 않습니다.
- 통합 메뉴트리는 기능리스트·매뉴얼·수집 결과를 고정하고 관찰한 웹 구조를 우선합니다. 근거의 형식적 일치와 의미적 연결 확인은 구분합니다.
- Excel은 1depth~5depth와 메뉴 설명의 6열입니다. 구조 행과 실제 기능 행을 구분합니다. 부분 초안·모의 결과·오래된 입력을 승인 완료로 취급하지 않습니다.
- 사용자 LLM 경로는 self-llm-v4 공통 규칙을 한 번 등록하고 제품별 프롬프트에는 요청·자료만 제공합니다. 고정 입력과 연결된 requestId 및 E0001 형식의 근거 번호를 사용하고 서버가 원문을 복원합니다. 다른 요청·존재하지 않는 근거는 거절하며 의미 연결은 사용자가 검토합니다. 외부 LLM에 자료를 자동 전송하지 않습니다.

상세: [화면 수집·원격 실행기](docs/REMOTE_CRAWL_AGENT.md), [통합 메뉴트리](docs/FUNCTIONAL_MENU_WORKFLOW.md), [공통 작성 규칙](frontend/public/downloads/menu-tree-common-rules.md).

### 메뉴트리 TC 생성

- 승인된 현재 메뉴의 기능 ID·전체 경로·설명·기능구분을 입력으로 씁니다. 경로가 같아도 다른 기능 ID를 임의로 합치지 않습니다.
- 로그인은 기능당 27건, 주요기능·부가기능은 기능당 9건, CRUD는 기능당 16건, 제품별 공통·문서 시험은 68건을 추가합니다. 로그인 기록 조회는 인증 기능으로 자동 분류하지 않으며 사용자 명시 분류는 보존합니다.
- 비적용 가능 항목도 생성합니다. 결과는 null/Excel 빈 셀이며 P/F/N/A는 실행 후 판정합니다. 결함 열은 **작성 예시**이지 실제 발견 결함이 아닙니다.
- XLSX 또는 rows 배열의 JSON을 TC 탭에서 올릴 수 있습니다. 업로드 메뉴는 사용자 제공 입력이며 화면 근거 검증 결과가 아닙니다. 문서 등록·검증은 필요합니다. XLSX 업로드는 첫 시트의 depth 열을 읽으며 여러 제품을 자동 병합하지 않습니다.
- 소유권·입력 digest·메뉴 revision을 검사하고 같은 입력은 재사용합니다. 원본 변경 후 과거 결과를 보존하고 변경 표시·다운로드/실행 시작 제한을 적용합니다.
- Excel은 TestCase 16열, 실행 정보 20열, 생성 정보 시트로 구성됩니다. TestCase는 사람용 시험 정의이고 실행 정보는 프로그램용 명세입니다. 단순 조회는 `execution-spec-v2-read`의 단계 코드와 구체적인 누락 정보가 생성됩니다. 연결되지 않은 기대값·대상은 `NEEDS_CONTEXT`이며 시험 준비와 실행 전에 검사합니다. 나머지는 기존 `execution-spec-v1` 초안입니다. Excel 편집은 서버에 자동 반영하지 않습니다.

상세: [메뉴트리 TC 생성기·샘플 재현](docs/MENU_TC_GENERATOR.md). 기존 /test-cases LLM 생성 흐름과 /menu-test-cases 템플릿 흐름은 별도입니다.

### 시험 실행 프로토타입

2번 화면 수집과 5번 시험 실행은 소요 시간·현재 메뉴/TC·실행 단계를 자동 갱신한다. TC에는 처리율과 판정/미완료 수를 표시하며, 전체 화면 수를 알 수 없는 수집에는 임의의 완료율을 표시하지 않는다. 종료 후 시계는 멈춘다.

화면 수집 시계는 버튼 클릭 시각을 고정해 요청 대기도 포함하고, 주기적인 조회나 소요 시간 누락/0 응답으로 초기화하지 않는다. 시작·종료 시각을 탭 세션에 보존한다. 진행 카드는 실행 중 주황색, COMPLETED/DONE 정상 탐색 종료 초록색, 중단·오류·한도 종료 빨간색이다. 녹색은 탐색 종료 상태이며 모든 메뉴 검증 완료를 뜻하지 않는다. 캡처 수는 정상 확인·진단·상태 정보 없음으로 합계를 맞추고, 고유 화면/동일 상태 재캡처/캡처 전 이동 실패는 별도 표시한다. 수집 기반 메뉴트리 초안은 접기·펼치기를 지원한다.

- **화면 이어서 수집**: 종료·중단된 작업의 캡처를 새 작업에 보존하고 같은 화면은 중복 저장하지 않는다. 이동 경로는 재탐색하여 미방문·로딩 미완료·이동 실패 화면을 다시 확인한다. 문서·대상·접속·범위가 바뀌면 새 수집이 필요하다. 시간·화면 수·깊이·로딩 한도는 조정 가능하며 합산 500화면/64MiB 한도는 유지한다. 원격 PC는 새 실행기 패키지가 필요하다.
- **TC 이어서 준비**: 완료 판정과 증거는 이전 실행에 보존하고 미완료 브라우저 TC만 새 실행으로 준비한다. 공통·문서 시험, 완료된 미판정은 별도 검토·선택 재시험 대상이다. 데이터 변경 허용 시험에서 중단 당시 진행 중이거나 오류/추가 정보 상태인 TC는 자동 반복하지 않는다. 이어서 준비 후 실행 요청으로 시작한다.

- 기능 ID·전체 경로로 TC를 묶습니다. 5depth가 비어 있어도 마지막 유효 기능을 유지하며 공통·문서 항목은 별도 그룹으로 보존합니다.
- 준비 시 TC 원본·메뉴/템플릿 버전·범위·URL·메모·데이터 변경 허용 여부를 별도 이력에 고정합니다. TC 원본 결과는 덮어쓰지 않습니다.
- Test connection 팝업은 현재 서버/원격 PC의 기존 검사를 사용합니다. HTTP 2xx/3xx 성공을 초록색으로 표시하고 URL·실행 위치 변경 시 초기화합니다. **실제 TC 워커는 현재 로컬 서버 실행만 연결**돼 있습니다.
- 화면 변경 이벤트마다 Qwen을 호출하지 않습니다. 기존 경로는 각 TC를 새 브라우저 세션에서 수행하고 조작 배치 뒤 다음 절차를 계획합니다. 최대 12배치·배치당 8조작, 일반 계획 호출의 실효 한도 300초·TC 전체 900초입니다. 저장 계정 정상 로그인 계획은 60초, 새 단순 조회 명세는 최대 60초의 코드 실행입니다. 실제 시험 동작과 명시적 FINISH 뒤 판정하며 한 TC의 성공을 다른 TC의 증거로 공유하지 않습니다.
- Jev confidence 기본 기준은 0.9이며 낮은 신뢰도·근거 부족은 미판정입니다. 검증된 정확도 90%를 뜻하지 않습니다. 사유는 선택·신뢰도·실행기 진단 중심이며 상세 원인 설명은 개선 대상입니다.
- 실제 텍스트·스크린샷·동작·계획·TC별 확인 조건을 조회합니다. Jev는 이미지를 판단하지 않으며 캡처는 사용자 검토용입니다. 1MiB 이하 CSV/TXT/JSON/TSV 다운로드 내용과 해시를 수집하며 잘린 파일·바이너리 내용은 검증 완료로 취급하지 않습니다.
- 원문 기대 조건·올바른 대상·전체 수행·관찰 근거가 충족돼야 P를 허용합니다. 실제 값의 재조회가 필요한 변경 시험은 성공 메시지만으로 통과하지 않습니다. 첫 실패·반복 쓰기·이전 F 뒤 성공은 보존하고 검토 대상으로 남깁니다. 이 안전장치로 실제 모델의 미판정 비율이 늘 수 있습니다.
- 시험 파일 전달, 네이티브 앱, 확인/입력 팝업 응답, 공통·문서 시험 자동 수행은 미지원입니다. 같은 출처 iframe의 관찰·기본 조작은 지원하지만 다른 출처 iframe 전체 시험을 지원한다는 의미는 아닙니다.
- 로그인은 저장한 설정의 정확히 같은 URL에만 재사용합니다. 수동/SSO는 미지원입니다. 데이터 변경 비허용 시 POST 등이 막혀 POST 기반 조회도 제약에 걸릴 수 있습니다.
- 단일 TMS 인스턴스를 가정합니다. 취소는 배치 전 검사와 결과 저장 시 fencing을 사용하며 조작을 즉시 강제 종료하지는 않습니다. 재시작 시 RUNNING은 INTERRUPTED로 닫고 재시험은 새 이력을 만듭니다.

| 구분 | 값 |
| --- | --- |
| 실행 상태 | PREPARED, WAITING_EXECUTOR, RUNNING, COMPLETED, PARTIAL, FAILED, INTERRUPTED, CANCELED |
| 항목 수행·판정 상태 | 대기, 수행 완료, 추가 정보 필요, 수행 오류, 검토 필요 등 |
| 시험 판정 | P / F / N/A / 미판정(null) |

F는 결함 후보이며 담당자 검토가 필요합니다. 06/07에서 사유를 남겨 판정·결함 상태를 바꾸며 자동 결과와 수정 이력을 보존합니다. 재시험 준비만으로 실제 실행을 시작하지 않습니다. 상세: [dev5 시험 실행](docs/TEST_EXECUTION_DEV5.md).

## 서비스 구성과 저장소

| 서비스 | 호스트 포트 | 역할 |
| --- | --- | --- |
| frontend | 5173 기본, 실행 시 지정 가능 | React·TypeScript·Vite 작업 화면 |
| api-gateway | 8080 | 제출·수집·실행 API 라우팅 |
| auth-service | 8081 | 인증 골격, 실제 인증·역할 권한은 후속 |
| test-management-service | 8082 | 문서·메뉴·TC·실행 이력 및 실제 워커 |
| test-execution-service | 8083 | 골격. 현재 실행 워커는 TMS에 있음 |
| report-service | 8084 | 리포트 골격 |
| ai-service | 8005 | 문서 접수·Jev 검사·메뉴/문서 생성·Qwen 계획·Jev 판정 |
| crawler-service | 8006 | Playwright 수집·접속 검사·TC 브라우저 수행 |
| Ollama | 21434 → 내부 11434 | 로컬 Qwen |
| PostgreSQL 16 + pgvector | 5432 | 운영 autotest, 격리 테스트 autotest_test |
| SeaweedFS | 8333 / 8334 / 9333 | S3 / S3 gRPC / Master |

Gateway의 /api/submissions/**와 /api/crawl-agents/**는 TMS로 연결됩니다. **실제 실행 API도 /api/submissions/{id}/executions로 TMS에 연결**되며 별도 서비스의 /api/executions/** 라우트와 다릅니다. AI·crawler 내부 API는 내부 토큰으로 직접 호출하고 Vite /api는 localhost:8080으로 프록시합니다.

```text
frontend/                      단계별 UI, 연결 팝업, 실행 결과
test-management-service/       Java 업무 API·저장·실행 워커
  src/main/resources/db/migration/  Flyway V1~V25
  src/main/resources/tc-templates/  버전 관리 시험 기준
ai-service/                    FastAPI, Jev, Ollama, 생성·계획·판정
crawler-service/               로컬 Playwright·Windows 원격 실행기
api-gateway/                   Spring Cloud Gateway
auth-service/, report-service/, test-execution-service/  골격
db/, common-lib/               PostgreSQL 이미지·Java 공통 모듈
sample/, scripts/, output/, docs/   원본·검증·기록·상세 계약
```

주요 영속 데이터는 submissions/submission_files, submission_ai_deliveries, product_description_jobs, tc_generation_jobs, playwright_crawl_setups, playwright_crawl_jobs, functional_menu_jobs, menu_tc_sets, test_execution_runs입니다. V21은 메뉴 TC, V22는 업로드 메뉴, V23은 실행 이력, V24는 실제 실행 상태·attempt입니다. 과거 메모리 TC 저장소와 현재 영속 생성 결과를 혼동하지 않습니다. Hibernate는 ddl-auto=validate이며 기존 Flyway 파일을 수정하지 않습니다.

## 주요 API

회원용 요청은 X-Member-Id, 내부 요청은 X-Internal-Token을 사용합니다. 개발용 회원 헤더는 실제 인증의 대체물이 아닙니다.

| Method | 경로 | 기능 |
| --- | --- | --- |
| POST | /api/submissions | 첫 문서: multipart file/productId/role |
| GET | /api/submissions/{id} | 문서·전달·사전 점검 |
| POST | /api/submissions/{id}/files | 추가 역할 문서 |
| POST | /api/submissions/{id}/files/{role}/replace | 실패·차단 교체 |
| POST | /api/submissions/{id}/ai-delivery/retry | 전달 복구 |
| GET / PUT | /api/submissions/{id}/playwright-setup | 접속 설정 |
| GET / POST | /api/submissions/{id}/playwright-crawls | 이력·CONNECTION/LOGIN/CRAWL 작업 |
| POST | /api/submissions/{id}/playwright-crawls/{job}/cancel | 수집·검사 중단 |
| GET / POST | /api/crawl-agents | 원격 PC 조회·등록 |
| GET / POST | /api/submissions/{id}/functional-menu | 메뉴 이력·생성 |
| POST | /api/submissions/{id}/functional-menu/upload | 메뉴 XLSX/JSON 업로드 |
| POST | /api/submissions/{id}/functional-menu/{job}/prompt 또는 /import | 프롬프트·JSON 가져오기 |
| PUT | /api/submissions/{id}/functional-menu/{job}/draft 또는 /approval | 메뉴 저장·승인 |
| GET | /api/submissions/{id}/functional-menu/{job}/download | 메뉴 Excel |
| GET / POST | /api/submissions/{id}/menu-test-cases | TC 이력·생성 |
| GET | /api/submissions/{id}/menu-test-cases/{set}/download | TC Excel |
| GET / POST | /api/submissions/{id}/executions | 실행 이력·준비 |
| POST | /api/submissions/{id}/executions/{run}/start 또는 /cancel | 실행 요청·중단 |
| POST | /api/submissions/{id}/executions/{run}/review 또는 /retry | 수정 이력·결함 검토·새 재시험 준비 |
| POST | /api/submissions/{id}/executions/{run}/resume | 완료 결과 보존·미완료 TC 이어서 준비 |
| POST | /api/submissions/{id}/playwright-crawls/{job}/resume | 기존 캡처 보존·미완료 화면 이어서 수집 |
| GET | /api/submissions/{id}/executions/{run}/report | Markdown 시험 보고서 초안 |
| POST | AI /api/v1/test-executions/feature | 내부 기능별 계획·수행·판정 |
| GET | AI /api/v1/test-executions/capabilities | 모델·Jev 설정, 품질 검증과 별개 |
| POST | crawler /api/v1/tc-browser/open, /{session}/step, /{session}/close | 내부 브라우저 세션 |

기존 LLM TC의 /api/submissions/{id}/test-cases와 제품 설명 /product-description API는 보존합니다. 과거 /crawl 요청 목록 수집 API는 현재 시험 실행 흐름과 다릅니다.

## 환경 설정과 실행

Java 21, Gradle Wrapper 8.10.2, Spring Boot 3.5.6, Python 3.13, Node.js 24.21.0(.nvmrc), npm 11.x, Docker Compose를 사용합니다. 프런트엔드는 Compose에 포함되지 않습니다.

루트 .env는 사용자가 작성하며 Git에 포함하지 않습니다.

```dotenv
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
PRODUCT_DESCRIPTION_INTERNAL_TOKEN=your-long-random-internal-token
TYPESAFE_API_KEY=your-typesafe-key
PRODUCT_DESCRIPTION_GENERATION_ENABLED=false

# 기존 문서·메뉴 생성 설정. 시험 실행 Qwen 경로는 이 설정과 별도이다.
LLM_MODE=real
LLM_PROVIDER=ollama
LLM_MODEL=qwen3:4b
```

Compose 기본 LLM_MODE는 mock이므로 실제 문서·메뉴 생성에 쓸 때 명시적으로 바꿉니다. 시험 실행은 별도 로컬 Qwen 경로를 사용하고 Jev 키가 필요합니다. DB 개발 계정은 Compose의 test/test, DB는 autotest입니다. S3_BUCKET을 .env에 적는 것만으로 저장 버킷이 바뀌지는 않습니다.

```powershell
# 루트에서 실행. Java 이미지는 미리 만든 JAR을 복사한다.
.\gradlew.bat :auth-service:bootJar :test-management-service:bootJar :test-execution-service:bootJar :report-service:bootJar :api-gateway:bootJar
docker compose up -d --build
docker compose exec ollama ollama pull qwen3:4b
curl.exe http://localhost:8082/actuator/health
```

다른 터미널에서 프런트엔드를 실행합니다.

```powershell
npm ci --prefix frontend
npm run dev --prefix frontend -- --host 127.0.0.1 --port 4173
```

브라우저 주소는 http://127.0.0.1:4173이며 포트를 지정하지 않은 Vite 기본은 5173입니다. 호스트 Ollama와 충돌하지 않도록 컨테이너 API는 http://localhost:21434로 노출합니다.

시험 실행은 AI 컨테이너의 EXECUTION_QWEN_MODEL(기본 qwen3:8b), OLLAMA_BASE_URL, CRAWLER_SERVICE_URL, MANAGEMENT_SERVICE_URL, EXECUTION_JEV_MIN_CONFIDENCE(기본 .9)를 사용합니다. 실행 모델은 `docker compose exec ollama ollama pull qwen3:8b`로 별도 준비합니다. **추가 변수는 현재 Compose가 모두 전달하지 않으므로** 기본값을 바꾸려면 Compose environment/override에 명시합니다. TMS의 execution.worker.enabled=false는 격리 테스트에서 자동 소비를 막을 때 사용합니다.

Qwen 8B 전환, 코드 기반 메뉴 이동·조회 완료 기준, 반복 탐색 방지와 검증 범위는 [시험 수행 개선·검증 결과](docs/EXECUTION_V3_2026-10-23.md)를 참고합니다. 명시적 문구 검증과 Jev 의미 판정은 구분하며, 실제품의 포괄적 기대 결과는 아직 검토 필요로 남을 수 있습니다.

### 변경 반영과 데이터 보존

- Java 소스는 bind mount가 아니므로 JAR 빌드 후 이미지를 재빌드·재생성합니다.
- AI·crawler 소스는 bind mount입니다. AI는 reload를 사용하고 Node crawler는 재시작이 필요합니다. 의존성 변경 시 이미지를 재빌드합니다. 다른 worktree를 마운트한 서버에 현재 소스가 반영됐다고 가정하지 말고 mount를 확인합니다.
- .env 변경은 재생성 후 반영됩니다. 키·모델 설정·영속 볼륨을 유지합니다. setup.ps1은 과거 초기화 스크립트이므로 현재 설치에 쓰지 않습니다.
- 원본 자료, DB, 모델·생성 cache, 자격정보 키 볼륨을 보존합니다. 비밀번호를 로그·모델 입력·메모에 넣지 않습니다.
- 운영 스키마 변경 전 custom-format 백업을 확보하고 복원 검증은 별도 DB에서 합니다. PowerShell에서 바이너리 dump를 텍스트 리다이렉션하지 않습니다.

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T db pg_dump -U test -d autotest -Fc -f /tmp/autotest-before-change.dump
docker compose exec -T db pg_restore --list /tmp/autotest-before-change.dump
docker compose cp db:/tmp/autotest-before-change.dump backups/autotest-before-change.dump
```

2026-10-22 확인 환경에서는 사용자 승인 후 V24와 실행 프로토타입을 반영하고 내부 API 연결·모델 설정을 확인했습니다. 서버 기동 검증이며 실제 NeoFlow 전체 시험 완료를 뜻하지 않습니다.

## 검증과 확인된 한계

운영 autotest를 테스트에서 초기화하지 않습니다. Java 통합 테스트는 이름이 _test로 끝나는 격리 DB만 사용합니다. 테스트 DB를 처음 만들 때만 docker compose exec -T db createdb -U test autotest_test를 실행합니다.

```powershell
$env:AUTOTEST_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5432/autotest_test'
.\gradlew.bat :test-management-service:test --tests '*MenuTcIntegrationTest' --tests '*ExecutionFeatureClassificationTest'
Remove-Item Env:AUTOTEST_TEST_DATABASE_URL

docker compose exec -T ai-service python -m unittest test_execution_test -v
node --test crawler-service/tc-execution.test.js
npm run build --prefix frontend

# Vite를 4173에서 실행한 뒤 사용. 다른 주소는 FRONTEND_QA_URL로 지정한다.
node scripts/verify-test-execution-ui.cjs
node scripts/verify-menu-tc-ui.cjs
node scripts/verify-frontend.cjs
```

AI·crawler 테스트는 조작 실패·범위·취소·낮은 confidence를, DB 테스트는 실제 워커 결과 저장·소유권·원본 고정·상태 전이를 검사합니다. UI 검증은 격리 API fixture이며 실제 제품 시험이 아닙니다. 검증 명령 목록이 매 문서 변경마다 모든 모델 시험을 재실행했다는 뜻은 아닙니다.

메뉴 샘플 4개 제품의 435개 기능에서 TC 4,277건을 생성·검증한 기록은 output/menu-tc-samples/에 있습니다. 재현 명령은 .\gradlew.bat :test-management-service:generateMenuTcSamples이며 입력은 sample/menu sample/menu sample.xlsx입니다. 과거 결과 수는 다른 입력·이후 버전의 품질을 보증하지 않습니다.

실제 로컬 Qwen·Playwright·Jev 합성 시험 기록:

| 시험 | 관찰된 처리 | 시간 |
| --- | --- | --- |
| 정상 저장 | P, Jev confidence .95 | 11.50초 |
| 오류 저장 | F, Jev confidence .96 | 6.46초 |
| 정상 상세 조회 | confidence .87로 미판정 | 7.12초 |
| 잘못된 상세 조회 | confidence .80으로 미판정 | 5.84초 |
| 시험 파일 누락, 최종 재시험 | 입력 제약으로 차단, NEEDS_CONTEXT·미판정 | 6.07초 |

기록은 output/test-execution/live-*.json과 [상세 검증 문서](docs/TEST_EXECUTION_DEV5.md)에 있습니다. Qwen은 누락 정보 오해·반복 클릭을 보였고 파일 누락 시험에서도 파일 선택을 시도했습니다. 제어 로직이 실행·확정을 제한한 것이며 모델이 완전히 이해했다고 해석하지 않습니다. 통과시키려고 Jev 기준을 낮추지 않았습니다. 실제 제품 전체 품질이나 F→P 오판 방지 완료를 주장하지 않습니다.

기존 제품 설명·LLM TC의 샘플 생성 기록은 해당 커밋과 상세 문서에 보존합니다. 생성·다운로드 성공, 인용 일치, 테스트 개수는 시험 내용의 실무 정확성과 별개입니다.

## 다음 계획

2026-10-27 합의한 우선순위입니다. 각 단위 완료 후 구현·검증 범위와 미완료 사항을 보고하고 사용자 첨삭을 반영한 다음 확장합니다.

1. **조회 TC 실행 정보 연결 — 1차 구현:** 기존 구조화 명세를 활용해 단계·미연결 사유를 기록하고 준비/실행 전 검사 및 준비된 명세의 수행 경로를 추가했습니다. TestCase 4,466건의 셀 동일성과 실제 Chromium 합성 시험을 확인했습니다. 생성본의 자동 연결과 사용자 보완 화면은 후속입니다.
2. **근거 기반 명세 보완:** 문서·수집 화면·시험 데이터에서 입력과 기대 기준을 연결하는 경로를 추가합니다. 선택자·기대값은 추측하지 않고 미확정 항목은 검토 대상으로 남깁니다.
3. **유형별 확장:** 조회 결과를 확인한 뒤 등록·수정·삭제·업로드·권한 등을 순차 보완합니다. TestCase 템플릿 전체 생성과 원본 보존 원칙을 유지합니다.
4. **모델 역할 비교:** 코드는 반복 절차·준비 상태·증거 검사를 담당합니다. Qwen/Jev는 대표 TC에서 명세 보완 품질·오류·시간을 비교한 뒤 역할을 결정합니다. Jev 명세 생성은 아직 구현되지 않았습니다.
5. **조건별 결과·복구:** 수행과 판정, 도구 오류와 제품 결함을 구분하고 조건별 증거·첫 실패·정리 결과를 보존합니다. 초기 화면 확인 이후 정상 이동의 상태 표시도 보완합니다.

아래는 이전 검증에서 이어지는 품질 과제입니다.

최신 합의는 **속도보다 충분한 수행을 우선하며 F여야 할 항목을 P로 확정하는 오류를 줄이는 것**입니다. 일부 미판정은 사용자가 검토하되 과도하게 쌓이지 않도록 원인을 구분합니다. 재시도는 첫 실패를 가리지 않게 보존해야 합니다. 아래 항목은 계획이며 아직 완료 기능이 아닙니다.

1. **메뉴트리 v2 계약:** UI·공통 규칙과 서버의 instructionVersion, 근거 번호 복원, requestId, 가져오기 경고를 맞추고 왕복 검증합니다.
2. **TC별 확인 조건·증거:** 한 기능의 성공을 모든 TC의 P로 확대하지 않고 각 TC의 수행 여부·기대 조건별 증거를 관리합니다.
3. **P 확정·재시도:** 올바른 대상·필요 동작·모든 조건을 확인합니다. 첫 실패와 재시도 결과·모순을 보존하고 실패 후 성공은 검토 대상으로 남깁니다. confidence만으로 P를 확정하지 않습니다.
4. **실제 반영·데이터 격리:** 저장 후 재조회·새로고침·목록/상세 대조, 다운로드 내용, 시험용 데이터와 초기 상태·정리 절차를 연결합니다. 재클릭에 의한 중복 등록·실행을 줄입니다.
5. **실제 제품·결함 주입 검증:** 일부 NeoFlow 기능부터 정상처럼 보이는 잘못된 결과, 일시 실패, 다른 항목 오조작을 시험합니다. F→P 오판과 과도한 미판정을 함께 검토합니다.
6. **사용자 검토·재시험:** 사유·증거를 보고 판정 수정·선택 재시험·패치 비교를 할 수 있게 하고 수동 시험과 실행 오류를 구분합니다.
7. **후속 보관 — 사전 필요 자료:** 실행 전에 파일/폴더를 업로드하고 TC·기능에 연결하는 구상입니다. 요청에 따라 기록만 하며 지금 구현하지 않습니다.
8. **이후 확장:** iframe·팝업·원격 TC 수행, 공통·문서 시험, 결함 확정·기업용 리포트·패치 이력, 실제 인증·인가, 표준 판·품질/결함 기준을 보완합니다.

현재는 로컬 Qwen + Jev를 유지합니다. 실제 검증에서 부족한 부분을 확인한 뒤 모델 크기·질문 분해를 개선하고 필요할 때 외부 생성형 LLM을 검토합니다. 상세 완료 기준: [개발 계획](docs/DEVELOPMENT_PLAN.md).

## 주요 변경 이력과 문서

| 시기·커밋 | 변화 |
| --- | --- |
| 초기~3889efb | 모듈·Compose, SeaweedFS·AWS SDK 저장 |
| c1ddc79~6f633e0 | 역할별 문서 영속화·교체·복구, Jev, 비동기 제품 설명·Ollama |
| 2c22791, 6c93464 | LLM 품질 한계 기록, 승인 기반 LLM TC·Excel |
| e10b362~c2b8326 | 로컬/원격 접속·로그인·화면 상태·이동 근거·메뉴트리·부분 결과·웹 구조 우선 |
| 261591c, 19bf085 | 단계별 UI, 탐색·접기·표 페이지·한국어 안내 |
| afc8a49, bce542c | 템플릿 메뉴 TC, 4제품 검증, 메뉴 업로드 |
| 3162660 | 시험 준비·이력·연결 팝업 |
| ab11bd3 | Qwen 계획·Playwright 수행·Jev 판정, 로그인 기록 분류 수정 |
| 582a893 | 사용자 LLM용 메뉴 UI·규칙 v2·경고. 서버 정합성 보완은 남음 |

과거 코드 제출·@test 파싱/ZIP 경로는 현재 문서 기반 시험 흐름이 아닙니다. MinIO를 재도입하거나 과거 구현을 현재 지원으로 설명하지 않습니다. 작업 규칙은 [AGENT.md](AGENT.md), 상세 계약은 [메뉴트리](docs/FUNCTIONAL_MENU_WORKFLOW.md), [TC 생성](docs/MENU_TC_GENERATOR.md), [시험 실행](docs/TEST_EXECUTION_DEV5.md), [개발 계획](docs/DEVELOPMENT_PLAN.md)을 참고하세요.
