# autoTest - 문서 기반 AI 테스트케이스 자동 생성 및 결함 탐지 플랫폼

> 시험합의서, 기능리스트, 제품 매뉴얼을 분석해 JEV(TypeSafe) + LLM으로 제품 설명과 테스트케이스(TC)를 생성하고, Playwright 기반 테스트로 결함을 탐지하는 것을 목표로 하는 프로젝트입니다.

현재는 서비스 골격, 제출 API, 파일 저장 어댑터와 기초 크롤러가 구현되어 있습니다. 문서 분석부터 TC 생성·실행·리포트까지 이어지는 전체 파이프라인은 아직 구현되지 않았습니다. 아래 내용은 저장소의 소스 코드와 설정을 기준으로 작성했습니다.

## 1. 프로젝트 폴더 구조

```text
autoTest/
├── ai-service/                    # FastAPI 상태 확인 API, Python 의존성, Dockerfile
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
│   │   └── db/migration/V1__create_submissions.sql
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

`.gradle/`, `build/`, 서비스별 `bin/`, `node_modules/`, `__pycache__/` 등은 캐시·빌드·설치 산출물입니다. DB 마이그레이션 SQL은 루트 `db/`가 아니라 `test-management-service/src/main/resources/db/migration/`에 있습니다.

## 2. 개발 환경

- Java 21: 루트 Gradle toolchain 및 Java 서비스 Dockerfile 기준
- Gradle 8.10.2: 내장 Wrapper 사용
- Python 3.13: `ai-service/Dockerfile` 기준
- Node.js: 프런트엔드에 설치된 Vite의 요구 버전은 `^20.19.0 || >=22.12.0`; 크롤러 Docker 이미지는 `node:20-slim`
- npm, Docker + Docker Compose v2

IDE는 자유롭게 선택할 수 있습니다. Java 서비스는 루트에서 모듈별 Gradle 명령으로 실행합니다.

## 3. 서비스 구성과 포트

| 구성 요소 | 포트 | 현재 역할 |
|---|---|---|
| frontend | 5173 (Vite 기본값) | React 기본 예제 화면, 업로드 UI 미구현 |
| api-gateway | 8080 | Java 서비스 라우팅 |
| auth-service | 8081 | 애플리케이션·DB 설정 골격, 인증 API 미구현 |
| test-management-service | 8082 | 단일 파일 제출, 저장, 기존 파서 실행 |
| test-execution-service | 8083 | 애플리케이션·DB 설정 골격, TC 실행 API 미구현 |
| report-service | 8084 | 애플리케이션·DB 설정 골격, 리포트 API 미구현 |
| ai-service | 8005 | FastAPI 상태 확인 API |
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

경로 등록 자체가 해당 API의 구현을 의미하지는 않습니다. `/api/ai/**`, `/api/crawl/**` 라우트는 없으며 AI·크롤러는 직접 호출해야 합니다. `frontend/vite.config.ts`에는 `/api` 프록시가 아직 없습니다.

## 4. 목표 파이프라인과 현재 구현

### 목표

```text
시험합의서 + 기능리스트 + 제품 매뉴얼 (PDF / Excel / HWP / Word)
  → 업로드 및 형식 검증
  → 문서 텍스트 추출
  → ai-service: JEV(TypeSafe) + LLM 제품 설명·TC 생성
  → 생성 결과 저장
  → test-execution-service + crawler-service: 제품 URL 테스트
  → report-service: 결함 리포트
  → frontend: 결과 표시
```

### 현재 제출 경로

`SubmissionController → SubmissionService → FileTypeResolver → FileParserFactory → 파일 저장·기존 파싱 → 제출 DB 저장·TC 메모리 저장` 순서로 동작하도록 작성되어 있습니다.

- 업로드 API는 요청당 `file` 한 개를 받습니다. 문서 3종 구분·합본 처리는 없습니다.
- 실제 업로드 경로의 `domain.vo.SubmissionType`은 `JAVA`, `PYTHON`, `JAVASCRIPT`, `ZIP`, `UNKNOWN`입니다. PDF/Excel/HWP/Word 전용 정책은 아직 적용되지 않았습니다.
- `SimpleTextFileParser`, `ZipFileParser`, `AbstractTextTestCaseParser`에 기존 `@test input:` / `@test expected:` 파싱이 남아 있습니다. 이는 프로젝트 목표와 맞지 않아 교체해야 할 구현입니다.
- `PdfFileParser`, `ExcelFileParser`, `HwpFileParser`, `DocxFileParser`는 별도의 `domain.submission.FileParser` 인터페이스를 구현합니다. 현재 업로드 서비스가 사용하는 `application.port.FileParser`와 연결되어 있지 않습니다.
- 문서 파서는 `AbstractStubFileParser`를 통해 파일 크기만 읽고 빈 텍스트를 반환합니다. PDFBox·POI·HWP 텍스트 추출은 미구현입니다.
- 확장자와 MIME의 이중 검증, 금지 형식에 대한 일관된 400 응답 처리는 미구현입니다. `Content-Type`은 저장 시 전달할 뿐 검증하지 않습니다.

## 5. 현재 API

| 서비스 | Method | Path | 요청 및 응답 |
|---|---|---|---|
| test-management-service :8082 | POST | `/api/submissions` | multipart `file`, `productId`, 헤더 `X-Member-Id` → 200 `{"value":"제출 UUID"}` |
| ai-service :8005 | GET | `/` | `{"status":"ai-service running","python":"3.13"}` |
| crawler-service :8006 | GET | `/` | `{"status":"crawler-service running"}` |
| crawler-service :8006 | POST | `/crawl` | `{"url":"https://example.com"}` → `{"apis":[{"method":"GET","url":"..."}]}` |

제출 조회 및 TC 조회 GET API, AI `/generate`와 `/jev-test`는 아직 없습니다. 크롤러는 요청 목록을 최대 50개 반환하며, TC 실행·스크린샷·결함 판정은 구현하지 않았습니다. 제출 업로드의 파일·요청 크기 제한은 각각 10MB입니다.

목표 문서 업로드 형식은 다음과 같습니다. **현재 이 요청이 성공하는 상태를 의미하지는 않습니다.**

```powershell
curl.exe -F "file=@시험합의서.pdf" -F "productId=1" -H "X-Member-Id: 1" http://localhost:8082/api/submissions
```

## 6. 저장소와 데이터베이스

### 파일 저장

- AWS SDK v2 `S3Client`로 SeaweedFS S3 API에 접근합니다.
- 현재 `application.yml`의 버킷 이름은 **`autotest-submissions`**입니다. `AGENT.md`의 목표 이름인 `autotest-docs`로 전환하려면 설정 변경이 필요합니다.
- S3 저장 경로: `s3://autotest-submissions/{uuid}-{filename}`
- 버킷 조회가 404이면 버킷을 생성합니다.
- S3 SDK 예외가 발생하거나 `s3.enabled=false`이면 `/tmp/autotest-submissions` 아래에 저장하고 `local:` 경로를 반환합니다. 따라서 업로드 성공만으로 S3 저장 성공을 판단할 수 없습니다.
- S3 루트 URL의 `AccessDenied` 응답만으로 실제 인증 업로드 성공 여부를 확인할 수는 없습니다. 저장 경로와 서비스 로그를 함께 확인해야 합니다.

### 제출 테이블

`V1__create_submissions.sql` 및 `SubmissionEntity` 기준입니다.

```sql
submission_id   UUID PRIMARY KEY
member_id       BIGINT NOT NULL
product_id      BIGINT NOT NULL
submission_type VARCHAR(20) NOT NULL
stored_path     TEXT NOT NULL
extracted_text  TEXT NOT NULL
status          VARCHAR(20) NOT NULL
uploaded_at     TIMESTAMPTZ NOT NULL
```

`member_id`, `product_id`에 인덱스가 있습니다. 현재 상태 enum은 `DRAFT`, `UPLOADED`, `PARSED`, `VERIFIED`입니다. `created_at`, `TC_GENERATED`, `EXECUTING`, `COMPLETED`는 현재 스키마·enum에 없습니다.

TC는 `InMemoryTestCaseRepository`에 저장하므로 서비스 재시작 시 사라집니다. `test_cases` 테이블과 영속 저장 구현은 없습니다.

## 7. 환경변수 및 실행

### Docker Compose

루트 `.env`에 다음 값을 직접 설정합니다. `.env`는 `.gitignore`에 포함되어 있습니다.

```dotenv
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
```

현재 Compose가 필수로 참조하는 값은 위 두 개입니다. `S3_BUCKET`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 `.env`에 추가해도 현재 Compose 설정에 반영되지 않습니다. DB 계정은 개발용 `test/test`, DB 이름은 `autotest`로 고정되어 있습니다.

`TYPESAFE_API_KEY`는 현재 Compose에서 빈 값으로 지정되어 있고 AI 코드에서도 사용하지 않습니다. 향후 연동 시 환경변수 전달 설정과 실제 호출 구현이 필요합니다.

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f test-management-service seaweedfs
```

Compose에는 서비스 정의 9개가 있으며 frontend는 포함되지 않습니다. 실행 성공 여부는 실제 컨테이너 상태와 로그로 확인해야 합니다.

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

macOS/Linux에서는 Java 서비스 명령을 `./gradlew :test-management-service:bootRun`으로 바꾸고 환경변수·경로 문법을 해당 셸에 맞게 사용합니다.

`setup.ps1`은 서비스 파일을 덮어쓰고 과거 MinIO 구성을 추가하는 초기화 스크립트입니다. 현재 환경 설치 절차로 사용하지 마세요.

## 8. 검증 방법

프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat :test-management-service:test --rerun-tasks
curl.exe http://localhost:8005/
curl.exe http://localhost:8006/
docker compose exec db psql -U test -d autotest -c "SELECT * FROM submissions ORDER BY uploaded_at DESC LIMIT 3;"
```

현재 제출 서비스에는 6개 테스트 클래스, 15개의 `@Test` 메서드가 있습니다. 테스트에는 기존 소스코드 파싱 검증도 포함되어 있어, 문서 기반 목표 파이프라인의 검증을 의미하지는 않습니다. 이 README 수정 과정에서는 테스트·컨테이너 실행을 수행하지 않았으며, 기존의 “15 tests passed” 또는 “E2E 검증 완료” 주장을 확인된 사실로 기재하지 않습니다.

## 9. 남은 구현 작업

- [ ] PDF/Excel/HWP/Word만 허용하도록 업로드 타입·파서 경로 통합
- [ ] 확장자·MIME 검증 및 오류 응답 처리
- [ ] 기존 소스코드·ZIP·`@test` 파싱 제거
- [ ] PDF, Excel, HWP/HWPX, Word DOC/DOCX 텍스트 추출
- [ ] 버킷 설정을 목표인 `autotest-docs`와 일치시키기
- [ ] 문서 3종 업로드·구분·합본 처리와 frontend UI
- [ ] AI `/generate` API 및 JEV(TypeSafe) + LLM 연결
- [ ] TC 영속 저장 및 제출·TC 조회 API
- [ ] TC 실행 서비스와 Playwright 연동, 결함 판정
- [ ] 인증·인가, 결함 리포트 및 결과 화면
- [ ] AI·크롤러 Gateway 라우트 및 frontend API 프록시

## 10. 에이전트 작업 규칙

작업 전에 `AGENT.md`를 읽습니다. 프로젝트 목표는 문서 기반 TC 생성과 결함 탐지이며, 코드 제출·채점 방향으로 개발하지 않습니다. 기존 `@test` 파싱을 확대하거나 MinIO를 재도입하지 않습니다.

`AGENT.md`의 목표 정책과 현재 소스 코드 사이에는 위에 기록한 차이가 있습니다. 실제 구현 상태는 관련 코드·설정을 함께 확인해야 합니다. `AGENT.md` 자체의 수정 금지 지침에 따라 이번 README 정리에서는 해당 파일을 변경하지 않았습니다.
