# 운영 배포 (사내 서버, Docker 전용)

2026-11-04 1단계 구성. 허브 연동 전체 설계는 [HUB_API_INTEGRATION.md](HUB_API_INTEGRATION.md)를 따른다.

> **웹 화면에는 인증이 없다(2026-11-06 결정).** 이 구성은 내부 포트를 닫고 HTTPS 진입점 하나로 모은다. 웹의 사용자 구분은 화면의 member ID 입력값이다. 외부 프로그램(허브)만 API 키(`AUTOTEST_API_KEYS`)로 인증한다. 신뢰할 수 있는 사내망에서만 공개한다.

## 1. 구성

```text
사내 PC(브라우저·허브·원격 실행기) ──HTTPS 443──▶ caddy ─┬─ /        frontend 정적 빌드
                                                          ├─ /api/*   api-gateway:8080
                                                          └─ /downloads/autotest-server-ca.crt, autotest-crawl-agent.zip
내부 네트워크 전용: db · seaweedfs · auth · test-management · test-execution · report · ai · crawler · ollama
```

| 파일 | 역할 |
| --- | --- |
| `docker-compose.prod.yml` | 개발 구성 위에 겹치는 운영 override |
| `docker/java-service.Dockerfile` | Java 5개 서비스를 이미지 안에서 Gradle 빌드(호스트 JDK 불필요) |
| `docker/web.Dockerfile` | frontend 빌드 + 원격 실행기 ZIP + Caddy |
| `docker/Caddyfile` | HTTPS(`tls internal`), `/api` 프록시, `/api/internal/*` 차단 |
| `.env.prod.example` | 운영 `.env` 항목 |

### 개발 구성과의 차이

- **외부 공개 포트:** caddy의 80(→443 리다이렉트)·443만 공개한다. db 5432, seaweedfs, 8080~8084, 8005, 8006, ollama는 공개하지 않는다.
- **소스 mount:** ai-service·crawler의 소스 bind mount와 `--reload`를 제거한다. 소스 변경 시 이미지를 다시 빌드한다.
- **이미지 고정:** seaweedfs·ollama는 2026-11-04에 실행 중이던 이미지 digest로 고정한다. 빌드 이미지는 `:prod` 태그를 쓰므로 개발 이미지를 덮어쓰지 않는다.
- **재시작:** 모든 서비스가 `restart: unless-stopped`이다.
- **메모리 한도:**

  | 서비스 | 한도 |
  | --- | --- |
  | ollama | 20g |
  | crawler | 4g |
  | test-management | 3g |
  | ai | 2g |
  | db | 2g |
  | seaweedfs | 1g |
  | 나머지 Java 서비스 | 768m |

  Java 힙은 컨테이너 한도의 70%이다.
- **모델 자동 준비:** `ollama-init`이 `OLLAMA_MODELS`에 지정한 모델 중 없는 것만 받는다.
- **DB 비밀번호:** `.env`의 `DB_PASSWORD`를 쓴다. `test/test` 기본값은 운영에서 쓰지 않는다.
- **container_name:** 고정 이름을 쓰지 않는다. 프로젝트 이름 `autotest` 기준 기본 이름은 `autotest-db-1`, `autotest-ai-service-1`, `autotest-crawler-service-1`이다.

## 2. 서버 PC 준비 (최초 1회)

1. Docker Desktop을 설치하고, 로그인 시 자동 시작을 켠다. Resources → Memory는 24GB 이상으로 한다.
2. Windows 전원 설정에서 절전·최대 절전을 해제한다.
3. 공유기·DHCP에서 이 PC의 IP를 예약한다. 사내 DNS가 있으면 이름(예: `autotest.local`)을 등록한다.
4. 방화벽에서 443·80을 개인 네트워크에서만 허용한다(관리자 PowerShell).
   ```powershell
   New-NetFirewallRule -DisplayName "autoTest HTTPS" -Direction Inbound -Protocol TCP -LocalPort 443,80 -Profile Private -Action Allow
   ```
   네트워크 프로필이 "공용"이면 "개인"으로 바꾼다.
5. `.env.prod.example`을 `.env`로 복사하고 값을 채운다. 비밀값은 영문·숫자만 쓴다.

## 3. 실행

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f ollama-init   # 첫 실행 시 모델 다운로드 확인
```

접속: `https://<AUTOTEST_SITE_ADDRESS>/`

## 4. 기존 개발 서버에서 전환

현재 서버(`C:\Users\tta\autoTest`, 프로젝트 `autotest`)의 볼륨(db_data, seaweed-data, ollama_data, generation_cache, playwright_credentials)을 그대로 이어 쓴다. 운영 override가 `name: autotest`를 지정하므로 실행 폴더가 달라도 같은 볼륨을 쓴다.

