# 원격 Playwright 실행기

## 수집 기반 메뉴트리

최신 실행기는 각 화면의 `navigation`에 메뉴 경로(상위→하위), 표시 순서, 메뉴 종류, 대상 URL, 발견한 프레임 URL과 `menuKey`를 함께 전송한다. 탐색 영역(`nav`/ARIA navigation·menu·tablist/사이드바)만 대상으로 하며 본문 링크를 메뉴로 간주하지 않는다. 허용 사이트의 표시 중인 iframe도 포함한다. 메뉴 링크·탭·탐색 버튼을 실제 클릭하고, 중첩 이동은 시작 화면부터 클릭 순서를 재현한다. 일반 입력 폼·위험한 작업 버튼과 호스트 선택 드롭다운은 클릭하지 않는다. 수집 한도 내에서 탐색하며 미방문 항목은 미확인으로 남는다.

V18은 `playwright_crawl_pages.navigation` JSONB에 원본 관찰을 보존한다. V19는 `screen_state`(상태 해시·로딩 판정·프레임 근거)와 `nav_transition`(클릭 메뉴·출발 화면·재현 순서·결과)을 추가한다. 작업 요약은 `playwright_crawl_jobs.crawl_result`에 저장한다. URL은 방문·중복 제거·캡처 연결의 단독 기준으로 사용하지 않는다. 동일 URL의 메뉴와 상태는 별도로 보존하며 서로 다른 메뉴가 같은 상태로 이어지는 것도 허용한다. `screenKey`는 그 시점의 상태 지문이며 실시간 값이 바뀌면 달라질 수 있다.

`GET /api/submissions/{id}/playwright-crawls/{job}/menu-tree`(`X-Member-Id` 소유권 확인)는 `captured-menu-tree-v2` 초안을 반환한다. 노드는 `nodeId`, `menuKey`, `title`, `kind`, `url`, `children`, `sources`, `screens`를 가진다. `CAPTURED`는 해당 메뉴를 클릭한 결과가 `READY/CONFIRMED`라는 뜻이다. `LOAD_TIMEOUT`은 로딩 미완료 진단 캡처, `NO_STATE_CHANGE`는 클릭 후 상태 변경·선택을 확인하지 못한 결과, `UNVISITED`는 메뉴만 발견됨, `UNVERIFIED`는 이전 수집으로 클릭 근거가 없음, `GROUP`은 계층 노드다. 이전 URL 일치만으로 정상 캡처를 연결하지 않는다. 이는 기능 정상 판정이 아니다. 트리는 최초 발견 순서를 유지하며 최대 2,000개 노드를 표시한다. 사용자 승인·문서 메뉴트리·TC 생성에 자동 반영하지 않는다.

수집 결과 아래 **메뉴트리 보기**에서 트리와 연결된 캡처를 확인한다. 수집 중에는 버튼으로 새로고침한다. 이전 실행기로 수집한 결과에는 계층을 추정해 채우지 않으며 재수집이 필요하다.

화면 수집 전 **페이지 로딩 최대 대기 시간**을 1~60초로 설정·저장한다(기본 30초). 각 이동의 HTML 로드·표시 중인 iframe·주요 스피너/aria-busy·본문 구조 안정(1초, 최초 최소 1.5초)에 적용하며 전체 수집 시간 한도도 넘지 않는다. 지속 네트워크 통신 종료를 필수 조건으로 삼지 않는다. 시간 초과 시 `LOAD_TIMEOUT` 진단 캡처를 저장하며 해당 화면에서 후속 탐색은 하지 않는다. 이동을 재현할 출발 화면 자체가 준비되지 않으면 작업 요약에 이동 실패로 남긴다. 로컬·원격 동일하게 적용한다. 인식하지 못하는 사용자 정의 로딩 표시나 의미적 데이터 완결성까지 보장하지는 않는다.

표시 중인 허용 사이트 iframe의 본문 텍스트·UI 요소·링크를 함께 저장하고, 프레임 안의 입력란도 마스킹한다. URL의 query나 토큰은 저장하지 않으며 라우팅 상태 차이는 원본 주소의 해시로만 화면 지문에 반영한다.

## 기본 접속·로그인 테스트

기본 실행 위치는 현재 서버의 `crawler-service` Playwright다. 별도 실행기 등록 없이 URL과 권한 확인을 입력하고 **Test Connection**을 누른다. 이 검사는 로그인·화면 수집을 하지 않고 브라우저에서 HTTP 응답이 도착하는지만 확인한다. 401/403/500도 연결 응답이므로 HTTP 상태를 함께 표시하며 정상 로그인이나 제품 정상 동작으로 해석하지 않는다.

그다음 아이디·비밀번호를 입력해 **로그인 테스트**를 누른다. 일반 로그인은 입력칸·버튼을 자동 탐색하고, 비밀번호 입력칸이 사라진 뒤 로그아웃 메뉴 또는 URL 변경과 탐색 메뉴의 조합으로 성공을 확인한다. 자동 판정이 불확실할 때만 고급 설정의 성공 URL/요소를 지정한다. 성공을 확신할 수 없으면 `LOGIN_NOT_CONFIRMED`로 표시한다. 테스트는 화면 수집을 시작하지 않고 설정을 저장하지도 않는다. 비밀번호는 작업 동안만 암호화 저장하며, UI 조회에는 포함하지 않는다.

