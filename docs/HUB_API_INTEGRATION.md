# 업무 허브 연동·서버 배포 설계

2026-11-04 설계안, 2026-11-06 인증 방식 변경. 1~3단계(운영 배포 구성, API 키, 허브 조회 API v1)는 구현·검증했고, 4단계(실행 시작·알림)는 미구현이다. 단계별로 구현할 때 이 문서를 갱신한다. 진행 현황은 [인수인계](LLM_HANDOFF_2026-11-05.md)를 따른다.

## 1. 전제

| 항목 | 내용 |
| --- | --- |
| 서버 | 이 Windows 11 PC(GPU 없음, RAM 32GB), Docker Desktop으로 상시 실행 |
| 허브 | 별도 프로젝트(Electron). exe 설치 프로그램으로 사용자 PC마다 배포. 서버와 같은 PC에서는 실행하지 않음 |
| 네트워크 | 허브는 대부분 서버와 같은 사내망. 도메인(주소)은 서로 다름 |
| 인증 | 허브·autoTest 모두 사용자 인증 없음. 사용자 로그인은 만들지 않고 외부 API 호출만 API 키로 인증한다(2026-11-06) |
| 연결 방식 | 미정. 허브가 API만 호출하는 경우와 허브에서 autoTest 화면을 여는 경우를 모두 지원 |

## 2. 현재 구조의 배포 차단 문제

1. **사용자 식별을 위조할 수 있음.** 모든 API가 `X-Member-Id` 헤더로 사용자를 구분하는데, 이 값을 화면 입력칸(`frontend/src/App.tsx`)에서 그대로 받는다. 값을 바꾸면 다른 사람의 작업을 열 수 있다.
2. **내부 포트가 모두 공개됨.** `docker-compose.yml`이 db(5432, test/test), seaweedfs, 8081~8084, 8005, 8006을 호스트에 공개한다. 8082로 직접 접근하면 게이트웨이를 거치지 않고 `/api/internal/*`에 닿는다.
3. **frontend 배포 구성이 없음.** Vite 개발 서버로만 실행한다.

내부 API(`/api/internal/*`)는 이미 `X-Internal-Token`을 상수 시간 비교로 검사하며 ai-service·crawler도 토큰을 보낸다. 다만 포트가 공개되어 있으면 토큰 하나에만 의존하게 된다.

1단계 운영 구성으로 2·3을 해결했다. 절차는 [운영 배포](PRODUCTION_DEPLOYMENT.md)를 따른다. 1은 API 키로 들어오는 요청에 한해 해결했다(3.1). 웹 화면은 사내망 신뢰 모델을 유지한다.

## 3. 핵심 결정

### 3.1 인증 = 외부 프로그램용 API 키 (2026-11-06 결정)

사용자 로그인은 만들지 않는다. 인증이 필요한 이유는 허브 같은 외부 프로그램의 API 호출뿐이기 때문이다. 이전 설계(계정·로그인·JWT·refresh token)는 로컬 브랜치 `wip-login-auth`에 보관만 했다.

| 항목 | 결정 |
| --- | --- |
| 키 형식 | `atk_` + 32바이트 난수(base64url 43자). 추측·대입이 불가능한 길이라 별도 잠금·요청 제한을 두지 않는다 |
| 서버 저장 | `.env`의 `AUTOTEST_API_KEYS`에 **SHA-256 해시만** 둔다. `.env`가 유출돼도 키 원문은 나오지 않는다 |
| 발급 | `node scripts/new-api-key.mjs <이름> <회원ID>` → 키 원문(한 번만 표시)과 `.env` 항목 출력 |
| 단위 | 사용자(또는 PC)마다 한 개. **exe에 공용 키를 넣지 않는다.** 사용자가 허브 설정 화면에 자기 키를 입력한다 |
| 신원 | 키 하나가 회원 ID 하나에 묶인다. 게이트웨이가 `X-Member-Id`를 키의 회원으로 덮어쓰므로 허브는 다른 회원을 사칭할 수 없다 |
| 폐기·교체 | `.env`에서 항목을 지우거나 바꾸고 `docker compose ... up -d api-gateway`(수 초) |
| 전송 | `Authorization: Bearer <키>`. 사내망 HTTPS(Caddy)로만 보낸다 |
| 허브 쪽 보관 | Electron main 프로세스에서 `safeStorage`로 암호화해 저장. renderer·평문 파일에 두지 않는다 |

DB 테이블·관리 화면을 두지 않은 이유: 허브 사용자가 소수이고, 키 추가·폐기가 `.env` 한 줄 수정과 게이트웨이 재생성으로 끝난다. 사용자가 늘어 재시작 없는 폐기가 필요해지면 그때 DB로 옮긴다.

