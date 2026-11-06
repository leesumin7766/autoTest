# autoTest 개발 인수인계 — 2026-11-05 (운영 배포·업무 허브 연동)

이 문서는 다음 LLM이 **사내 서버 배포와 업무 허브 API 연동** 작업을 이어받기 위한 자료다. 이 문서만 읽어도 맥락을 알 수 있게 작성했다. 작업 전에 저장소 코드와 실행 환경을 직접 확인하라.

용어: **구현·검증 완료**, **설계만 확정(미구현)**, **미결정**을 구분한다. 계획을 완료로 보고하지 않는다.

> **2026-11-06 변경(이 문서의 이전 인증 설계보다 우선):**
> - `deploy-prod-env`를 `main`에 합쳤다(`origin/main` = `7973a22`). 이후 작업은 `hub-api-key` 브랜치에서 한다.
> - 사용자 결정: 사용자 로그인은 만들지 않는다. 인증은 **외부 프로그램(허브)의 API 호출에만** 쓴다.
> - 그래서 2단계를 **게이트웨이 API 키**로 바꿔 구현했다(`ApiKeyFilter`, `scripts/new-api-key.mjs`, `GET /api/v1/hub/whoami`). 설계는 `HUB_API_INTEGRATION.md` 3장.
> - 이전 로그인 설계로 누군가 작업하다 커밋하지 않은 코드(auth-service 계정·JWT·로그인 화면)는 로컬 브랜치 `wip-login-auth`(`dd796c2`)에 보관만 했다. 사용하지 않는다.
> - 아래 5장 2~4번, 6.B는 이 변경으로 대체됐다.
> - 3단계 허브 조회 API v1도 같은 브랜치에서 구현·검증했다(6.C). 계약은 `docs/hub-api-v1.openapi.yaml`.
> - 허브 범위는 **조회 + 웹 화면 링크 이동까지**로 확정했다(2026-11-06 사용자 결정). 허브에서 시험 실행 시작·완료 알림은 만들지 않는다. 아래 4단계·`202`/`409`·이벤트 관련 내용은 무효다.

---

## 0. 한눈에 보기

| 구분 | 상태 |
| --- | --- |
| 1단계 운영 배포 구성(Docker 전용, HTTPS 단일 진입점) | **구현·검증 완료**. `origin/deploy-prod-env`에 push(`8e6c64d`) |
| 실제 서버를 운영 구성으로 전환 | **미수행**. 사용자 승인 필요(데이터·실행 중 서버 영향) |
| 2단계 인증 → **API 키 방식으로 변경**(게이트웨이 `ApiKeyFilter`) | **구현·검증 완료**(`hub-api-key` 브랜치) |
| 3단계 허브 API v1(`/api/v1/hub/*` 조회) | **구현·검증 완료**(`hub-api-key` 브랜치) |
| 허브에서 실행 시작·완료 알림 | **하지 않음**(2026-11-06 사용자 결정) |
| 허브와 웹의 연결 방식(API만 / 화면 열기 / 둘 다) | **미결정**. 설계는 둘 다 지원 |

---

## 1. 제품과 이번 작업의 목적

autoTest는 업체 문서(기능리스트·매뉴얼·시험합의서)를 받아 다음 순서로 시험을 지원하는 웹 프로그램이다.

화면 수집 → 메뉴트리 → TC 생성 → Playwright 시험 실행 → 매뉴얼 검토 → 결함 검토·보고서

이번 작업의 목적:
1. 이 웹 프로그램을 **서버(이 PC)** 에 올린다.
2. 사용자가 따로 만든 **업무 허브 프로그램**이 API로 연결할 수 있게 한다.

### 사용자가 확정한 사실(2026-11-04)

- **서버:** 사용자의 Windows 11 PC. GPU 없음, RAM 32GB, Docker Desktop으로 상시 실행한다(Docker 메모리 약 23.5GiB).
- **허브:**
  - 별도 프로젝트이며 **Electron** 앱이다. **exe 설치 프로그램**으로 사용자 PC마다 배포한다.
  - 서버와 **같은 PC에서는 실행하지 않는다**. 대부분 **같은 사내망**에서 쓴다.
  - 도메인(주소)이 서버와 다르다.
- **인증:** 허브와 autoTest **모두 사용자 인증이 없다**. autoTest에는 빈 `auth-service` 모듈만 있다.
- **허브 연결 방식:** **미결정**이다.
- **사용자 요청:** 지금까지 로컬에 설치하던 것들을 Docker로 묶어 올린다. "컨테이너 하나"가 아니라 **Compose 하나로 한 번에 실행**하는 방식으로 합의했다(이유는 5장).

