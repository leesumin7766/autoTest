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

- 자료 등록: 역할별 파일·Jev 진행·문서 교체·전달 복구.
- 화면 수집: 접속·로그인·로컬/원격 설정·수집 근거.
- 메뉴트리: 문서/화면 기반 편집·승인·Excel. 사용자 LLM self-llm-v2 UI는 서버 계약 정합성 보완이 남아 있습니다.
- TC 생성: 승인/업로드 메뉴의 템플릿 TC·검색·상세·다운로드.
- 시험 실행: 범위·URL·Test connection 팝업·준비·요청·중단·이력·Qwen/Jev 결과/증거. 실제 TC 워커는 로컬 실행입니다.
- 결함 검토·리포트/재시험: 탐색 탭이며 세부 기능은 후속입니다.

Vite 실행 후 루트의 scripts/verify-frontend.cjs, verify-menu-tc-ui.cjs, verify-test-execution-ui.cjs를 Node로 실행합니다. 기본 대상은 4173이며 FRONTEND_QA_URL로 바꿀 수 있습니다. 격리 API fixture로 UI를 검사하며 실제 제품·모델 시험을 하지 않습니다.

실행 오류·추가 정보 필요·판정 보류를 제품 F/N/A로 자동 치환하지 않습니다. 생성된 결함 예시를 실제 결함으로 표시하지 않으며 개발용 회원 헤더는 실제 인증이 아닙니다.
