const errorMessages: Record<string, string> = {
  LOGIN_FAILED: '로그인에 실패했습니다. 계정과 로그인 성공 기준을 확인해 주세요.',
  LOGIN_CONFIRMATION_REQUIRED: '로그인 성공 URL 또는 확인 요소를 지정해 주세요.',
  LOGIN_METHOD_UNSUPPORTED: '현재 자동 실행은 일반 로그인·Basic 인증·로그인 없음만 지원합니다.',
  TARGET_UNREACHABLE: '실행 PC에서 대상 주소에 접속할 수 없습니다. URL·포트·네트워크를 확인해 주세요.',
  CERTIFICATE_ERROR: '대상 사이트 인증서를 신뢰할 수 없습니다. 실행 PC의 인증서 구성을 확인해 주세요.',
  PAGE_LOAD_TIMEOUT: '설정한 최대 대기 시간 안에 페이지 로딩이 끝나지 않았습니다.',
  AGENT_DISCONNECTED: '실행기 연결이 끊기거나 작업 대기 시간이 만료되었습니다.',
  AGENT_UPDATE_REQUIRED: '이어서 수집하려면 원격 실행기를 새 패키지로 업데이트해 주세요. 기존 화면은 보존되어 있습니다.',
  DOCUMENT_VERSION_MISMATCH: '문서가 변경되었습니다. 검증과 설정 저장을 다시 진행해 주세요.',
  NO_PAGES_CAPTURED: '설정한 범위에서 수집할 화면을 찾지 못했습니다.',
  CAPTURE_TOO_LARGE: '스크린샷이 화면별 저장 한도를 초과했습니다.',
  CRAWL_FAILED: '화면 수집을 완료하지 못했습니다. 로그인 설정과 실행기를 확인해 주세요.',
  CRAWL_LANGUAGE_RESTART_REQUIRED: '이전 수집과 화면 언어가 달라 이어서 수집하지 않았습니다. 기존 화면은 보존됩니다. 새 수집을 시작해 주세요.',
  LOGIN_NOT_CONFIRMED: '로그인 성공을 확실히 확인하지 못했습니다. 고급 설정에서 성공 URL 또는 확인 요소를 지정해 주세요.',
  LOCAL_RUNNER_UNAVAILABLE: '현재 서버의 Playwright 실행기에 연결할 수 없습니다.',
  CONNECTION_SCOPE_BLOCKED: '다른 사이트로 이동하거나 허용 범위를 벗어났습니다. 고급 설정의 허용 사이트를 확인해 주세요.',
}
export const browserCheckMessage = (code: string | null) => errorMessages[code || ''] || '테스트를 완료하지 못했습니다.'

