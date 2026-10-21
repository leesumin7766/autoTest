# 메뉴트리 → TC 생성 검증 결과

원본: `sample/menu sample/menu sample.xlsx` (SHA-256은 `generation-report.json` 참조).

| 원본 시트 | 기능 수 | TC 수 | Excel | 실행 입력 JSON |
|---|---:|---:|---|---|
| 메뉴트리 | 133 | 1,283 | [product-1.xlsx](product-1.xlsx) | [product-1.json](product-1.json) |
| 메그메뉴트리의 사본 | 86 | 842 | [product-2.xlsx](product-2.xlsx) | [product-2.json](product-2.json) |
| 나일소프트-메뉴트리의 사본 | 172 | 1,670 | [product-3.xlsx](product-3.xlsx) | [product-3.json](product-3.json) |
| 메뉴트리-에잇바이트의 사본 | 44 | 482 | [product-4.xlsx](product-4.xlsx) | [product-4.json](product-4.json) |
| 합계 | 435 | 4,277 | | |

검증 기준:

- 로그인 기능마다 27건, 주요·부가·CRUD 기능마다 9건, 제품 공통·문서 시험 68건 추가.
- 원본의 435개 기능 전부 포함. 메그 시트의 설명 없는 음영기복도·도움말·설정 3개도 보존.
- 각 기능 ID에 규칙 9개 또는 27개가 배정됨. 같은 경로의 서로 다른 기능을 합치지 않음.
- 서로 다른 시트의 TC 고유 ID 충돌 없음. 시트·행·경로·기능 설명·규칙을 추적 가능.
- 모든 결과는 미실행: JSON null, Excel 빈 셀. P/F/N/A 선택 목록만 제공.
- Excel에 수식 없음. 결함 열은 작성 예시이며 실제 발견 결함이 아님.
- JSON·Excel 행 수·파일 해시 일치. 세부 확인은 `verification-report.json`에 기록.

`product-*-preview.png`는 실제 생성 Excel 일부의 렌더링이다. `tc-desktop.png`·`tc-mobile.png`는 실제 생성 자료를 모의 API 응답으로 불러온 UI 검사 화면이며 제품 시험 실행 화면이 아니다.

Java 회귀 테스트 19건(실제 PostgreSQL 격리 DB HTTP·저장 통합 1건 포함), 독립 Python 검증, 새·기존 UI 브라우저 검사, 프런트엔드 프로덕션 빌드를 통과했다. 운영 서버·DB는 변경하지 않았다.

재현 명령과 서비스 계약은 [생성기 설명](../../docs/MENU_TC_GENERATOR.md)을 참고한다.