> 참고: 2026-11-02 인수인계(메인 체크아웃의 `LLM_HANDOFF_2026-11-02.md`, 이 브랜치에는 없음)에는 "무료·고정 URL로 외부 공개"가 후보로 적혀 있었다. 11-04 대화에서 허브가 같은 사내망이라는 점이 확인됐다. 그래서 현재 구성은 **사내망 HTTPS**다. 외부 공개(Cloudflare Tunnel 등)는 필요해지면 다시 검토하며, 이 경우 2단계 인증이 반드시 먼저 끝나 있어야 한다.

---

## 2. 저장소·브랜치·폴더

- **저장소:** `https://github.com/leesumin7766/autoTest`
- **이번 작업 브랜치:** `deploy-prod-env` (원격 `origin/deploy-prod-env`, 커밋 `8e6c64d`, 이후 이 문서 커밋)
  - `origin/dev-read-spec-bindings`(`9505c73`)에서 분기했다. 이 브랜치는 origin/main(`81f5ff9`)을 포함한 당시 최신 통합 브랜치다.
  - 다른 작업 공간에서 진행 중이던 `dev-self-llm`(+2 커밋)과 `dev-captured-menu-tree`(+1 커밋)는 **포함하지 않았다**. 병합할 때 충돌 여부를 확인한다.
  - main에는 아직 병합하지 않았다.
- **작업 폴더(worktree):** `C:\Users\tta\autoTest\.runtime\dev-document-publish`
- **실행 중인 서버:**
  - 메인 체크아웃 `C:\Users\tta\autoTest`에서 개발 compose(`docker-compose.yml`)로 띄운 Compose 프로젝트 **`autotest`**다.
  - 2026-11-04 기준 11개 컨테이너가 2일째 실행 중이었다.
  - **이 서버를 임의로 재생성·중지하지 않는다.**
- 다른 worktree(`C:\Users\tta\.codex\worktrees\...`)도 있다. 작업 전에 `git worktree list`, `git status`로 위치를 확인한다.

---

## 3. 아키텍처(현재)

```text
[개발 구성: docker-compose.yml]
frontend(Vite 5173, 호스트에서 실행) ─/api→ api-gateway:8080 ─┬ auth-service:8081 (비어 있음)
                                                              ├ test-management-service(TMS):8082  ← 실제 업무 로직 대부분
                                                              ├ test-execution-service:8083 (실제 실행 worker는 TMS 안)
                                                              └ report-service:8084 (보고서도 실제로는 TMS)
TMS → ai-service:8005(Python), crawler-service:8006(Node/Playwright), manual-review-service:8007, db(Postgres+pgvector), seaweedfs(S3)
ai-service/manual-review → ollama:11434 (qwen3.8:27b 실행 계획, qwen3:4b 매뉴얼 검토)
개발 구성은 db·seaweedfs·8080~8084·8005·8006을 모두 호스트에 공개한다.

[운영 구성: + docker-compose.prod.yml]  ← 이번에 추가
사내 PC ─HTTPS 443─▶ caddy ─┬ /                 frontend 정적 빌드
                            ├ /api/*            api-gateway:8080
                            ├ /api/internal/*   404 (차단)
                            └ /downloads/...    autotest-server-ca.crt, autotest-crawl-agent.zip, 공통 규칙 md
나머지 서비스는 내부 Docker 네트워크에서만 접근한다(호스트 포트 없음).
```

**사용자 식별의 현재 상태:**
- 모든 업무 API가 `@RequestHeader("X-Member-Id")`로 소유자를 구분한다.
- 이 값은 화면 입력칸에서 그대로 온다(`frontend/src/App.tsx:86` 초기값, `:377` 입력칸, localStorage `autotest:last-member:v1`).
- 따라서 **값을 바꾸면 다른 사람의 작업을 열 수 있다.** 2026-11-06 결정으로 웹 화면은 이 상태를 유지한다(사내망 전용). API 키 요청만 게이트웨이가 회원 ID를 덮어쓴다.

**서비스 간 내부 API:**
- `/api/internal/*`는 TMS에서 `X-Internal-Token`(`PRODUCT_DESCRIPTION_INTERNAL_TOKEN`)을 상수 시간 비교로 검사한다.
- ai-service(`ai-service/test_execution.py:190`)와 crawler도 이 토큰을 보낸다.
- 게이트웨이는 `/api/internal/**`을 라우팅하지 않는다.

