# autoTest - 문서 기반 AI 테스트케이스 자동 생성 및 결함 탐지 플랫폼

> 시험합의서, 기능리스트, 제품 매뉴얼을 접수·추출하고 ai-service에 전달합니다. LLM 실행 전에 프로그램 규칙과 Jev(TypeSafe)로 문서 적합성을 사전 점검하며, 다음 단계에서 통과한 문서로 제품 설명과 테스트케이스를 생성합니다.

현재 문서 업로드, 형식 검증, SeaweedFS 원본 저장·조회, 텍스트 추출, PostgreSQL 저장, AI 문서 접수·재시도, LLM 전 문서 사전 점검까지 구현되어 있습니다. 제품 설명·TC 생성과 테스트 실행은 아직 구현되지 않았습니다.

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
├── docker-compose.yml            # 인프라와 백엔드 서비스 10개 (frontend 제외)
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
| ai-service | 8005 | 문서 접수·사전 점검·제품 설명 PDF 생성 |
| Ollama 컨테이너 | `21434:11434` | 로컬 LLM API (`qwen3:4b` 등) |
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
  → [다음 단계] LLM 전 문서 필터: 프로그램 품질 검사 + Jev 의미 판정
  → BLOCKED는 생성 중단, READY 또는 READY_WITH_WARNINGS는 LLM에 전달
  → [향후] 제품 설명·TC 생성 → TC 실행 및 결함 판정
```

### 현재 제출 경로

`SubmissionController → SubmissionService → FileTypeResolver → 문서 파서 → 파일 저장 → submission_files 저장` 순서로 동작합니다. 역할은 `domain.submission.SubmissionType`, 파일 분류는 `domain.vo.SubmissionType`으로 별도 관리합니다.

- 첫 파일은 `POST /api/submissions`에 `file`, `productId`, `role`을 보내고, 같은 세트의 추가 파일은 `POST /api/submissions/{id}/files`에 `file`, `role`을 보냅니다.
- 역할은 `AGREEMENT`, `FUNCTION_LIST`, `MANUAL`이며, PDF/Excel/HWP/Word 확장자와 MIME을 모두 검증합니다.
- `GET /api/submissions/{id}`는 세트 상태와 함께 각 파일의 역할, 세부 형식, 경로, 추출 텍스트, 처리 상태를 반환합니다.
- 세 역할의 문서가 모두 정상 파싱되면 `http://ai-service:8005/api/v1/document-intakes`로 제출 ID, 제품 ID, 문서별 식별자·역할·파일명·형식·저장 경로·추출 텍스트를 전달합니다.
- 응답의 `aiDelivery`는 문서 업로드/파싱 상태와 별도이며 `NOT_READY`, `PENDING`, `FAILED`, `DELIVERED`, `BLOCKED`를 반환합니다. 전달 실패(`FAILED`)와 사전 점검 차단(`BLOCKED`)은 구별되며 둘 다 원본과 추출 텍스트를 유지합니다.
- AI 전달 재시도: `POST /api/submissions/{id}/ai-delivery/retry`. 파일을 다시 업로드하지 않으며, 같은 제출 ID의 접수 재요청은 AI 서비스가 중복 처리하지 않습니다.
- 문서 교체: `POST /api/submissions/{id}/files/{role}/replace` multipart `file`, 헤더 `X-Member-Id`. 소유자만 호출할 수 있고 `FAILED` 파싱 문서 또는 AI 전달 상태가 `BLOCKED`인 제출의 문서를 교체합니다. 전달 상태가 `PENDING`·`DELIVERED`이거나 통신 실패(`FAILED`)로 전달이 시도된 제출은 409입니다. `BLOCKED` 제출은 교체가 성공하면 전달 상태가 `NOT_READY`로 돌아가고 같은 요청에서 다시 사전 점검합니다. 교체 파일 파싱이 실패하면 기존 문서·원본과 `BLOCKED` 상태를 유지하며, `BLOCKED`/`NOT_READY`(시도 이력 포함) 상태에서는 다시 교체할 수 있습니다.
- AI `PENDING`은 45초(전달 lease)가 지나면 `GET /api/submissions/{id}`의 `aiDelivery.retryable`이 `true`가 됩니다. 화면에서 문서 세트를 다시 조회한 뒤 `전달 복구`를 누르면 재업로드 없이 재시도합니다. 45초 이내의 정상 진행 중 요청은 재시도되지 않습니다. `BLOCKED`는 자동 재시도 대상이 아니며 문서 교체로만 다시 점검됩니다.
- 업로드 한도는 파일당 100 MiB이며, 프런트엔드에서 세 문서를 한 번에 선택해 순차 업로드하거나 역할별로 나누어 업로드할 수 있습니다.

