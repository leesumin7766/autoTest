# autoTest - Agent Guide

작업 전에 이 파일을 읽는다. 사용자의 최신 명시적 지시를 우선하며, 사용자 요청에 따라 목표·범위를 갱신한다.

## 1. 목표와 현재 범위

- 최종 목표: 시험합의서·기능리스트·제품 매뉴얼 업로드 → LLM으로 제품 설명과 TC 생성 → 테스트 자동화와 결함 리포트.
- LLM 전 문서 적합성 판정에는 결정적인 프로그램 검사와 Jev(TypeSafe) 의미 판정을 사용한다. 제품 설명·TC 생성은 생성형 LLM에 맡긴다.
- **현재 구현 범위는 업로드, 원본 저장, 텍스트 추출, DB 저장, ai-service 문서 접수 및 실패 복구까지다.**
- `dev-ai-service`의 다음 구현 범위는 LLM 전 문서 필터다. BLOCKED는 생성을 중단하고, READY_WITH_WARNINGS는 경고를 붙여 진행하며, READY는 정상 진행한다. 생성형 LLM의 제품 설명·TC 생성과 테스트 실행은 별도 범위다.
- 코드 제출/채점 시스템이 아니다. Main.java 및 @test input: 주석 파싱·채점 로직을 도입하지 않는다.

## 2. 실행 구성

docker-compose.yml이 서비스 구성의 기준이다. 사용자 제공 Docker Desktop 화면(2026-10-02)에는 아래 9개 서비스가 실행 중으로 표시되었다. 실행 표시는 API 정상 동작이나 최신 이미지 반영을 보장하지 않는다. 작업 시 실제 상태와 로그를 확인한다.

| Compose 서비스명 | 호스트:컨테이너 포트 | 역할 |
|---|---|---|
| db | 5432:5432 | PostgreSQL, pgvector |
| seaweedfs | 8333:8333, 8334:8334, 9333:9333 | S3 API, S3 gRPC, master |
| api-gateway | 8080:8080 | API 라우팅 |
| auth-service | 8081:8081 | 인증 서비스 |
| test-management-service | 8082:8082 | 업로드·파싱·문서 저장 |
| test-execution-service | 8083:8083 | 향후 테스트 실행 |
| report-service | 8084:8084 | 향후 리포트 |
| ai-service | 8005:8005 | 문서 수신 및 향후 LLM 처리 |
| crawler-service | 8006:8006 | Playwright 크롤러 |

- frontend는 Compose에 포함되지 않는다. 개발 중에는 호스트의 frontend 디렉터리에서 npm run dev로 실행한다. 기본 포트는 5173이며 실제 시작 로그를 확인한다.
- 로컬 Vite의 /api 프록시는 http://localhost:8080으로 연결된다.
- 호스트에서 컨테이너 접근은 localhost:<공개 포트>를 사용한다.
- Docker 내부 서비스 간 접근은 db:5432, seaweedfs:8333, ai-service:8005 등 Compose 서비스명을 사용한다. 컨테이너의 localhost는 해당 컨테이너 자신이다.
- Java 서비스에는 소스 bind mount가 없다. 소스 변경 후 이미지 재빌드·컨테이너 재생성이 필요하다.
- ai-service와 crawler-service 소스는 /app에 bind mount된다. 변경 반영에 재시작이 필요한지는 실행 명령을 확인한다. 의존성 변경 시 이미지를 재빌드한다.
- db_data와 seaweed-data 볼륨에 데이터를 보존한다. 업로드 오류 해결을 위해 볼륨을 삭제하지 않는다.
- depends_on만으로 애플리케이션 API 준비 완료를 보장하지 않는다. DB는 service_healthy 조건이 지정된 서비스에서 healthcheck를 기다린다.
- `test-management-service`는 `/actuator/health` healthcheck를 제공하고 Gateway는 해당 서비스가 healthy가 된 후 시작한다.

## 3. 업로드 정책과 API

