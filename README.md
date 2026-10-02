# autoTest - 문서 기반 AI 테스트케이스 자동 생성 및 결함 탐지 플랫폼

> 시험합의서, 기능리스트, 제품 매뉴얼을 접수·추출하고 ai-service에 전달합니다. 향후 제품 설명과 테스트케이스는 LLM으로 생성할 예정이며 Jev(TypeSafe)는 사용하지 않습니다.

현재 문서 업로드, 형식 검증, SeaweedFS 원본 저장·조회, 텍스트 추출, PostgreSQL 저장, AI 문서 접수·재시도까지 구현되어 있습니다. 이번 파이프라인은 LLM/Jev·TC 생성·테스트 실행을 호출하지 않습니다.

## 1. 프로젝트 폴더 구조

```text
autoTest/
├── ai-service/                    # FastAPI 문서 수신·중복 방지 API
├── sample/                        # 업로드 검증 기준 문서 3개 (원본 보존)
├── api-gateway/                   # Spring Cloud Gateway 라우팅
├── auth-service/                  # Spring Boot 인증 서비스 골격
├── common-lib/                    # Gradle 공통 모듈 (현재 Application 클래스)
├── crawler-service/               # Express + Playwright 요청 수집
├── db/                            # PostgreSQL 16 + pgvector 이미지 Dockerfile
├── frontend/                      # React + TypeScript + Vite 기본 화면
├── report-service/                # Spring Boot 리포트 서비스 골격
├── test-execution-service/        # Spring Boot 테스트 실행 서비스 골격
├── test-management-service/
│   ├── src/main/java/com/autotest/test_management_service/
│   │   ├── application/           # 제출 서비스, 파서 선택, 저장 포트
│   │   ├── domain/                # 제출·TC 모델, VO, 저장소 인터페이스
│   │   ├── infrastructure/        # 파서, JPA, 메모리 TC 저장소, S3 어댑터
│   │   └── presentation/          # SubmissionController
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/            # Flyway V1–V6
│   └── src/test/java/             # 도메인, 서비스, 파서, 저장소, 컨트롤러 테스트
├── gradle/wrapper/                # Gradle 8.10.2 Wrapper
├── gradlew / gradlew.bat
├── build.gradle.kts               # Spring Boot 3.5.6, Java 21 toolchain
├── settings.gradle.kts            # Java 모듈 6개 등록
├── docker-compose.yml            # 인프라와 백엔드 서비스 9개 (frontend 제외)
├── docker-compose.yml.bak        # 이전 설정 백업
├── setup.ps1                     # 과거 초기화 스크립트
├── Main.java                     # 기존 코드 제출 예제 잔재
├── .gitignore
├── .env                          # 로컬 환경변수, Git 제외
├── AGENT.md                      # 프로젝트 목표와 에이전트 작업 규칙
└── README.md
```

## 2. 개발 환경

- Java 21: 루트 Gradle toolchain 및 Java 서비스 Dockerfile 기준
- Python 3.13: `ai-service/Dockerfile` 기준
- Node.js `24.21.0` (저장소 루트 `.nvmrc` 기준)
- npm `11.19.0` (Node.js 설치에 포함된 버전)
- Docker Engine 27.4.0 및 Docker Compose 2.31.0 (Node 버전 변경과 무관)

macOS/Linux에서 저장소 Node 버전을 선택하려면 루트에서 실행합니다. nvm의 기본 버전을 바꾸면 새 셸도 같은 버전을 사용합니다.

```bash
nvm install
nvm use
nvm alias default 24.21.0
node -v  # v24.21.0
npm -v   # 11.19.0
```

npm은 Node.js 설치에 포함되므로 별도로 설치하거나 시스템 npm을 전역 업데이트하지 않습니다.

IDE는 자유롭게 선택할 수 있습니다. Java 서비스는 루트에서 모듈별 Gradle 명령으로 실행합니다.

## 3. 서비스 구성과 포트

