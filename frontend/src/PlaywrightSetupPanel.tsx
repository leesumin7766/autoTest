import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { PreflightWarningDetails, type PreflightDiagnostics, type PreflightWarning } from './PreflightWarningDetails'

type LoginMethod = 'NONE' | 'FORM_AUTO' | 'BASIC_AUTH' | 'MANUAL' | 'SSO' | 'OTHER'
type Automation = {
  productVersion: string; loginUrl: string; username: string
  usernameSelector: string; passwordSelector: string; submitSelector: string
  successUrl: string; successSelector: string; allowedOrigins: string[]
  includePaths: string[]; excludePaths: string[]; maxPages: number; maxDepth: number
  maxDurationSeconds: number; viewportWidth: number; viewportHeight: number
}
type Setup = {
  targetUrl: string; loginMethod: LoginMethod; loginInstructions: string; testAccountLabel: string
  crawlScopeNotes: string; testDataNotes: string; testEnvironmentConfirmed: boolean
  stale: boolean; status: string; automation: Automation; passwordConfigured: boolean
}
type Preflight = {
  decision: 'BLOCKED' | 'READY_WITH_WARNINGS' | 'READY'; warnings: PreflightWarning[]
  diagnostics?: PreflightDiagnostics; generationGate?: { allowed: boolean }
}
type Props = { submissionId: string; memberId: string; deliveryStatus: string; preflight: Preflight | null }
const automationDefaults: Automation = {
  productVersion: '', loginUrl: '', username: '', usernameSelector: '', passwordSelector: '', submitSelector: '',
  successUrl: '', successSelector: '', allowedOrigins: [], includePaths: [], excludePaths: ['/logout*', '/signout*'],
  maxPages: 100, maxDepth: 5, maxDurationSeconds: 600, viewportWidth: 1440, viewportHeight: 900,
}
const emptySetup: Setup = {
  targetUrl: '', loginMethod: 'FORM_AUTO', loginInstructions: '', testAccountLabel: '', crawlScopeNotes: '',
  testDataNotes: '', testEnvironmentConfirmed: false, stale: false, status: 'CONFIGURED',
  automation: automationDefaults, passwordConfigured: false,
}

