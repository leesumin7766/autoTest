# 단계 간 연결 점검 — 2026-10-22, dev5

## 인증서 오류 재발 보정

후속 사용자 확인에서 현재 문서 세트 `d98c326c-fd8a-4457-8343-f03810bd1c7f`는 접속 설정이 없는 새 세트였고, 앞선 653개 TC 검증 세트와 달랐다. 연결 요청은 인증서 예외 false로 전송되어 실패했다. `/playwright-setup/tls-policy`에서 같은 회원의 과거 허용·연결 완료 기록을 조회해 정확히 같은 사이트(scheme/host/port)의 예외를 재사용하도록 수정했다. 연결 팝업에 예외 확인·변경 체크박스를 추가했다. 다른 회원·호스트·포트·프로토콜에 확장되지 않는 격리 DB 회귀와 UI 회귀를 통과했고, 실제 현재 세트에서 새 설정 저장 없이 HTTP 200을 확인했다. 기록 화면은 `output/pipeline-audit/current-set-tls-success.png`다. 이 정책은 연결 검사이며 로그인 설정 자체를 생성하지 않는다.

## 발견한 원인과 수정

| 문제 | 수정 및 확인 |
| --- | --- |
| 한 번 방문한 탭을 숨긴 채 유지해, 이후 다른 탭에서 만든 TC·실행이 표시되지 않음 | 02~07 탭 재진입 시 해당 API 재조회. 저장 전 설정·메뉴 편집은 보존. 같은 TC의 검색·페이지·상세도 유지 |
| 이전 TC 세트를 선택한 상태에서 새 TC를 만들어도 실행 탭은 이전 선택 유지 | TC 생성 응답의 setId를 같은 문서 세트·회원의 실행 탭에 전달. 서버 기본 최신 조회와 명시적 새 생성 선택을 구분 |
| 실행 API 하나가 실패하면 함께 요청한 정상 TC 목록도 사라짐 | TC·실행 이력·접속 설정을 독립 조회. 실행 API 장애 때 TC는 표시하고 준비·시작은 차단. TC 조회 실패 시 이전 TC로 준비하지 않음 |
| AI 컨테이너가 과거 worktree를 마운트해 시험 실행 API가 404 | 현재 작업 폴더로 마운트·재생성. TMS·AI·crawler·Gateway를 현재 구현으로 반영 |
| 실행기·판정 연결 여부를 실제 확인 없이 true로 응답 | 내부 인증을 거친 프로토콜·로컬 모델 설치·Chromium 실행 파일·Jev 설정 확인. 준비되지 않으면 원인 표시 및 시작 API 503. 루트 health만으로 준비 판정하지 않음 |
| Docker 빌드는 npm install 최신 버전, 실행은 호스트 node_modules여서 Chromium 불일치 | lock 기반 npm ci, node_modules 빌드 제외, 소스 파일만 마운트해 이미지의 Linux 패키지와 Chromium 사용. 컨테이너 실제 launch 확인 |
| 수집은 무조건 인증서 예외, 실행은 예외 설정 전달 불가 | 접속 설정에 ignoreHTTPSErrors 저장. 연결 검사·수집·시험 실행에서 같은 설정 사용. 새 설정은 기본 false. 사용자가 이미 허용한 NeoFlow 개발 서버에 true 저장, 기존 암호문 유지 |
| 실행 URL과 저장한 로그인 대상이 다른 경우 시작 후에야 실패 | 시작 API에서 대상 일치·설정 유효성·자동 로그인 지원 여부 확인 |
| 서버 재생성으로 IP가 바뀌었으나 Gateway는 이전 IP로 계속 연결 | Netty DNS 캐시 최대 5초, 음성 캐시 0초로 설정. Gateway 반영 후 실제 API 경유 재확인 |
| 검토 탭의 재시험 응답을 화면에 반영하지 않음 | 새 실행·이력을 즉시 표시. 05 재진입 시 준비된 재시험 조회. 검토 중 사유는 탭 왕복 시 보존 |

TC 업로드 이후 생성 API 누락을 막는 기존 bootJar 검사에 실행 controller·worker·검토·준비 상태 클래스 검사도 추가했다. 기존 TC 원본과 실행·판정 이력은 유지한다.

## 검증 범위

