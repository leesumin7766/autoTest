# 매뉴얼 검토 (dev-document)

06에서 PDF를 로컬로 분석해 오탈자와 지원 종료 SW 표기 후보를 표시한다. 원문은 수정하지 않으며 **모든 후보는 확인 필요**다. 05 TC 시험·Jev·외부 생성형 API에 의존하지 않는다.

## 사용자 흐름

01에서 제품 매뉴얼을 등록한 뒤 06의 `매뉴얼 검토 시작`을 누른다. 매뉴얼 단독 등록으로도 가능하며 탭 진입·새로고침·복원만으로 검사를 시작하지 않는다. 서버에서 처리하고 탭이 활성일 때 3초 간격으로 상태를 조회한다. 페이지·인용문·수정 제안·공식 지원 기준과 이전 이력을 확인할 수 있다.

현재 50 MiB 이하·최대 300페이지 PDF를 지원한다. 다른 등록 형식은 보존하되 자동 검사 버튼을 비활성화한다. 기능 설명 누락·프로그램 동작과의 불일치는 검사하지 않는다.

## 구조

- TMS: 소유권 확인, 원본·SHA-256·fileId·Asia/Seoul 실행일 고정, V28 manual_review_jobs에 작업·결과 저장.
- CPU 서비스: 텍스트층이 있는 페이지는 직접 추출하고, 텍스트가 부족한 페이지에는 PaddleOCR-VL 1.6을 적용한다. OCR 자식 프로세스 종료로 모델 메모리를 해제한 뒤 로컬 Qwen 4B를 호출한다.
- 프로그램: Qwen 인용·수정 대상이 원문에 실제 존재하는지 검증하고, 지원 종료는 내부 기준표로 판단한다. 잘린 응답·형식 오류는 구간을 더 작게 나눠 재검사하고, 최소 구간에서도 실패하면 미완료로 남긴다.
- 이미지가 포함된 텍스트 페이지의 이미지 안 글자는 현재 별도 OCR하지 않는다. 해당 페이지를 PARTIAL 경고에 표시한다. 읽지 못한 페이지나 실패 구간을 결함 없음으로 처리하지 않는다.
- 좌표/문자 범위·페이지·블록 ID를 후보에 보존한다. PDF 뷰어·영역 강조·후보 확정/기각은 이번 범위에 포함하지 않는다.
- 상태: RUNNING/PAUSING/PAUSED/COMPLETED/PARTIAL/FAILED/INTERRUPTED. 중단은 실제 프로세스 종료를 확인한 뒤 PAUSED로 확정한다. 종료 확인 실패 시 PAUSING을 유지하고 중단 확인을 재시도한다. 서버 재시작도 종료 확인 후 INTERRUPTED로 복구한다.
- 먼저 전체 페이지 수를 확인하고 현재/전체 페이지·완료/남은 페이지·처리 단계·누적 실행 시간을 표시한다. 중단 상태의 시간은 누적하지 않는다. 기본 프론트 포트 5173을 유지한다.
- V29 manual_review_pages에 페이지 결과를 저장한다. 이어하기는 같은 작업의 attempt를 증가시키며 완료 페이지를 건너뛰고 중단·실패 페이지를 다시 검사한다. 늦은 이전 attempt 응답은 반영하지 않는다. 원본 fileId/SHA 또는 검사 코드·모델 설정·기준표 지문이 바뀌면 새 검토가 필요하다.
- CPU 분석은 한 번에 한 작업이며 attempt당 약 6시간의 상한이 있다. 문서 변경 후 이전 결과에는 오래된 이력 안내를 표시한다. 이미지 경고만 있는 PARTIAL처럼 모든 페이지가 처리된 작업은 이어하기 대상이 아니다. 과거 작업에 페이지 저장 정보가 없으면 첫 페이지부터 처리한다.

## 지원 종료 기준

`manual-review-service/lifecycle.json`에 공식 URL·지원 범위·일반 지원 종료일·확인일·버전을 저장하며 검사 중에는 인터넷을 조회하지 않는다. 초기 범위는 Windows 10 Home/Pro, Windows 8.1, Windows 7이다. 다른 SW까지 검사했다고 표시하지 않는다.
Windows 10 LTSC/LTSB/IoT/ESU나 에디션 미지정은 범위 확인 사유를 표시한다. 일반 지원 종료일만으로 동작 불가나 문서 결함을 확정하지 않는다. 명시적 미지원 문구는 제외한다. 기준 갱신은 공식 출처를 확인한 뒤 JSON·테스트를 함께 변경하며, 결과에는 당시 기준 버전과 출처를 보존한다. 자동 온라인 갱신은 미구현이다.

공식 출처:

- https://learn.microsoft.com/en-us/lifecycle/products/windows-10-home-and-pro
- https://learn.microsoft.com/en-us/lifecycle/products/windows-81
- https://learn.microsoft.com/en-us/lifecycle/products/windows-7
- https://www.paddleocr.ai/latest/en/version3.x/pipeline_usage/PaddleOCR-VL.html
- https://docs.ollama.com/capabilities/structured-outputs

