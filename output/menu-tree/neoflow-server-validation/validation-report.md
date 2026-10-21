# NeoFlow Server 메뉴트리 실제 검증 결과

- 검증일: 2026-10-20
- 대상: https://211.233.211.228/login
- 1depth 제품명: NeoFlow Server
- 문서: sample/sample3의 NeoFlow Server 사용자매뉴얼 v1.0.3 및 NeoFlow 기능리스트 v1.0.3
- 제출 ID: b096f467-cfbc-41ca-9528-1ac23db2be19
- 화면 수집 ID: f00875a9-9700-400e-968f-7f10f84a25c9
- 통합 생성 ID: a0518a79-53ba-4609-8476-d5f39346b649

## 판정

정상 메뉴트리 작성 검증 실패. 승인 및 최종 Excel 출력은 수행하지 않았다.

## 확인한 결과

1. 두 문서 PARSED, AI 전달 DELIVERED, Jev READY_WITH_WARNINGS. 시험합의서 누락은 경고로 처리되며 생성 게이트는 허용된다. Jev 입력 일부 생략 경고가 있으며 통합 생성에 사용하는 문서 원문 청크와는 별도다.
2. 호스트 curl은 접속 실패했으나 실제 Docker Playwright 연결은 HTTP 200. 로그인 테스트 loginVerified=true. 이번 실행에는 원격 실행기가 필요하지 않았다.
3. 수집 COMPLETED/DONE이지만 캡처 8개, 고유 상태 8개, 메뉴 이동 실패 45개. 캡처 URL은 dashboard와 settings에 한정된다. 로딩 시간 초과 0개.
4. 관찰 트리는 58노드이며 클릭 결과 확인 메뉴는 7개. 이는 전체 업무 메뉴 수나 기능 동작 합격 수가 아니다.
5. Collapse sidebar, Dark Mode, 한국어/English 전환을 업무 탐색과 같이 실행한다. 이후 Jobs/Schedules/DAGs 등에서 CLICK_MENU/NAVIGATION_NOT_AVAILABLE가 기록됐다. 현재 DOM 위치 및 문구를 엄격히 대조하는 선택자와 브라우저 내 UI 설정 변화가 충돌한 정황이 있다. 언어가 다른 동일 메뉴가 별개 관찰로 늘어난다.
6. 통합 자동 생성은 근거 201개를 고정한 뒤 FAILED/MENU_SERVICE_UNAVAILABLE, 결과 0행이다. AI 서버에서 Unsupported upgrade request 및 Invalid HTTP request received가 관찰되며 generate 요청의 정상 처리는 확인되지 않았다. Java HttpClient의 기본 HTTP/2 업그레이드와 AI HTTP 서버의 호환 문제 가능성이 있어 HTTP/1.1 강제 후 재검증이 필요하다. 원인은 아직 수정 및 재시험으로 확정하지 않았다.

## 후속 수정 및 재검증

- 언어/테마/사이드바 접기 등의 UI 환경 전환은 관찰 목록에 보존하고 자동 탐색에서는 분리한다.
- 메뉴 재현 전에 환경을 일관되게 복원하고, DOM 위치만 의존하지 않는 메뉴 식별을 사용한다.
- 수집 DONE과 업무 메뉴 커버리지를 구분해 이동 실패가 다수이면 불완전 상태를 표시한다.
- AI 호출 HTTP 통신을 확인한 뒤 동일 문서와 재수집 결과로 자동 생성한다.
- 문서 기능과 실제 업무 메뉴의 연결, Operations/Configuration 등 그룹 계층, 상단/하단 메뉴, 6열 Excel을 검증한다.

인증정보는 이 보고서와 결과 파일에 기록하지 않았다.