> 앞선 설계 초안에는 "ai-service 내부 토큰 누락"이라고 적었으나 **사실이 아니었다**. 문서는 정정했다.

---

## 4. 완료한 작업 — 1단계 운영 배포 구성(커밋 `8e6c64d`)

### 4.1 추가·수정 파일

| 파일 | 내용 |
| --- | --- |
| `docker-compose.prod.yml` | 개발 compose 위에 겹치는 운영 override. 내용은 아래 목록 |
| `docker/java-service.Dockerfile` (+ `.dockerignore`) | `ARG SERVICE`로 Java 5개 서비스를 이미지 안에서 Gradle 빌드(BuildKit 캐시). 런타임은 temurin 21 JRE alpine, uid 10001, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError`. TMS가 리소스로 쓰는 `frontend/public/downloads/menu-tree-common-rules.md`도 함께 복사 |
| `docker/web.Dockerfile` (+ `.dockerignore`) | `node:24.21.0-slim`에서 frontend `npm ci && npm run build`. `alpine:3.22`에서 원격 실행기 ZIP 생성(`scripts/package-crawl-agent.ps1`과 같은 파일 구성). 최종 이미지는 `caddy:2.10.2-alpine` |
| `docker/Caddyfile` | 아래 목록 |
| `.env.prod.example` | 운영 `.env` 항목(사이트 주소, DB 계정, S3, 내부 토큰, LLM, `OLLAMA_MODELS`) |
| `crawler-service/remote-agent/Start-Agent.ps1` | `node --use-system-ca`로 실행. 옆에 `server-ca.crt`가 있으면 `NODE_EXTRA_CA_CERTS`로 지정 |
| `docs/PRODUCTION_DEPLOYMENT.md` | 운영 절차(서버 준비·실행·전환·인증서·갱신)와 검증 기록 |
| `docs/HUB_API_INTEGRATION.md` | 허브 연동 전체 설계(인증·API·단계) |
| `README.md` | 운영 배포·허브 연동 링크 |

`docker-compose.prod.yml`의 내용:
- `name: autotest`를 지정했다. 실행 폴더가 달라도 기존 볼륨(db_data, seaweed-data, ollama_data, generation_cache, playwright_credentials)을 이어 쓴다.
- caddy만 `${AUTOTEST_HTTP_PORT:-80}:80`, `${AUTOTEST_HTTPS_PORT:-443}:443`을 공개한다. 나머지 서비스는 `ports: !reset []`이다.
- 고정 `container_name`은 `!reset null`로 없앴다. 그래서 프로젝트 이름만 바꿔 격리 검증을 할 수 있다.
- 빌드 이미지는 `:prod` 태그를 쓴다. 개발 이미지를 덮어쓰지 않는다.
- seaweedfs·ollama는 11-04에 실행 중이던 이미지 **digest로 고정**했다.
- ai-service는 bind mount와 `--reload`를 제거했다. `command`를 재정의했고, `volumes: !override`로 generation_cache만 남겼다.
- crawler는 `volumes: !reset []`이다.
- DB 비밀번호는 `${DB_PASSWORD:?}`이고, `AI_DATABASE_URL`에도 들어간다.
- 모든 서비스가 `restart: unless-stopped`이다.
- 메모리 한도:

  | 서비스 | 한도 |
  | --- | --- |
  | ollama | 20g |
  | crawler | 4g |
  | TMS | 3g |
  | ai | 2g |
  | db | 2g |
  | seaweedfs | 1g |
  | 다른 Java 서비스 | 768m |
  | caddy | 256m |

- `ollama-init`은 일회성 서비스다. `OLLAMA_MODELS`(공백 구분, 기본 `qwen3.8:27b`)에 지정한 모델 중 없는 것만 `pull`한다.

`docker/Caddyfile`의 내용:
- 전역 옵션: `admin off`, `skip_install_trust`, `read_body 30m`.
- 사이트 주소는 `{$AUTOTEST_SITE_ADDRESS}`다. 쉼표로 여러 개를 지정할 수 있다(IP·사내 DNS 이름).
- `tls internal`로 Caddy 자체 CA를 쓰며, 루트 인증서의 유효 기간은 10년이다.
- `/api/*` 프록시: `flush_interval -1`, `response_header_timeout 30m`, `request_body max_size 512MB`.
- `/api/internal/*`는 404로 차단한다.
- 루트 CA를 `/downloads/autotest-server-ca.crt`로 제공한다.
- SPA fallback을 쓰고, `/assets/*`는 `immutable`, 나머지는 `no-cache`로 캐시한다.
- 보안 헤더: `-Server`, `nosniff`, `X-Frame-Options DENY`, `Referrer-Policy same-origin`.

### 4.2 검증 내용(2026-11-04, 이 PC)

실행 중인 서버에 영향을 주지 않도록 별도 프로젝트로 검증했다.
- 프로젝트 이름 `-p autotest-prodverify`, 새 볼륨, 포트 18443/18080, `OLLAMA_MODELS=` 설정으로 모델 다운로드를 생략했다.
- 검증 env 파일은 scratchpad에 임시 값으로 만들었다.
- 검증이 끝난 뒤 `down -v`로 **검증 프로젝트만** 정리했다. 운영 `autotest` 프로젝트는 계속 실행 중인 것을 확인했다.

| 항목 | 결과 |
| --- | --- |
| 빌드 | 호스트 JDK·Node 없이 Java 5, web, ai, crawler, db 이미지 모두 성공 |
| 기동 | 전 서비스 시작. 새 DB에서 Flyway(V1~V30) 후 TMS healthy |
| 공개 포트 | caddy 외 0개 |
| TLS | 받은 CA로 curl·Node 검증 성공, CA 없이는 거절(Node: `UNABLE_TO_GET_ISSUER_CERT_LOCALLY`) |
| 화면 | Chromium 렌더링, 콘솔 오류 0, "실행기 사용 가능" 표시(게이트웨이→TMS→crawler 경로) |
| API·업로드 | workspaces·executor status 200. xlsx 업로드 → PARSED → `s3://autotest-docs` 저장. 90MB 통과 |
| 크기 초과 | 120·150MB를 TMS가 413 `FILE_TOO_LARGE`로 거절(HTTP/2 경유 시 정상 수신) |
| 차단 | `/api/internal/*` 404, `/actuator/*` 비공개(SPA index 반환) |
| 기타 | ai·crawler bind mount 0, Java uid 10001, 실행기 ZIP에 수정된 Start-Agent 포함, ollama-init 받기 후 재실행 시 즉시 종료 |

**검증하지 않은 것(과장 금지):**
- 27B 모델의 실제 추론(모델 다운로드를 생략함)
- 사내 **다른 PC**에서의 접속(방화벽·IP 설정 전)
- 원격 실행기를 HTTPS로 다시 등록한 뒤의 실제 수집
- 운영 볼륨으로의 전환
- 장시간 안정성

---

## 5. 확정된 설계 결정과 이유

자세한 내용은 `docs/HUB_API_INTEGRATION.md`에 있다. 다시 논의하지 않도록 핵심과 이유를 적는다.

1. **컨테이너 하나로 합치지 않는다.**
   - 서비스별 메모리 한도와 독립 재시작이 필요하다. 27B 모델이 약 17GB를 쓴다.
   - 서비스 하나만 갱신할 수 있어야 한다.
   - manual-review의 내부망 격리(`internal: true`)를 유지해야 한다.
   - 대신 "Docker와 `.env`만 있으면 Compose 한 줄로 실행"하는 것을 설치 단위로 삼는다. 다른 PC로 옮길 때는 `docker save` tar로 오프라인 설치한다(후속).
2. ~~허브 공용 API 키 금지·autoTest 계정 로그인~~ → **2026-11-06 대체:** 사용자·PC별 API 키. exe에 공용 키를 넣지 않는 원칙은 유지한다(사용자가 허브 설정에 자기 키를 입력).
3. ~~auth-service가 인증 주체~~ → **대체:** 게이트웨이가 `.env`의 `AUTOTEST_API_KEYS`(SHA-256 해시)로 검증한다. auth-service는 쓰지 않는다. DB 변경 없음.
4. ~~쿠키·JWT·refresh token~~ → **대체:** `Authorization: Bearer atk_...` 하나. 폐기는 `.env` 항목 삭제 후 `up -d api-gateway`.
5. **게이트웨이가 인증 경계다.**
   - 들어온 `X-Internal-Token`·`X-Api-Client`는 **항상 삭제**한다.
   - API 키 요청은 키의 member ID로 `X-Member-Id`를 덮어쓴다. 그래서 TMS 등 하위 서비스 코드는 바꾸지 않는다. 키 없는 웹 요청은 기존처럼 통과한다(2026-11-06).
6. **Electron은 main 프로세스에서만 API를 호출한다.**
   - renderer는 IPC로 main에 요청한다. API 키는 `safeStorage`로 암호화해 저장한다.
   - main 프로세스의 호출은 CORS 대상이 아니므로 서버에서 CORS를 열지 않는다.
   - Caddy 사내 CA는 main 프로세스의 HTTPS 클라이언트에 CA로 지정한다. `rejectUnauthorized=false`는 금지한다.
   - `Origin: null`은 허용하지 않는다.
7. **허브 전용 API `/api/v1/hub/*`를 따로 둔다.**
   - 기존 `/api/submissions/**`는 허브 계약에 포함하지 않는다. 화면 변경이 설치형 허브를 깨뜨리지 않게 하기 위해서다.
   - v1은 필드 추가만 허용한다.
   - 허브는 `X-Hub-Version` 헤더를 보낸다. 서버가 `/health`의 `minHubVersion`보다 낮은 버전의 요청을 받으면 `426 HUB_UPDATE_REQUIRED`를 돌려준다.
8. **iframe 삽입은 지원하지 않는다.** 도메인이 달라 서드파티 쿠키가 차단된다. 화면 이동은 서버 주소 + `webPath`를 기본 브라우저로 여는 방식이다.
9. **허브에는 서버가 없으므로 웹훅을 쓰지 않는다.** 완료 알림도 만들지 않는다(2026-11-06).
10. **허브는 실행을 시작하지 않는다(2026-11-06).** CPU 추론이라 실행은 한 번에 한 건이고, 실행은 웹 화면에서 한다. 허브는 5초 이상 간격으로 조회한다. 요청 제한(`429`)은 미구현.
11. **사내망도 HTTPS로 운영한다.** API 키가 오가기 때문이다. 사용자 PC·허브·원격 실행기는 Caddy 루트 CA를 신뢰하도록 설정한다.

---

## 6. 다음 작업

### 6.A 운영 전환(사용자 승인 후)

절차는 `docs/PRODUCTION_DEPLOYMENT.md` 4장을 따른다.

1. **백업:** `pg_dump -Fc -f /tmp/...`로 컨테이너 안에 만든 뒤 `pg_restore --list`로 검증하고 `docker compose cp`로 꺼낸다. **PowerShell `>`로 바이너리 dump를 리다이렉트하지 않는다.**
2. **DB 비밀번호 변경:** 기존 볼륨은 `POSTGRES_PASSWORD`를 바꿔도 비밀번호가 바뀌지 않는다. `ALTER USER test PASSWORD '<DB_PASSWORD>'`를 먼저 실행한다. 다시 개발 구성으로 돌아가려면 `test`로 되돌린다.
3. **`.env` 보강:** `AUTOTEST_SITE_ADDRESS`, `DB_PASSWORD`, `OLLAMA_MODELS`를 추가한다(값은 사용자가 입력). 현재 `.env`에는 `LLM_PROVIDER=ollama`, `LLM_MODE=real`, `EXECUTION_QWEN_MODEL=qwen3.8:27b`가 있다.
4. **실행:** 메인 체크아웃 또는 이 브랜치를 체크아웃한 폴더에서 `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`를 실행한다. 프로젝트 이름이 같으므로 개발 컨테이너가 교체된다.
5. **서버 PC 설정:**
   - 방화벽: 443·80을 개인(Private) 프로필에서만 허용한다.
   - DHCP에서 IP를 예약한다.
   - 절전을 해제하고 Docker Desktop을 자동 시작한다.
6. **원격 실행기:** 기존 등록 주소 `http://<IP>:8080`은 닫힌다. 실행기를 `https://<서버>`로 다시 등록하고 CA를 설치한다.
7. **확인:** 사내 다른 PC에서 화면에 접속하고, 실제 27B 실행을 1건 해 본다. 결과는 `docs/PRODUCTION_DEPLOYMENT.md` 7장에 기록한다.

### 6.B 2단계 — API 키(구현·검증 완료, 2026-11-06)

- `api-gateway/src/main/java/com/autotest/api_gateway/ApiKeyFilter.java`: 규칙은 `HUB_API_INTEGRATION.md` 3.2절.
- `HubIdentityController`: `GET /api/v1/hub/whoami` → `{client, memberId}`(허브 키 설정 확인용).
- `scripts/new-api-key.mjs <이름> <회원ID>`: 키 원문과 `.env` 항목(`name:memberId:sha256`) 출력.
- `docker-compose.yml` api-gateway에 `AUTOTEST_API_KEYS` 전달(prod는 base를 상속). `.env.prod.example`에 항목 추가.
- 검증: `ApiKeyFilterTest` 6건 통과. 실제 게이트웨이 jar를 임시 키로 띄워 whoami 200(`memberId` 42), 키 없는 hub 경로 401, 틀린 키 401, 키·무키 일반 경로 모두 필터 통과(하위 서비스 없음 503)를 확인했다.
- 원격 실행기 채널은 `Authorization`을 보내지 않으므로 그대로 통과한다(테스트 포함).
- 남은 위험: 웹 화면·키 없는 `/api/**`는 여전히 회원 ID를 믿는다. 사내망 전용 운영이 전제다.

### 6.C 3단계 — 허브 API v1(조회, 구현·검증 완료 2026-11-06)

**구현 결과(아래 원래 계획보다 우선):**
- `test-management-service/.../presentation/controller/HubV1Controller.java`: health, workspaces, summary, runs, run, report. 기존 `SubmissionWorkspaceService`·`TestExecutionService.status`·`ExecutionReviewService.report`를 재사용하고, 응답은 고정 레코드로 옮긴다(`snapshot` 비노출).
- 게이트웨이 라우트에 `/api/v1/hub/**` → TMS 추가. `whoami`는 게이트웨이 컨트롤러가 먼저 처리한다.
- `X-Hub-Version` 필수(없으면 400), `HUB_MIN_VERSION`(기본 `0.0.0`)보다 낮으면 426.
- `webUrl` 대신 `webPath`(`/?member=&workspace=&page=`)를 준다. `frontend/src/App.tsx`가 이 링크를 처리한다.
- OpenAPI는 springdoc 대신 계약 파일 `docs/hub-api-v1.openapi.yaml`.
- 검증: `HubV1ControllerTest` 8건. 격리 스택(`-p autotest-hubverify`, Caddy HTTPS)에서 16항목 확인 — whoami·health, 키 없음 401, 버전 없음 400·낮음 426, 키로 업로드 시 위조 `X-Member-Id: 1`이 42로 바뀜, 목록·요약·실행 이력, 다른 회원 키 404·빈 목록, 없는 실행 404, `/api/internal` 404. Chromium으로 `webPath` 링크 → 작업 복원·08 단계 이동·콘솔 오류 0.
- 미검증: 실제 실행 이력이 있는 작업의 `runs/{run}`·`report` 응답(단위 테스트로만 확인). 요청 제한(429)은 미구현.
- 검토(2026-11-06): 다른 회원 작업의 `runs`·`runs/{run}`·`report`가 기존 실행 서비스 때문에 403(존재 노출)을 주던 것을 작업 소유권 검사 선행으로 404로 통일했다. `HUB_VERSION_REQUIRED`·`HUB_UPDATE_REQUIRED`를 `TestManagementErrorCode`에 등록해 안내 문구가 전달되게 했다.
- 전체 테스트: api-gateway 7건 통과. TMS는 `TcReadSpecificationTest` 1건 실패(84 기대·85 실제)가 남는데 **`origin/main`에서도 동일하게 실패하는 기존 문제**다. DB 통합 테스트는 공유 개발 DB 연결 수 초과로 한 번 실패했다가 재실행 시 통과했다.

**원래 계획:**

- **엔드포인트:**
  - `GET /api/v1/hub/health` → `{status, apiVersion, minHubVersion}`
  - `GET /api/v1/hub/workspaces?offset=&limit=` (각 항목에 `webUrl`)
  - `GET /api/v1/hub/workspaces/{id}/summary` (단계별 상태, TC 수, 최근 실행 P/F/미판정 건수)
  - `GET /api/v1/hub/workspaces/{id}/runs/{run}`
  - `GET /api/v1/hub/workspaces/{id}/runs/{run}/report` (Markdown)
- **위치:**
  - 데이터가 TMS에 있으므로 TMS에 `HubV1Controller`를 두고, 기존 서비스(워크스페이스·실행·보고서)를 재사용한다.
  - 게이트웨이 `application.yml`에 `Path=/api/v1/hub/**` 라우트를 추가한다.
  - ai-service에도 `/api/v1/functional-menus` 같은 내부 경로가 있지만 게이트웨이를 거치지 않는다. 경로 이름이 겹치지 않는지 확인한다.
- **소유권:** 기존 member 검사를 그대로 쓴다(키에 묶인 회원 데이터만 조회). `/api/v1/hub/**`는 게이트웨이가 이미 키를 강제한다.
- **버전 검사:** `X-Hub-Version`과 `426`.
- **문서:** OpenAPI(springdoc)를 `/api/docs`에 제공한다.
- **허브 개발자용 예시:** Electron main 프로세스 코드를 함께 남긴다. 다음 내용을 담는다.
  - CA 지정
  - API 키를 `safeStorage`로 저장
  - 401을 받으면 키 재입력 안내
  - 426을 받으면 업데이트 안내

### 6.D 이후 후보(허브 실행·알림은 제외)

- 운영 서버 전환(6.A, 사용자 승인 필요). 운영 스택에서 `node scripts/new-api-key.mjs`로 실제 허브 키 발급.
- 허브 쪽 연동 예시(Electron main 프로세스: CA 지정, `safeStorage` 키 저장, 401·426 처리).
- 필요해지면 `/api/v1/hub` 요청 제한(`429`).
- 다른 PC 이전용 `docker save` 오프라인 설치 묶음.
- DB 백업 자동화(작업 스케줄러)와 복구 시험.

### 6.E 제품 기능 쪽 남은 과제(2026-11-02 인수인계 요약, 이번 작업에서 변경 없음)

- **05 시험 실행 신뢰성(P0/P1):**
  - 실패해야 할 시험을 P로 확정하지 않는다.
  - 조회 실행 명세(`execution-spec-v2-read`)의 근거 연결 다음 단계를 진행한다.
  - 조건별 증거, 비동기 완료 확인, 변경 시험 격리, 실제 모델 회귀 집합이 필요하다.
- **06 매뉴얼 검토:** 정확도 측정, 후보 확정·기각, 원문 대조. 범위는 오탈자와 지원 종료 SW로만 의도적으로 축소했다.
- **TC:** 본문 편집, 승인, 이력 관리.
- **보고서:** 기업용 서식, 재현 절차.
- **보안:** 로그·모델 입력·보고서로 흐르는 정보의 마스킹.
- **저장소 이력:** 과거 커밋에 sample 업체 자료가 남아 있다. 완전 제거는 이력 재작성이 필요한 별도 작업이다.

---

## 7. 함정과 교훈(이번 작업에서 실제로 겪은 것)

- **Windows curl(Schannel):** 사내 CA에 대해 `the revocation status is unknown`으로 실패한다. `--ssl-no-revoke`를 붙인다. 브라우저는 이 조회 실패를 통과시키고, Node/Electron은 이 조회를 하지 않는다.
- **HTTP/1.1 크기 초과 업로드:** TMS는 413을 즉시 보내지만, HTTP/1.1 클라이언트는 본문을 계속 보내다가 연결이 끊겨 413을 받지 못할 수 있다(150MB에서 재현). HTTP/2(브라우저)는 정상이다. 허브는 업로드 전에 100 MiB 이하인지 검사한다. 화면에도 업로드 전 크기 검사가 **없다**(개선 후보).
- **격리 검증 시 Host 이름:** 컨테이너 안에서 `https://caddy`로 접속하면 사이트 주소(`localhost`)와 맞지 않아 실패한다. curl은 `--resolve localhost:443:<caddy IP>`, Chromium은 `--host-resolver-rules=MAP localhost <IP>`를 쓴다.
- **Git Bash에서 `docker run -v`:** 경로가 변환되므로 `MSYS_NO_PATHCONV=1`을 설정한다.
- **고정 `container_name`:** 개발 compose의 `autotest-db-1` 등은 다른 프로젝트 이름으로 검증할 때 충돌한다. prod override에서 `!reset null`로 없앴다.
- **이미지 태그 공유:** `autotest-db-pgvector` 태그는 개발과 운영이 같이 쓴다. 검증 중 같은 Dockerfile로 다시 빌드했다(실행 중 컨테이너에는 영향 없음).
- **볼륨 비밀번호:** 기존 볼륨의 DB 비밀번호는 env로 바뀌지 않는다(6.A-2).
- **소스 반영:** 개발 compose는 ai/crawler 소스를 bind mount한다. 운영은 이미지에 소스를 넣으므로 **소스를 바꾸면 `--build`가 필요하다.**
- **`.env`:** 사용자만 수정한다. 비밀 값을 출력하거나 문서·로그·프롬프트에 복사하지 않는다(키 이름만 다룬다).
- **사내 CA 보존:** caddy_data 볼륨을 지우면 CA가 바뀐다. 그러면 모든 PC에 인증서를 다시 배포해야 한다.

---

## 8. 검증 방법(재현용)

```bash
# 격리 운영 스택(실행 중인 autotest 프로젝트와 분리). verify.env 에는 임시 값만 넣는다:
#   AUTOTEST_SITE_ADDRESS=localhost, AUTOTEST_HTTPS_PORT=18443, AUTOTEST_HTTP_PORT=18080,
#   DB_PASSWORD/S3_*/PRODUCT_DESCRIPTION_INTERNAL_TOKEN=임시값, LLM_MODE=mock, OLLAMA_MODELS=
DC="docker compose -p autotest-prodverify -f docker-compose.yml -f docker-compose.prod.yml --env-file verify.env"
$DC config >/dev/null && $DC build && $DC up -d
$DC cp caddy:/data/caddy/pki/authorities/local/root.crt ./root.crt
curl --ssl-no-revoke --cacert root.crt https://localhost:18443/                                        # 200
curl --ssl-no-revoke --cacert root.crt -H 'X-Member-Id: 1' https://localhost:18443/api/crawl-agents/local/status
curl --ssl-no-revoke --cacert root.crt -X POST https://localhost:18443/api/internal/playwright-crawls/events  # 404
docker ps --filter label=com.docker.compose.project=autotest-prodverify --format '{{.Names}} {{.Ports}}'      # caddy만 '->'
$DC down -v --remove-orphans   # 반드시 -p autotest-prodverify 인지 확인한 뒤 실행
```

운영 스택에서 추가할 API 키 검증:
- `node scripts/new-api-key.mjs` 항목을 `.env`에 넣고 `https://<서버>/api/v1/hub/whoami`를 키로 호출 → 200
- 키 없음·틀린 키 → 401, 위조 `X-Member-Id`가 키의 회원으로 바뀌는지
- 원격 실행기 채널 통과
- 허브 조건(Node main 프로세스 + CA + Bearer) 호출