### LLM 전 문서 필터

필터는 “문서가 완벽한가”가 아니라 “LLM이 유용하게 작업할 수 있는가”를 판정합니다. 명확한 입력 오류는 프로그램 규칙으로 확인하고, 문서 역할 및 제품 일치 여부처럼 의미가 필요한 판단은 Jev(TypeSafe)에 맡깁니다.

판정 결과는 다음 세 가지입니다.

| 결과 | 판정 기준 | 처리 |
|---|---|---|
| `BLOCKED` | 필수 역할 문서 누락, 파싱 실패·빈 텍스트, 한 문서가 사실상 읽을 수 없을 정도로 손상, 역할과 명백히 다른 문서, 또는 세 문서가 서로 다른 제품이라는 근거가 높음 | LLM 생성을 중단하고 원인과 해당 문서를 사용자에게 표시 |
| `READY_WITH_WARNINGS` | 일부 문자 손상, 시험코드 미발견, 제한된 페이지·시트의 추출 부족, 제품명·모델명이 없거나 별칭·버전 차이로 동일성 판단이 불확실함 | 경고를 전달하고 LLM 생성 진행 |
| `READY` | 세 문서 모두 역할에 맞는 내용을 충분히 담고 제품 정보가 대체로 일치함 | 경고 없이 LLM 생성 진행 |

시험코드가 없거나 제품명을 확인할 수 없다는 이유만으로 차단하지 않습니다. 제품이 다르다는 증거가 명확할 때만 차단하고, 표기 차이·버전 차이·불확실한 일치는 경고로 처리합니다.

현재 초기 구현은 문서별 의미 문자 수와 대체·제어 문자 비율을 검사합니다. 의미 문자가 12개 미만이거나 의심 문자가 10% 이상이면 차단하고, 의미 문자가 80개 미만이거나 의심 문자가 1% 이상이면 경고합니다. 이 값은 초기값이며 `sample/` 실제 문서와 경미·심각한 손상 사본으로 조정해야 합니다. 파서는 페이지·시트별 추출 품질을 제공하지 않아 현재 위치별 검사는 하지 않습니다. 현재 PDF 파서는 텍스트 추출만 하며 OCR을 수행하지 않으므로, `PARSED` 상태만으로 추출 품질을 보장하지 않습니다.

경고는 문서의 `extractedText`와 분리된 `preflight` 메타데이터로 반환·저장하며 화면에 표시합니다. 각 경고에는 코드, 역할, 심각도, 짧은 설명이 포함됩니다. 현재 파서가 페이지·시트 위치를 제공하지 않아 위치 정보는 포함하지 않습니다. 향후 생성 경로에는 경고를 근거로 누락 정보를 지어내지 말고, 확인할 수 없는 값은 미확인으로 표시하도록 전달해야 합니다.

