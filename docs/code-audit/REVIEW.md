# 코드 구조·연결 검토 — 2026-10-27

검토 기준 커밋은 `ccfe317`이다. 이번 작업은 코드 설명·문서 정정이며 제품 기능을 추가하거나 서버를 교체하지 않는다. 소스에서 확인한 사실, 기존 실행 기록, 이번에 새로 실행한 검사를 구분한다.

## 1. 파일 범위와 이 문서의 읽는 방법

- [전체 파일별 설명](ALL_FILES.md), [인벤토리](inventory.json): 기준 Git 추적 파일 1,321개 전부의 경로·분류·역할·크기·SHA-256. 해시는 목록 생성 당시 작업 디렉터리 내용이며 수정 전 커밋의 blob 해시라는 뜻이 아니다. 외부 의존성·바이너리·산출물도 분류에 포함한다.
- [파일별 역할·선언·HTTP 경로](FILE_INDEX.md): 자체 코드·설정·템플릿 237개와 검증·보조 코드 99개, 합계 336개 파일을 각각 설명한다. 파일/행 번호로 원문에 돌아갈 수 있다.
- [외부 패키지 설명](DEPENDENCIES.md): 추적된 node_modules의 패키지별 실제 manifest 설명·버전·파일 수.
- 이 문서: 사용자 기능을 기준으로 실제 호출·저장·실행 관계와 README의 오류를 설명한다.

| 인벤토리 분류 | 파일 수 | 처리 |
| --- | ---: | --- |
| 제품 코드·설정·템플릿 | 237 | 파일별 역할·정적 선언 목록, 아래 모듈별 연결 설명 |
| 검증·보조 코드 | 99 | 파일별 목록·선언. 존재와 실제 실행/통과는 구별 |
| 외부 의존성 | 800 | crawler-service/node_modules 안의 제3자 파일. 전체 목록·해시 보존 |
| 생성·컴파일 파일 | 37 | __pycache__ 등. 원본 코드와 중복 집계하지 않음 |
| 기존 검증 산출물 | 73 | 과거 검증의 입력·결과. 현재 코드의 품질 증명으로 확대하지 않음 |
| 시험 입력 샘플 | 15 | 제품 입력 자료. 실행 지침으로 취급하지 않음 |
| 문서 | 37 | 과거 문서와 현재 설명의 시점 구분 |
| 빌드 도구·자산·기타 | 23 | Wrapper·Dockerfile·이미지·정적 자산 등 |

목록은 파일 누락을 확인하기 위한 장치다. 정규식으로 추출한 선언 목록은 완전한 언어 파서나 호출 그래프가 아니며, **제3자 코드까지 모든 행의 의미·정확성을 검증했다는 뜻이 아니다.** 무결점이나 환각 0을 보증하는 표현을 사용하지 않는다. 무시된 .env·인증키·로컬 node_modules 및 미추적 시험 결과는 제품 원본 소스에 섞지 않는다. 이번에 추가한 설명 문서/목록 생성기는 기준 커밋 이후의 변경이다.

## 2. 실제 실행 구성

```text
React App
  → Vite /api 프록시 → Gateway
    → TMS(문서·화면 수집 관리·메뉴·TC·시험 worker·검토·보고서)
      → PostgreSQL: 작업/버전/결과/증거
      → SeaweedFS: 원본/생성 문서
      → AI: Jev 사전 점검 / 기존 생성 경로 / 시험 계획·판정
      → crawler: 현재 서버 Playwright
      ↔ 원격 agent: 화면 수집·연결/로그인 검사

AI 시험 실행 → Ollama Qwen 계획 → crawler 조작/관찰 → Jev 텍스트 판정
```