| 구성 요소 | 포트 | 현재 역할 |
|---|---|---|
| frontend | 5173 (Vite 기본값) | 역할별 문서 업로드와 상태 조회 |
| api-gateway | 8080 | Java 서비스 라우팅 |
| auth-service | 8081 | 애플리케이션·DB 설정 골격, 인증 API 미구현 |
| test-management-service | 8082 | 역할별 업로드·파싱·저장·AI 전달 및 재시도 |
| test-execution-service | 8083 | 애플리케이션·DB 설정 골격, TC 실행 API 미구현 |
| report-service | 8084 | 애플리케이션·DB 설정 골격, 리포트 API 미구현 |
| ai-service | 8005 | 문서 접수·중복 방지 API (LLM 호출 없음) |
| crawler-service | 8006 | URL 방문 및 HTTP 요청 목록 수집 |
| PostgreSQL | 5432 | `autotest` DB, pgvector 설치 이미지 |
| SeaweedFS | 8333 / 8334 / 9333 | S3 API / S3 gRPC / Master |

Gateway는 서비스 탐색용 `lb://` 대신 다음 고정 HTTP 주소를 사용합니다.

| 경로 조건 | 대상 |
|---|---|
| `/api/auth/**` | `http://auth-service:8081` |
| `/api/tests/**`, `/api/submissions/**` | `http://test-management-service:8082` |
| `/api/executions/**` | `http://test-execution-service:8083` |
| `/api/reports/**` | `http://report-service:8084` |

경로 등록 자체가 해당 API의 구현을 의미하지는 않습니다. `/api/ai/**`, `/api/crawl/**` 라우트는 없으며 AI·크롤러는 직접 호출해야 합니다. `frontend/vite.config.ts`의 `/api` 요청은 `localhost:8080`으로 프록시됩니다.

## 4. 목표 파이프라인과 현재 구현

### 목표

```text
시험합의서 + 기능리스트 + 제품 매뉴얼 (PDF / Excel / HWP / Word)
  → 업로드 및 형식 검증 → SeaweedFS 원본 저장 → 텍스트 추출·DB 저장
  → 세 문서 정상 처리 시 ai-service 문서 접수 API로 전달·수신 확인
  → 이번 범위에서는 LLM/Jev 호출과 TC 생성·테스트 실행을 하지 않음
```

### 현재 제출 경로

`SubmissionController → SubmissionService → FileTypeResolver → 문서 파서 → 파일 저장 → submission_files 저장` 순서로 동작합니다. 역할은 `domain.submission.SubmissionType`, 파일 분류는 `domain.vo.SubmissionType`으로 별도 관리합니다.

- 첫 파일은 `POST /api/submissions`에 `file`, `productId`, `role`을 보내고, 같은 세트의 추가 파일은 `POST /api/submissions/{id}/files`에 `file`, `role`을 보냅니다.
- 역할은 `AGREEMENT`, `FUNCTION_LIST`, `MANUAL`이며, PDF/Excel/HWP/Word 확장자와 MIME을 모두 검증합니다.
- `GET /api/submissions/{id}`는 세트 상태와 함께 각 파일의 역할, 세부 형식, 경로, 추출 텍스트, 처리 상태를 반환합니다.
- 세 역할의 문서가 모두 정상 파싱되면 `http://ai-service:8005/api/v1/document-intakes`로 제출 ID, 제품 ID, 문서별 식별자·역할·파일명·형식·저장 경로·추출 텍스트를 전달합니다.
- 응답의 `aiDelivery`는 문서 업로드/파싱 상태와 별도이며 `NOT_READY`, `PENDING`, `FAILED`, `DELIVERED`를 반환합니다. 전달 실패는 원본과 추출 텍스트를 유지합니다.
- AI 전달 재시도: `POST /api/submissions/{id}/ai-delivery/retry`. 파일을 다시 업로드하지 않으며, 같은 제출 ID의 접수 재요청은 AI 서비스가 중복 처리하지 않습니다.
- 파싱 실패 문서 교체: `POST /api/submissions/{id}/files/{role}/replace` multipart `file`, 헤더 `X-Member-Id`. 소유자 및 `FAILED` 문서만 교체할 수 있습니다. AI 전달 시도가 시작된 제출, 전달 중 제출, 이미 전달된 제출의 교체는 409입니다. 새 문서 파싱이 실패하면 기존 문서·원본을 유지합니다.
- AI `PENDING`은 30초가 지나면 `GET /api/submissions/{id}`의 `aiDelivery.retryable`이 `true`가 됩니다. 화면에서 문서 세트를 다시 조회한 뒤 `전달 복구`를 누르면 재업로드 없이 재시도합니다. 30초 이내의 정상 진행 중 요청은 재시도되지 않습니다.
- 업로드 한도는 파일당 100 MiB이며, 프런트엔드에서 세 문서를 한 번에 선택해 순차 업로드하거나 역할별로 나누어 업로드할 수 있습니다.