AI 문서 접수 API는 `preflight`에 판정·경고·진단을 포함해 반환하고, TMS가 이를 저장해 제출 조회 응답과 화면에 표시합니다. `BLOCKED`는 사전 점검 결과상 차단 판정입니다. ai-service는 이때 문서 본문을 접수 테이블에 저장하지 않고 `accepted:false, blocked:true`와 `preflight`만 반환하며, TMS는 업로드 원본·추출 텍스트를 유지한 채 상태 `BLOCKED`와 판정·차단 사유(`block_reasons`)를 저장합니다. `BLOCKED`는 통신 오류 `FAILED`와 구별되고 자동 재호출되지 않으며, 문서를 교체하면 같은 제출이 다시 검증됩니다. 같은 제출·같은 본문 해시의 Jev 호출은 ai-service DB lease로 한 번만 수행합니다. 시간 계층은 Jev 전체 마감 13초 < 대기 15초 < TMS HTTP 읽기 20초 < ai-service lease 30초 < TMS 전달 lease 45초입니다. Jev 요청은 단계별(connect 2·read 5·write 2·pool 1초) 제한과 재시도 1회(예산 8초)를 쓰며, 마감으로 호출을 반환해도 SDK 요청은 취소되지 않고 최대 18초(재시도 예산 8초 + 시도 10초)까지 살아 있을 수 있습니다. 이 요청은 이미 저장된 판정을 바꾸지 못하고 lease 안에서 끝납니다. Jev API 키가 없거나 Jev 호출에 실패하면 시스템 검사를 유지하고 경고와 함께 진행 허용 결과를 반환합니다. Compose에서 `TYPESAFE_API_KEY`를 설정하면 Jev의 역할·가독성·제품 일치 판정을 사용합니다.

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
| test-management-service :8082 | POST | `/api/submissions/{id}/files/{role}/replace` | 실패·차단 문서 교체 | 소유자 헤더 필요. `FAILED` 문서이거나 AI 전달이 `BLOCKED`일 때 허용. `PENDING`·`DELIVERED`·통신 실패 후 시도 이력이 있는 제출은 409 |
| ai-service :8005 | GET | `/` | `{"status":"ai-service running","python":"3.13"}` |
| ai-service :8005 | POST | `/api/v1/document-intakes` | 문서 3개 검증·접수. 동일 본문 재접수는 영수증 반환, 다른 본문은 409 |
| crawler-service :8006 | GET | `/` | `{"status":"crawler-service running"}` |
| crawler-service :8006 | POST | `/crawl` | `{"url":"https://example.com"}` → `{"apis":[{"method":"GET","url":"..."}]}` |

AI 문서 접수 API는 Jev 사전 점검을 선택적으로 호출하지만 생성형 LLM은 호출하지 않습니다. LLM 생성 경로는 아직 구현되지 않았습니다. 크롤러는 요청 목록을 최대 50개 반환하며, TC 실행·스크린샷·결함 판정도 구현되지 않았습니다.

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

정상 응답에는 `accepted`, `blocked`, `duplicate`, `submissionId`, `documentCount`, `receivedAt`과 `preflight` 판정 객체가 포함됩니다. 사전 점검이 `BLOCKED`이면 본문을 저장하지 않고 `accepted:false, blocked:true, receivedAt:null`과 `preflight`만 200으로 반환합니다. 같은 제출 ID와 같은 요청 본문은 `duplicate:true`로 같은 접수 결과를 반환합니다. 같은 제출 ID에 다른 요청 본문은 `409 Conflict`이며, `BLOCKED`로 저장되지 않은 제출은 본문이 바뀌어도 새 해시로 다시 평가합니다. 같은 해시의 사전 점검이 다른 요청에서 진행 중이어서 15초 안에 끝나지 않으면 `503`입니다. TMS는 접수 필드와 `preflight`를 확인·저장한 뒤 `DELIVERED`(또는 차단 시 `BLOCKED`)로 기록합니다.

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

`submission_files`가 문서 정보의 유일한 기준입니다. 역할, 파일 분류 및 확장자 형식, 원본 파일명, MIME, 저장 경로, 추출 텍스트, 문서 파싱 상태와 실패 이유를 보관합니다. 한 세트 안에서 같은 역할은 한 번만 등록할 수 있습니다. 파싱 실패 파일이나 AI 사전 점검으로 `BLOCKED`된 제출의 파일을 교체 경로로 교체할 수 있습니다.

세트 상태 규칙은 다음과 같습니다.

- 필수 역할 중 하나라도 누락됐고 실패가 없으면 `UPLOADED`입니다. 첫 문서만 `PARSED`여도 세트는 준비 완료가 아닙니다.
- 하나라도 문서 파싱이 실패하면 `FAILED`입니다. 실패 이유는 해당 `submission_files` 문서에서 응답합니다.
- `AGREEMENT`, `FUNCTION_LIST`, `MANUAL` 각 한 행이 모두 `PARSED`이고 추출 텍스트가 비어 있지 않을 때만 세트가 `PARSED`입니다.
- AI 전달 상태(`NOT_READY`, `PENDING`, `FAILED`, `DELIVERED`, `BLOCKED`)는 문서 준비 상태와 별도로 `submission_ai_deliveries`에 저장합니다.