export function PlaywrightSetupPanel({ submissionId, memberId, deliveryStatus, preflight }: Props) {
  const [setup, setSetup] = useState<Setup>(emptySetup)
  const [password, setPassword] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const ready = deliveryStatus === 'DELIVERED' && ['READY', 'READY_WITH_WARNINGS'].includes(preflight?.decision ?? '')
    && preflight?.generationGate?.allowed !== false
  const automatic = setup.loginMethod === 'FORM_AUTO' || setup.loginMethod === 'BASIC_AUTH'
  const endpoint = `/api/submissions/${submissionId}/playwright-setup`

  const load = useCallback(async (signal?: AbortSignal) => {
    const response = await fetch(endpoint, { headers: { 'X-Member-Id': memberId, 'Cache-Control': 'no-cache' }, signal })
    const body = await response.json() as { setup?: Setup | null; message?: string }
    if (!response.ok) throw new Error(body.message || 'Playwright 준비 설정을 불러오지 못했습니다.')
    if (signal?.aborted) return
    setSetup(body.setup ? { ...emptySetup, ...body.setup, automation: { ...automationDefaults, ...body.setup.automation } } : emptySetup)
    setPassword(''); setSaved(Boolean(body.setup)); setDirty(false); setLoaded(true); setError('')
  }, [endpoint, memberId])
  useEffect(() => {
    if (!ready) { setLoaded(false); setPassword(''); return }
    const controller = new AbortController()
    void load(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) { setError(failure.message); setLoaded(false) } })
    return () => controller.abort()
  }, [ready, load])

  const update = <K extends keyof Setup>(key: K, value: Setup[K]) => {
    setSetup(current => ({ ...current, [key]: value })); setDirty(true)
  }
  const updateAutomation = <K extends keyof Automation>(key: K, value: Automation[K]) => {
    setSetup(current => ({ ...current, automation: { ...current.automation, [key]: value } })); setDirty(true)
  }
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!ready || saving) return
    setSaving(true); setError('')
    try {
      const response = await fetch(endpoint, {
        method: 'PUT', headers: { 'X-Member-Id': memberId, 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUrl: setup.targetUrl, loginMethod: setup.loginMethod,
          loginInstructions: setup.loginInstructions, testAccountLabel: setup.testAccountLabel,
          crawlScopeNotes: setup.crawlScopeNotes, testDataNotes: setup.testDataNotes,
          testEnvironmentConfirmed: setup.testEnvironmentConfirmed, automation: setup.automation,
          password: automatic ? password : undefined }),
      })
      const body = await response.json() as { setup?: Setup; message?: string }
      if (!response.ok || !body.setup) throw new Error(body.message || '준비 설정을 저장하지 못했습니다.')
      setSetup(body.setup); setPassword(''); setSaved(true); setDirty(false)
    } catch (failure) { setError(failure instanceof Error ? failure.message : '준비 설정 저장 실패') }
    finally { setSaving(false) }
  }
  const config = setup.automation

  return <section className="playwright-setup" aria-label="Playwright 실행 준비">
    <h2>자동 로그인 · 화면 수집 준비</h2>
    <p className="playwright-flow">Jev 문서 검증 <span>→</span> 로그인·수집 설정 <span>→</span> 화면 형상 수집 <span>→</span> 메뉴트리·TC 작성</p>
    <p className="playwright-description">기능리스트·매뉴얼과 실제 화면을 함께 분석할 수 있도록 접속 정보를 준비합니다. 현재는 설정 저장까지 제공하며, 자동 로그인·화면 수집 실행은 다음 단계입니다.</p>
    {!ready && <div className="playwright-gate" role="status"><strong>{preflight?.decision === 'BLOCKED'
      ? 'Jev 검증이 차단되었습니다. 문서를 확인하고 다시 검증해 주세요.' : 'Jev 문서 검증이 통과되면 준비 설정을 입력할 수 있습니다.'}</strong></div>}
    {ready && <>
      {preflight && <PreflightWarningDetails warnings={preflight.warnings} diagnostics={preflight.diagnostics} />}
      {!loaded && !error && <p role="status">저장된 설정을 확인하고 있습니다…</p>}
      {loaded && <form className="playwright-setup-form" onSubmit={save}>
        <fieldset disabled={saving}>
          <legend>1. 접속할 제품</legend>
          <label>화면 수집 시작 URL <input type="url" required maxLength={2048} value={setup.targetUrl}
            placeholder="https://test.example.com/dashboard" onChange={event => update('targetUrl', event.target.value)} /></label>
          <small>로그인 후 수집을 시작할 화면입니다. Playwright 서버에서 접근 가능한 주소를 입력하세요. 사용자 PC의 서비스는 localhost 대신 host.docker.internal을 사용합니다.</small>
          <div className="playwright-grid">
            <label>제품 버전·빌드 <input maxLength={160} value={config.productVersion} placeholder="예: PrintChaser 2.0 · QA 빌드 2026.10"
              onChange={event => updateAutomation('productVersion', event.target.value)} /></label>
            <label>계정 이름·역할 <input maxLength={160} value={setup.testAccountLabel} placeholder="예: QA 관리자 · 관리자 권한"
              onChange={event => update('testAccountLabel', event.target.value)} /></label>
          </div>
        </fieldset>
        <fieldset disabled={saving}>
          <legend>2. 로그인</legend>
          <label>로그인 방식 <select value={setup.loginMethod} onChange={event => {
            update('loginMethod', event.target.value as LoginMethod); setPassword('')
          }}>
            <option value="FORM_AUTO">아이디·비밀번호로 자동 로그인</option>
            <option value="BASIC_AUTH">HTTP Basic 인증 (브라우저 인증창)</option>
            <option value="NONE">로그인 없음</option>
            <option value="SSO">SSO · MFA · CAPTCHA (추가 절차 필요)</option>
            <option value="MANUAL">사용자가 직접 로그인</option>
            <option value="OTHER">기타 로그인</option>
          </select></label>
          {automatic && <>
            {setup.loginMethod === 'FORM_AUTO' && <label>로그인 페이지 URL (선택) <input type="url" maxLength={2048} value={config.loginUrl}
              placeholder="비워두면 시작 URL에서 로그인 화면으로 이동" onChange={event => updateAutomation('loginUrl', event.target.value)} /></label>}
            <div className="playwright-grid">
              <label>로그인 아이디 <input required maxLength={320} autoComplete="off" value={config.username}
                placeholder="테스트 계정 아이디 또는 이메일" onChange={event => updateAutomation('username', event.target.value)} /></label>
              <label>비밀번호 <input type="password" required={!setup.passwordConfigured} maxLength={4096} autoComplete="new-password" value={password}
                placeholder={setup.passwordConfigured ? '저장됨 · 변경할 때만 입력' : '테스트 계정 비밀번호'}
                onChange={event => { setPassword(event.target.value); setDirty(true) }} /></label>
            </div>
            <small>비밀번호는 암호화해 저장하며 조회·문서 분석 결과에는 포함하지 않습니다. 저장된 경우 빈칸이면 기존 값을 유지합니다. 계정이나 로그인 사이트를 바꾸면 다시 입력하세요.</small>
            {setup.loginMethod === 'FORM_AUTO' && <details className="playwright-advanced">
              <summary>고급 로그인 설정 · 자동 인식이 어려운 화면</summary>
              <p>실행 단계에서 입력칸과 버튼을 자동 인식할 예정입니다. 필요한 경우 CSS 선택자로 화면 요소를 지정할 수 있습니다.</p>
              <div className="playwright-grid">{([
                ['usernameSelector', '아이디 입력칸', 'input[name="username"]'],
                ['passwordSelector', '비밀번호 입력칸', 'input[type="password"]'],
                ['submitSelector', '로그인 버튼', 'button[type="submit"]'],
                ['successSelector', '로그인 성공 확인 요소', 'nav[aria-label="메인 메뉴"]'],
                ['successUrl', '로그인 성공 후 URL', 'https://test.example.com/dashboard'],
              ] as const).map(([key, label, hint]) => <label key={key}>{label}<input type={key === 'successUrl' ? 'url' : 'text'}
                maxLength={key === 'successUrl' ? 2048 : 500} value={config[key]} placeholder={`예: ${hint}`}
                onChange={event => updateAutomation(key, event.target.value)} /></label>)}</div>
              <small>성공 URL 또는 요소를 기준으로 로그인 완료를 확인하도록 준비합니다. 여러 화면을 거치는 로그인 절차는 아래 메모에 적어 주세요.</small>
            </details>}
          </>}
          {['SSO', 'MANUAL', 'OTHER'].includes(setup.loginMethod) && <p className="playwright-assisted">이 방식은 사용자 개입 또는 별도 인증 연동이 필요합니다. SSO·MFA·CAPTCHA 자동 통과는 제공하지 않습니다. 이 방식으로 저장하면 이전 비밀번호는 삭제됩니다.</p>}
          {setup.loginMethod === 'NONE' && setup.passwordConfigured && <p>저장하면 기존 로그인 비밀번호를 삭제합니다.</p>}
          <label>추가 로그인 절차 (선택)<textarea rows={2} maxLength={2000} value={setup.loginInstructions}
            placeholder="예: 조직 선택 → 아이디 입력 → 비밀번호 입력. MFA는 담당자 확인 필요."
            onChange={event => update('loginInstructions', event.target.value)} /></label>
          <small>비밀번호는 전용 입력란에만 입력하세요. 메모에 비밀번호·쿠키·토큰을 적지 마세요.</small>
        </fieldset>
        <fieldset disabled={saving}>
          <legend>3. 수집할 화면 범위</legend>
          <label>추가 허용 사이트 (한 줄에 하나)<textarea rows={2} value={config.allowedOrigins.join('\n')} maxLength={40000}
            placeholder={'https://login.example.com\nhttps://admin.example.com:8443'}
            onChange={event => updateAutomation('allowedOrigins', event.target.value.split('\n'))} /></label>
          <small>시작 URL의 사이트는 기본 포함합니다. 다른 로그인 사이트·관리 사이트는 주소와 포트만 추가하세요. URL에는 계정정보·query parameter·fragment를 넣지 마세요.</small>
          <div className="playwright-grid">
            <label>포함 경로 (선택, 한 줄에 하나)<textarea rows={3} value={config.includePaths.join('\n')} maxLength={25000}
              placeholder={'/dashboard*\n/admin/*\n비워두면 허용 사이트 전체'} onChange={event => updateAutomation('includePaths', event.target.value.split('\n'))} /></label>
            <label>제외 경로 (한 줄에 하나)<textarea rows={3} value={config.excludePaths.join('\n')} maxLength={25000}
              placeholder={'/logout*\n/signout*\n/billing/*'} onChange={event => updateAutomation('excludePaths', event.target.value.split('\n'))} /></label>
          </div>
          <label>우선 수집할 메뉴·주의할 화면<textarea rows={2} maxLength={4000} value={setup.crawlScopeNotes}
            placeholder="예: 대시보드 → 작업 관리 → 설정. 삭제·결제·실제 작업 실행은 제외."
            onChange={event => update('crawlScopeNotes', event.target.value)} /></label>
          <details className="playwright-advanced"><summary>수집 한도·화면 크기</summary>
            <div className="playwright-grid">{([
              ['maxPages', '최대 화면 수', 1, 500], ['maxDepth', '최대 탐색 깊이', 1, 10],
              ['maxDurationSeconds', '최대 시간 (초)', 30, 3600], ['viewportWidth', '화면 너비 (px)', 320, 3840],
              ['viewportHeight', '화면 높이 (px)', 320, 2160],
            ] as const).map(([key, label, min, max]) => <label key={key}>{label}<input type="number" required min={min} max={max}
              value={config[key]} onChange={event => updateAutomation(key, Number(event.target.value))} /></label>)}</div>
          </details>
        </fieldset>
        <fieldset disabled={saving}>
          <legend>4. 분석에 사용할 근거</legend>
          <div className="playwright-evidence"><span>기능리스트</span><span>제품 매뉴얼</span><span>수집할 스크린샷·화면 구조·URL</span></div>
          <p>화면별 메뉴 경로와 이동 관계를 문서와 대조해 메뉴트리·TC 작성에 사용합니다. 화면 수집과 분석 연결은 다음 구현 단계입니다.</p>
          <label>테스트 데이터 준비 메모<textarea rows={2} maxLength={4000} value={setup.testDataNotes}
            placeholder="예: 조회 가능한 작업 2건, 관리자 권한 계정, 오류 메시지 확인용 빈 데이터"
            onChange={event => update('testDataNotes', event.target.value)} /></label>
          <label className="playwright-confirmation"><input type="checkbox" required checked={setup.testEnvironmentConfirmed}
            onChange={event => update('testEnvironmentConfirmed', event.target.checked)} />접속·화면 수집 권한이 있는 테스트 환경과 계정입니다.</label>
        </fieldset>
        {setup.stale && <p className="playwright-stale" role="status">문서가 변경되었습니다. 현재 검증 기준으로 설정을 다시 저장해 주세요.</p>}
        {saved && !dirty && !setup.stale && <p className="playwright-ready" role="status">설정을 저장했습니다. 브라우저 실행 전 준비 단계입니다.</p>}
        {dirty && <p role="status">저장하지 않은 변경사항이 있습니다.</p>}
        {error && <p className="error-copy" role="alert">{error}</p>}
        <div className="dialog-actions"><button className="primary-button" type="submit" disabled={saving || !setup.testEnvironmentConfirmed}>
          {saving ? '저장 중…' : '로그인·화면 수집 설정 저장'}</button>
          <span className="playwright-not-run">설정 저장 · 자동 로그인 및 화면 수집 실행은 다음 단계</span></div>
      </form>}
    </>}
    {error && !loaded && <p className="error-copy" role="alert">{error} <button className="quiet-button"
      onClick={() => void load().catch((failure: Error) => setError(failure.message))}>다시 조회</button></p>}
  </section>
}