1. **백업:** 기존 규칙(README)에 따라 DB를 백업한다. PowerShell에서 바이너리 dump를 `>`로 리다이렉트하지 않는다.
   ```powershell
   New-Item -ItemType Directory -Force backups | Out-Null
   docker compose exec -T db pg_dump -U test -d autotest -Fc -f /tmp/autotest-before-prod.dump
   docker compose exec -T db pg_restore --list /tmp/autotest-before-prod.dump
   docker compose cp db:/tmp/autotest-before-prod.dump backups/autotest-before-prod.dump
   ```
2. **DB 비밀번호 변경:** 기존 볼륨은 `POSTGRES_PASSWORD`를 바꿔도 비밀번호가 바뀌지 않는다. 전환 전에 `.env`의 `DB_PASSWORD`와 같은 값으로 변경한다.
   ```powershell
   docker compose exec -T db psql -U test -d autotest -c "ALTER USER test PASSWORD '<DB_PASSWORD 값>'"
   ```
   이후 개발 구성(`docker compose up`)으로 다시 실행하려면 개발 compose의 `test` 비밀번호와 맞지 않으므로 같은 방법으로 되돌린다.
3. **전환:** 기존 `.env`에 `AUTOTEST_SITE_ADDRESS`, `DB_PASSWORD`, `OLLAMA_MODELS`를 추가하고 3장의 명령을 실행한다. compose가 개발 컨테이너를 운영 컨테이너로 교체한다.
4. **원격 실행기:** 기존 실행기 설정의 서버 주소 `http://<IP>:8080`은 더 이상 열려 있지 않다. 실행기를 `https://<서버 주소>`로 다시 등록하고 5장의 인증서를 설치한다.

## 5. 사내 인증서(HTTPS) 신뢰

Caddy가 사내 전용 CA를 만들어 서버 인증서를 발급·자동 갱신한다. 루트 인증서는 caddy_data 볼륨에 보존되며 공개 정보다.

- **받기:** `https://<서버>/downloads/autotest-server-ca.crt`. 처음에는 브라우저 경고가 표시되며, 받은 뒤에는 표시되지 않는다.
  CLI로 받으려면 다음 명령을 쓴다.
  ```powershell
  docker compose -f docker-compose.yml -f docker-compose.prod.yml cp caddy:/data/caddy/pki/authorities/local/root.crt .\autotest-server-ca.crt
  ```
- **사용자 PC(브라우저):** 인증서를 더블클릭하고 "신뢰할 수 있는 루트 인증 기관"에 설치한다. 관리자 PowerShell에서는 다음 명령을 쓴다.
  ```powershell
  Import-Certificate -FilePath .\autotest-server-ca.crt -CertStoreLocation Cert:\LocalMachine\Root
  ```
- **원격 실행기:** 다음 중 하나를 한다. `Start-Agent.ps1`은 `--use-system-ca`로 실행하며, `server-ca.crt`가 있으면 `NODE_EXTRA_CA_CERTS`로 지정한다.
  - Windows 신뢰 저장소에 설치한다.
  - 실행기 폴더(package.json 옆)에 `server-ca.crt`로 둔다.
- **허브(Electron):** main 프로세스의 HTTPS 클라이언트에 이 인증서를 CA로 지정한다. 인증서 검증을 끄지 않는다.
- caddy_data 볼륨을 지우면 CA가 새로 만들어진다. 그 경우 모든 PC에 인증서를 다시 배포해야 하므로 지우지 않는다.

## 6. 갱신·운영

- **소스 갱신:** `git pull`한 뒤 3장의 `up -d --build`를 실행한다. 바뀐 이미지만 다시 만든다.
- **모델 추가:** `.env`의 `OLLAMA_MODELS`를 수정한 뒤 `... up -d ollama-init`을 실행한다.
- **manual-review:** 필요할 때만 `--profile manual-review`를 붙인다. 27B와 동시에 실행하지 않는다.
- **디버깅용 포트:** 운영 파일을 수정하지 말고 `docker compose ... exec <서비스> ...`로 접근한다.
- **로그:** `docker compose ... logs --since 1h <서비스>`

## 7. 검증 기록 (2026-11-04)

실행 중인 서버에 영향을 주지 않도록 별도 프로젝트(`-p autotest-prodverify`)와 새 볼륨, 포트 18443/18080, 모델 다운로드 생략(`OLLAMA_MODELS=`)으로 검증했다. 결과는 아래 표와 같다.