- 허용: PDF(.pdf), Excel(.xls/.xlsx), HWP(.hwp/.hwpx), Word(.doc/.docx).
- 파일당 최대 100 MiB. 확장자와 MIME을 함께 검증한다.
- 그 외 파일(.java/.py/.js/.zip/.exe 등)은 400과 다음 메시지로 거부한다:
  허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다.
- 문서 역할 AGREEMENT, FUNCTION_LIST, MANUAL은 파일 형식과 구분한다.
- 첫 문서: POST /api/submissions, multipart file, productId, role, 헤더 X-Member-Id.
- 추가 문서: POST /api/submissions/{id}/files, multipart file, role, 헤더 X-Member-Id.
- 실패 문서 교체: POST /api/submissions/{id}/files/{role}/replace, multipart file, 헤더 X-Member-Id. `FAILED` 파싱 결과 또는 AI 전달 상태가 `BLOCKED`인 제출의 문서를 교체한다.
- 조회: GET /api/submissions/{id}.
- AI 전달 재시도: POST /api/submissions/{id}/ai-delivery/retry, 헤더 X-Member-Id.
- 세 문서를 한 번에 선택해도 현재 프런트엔드는 순차 요청한다. 같은 제출 ID 아래 역할별 문서를 저장한다.
- 원본 저장 경로: s3://autotest-docs/... . 이전 버킷 autotest-submissions를 사용하지 않는다.
- AWS SDK S3Client와 환경변수 인증을 사용한다. MinIO/OkHttp를 재도입하지 않는다.
- 익명 접근의 AccessDenied만으로 저장 성공·실패를 판단하지 않는다. 인증된 저장·조회로 확인한다.
- 인증 정보와 문서 본문을 로그에 출력하지 않는다.

## 4. 이번 작업의 파이프라인

호스트 frontend → localhost:8080 Gateway → test-management-service:8082
→ 파일 검증 → SeaweedFS 원본 저장 → 텍스트 추출 → PostgreSQL 저장
→ 세 역할 문서가 모두 정상 파싱되면 ai-service:8005로 전달
→ 수신 확인 → 프런트엔드에 전달 상태 표시.

