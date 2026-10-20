import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { PreflightWarningDetails, type PreflightDiagnostics, type PreflightWarning } from './PreflightWarningDetails'

type LoginMethod = 'NONE' | 'MANUAL' | 'SSO' | 'OTHER'
type Setup = {
  targetUrl: string
  loginMethod: LoginMethod
  loginInstructions: string
  testAccountLabel: string
  crawlScopeNotes: string
  testDataNotes: string
  testEnvironmentConfirmed: boolean
  stale: boolean
  status: 'READY_TO_CRAWL'
}
type Preflight = {
  decision: 'BLOCKED' | 'READY_WITH_WARNINGS' | 'READY'
  warnings: PreflightWarning[]
  diagnostics?: PreflightDiagnostics
  generationGate?: { allowed: boolean }
}
type Props = {
  submissionId: string
  memberId: string
  deliveryStatus: string
  preflight: Preflight | null
}

const emptySetup: Setup = {
  targetUrl: '', loginMethod: 'MANUAL', loginInstructions: '', testAccountLabel: '',
  crawlScopeNotes: '', testDataNotes: '', testEnvironmentConfirmed: false,
  stale: false, status: 'READY_TO_CRAWL',
}

export function PlaywrightSetupPanel({ submissionId, memberId, deliveryStatus, preflight }: Props) {
  const [setup, setSetup] = useState<Setup>(emptySetup)
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const ready = deliveryStatus === 'DELIVERED'
    && ['READY', 'READY_WITH_WARNINGS'].includes(preflight?.decision ?? '')
    && preflight?.generationGate?.allowed !== false
  const endpoint = `/api/submissions/${submissionId}/playwright-setup`

  const load = useCallback(async (signal?: AbortSignal) => {
    const response = await fetch(endpoint, { headers: { 'X-Member-Id': memberId, 'Cache-Control': 'no-cache' }, signal })
    const body = await response.json() as { setup?: Setup | null; message?: string }
    if (!response.ok) throw new Error(body.message || 'Playwright 준비 설정을 불러오지 못했습니다.')
    if (signal?.aborted) return
    setSetup(body.setup ? { ...emptySetup, ...body.setup } : emptySetup)
    setLoaded(true)
    setError('')
  }, [endpoint, memberId])

  useEffect(() => {
    if (!ready) { setLoaded(false); return }
    const controller = new AbortController()
    void load(controller.signal).catch((failure: Error) => {
      if (!controller.signal.aborted) { setError(failure.message); setLoaded(true) }
    })
    return () => controller.abort()
  }, [ready, load])

  const update = <K extends keyof Setup>(key: K, value: Setup[K]) =>
    setSetup(current => ({ ...current, [key]: value }))

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!ready || saving) return
    setSaving(true)
    setError('')
    try {
      const response = await fetch(endpoint, {
        method: 'PUT',
        headers: { 'X-Member-Id': memberId, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targetUrl: setup.targetUrl,
          loginMethod: setup.loginMethod,
          loginInstructions: setup.loginInstructions,
          testAccountLabel: setup.testAccountLabel,
          crawlScopeNotes: setup.crawlScopeNotes,
          testDataNotes: setup.testDataNotes,
          testEnvironmentConfirmed: setup.testEnvironmentConfirmed,
        }),
      })
      const body = await response.json() as { setup?: Setup; message?: string }
      if (!response.ok || !body.setup) throw new Error(body.message || 'Playwright 준비 설정을 저장하지 못했습니다.')
      setSetup(body.setup)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Playwright 준비 설정 저장 실패')
    } finally {
      setSaving(false)
    }
  }

  return <section className="playwright-setup" aria-label="Playwright 실행 준비">
    <h2>Playwright 실행 준비</h2>
    <p className="playwright-flow">Jev 사전 검증 <span>→</span> 접속 환경 설정 <span>→</span> 화면 수집 <span>→</span> 메뉴 트리·TC 작성</p>
    {!ready && <div className="playwright-gate" role="status">
      {preflight?.decision === 'BLOCKED'
        ? <><strong>Jev 검증이 차단되어 준비 설정을 열 수 없습니다.</strong><p>문제를 해결하고 사전 검증을 다시 실행해 주세요.</p></>
        : <><strong>Jev 검증이 통과되면 Playwright 접속 환경을 설정할 수 있습니다.</strong><p>현재는 브라우저에 접속하거나 크롤링하지 않습니다.</p></>}
    </div>}
    {ready && <>
      {preflight && <PreflightWarningDetails warnings={preflight.warnings} diagnostics={preflight.diagnostics} />}
      {!loaded && !error && <p role="status">저장된 준비 설정을 확인하고 있습니다…</p>}
      {loaded && <form className="playwright-setup-form" onSubmit={save}>
        <label htmlFor="playwright-target-url">테스트 웹사이트 시작 URL</label>
        <input id="playwright-target-url" type="url" required maxLength={2048} value={setup.targetUrl}
          placeholder="https://test.example.local/" onChange={event => update('targetUrl', event.target.value)} />
        <small>http/https 주소를 입력하세요. 이후 Playwright 컨테이너에서 접속 가능한 주소여야 합니다. 컨테이너의 localhost는 사용자 PC가 아닙니다. URL에 로그인 정보, query parameter 또는 fragment는 넣지 마세요.</small>

        <label htmlFor="playwright-login-method">로그인 방식</label>
        <select id="playwright-login-method" value={setup.loginMethod}
          onChange={event => update('loginMethod', event.target.value as LoginMethod)}>
          <option value="NONE">로그인 필요 없음</option>
          <option value="MANUAL">사용자가 브라우저에서 직접 로그인</option>
          <option value="SSO">SSO 로그인</option>
          <option value="OTHER">기타 방식</option>
        </select>

        <label htmlFor="playwright-login-instructions">로그인 절차 메모</label>
        <textarea id="playwright-login-instructions" maxLength={2000} rows={3} value={setup.loginInstructions}
          placeholder="예: 사내 SSO 페이지에서 테스트 계정으로 로그인" onChange={event => update('loginInstructions', event.target.value)} />
        <small>비밀번호·쿠키·토큰은 입력하지 마세요. 브라우저 실행 단계에서 사용자가 직접 로그인하는 방식으로 준비합니다.</small>

        <label htmlFor="playwright-account-label">테스트 계정 식별명과 권한</label>
        <input id="playwright-account-label" maxLength={160} value={setup.testAccountLabel}
          placeholder="예: QA 관리자 계정 (관리자 권한)" onChange={event => update('testAccountLabel', event.target.value)} />

        <label htmlFor="playwright-scope">수집할 메뉴·화면과 제외 범위</label>
        <textarea id="playwright-scope" maxLength={4000} rows={4} value={setup.crawlScopeNotes}
          placeholder="예: 대시보드, 작업 관리, 설정 메뉴. 운영/결제 화면은 제외." onChange={event => update('crawlScopeNotes', event.target.value)} />

        <label htmlFor="playwright-data">테스트 데이터 준비 메모</label>
        <textarea id="playwright-data" maxLength={4000} rows={4} value={setup.testDataNotes}
          placeholder="예: 테스트 전용 Job 2건, 성공/실패 데이터 각 1건" onChange={event => update('testDataNotes', event.target.value)} />
        <small>실제 고객 개인정보나 비밀값 대신 테스트 데이터의 종류와 준비 방법을 적으세요.</small>

        <label className="playwright-confirmation">
          <input type="checkbox" checked={setup.testEnvironmentConfirmed}
            onChange={event => update('testEnvironmentConfirmed', event.target.checked)} />
          이 URL은 접속·화면 수집 권한이 있는 테스트 환경입니다.
        </label>
        {setup.stale && <p className="playwright-stale" role="status">문서가 바뀐 뒤의 설정입니다. 현재 Jev 검증 기준으로 다시 저장해 주세요.</p>}
        {setup.targetUrl && !setup.stale && <p className="playwright-ready" role="status">접속 환경 설정이 저장되어 있습니다. 아직 Playwright 브라우저는 실행하지 않았습니다.</p>}
        {error && <p className="error-copy" role="alert">{error}</p>}
        <div className="dialog-actions">
          <button className="primary-button" type="submit" disabled={saving || !setup.targetUrl.trim() || !setup.testEnvironmentConfirmed}>
            {saving ? '설정 저장 중…' : setup.targetUrl && !setup.stale ? '접속 설정 수정 저장' : 'Playwright 실행 환경 저장'}
          </button>
          <span className="playwright-not-run">저장만 수행 · 브라우저 접속/크롤링은 다음 단계</span>
        </div>
      </form>}
    </>}
    {error && !loaded && <p className="error-copy" role="alert">{error} <button className="quiet-button" onClick={() => void load().catch((failure: Error) => setError(failure.message))}>다시 조회</button></p>}
  </section>
}