| 항목 | 결과 |
| --- | --- |
| 이미지 빌드(Java 5, web, ai, crawler, db) | 호스트 JDK·Node 없이 모두 성공 |
| 기동 | 전 서비스 시작, 새 DB에서 Flyway 마이그레이션 후 TMS healthy |
| 외부 공개 포트 | caddy(18443/18080)만 공개. 그 외 서비스 0개 |
| HTTPS | 받은 루트 CA로 검증 성공. CA 없이는 거절(curl, Node 모두). Node+CA(Electron·실행기 조건) 200 |
| HTTP | 308로 HTTPS 리다이렉트 |
| 화면 | 정적 자산 200, SPA 경로 fallback, Chromium 렌더링 콘솔 오류 0, "실행기 사용 가능" 표시(게이트웨이→TMS→crawler) |
| 캐시 헤더 | index는 `no-cache`, `/assets/*`는 `immutable` |
| API | `/api/submissions/workspaces`, `/api/crawl-agents/local/status` 200 |
| 문서 업로드 | xlsx 업로드 → 파싱 PARSED → `s3://autotest-docs` 저장. 90MB 업로드가 edge를 통과(200) |
| 크기 초과 | 120MB·150MB는 TMS가 413 `FILE_TOO_LARGE`로 거절(HTTP/2 경유 시 클라이언트가 413을 받음) |
| `/api/internal/*` | edge에서 404 |
| `/actuator/*` | 공개되지 않음(SPA index 반환) |
| 소스 mount | ai-service·crawler 모두 bind mount 0개 |
| Java 실행 사용자 | uid 10001(root 아님) |
| 다운로드 | CA 인증서, 원격 실행기 ZIP(`Start-Agent.ps1`의 `--use-system-ca` 포함), 공통 규칙 md 200 |
| ollama-init | 모델이 없으면 받고, 다시 실행하면 "model ready"로 바로 종료 |

**확인된 제약**
- **HTTP/1.1 크기 초과 업로드:** 100 MiB를 넘는 파일을 HTTP/1.1로 올리면, 서버가 본문을 다 받기 전에 413을 보내고 연결을 닫는다. 이때 클라이언트는 413 대신 연결 오류를 받을 수 있다. 브라우저는 HTTP/2로 연결되어 413을 정상적으로 받는다. API 클라이언트(허브 등)는 업로드 전에 파일 크기를 검사한다.
- **27B 모델 추론:** 검증에서는 모델 다운로드를 생략했으므로 실제 추론을 확인하지 않았다. 운영 전환 시에는 기존 ollama_data 볼륨을 이어 쓴다.
- **사용자 PC 접속:** 사내 다른 PC에서의 접속은 서버 PC의 방화벽·IP 설정 후 확인해야 한다(이 PC 안에서만 검증).
- **인증:** 웹 화면은 없다. 허브 등 외부 프로그램은 API 키로 인증한다(`docs/HUB_API_INTEGRATION.md` 3장). 키 발급: `node scripts/new-api-key.mjs <이름> <회원ID>` → `.env`의 `AUTOTEST_API_KEYS`에 추가 → `up -d api-gateway`.

## 8. 이 PC(DESKTOP-JA2IHD6) 로컬 운영 구성 기록 (2026-11-07)

실제 운영 서버는 다른 PC다. 이 PC 는 사내망이 아니라서, **지금 환경 기준(localhost)** 으로 운영 구성을 올려 ATDT 연동을 확인했다. 사내망에 올릴 때는 아래 "바꿀 것" 만 바꾼다.

| 단계 | 한 일 | 결과 |
|---|---|---|
| 백업 | `.env` → `.env.before-prod-20261107`(git 제외: `.git/info/exclude`), DB `pg_dump -Fc` → `backups/autotest-before-prod-20261107.dump` | 표 18개, 788KB |
| `.env` 추가 | `AUTOTEST_SITE_ADDRESS=localhost`, `AUTOTEST_HTTP_PORT=18080`, `AUTOTEST_HTTPS_PORT=18443`(이 PC 는 80 을 Windows 가 이미 씀), `DB_USER=test`, `DB_PASSWORD`(무작위 32자, 출력하지 않음), `OLLAMA_MODELS=`(빈칸: 이 PC 는 Docker 메모리 8GB 라 모델을 받지 않음), `EXECUTION_QWEN_MODEL=qwen3:4b`, `AUTOTEST_API_KEYS=`(빈칸), `HUB_MIN_VERSION=0.0.0` | |
| DB 비밀번호 | `ALTER USER test PASSWORD …` (값은 환경 변수로만 넘김) | |
| 실행 | `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build` | 1회차 Gradle 빌드 오류, 2회차 성공. 11개 서비스 실행 |
| 확인 | `https://localhost:18443/` 200, `/api/v1/hub/health`(키 없음) 401 `API_KEY_REQUIRED`, `/api/internal/*` 404, `/downloads/autotest-server-ca.crt` 200, CA 없이 접속하면 거절, 5432·8080·8082·8005·8006·9333 닫힘, 기존 데이터 유지(submissions 5건) | |
| CA | `backups/autotest-server-ca.crt` 로 꺼냄 | |