V5는 submissions의 legacy 파일 사본을 `submission_legacy_file_archive`에 보존한 뒤 `submission_type`, `stored_path`, `extracted_text`를 제거합니다. 문서 행이 없는 제출은 역할·형식·식별자를 추정하지 않고 legacy 값과 보존 사유만 archive에 남깁니다. V6는 기존 세트 실패 사유도 archive에 보존하고 세트 상태를 문서 행에서 재계산한 뒤 parent의 중복 `failure_reason`을 제거합니다. 기존 Flyway migration은 수정하지 않습니다. Hibernate는 `ddl-auto=validate`로 스키마만 확인합니다.

운영 스키마 변경 전 DB 백업을 만들고 archive 결과를 확인합니다. 복구가 필요하면 운영 DB를 직접 덮어쓰지 말고 별도 DB에 백업을 복원해 검증합니다. 현재 로컬 환경에서 확보한 custom-format 사전 백업은 `test-management-service/build/autotest-before-submission-files-v6.dump`에 있습니다.

```powershell
docker compose exec -T db createdb -U test autotest_restore_check
docker compose cp test-management-service/build/autotest-before-submission-files-v6.dump db:/tmp/autotest-before-submission-files-v6.dump
docker compose exec -T db pg_restore -U test -d autotest_restore_check /tmp/autotest-before-submission-files-v6.dump
```

복원 결과를 별도 DB에서 확인한 뒤 운영 복구 여부를 결정합니다. 위 명령은 `autotest`를 변경하지 않습니다.

`submission_ai_deliveries`에는 `NOT_READY`, `PENDING`, `FAILED`, `DELIVERED`, `BLOCKED`, 시도 횟수, 실패 요약, 사전 점검 결과(`preflight_result`), 차단 사유(`block_reasons`), `blocked_at`, `updated_at`, `delivered_at`이 기록됩니다. `BLOCKED` 문서 교체 시 상태는 `NOT_READY`로 돌아가고 판정·차단 사유는 비워지며 시도 횟수는 유지됩니다. 각 전달 결과 반영은 해당 `attempt_count`가 아직 현재 시도와 일치할 때만 수행해 오래된 HTTP 요청이 새 시도를 덮지 못하게 합니다. AI 서비스는 `ai_document_intakes`에 제출 ID당 한 요청 본문만 저장하며, `BLOCKED` 문서 본문은 저장하지 않습니다. 사전 점검 시도는 본문 없이 판정만 `ai_preflight_attempts`(제출 ID + 본문 해시 키, 7일 보관)에 DB lease(30초)로 선점·기록하므로 프로세스 재시작이나 여러 인스턴스에서도 같은 해시의 Jev 호출은 한 번입니다. 진행 중이면 최대 15초 대기 후 503(TMS는 `FAILED`로 기록)을 반환하고, 완료된 판정은 재사용합니다. 소유권은 lease 만료가 아니라 토큰 교체(재선점)로 잃습니다. 만료만 됐고 아직 재선점되지 않았다면 같은 소유자가 저장할 수 있고, 재선점된 뒤 이전 소유자는 완료 저장이 거부되고(`complete()`가 소유권을 확인해 결과를 반환) 자기 판정을 버린 채 저장된 최신 판정을 다시 읽으므로, 이전 `READY`가 새 `BLOCKED`를 덮거나 본문을 저장하지 않습니다. 마감 뒤에도 남는 SDK 요청은 프로세스당 동시 4개까지만 허용하며 슬롯은 요청이 실제로 끝날 때 반환됩니다. 초과분은 1초 대기 후 `JEV_UNAVAILABLE` 경고로 진행(fail-open)합니다.

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

Compose가 필수로 참조하는 값은 `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `PRODUCT_DESCRIPTION_INTERNAL_TOKEN`입니다. 내부 토큰은 32바이트 이상의 임의 값으로 정하고 `.env`에 보관합니다. `S3_BUCKET`, `POSTGRES_USER`, `POSTGRES_PASSWORD`는 `.env`에 추가해도 현재 Compose 설정에 반영되지 않습니다. DB 계정은 개발용 `test/test`, DB 이름은 `autotest`로 고정되어 있습니다.

Compose는 `AI_SERVICE_URL=http://ai-service:8005`를 TMS에 전달하고 `AI_DATABASE_URL=postgresql://test:test@db:5432/autotest`로 AI 접수 DB를 설정합니다. Jev 의미 판정에는 선택 설정 `TYPESAFE_API_KEY`를 `.env`에 지정합니다. 키가 없으면 결정적 검사만 수행하고 경고를 반환합니다.

