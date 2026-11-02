# manual-review-service

제품 매뉴얼 PDF를 로컬에서 읽고 **오탈자와 지원 종료 소프트웨어 표기 후보**를 찾는 CPU 전용 내부 서비스입니다. PaddleOCR-VL 1.6, 로컬 Ollama의 Qwen, 프로그램 검증을 사용합니다.

모든 결과는 **확인 필요(NEEDS_REVIEW)** 후보입니다. 원문을 수정하거나 결함을 확정하지 않습니다. 프로그램 동작과 매뉴얼의 일치 여부, 기능 설명 누락은 검사하지 않습니다.

## 시스템에서의 역할

```text
01 제품 매뉴얼 등록 → 06 매뉴얼 검토에서 명시적으로 시작
    ↓
test-management-service
  소유권 확인 · 원본 조회 · 작업/입력 식별 정보 저장
    ↓ 내부 토큰 인증
manual-review-service
  PDF 추출 → 로컬 OCR → 오탈자/지원 종료 표기 검사
    ↓
test-management-service
  결과/이력 저장 → 06 화면에 후보 표시
```

- 05 시험 실행, TC 생성, Jev 판정 없이 사용할 수 있습니다.
- 작업과 결과는 TMS의 `manual_review_jobs` 테이블에 저장합니다. 이 서비스는 작업 DB를 만들지 않습니다.
- TMS가 페이지 단위 내부 API(`manual-review-pages-v2`)를 호출하며, 진행률과 완료 페이지를 DB에 저장합니다. `/analyze`는 기존 호환용으로 유지합니다.
- 입력 PDF와 중간 결과는 임시 디렉터리에서 처리하며 완료·중단 시 정리합니다.

## 진행 현황·중단·이어하기

먼저 PDF 전체 페이지 수를 확인한 뒤 추출과 검사를 시작합니다. 06 화면에는 현재 페이지/전체 페이지(예: `3/81`), 완료·남은 페이지 수, 처리 단계, 누적 실행 시간을 표시합니다. 페이지 내부 처리 시간이 일정하지 않으므로 진행률은 완료 페이지 기준입니다.

`검토 중단`은 먼저 `PAUSING`으로 전환하고 OCR 자식 프로세스와 Qwen 요청을 종료합니다. 실제 종료가 확인되어야 `PAUSED`가 되고 이어하기를 허용합니다. 종료 확인에 실패하면 중단 확인 재시도가 필요하며 다른 분석도 시작하지 않습니다.

`이어서 검토`는 같은 작업의 attempt를 증가시킵니다. 완료 페이지는 재사용하고 중단된 페이지·실패한 페이지는 처음부터 다시 검사합니다. V29의 `manual_review_pages`가 페이지별 결과를 보존하며, 이전 attempt의 늦은 응답은 반영하지 않습니다. 실행 시간은 실행 구간만 누적하고 PAUSED 상태의 대기 시간은 제외합니다. 서버 재시작 시에도 기존 실행의 종료를 확인한 뒤 `INTERRUPTED`로 복구합니다.

원본 fileId·SHA-256 또는 검사 코드·모델 설정·기준표의 지문이 변경되면 기존 결과와 혼합하지 않도록 이어하기를 거부합니다. 이때는 새 검토를 시작해야 합니다. V29 이전 작업에 페이지 체크포인트가 없다면 이어하기는 첫 페이지부터 처리합니다. 이미지 영역 경고만 남아 모든 페이지 처리가 끝난 `PARTIAL`은 이어하기 대상이 아닙니다.

## 처리 방식

1. PDF의 텍스트층을 페이지별로 추출합니다.
2. 페이지에 문자·숫자가 20자 미만이면 CPU PaddleOCR-VL 1.6으로 읽습니다.
3. OCR 자식 프로세스를 종료해 모델 메모리를 해제합니다.
4. 프로그램이 내부 지원 종료 기준표와 원문을 비교합니다.
5. 로컬 Qwen이 작은 원문 구간에서 오탈자 후보를 추출합니다.
6. 프로그램이 인용문과 수정 대상이 실제 원문에 있는지 확인합니다.