- settings.gradle.kts는 Gateway/auth/TMS/test-execution/report/common-lib 6개 Java 하위 프로젝트를 포함한다. 루트 build.gradle.kts는 Java 21·Boot 3.5.6을 지정한다.
- 현재 시험 worker는 **test-management-service**의 TestExecutionWorker다. 이름이 test-execution-service인 별도 모듈은 Application·설정·빌드/이미지 골격이다. report-service도 현재 보고서 생성 코드의 위치가 아니다.
- auth-service의 Application은 실제 로그인·회원·역할 API 구현을 의미하지 않는다. X-Member-Id는 개발용 식별/소유권 값이다.
- Gateway는 /api/submissions/** 등을 TMS로 보낸다. /api/executions/**의 별도 서비스 경로와 현재 시험 API를 혼동하지 않는다.
- common-lib는 오류 계약·Servlet 공통 핸들러다. Gateway는 Reactive 핸들러를 따로 쓴다.
- Docker의 AI 전체 디렉터리, crawler의 index.js/tc-execution.js/remote-agent는 bind mount다. TMS/Gateway Dockerfile은 호스트 JAR을 복사하며 auth/report/test-execution은 이미지 내부에 별도 Gradle 빌드 단계를 갖는다. Java 소스만 수정해서 실행 컨테이너에 반영됐다고 보지 않는다. 현재 worktree와 실제 mount 위치의 동일성도 가정하지 않는다.
- root Main.java는 출력 예제이며 Gradle 기본 src/main/java 소스 디렉터리에 없다. sites의 HTML은 정적 예시이며 App의 업무 화면이 아니다.

## 3. 화면과 실제 컴포넌트

| 화면 | 현재 App 연결 | 책임 |
| --- | --- | --- |
| 01 자료 등록 | App.tsx | 역할별 선택·순차 업로드·조회·실패 교체·AI 전달 재시도·사전 점검 상세 |
| 02 화면 수집 | PlaywrightSetupPanel → RemoteCrawlPanel | 접속 설정·원격 등록/선택·검사·수집·캡처/사유·이어 수집 |
| 03 메뉴트리 | FunctionalMenuPanel | 외부 프롬프트/JSON·근거·포함/배치/분리/설명 수정·승인·Excel |
| 04 TC 생성 | MenuTcPanel | 승인/업로드 메뉴·기능구분·생성·검색·상세·Excel/JSON |
| 05 시험 실행 | TestExecutionPanel | TC 선택·접속 검사·준비/요청/중단·상태/증거·이어 준비 |
| 06 결함 검토 | ExecutionReviewPanel(reports=false) | 사용자 판정·사유·결함 상태 저장·선택 재시험 |
| 07 리포트·재시험 | ExecutionReviewPanel(reports=true) | 전체 결과·재시험 버전·Markdown 보고서 초안 |

App은 방문한 패널을 유지하고 active 값으로 현재 탭을 구분한다. 생성한 TC setId를 시험 실행 탭에 넘긴다. ExecutorStatus는 공용 헤더의 실행기 점유 UI다. RunProgress는 시계/표시 컴포넌트이며 수집 worker가 아니다. useExecutionEvidence는 별도 증거 API를 지연 조회한다.

**기존 검토가 없다는 설명은 틀렸다.** 03 메뉴 편집·승인과 04 생성 TC 상세 확인, 06/07 판정 수정·증거 검토·재시험이 이미 있다. 다만 서로 다른 데이터를 다룬다.

| 검토/변경 대상 | 현재 구현 |
| --- | --- |
| 메뉴 기능명·설명·배치·포함 | FunctionalMenuPanel → draft/approval API 저장 |
| TC 생성 전 기능구분·로그인 URL | MenuTcPanel → Generate.featureTypes/loginUrl로 새 생성 요청 |
| 생성 TC 시나리오·입력·기대 출력 | MenuTcPanel 상세에서 표시. MenuTcController에 본문 수정 PUT/PATCH API 없음 |
| v2-read bindings/기대값/단계 | 내부 데이터 구조·검사·실행 경로 있음. 현재 04 상세 UI에는 이 필드를 저장하는 입력/요청 없음 |
| 수행 후 P/F/N/A·사유·결함 상태 | ExecutionReviewPanel → review API, 자동 판정과 사용자 이력 분리 저장 |
| Excel 편집 | 서버 재가져오기 경로 없음. 메뉴트리 업로드와 TC Excel 재가져오기는 다름 |

TcGenerationPanel·ProductDescriptionPanel 소스는 존재하지만 현재 App은 import/render하지 않는다. 이 파일에 있는 과거 버튼·검토 기능을 현행 04 화면에 있는 것처럼 설명하지 않는다. App의 upcomingPages 설명에는 TC 수정·승인 같은 의도 문장이 남아 있으므로 그것만으로 구현 완료를 판단하지 않는다.

## 4. 01 문서 접수와 사전 점검

1. App.sendFile은 첫 파일로 제출을 만들고 추가 역할/실패 교체 요청을 같은 제출에 보낸다. 역할은 AGREEMENT/FUNCTION_LIST/MANUAL이다.
2. SubmissionController → SubmissionService에서 파일 형식/크기를 확인하고 FileStoragePort를 통해 S3에 원본을 저장한다. 파싱 후 텍스트/성공·실패를 문서 행에 저장한다.
3. DocumentFileParserFactory는 FileParser 구현 중 supports(format)이 맞는 parser를 고른다. PDFBox, POI, hwplib/hwpxlib로 문서를 추출한다. OCR 필요 실패는 OCR 수행 완료가 아니다.
4. SubmissionPersistenceService는 문서/세트 상태·교체·정리 outbox·전달 claim을 트랜잭션으로 관리한다. 현재 필수 역할은 FUNCTION_LIST와 MANUAL이며 합의서는 선택이다.
5. AiDocumentDeliveryService가 claim한 문서를 AI /document-intakes에 보낸다. attempt로 오래된 완료를 차단하고 전달 상태·사전 점검·문서 digest를 저장한다.
6. AI main.DocumentIntake는 2~3개 역할·중복·빈 텍스트를 검사한다. preflight.py는 텍스트 상태 검사와 Jev 선택형 판정, preflight_store는 30초 claim/lease와 중복 평가를 제어한다.
7. AI 접수 저장에는 문서 JSON이 들어간다. 반면 ai_preflight_attempts에는 판정만 저장한다. 두 테이블의 개인정보 보관 범위를 혼동하지 않는다.
8. BLOCKED는 전달 실패와 다르다. 차단/파싱 실패 문서 교체, 통신 실패 재시도, 원본 정리 outbox는 별도 경로다.

필수 두 문서의 접수 정책과 기본 비활성인 과거 제품 설명 생성의 세 역할 요구는 별개다. ProductDescriptionService/Worker/Gateway, AI product_description의 API·문서 모델·builder·provider·renderer·template_store는 기존 제품 설명 작업을 담당한다. 현재 메뉴 기반 TC는 제품 설명 PDF를 입력으로 쓰지 않는다.

## 5. 02 화면 수집

- PlaywrightSetupService는 소유권·문서 파싱·Jev 허용·digest를 확인한다. 비밀번호는 PlaywrightCredentialCipher를 통해 암호화 저장하고 설정 조회는 passwordConfigured만 반환한다.
- CrawlAutomation의 기본값은 로딩 60초·최대 500화면·깊이 10·총 3000초다. 상한은 로딩 60초·500화면·깊이 10·총 3600초다.
- RemoteCrawlService는 CONNECTION/LOGIN/CRAWL 작업, agent/로컬 실행, claim/lease·heartbeat, 페이지/결과 저장·중단·이어 수집을 관리한다. LocalCrawlDispatcher는 로컬 작업을 crawler에 전달한다.
- crawler/index.js는 내부 토큰과 공용 localBusy를 검사한다. agent.runJob은 CONNECTION/LOGIN이면 probe, CRAWL이면 engine/walker를 사용하고 이벤트를 TMS에 보낸다.
- engine은 URL 범위·위험 동작과 로그인 공통 절차, probe는 연결/로그인 확인, walker는 메뉴 탐색/이동 재현·캡처·한도·중단 결과, navigation/screen은 메뉴 구조와 로딩/프레임 관찰을 담당한다.
- language는 새 수집에서 ko-KR과 확인 가능한 한국어 선택 UI를 사용한다. 미확인은 한국어 전환 성공이 아니다. 과거 이어 수집의 언어를 임의로 섞지 않는다.
- retry는 SELF/SUBTREE 선택 범위를 계산한다. UI SkippedMenuRetry의 상위 체크는 하위 선택과 연결된다. 재수집도 상위 경유 이동이 필요하므로 메뉴 한 번 클릭만 수행하는 기능은 아니다.
- 정상 확인/진단/상태 없음 캡처는 저장 합계의 분류다. 고유 화면/동일 화면 재캡처/캡처 전 실패는 별도다. skipped 1건은 기록된 실패 항목 1건이지 모든 제품 기능이 나머지까지 수집됐다는 증명은 아니다.
- 원격 agent의 protocol은 암호화 채널, Install/Start 스크립트는 Windows 실행기를 설치/기동한다. 원격 수집 지원이 원격 TC 시험 지원을 뜻하지 않는다.

## 6. 03 메뉴트리

1. FunctionalMenuService.snapshot은 기능리스트·매뉴얼, 수집 화면 본문·메뉴·버튼·위치·상태를 고정한다. WebMenuStructure가 관찰된 구조를 만든다.
2. SelfLlmMenuContract.describe는 작업·버전·snapshot의 requestId와 E번호를 만든다. 제품 프롬프트에는 공통 규칙 본문을 반복하지 않는다.
3. 공통 지침의 실제 원본은 frontend/public/downloads/menu-tree-common-rules.md다. TMS processResources가 같은 파일을 JAR menu-templates로 복사한다. 소스에 같은 내용의 두 원본이 따로 있는 구조가 아니다.
4. UI는 제품 프롬프트를 복사/TXT 다운로드하고 사용자가 외부 LLM에 전달한다. 프로그램이 외부 LLM에 자동 제출하지 않는다.
5. JSON 가져오기는 현재 textarea 전체를 JSON.parse한 뒤 requestId/rows를 서버로 보낸다. 파싱 이전에 일정 문자 수로 자르는 slice/substring은 해당 경로에서 확인되지 않았다. 잘못된 입력의 실제 원인을 과거 오류 문구만으로 복원할 수는 없다.
6. restore는 요청 일치·참조 수/존재·중복 기능 등을 검사하고 원문 근거를 복원한다. FunctionalMenuValidator와 MenuPlacementRecovery는 경로·포함·배치·근거·품질 경고를 정규화한다.
7. ensureMenus는 관찰된 메뉴 구조를 보존한다. 구조 행과 실제 기능은 rowType으로 구분하며 포함 구조 행이 그대로 TC가 되지 않는다.
8. 사용자는 행의 포함/배치/기능명/설명·분리를 검토하고 draft/approval로 저장한다. revision 충돌·문서 stale·mock/부분 결과 승인 제한이 있다.
9. FunctionalMenuExcel은 메뉴트리 workbook을 출력한다. 문서 참조 범위는 의미적 기능 완전성 보증이 아니다.

functional_menu.py와 ai-service/menu_templates/functional-v1은 별도 내부 AUTO/과거 생성 경로다. 현재 UI에서 로컬 자동 생성 버튼을 숨겼다는 것과 서버 코드 삭제는 다르다. self-llm-v4, functional-menu-v1, menu-tc-v3, execution-spec-v1/v2-read는 서로 다른 계약의 버전이다.

## 7. 04 TC 생성: 사람이 보는 내용과 프로그램 정보

1. MenuTcService는 승인/업로드 메뉴의 포함 기능을 features로 만든다. 사용자 featureTypes를 우선하며 rowId·전체 경로·설명·원문 근거·matchedMenuKeys를 유지한다.
2. AuthenticationTcContext는 문서/접속/수집에서 로그인 필요를 확인하고 필요하면 authentication:login 기능을 추가한다. 로그인 메뉴가 사이드바에 없어도 TC를 만들 수 있다. 잠금 횟수는 근거가 단일하게 확인될 때만 사용한다.
3. MenuTcGenerator는 menu-tc-v3 템플릿으로 로그인 27, 주요/부가 9, CRUD 16, 공통/제품설명서/사용자취급설명서 68건을 확장한다. 생성 자체에 LLM 호출은 없다. 로그인 기능을 앞에, 그 안에서 정상 로그인 LOGIN-04를 앞에 둔다.
4. 기능구분은 명시값 우선이다. 없으면 로그인과 마지막 단어 조회/생성/삭제/등록/수정으로 제안한다. 동작 유형 READ/CREATE/UPDATE/DELETE와 기능구분 CRUD는 별도 필드다.
5. TcTemplateBinding은 제품 문맥·동작 충돌 후보·추가 정보·원본 템플릿을 연결한다. TcProcedureWriter는 절차/입력/기대 출력의 문장을 만든다. 이것이 실제 수행 증거는 아니다.
6. executionPlan에는 URL·menuKeys·steps·requiredContext·selection·digest·specification이 이미 들어간다. 이를 새로 처음 만들어야 한다는 설명은 잘못이다.
7. TcExecutionSpecification은 지원되는 단순 조회를 v2-read로 만들고 bindings.target/readContext/check oracle을 미연결로 둔다. v1·v2 모두 구조 존재와 실제 데이터 준비 완료는 다르다.
8. MenuTcService는 inputDigest로 같은 입력을 재사용하고 menu_tc_sets에 JSON 결과를 저장한다. 메뉴 revision·문서 변경으로 stale 여부를 확인한다.
9. MenuTcWorkbook.export는 TestCase 16열·실행 정보 20열·생성 정보 3시트를 출력한다. 결과는 빈 셀, 결함 열은 예시다. 실행 정보에 구조화 JSON도 포함되지만 실행기는 Excel 파일을 직접 여는 것이 아니라 서버에 저장한 JSON을 읽는다.

UI의 메뉴 XLSX 업로드는 UploadedMenuService가 **첫 시트**를 읽는다. 샘플 CLI의 MenuTcWorkbook.read는 여러 메뉴 시트를 각각 읽는다. 두 경로를 혼동하지 않는다. 업로드 메뉴는 화면 근거를 검증한 메뉴가 아니라 사용자 입력으로 승인된 메뉴다.

## 8. 05 시험 준비·실행

1. TestExecutionPanel은 TC 세트·URL·메모·버전·변경 허용·기능별 선택을 POST /executions로 보낸다.
2. TestExecutionService.prepare는 원본을 바꾸지 않고 snapshot/groups/items를 만든다. 공통·추가 정보·v2 미완성 여부를 사전 분류한다. 이 수는 수행 완료 수가 아니다.
3. ExecutionConnectionTest는 CONNECTION 또는 LOGIN 검사 후 성공한 설정을 저장한다. 로그인 필요이면 실제 loginVerified가 있어야 한다. 단순 2xx로 모든 경우 로그인 성공을 표시하지 않는다.
4. start는 stale·URL/접속 설정·실행기 readiness와 지원 명세를 확인한 뒤 WAITING_EXECUTOR로 넘긴다. readiness는 5초 캐시한 프로토콜·브라우저 설치·모델 설치·Jev 키 검사다. 실제 모델 추론이나 Jev 품질 성공을 보장하지 않는다.
5. TestExecutionWorker는 단일 worker로 WAITING 작업을 선점하고 TC 한 건씩 AI로 보낸다. 문서에서 경로 단어가 처음 발견된 주변 발췌와 메뉴 키 기반 navigationHints를 붙인다. 문서 전체를 이해해 모든 기대값을 확정하는 기능은 아니다.
6. AI execute_feature는 새 명세 검증 → 적용 제외 후보 검토 → 추가 증거 필요 검사 → 개별 수행을 거친다. v2의 미연결은 Qwen 자유 계획으로 대체하지 않는다. 기존 v1은 기존 계획/코드 경로다.
7. 정상 로그인 LOGIN-04는 Qwen이 저장 계정 동작을 선택하고 Playwright가 아이디·비밀번호·제출을 한 번 수행한다. 로그인의 시작 상태 검사는 실제 로그인 폼 관찰을 요구한다.
8. 일반 계획은 관찰된 elementId/href만 사용하며 한 계획 최대 1조작, 최대 12회다. 브라우저 기본 API 자체는 기존 8조작 상한을 갖지만 현재 Qwen Plan 계약은 1조작이다.
9. TC마다 새 context를 만들고 일반 업무 TC는 저장 계정 사전 로그인을 사용한다. 최초 진입/로그인 화면 시험의 anonymous 예외가 있으므로 다음 절의 조건부 문제도 확인한다.
10. 브라우저는 허용 origin·쓰기 제한·비밀번호/파일 일반 입력 제한, 활성 dialog/menu 관찰·가려진 요소 제외·준비 대기·다운로드를 관리한다. 예외 코드는 오류/미수행으로 보존한다.
11. 관찰/행동 증거와 명시 FINISH 후 코드 조건 비교·Jev 선택형 판정을 한다. screenshots는 사용자 검토용이고 Qwen/Jev가 이 경로에서 이미지를 직접 해석하지 않는다.
12. 실행 증거는 test_execution_evidence에 별도 저장한다. snapshot에는 증거 URL/개수와 항목 결과를 보관하고 상세 화면에서 증거를 가져온다.

### 판정과 시간 한도

- 일반 계획 호출 바깥 대기 최대 300초, TC 전체 일반 예산 900초. 내부 일반 HTTP timeout은 600초여도 바깥 한도/남은 TC 예산이 더 먼저 적용된다.
- 정상 로그인·최초 복구 계획은 최대 300초와 남은 TC 예산, 작은 결정의 keep_alive는 15분이다. 실행 선택은 120초다.
- 준비된 단순 조회 명세는 최대 60초다. TMS→AI HTTP 한도는 18분이다.
- Jev 기본 confidence .9는 정확도 90%를 뜻하지 않는다. 원문 조건·대상·수행·전체 범위·반대 증거·저장 재확인 보호 검사를 통과해야 P를 유지한다.
- 자동화 오류·추가 정보·적용 제외는 제품 F나 자동 N/A와 다르다. Jev N_A 판정과 EXCLUDED도 구분한다.
- 취소는 상태/attempt fencing과 활성 검사·브라우저 감시를 사용한다. 모든 in-flight 모델/HTTP 요청을 즉시 취소하는 보장은 없다.

## 9. 06/07 사용자 검토·재시험·보고서

ExecutionReviewService.review는 종료한 실행의 revision·사유·판정을 검사하고 automaticResult/automaticReason과 reviews 이력을 보존한다. CONFIRMED 결함은 F에만 허용한다. 사용자 수동 P는 자동 P 보호 검사를 재실행한 결과가 아니며 manualOverride로 구분한다.

retry는 선택 TC 또는 기본 F/미판정으로 새 준비 이력을 만든다. resume는 공통·문서/완료 미판정을 제외하고 미완료 브라우저 TC 중 재실행 정책에 맞는 것만 선택한다. 변경 허용 시험의 불확실한 항목은 자동으로 반복하지 않는다. 준비만으로 시험을 시작하지 않는다.

report는 ExecutionReviewService가 만드는 Markdown 초안이다. report-service의 기업용 보고서 엔진이나 확정된 기업 서식 출력이 아니다. 현재 판정/사유·입력/기대 출력·이전 실패·검토 이력은 이미 포함한다.

## 10. 남은 문제: 근거 수준을 구분한 기록

아래는 새 개발 약속이 아니라 **코드에서 확인한 제한/조건부 위험**이다. 수정 우선순위는 이 검토에 대한 사용자 판단 후 정한다.

| 항목 | 근거·현재 동작 | 검증 수준 |
| --- | --- | --- |
| v2-read 미연결을 저장할 사용자 경로 | upgradeRead는 null bindings/UNRESOLVED로 생성. MenuTcController에는 생성/조회/다운로드만 있고 UI에는 본문/bindings 저장 호출 없음 | 소스 확인. 모든 기존 v1 실행이 막힌다는 뜻은 아님 |
| 준비 상태의 제품별 기대 데이터 | 현재 메뉴·문서 발췌·입력 문장이 전달되지만 모든 선택자/정답 데이터가 자동 연결되는 구현은 아님 | 소스 확인. 대표 실제 제품별 확인 필요 |
| 로그인 TC를 빼고 첫 업무 TC만 선택한 경우 | worker.firstEntry는 첫 수행 TC에 true. crawler.open은 initialEntry면 사전 로그인 생략. login_saved_account는 LOGIN-04만 허용 | 코드상 조건부 위험. 이번 감사에서 해당 조합 실서버 재현은 하지 않음 |
| 인증 시험 전체 자동화 | prerequisites는 전용 잘못된 인증/마스킹/잠금/복수 세션/비밀번호 변경 등을 사전 정보 필요로 차단 | 소스 확인. 27B 변경만으로 해결되지 않음 |
| 권한까지 요구하는 정상 로그인 TC | 실제 /login→/dashboard 성공과 모든 역할 접근 검증은 다름 | 기존 27B 검증에서 수행 완료·NEEDS_REVIEW |
| POST 조회 | 읽기 모드에서 비로그인 POST/PUT 등을 기본 차단 | 소스 확인. POST 조회가 있는 제품에서는 자동화 제한 가능 |
| 1분마다 독립 화면 캡처 | 현재 단계별 관찰 캡처/상세 조회는 있음. Qwen 응답 대기와 독립된 60초 주기 영상 스트림은 해당 경로에 없음 | 소스 확인. 기존 증거 화면과 동영상 모니터링을 구분 |
| 과거 /crawl | crawler/index.js의 이 라우트는 internalAuth를 쓰지 않고 별도 Chromium 이동을 수행 | 소스 확인. 현재 02/05 경로와 별개. 도달 가능 범위는 배포 설정에 따라 다름 |
| 문서 접수 토큰 | main.py /document-intakes는 토큰 dependency가 없고 TMS도 해당 요청에 토큰을 붙이지 않음 | 소스 확인. 다른 AI 내부 router의 인증과 동일하다고 설명하면 안 됨 |
| 오래된 초기화 스크립트 | setup.ps1은 현재 main.py/index.js/requirements/Compose를 덮어쓰는 코드가 있음 | 소스 확인. 실행하지 않음 |
| 수집 완료 문구 분기 | RemoteCrawlPanel의 incompleteEnd는 normalEnd와 같은 COMPLETED/DONE 조건에 !normalEnd를 더해 항상 false. 별도 상세 사유는 표시하지만 해당 제목 분기는 도달 불가 | 소스의 논리식 확인. 이번 문서 작업에서 UI 코드는 변경하지 않음 |
| 추적된 의존성·캐시 | node_modules 800개, 생성 파일 37개가 Git 추적 목록에 있음 | 인벤토리로 확인. 이번 작업에서 삭제하지 않음 |

## 11. README에서 정정한 사항

| 이전 설명 | 코드 근거에 따른 정정 |
| --- | --- |
| 실행 모델 기본 qwen3:8b 및 설치 명령 | qwen3.8:27b, EXECUTION_QWEN_MODEL과 Ollama 모델 설치를 일치 |
| 계획 배치당 8조작 | Qwen 계획당 1조작. 브라우저 API의 8조작 상한과 구분 |
| 정상 로그인 계획 60초 | 최대 300초/남은 TC 예산, 초기 로딩을 포함 |
| Test connection은 2xx/3xx면 성공 | 로그인 필요 선택 시 loginVerified 및 설정 저장 성공도 필요 |
| 다음에 사용자 검토/재시험을 새로 구현 | 기존 기능·추가로 없는 실행 명세 저장 경로를 명시적으로 구분 |
| self-llm-v2의 서버 정합성이 미완료 | 현재 self-llm-v4와 requestId/참조 복원 구현, 과거 문서와 구분 |
| frontend README의 06/07은 탐색 탭 | 실제 판정 저장·결함 상태·선택 재시험·Markdown 초안 구현 |
| 계정 인증·권한 기능 및 전체 시험 품질 | 제품 접속 로그인, 프로그램 사용자 인증, 제품 권한 시험을 각각 구분 |

## 12. 검사·미검증 범위

이번 감사는 정적 코드/문서 대조다. 기존 기록의 Python 41건·Chromium 17건·실제 정상 로그인 58초/8.9초를 이번에 새로 수행한 것처럼 보고하지 않는다. 모든 제품/모든 TC/모든 외부 의존성의 동적 검증은 이번 문서 감사의 완료 항목이 아니다.

정적 파일 목록은 scripts/audit-code-catalog.cjs로 재현한다. 현재 소스와 문서의 버전·한도·경로·파일 수, 상대 링크를 대조한다. 원본 문서·운영 DB·기존 산출물·사용자 입력은 수정하지 않는다.

2026-10-28 정적 검사 결과: Git 추적 경로 1,321개와 인벤토리의 일치·중복 없음, 파일 해시 1,321개 일치, 자체 코드/검증 파일 설명 336개, 관련 문서 상대 링크 1,699개 중 끊어진 링크 0개를 확인했다. 목록 생성기의 Node 구문 검사와 git diff --check도 통과했다. 이번 문서 변경은 아직 커밋/배포하지 않았으며 제품 기능 코드는 변경하지 않았다.
