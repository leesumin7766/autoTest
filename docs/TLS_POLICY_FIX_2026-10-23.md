# 화면 수집 인증서 예외 누락 수정

## 원인

새 문서 세트 cecf938f-931f-4fa1-a443-cc31f1734a8b의 연결 테스트 두 건이 CERTIFICATE_ERROR로 실패했다. 실제 화면 수집 CRAWL 이전 단계이며 두 작업의 automation.ignoreHTTPSErrors는 false였다. 동일 회원이 동일 HTTPS origin에 대해 완료한 기존 작업에는 true가 저장되어 있었다.

5번 ExecutionConnectionTest는 기존 tls-policy API를 조회하지만 2번 PlaywrightSetupPanel은 신규 세트의 기본 false를 그대로 보냈다. 이전 수정에서 화면 수집 쪽 연결을 누락했다. Playwright 자체의 예외 설정 지원이 사라진 문제가 아니다.

## 수정

- 2번도 회원·정확한 origin(스킴/호스트/포트)에 한정된 동일 정책 API를 조회한다. 현재 세트에 저장한 명시적인 false는 기존 서버 정책대로 존중한다.
- URL 변경 시 이전 요청을 취소하며, 다른 origin으로 변경하면 이전 예외를 즉시 지운다. 조회 완료 전 연결·로그인·저장·수집을 시작하지 않는다.
- 예외 체크박스와 적용 상태를 연결 테스트 옆으로 이동했다. 연결 테스트·로그인·저장이 동일 draft.automation을 사용한다.
- 정책 조회 실패는 표시하고, 사용자가 체크박스로 직접 설정을 확인할 수 있게 한다. 모든 사이트의 검증을 전역 해제하지 않는다.

## 검증

- UI 회귀 시험에 동일 origin 예외 복원, 다른 origin 예외 초기화, 연결 테스트 요청의 ignoreHTTPSErrors=true 검증을 추가했다.
- 문제가 발생한 실제 새 문서 세트에서 정책 조회 후 연결 테스트 420a7e38-7534-482d-adcc-b34cd701f281을 실행했다. COMPLETED, HTTP 200, errorCode=null이다. 로그인이나 전체 수집을 새로 시작하지 않았다.
- 프런트 TypeScript·Vite 빌드와 화면 수집·설정 저장 UI 흐름을 검증한다.