**원격 PC에서 실행** 옵션을 켜면 동일한 두 테스트가 선택된 원격 PC에서 수행된다. 원격 실행기 등록·설치는 이 옵션을 사용할 때만 필요하다. URL/실행 위치를 변경하면 두 테스트 결과를 무효화하며 계정 변경 시에는 로그인 테스트만 다시 요구한다. 새로고침 후에도 현재 입력에 대한 검증을 다시 수행한다. 연결·로그인 확인 뒤 설정 저장 및 화면 수집 시작 버튼을 사용한다. 실제 수집은 새로운 브라우저 세션에서 다시 로그인한다.

검사는 기존 작업 API에 `purpose: CONNECTION|LOGIN|CRAWL`, `executionMode: LOCAL|REMOTE`, 테스트용 `draft`를 함께 전송한다. 기존 실행기 클라이언트와 호환되는 기본값은 REMOTE/CRAWL이며 새 UI는 항상 실행 위치를 명시한다. V17은 로컬 작업의 nullable agent와 검사 종류/결과를 추가한다. 로컬 실행은 내부 토큰으로 보호된 `crawler-service /api/v1/browser-jobs`와 TMS `/api/internal/playwright-crawls/events`를 사용한다. 원격은 기존 AES-GCM 채널을 동일하게 사용한다. 로컬 실행기는 한 번에 하나의 검사/수집을 수행한다.

관리 서버 `.206`과 시험용 PC `.205`는 그대로 사용한다. 대상 제품에 접근 가능한 `.205`에서 Chromium이 실행되며, 관리 서버에는 인바운드로 접속해서 작업을 가져온다. `.205`에는 리스닝 포트를 열거나 원격 데스크톱을 연결할 필요가 없다.

## 최초 설치

1. 관리 화면의 **실행 PC 최초 등록**에서 이름(예: `시험 PC .205`)과 `.205`에서 접근할 관리 서버 주소(예: `http://210.96.71.206:8080`)를 입력한다. 다른 PC의 실행기를 등록할 때 `localhost`를 사용하지 않는다.
2. 실행기를 등록하고 `agent-config.json`을 다운로드한다. 등록 키는 이 응답에서 한 번만 반환하며 재조회하지 않는다. 파일을 잃으면 연결을 해제하고 새로 등록한다.
3. 실행기 ZIP을 `.205`에 압축 해제하고 `package.json` 옆에 설정 파일을 둔다. Windows용 Node.js 24 LTS가 필요하다.
4. 해당 폴더에서 PowerShell로 `./remote-agent/Install.ps1`을 실행한다. npm 잠금 파일 기준으로 의존성과 Chromium을 설치하고, 설정 파일의 읽기 권한을 현재 Windows 사용자로 제한한다.
5. `./remote-agent/Start-Agent.ps1`을 실행한 채로 둔다. `관리 서버 연결 완료 · 작업 대기`가 표시되며 UI에서 약 5초 이내에 연결 상태가 바뀐다.

조직의 PowerShell 정책으로 스크립트 실행이 제한된 경우 정책을 변경하지 않고, 폴더에서 `npm ci --omit=dev`, `npx playwright install chromium`, `node remote-agent/agent.js agent-config.json`을 순서대로 실행할 수 있다. 이 경우에도 설정 파일은 시험 PC의 사용자만 읽을 수 있도록 보관한다.

설정 파일은 제품 비밀번호가 아니라 실행기 전용 등록 키를 포함한다. Git·문서·공유 폴더에 업로드하지 않는다. 시험 대상 사이트의 자체 서명·만료·호스트 불일치 등 인증서 신뢰 오류는 로컬·원격 연결/로그인 테스트 및 화면 수집에서 허용한다. 실제 HTTP 응답과 로그인 성공 여부는 계속 확인한다. 관리 서버와 실행기 사이의 통신에는 이 설정을 적용하지 않는다.

## 일상 사용

