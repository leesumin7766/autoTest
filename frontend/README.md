# autoTest frontend

React + TypeScript + Vite 기반 작업 화면입니다. 전체 기능·백엔드·검증 한계·계획은 [루트 README](../README.md)를 따릅니다.

## 실행

Node.js 24.21.0(../.nvmrc), npm 11.x를 사용합니다. 저장소 루트에서:

```powershell
npm ci --prefix frontend
npm run dev --prefix frontend -- --host 127.0.0.1 --port 4173
```

주소는 http://127.0.0.1:4173이며 포트 미지정 시 Vite 기본은 5173입니다. /api는 localhost:8080 Gateway로 프록시됩니다. 프런트엔드는 Compose에 포함되지 않습니다.

```powershell
npm run build --prefix frontend
npm run lint --prefix frontend
```

## 화면과 검증

- 저장 시험 작업: 이름·고유 ID·목록·이름 변경·기존 자료/산출물 불러오기. 새로고침 시 마지막 선택 작업과 탭을 복원하며 자동 시험 시작은 하지 않습니다. [저장 구조·검증](../docs/SAVED_WORKSPACES.md)을 참고하세요.

- 자료 등록: 역할별 파일·Jev 진행·문서 교체·전달 복구.
- 화면 수집: 접속·로그인·로컬/원격 설정·수집 근거.
- 메뉴트리: 문서/화면 기반 편집·승인·Excel. self-llm-v4 공통 지침 1회 등록 → 제품 프롬프트 → JSON 가져오기·근거 복원 → 검토 흐름입니다.
- TC 생성: 승인/업로드 메뉴의 템플릿 TC·검색·상세·다운로드.
- 시험 실행: 범위·URL·Test connection 팝업·준비·요청·중단·이력·Qwen/Jev 결과/증거. 실제 TC 워커는 로컬 실행입니다.
- 결함 검토·리포트/재시험: 증거 조회·사용자 판정/사유·결함 상태 저장, 선택/F·미판정 재시험 준비, Markdown 보고서 초안을 지원합니다. 기업용 서식·버전 비교 화면은 별도입니다.

현재 App은 MenuTcPanel을 04 화면에 연결합니다. TcGenerationPanel·ProductDescriptionPanel은 기존 소스로 보존되어 있으나 App에서 렌더링하지 않습니다. 생성 TC 상세 확인과 수행 후 판정 수정은 이미 구현되어 있습니다. TC 본문이나 새 조회 실행 명세의 bindings를 수정·저장하는 UI/API는 현재 경로에 없습니다. [코드 연결 검토](../docs/code-audit/REVIEW.md)를 참고하세요.

Vite 실행 후 루트의 scripts/verify-frontend.cjs, verify-menu-tc-ui.cjs, verify-test-execution-ui.cjs를 Node로 실행합니다. 기본 대상은 4173이며 FRONTEND_QA_URL로 바꿀 수 있습니다. 격리 API fixture로 UI를 검사하며 실제 제품·모델 시험을 하지 않습니다.

실행 오류·추가 정보 필요·판정 보류를 제품 F/N/A로 자동 치환하지 않습니다. 생성된 결함 예시를 실제 결함으로 표시하지 않으며 개발용 회원 헤더는 실제 인증이 아닙니다.