### 3.2 게이트웨이 규칙 (`api-gateway/.../ApiKeyFilter.java`)

1. 외부에서 들어온 `X-Internal-Token`, `X-Api-Client`는 **항상 삭제**한다.
2. `Authorization` 헤더가 **있으면** 반드시 등록된 키여야 한다. 아니면 `401 API_KEY_INVALID`(형식 오류·폐기 키·`Basic` 등 모두 동일).
3. 키가 맞으면 `X-Member-Id`를 키의 회원 ID로, `X-Api-Client`를 키 이름으로 설정하고 `Authorization`은 하위 서비스로 넘기지 않는다.
4. `/api/v1/hub/**`는 키가 필수다. 없으면 `401 API_KEY_REQUIRED`.
5. `Authorization`이 없는 그 밖의 요청(웹 화면, 원격 실행기 채널)은 기존과 같이 통과한다.
6. `AUTOTEST_API_KEYS` 형식이 틀리면 게이트웨이가 기동하지 않는다(조용히 무시하지 않음).
7. `/api/internal/**`은 게이트웨이에 라우팅하지 않고 Caddy도 404로 막는다(현재와 같음).

키 확인용: `GET /api/v1/hub/whoami` → `{client, memberId}`.

### 3.3 남는 위험 (알고 받아들인 것)

- **웹 화면은 여전히 인증이 없다.** 사내망에서 서버에 접속할 수 있는 사람은 화면이나 키 없는 `/api/**` 호출로 회원 ID를 바꿔 다른 사람의 작업을 볼 수 있다. 이 서버는 사내망 전용(방화벽 Private 프로필)으로만 운영한다.
- 즉 API 키가 보장하는 것은 "허브 요청이 누구 것인지"와 "허브 전용 API는 키 없이 못 쓴다"이다. 웹 쪽 데이터 보호는 아니다.
- 사내망 밖에 공개하거나 사용자별 접근 통제가 필요해지면 `wip-login-auth` 설계(로그인)를 다시 검토한다.

### 3.4 CORS

- 웹 화면은 같은 주소(Caddy)에서 서비스되므로 CORS가 필요 없다.
- **허브(Electron)는 autoTest API를 main 프로세스에서만 호출한다.** renderer는 IPC로 main에 요청한다.
  - main 프로세스의 Node `fetch`는 CORS 대상이 아니므로 서버에서 CORS를 열지 않는다.
  - 토큰이 renderer(화면 코드, XSS 노출 범위)에 들어가지 않는다.
  - Caddy 사내 CA는 main 프로세스의 HTTPS 클라이언트에 CA로 지정해 신뢰한다. `rejectUnauthorized=false`는 쓰지 않는다.