- 문서 Jev 검증 → 대상 URL·로그인 계정·수집 범위 저장 → 연결된 실행 PC 선택 → **로그인하고 화면 수집 시작**.
- 사용 가능한 실행기가 하나뿐이면 자동 선택한다. 시작 URL은 관리 서버나 실행기 IP가 아니라 실제 제품 URL(예: `https://211.233.211.228/`)이다.
- 일반 폼 로그인의 입력칸·버튼·성공 여부는 자동 인식하며 CSS 선택자와 성공 URL/요소로 보정할 수 있다. HTTP Basic 인증과 로그인 없는 사이트도 지원한다. SSO·MFA·CAPTCHA·사용자 직접 로그인은 현재 자동 실행 대상이 아니다.
- 로그인 후에는 같은 브라우저 컨텍스트의 세션으로 앵커 링크와 탐색 그룹을 수집한다. 허용 사이트·포함/제외 경로·화면 수·탐색 깊이·시간·뷰포트를 적용한다. 데이터 입력 폼은 제출하지 않고 로그인 후 POST/PUT/PATCH/DELETE를 차단한다. 따라서 POST 기반 조회 API, 앵커 없이 동작하는 SPA 버튼, 복잡한 다단계 로그인은 추가 어댑터가 필요할 수 있다. 모든 메뉴를 빠짐없이 수집했다고 보장하지 않는다.
- 화면별 JPEG(입력칸과 비밀번호 텍스트 마스킹), 본문 텍스트, 요소 요약, URL·이동 링크를 서버에 보관한다. HTML의 스크립트·입력값·쿠키·storageState는 전송하지 않는다. 화면별 최대 2 MiB, 작업당 스크린샷 64 MiB, 기본 100화면/깊이 5/600초다.
- UI에서 진행률·화면 목록·스크린샷·화면 텍스트를 확인하거나 중단한다. 완료하지 못한 작업도 이미 수집한 화면은 남긴다. 한 실행기 및 한 제출당 활성 작업은 하나다.
- 이번 구현은 **원격 실행기 → 로그인 → 화면/구조 수집 → 결과 회수**까지다. 기능리스트·매뉴얼·수집 결과를 합친 메뉴트리/TC 자동 작성은 별도 후속 작업이다. 실제 테스트 결과나 결함을 자동 판정하지 않는다.

## 연결·복구 계약

- 관리 API: `GET|POST /api/crawl-agents`, `POST /api/crawl-agents/{agentId}/revoke`.
- 제출 API: `GET|POST /api/submissions/{id}/playwright-crawls`, `POST .../{jobId}/cancel`, `GET .../{jobId}/pages/{index}`, `GET .../{jobId}/pages/{index}/screenshot`. 현재의 개발용 `X-Member-Id` 소유권 검사를 적용한다. 실제 운영에는 계정 인증 연동이 필요하다.
- 실행기 API: `POST /api/crawl-agents/{id}/channel/{heartbeat|claim|event}`. 키 자체를 HTTP 헤더/본문에 보내지 않고 AES-256-GCM 봉투로 요청/응답을 인증·암호화한다. 요청 AAD는 `autotest-agent-v1\n<agentId>\n<action>\n<timestamp>\n<nonce>`이며 응답 AAD는 `response\n<nonce>`다. nonce 재사용과 60초 이상 시계 차이는 거부한다. HTTPS도 함께 사용하면 접속 메타데이터를 추가 보호할 수 있다.
- 키는 서버에서 기존 credential key로 암호화하고 실행 PC 설정 파일로 최초 전달한다. 제품 비밀번호는 작업을 claim한 실행기에만 암호화된 응답으로 전달하고 실행기 메모리에서만 사용한다. 서버·실행기 로그에 키·비밀번호·페이지 본문을 출력하지 않는다.
- 상태: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELED`. 실행기 heartbeat 5초, 작업 heartbeat 10초, lease 45초다. lease 만료는 조회/작업 claim 시 `AGENT_DISCONNECTED`로 종료한다. 재접속 후 자동으로 실패한 브라우저 작업을 반복하지 않으며 사용자가 새 수집을 시작한다. 대기 작업도 10분 후 만료한다.
- 이벤트는 작업·실행기·claim token·미만료 lease를 모두 확인하고 행 잠금으로 직렬화한다. 중단 뒤 늦게 도착한 화면/완료 이벤트는 거부한다. 실행기 연결 해제는 등록 키를 폐기하고 활성 작업을 중단한다. 설정은 작업 시작 시 스냅샷으로 고정하며 문서 digest는 claim 시 다시 검증한다.
- V16의 `crawl_agents`, `crawl_agent_nonces`, `playwright_crawl_jobs`, `playwright_crawl_pages`에 보관한다. 스크린샷은 초기 구현에서 제한된 BYTEA로 저장하며 기존 업로드/S3 원본은 건드리지 않는다. 운영 스키마 반영 전 pg_dump 백업을 확보한다.

## 개발·패키징

저장소 루트에서 `./scripts/package-crawl-agent.ps1`을 실행하면 `frontend/public/downloads/autotest-crawl-agent.zip`을 생성한다. 설정 파일·키·node_modules·테스트 문서는 포함하지 않는다. 프런트엔드 개발 서버 또는 배포 빌드에서 `/downloads/autotest-crawl-agent.zip`으로 다운로드한다. 패키징은 `npm run build` 이전에 수행한다.

검증: `./gradlew.bat :test-management-service:test :test-management-service:bootJar :api-gateway:bootJar`, `npm run build`(frontend), `npm run test:agent`(crawler-service, Chromium 설치 환경). 실제 `.205` 설치·대상 URL 로그인은 해당 PC와 제품 계정이 준비된 뒤 확인해야 한다. 로컬 fixture 성공을 실제 제품 접속 성공으로 보고하지 않는다.