- 제출 세트는 submissions, 개별 문서는 submission_files에 저장한다.
- submissions는 제출 ID, 회원·제품 ID, 세트 상태, 업로드 시각만 관리한다. 역할·파일명·형식·MIME·원본 경로·추출 텍스트·문서 상태·실패 이유는 submission_files만 기준으로 삼는다.
- 개별 문서에는 역할·형식·원본 경로·추출 텍스트·처리 상태·실패 원인이 있다.
- 세트 상태 규칙: 필수 문서가 일부 누락되고 실패가 없으면 UPLOADED, 하나라도 FAILED이면 FAILED, AGREEMENT/FUNCTION_LIST/MANUAL 세 행이 모두 PARSED이며 추출 텍스트가 비어 있지 않을 때만 PARSED.
- DocumentParser는 텍스트 추출만 담당한다. 향후 산출물 생성은 ai-service에 위임한다.
- ai-service에 문서 수신 전용 API를 구현하고 경로·요청·응답 계약을 문서화한다. 아직 없는 /generate를 구현된 API로 취급하지 않는다.
- 전달 데이터: submissionId, productId, 문서별 fileId, role, originalFilename, format, storedPath, extractedText.
- 역할과 식별자를 유지한다. 텍스트를 구분 없이 합치거나 원본 경로만 전달하지 않는다.
- 필수 역할 누락·중복 및 빈 추출 텍스트를 검증한다. ai-service는 이번에는 접수 결과만 반환한다.
- AI 전달 상태는 업로드·파싱 상태와 구분한다.
- 전달 실패 시 원본·추출 결과를 보존하고 재업로드 없이 전달을 재시도할 수 있게 한다.
- FAILED 또는 45초(전달 lease) 이상 갱신되지 않은 PENDING은 조회 응답의 `aiDelivery.retryable=true`로 나타내고 프런트에서 재시도할 수 있다. 새로고침 후에도 조회로 복구한다. `BLOCKED`는 사전 점검 차단이며 재시도 대상이 아니다.
- PENDING 시도는 attempt_count로 fence한다. 이전 요청이 늦게 끝나도 최신 시도의 상태를 덮어쓰지 않는다.
- 교체와 AI 전달 시작은 같은 제출 행 비관적 잠금을 사용한다. AI 전달 claim은 문서 준비 여부와 attempt를 확정한 뒤 트랜잭션을 끝내며 잠금 해제 후 HTTP를 호출한다.
- 문서 교체는 `FAILED` 파싱 문서이거나 AI 전달이 `BLOCKED`인 경우 허용한다. `BLOCKED` 교체 성공 시 전달 상태는 `NOT_READY`로 돌아가 같은 요청에서 재검증하며, 교체 파싱이 실패하면 기존 문서·상태를 보존하고 다시 교체할 수 있다. `PENDING`·`DELIVERED`와 통신 실패(`FAILED`) 후 시도 이력이 있는 제출은 교체를 거부한다. 새 파싱 실패 또는 동시 교체 패배 시 기존 DB 문서와 원본을 보존한다.
- `BLOCKED` 판정은 ai-service가 문서 본문을 저장하지 않고 TMS가 판정·차단 사유만 저장한다. 같은 제출 ID·본문 해시의 Jev 호출은 ai-service DB(`ai_preflight_attempts`, 본문 없음)의 선점·lease(30초)로 중복 방지한다. 시간 계층: Jev 마감 13초 < 대기 15초 < TMS 읽기 20초 < ai lease 30초 < TMS 전달 lease 45초.
- 교체 전 원본 및 DB 반영 실패로 남은 새 원본은 트랜잭션 안에서 s3_cleanup_outbox에 예약한다. 커밋 후 worker가 삭제하며 실패는 재시도한다. 현재 submission_files가 참조하는 원본은 삭제하지 않는다.
- 기존 Flyway 파일을 수정하지 않는다. V5는 legacy 파일 정보를 archive에 보존하고 중복 컬럼을 제거한다. V6는 legacy 실패 이유를 보존하고 문서 행으로 세트 상태를 재계산한 뒤 submissions의 중복 failure_reason을 제거한다. Hibernate는 ddl-auto=validate만 사용한다.
- AI 접수 응답의 `accepted=true`, 제출 ID 일치, 문서 수 3을 확인한 경우에만 DELIVERED로 기록한다.
- 재전송의 중복 접수를 방지하고 HTTP 타임아웃·오류 처리를 구현한다.
- 서비스 간 주소는 환경변수로 설정한다.
- AI 수신: POST `http://ai-service:8005/api/v1/document-intakes`. 요청은 submissionId, productId, documents[{fileId, role, originalFilename, format, storedPath, extractedText}]. 역할별 문서 3개가 필요하다. 동일 ID·동일 본문은 duplicate 영수증, 다른 본문은 409다.

## 5. 확인된 코드 상태와 미해결 사항

- 업로드 컨트롤러, 역할별 문서 저장, 문서별 파서 코드, Vite 프록시가 존재한다.
- 파서의 실제 지원 범위·추출 품질은 문서로 검증한다. 기존 파서를 일괄 교체하지 않는다.
- ai-service는 문서 접수·중복 방지·LLM 전 사전 점검 API를 제공한다. 생성형 LLM 연동은 미구현이다.
- Jev 필터는 출력 형식이 구조화되어도 판정 정확성이 자동으로 보장되는 것은 아니므로 실제 샘플과 손상 문서로 확인한다.
- 과거 `/api/submissions` 404 원인은 Gateway 모듈에 Spring Cloud Gateway 의존성이 없고 실행 이미지도 오래된 MVC 이미지였던 것이다. WebFlux Gateway 의존성과 라우트 설정을 적용했고, 최신 컨테이너에서 Vite→Gateway→TMS 요청이 415 multipart 검증 응답까지 도달하는 것을 확인했다.
- 과거 `Failed to fetch`는 HTTP 응답이 아닌 연결 실패다. 당시 상세 브라우저 오류 기록은 보존되지 않아 최초 원인은 단정하지 않는다. Vite 5173 및 프록시 경로는 현재 정상 동작한다.
- 404를 SeaweedFS 미완성이나 LLM 미구현 때문이라고 단정하지 않는다.
- 과거 테스트 성공 기록, 컨테이너 실행 표시, 상태 확인 API만으로 전체 흐름의 성공을 판단하지 않는다.