### 실제 업로드 검증 기준

`sample/`의 원본 3개를 아래 역할로 사용합니다. 검증 전용 사본은 만들 수 있지만 원본은 수정·삭제하지 않습니다.

| 역할 | 파일 |
|---|---|
| `AGREEMENT` | `TTA-26-00872 시험합의서 v1.0.pdf` |
| `FUNCTION_LIST` | `2. PrintChaser 기능리스트_v0.5_20260304.xlsx` |
| `MANUAL` | `3. PrintChaser 관리자매뉴얼.pdf` |

## 5. 현재 API

| 서비스 | Method | Path | 요청 및 응답 |
|---|---|---|---|
| test-management-service :8082 | POST | `/api/submissions` | multipart `file`, `productId`, `role`, 헤더 `X-Member-Id` → submission 및 문서 결과 |
| test-management-service :8082 | POST | `/api/submissions/{id}/files` | multipart `file`, `role`, 헤더 `X-Member-Id` → 문서 결과 |
| test-management-service :8082 | GET | `/api/submissions/{id}` | submission 상태 및 역할별 문서·추출 결과 |
| test-management-service :8082 | POST | `/api/submissions/{id}/ai-delivery/retry` | 소유자 헤더 필요. `FAILED` 또는 만료된 `PENDING` 재시도 → 전달 상태 |
| test-management-service :8082 | POST | `/api/submissions/{id}/files/{role}/replace` | 실패 역할 문서 교체. `FAILED` 상태이고 AI 전달 시도 전일 때만 허용 |
| ai-service :8005 | GET | `/` | `{"status":"ai-service running","python":"3.13"}` |
| ai-service :8005 | POST | `/api/v1/document-intakes` | 문서 3개 검증·접수. 동일 본문 재접수는 영수증 반환, 다른 본문은 409 |
| crawler-service :8006 | GET | `/` | `{"status":"crawler-service running"}` |
| crawler-service :8006 | POST | `/crawl` | `{"url":"https://example.com"}` → `{"apis":[{"method":"GET","url":"..."}]}` |

AI `/generate`와 `/jev-test`는 이번 범위에 없으며 구현하지 않습니다. AI 문서 접수 API는 LLM을 호출하지 않습니다. 크롤러는 요청 목록을 최대 50개 반환하며, TC 실행·스크린샷·결함 판정은 구현하지 않았습니다.

AI 접수 요청 예시(비민감 합성 데이터, 실제 요청에는 전체 추출 텍스트 전달):

```json
{
  "submissionId": "11111111-1111-4111-8111-111111111111",
  "productId": 1,
  "documents": [
    {
      "fileId": "22222222-2222-4222-8222-222222222222",
      "role": "AGREEMENT",
      "originalFilename": "agreement.docx",
      "format": "DOCX",
      "storedPath": "s3://autotest-docs/agreement.docx",
      "extractedText": "Agreement body"
    },
    {
      "fileId": "33333333-3333-4333-8333-333333333333",
      "role": "FUNCTION_LIST",
      "originalFilename": "functions.xlsx",
      "format": "XLSX",
      "storedPath": "s3://autotest-docs/functions.xlsx",
      "extractedText": "Function body"
    },
    {
      "fileId": "44444444-4444-4444-8444-444444444444",
      "role": "MANUAL",
      "originalFilename": "manual.pdf",
      "format": "PDF",
      "storedPath": "s3://autotest-docs/manual.pdf",
      "extractedText": "Manual body"
    }
  ]
}
```

정상 응답은 `{"accepted":true,"duplicate":false,"submissionId":"...","documentCount":3,"receivedAt":"..."}`입니다. 같은 제출 ID와 같은 요청 본문은 `duplicate:true`로 같은 접수 결과를 반환합니다. 같은 제출 ID에 다른 요청 본문은 `409 Conflict`입니다. TMS는 응답의 `accepted`, 요청과 같은 `submissionId`, `documentCount: 3`을 모두 확인해야 `DELIVERED`로 기록합니다.

목표 문서 업로드 형식은 다음과 같습니다. **현재 이 요청이 성공하는 상태를 의미하지는 않습니다.**

```powershell
curl.exe -F "file=@시험합의서.pdf" -F "productId=1" -F "role=AGREEMENT" -H "X-Member-Id: 1" http://localhost:8082/api/submissions
```