알게 된 것:
- Windows `curl`(Schannel) 은 사내 CA 에 폐기 목록이 없어 `CERT_TRUST_REVOCATION_STATUS_UNKNOWN` 으로 거절한다. 확인할 때 `--ssl-no-revoke --cacert …` 를 쓴다(체인 검증은 함).
- `http://localhost:18080` 은 응답하지 않았다(HTTPS 로만 접속). 사내망 구성에서 80 을 쓰면 다시 확인.
- 이 PC 는 소켓 listen·connect 가 가끔 `EFAULT`(WSAEFAULT) 로 실패한다. Docker 포트 열기에서도 났고 다시 하면 된다. ATDT 테스트의 `tests/net/efault.ts` 와 같은 원인.
- ATDT 의 "시험 웹앱" 바로가기는 키 없이 `/api/v1/hub/health` 로 켜짐을 확인한다(401 `API_KEY_…` = 켜짐). ATDT 는 Windows 인증서 저장소를 쓰므로 `autotest-server-ca.crt` 를 **"신뢰할 수 있는 루트 인증 기관"** 에 설치해야 HTTPS 상태 확인과 브라우저 열기가 경고 없이 된다(5장 명령).
- 허브 API 키(`AUTOTEST_API_KEYS`)는 ATDT 가 더 쓰지 않는다(2026-11-07 결정: ATDT 는 autoTest 를 바로가기로만 연동).

사내망으로 옮길 때 바꿀 것: `AUTOTEST_SITE_ADDRESS`(서버 IP 또는 이름, 여러 개면 쉼표), `AUTOTEST_HTTP_PORT`·`AUTOTEST_HTTPS_PORT`(기본 80·443 이면 줄 삭제), `OLLAMA_MODELS`·`EXECUTION_QWEN_MODEL`(실제 서버 모델), 2장 4번 방화벽. 주소가 바뀌면 Caddy 가 새 서버 인증서를 만들지만 CA 는 같다(`caddy_data` 볼륨 유지 시).

되돌리기(개발 구성으로): `docker compose -f docker-compose.yml -f docker-compose.prod.yml down` → `.env.before-prod-20261107` 를 `.env` 로 → DB 비밀번호를 `test` 로 `ALTER USER` → `docker compose up -d`.

### 8-1. 2026-11-07 오전 점검·조치