## 배포·보안

```powershell
docker compose --profile manual-review build manual-review-service
.\gradlew.bat :test-management-service:bootJar
# 스키마 변경 전 AGENT.md의 custom-format pg_dump 백업·검증 필수
docker compose build test-management-service
docker compose --profile manual-review up -d test-management-service manual-review-service ollama
```

CPU 전용 Python 3.11 이미지에 PaddlePaddle 3.2.2·PaddleOCR 3.6.0을 설치하고 공식 모델을 빌드 때 받는다. 모델 준비 단계는 코드 변경과 분리해 캐시한다. 실행 시 외부 포트 없이 internal 전용 Docker 망에서 TMS·Ollama와 통신한다. 비루트 사용자·capability 제거를 적용했다. 모든 의존성의 보안 감사를 의미하지는 않는다.
`MANUAL_REVIEW_QWEN_MODEL` 기본값은 기존 `qwen3:4b`다. 05의 EXECUTION_QWEN_MODEL 설정은 변경하지 않는다. 외부 LLM fallback은 없으며 원문·추출문·비밀 값을 로그에 출력하지 않는다. 입력 임시 파일은 종료 후 정리한다.
V28/V29를 추가하고 기존 마이그레이션은 수정하지 않는다. 모든 API에서 회원 소유권과 작업 ID 범위를 확인한다. 회원 헤더는 기존 프로젝트의 개발용 인증 정책을 따른다. 운영 V29 적용 전 custom-format 백업을 생성하고 pg_restore --list로 확인했다.

API:

- GET /api/submissions/{id}/manual-reviews?job={optionalJobId}: 최근 이력·선택 결과
- POST /api/submissions/{id}/manual-reviews: 명시적 새 검토 시작 (202)
- POST /api/submissions/{id}/manual-reviews/{job}/pause: 중단·종료 확인 (202)
- POST /api/submissions/{id}/manual-reviews/{job}/resume: 동일 작업 이어하기 (202)
- 내부 /tasks/{job}/{attempt}/prepare, /progress, /pages/{page}, /cancel, /close: 페이지 단위 실행·중단 프로토콜. 외부 다운로드 URL은 받지 않는다. /analyze는 기존 호환용이다.

이번 환경에서는 .env의 S3 변수 누락으로 Compose 보간이 실패해 기존 TMS 컨테이너의 동일한 키를 프로세스 메모리에서 재사용했다. .env와 비밀 값은 변경·출력하지 않았다. 이후 운영자는 S3_ACCESS_KEY/S3_SECRET_KEY 설정을 확인해야 한다.

## 검증

- Python: python -m unittest test_tasks test_review test_api -v (19개)
- Java: .\gradlew.bat :test-management-service:test --tests '*ManualReview*Test' (autotest_test 격리 DB, 6개)
- UI: scripts/verify-manual-review.cjs 및 scripts/verify-manual-review-run.cjs (FRONTEND_QA_URL로 5173 지정)
- 기존 05 UI·저장 작업 복원·공통 오류 메시지 회귀 검사도 실행한다.
- 실제 CPU OCR: scripts/verify-manual-ocr-runtime.py를 --network none 컨테이너에서 실행한다. 원본은 읽기 전용으로 연결하고 통계만 기록한다.
- 실제 Qwen: scripts/verify-manual-qwen-runtime.py의 합성 문장을 로컬 Ollama에서 검사한다.
- 실제 API: scripts/verify-manual-review-live.py가 별도 검증 회원 98011에 원본을 보존한 1페이지 발췌본을 등록한다. 재검사는 REVIEW_TEST_SUBMISSION을 지정한다.
- 진행 UI: scripts/verify-manual-review-progress.cjs가 페이지·시간·중단 확인·이어하기·모바일을 검사한다.
- 실제 중단/재개: scripts/verify-manual-review-progress-live.py가 별도 검증 회원 98111에 56페이지 문서를 등록해 1페이지 완료 후 2페이지에서 중단·재개하고 PAUSED로 남겼다. 완료 페이지 timestamp 유지, 중단 중 시간 고정, 회원 권한을 확인했다. 전체 문서 품질 검증은 아니다. 통계는 docs/MANUAL_REVIEW_PROGRESS_VALIDATION_2026-11-02.json에 기록한다.

2026-11-02 기존 샘플 원문 53페이지 한 장에서 CPU OCR이 22블록/1,000자를 추출했고 약 360초가 걸렸다. 합성 Qwen 검사에서는 오탈자 1건·지원 종료 표기 1건을 받았다. 이는 매뉴얼 전체의 정확도 검증이 아니다. 문서 본문 없는 실제 통계는 docs/MANUAL_REVIEW_VALIDATION_2026-11-02.json에 기록한다.