## 6. 저장소와 데이터베이스

### 파일 저장

- AWS SDK v2 `S3Client`로 SeaweedFS S3 API에 접근합니다.
- `application.yml`의 버킷 이름은 **`autotest-docs`**입니다.
- S3 저장 경로: `s3://autotest-docs/{uuid}-{filename}`
- 버킷 조회가 404이면 버킷을 생성합니다.
- S3 SDK 예외가 발생하거나 `s3.enabled=false`이면 `/tmp/autotest-docs` 아래에 저장하고 `local:` 경로를 반환합니다. 따라서 업로드 성공만으로 S3 저장 성공을 판단할 수 없습니다.
- S3 루트 URL의 `AccessDenied` 응답만으로 실제 인증 업로드 성공 여부를 확인할 수는 없습니다. 저장 경로와 서비스 로그를 함께 확인해야 합니다.

### 제출 테이블

`submissions`는 제출 세트의 식별자와 세트 상태만 보관합니다. 문서 정보를 submissions에 중복 저장하지 않습니다.

```sql
submission_id   UUID PRIMARY KEY
member_id       BIGINT NOT NULL
product_id      BIGINT NOT NULL
status          VARCHAR(20) NOT NULL
uploaded_at     TIMESTAMPTZ NOT NULL
```

`submission_files`가 문서 정보의 유일한 기준입니다. 역할, 파일 분류 및 확장자 형식, 원본 파일명, MIME, 저장 경로, 추출 텍스트, 문서 파싱 상태와 실패 이유를 보관합니다. 한 세트 안에서 같은 역할은 한 번만 등록할 수 있습니다. 파싱 실패 파일만 실패 교체 경로로 교체할 수 있습니다.

세트 상태 규칙은 다음과 같습니다.

- 필수 역할 중 하나라도 누락됐고 실패가 없으면 `UPLOADED`입니다. 첫 문서만 `PARSED`여도 세트는 준비 완료가 아닙니다.
- 하나라도 문서 파싱이 실패하면 `FAILED`입니다. 실패 이유는 해당 `submission_files` 문서에서 응답합니다.
- `AGREEMENT`, `FUNCTION_LIST`, `MANUAL` 각 한 행이 모두 `PARSED`이고 추출 텍스트가 비어 있지 않을 때만 세트가 `PARSED`입니다.
- AI 전달 상태(`NOT_READY`, `PENDING`, `FAILED`, `DELIVERED`)는 문서 준비 상태와 별도로 `submission_ai_deliveries`에 저장합니다.

V5는 submissions의 legacy 파일 사본을 `submission_legacy_file_archive`에 보존한 뒤 `submission_type`, `stored_path`, `extracted_text`를 제거합니다. 문서 행이 없는 제출은 역할·형식·식별자를 추정하지 않고 legacy 값과 보존 사유만 archive에 남깁니다. V6는 기존 세트 실패 사유도 archive에 보존하고 세트 상태를 문서 행에서 재계산한 뒤 parent의 중복 `failure_reason`을 제거합니다. 기존 Flyway migration은 수정하지 않습니다. Hibernate는 `ddl-auto=validate`로 스키마만 확인합니다.

운영 스키마 변경 전 DB 백업을 만들고 archive 결과를 확인합니다. 복구가 필요하면 운영 DB를 직접 덮어쓰지 말고 별도 DB에 백업을 복원해 검증합니다. 현재 로컬 환경에서 확보한 custom-format 사전 백업은 `test-management-service/build/autotest-before-submission-files-v6.dump`에 있습니다.

```powershell
docker compose exec -T db createdb -U test autotest_restore_check
docker compose cp test-management-service/build/autotest-before-submission-files-v6.dump db:/tmp/autotest-before-submission-files-v6.dump
docker compose exec -T db pg_restore -U test -d autotest_restore_check /tmp/autotest-before-submission-files-v6.dump
```

복원 결과를 별도 DB에서 확인한 뒤 운영 복구 여부를 결정합니다. 위 명령은 `autotest`를 변경하지 않습니다.

`submission_ai_deliveries`에는 `NOT_READY`, `PENDING`, `FAILED`, `DELIVERED`, 시도 횟수, 실패 요약, `updated_at`, `delivered_at`이 기록됩니다. 각 전달 결과 반영은 해당 `attempt_count`가 아직 현재 시도와 일치할 때만 수행해 오래된 HTTP 요청이 새 시도를 덮지 못하게 합니다. AI 서비스는 `ai_document_intakes`에 제출 ID당 한 요청 본문만 저장합니다.