Compose의 `ollama` 서비스는 Ollama API 컨테이너 포트 `11434`를 Windows 호스트의 `127.0.0.1:21434`에 연결합니다. Windows에 설치된 Ollama가 기본 포트 `11434`를 쓰거나 Windows 예약 포트와 충돌할 수 있어 호스트 포트를 별도로 사용합니다. 두 포트를 혼동하지 마세요.

```powershell
# Ollama 컨테이너 시작 및 모델 다운로드 (모델 데이터는 ollama_data 볼륨에 보존)
docker compose up -d ollama
docker compose exec ollama ollama pull qwen3:4b
docker compose exec ollama ollama list

# Windows 호스트에서 컨테이너 API 확인
Invoke-RestMethod http://127.0.0.1:21434/api/tags
```

Docker 네트워크 안의 `ai-service`는 호스트 포트가 아닌 `http://ollama:11434`로 Ollama에 연결합니다. 실제 Ollama 생성을 사용하려면 `.env`에 아래 설정을 추가한 뒤 ai-service를 재생성합니다. 기본 설정은 테스트용 `LLM_MODE=mock`입니다.

```dotenv
LLM_MODE=real
LLM_PROVIDER=ollama
LLM_MODEL=qwen3:4b
```

```powershell
docker compose up -d --force-recreate ai-service
```

Ollama는 로컬 CPU/GPU와 메모리로 추론하며 외부 API 호출 한도는 없습니다. 이 노트북의 컨테이너 모델은 CPU로 실행되는 것을 확인했습니다. 첫 모델 다운로드는 약 2.5 GB입니다. `ollama_data` 볼륨을 지우면 모델을 다시 받아야 합니다. 기존 Windows Ollama는 `localhost:11434`에서 별도로 실행될 수 있습니다.

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f test-management-service seaweedfs
```

Compose에는 서비스 정의 10개가 있으며 frontend는 포함되지 않습니다. crawler 컨테이너는 `node:24.21.0-slim`을 기반으로 빌드됩니다. 실행 성공 여부는 실제 컨테이너 상태와 로그로 확인해야 합니다.

새 DB에서는 `test-management-service`가 Flyway 마이그레이션을 완료하고 healthy가 된 뒤 `ai-service`를 시작합니다. 두 서비스가 같은 `public` 스키마를 사용하므로 AI 테이블이 먼저 생성되면 Flyway가 `Found non-empty schema(s) "public" but no schema history table` 오류로 중단됩니다. 이미 이 오류가 발생했다면 볼륨을 삭제하지 말고 DB 백업과 테이블 구성을 먼저 확인합니다. AI 테이블만 있고 제출 테이블이 없는 경우에 한해 일회성 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `SPRING_FLYWAY_BASELINE_VERSION=0`으로 V1부터 적용한 뒤 두 설정을 제거합니다. 기존 제출 테이블이 있는 DB에는 이 복구 방법을 그대로 적용하지 않습니다.

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
- [x] LLM 전 문서 적합성 사전 점검: 프로그램 기반 추출 품질 검사 + 선택적 Jev 역할·제품 일치 판정
- [ ] 초기 필터 임계값과 Jev 판정을 샘플 및 손상 문서로 보정·검증
- [ ] LLM 문서 분석·제품 설명·TC 생성 API 연결
- [ ] TC 영속 저장 및 제출·TC 조회 API
- [ ] TC 실행 서비스와 Playwright 연동, 결함 판정
- [ ] 인증·인가, 결함 리포트 및 결과 화면
- [x] 제출 API frontend 프록시

## 10. 에이전트 작업 규칙

작업 전에 `AGENT.md`를 읽습니다. 프로젝트 목표는 문서 기반 TC 생성과 결함 탐지이며, 코드 제출·채점 방향으로 개발하지 않습니다. 기존 `@test` 파싱을 확대하거나 MinIO를 재도입하지 않습니다.

`AGENT.md`에는 작업 범위, 데이터 보존 및 검증 안전 규칙이 있습니다. 데이터 구조와 API 계약을 바꿀 때는 이 문서와 함께 갱신합니다.