- 증상: 누군가 `-f` 없이 `docker compose up -d`(개발 구성)를 실행해 ai-service·api-gateway 등이 2주 전 개발 이미지(`:latest`)로 바뀌고, DB 비밀번호(개발 `test` ↔ 운영 무작위)가 맞지 않아 auth·test-management·test-execution·report 가 `password authentication failed` 로 죽음. 개발 게이트웨이에는 허브 API 가 없어 `/api/v1/hub/health` 404.
- 조치: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` 로 복구(빌드 없음). 11개 서비스 Up, 웹 200·health 401·internal 404, 내부 포트 닫힘, 데이터 유지(submissions 5).
- 재발 방지: `.env` 에 `COMPOSE_PATH_SEPARATOR=;`, `COMPOSE_FILE=docker-compose.yml;docker-compose.prod.yml` 추가(바꾸기 전 `.env.before-composefile-20261107`, git 제외). 이제 이 폴더에서 `-f` 없이 `docker compose up -d`·`ps`·`logs` 를 써도 운영 구성. `docker compose up -d` 를 직접 실행해 컨테이너가 그대로인 것을 확인함.
- 자동 시작: Docker Desktop 은 Windows 로그인 때 시작(HKCU Run), 컨테이너는 모두 `restart: unless-stopped`.
- 개발 구성으로 돌아가려면: `.env` 의 두 줄을 지우고 8장 "되돌리기" 순서.

### 8-2. 2026-11-08 운영 결함 2건 수정

모델 실행을 뺀 전 단계 점검(가짜 업무 사이트로 01~08)에서 찾은 운영 결함이다.

1. **로그인 정보 저장 실패(`PLAYWRIGHT_CREDENTIAL_STORAGE_UNAVAILABLE`)**
   - 원인: 개발 구성(root 실행)이 먼저 만든 `playwright_credentials` 볼륨이 root 소유인데, 운영 Java 이미지는 uid 10001(`app`)로 실행되어 키 파일을 만들지 못했다. 로그인이 필요한 화면 수집이 전부 막힌다.
   - 수정: `docker/java-service.Dockerfile` 이 `/app/.runtime/playwright` 를 `app` 소유로 미리 만든다(새 볼륨은 이 소유자를 물려받음). 이미 있는 볼륨은 `docker-compose.prod.yml` 의 일회성 `test-management-service-volume-init`(root, CHOWN 권한만, 네트워크 없음)이 시작할 때마다 소유자를 맞추고 끝나며, TMS 는 이 작업이 성공한 뒤 시작한다.
   - 실패 시 TMS 로그에 `Credential key storage unavailable at <경로>: <원인 종류>` 가 남는다(키·비밀번호 값은 남기지 않음).
2. **매뉴얼 검토 서비스가 없을 때 작업이 `PAUSING` 에 영구 정지**
   - 원인: 서비스 연결이 안 되면 중단 확인(`/close`)을 받을 수 없어 `STOP_NOT_CONFIRMED` 로 남았고, DB 의 "실행 중 작업 1개" 제약(`manual_review_one_active`, 전체 공용) 때문에 **모든 사용자·작업의 매뉴얼 검토가 `MANUAL_REVIEW_BUSY` 로 막혔다.** TMS 재시작으로도 풀리지 않았다.
   - 수정(`ManualReviewService`):
     - 시작·이어하기는 입력 검증 뒤 `/health` 를 확인하고, 서비스가 없으면 `503 MANUAL_REVIEW_UNAVAILABLE` 로 거절하며 작업을 만들지 않는다.
     - 중단 요청에서 **연결 거부·주소 없음**은 "그쪽에서 처리 중인 작업 없음"으로 보고 정리한다. 응답 지연(시간 초과)은 처리 중일 수 있으므로 기다린다.
     - 1분마다(그리고 시작·이어하기 직전에) `PAUSING` 작업에 중단을 다시 요청한다. 확인되면 `INTERRUPTED`(`STOP_CONFIRMED_LATE`), 서비스가 없으면 `INTERRUPTED`(`SERVICE_UNAVAILABLE`), 15분이 지나도 확인이 안 되면 `INTERRUPTED`(`STOP_TIMEOUT`). 모두 저장된 페이지부터 이어서 검토할 수 있다. 늦게 도착한 결과는 기존 attempt 비교로 버려진다.
   - 테스트: `ManualReviewUnavailableIntegrationTest`(4건), `ManualReviewStopTimeoutIntegrationTest`(1건).

3. **(1번에서 이어진 문제) 넘기기 실패 시 수집 작업이 `QUEUED` 로 10분간 남음**
   - 원인: 작업 행을 저장한 뒤 별도 트랜잭션에서 실행기로 넘기는데(저장된 비밀번호 복호화 포함), 이 단계가 실패하면 작업이 `QUEUED` 로 남아 10분 동안 다음 연결·로그인·수집 요청이 `CRAWL_ALREADY_ACTIVE`(409) 로 막혔다.
   - 수정(`RemoteCrawlService`): 넘기기가 실패하면 그 작업을 바로 `FAILED`(원인 코드, 비밀번호 사본 삭제)로 바꾸고 오류를 돌려준다. 테스트: `PlaywrightSetupIntegrationTest.localDispatchFailureFailsTheJobImmediatelyInsteadOfLeavingItQueued`(수정 전 코드에서 실패 확인).

검증(이 PC): 볼륨을 root 소유로 되돌려 결함을 재현(로그인 확인 503)한 뒤 새 이미지 배포 → 초기화 작업 성공(종료 코드 0), 기존 키 유지, 로그인 확인 `loginVerified=true`. 매뉴얼 검토 서비스가 없는 상태에서 시작 → 2초 안에 `503 MANUAL_REVIEW_UNAVAILABLE`, 남는 작업 없음. 자동 테스트: Java 179건 중 176 통과(나머지 2건은 기존 문제, 1건 건너뜀).

이 PC 참고: Docker 메모리가 7.8GB 라 매뉴얼 검토 이미지(OCR 모델 준비) 빌드 중 Docker 가 재시작했다. 그래서 이 PC 에서는 매뉴얼 검토 서비스를 띄우지 않는다(`COMPOSE_PROFILES` 미설정). 전체 자동 테스트는 `node scripts/run-all-tests.mjs`.