### 표준 업로드 검증 문서

향후 실제 업로드 검증은 루트 `sample/`의 원본 파일 3개로 수행하고 원본을 수정·삭제하지 않는다.

| 역할 | 파일 |
|---|---|
| AGREEMENT | `TTA-26-00872 시험합의서 v1.0.pdf` |
| FUNCTION_LIST | `2. PrintChaser 기능리스트_v0.5_20260304.xlsx` |
| MANUAL | `3. PrintChaser 관리자매뉴얼.pdf` |

각 검증은 새 제출 ID를 만들고, 세 역할의 파싱 상태·추출된 주요 내용·S3 저장·AI 접수 일치 여부를 확인한다. 문서 본문을 로그나 보고에 복사하지 않는다.

## 6. 완료 기준과 검증

- 프런트엔드에서 실제 문서 3개 업로드 및 동일 제출 ID 아래 세 역할 저장 확인.
- SeaweedFS 원본 저장·조회와 DB 추출 텍스트 확인.
- 필수 문서 미준비·파싱 실패 시 AI 전달하지 않음. 파싱 실패 문서는 AI 전달 전 또는 `BLOCKED` 상태에서 같은 역할 교체 가능.
- ai-service의 세 문서 역할·식별자·추출 텍스트 수신 확인.
- 프런트엔드에서 업로드 상태와 AI 전달 상태를 구분해 표시.
- ai-service 중단 시 문서 보존 및 전달 재시도 확인.
- 오래된 PENDING을 조회 후 복구하고 시도 번호 fencing으로 오래된 응답이 최신 상태를 덮지 않는지 확인.
- 문서 필터는 파싱 실패·빈 텍스트·심각한 손상·명확한 제품 불일치만 차단하고, 경미한 손상·누락된 시험코드·불확실한 일치는 경고 후 진행한다.
- 필터 경고는 `extractedText`와 분리된 메타데이터로 LLM에 전달하고, LLM이 누락 정보를 지어내지 않도록 지시한다.
- 필요한 테스트와 실제 서비스 경유 검증 수행. HTTP 200뿐 아니라 문서 상태와 추출 내용을 확인.

PowerShell 진단 예시(실제 문서 경로로 교체):

```powershell
docker compose ps
docker compose logs --tail 100 api-gateway test-management-service ai-service seaweedfs
.\gradlew.bat :test-management-service:test
curl.exe -F "file=@합의서.pdf" -F "productId=1" -F "role=AGREEMENT" -H "X-Member-Id: 1" http://localhost:8082/api/submissions
```

Gateway 비교 요청은 주소를 http://localhost:8080/api/submissions로 변경한다.
각 POST는 새 제출을 만들 수 있으므로 진단용 문서를 사용한다.
첫 업로드 응답에는 submissionId와 documents 등이 포함된다.

운영 DB를 자동 테스트에서 truncate/reset하지 않는다. SubmissionDatabaseIntegrationTest는 autotest_test 또는 AUTOTEST_TEST_DATABASE_URL로 지정한 격리 DB만 사용한다. 운영 DB URL을 지정하지 않는다.
운영 스키마 변경 전에 custom-format pg_dump 백업을 확보한다. 복구가 필요하면 운영 DB를 바로 덮어쓰지 말고 별도 DB에 백업을 복원해 확인한다.

최종 보고에는 Gateway 404 원인, `sample/` 실제 파일 역할별 업로드 결과, 검증 제출 ID, 실패·PENDING 복구 결과, 다음 LLM 작업이 사용할 수신 API 계약을 정리한다.