---

## 9. 먼저 읽을 파일(`deploy-prod-env` 루트 기준)

1. `AGENT.md` — 작업·백업·테스트 규칙
2. `docs/HUB_API_INTEGRATION.md` — 허브 연동 설계 전체
3. `docs/PRODUCTION_DEPLOYMENT.md` — 운영 절차·검증 기록
4. `docker-compose.yml`, `docker-compose.prod.yml`, `docker/`
5. `api-gateway/src/main/resources/application.yml`, `api-gateway/src/main/java/com/autotest/api_gateway/GatewayExceptionHandler.java`
6. `api-gateway/.../ApiKeyFilter.java`, `common-lib/src/main/java/com/autotest/common_lib/exception/*` (오류 형식)
7. `frontend/src/App.tsx`(memberId 처리), `frontend/src/apiFetch.ts`
8. `test-management-service/.../presentation/controller/RemoteCrawlController.java` (실행기 채널 서명 인증), `TestExecutionInternalController.java` (내부 토큰)
9. `test-management-service/src/main/resources/db/migration/` (V1~V30, 기존 파일 수정 금지)
10. `README.md` — 2026-11-05 항목

---

## 10. 다음 LLM에게 전달할 작업 지시

> 이 문서를 운영 배포·허브 연동 작업의 인수인계로 사용하라.
>
> 먼저 `main`(또는 이어서 작업 중인 브랜치)과 실제 실행 중인 Docker 상태를 확인하라. 실행 중인 `autotest` 프로젝트는 사용자 승인 없이 재생성·중지하지 말라. 운영 전환(6.A)은 사용자가 승인했을 때만 백업부터 수행하라.
>
> 2·3단계(API 키, 허브 조회 API v1)는 완료됐다. 사용자 로그인은 사용자가 원하지 않으므로 다시 제안하지 말라. 허브는 조회와 웹 링크 이동까지만 한다. 허브에서 실행 시작·완료 알림은 사용자가 원하지 않으므로 제안하지 말라. 남은 일은 6.D와 6.E이며 TDD로 작게 진행하라. v1 계약(`docs/hub-api-v1.openapi.yaml`)은 필드 추가만 허용한다.
>
> 5장의 확정된 결정(exe 공용 키 금지·사용자별 API 키, Electron main 프로세스 호출, CORS 미개방, `/api/v1/hub` 분리, iframe 미지원)은 사용자의 새 지시 없이 바꾸지 말라.
>
> 검증은 격리 프로젝트(`-p autotest-prodverify`)와 격리 DB로 하고, 운영 DB와 볼륨은 건드리지 말라. `.env`·sample·계정·원문을 Git이나 외부 LLM에 올리지 말라.
>
> 검증하지 않은 것을 완료로 보고하지 말라. 변경 후 관련 문서와 README를 갱신하고, commit/push는 사용자가 요청한 범위에서 수행하라.