Qwen 출력은 JSON schema로 제한합니다. 응답이 잘리거나 형식이 맞지 않으면 해당 구간을 더 작게 나눠 재검사합니다. 최소 구간에서도 실패하면 미완료로 기록합니다. 원문에 없는 제안, 동일한 수정 제안, 띄어쓰기만 바꾼 제안 등은 제외합니다.

텍스트층과 이미지가 함께 있는 페이지에서는 **텍스트층만 검사**합니다. 이미지 안의 글자는 별도 확인이 필요하므로 페이지 번호와 함께 `PARTIAL` 경고를 반환합니다.

## 실행 환경

| 항목 | 구성 |
| --- | --- |
| 런타임 | Python 3.11, Linux CPU 컨테이너 |
| OCR | PaddlePaddle 3.2.2 / PaddleOCR 3.6.0 / PaddleOCR-VL 1.6 |
| LLM | 로컬 Ollama, 기본 `qwen3:4b` |
| Compose 자원 제한 | CPU 4개, 메모리 10 GiB — Ollama 메모리는 별도 |
| 동시 처리 | Uvicorn worker 1개, 분석 요청 1개 |
| 내부 포트 | 8007, 호스트 포트 미공개 |

CPU·메모리 32GB 환경을 대상으로 구성했습니다. 실제 처리 시간은 페이지 내용과 다른 작업의 자원 사용에 따라 달라집니다. 기존 05의 `EXECUTION_QWEN_MODEL`은 변경하지 않습니다.

## 빌드와 실행

아래 명령은 **저장소 루트**에서 실행합니다. Docker Desktop과 Compose가 필요합니다. Windows에서 `docker`를 찾지 못하면 `%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin` 등 실제 설치 경로와 PATH를 확인하세요.

```powershell
# 로컬 모델 확인. 없을 때만 pull을 실행합니다.
docker compose up -d ollama
docker compose exec ollama ollama list
docker compose exec ollama ollama pull qwen3:4b

# 공식 OCR 모델을 포함한 CPU 이미지 빌드
docker compose --profile manual-review build manual-review-service
docker compose --profile manual-review up -d manual-review-service
```

최초 빌드는 패키지와 공식 모델을 다운로드하므로 인터넷 연결이 필요합니다. 모델은 이미지에 포함되며, 준비 단계는 애플리케이션 코드 변경과 분리해 빌드 캐시를 재사용합니다. 실행 시에는 인터넷 접근이 필요하지 않습니다.

TMS의 새 API와 DB 마이그레이션까지 배포할 때는 다음 절차도 필요합니다.

```powershell
.\gradlew.bat :test-management-service:bootJar
docker compose build test-management-service
# 운영 DB의 custom-format pg_dump 백업·검증 완료 후 실행
docker compose --profile manual-review up -d test-management-service manual-review-service ollama
```

운영 DB 백업 규칙은 [AGENT.md](../AGENT.md)를 따릅니다. 기존 Flyway 파일을 수정하지 않으며, 작업 저장 V28과 페이지 체크포인트 V29를 추가합니다. 06 탭의 화면 위치 저장에는 V27도 필요합니다.

Compose가 요구하는 S3 인증 변수와 내부 토큰도 설정되어 있어야 합니다. 비밀 값을 README나 Git에 기록하지 마세요. 프론트 기본 주소는 `http://localhost:5173`이며 별도 포트로 변경할 필요가 없습니다.

## 환경변수