교체와 AI 전달 시작은 같은 제출 행 잠금으로 직렬화됩니다. 문서 준비 상태와 시도 번호를 DB에서 확정하고 트랜잭션을 종료한 뒤 AI HTTP를 호출하므로 네트워크 응답을 기다리며 DB 잠금을 유지하지 않습니다. 기존 원본 삭제와 DB 반영 실패 후 남은 새 원본은 `s3_cleanup_outbox`에 영속 예약하고 worker가 재시도합니다. 삭제 직전에 현재 문서 참조를 확인합니다.

`member_id`, `product_id`에 인덱스가 있습니다. 제출 및 문서 처리 상태는 `UPLOADED`, `PARSED`, `FAILED`입니다. `DRAFT`는 API 제출 완료 상태가 아니며 `VERIFIED`, `TC_GENERATED`, `EXECUTING`, `COMPLETED`는 현재 제출 스키마·enum에 없습니다.

TC는 `InMemoryTestCaseRepository`에 저장하므로 서비스 재시작 시 사라집니다. `test_cases` 테이블과 영속 저장 구현은 없습니다.

## 7. 환경변수 및 실행

### Docker Compose

루트 `.env`에 다음 값을 직접 설정합니다. `.env`는 `.gitignore`에 포함되어 있습니다.

```dotenv
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
```

현재 Compose가 필수로 참조하는 값은 위 두 개입니다. `S3_BUCKET`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 `.env`에 추가해도 현재 Compose 설정에 반영되지 않습니다. DB 계정은 개발용 `test/test`, DB 이름은 `autotest`로 고정되어 있습니다.

Compose는 `AI_SERVICE_URL=http://ai-service:8005`를 TMS에 전달하고 `AI_DATABASE_URL=postgresql://test:test@db:5432/autotest`로 AI 접수 DB를 설정합니다. LLM/Jev API 키는 이 범위에서 설정하거나 사용하지 않습니다.

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f test-management-service seaweedfs
```

Compose에는 서비스 정의 9개가 있으며 frontend는 포함되지 않습니다. crawler 컨테이너는 `node:24.21.0-slim`을 기반으로 빌드됩니다. 실행 성공 여부는 실제 컨테이너 상태와 로그로 확인해야 합니다.

### 개별 서비스 개발

다음 명령은 Windows PowerShell 기준이며 각 블록은 프로젝트 루트의 별도 터미널에서 시작합니다.

```powershell
# DB와 파일 저장소
docker compose up -d db seaweedfs

# Java 서비스: 호스트 실행용 DB 주소와 S3 키 지정
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/autotest'
$env:S3_ENDPOINT = 'http://localhost:8333'
$env:S3_ACCESS_KEY = 'your-access-key'
$env:S3_SECRET_KEY = 'your-secret-key'
.\gradlew.bat :test-management-service:bootRun
```

로컬 Gradle 실행은 `.env`를 자동으로 읽지 않으므로 환경변수를 직접 전달해야 합니다. Gateway 기본 대상 호스트는 Docker 서비스명입니다. Gateway를 호스트에서 실행하려면 라우트 URI를 로컬 주소로 재설정해야 합니다.

```powershell
# frontend
Set-Location frontend
npm install
npm run dev
```

```powershell
# ai-service
Set-Location ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn main:app --port 8005 --reload
```

```powershell
# crawler-service
Set-Location crawler-service
npm install
npx playwright install chromium
npm start
```

Windows에서 nvm-windows를 사용하는 경우 Node 버전을 `nvm install 24.21.0` 및 `nvm use 24.21.0`으로 선택한 뒤 위 명령을 실행합니다. macOS/Linux에서는 Java 서비스 명령을 `./gradlew :test-management-service:bootRun`으로 바꾸고 환경변수·경로 문법을 해당 셸에 맞게 사용합니다.

`setup.ps1`은 서비스 파일을 덮어쓰고 과거 MinIO 구성을 추가하는 초기화 스크립트입니다. 현재 환경 설치 절차로 사용하지 마세요.

## 8. 검증 방법

프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat :test-management-service:test --rerun-tasks
curl.exe http://localhost:8005/
curl.exe http://localhost:8006/
docker compose exec db psql -U test -d autotest -c "SELECT * FROM submissions ORDER BY uploaded_at DESC LIMIT 3;"
```

