# 매뉴얼 검토 — 프론트 연결 (dev-document)

## 이번 구현 범위

- 05 시험 실행 뒤에 `06 매뉴얼 검토`를 추가한다. 기존 결함 검토와 리포트는 ID를 유지하고 표시 번호만 07/08로 변경한다.
- 문서 세트가 있고 MANUAL 상태가 PARSED이며 업로드/복원이 끝나면 01에 `매뉴얼 검토로 이동` 버튼을 표시한다. 파일 선택만으로는 표시하지 않는다.
- 매뉴얼 검토는 FUNCTION_LIST, Jev 판정, 크롤링, TC 및 시험 실행 완료에 의존하지 않는다.
- 직접 탭 진입 시 미등록/실패/처리 중 매뉴얼에는 자료 등록 안내를 표시한다.
- 실제 문서 파일명, 오탈자 및 지원 종료 SW 표기 검토 범위, 검사 미수행 상태를 표시한다. 검사 시작 버튼은 비활성이다. 가짜 결과나 OCR/LLM 요청은 만들지 않는다.
- 기존 workspace 저장/복원에 manual-review ID를 추가한다. V27은 기존 V26을 수정하지 않고 DB 허용 목록을 확장한다.

## 후속 구현

CPU/32GB 환경에서 PaddleOCR-VL 1.6을 검증하고 별도 작업으로 연결한다. 페이지·원문·위치와 추출 결과를 저장한 후 LLM은 오탈자/소프트웨어 언급 후보를 추출하고, 프로그램이 원문 인용과 버전별 지원 종료 기준을 검증한다. 지원 종료 판단은 에디션·확장 지원·기준일을 구분하며 SW 이름만으로 결함을 확정하지 않는다.

## 테스트

먼저 UI 수용 테스트를 추가해 실패를 확인하고 화면을 연결했다. 새 페이지 ID의 서버 검증도 실패를 확인한 뒤 허용 목록을 확장했다. origin/dev7 병합 시 공통 오류 처리, 실행기 점유 현황, TC 분류의 기존 구현을 유지했다.

`node scripts/verify-manual-review.cjs`는 격리 API fixture로 미등록, 정상 등록(매뉴얼 단독/Jev 미수행), 실패/처리 중 문서, 이동, 작업 복원, 모바일 넘침, 시험 실행 API 비호출을 확인한다. 실제 업로드·OCR·LLM 검증은 아니다.

서버 검증은 DB를 쓰지 않는 Mockito 단위 테스트로 실행한다:

```powershell
.\gradlew.bat :test-management-service:test --tests '*SubmissionWorkspacePageTest'
```

origin/dev7 병합 후 프론트 TypeScript/프로덕션 빌드, 매뉴얼 UI 테스트와 위 Java 테스트가 통과했다. Gradle의 main/test 소스 컴파일도 통과했다. 전체 백엔드 테스트를 실행한 것은 아니다. 기존 시험 실행 UI fixture에는 workspace 조회·실행기 상태 응답을 추가하고, 단계 번호와 공통 오류 처리의 503 안내 기대값을 맞췄다. 05의 실제 수행·판정 코드는 수정하지 않았다.

`node scripts/verify-test-execution-ui.cjs` 회귀 검사와 `FRONTEND_QA_URL=http://127.0.0.1:4173 node scripts/verify-saved-workspaces.cjs` 저장 작업 복원 검사도 통과했다. 이 검사들은 격리 fixture를 사용하며 실제 제품 시험 실행 검증을 의미하지 않는다.

운영 DB 및 컨테이너는 변경하지 않았다. V27 배포 전에는 AGENT.md의 DB 백업 절차를 따른다. 실제 서버를 통한 화면 위치 저장 검증은 남아 있다.