| 변수 | 기본값/설명 |
| --- | --- |
| `PRODUCT_DESCRIPTION_INTERNAL_TOKEN` | 필수. TMS와 같은 내부 토큰 |
| `OLLAMA_BASE_URL` | `http://ollama:11434` |
| `MANUAL_REVIEW_QWEN_MODEL` | `qwen3:4b` |
| `OCR_CPU_THREADS` | `4` |
| `OMP_NUM_THREADS`, `MKL_NUM_THREADS` | Docker 이미지/Compose에서 각각 `4` |
| `OCR_FORCE_ALL_PAGES` | `1`이면 텍스트층이 있어도 모든 페이지를 OCR. 기본 미설정이며 Compose에 노출하지 않은 진단용 옵션 |

이미지에는 오프라인·텔레메트리 관련 설정과 모델 호스트 연결 확인을 생략하는 설정도 포함됩니다. 실제 외부 통신 차단은 Compose의 `manual_review` 내부 네트워크로 수행합니다.

`OLLAMA_BASE_URL`은 HTTP 방식의 `ollama`, `localhost`, `127.0.0.1`, `host.docker.internal`만 허용합니다. 컨테이너 안의 `localhost`는 호스트 PC가 아니라 해당 컨테이너입니다. 외부 모델 API로 자동 대체하지 않습니다.

## API