검증 명령:

```powershell
docker compose exec -T db createdb -U test autotest_test
$env:AUTOTEST_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5432/autotest_test'
.\gradlew.bat :test-management-service:test --tests "*SubmissionDatabaseIntegrationTest" --rerun-tasks
Remove-Item Env:AUTOTEST_TEST_DATABASE_URL
.\gradlew.bat :test-management-service:test --tests "*AiDocumentDeliveryServiceTest" --tests "*SubmissionServiceTest" --tests "*SubmissionTest" --tests "*SubmissionControllerTest"
python -m unittest discover -s ai-service -p test_main.py
npm --prefix frontend run build
```

격리 DB 테스트를 위해 `autotest_test`를 사용합니다. 이 DB가 이미 있으면 생성 명령은 건너뛰고 `AUTOTEST_TEST_DATABASE_URL`이 운영 `autotest`를 가리키지 않는지 확인합니다. integration 테스트는 자체 생성한 제출 행만 삭제합니다.

실제 업로드 검증은 앞의 `sample/` 파일 3개를 Gateway를 거쳐 업로드하고, 같은 제출 ID의 문서 상태·추출 내용·S3 참조·AI 접수 내역을 확인합니다. 모든 역할이 정상 파싱되어야 세트가 `PARSED`입니다.

### 검증 기록 (2026-10-02)

- V5/V6 적용 전 복구 가능한 custom-format 백업을 만들었습니다. V4 백업 복사본과 실제 DB 모두 migration 전 제출 8건, 문서 21건, AI 전달 7건, AI 접수 10건이었습니다.
- V6 적용 후 제출 8건, 문서 21건, AI 전달 7건, AI 접수 10건이 유지됐고 archive에 legacy 제출 8건이 보존됐습니다. 문서 행 누락 1건과 legacy 중복 사본 불일치 1건은 추정·삭제하지 않았습니다. submissions의 중복 파일 컬럼 4개는 제거됐고 archive와 대응하지 않는 제출은 0건입니다.
- `sample/` 실제 경유 검증 제출 ID: `3efe8b81-903f-49b5-a30c-7acfc56692d1`. 세 역할 AGREEMENT/PDF, FUNCTION_LIST/XLSX, MANUAL/PDF가 모두 `PARSED`, 원본 경로는 모두 S3, 세트 상태는 `PARSED`, AI 전달은 attempt 1에서 `DELIVERED`, 일치하는 AI 접수는 1건입니다. 원문과 저장 경로는 기록하지 않았습니다.
- 교체 실패/성공, XLSX→PDF 교차 형식, 동시 교체와 AI claim, 삭제 실패 후 재시도, 현재 원본 참조 보호, 재시작 뒤 durable cleanup 복구는 격리 PostgreSQL 테스트 및 별도 synthetic outbox 작업으로 확인했습니다.

## 9. 남은 구현 작업

- [x] PDF/Excel/HWP/Word만 허용하도록 업로드 타입·파서 경로 통합
- [x] 확장자·MIME 검증 및 오류 응답 처리
- [x] PDF, Excel, HWP/HWPX, Word DOC/DOCX 텍스트 추출
- [x] 버킷 설정을 목표인 `autotest-docs`와 일치시키기
- [x] 문서 3종 업로드·구분·frontend UI 및 역할별 파일 영속화
- [ ] LLM 문서 분석·TC 생성 API 연결 (Jev 미사용)
- [ ] TC 영속 저장 및 제출·TC 조회 API
- [ ] TC 실행 서비스와 Playwright 연동, 결함 판정
- [ ] 인증·인가, 결함 리포트 및 결과 화면
- [x] 제출 API frontend 프록시

## 10. 에이전트 작업 규칙

작업 전에 `AGENT.md`를 읽습니다. 프로젝트 목표는 문서 기반 TC 생성과 결함 탐지이며, 코드 제출·채점 방향으로 개발하지 않습니다. 기존 `@test` 파싱을 확대하거나 MinIO를 재도입하지 않습니다.

`AGENT.md`에는 작업 범위, 데이터 보존 및 검증 안전 규칙이 있습니다. 데이터 구조와 API 계약을 바꿀 때는 이 문서와 함께 갱신합니다.
