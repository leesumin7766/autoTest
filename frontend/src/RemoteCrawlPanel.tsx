import { browserCheckMessage } from './browserCheckMessages'
import { CapturedMenuTreePanel } from './CapturedMenuTreePanel'
import { RunProgress } from './RunProgress'
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react'

type Agent = { agentId: string; name: string; online: boolean; busy: boolean }
export type BrowserCheck = { jobId: string; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELED'
  agentName: string; pageCount: number; errorCode: string | null; stopReason: string | null
  result?: { httpStatus: number; elapsedMs: number; loginVerified: boolean } }
type Job = BrowserCheck & { createdAt?: string; updatedAt?: string; elapsedMs?: number; resumeAvailable?: boolean; crawlResult?: { skipped?: {path:string[];stage:string;reason:string}[]; resumedFrom?: string; preservedCount?: number; unresolvedCount?: number; progress?: {stage: string; current: string; remaining: number}; incompleteCount: number; navigationFailureCount: number; noChangeCount: number; uniqueScreenCount: number } }
type Page = { pageIndex: number; url: string; title: string; screenState?: { screenKey?: string; readiness?: string; frames?: unknown[] }; transition?: { menuKey?: string; menuPath?: string[]; outcome?: string } }
type View = { job: Job | null; pages: Page[]; checks?: Partial<Record<CheckType, BrowserCheck>> }
export type CheckType = 'CONNECTION' | 'LOGIN'
export type BrowserExecutionHandle = { runCheck: (type: CheckType, draft: unknown) => Promise<void>; invalidate: (type: 'ALL' | 'LOGIN') => void }
type Props = { submissionId: string; memberId: string; canStart: boolean; mode: 'LOCAL' | 'REMOTE'
  onChecks: (checks: Partial<Record<CheckType, BrowserCheck>>) => void; onBusyChange: (busy: boolean) => void }
const stopMessages: Record<string, string> = { PAGE_LIMIT: '최대 화면 수 도달', DEPTH_LIMIT: '탐색 깊이 도달',
  TIME_LIMIT: '최대 시간 도달', STORAGE_LIMIT: '저장 한도 도달', DONE: '탐색 완료' }
type CrawlClock = { start: number; end?: string }
function storedClock(job: string): CrawlClock | undefined {
  try { const clock=JSON.parse(sessionStorage.getItem(`autotest:crawl-clock:${job}`) || 'null');return clock && Number.isFinite(clock.start) && (!clock.end || Number.isFinite(Date.parse(clock.end))) ? clock : undefined } catch { return undefined }
}
function storeClock(job: string, clock: CrawlClock) { try { sessionStorage.setItem(`autotest:crawl-clock:${job}`,JSON.stringify(clock)) } catch { /* The in-memory clock works when storage is disabled. */ } }

