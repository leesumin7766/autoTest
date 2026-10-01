# autoTest - Agent Guide (항상 먼저 읽을 것)

> 이 파일을 수정하지 않고, 모든 작업 전에 반드시 읽어라. 이전 대화의 코드 채점 로직은 폐기된 잘못된 구현이다.

## 1. 프로젝트 목표 (절대 잊지 말 것)

**기업체가 제공한 시험합의서, 기능리스트, 제품메뉴얼 3종 문서를 업로드해서 JEV(TypeSafe) + LLM으로 제품 설명 + 테스트케이스(TC)를 만들고, 이를 기반으로 테스트 자동화를 돌려 결함을 찾아내는 시스템.**

- 코드 제출/채점 시스템이 아님. `Main.java`, `@test input:` 주석 파싱은 잘못된 방향이므로 더 이상 구현하지 않는다.
- 최종 목표: 문서 3개 업로드 → TC 자동 생성 → Playwright 크롤러로 결함 탐지

## 2. 업로드 파일 포맷 정책 (보안)

**허용:**
- PDF (.pdf)
- Excel (.xls, .xlsx)
- HWP (.hwp, .hwpx)
- Word (.doc, .docx) - docs, word 모두 포함

**보안상 허용 금지:**
- 그 외 모든 파일: .java, .py, .js, .zip, .exe, .sh, .dll 등
- 금지 파일 업로드 시 400 Bad Request + 메시지: "허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다."
- MIME + 확장자 이중 검증 필수

> 3개 파일은 각각 시험합의서, 기능리스트, 제품메뉴얼에 해당하며, 한 번에 3개 업로드 또는 각각 업로드 가능. 모두 위 허용 포맷만 가능.

## 3. 올바른 파이프라인

```
[Frontend 5173] 
  -> [Gateway 8080 /api/submissions/**] 
  -> [test-management-service 8082]
    1. FileTypeResolver: PDF/Excel/HWP/Word만 허용
    2. S3FileStorageAdapter: SeaweedFS 8333에 저장 (s3://autotest-docs/...)
       - 8333에서 AccessDenied XML이 뜨는 것은 정상 (익명 접근 차단, S3 살아있다는 증거)
       - 실제 저장은 AWS SDK S3Client with Seaweed-access-key로 인증되어 성공
    3. DocumentParser: PDFBox / POI / HWP Parser로 텍스트 추출 (UTF-8)
    4. PostgreSQL: submissions 테이블에 stored_path, submission_type=PDF/EXCEL/HWP/WORD, status=UPLOADED 저장

  -> [ai-service] : FastAPI
    - POST /generate
    - 입력: 추출된 텍스트 3개 합본
    - 로직: TypeSafeClient(JEV) + LLM (TYPESAFE_API_KEY 사용)
      client.system_one.create(state="추출된 기능 설명", questions=[Choice(name="testability", ...)])
    - 출력: { product_description, test_cases: [{id, description, steps, expected}] }

  -> [crawler-service 8006]
    - POST /crawl { url: 제품 URL, test_cases: [...] }
    - Playwright로 API/스크린샷 수집 + 결함 판정

  -> [Frontend 결과]: 제품 설명 + TC 목록 + 결함 리포트
```

## 4. 현재 구현 상태 (Phase 1-5)

- Phase 1: .env (S3_ACCESS_KEY=Seaweed-access-key)
- Phase 2: MinIO 제거 -> AWS SDK S3 + SeaweedFS 8333(S3 API)/8334(gRPC) 분리 완료
- Phase 3-4: S3 저장 + Parser 껍데기는 만들었으나, Java/Python 파서는 잘못된 구현. PDF/Excel/HWP/Word 파서로 교체 필요
- Phase 5: SubmissionController (/api/submissions) + MockMvc 15개 테스트 통과, curl + PostgreSQL + SeaweedFS E2E 검증 완료
- Frontend: Vite 5173 proxy /api -> 8080

## 5. 에이전트가 절대 하면 안 되는 것

- @test input: / expected: Regex 파싱으로 TestCase 만들기 (폐기)
- .java, .py, .js, .zip을 허용 포맷으로 처리
- MinIO, OkHttp 클라이언트 재도입
- localhost:8333에서 AccessDenied 뜨면 실패로 판단 (정상임)
- JEV를 무시하고 LLM 없이 TC 생성

## 6. 에이전트가 해야 할 것

- 작업 시작 전 이 파일 먼저 읽기
- FileTypeResolver는 PDF, EXCEL, HWP, WORD만 반환, 그 외는 예외
- DocumentParser는 텍스트 추출만, TC 생성은 ai-service에 위임
- 보안 검증 로직: 확장자 + Content-Type 모두 체크
- 모든 파일 업로드는 s3://autotest-docs/ 경로 사용 (autotest-submissions는 이전 잘못된 이름)
- 테스트: PDF, DOCX, XLSX MockMultipartFile로 업로드 시 200 OK 검증

## 7. 서비스 포트 정리

- db: 5432
- frontend: 5173
- gateway: 8080
- test-management-service: 8082
- seaweedfs S3 API: 8333
- seaweedfs gRPC: 8334
- seaweedfs master: 9333
- crawler-service: 8006
- ai-service: FastAPI (8000대)

## 8. 검증 명령어

```bash
./gradlew :test-management-service:test --rerun-tasks # 15개 이상 통과
curl -F "file=@합의서.pdf" -F "productId=1" -H "X-Member-Id: 1" http://localhost:8082/api/submissions
# -> 200 {"value": "..."}
# PostgreSQL: SELECT * FROM submissions;
# SeaweedFS: docker logs seaweedfs | grep PUT
```

이 문서를 항상 최우선으로 참조하라.