- renderer에서 직접 호출해야 하는 경우가 생기면 그때 `.env`의 `HUB_CORS_ORIGINS`로 허용 출처를 지정한다. `Origin: null`(file://)은 허용하지 않는다.

### 3.5 화면 연결

- 허브 API 응답의 작업·실행에 `webPath`(예: `/?member=3&workspace=<id>&page=reports`)를 포함한다. 허브는 자기가 아는 서버 주소 뒤에 붙여 기본 브라우저로 연다(서버가 공개 주소를 알 필요 없음).
- 웹 화면은 이 링크로 열리면 회원 ID를 맞추고 작업을 불러와 해당 단계로 이동한 뒤 주소창의 파라미터를 지운다.
- 웹 화면에는 로그인이 없으므로 주소만 열면 된다.
- iframe 삽입은 지원하지 않는다. 도메인이 달라 서드파티 쿠키가 차단된다.

## 4. 배포 구성

```text
사용자 PC들 (허브 exe, 브라우저)
        │ 사내망 HTTPS :443
        ▼
[이 PC — Docker Desktop]
  caddy :443  ── /        → frontend 정적 빌드
              └─ /api/**  → api-gateway:8080 (인증 경계)
  내부 네트워크 전용: auth · test-management · test-execution · report · ai · crawler · ollama · db · seaweedfs
```

- `docker-compose.prod.yml`(override 파일)을 새로 둔다. 기존 `docker-compose.yml` 개발 구성은 유지한다.
  - caddy만 `443`(필요하면 `80` → 443 리다이렉트)을 공개한다.
  - 나머지 서비스의 `ports`를 제거한다. 개발 중 확인이 필요한 포트만 `127.0.0.1:`로 묶는다.
  - db·S3 계정, `AUTOTEST_API_KEYS`, `PRODUCT_DESCRIPTION_INTERNAL_TOKEN`을 `.env`로 받는다. `test/test` 기본값은 운영에서 쓰지 않는다.
  - 모든 서비스에 `restart: unless-stopped`를 두고, `latest` 이미지 태그를 고정 버전으로 바꾼다.
- frontend는 다단계 빌드(`node:24` → 정적 파일)로 caddy 이미지에 포함한다.
- Caddy 설정:
  - 업로드 최대 크기는 100 MiB 이상으로 한다.
  - 장시간 요청의 읽기 시간 제한을 늘린다.
  - 사내망 HTTPS는 `tls internal`(Caddy 자체 CA)을 사용한다.
- **사내망 HTTPS:** API 키가 오가므로 HTTP로 운영하지 않는다. 허브 exe는 둘 중 하나를 한다.
  - (a) Caddy 루트 인증서를 함께 배포해 신뢰 저장소에 설치한다.
  - (b) 서버 인증서의 지문을 고정(pin)한다.
- **서버 주소 고정:** 공유기·DHCP에서 이 PC의 IP를 예약하고, 가능하면 사내 DNS 이름(예: `autotest.local`)을 붙인다. 허브는 설치할 때 또는 설정 화면에서 서버 주소를 입력받는다.
- **Windows 운영:**
  - 절전·자동 재부팅을 해제한다.
  - Docker Desktop을 로그인 시 자동 시작한다.
  - 방화벽은 443을 **개인(Private) 네트워크 프로필에서만** 허용한다.
  - DB를 정기 백업한다(기존 `backups/` 규칙).

### 4.1 설치 묶음 (서버 PC에 Docker만 필요)

목표: 서버 PC에는 Docker Desktop과 `.env`만 준비한다. JDK·Gradle·Node·Python은 설치하지 않는다. 전체 구성을 한 번에 실행한다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

| 현재 로컬 의존 | prod 정리 |
| --- | --- |
| test-management-service·api-gateway의 Dockerfile이 호스트 빌드 jar(`build/libs`)를 복사 | auth-service처럼 이미지 안에서 Gradle 빌드(다단계) |
| frontend를 호스트의 Vite 개발 서버로 실행 | `node:24` 단계에서 빌드한 뒤 정적 파일을 caddy 이미지에 포함 |
| ai-service·crawler-service 소스 bind mount, `uvicorn --reload` | prod override에서 mount를 제거하고 이미지에 복사한 소스를 사용. reload 끔 |
| `ollama pull` 수동 실행 | `ollama-init` 일회성 서비스가 모델이 없을 때만 받음(첫 실행 시 27B 약 17GB 다운로드) |
| `latest` 태그(seaweedfs, ollama) | 버전 고정 |
| 테스트·`scripts/` 검증 도구 | 개발 전용. prod에서 제외 |
| 원격 실행기(`remote-agent`) | 대상에 접근할 수 있는 다른 PC용이므로 서버 묶음에서 제외. 기존 `scripts/package-crawl-agent.ps1` zip 유지 |

**컨테이너 하나로 합치지 않는다.**
- 서비스마다 메모리 한도와 재시작이 독립되어야 한다. 32GB 중 27B 모델이 약 17GB를 쓴다.
- 서비스 하나만 갱신할 수 있어야 한다.
- manual-review의 내부망 격리를 유지해야 한다.

Compose 프로젝트 하나를 설치 단위로 삼는다. 다른 PC로 옮길 때는 `docker save`로 만든 이미지 tar와 compose 파일, `.env.example`을 묶어 오프라인 설치할 수 있다(후속).

메모리 배분(Docker 24GB 기준, 조정 대상):

| 서비스 | 한도 |
| --- | --- |
| ollama | 18GB |
| Java 서비스 각각 | `-Xmx512m` |
| crawler | 2GB |
| db | 1GB |
| 나머지 | 소량 |

manual-review(`qwen3:4b`)는 기존처럼 profile로 필요할 때만 실행한다. 27B와 동시에 실행하지 않는다.

### 4.2 자원 한계와 API 계약

- GPU 없이 `qwen3.8:27b`를 CPU로 실행하므로 시험 실행은 한 번에 한 건이고 오래 걸린다.
- 오래 걸리는 작업은 모두 비동기로 처리한다. 시작 요청에 `202`와 작업 ID를 돌려주고, 허브는 상태를 조회한다.
- 실행기가 사용 중이면 `409 EXECUTOR_BUSY`를 돌려준다. 허브는 자동 재시도하지 않고 사용자에게 안내한다.
- 허브의 상태 조회 간격은 최소 5초로 하고, 서버는 사용자별 요청 제한(`429`)을 둔다.

## 5. API

### 5.1 인증

```text
모든 허브 요청: Authorization: Bearer atk_...
GET /api/v1/hub/whoami   {client, memberId}   키 설정 확인(구현됨)
401 {code: API_KEY_REQUIRED | API_KEY_INVALID, message, traceId}
```

### 5.2 허브 v1 (`/api/v1/hub`, 조회만, 구현됨)

계약 전체: [hub-api-v1.openapi.yaml](hub-api-v1.openapi.yaml). 구현: `test-management-service/.../HubV1Controller.java`.

```text
GET /api/v1/hub/health                              {status, apiVersion, minHubVersion} (키 필요)
GET /api/v1/hub/workspaces?offset=&limit=           작업 목록 + webPath
GET /api/v1/hub/workspaces/{id}/summary             단계별 상태, TC 수, 최근 실행 P/F/미판정 건수
GET /api/v1/hub/workspaces/{id}/runs                실행 이력(최근 50건)
GET /api/v1/hub/workspaces/{id}/runs/{run}          실행 상태·진행률
GET /api/v1/hub/workspaces/{id}/runs/{run}/report   보고서 Markdown
```

- 데이터는 키에 묶인 회원 본인 것만 조회한다(기존 member 소유권 검사를 그대로 사용).
- 오류 형식은 기존 `{code, message, traceId}`를 그대로 쓴다.
- **버전 호환:** exe는 사용자 PC마다 업데이트 시점이 다르다.
  - `/api/v1`의 필드를 삭제하거나 의미를 바꾸지 않는다. 필드 추가만 허용한다.
  - 허브는 `X-Hub-Version: x.y.z` 헤더를 보낸다(`/health`·`/whoami` 제외 필수, 없으면 `400 HUB_VERSION_REQUIRED`). 최소 버전은 `.env`의 `HUB_MIN_VERSION`.
  - 서버가 `/health`의 `minHubVersion`보다 낮은 버전의 요청을 받으면 `426 HUB_UPDATE_REQUIRED`를 돌려준다.
- OpenAPI는 springdoc 자동 생성 대신 손으로 관리하는 계약 파일(`docs/hub-api-v1.openapi.yaml`)로 둔다. 자동 생성은 TMS의 웹·내부 API까지 노출하고, 계약이 코드 변경에 따라 조용히 바뀌기 때문이다.
- 응답은 웹용 실행 `snapshot`(대상 URL·TC 본문 등)을 그대로 내보내지 않고 고정된 요약 형태로 옮긴다.
- 요청 제한(`429`)은 아직 없다. 키 소유자가 사내 사용자뿐이라 후속으로 미뤘다.
- 기존 `/api/submissions/**` 등 웹 화면 API는 허브 계약에 포함하지 않는다. 내부 변경이 허브에 영향을 주지 않게 하기 위해서다.

### 5.3 후속 (v1.1+)

- `run` 권한: 허브에서 시험 재실행을 시작한다(`202` 반환, 실행 중이면 `409`).
- 허브 알림: 허브는 서버가 없어 웹훅을 받을 수 없다. 대신 `GET /api/v1/hub/events?since=` 폴링이나 SSE로 완료 알림을 받는다.

## 6. 구현 단계

| 단계 | 범위 | 완료 기준 |
| --- | --- | --- |
| 1 배포 구성·설치 묶음 (구현) | prod compose, 전 서비스 이미지 내 빌드, 내부 포트 비공개, caddy+frontend 빌드, ollama-init, 비밀값 `.env.prod.example` | JDK·Node 없이 compose 한 줄로 기동. 사내 다른 PC에서 `https://<서버>`로 화면 접속. 5432·8082 등 외부 접근 불가 |
| 2 API 키 (구현) | 게이트웨이 `ApiKeyFilter`, 발급 스크립트, `/api/v1/hub/whoami` | 키 요청은 `X-Member-Id` 위조 불가, 잘못된 키 401, `/api/v1/hub/**` 키 없이 401 |
| 3 허브 API v1 (구현) | `/api/v1/hub/*` 조회, OpenAPI 계약 파일, 버전 검사, 웹 딥링크 | 키로 작업 목록·요약·실행·보고서 조회, 다른 회원 404, 낮은 버전 426 |
| 4 후속 | 실행 시작, 이벤트 | 별도 계획 |

운영 DB 스키마를 바꾸기 전에는 기존 백업 규칙에 따라 백업한다(2단계는 DB 변경 없음).