export const RemoteCrawlPanel = forwardRef<BrowserExecutionHandle, Props>(function RemoteCrawlPanel({ submissionId, memberId, canStart, mode, onChecks, onBusyChange }, ref) {
  const [agents, setAgents] = useState<Agent[]>([])
  const [selected, setSelected] = useState('')
  const [name, setName] = useState('')
  const [serverUrl, setServerUrl] = useState(['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname) ? '' : window.location.origin)
  const [registration, setRegistration] = useState<{ agentId: string; token: string } | null>(null)
  const [view, updateView] = useState<View>({ job: null, pages: [] })
  const [busy, setBusy] = useState(false)
  const [receivedAt, setReceivedAt] = useState(Date.now())
  const [startingAt, setStartingAt] = useState<number | null>(null)
  const starts = useRef(new Map<string, CrawlClock>())
  const setView = useCallback((next: View) => { setReceivedAt(Date.now()); updateView(next) }, [])
  const [error, setError] = useState('')
  const [image, setImage] = useState('')
  const [evidence, setEvidence] = useState('')
  const [pageTitle, setPageTitle] = useState('')
  const preview = useRef<HTMLDialogElement>(null)
  const base = `/api/submissions/${submissionId}/playwright-crawls`
  const activeJob = [view.job, ...Object.values(view.checks || {})].find(job => job?.status === 'QUEUED' || job?.status === 'RUNNING')
  const active = Boolean(activeJob)
  const confirmedPages = view.pages.filter(page => page.screenState?.readiness === 'READY' && page.transition?.outcome === 'CONFIRMED')
  const diagnosticPages = view.pages.filter(page => page.screenState?.readiness === 'LOAD_TIMEOUT' || ['LOAD_TIMEOUT','NO_STATE_CHANGE'].includes(page.transition?.outcome || ''))
  const savedCount = Math.max(view.job?.pageCount || 0,view.pages.length)
  const unknownCount = savedCount-confirmedPages.length-diagnosticPages.length
  const uniqueCount = new Set(confirmedPages.map(page => page.screenState?.screenKey || page.pageIndex)).size
  const duplicateCount = confirmedPages.length-uniqueCount
  const jobActive = !!view.job && ['QUEUED','RUNNING'].includes(view.job.status)
  const normalEnd = view.job?.status === 'COMPLETED' && view.job.stopReason === 'DONE'
    && !view.job.crawlResult?.navigationFailureCount && !view.job.crawlResult?.incompleteCount && !view.job.crawlResult?.noChangeCount && !view.job.crawlResult?.unresolvedCount
  const incompleteEnd = view.job?.status === 'COMPLETED' && view.job.stopReason === 'DONE' && !normalEnd
  if(view.job && !starts.current.has(view.job.jobId)){const saved=storedClock(view.job.jobId);if(saved)starts.current.set(view.job.jobId,saved)}
  const clock = view.job ? starts.current.get(view.job.jobId) : undefined
  if(clock && !jobActive && !clock.end){clock.end = new Date().toISOString();storeClock(view.job!.jobId,clock)}
  const issued = useRef<Partial<Record<CheckType, { id: string }>>>({})
  const online = agents.filter(agent => agent.online && !agent.busy)
  const request = useCallback(async (url: string, options?: RequestInit) => {
    const response = await fetch(url, { ...options, headers: { 'X-Member-Id': memberId,
      ...(options?.body ? { 'Content-Type': 'application/json' } : {}), ...options?.headers } })
    let body
    try { body = await response.json() } catch { throw new Error('서비스에 다시 연결하고 있습니다. 잠시 후 다시 시도해 주세요.') }
    if (!response.ok) throw new Error(body.message || '원격 실행 요청을 처리하지 못했습니다.')
    return body
  }, [memberId])
  const refresh = useCallback(async (signal?: AbortSignal) => {
    const [list, status] = await Promise.all([request('/api/crawl-agents', { signal }), request(base, { signal })])
    if (!signal?.aborted) {
      setAgents(list.agents); setView(status); setError('')
      const checks: Partial<Record<CheckType, BrowserCheck>> = {}
      for (const type of ['CONNECTION', 'LOGIN'] as const) if (issued.current[type]?.id === status.checks?.[type]?.jobId && issued.current[type]) checks[type] = status.checks[type]
      onChecks(checks)
    }
  }, [request, base, onChecks])
  useEffect(() => { onBusyChange(active || busy) }, [active, busy, onBusyChange])
  useImperativeHandle(ref, () => ({ invalidate: type => {
    if (type === 'ALL') issued.current = {}; else delete issued.current.LOGIN
  }, runCheck: async (type, draft) => {
    if (active || busy) throw new Error('진행 중인 테스트가 끝난 뒤 다시 시도해 주세요.')
    setBusy(true)
    try {
      const status = await request(base, { method: 'POST', body: JSON.stringify({
        agentId: mode === 'REMOTE' ? selected || null : null, executionMode: mode, purpose: type, draft,
      }) })
      const check = status.checks[type] as BrowserCheck
      issued.current[type] = { id: check.jobId }; setView(status)
      const checks: Partial<Record<CheckType, BrowserCheck>> = {}
      for (const key of ['CONNECTION', 'LOGIN'] as const) if (issued.current[key] && issued.current[key]?.id === status.checks?.[key]?.jobId) checks[key] = status.checks[key]
      onChecks(checks)
    } finally { setBusy(false) }
  } }), [active, busy, base, mode, selected, request, onChecks])
  useEffect(() => {
    const controller = new AbortController()
    void refresh(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message) })
    const timer = window.setInterval(() => {
      void refresh(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message) })
    }, 5000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [refresh])
  useEffect(() => () => { if (image) URL.revokeObjectURL(image) }, [image])

  const action = async (perform: () => Promise<void>) => {
    if (busy) return
    setBusy(true); setError('')
    try { await perform() } catch (failure) { setError(failure instanceof Error ? failure.message : '요청 실패') }
    finally { setBusy(false) }
  }
  const register = () => action(async () => {
    const url = new URL(serverUrl)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== ''))
      throw new Error('실행 PC가 접속할 관리 서버의 주소와 포트만 입력해 주세요.')
    const created = await request('/api/crawl-agents', { method: 'POST', body: JSON.stringify({ name }) })
    setRegistration({ agentId: created.agent.agentId, token: created.token }); setSelected(created.agent.agentId)
    await refresh()
  })
  const downloadConfig = () => {
    if (!registration) return
    const url = URL.createObjectURL(new Blob([JSON.stringify({ serverUrl: new URL(serverUrl).origin, ...registration }, null, 2)], { type: 'application/json' }))
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'agent-config.json'; anchor.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  const showPage = (page: Page) => action(async () => {
    const endpoint = `${base}/${view.job!.jobId}/pages/${page.pageIndex}`
    const [response, details] = await Promise.all([
      fetch(`${endpoint}/screenshot`, { headers: { 'X-Member-Id': memberId } }), request(endpoint),
    ])
    if (!response.ok) throw new Error('수집 화면을 불러오지 못했습니다.')
    setImage(URL.createObjectURL(await response.blob())); setEvidence(details.bodyText); setPageTitle(page.title || page.url)
    preview.current?.showModal()
  })

  return <section className="remote-crawl-panel" aria-label="원격 화면 수집">
    <h3>{mode === 'REMOTE' ? '원격 실행 옵션 · 화면 수집' : '화면 수집 시작'}</h3>
    <p>{mode === 'REMOTE' ? '원격 실행 PC에서도 위의 연결 테스트·로그인 테스트 버튼으로 같은 검사를 수행합니다.' : '현재 서버의 Playwright로 실행합니다. 별도 실행기 등록이나 설치가 필요하지 않습니다.'}</p>
    {mode === 'REMOTE' && <>
    <details className="playwright-advanced"><summary>실행 PC 최초 등록 · 설치 방법</summary>
      <p>대상 제품에 접속 가능한 PC(예: 210.96.71.205)에 실행기를 설치하세요. 해당 PC는 관리 서버(예: 210.96.71.206:8080)에 접속할 수 있어야 합니다.</p>
      <label>실행 PC 이름<input maxLength={160} value={name} placeholder="예: 시험실 PC · 210.96.71.205" onChange={event => setName(event.target.value)} /></label>
      <label>실행 PC가 접속할 관리 서버 주소<input type="url" value={serverUrl} disabled={Boolean(registration)}
        placeholder="http://210.96.71.206:8080" onChange={event => setServerUrl(event.target.value)} /></label>
      <small>localhost는 실행 PC 자신을 가리킵니다. 다른 PC에 설치한다면 관리 서버의 실제 IP와 포트를 입력하세요.</small>
      <button type="button" className="quiet-button" disabled={busy || !name.trim() || !serverUrl.trim() || Boolean(registration)} onClick={() => void register()}>실행기 등록</button>
      {registration && <div className="playwright-ready" role="status">
        <p>실행기를 등록했습니다. 연결 설정 파일은 지금 다운로드해 보관하세요. 등록 키는 재조회할 수 없습니다.</p>
        <button type="button" className="quiet-button" onClick={downloadConfig}>agent-config.json 다운로드</button>
        <button type="button" className="text-button" onClick={() => setRegistration(null)}>다운로드 완료</button>
      </div>}
      <p><a href="/downloads/autotest-crawl-agent.zip" download>Windows 실행기 ZIP 다운로드</a></p>
      <ol><li>실행기 ZIP을 실행 PC에 압축 해제합니다.</li>
        <li>다운로드한 agent-config.json을 실행기 폴더에 넣습니다.</li>
        <li>Windows PowerShell에서 remote-agent/Install.ps1을 한 번 실행한 뒤 remote-agent/Start-Agent.ps1을 실행합니다.</li>
        <li>아래 실행 PC가 ‘연결됨’으로 바뀌면 수집을 시작합니다.</li></ol>
      <small>설정 파일에는 실행기 등록 키가 포함됩니다. 제품 비밀번호는 파일에 포함하지 않으며, 작업할 때 암호화된 연결로 전달합니다.</small>
    </details>
    {agents.length === 0 ? <p role="status">등록된 실행 PC가 없습니다. 최초 등록 후 실행기를 켜 주세요.</p> : <>
      <label>화면 수집을 실행할 PC<select value={selected} disabled={active || busy} onChange={event => { issued.current = {}; onChecks({}); setSelected(event.target.value) }}>
        <option value="">{online.length === 1 ? `자동 선택 · ${online[0].name}` : '실행 PC 선택'}</option>
        {agents.map(agent => <option key={agent.agentId} value={agent.agentId} disabled={!agent.online || agent.busy}>
          {agent.name} · {agent.online ? agent.busy ? '작업 중' : '연결됨' : '연결 안 됨'}</option>)}
      </select></label>
      <details><summary>실행 PC 연결 관리</summary>{agents.map(agent => <p key={agent.agentId}>{agent.name} · {agent.online ? '연결됨' : '연결 안 됨'}{' '}
        <button type="button" className="text-button" disabled={busy} onClick={() => void action(async () => {
          await request(`/api/crawl-agents/${agent.agentId}/revoke`, { method: 'POST' });
          if (selected === agent.agentId) setSelected(''); if (registration?.agentId === agent.agentId) setRegistration(null); await refresh()
        })}>연결 해제 · 실행 중인 수집 중단</button></p>)}</details>
    </>}
    </>}
    {!canStart && <p>연결·로그인 테스트를 확인하고 설정을 저장한 뒤 수집을 시작해 주세요.</p>}
    <div className="dialog-actions"><button type="button" className="primary-button"
      disabled={busy || active || !canStart || (mode === 'REMOTE' && ((!selected && online.length !== 1) || Boolean(selected && !online.some(agent => agent.agentId === selected))))}
      onClick={() => void action(async () => { const clicked=Date.now();setStartingAt(clicked);try{const next=await request(base, { method: 'POST', body: JSON.stringify({ agentId: mode === 'REMOTE' ? selected || null : null, executionMode: mode, purpose: 'CRAWL' }) });if(next.job){const clock={start:clicked};starts.current.set(next.job.jobId,clock);storeClock(next.job.jobId,clock);}setView(next);}finally{setStartingAt(null)}await refresh() })}>
      {busy ? '요청 중…' : '로그인하고 화면 수집 시작'}</button>
      {active && <button type="button" className="quiet-button" disabled={busy} onClick={() => void action(async () => {
        setView(await request(`${base}/${activeJob!.jobId}/cancel`, { method: 'POST' })); await refresh()
      })}>수집 중단</button>}
    </div>
    <p className="playwright-description">수집은 메뉴와 화면 조회를 중심으로 진행합니다. 로그인 이후 데이터 입력·삭제·결제 폼을 제출하지 않습니다. 메뉴트리 화면에서 기능리스트·매뉴얼과 함께 분석할 수 있습니다.</p>
    {view.job && <p role="status">{view.job.agentName} · {({ QUEUED: '실행기 작업 수신 대기', RUNNING: '화면 수집 중', COMPLETED: '수집 완료', FAILED: '수집 실패', CANCELED: '수집 중단' })[view.job.status]}
      {' · '}{view.job.pageCount}개 화면{view.job.stopReason && ` · ${stopMessages[view.job.stopReason] || view.job.stopReason}`}</p>}
    {startingAt !== null && <RunProgress taskKey={`request-${startingAt}`} startedAt={startingAt} receivedAt={startingAt} title="화면 수집 요청 중" active completed={0} current="실행기에 수집을 요청하고 있습니다." metrics={[]} />}
    {view.job && startingAt === null && <RunProgress taskKey={view.job.jobId} startedAt={clock?.start ?? view.job.createdAt} endedAt={clock?.end ?? view.job.updatedAt}
      tone={jobActive ? 'active' : normalEnd ? 'success' : 'danger'}
      title={jobActive ? view.job.status === 'QUEUED' ? '수집 실행기 대기' : '화면 수집 진행 중' : normalEnd ? '탐색 정상 종료' : incompleteEnd ? '수집 종료 · 미완료 항목 있음' : view.job.status === 'CANCELED' ? '수집 중단 · 결과 보존됨' : view.job.status === 'FAILED' ? '수집 오류 · 결과 보존됨' : '수집 중지 · 한도 도달'}
      active={jobActive} elapsedMs={view.job.elapsedMs} receivedAt={receivedAt} completed={savedCount}
      current={jobActive ? `${({CONNECTING:'브라우저 접속',LOGIN:'로그인 확인',DISCOVERING:'메뉴 탐색',NAVIGATING:'메뉴 이동',LOADING:'화면 로딩 대기',CAPTURING:'화면·근거 저장'} as Record<string,string>)[view.job.crawlResult?.progress?.stage || 'CONNECTING']} · ${view.job.crawlResult?.progress?.current || '실행기 연결 확인 중'}` : view.job.status==='FAILED' ? `중단 사유: ${view.job.errorCode==='CRAWL_FAILED'?'수집 중 예외가 발생했습니다. 현재 기록에는 구체적인 원인이 없습니다.':browserCheckMessage(view.job.errorCode)} (${view.job.errorCode || '원인 코드 없음'})${view.job.crawlResult?.progress?.current?` · 마지막 진행 위치: ${view.job.crawlResult.progress.current}`:''}` : view.job.status==='CANCELED' ? '수집 중단 요청으로 종료했습니다. 저장된 화면은 보존됩니다.' : stopMessages[view.job.stopReason || ''] || '종료 사유가 기록되지 않았습니다.'}
      metrics={[{label:'저장 캡처',value:savedCount},{label:'정상 확인 캡처',value:confirmedPages.length,tone:'success'},{label:'진단 캡처',value:diagnosticPages.length,tone:'warning'},{label:'상태 정보 없는 캡처',value:unknownCount}]}
      note={`저장 ${savedCount} = 정상 확인 ${confirmedPages.length} + 진단 ${diagnosticPages.length} + 상태 정보 없음 ${unknownCount}. 정상 확인 중 고유 화면 ${uniqueCount}개 · 동일 상태 재캡처 ${duplicateCount}개. 캡처 전 이동 실패 ${view.job.crawlResult?.navigationFailureCount || 0}건은 저장 수와 별도입니다.${jobActive ? ` 탐색 대기 ${view.job.crawlResult?.progress?.remaining || 0}건.` : ''}${view.job.crawlResult?.resumedFrom ? ` 이전 화면 ${view.job.crawlResult.preservedCount || 0}개 보존 · 중단 대기 시간을 제외한 누적 시간입니다.` : ''}`} />}
    {view.job?.resumeAvailable && <div className="dialog-actions"><button type="button" className="primary-button" disabled={busy || active || (mode === 'REMOTE' && !online.length)} onClick={() => void action(async () => {const clicked=Date.now();setStartingAt(clicked);try{const next=await request(`${base}/${view.job!.jobId}/resume`, {method:'POST',body:JSON.stringify({agentId:mode==='REMOTE'?selected || null:null,executionMode:mode})});if(next.job){const clock={start:clicked};starts.current.set(next.job.jobId,clock);storeClock(next.job.jobId,clock);}setView(next);}finally{setStartingAt(null)}await refresh()})}>미완료 화면 이어서 수집</button><span>저장한 화면을 보존하고 새 작업으로 이어갑니다.</span></div>}
    {view.job?.errorCode && <p className="error-copy" role="alert">{browserCheckMessage(view.job.errorCode)}</p>}
    {!!view.job?.crawlResult?.skipped?.length&&<details open><summary>수집하지 못한 메뉴·사유 ({view.job.crawlResult.skipped.length}건)</summary><ul>{view.job.crawlResult.skipped.map((item,index)=><li key={index}>{item.path.join(' → ') || '시작 화면'} · {({CAPTURE_CONTEXT_CHANGED:'캡처 중 화면이 전환됨 · 재수집 필요',CAPTURE_TIMEOUT:'스크린샷 저장 대기 시간 초과',BROWSER_ERROR_PAGE:'브라우저 오류 페이지로 이동함',CONNECTION_SCOPE_BLOCKED:'허용된 수집 주소 범위를 벗어남',SOURCE_LOAD_TIMEOUT:'이동 전 화면의 로딩이 완료되지 않음',NAVIGATION_NOT_AVAILABLE:'이동할 메뉴 요소를 찾지 못함',CLICK_TIMEOUT:'메뉴 클릭 대기 시간 초과',NAVIGATION_FAILED:'화면 이동 실패',ERR_BLOCKED_BY_CLIENT:'실행기의 네트워크 정책에 의해 요청 차단',ERR_ABORTED:'브라우저가 화면 이동을 중단함',ERR_CONNECTION_RESET:'대상 서버 연결이 끊김',ERR_CONNECTION_REFUSED:'대상 서버가 연결을 거부함',ERR_TIMED_OUT:'대상 서버 응답 시간 초과'} as Record<string,string>)[item.reason] || item.reason} ({item.reason}) · 단계: {item.stage}</li>)}</ul></details>}
    {view.job?.status === 'COMPLETED' && view.job.crawlResult && (view.job.crawlResult.navigationFailureCount > 0 || view.job.crawlResult.incompleteCount > 0 || view.job.crawlResult.noChangeCount > 0 || view.job.stopReason !== 'DONE') &&
      <p className="error-copy" role="status">수집이 종료됐지만 전체 메뉴 확인은 완료되지 않았습니다. 실패·미방문 항목을 검토한 후 메뉴트리를 작성하세요.</p>}
    {view.job && view.pages.length > 0 && <CapturedMenuTreePanel key={view.job.jobId} base={base} jobId={view.job.jobId}
      memberId={memberId} busy={busy} showPage={showPage} />}
    {view.pages.length > 0 && <details><summary>수집한 화면 · 근거 보기 ({view.pages.length}개)</summary><ul className="crawl-page-list">{view.pages.map(page => <li key={page.pageIndex}>
      <button type="button" disabled={busy} onClick={() => void showPage(page)}>{page.pageIndex + 1}. {page.title || '제목 없음'} · 화면·근거 보기</button>
      <small>{page.transition?.menuPath?.join(' → ') || '시작 화면'} · {page.screenState?.readiness === 'LOAD_TIMEOUT' ? '로딩 미완료 · 진단 캡처'
        : page.transition?.outcome === 'NO_STATE_CHANGE' ? '클릭 후 변화 미확인'
        : page.screenState?.readiness === 'READY' ? '로딩 완료 확인' : '이전 수집 · 상태 근거 없음'}</small>
      <small>{page.url}</small></li>)}</ul></details>}
    {error && <p className="error-copy" role="alert">{error}</p>}
    <dialog ref={preview} className="crawl-preview"><h3>{pageTitle}</h3>
      <button type="button" className="quiet-button" onClick={() => preview.current?.close()}>닫기</button>
      {image && <img src={image} alt={pageTitle} />}<details><summary>수집된 화면 텍스트</summary><pre>{evidence}</pre></details></dialog>
  </section>
})