### 내부 서비스 API

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/health` | 없음 | 프로세스 상태·모델 이름·장치 표시 |
| GET | `/catalog` | 내부 토큰 | 현재 지원 종료 기준표 |
| POST | `/analyze` | 내부 토큰 | PDF 분석 결과 반환 |
| POST | `/tasks/{job}/{attempt}/prepare` | 내부 토큰 | PDF 등록·전체 페이지 수·검사 지문 반환 |
| GET | `/tasks/{job}/{attempt}/progress` | 내부 토큰 | 현재 추출·검사 단계 조회 |
| POST | `/tasks/{job}/{attempt}/pages/{page}` | 내부 토큰 | 지정 페이지 추출·검사 (`asOf` 요청) |
| POST | `/tasks/{job}/{attempt}/cancel` | 내부 토큰 | 프로세스 종료 확인·임시 파일 정리 |
| POST | `/tasks/{job}/{attempt}/close` | 내부 토큰 | 완료 세션 정리 |

인증 헤더는 `X-Internal-Token`입니다. `/health`의 `UP`은 프로세스 응답 확인이며, OCR 추론과 Ollama 연결의 성공을 보장하지 않습니다.

`POST /analyze` 요청:

```json
{
  "pdfBase64": "<PDF 파일 바이트의 Base64 문자열>",
  "asOf": "2026-11-02"
}
```

파일 경로나 외부 다운로드 URL은 받지 않습니다. TMS가 원본 바이트를 전달하고, 기준일은 TMS의 Asia/Seoul 실행일로 고정합니다.

주요 응답 필드:

| 필드 | 의미 |
| --- | --- |
| `status` | `COMPLETED` 또는 `PARTIAL` |
| `pageCount`, `nativePageCount`, `ocrPageCount` | 문서·텍스트 추출·OCR 페이지 수 |
| `reviewedChunks`, `failedChunks`, `retriedChunks` | 오탈자 검사 완료·실패·분할 재시도 구간 수 |
| `catalogVersion`, `catalogProducts`, `asOf` | 적용한 지원 종료 기준과 기준일 |
| `candidates` | `TYPO` / `EOL_SOFTWARE` 후보 목록 |
| `warnings`, `failureCodes` | 미검사 범위·실패 사유 코드 |

후보에는 `NEEDS_REVIEW` 상태, PDF의 1부터 시작하는 페이지 번호, 블록 ID, 인용문, 문자 범위와 좌표를 포함합니다. 오탈자는 원문과 수정 제안, 지원 종료 표기는 공식 URL·지원 범위·종료일·확인일을 추가합니다. 좌표는 추출 경로에 따라 PDF 좌표 또는 OCR 렌더 이미지 좌표이므로 그대로 같은 좌표계의 영역 강조에 사용하면 안 됩니다.

주요 오류:

| HTTP | 코드 | 의미 |
| --- | --- | --- |
| 403 | `INTERNAL_AUTH_REQUIRED` | 토큰 누락·불일치 또는 서버 토큰 미설정 |
| 409 | `OCR_BUSY` | 다른 분석 요청 처리 중 |
| 422 | `INVALID_REVIEW_INPUT` | 요청 필드 검증 실패 |
| 422 | `INVALID_PDF` | Base64·PDF 헤더·파일 크기 검증 실패 |
| 422 | `NO_TEXT_EXTRACTED` | 추출한 텍스트가 없음 |
| 503 | `OCR_EXTRACTION_FAILED` | OCR 실패·시간 초과 또는 추출 단계 제한 위반 |

Qwen 일부 구간 실패는 전체 HTTP 오류 대신 `PARTIAL`과 `failedChunks`로 표시할 수 있습니다. 결과가 비어 있거나 HTTP 200이라는 이유만으로 문서에 결함이 없다고 판단하지 마세요.

### 프론트에서 사용하는 TMS API

```text
GET  /api/submissions/{id}/manual-reviews?job={선택적 작업 ID}
POST /api/submissions/{id}/manual-reviews
POST /api/submissions/{id}/manual-reviews/{job}/pause
POST /api/submissions/{id}/manual-reviews/{job}/resume
```

TMS에서 `X-Member-Id`와 문서·작업 소유권을 확인합니다. POST는 202를 반환합니다. 상태는 `RUNNING`, `PAUSING`, `PAUSED`, `COMPLETED`, `PARTIAL`, `FAILED`, `INTERRUPTED`입니다. 조회의 `progress`에는 `totalPages`, `currentPage`, `completedPages`, `stage`, `elapsedMs`가 있으며, `resumable`과 체크포인트 메타데이터도 반환합니다.

## 지원 종료 기준 관리

[`lifecycle.json`](lifecycle.json)에 공식 출처·지원 범위·일반 지원 종료일·확인일을 저장합니다. 초기 범위는 Windows 10 Home/Pro, Windows 8.1, Windows 7입니다. 검사 중 인터넷을 조회하지 않습니다.

LTSC/LTSB/IoT/ESU, 에디션 미지정 등은 별도 확인 사유를 표시합니다. 지원 종료된 운영체제를 프로그램이 지원한다고 해서 곧바로 문서 결함으로 확정하지 않습니다.

기준을 갱신할 때는 공식 출처에서 적용 범위를 확인하고, 제품별 값과 `checkedAt`, 전체 `version`을 변경한 뒤 관련 테스트를 실행하고 이미지를 다시 빌드합니다. 자동 온라인 갱신은 미구현입니다. 과거 결과에는 당시 기준 정보가 보존됩니다.

## 테스트

모델을 실제 호출하지 않는 단위·API 테스트입니다. 테스트 파일은 이미지에서 제외하므로 읽기 전용으로 연결합니다.

```powershell
# 저장소 루트 / PowerShell
$testSource = (Resolve-Path ./manual-review-service).Path
docker run --rm --network none `
  --mount "type=bind,source=$testSource,target=/checks,readonly" `
  -e PYTHONPATH=/checks -e PYTHONDONTWRITEBYTECODE=1 -w /checks `
  autotest-manual-review:cpu-v1 python -m unittest test_tasks test_review test_api -v
```

검증 범위는 원문 근거 확인, 지원 범위 예외, 구간 분할 재검사, 내부 인증, 동시 요청 차단, 오류 응답의 문서 내용 노출 방지, 외부 모델 URL 거부 등입니다.

실제 모델·화면 검증 스크립트는 저장소 루트의 다음 파일을 사용합니다. 컨테이너 마운트와 테스트 작업 생성 여부를 각 파일에서 먼저 확인하세요.