- TMS 전체 자동 테스트 106건: 격리 DB `autotest_test`, 실패·생략 0. 실제 XLSX 메뉴 업로드 → 승인 메뉴 조회 → TC 생성 → 다운로드 → 실행 준비·시작·중단 → 워커 결과 저장 → 사용자 검토 → 재시험·보고서, 소유권·원본 변경·이전 attempt 차단 포함. 이 테스트의 AI 응답은 대역이다.
- Python 실행·증거 회귀 10건, 실제 브라우저를 사용하는 crawler/원격 실행기 테스트 15건 통과. 인증서 예외 미허용 거절·허용 시 연결/로그인/수집도 확인.
- 프런트 빌드와 브라우저 회귀 4개: 문서/수집/메뉴, 메뉴 업로드/TC, 실행/검토/재시험, 소요 시간/상태/이어가기. 실행 회귀에는 탭을 먼저 열어 둔 뒤 생성, 기존 선택 뒤 새 세트 생성, 부분 API 장애, 저장된 TLS 설정 전달을 포함한다.
- 실제 Gateway의 문서·접속 설정·수집·메뉴·TC·실행 조회 6개 모두 200. 실제 NeoFlow 문서 세트의 **653개 TC**, 연결 검사 HTTP 200, 실행 화면 세트 표시 및 준비 버튼 활성화를 확인했다. 제품 전체 TC는 시작하지 않았다.
- 실제 로컬 Qwen → crawler API/Chromium 클릭 → Jev 호출을 임시 합성 조회 화면에서 수행했다. 클릭 1회, 계획 1회, 증거 3건, 기대 문구 검사 PASS. Jev 제안은 P/0.88이며 기준 0.9 미만이어서 **최종 미판정**으로 보존했다. 연계 성공과 자동 P 확정 품질을 구분한다. 운영 DB 실행 이력을 만들거나 실제 제품 데이터를 변경하지 않는 별도 구성요소 검사다.

기록: `output/pipeline-audit/runtime.json`, `live-smoke.json`, `live-execution-ready.png`. 실제 NeoFlow 이전 조회 두 건의 미판정 기록은 `output/test-execution/neoflow-readonly.json`을 유지한다.

## 재현

```powershell
.\gradlew.bat :test-management-service:test :test-management-service:bootJar :api-gateway:bootJar
npm --prefix frontend run build
node scripts/verify-frontend.cjs
node scripts/verify-menu-tc-ui.cjs
node scripts/verify-test-execution-ui.cjs
node scripts/verify-run-progress.cjs
node scripts/verify-pipeline-runtime.cjs <문서-세트-UUID>
```

마지막 명령은 조회 전용이다. `--connection`을 추가하면 저장된 대상에 로그인 없는 연결 검사를 실제 수행한다. `scripts/verify-execution-live.py`는 AI 컨테이너에서만 실행하는 선택 검사이며 실제 Qwen/Jev를 호출한다. 기본 성공 조건은 수행과 판정 응답까지의 연계다. `--require-pass`를 추가하면 자동 P까지 요구하며, 위 미판정 사례는 이 기준에 실패한다.

서버 반영 전 운영 DB와 기존 TMS·AI·crawler 이미지를 백업했다. DB 스키마 변경·운영 데이터 초기화·.env 수정은 하지 않았다. 다음 반영도 `C:\Users\tta\autoTest`의 Compose 경로를 명시하고 JAR 빌드 → 관련 서비스 반영 → Gateway와 실제 UI 검사를 진행한다. 기존 worktree의 Compose 명령이나 root health 성공을 배포 완료 증거로 사용하지 않는다.

## 다음 개선 제안

1. **TC 수행 명세 구조화**: 전제·시험 대상·입력·조작·기대 조건·관찰 방법을 분리한다. 기능 설명을 출력 문구로 잘못 해석하는 계획과 불필요한 미판정을 줄이는 우선 과제다. 신뢰도 기준을 낮춰 통과 수를 늘리지 않는다.
2. **전체 작업 맥락 통합**: 문서·메뉴·TC·실행 ID와 버전을 공통 맥락으로 관리하고 각 화면에 현재 연결을 표시한다. 이번에는 새 TC 선택 전달을 연결했으며, 나머지 명시적 이력 선택은 유지한다.
3. **배포와 계약 자동 검사**: Gateway 경유 기본 검사를 배포 완료 조건에 포함하고 OpenAPI 기반 타입/응답 계약을 공유한다. 배포된 빌드 버전과 서비스 프로토콜을 화면에서 확인하도록 확장한다.
4. **판정 품질 회귀 집합**: 정상·결함 주입·대상 오류·부분 수행·필요 파일 누락의 고정 집합으로 F→P 오판과 미판정 비율을 따로 측정한다. 현재 Jev 준비 검사는 설정 존재만 확인하며 외부 서비스 응답·판정 품질은 실제 호출로 검증해야 한다.

아직 검증하지 않은 전체 제품의 자동 시험 품질, TC 직접 편집/최종 승인, 사전 파일·폴더 공급, 기업용 보고서 서식, 실제 사용자 인증·권한 체계는 완료로 표시하지 않는다.