- `scripts/verify-manual-ocr-runtime.py`: 네트워크를 차단한 CPU OCR 샘플 검사
- `scripts/verify-manual-qwen-runtime.py`: 로컬 Qwen 합성 오탈자 검사
- `scripts/verify-manual-review-live.py`: 별도 검증 회원의 자료 등록·검토·저장·권한 검사. 새 검증 자료를 생성할 수 있음
- `scripts/verify-manual-review-live-ui.cjs`: 실제 API 결과의 5173 표시·복원 확인
- `scripts/verify-manual-review-run.cjs`: 격리 fixture 기반 시작·상태 조회·부분 실패·모바일 검사
- `scripts/verify-manual-review-progress.cjs`: 페이지 수·시계·중단 확인·이어하기·모바일 UI 검사
- `scripts/verify-manual-review-progress-live.py`: 별도 검증 회원의 실제 전체 PDF를 사용한 페이지 저장·중단·재개 검사. 새 자료를 생성하고 중단 상태로 남김

진행 현황 변경은 Python 19개, 격리 DB Java 6개 테스트와 UI 테스트로 검증했습니다. 실제 56페이지 문서에서도 1페이지 완료 후 2페이지에서 중단·재개했으며 완료 페이지 재사용, 중단 중 시간 고정, 이전 attempt 응답 차단을 확인했습니다. 전체 56페이지의 검사 품질을 검증한 것은 아닙니다. [진행 현황 검증 기록](../docs/MANUAL_REVIEW_PROGRESS_VALIDATION_2026-11-02.json)을 참고하세요.

2026-11-02 검증에서는 CPU OCR 한 페이지에 약 360초가 걸렸습니다. 이는 모델 초기화와 해당 페이지 처리에 대한 단일 측정이며, 전체 매뉴얼의 처리 속도나 정확도 보장이 아닙니다. 실제 API 검증은 원본을 보존한 1페이지 발췌본으로 수행했습니다.

## 제약과 보안 경계

- PDF 50 MiB·300페이지, 추출 텍스트 200만 자 제한이 있습니다. 암호화 문서 등 처리하지 못한 입력은 오류로 종료합니다.
- 분석은 한 번에 한 작업이며, 실행 attempt당 약 6시간의 상한이 있습니다. 중단 페이지 내부의 OCR·LLM 구간까지 이어서 처리하지는 않습니다.
- 띄어쓰기·문체 교정, 이미지가 섞인 텍스트 페이지의 이미지 글자, 기준표에 없는 SW까지 모두 검사하지 않습니다.
- OCR 인식 오류·LLM 오탐·누락이 가능하므로 원본 대조가 필요합니다.
- 실행 컨테이너는 비루트 사용자, capability 제거, 외부 포트 미공개, 내부 네트워크를 사용합니다. 모든 패키지에 대한 보안 감사를 의미하지는 않습니다.
- 문서·모델 응답·인증 값을 로그에 출력하지 않으며, 입력 검증 실패 응답에서도 PDF Base64를 반환하지 않습니다.

## 파일 구성

| 파일 | 역할 |
| --- | --- |
| `app.py` | FastAPI, 내부 인증, 요청 제한, OCR 자식 프로세스·Qwen 연결 |
| `ocr_worker.py` | PDF 텍스트 추출, CPU OCR, 페이지·블록 정보 생성 |
| `page_worker.py` | 한 페이지의 추출·Qwen 검사·단계 보고 |
| `tasks.py` | attempt별 임시 세션·프로세스 그룹 종료·늦은 요청 차단 |
| `review.py` | 지원 종료 표기·오탈자 근거 검증, 구간 분할 재검사 |
| `lifecycle.json` | 버전이 있는 지원 종료 기준표 |
| `test_tasks.py`, `test_review.py`, `test_api.py` | 프로세스 종료·검사 로직·내부 API 테스트 |
| `Dockerfile`, `requirements.txt` | CPU 이미지와 직접 의존성 버전 |

전체 업무 흐름은 [매뉴얼 검토 문서](../docs/MANUAL_REVIEW.md), 실제 검증 통계는 [2026-11-02 검증 기록](../docs/MANUAL_REVIEW_VALIDATION_2026-11-02.json)을 참고하세요.
