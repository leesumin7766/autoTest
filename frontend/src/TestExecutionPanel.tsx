import { apiFetch as fetch } from './apiFetch'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ExecutionConnectionTest } from './ExecutionConnectionTest'
import { RunProgress } from './RunProgress'
import { JevConfidence } from './JevConfidence'
import { useExecutionEvidence, type EvidenceGroup } from './useExecutionEvidence'

// TC source and execution health are loaded independently when this tab opens.

type Tc = { id: string; tcId: string; featureId: string; featurePath: string[]; scenario: string; expectedResult: string; inputs: string }
type TcSet = { setId: string; stale: boolean; output: { productName: string; testCases: Tc[] } }
export type Item = { confidence?: number; judgmentDetails?: Record<string, {choice?: string; confidence?: number}>; judgedBy?: string; caseId: string; tc: Tc; executionStatus: string; judgmentStatus: string; result: 'P' | 'F' | 'N/A' | null; actualResult: string | null; reason: string | null; evidence: unknown[]; defectCandidate?: boolean; defectDisposition?: string; conditionAssessments?: { conditionId: string; requirementQuote: string; outcome: string; evidenceIds: string[] }[] }
export type Run = { runId: string; tcSetId: string; status: string; stale: boolean; elapsedMs?: number; completedCount: number; notExecutedCount?: number; excludedCount?: number; reviewCount?: number; pendingCount?: number; errorCount?: number; passCount?: number; failCount?: number; naCount?: number; unjudgedCount?: number; snapshot: { productName: string; allowMutation?: boolean; startedAt?: string; priorElapsedMs?: number; resumedFrom?: string; preservedCount?: number; resumeNeedsReview?: string[]; progress?: { stage: string; round?: number; caseId?: string; trace?: { round:number; planner:string; decision:string; phase:string; explanation:string; rejectedReason?:string; actions:unknown[]; records?:unknown[] }[] }; productVersion?: string; reviewRevision?: number; reviews?: unknown[]; previousAttempts?: unknown[]; retryOf?: string; targetUrl: string; notes: string; errorCode?: string; currentFeatureId?: string; currentCaseId?: string; caseCount: number; groupCount: number; groupResults?: EvidenceGroup[]; groups: { featureId: string; kind: string; featurePath: string[]; caseIds: string[] }[]; items: Item[] } }
export type View = { run: Run | null; history: { runId: string; status: string; createdAt: string }[]; executorConnected: boolean; judgeConnected: boolean; executorBusy?: boolean; planner?: string; readinessIssues?: {code:string;message:string}[] }
const labels: Record<string, string> = { EXCLUDED: '실행 제외·검토 필요', PREPARED: '실행 전 · 항목 확인', WAITING_EXECUTOR: '실행 대기', RUNNING: '시험 중', COMPLETED: '완료', PARTIAL: '일부 미판정', FAILED: '실행 실패', INTERRUPTED: '실행 중단·복구 필요', ERROR: '수행 오류', NEEDS_CONTEXT: '추가 정보 필요', NEEDS_REVIEW: '검토 필요', CANCELED: '중단됨', PENDING: '대기' }
const executionErrors: Record<string,string> = {
  EXECUTION_INITIAL_ENTRY_NOT_READY:'지정한 최초 URL을 확인하지 못해 시험을 시작하지 않았습니다. 초기 진입·복구 증거에서 실제 도착 주소를 확인하세요.',
  EXECUTION_AI_REQUEST_REJECTED:'AI 서버가 시험 요청을 거부했습니다. 요청 형식과 서비스 간 통신 버전을 확인해야 합니다.',
  EXECUTION_AI_AUTH_FAILED:'AI 서버 내부 인증에 실패했습니다. 서비스 간 인증 설정을 확인해야 합니다.',
  EXECUTION_AI_API_MISSING:'AI 서버에 시험 실행 API가 없습니다. 실행 중인 소스 버전을 확인해야 합니다.',
  EXECUTION_AI_BUSY:'AI 실행 서비스가 사용 중입니다. 진행 중인 작업을 확인한 뒤 다시 준비하세요.',
  EXECUTION_AI_TIMEOUT:'AI 서버 응답 대기 시간이 초과됐습니다. 현재 TC의 증거를 확인한 뒤 이어서 진행하세요.',
  EXECUTION_AI_CONNECTION_FAILED:'AI 서버와 통신하지 못했습니다. 네트워크와 서비스 상태를 확인해야 합니다.',
  EXECUTION_AI_UNAVAILABLE:'AI 서버 요청 처리에 실패했습니다. 서비스 상태와 서버 오류 기록을 확인해야 합니다.',
}

export function TestExecutionPanel({ submissionId, memberId, active = true, preferredSetId }: { submissionId: string; memberId: string; active?: boolean; preferredSetId?: string }) {
  const [source, setSource] = useState<TcSet | null>(null)
  const [sets, setSets] = useState<{ setId: string; createdAt: string }[]>([])
  const [selectedSet, setSelectedSet] = useState('')
  useEffect(() => { if(preferredSetId)setSelectedSet(preferredSetId) }, [preferredSetId])
  const [view, updateView] = useState<View>({ run: null, history: [], executorConnected: false, judgeConnected: false })
  const [receivedAt, setReceivedAt] = useState(Date.now())
  const setView = useCallback((next: View) => { setReceivedAt(Date.now()); updateView(next) }, [])
  const [targetUrl, setTargetUrl] = useState(''), [notes, setNotes] = useState('')
  const [connectionBusy, setConnectionBusy] = useState(false)
  const [allowMutation, setAllowMutation] = useState(false)
  const [ignoreHTTPSErrors, setIgnoreHTTPSErrors] = useState(false)
  const [productVersion, setProductVersion] = useState('')
  const [excluded, setExcluded] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [filter, setFilter] = useState(''), [page, setPage] = useState(0), [detail, setDetail] = useState<Item | null>(null)
  const base = `/api/submissions/${submissionId}`
  const sourceVersion = useRef('')
  const [executionAvailable, setExecutionAvailable] = useState(false)
  const request = useCallback(async (path: string, options: RequestInit = {}) => {
    const response = await fetch(path, { ...options, headers: { 'X-Member-Id': memberId, ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    let data
    try { data = await response.json() } catch { throw new Error('시험 실행 서버 응답을 읽지 못했습니다.') }
    if (!response.ok) throw new Error(data.message || `시험 실행 정보를 불러오지 못했습니다. (HTTP ${response.status})`)
    return data
  }, [memberId])
  const load = useCallback(async (signal?: AbortSignal) => {
    const [tc, runs, setup] = await Promise.allSettled([
      request(`${base}/menu-test-cases${selectedSet ? `?set=${selectedSet}` : ''}`, {signal}),
      request(`${base}/executions`, {signal}), request(`${base}/playwright-setup`, {signal})])
    if (signal?.aborted) return
    const errors: string[]=[]
    if(tc.status==='fulfilled'){
      setSource(tc.value.set);setSets(tc.value.history)
      const next=tc.value.set?.setId || '';if(sourceVersion.current!==next){sourceVersion.current=next;setExcluded(new Set())}
    }else {setSource(null);errors.push(tc.reason instanceof Error?tc.reason.message:'TC 조회 실패')}
    if(runs.status==='fulfilled'){setView(runs.value);setExecutionAvailable(true)}else{setExecutionAvailable(false);errors.push(runs.reason instanceof Error?runs.reason.message:'실행 이력 조회 실패')}
    if(setup.status==='fulfilled'&&setup.value.setup){const saved=setup.value.setup;setIgnoreHTTPSErrors(saved.automation?.ignoreHTTPSErrors===true);setTargetUrl(current=>current || saved.targetUrl || '');setProductVersion(current=>current || saved.automation?.productVersion || '')}
    setError(errors.join(' '))
  }, [base, request, selectedSet, setView])
  useEffect(() => {
    if(!active)return
    const controller = new AbortController(); setLoading(true)
    void load(controller.signal).catch(e => { if (!controller.signal.aborted) setError(e.message) }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [load,active])
  const action = async (work: () => Promise<void>) => { setBusy(true); setError(''); try { await work() } catch (e) { setError(e instanceof Error ? e.message : '요청 실패') } finally { setBusy(false) } }
  const groups = useMemo(() => {
    const grouped = new Map<string, { path: string[]; cases: Tc[] }>()
    for (const tc of source?.output.testCases || []) { if (!grouped.has(tc.featureId)) grouped.set(tc.featureId, { path: tc.featurePath, cases: [] }); grouped.get(tc.featureId)!.cases.push(tc) }
    return [...grouped].map(([id, group]) => ({ id, ...group }))
  }, [source])
  const caseIds = groups.filter(g => !excluded.has(g.id)).flatMap(g => g.cases.map(tc => tc.id))
  const run = view.run
  const running = !!run && ['WAITING_EXECUTOR', 'RUNNING'].includes(run.status)
  const unfinished = run?.snapshot.items.filter(item => ['PENDING', 'ERROR', 'NEEDS_CONTEXT'].includes(item.executionStatus)) || []
  const resumable = unfinished.filter(item => !item.tc.featureId.startsWith('scope:') && (!run?.snapshot.allowMutation || (item.executionStatus === 'PENDING' && item.caseId !== run.snapshot.currentCaseId)))
  const runItems = run?.snapshot.items || []
  const performed = runItems.filter(item => item.executionStatus === 'COMPLETED')
  const inputNeeded = runItems.filter(item => item.executionStatus === 'NEEDS_CONTEXT' && !item.tc.featureId.startsWith('scope:'))
  const separate = runItems.filter(item => item.tc.featureId.startsWith('scope:') && ['NEEDS_CONTEXT','PENDING'].includes(item.executionStatus))
  const waiting = runItems.filter(item => item.executionStatus === 'PENDING' && !item.tc.featureId.startsWith('scope:'))
  const failedExecution = runItems.filter(item => item.executionStatus === 'ERROR')
  const executionExcluded = runItems.filter(item => item.executionStatus === 'EXCLUDED')
  const unjudged = performed.filter(item => !item.result)
  const hasStarted = !!run && (!!run.snapshot.startedAt || running || performed.length > 0 || failedExecution.length > 0 || ['COMPLETED','PARTIAL','FAILED','INTERRUPTED'].includes(run.status))
  const bucket = (item: Item) => item.tc.featureId.startsWith('scope:') && ['NEEDS_CONTEXT','PENDING'].includes(item.executionStatus) ? 'SEPARATE' : item.executionStatus === 'NEEDS_CONTEXT' ? 'INPUT' : item.executionStatus === 'PENDING' ? 'WAITING' : item.executionStatus
  const stages: Record<string, string> = { AUTHENTICATING: '저장된 시험 계정 입력·로그인 수행 중', INITIALIZING: 'Qwen이 최초 URL 복구 확인 중', SELECTING: 'Qwen이 TC 적용 범위 확인 중', CONNECTING: '브라우저 접속·초기 상태 준비', PLANNING: 'Qwen이 다음 조작 계획 중', ACTING: 'Playwright 화면 조작·증거 수집', RECHECK: '변경 내용 재조회', JUDGING: 'Jev가 수행 증거 판정 중' }
  const currentItem = run?.snapshot.items.find(item => item.caseId === run.snapshot.currentCaseId)
  useEffect(() => {
    if (!run || !['WAITING_EXECUTOR', 'RUNNING'].includes(run.status)) return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      void request(`${base}/executions?run=${run.runId}`, { signal: controller.signal }).then(next => { if (!controller.signal.aborted) setView(next) }).catch(e => { if (!controller.signal.aborted) setError(e.message) })
    }, 3000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [base, request, run?.runId, run?.status])
  const sourceDetailGroup = run?.snapshot.groupResults?.find(group => group.caseId ? group.caseId === detail?.caseId : group.featureId === detail?.tc.featureId)
  const {group:detailGroup,error:evidenceError,loading:evidenceLoading}=useExecutionEvidence(sourceDetailGroup,memberId)
  const items = runItems.filter(item => statusFilter === 'ALL' || bucket(item) === statusFilter).filter(item => `${item.tc.tcId} ${item.tc.featurePath.join(' ')} ${item.tc.scenario}`.toLowerCase().includes(filter.toLowerCase()))
  const pages = Math.max(1, Math.ceil(items.length / 20)), activePage = Math.min(page, pages - 1)
  const showRun = (next: View) => { setView(next); setPage(0); setDetail(null); setStatusFilter('ALL') }
  return <section className="menu-tc-panel execution-panel" aria-label="시험 실행 관리">
    <div className="section-heading"><h2>시험 실행</h2><button className="quiet-button" disabled={busy || loading} onClick={() => void action(() => load())}>실행 정보 새로고침</button></div>
    <p>5depth 기능별 TC → Playwright 수행·증거 수집 → LLM 판정 → 결과 검토</p>
    <p className="functional-menu-warning">계획 모델: {view.planner || '확인 중'}. 프로그램이 메뉴 이동과 완료 기준을 관리하고 Qwen이 필요한 조작을 보완합니다. 명시적 문구는 코드로 대조하고 설명형 조건은 Jev가 판정합니다. 정보·증거가 부족하면 검토 대상으로 남깁니다.</p>
    {view.executorBusy && !running && <p role="status">화면 수집 또는 다른 시험이 브라우저를 사용 중입니다. 작업 종료 후 실행 정보를 갱신하세요.</p>}
    {!loading && view.readinessIssues?.map(issue => <p key={issue.code} role="status" className="functional-menu-warning">{issue.message}</p>)}
    {error && <p role="alert" className="global-notice error-copy">{error}</p>}
    {evidenceLoading && <p role="status">수행 증거를 불러오는 중…</p>}{evidenceError && <p role="alert">{evidenceError}</p>}
    {loading && <p role="status"><span className="spinner" /> 실행 정보를 불러오고 있습니다…</p>}
    <details open><summary>1. 시험 준비</summary>
      <label className="tc-field">TC 세트<select aria-label="실행할 TC 세트" value={selectedSet || source?.setId || ''} disabled={busy || loading} onChange={e => setSelectedSet(e.target.value)}><option value="" disabled>TC 세트를 선택하세요</option>{sets.map(set => <option key={set.setId} value={set.setId}>{new Date(set.createdAt).toLocaleString('ko-KR')} · {set.setId.slice(0, 8)}</option>)}</select></label>
      {!loading && !source && <p>TC 생성 탭에서 먼저 TC를 생성하세요.</p>}
      {source?.stale && <p role="alert">원본이 변경된 TC입니다. TC를 다시 생성하세요.</p>}
      <label className="tc-field">시험 대상 URL<input type="url" placeholder="https://시험대상주소" value={targetUrl} disabled={connectionBusy} maxLength={2000} onChange={e => setTargetUrl(e.target.value)} /></label>
      <ExecutionConnectionTest submissionId={submissionId} memberId={memberId} targetUrl={targetUrl} ignoreHTTPSErrors={ignoreHTTPSErrors} onUrlChange={setTargetUrl} onBusyChange={setConnectionBusy} />
      <label className="tc-field">시험 데이터·환경 메모<textarea rows={3} maxLength={4000} value={notes} placeholder="필요한 시험 데이터와 환경을 적어주세요. 비밀번호는 입력하지 마세요." onChange={e => setNotes(e.target.value)} /></label>
      <label className="tc-field">제품·패치 버전<input value={productVersion} maxLength={120} onChange={e => setProductVersion(e.target.value)} placeholder="예: 1.0.1 · 패치 2" /></label>
      <label className="playwright-confirmation"><input type="checkbox" checked={allowMutation} onChange={e => setAllowMutation(e.target.checked)} />선택한 시험 범위에서 데이터 생성·수정·삭제와 작업 실행 허용</label>
      <details><summary>기능별 시험 범위 · {groups.length - excluded.size}개 그룹 / {caseIds.length.toLocaleString()}개 TC 선택</summary>
        <div className="execution-groups">{groups.map(group => <label key={group.id}><input type="checkbox" checked={!excluded.has(group.id)} onChange={e => setExcluded(previous => { const next = new Set(previous); if (e.target.checked) next.delete(group.id); else next.add(group.id); return next })} /><span>{group.id.startsWith('scope:') ? '공통·문서 시험 · ' : ''}{group.path.join(' > ')}<small>{group.cases.length}개 TC</small></span></label>)}</div>
      </details>
      <div className="tc-generate-toolbar"><button className="primary-button" disabled={busy || loading || !executionAvailable || !source || (selectedSet !== '' && selectedSet !== source.setId) || source.stale || !targetUrl.trim() || !caseIds.length} onClick={() => void action(async () => { showRun(await request(`${base}/executions`, { method: 'POST', body: JSON.stringify({ tcSetId: source!.setId, targetUrl, notes, caseIds, allowMutation, productVersion }) })) })}>시험 실행 준비</button><span>TC 원본을 보존하고 별도 실행 이력을 만듭니다.</span></div>
    </details>
    <details open><summary>2. 실행 진행</summary>
      <label className="tc-field">실행 이력<select aria-label="시험 실행 이력" value={run?.runId || ''} disabled={busy || loading} onChange={e => void action(async () => showRun(await request(`${base}/executions?run=${e.target.value}`)))}><option value="" disabled>실행 이력이 없습니다</option>{view.history.map(item => <option key={item.runId} value={item.runId}>{new Date(item.createdAt).toLocaleString('ko-KR')} · {labels[item.status] || item.status} · {item.runId.slice(0, 8)}</option>)}</select></label>
      {run && <>
        <div className="execution-summary" role="status"><strong>{run.status === 'PREPARED' ? '시험 시작 전' : labels[run.status] || run.status}</strong><span>{run.snapshot.productName}</span><span>기능 {new Set(runItems.filter(item=>!item.tc.featureId.startsWith('scope:')).map(item=>item.tc.featureId)).size}개 · 공통·문서 {new Set(runItems.filter(item=>item.tc.featureId.startsWith('scope:')).map(item=>item.tc.featureId)).size}개 그룹 · 전체 TC {run.snapshot.caseCount.toLocaleString()}개</span><span>수행 완료 {performed.length.toLocaleString()}개 · 수행 오류 {failedExecution.length.toLocaleString()}개</span></div>
        <RunProgress taskKey={`${run.runId}:${hasStarted ? 'execution' : 'prepared'}`} title={run.status === 'PREPARED' ? '시험 시작 전' : labels[run.status] || run.status} active={running} elapsedMs={hasStarted ? (run.elapsedMs || 0)+(run.snapshot.priorElapsedMs || 0) : 0} receivedAt={receivedAt} completed={performed.length} total={run.snapshot.caseCount}
          progressAriaLabel="TC 수행 완료율" progressText={`수행 완료 ${performed.length.toLocaleString()} / 전체 ${run.snapshot.caseCount.toLocaleString()}개 TC · ${Math.round(performed.length / Math.max(1,run.snapshot.caseCount)*100)}%`}
          current={!hasStarted ? '아직 시험을 수행하지 않았습니다. 아래 실행 대기·사전 입력·별도 수행 항목을 확인하세요.' : run.status === 'WAITING_EXECUTOR' ? '실행기 순서를 기다리고 있습니다.' : run.status === 'RUNNING' ? `${currentItem?.tc.tcId || ''} · ${stages[run.snapshot.progress?.stage || 'CONNECTING'] || '수행 준비 중'}${currentItem ? ` · ${currentItem.tc.featurePath.join(' → ')}` : ''}` : `수행 완료 ${performed.length.toLocaleString()}개 · 수행 후 판정 검토 ${unjudged.length.toLocaleString()}개 · 이어서 준비 대상 ${resumable.length.toLocaleString()}개`}
          metrics={[{ label: '실행 대기', value: waiting.length }, { label: hasStarted ? '추가 입력·환경 필요' : '사전 입력·환경 필요', value: inputNeeded.length, tone: 'warning' }, { label: '별도 수행 필요', value: separate.length, tone: 'warning' }, { label: '수행 완료', value: performed.length, tone: 'success' }, { label: '수행 오류', value: failedExecution.length, tone: 'danger' }, { label: '실행 제외·검토', value: executionExcluded.length }]}
          note={!hasStarted ? '입력·환경 필요와 별도 수행 항목은 준비 단계의 분류이며 수행 건수에 포함하지 않습니다. 실행 대기도 모든 입력·판정 기준의 준비 완료를 뜻하지 않습니다.' : running ? '입력 부족·별도 수행·실행 제외는 수행 완료율에 포함하지 않습니다. 중단 시 완료 결과를 보존합니다.' : '이어서 준비는 미완료 항목 중 재실행 정책상 허용되는 대상입니다. 추가 입력이 필요한 항목은 정보를 보완해야 하며, 변경 결과가 불확실한 항목은 먼저 확인하세요.'} />
        {hasStarted && <p>판정 결과: P {run.passCount || 0} · F {run.failCount || 0} · N/A {run.naCount || 0} · 수행 후 판정 검토 {unjudged.length}</p>}
        <div className="tc-output-toolbar" aria-label="TC 상태별 보기">{[['ALL','전체',runItems.length],['WAITING','실행 대기',waiting.length],['INPUT','입력·환경 필요',inputNeeded.length],['SEPARATE','별도 수행 필요',separate.length],['COMPLETED','수행 완료',performed.length],['ERROR','수행 오류',failedExecution.length],['EXCLUDED','실행 제외',executionExcluded.length]].map(([key,label,count])=><button key={key} className="quiet-button" aria-pressed={statusFilter===key} onClick={()=>{setStatusFilter(String(key));setPage(0)}}>{label} {Number(count).toLocaleString()}개</button>)}</div>
        {!!run.snapshot.progress?.trace?.length&&<details open aria-label="실시간 조작 계획"><summary>현재 TC 계획·실행 기록 · {run.snapshot.progress.trace.length}개</summary>{run.snapshot.progress.trace.map((trace,index)=><article key={index}><strong>{trace.round}차 · {trace.planner==='QWEN'?'Qwen':'프로그램'} · {trace.decision} · {trace.phase}</strong><p>{trace.explanation}</p>{trace.rejectedReason&&<p className="error-copy">{trace.rejectedReason}</p>}<pre>{JSON.stringify({actions:trace.actions,records:trace.records},null,2)}</pre></article>)}</details>}
        {run.snapshot.resumedFrom && <div><p>이전 실행 결과 {run.snapshot.preservedCount || 0}개 보존 · 미완료 항목 이어서 진행{run.snapshot.resumeNeedsReview?.length ? ` · 변경 반영 확인 필요 ${run.snapshot.resumeNeedsReview.length}개는 이전 실행에서 검토하세요.` : ''}</p><button className="quiet-button" disabled={busy} onClick={() => void action(async () => showRun(await request(`${base}/executions?run=${run.snapshot.resumedFrom}`)))}>이전 실행 결과 보기</button></div>}
        {!!(inputNeeded.length + separate.length) && <details><summary>입력·별도 수행 사유 · {inputNeeded.length + separate.length}개 TC</summary><ul>{[...new Set(run.snapshot.items.filter(item => item.executionStatus === 'NEEDS_CONTEXT').map(item => `${item.tc.featurePath.join(' → ')} · ${item.reason}`))].map(reason => <li key={reason}>{reason}</li>)}</ul></details>}
        <p>{run.snapshot.targetUrl}</p>{run.snapshot.notes && <p className="execution-notes">{run.snapshot.notes}</p>}
        <p>제품·패치 버전: {run.snapshot.productVersion || '미입력'}{run.snapshot.retryOf && ` · 재시험 원본: ${run.snapshot.retryOf}`}</p>
        {run.snapshot.currentCaseId && <p role="status">현재 TC: {run.snapshot.items.find(item => item.caseId === run.snapshot.currentCaseId)?.tc.tcId}</p>}
        {run.stale && <p role="alert">원본이 변경되어 이 실행을 시작할 수 없습니다. 새 TC로 실행을 준비하세요.</p>}
        {run.snapshot.errorCode && <p role="alert">{executionErrors[run.snapshot.errorCode] || '시험 실행 중 오류가 발생했습니다.'} ({run.snapshot.errorCode}{'errorHttpStatus' in run.snapshot ? ` · HTTP ${run.snapshot.errorHttpStatus}` : ''})</p>}
        {run.snapshot.currentFeatureId && <p role="status">현재 시험 기능: {run.snapshot.groups.find(group => group.featureId === run.snapshot.currentFeatureId)?.featurePath.join(' > ')}</p>}
        <ol className="execution-pipeline"><li>TC·기능 연결</li><li>Qwen 계획·Playwright 수행</li><li>Jev의 P/F/N/A 판정</li><li>결과·결함 후보 검토</li></ol>
        <div className="tc-output-toolbar"><button className="primary-button" disabled={busy || view.executorBusy || !executionAvailable || !run.snapshot.items.some(item => item.executionStatus === 'PENDING' && !item.tc.featureId.startsWith('scope:')) || !view.executorConnected || !view.judgeConnected || run.status !== 'PREPARED' || run.stale} onClick={() => void action(async () => showRun(await request(`${base}/executions/${run.runId}/start`, { method: 'POST' })))}>실행 요청</button><button className="quiet-button" disabled={busy || !['PREPARED', 'WAITING_EXECUTOR', 'RUNNING'].includes(run.status)} onClick={() => void action(async () => showRun(await request(`${base}/executions/${run.runId}/cancel`, { method: 'POST' })))}>{run.status === 'PREPARED' ? '준비 취소' : '실행 중단'}</button>{hasStarted && <button className="primary-button" disabled={busy || run.stale || ['PREPARED', 'WAITING_EXECUTOR', 'RUNNING'].includes(run.status) || !resumable.length} onClick={() => void action(async () => showRun(await request(`${base}/executions/${run.runId}/resume`, { method: 'POST' })))}>미완료만 이어서 준비</button>}</div>
      </>}
    </details>
    <details open><summary>3. TC 목록·준비 및 수행 상태</summary>
      <label className="tc-field">TC 검색<input type="search" aria-label="실행 TC 검색" value={filter} onChange={e => { setFilter(e.target.value); setPage(0) }} /></label>
      <div className="table-scroll"><table className="tc-table"><thead><tr><th>TC</th><th>기능 경로</th><th>시험 항목</th><th>수행 상태</th><th>판정</th><th>Jev 신뢰도</th><th>근거</th></tr></thead><tbody>{items.slice(activePage * 20, activePage * 20 + 20).map(item => <tr key={item.caseId}><td>{item.tc.tcId}</td><td>{item.tc.featurePath.join(' > ')}</td><td>{item.tc.scenario}</td><td>{bucket(item) === 'SEPARATE' ? '별도 수행 필요' : bucket(item) === 'INPUT' ? '입력·환경 필요' : labels[item.executionStatus] || item.executionStatus}</td><td>{item.result || (item.executionStatus === 'COMPLETED' ? '판정 검토 필요' : item.executionStatus === 'EXCLUDED' ? '제외 사유 검토' : '판정 전')}</td><td><JevConfidence item={item} /></td><td><button className="quiet-button" onClick={() => setDetail(item)}>실행 상세</button></td></tr>)}</tbody></table></div>
      {!items.length && <p>표시할 시험 항목이 없습니다.</p>}
      <div className="tc-pagination"><button className="quiet-button" disabled={!activePage} onClick={() => setPage(activePage - 1)}>이전</button><span>{activePage + 1} / {pages}</span><button className="quiet-button" disabled={activePage + 1 >= pages} onClick={() => setPage(activePage + 1)}>다음</button></div>
      {detail && <article className="tc-case-detail"><div className="section-heading"><h3>{detail.tc.tcId} · 실행 상세</h3><button className="quiet-button" onClick={() => setDetail(null)}>상세 닫기</button></div><dl><dt>시험 항목</dt><dd>{detail.tc.scenario}</dd><dt>입력·조건</dt><dd>{detail.tc.inputs}</dd><dt>기대 결과</dt><dd>{detail.tc.expectedResult}</dd><dt>실제 결과</dt><dd>{detail.actualResult || '아직 수행하지 않았습니다.'}</dd><dt>{detail.executionStatus === 'COMPLETED' ? '판정 주체·이유' : '준비·수행 상태와 사유'}</dt><dd>{detail.executionStatus === 'COMPLETED' ? `${detail.judgedBy==='CODE_READ_SEARCH'?'코드 · 목록 검색 검증':detail.judgedBy==='CODE_READ_VIEW'?'코드 · 조회 화면 확인':detail.judgedBy==='CODE_LITERAL_ASSERTION'?'코드 · 명시적 문구 대조':'Jev'} · ${detail.result || '판정 검토 필요'}` : bucket(detail) === 'SEPARATE' ? '별도 수행 필요' : bucket(detail) === 'INPUT' ? '입력·환경 필요' : labels[detail.executionStatus] || detail.executionStatus} · {detail.reason || '아직 판정하지 않았습니다.'}</dd><dt>판정 신뢰도</dt><dd>{detail.judgedBy?.startsWith('CODE_') ? '규칙 기반 판정' : <JevConfidence item={detail} />}</dd><dt>증거</dt><dd>{detailGroup?.evidence?.length ? detailGroup.evidence.map(evidence => <details key={evidence.id}><summary>{evidence.id} · {evidence.kind}</summary><p>{evidence.url}</p>{evidence.text && <pre>{evidence.text}</pre>}{evidence.records && <pre>{JSON.stringify(evidence.records, null, 2)}</pre>}{evidence.screenshot && <img className="execution-evidence-image" src={`data:image/png;base64,${evidence.screenshot}`} alt={`${evidence.id} 수행 화면`} />}</details>) : '수집된 증거가 없습니다.'}</dd></dl>{detailGroup?.plans && <details><summary>Qwen 실행 계획</summary><pre>{JSON.stringify(detailGroup.plans, null, 2)}</pre></details>}</article>}
      {detail?.conditionAssessments && <details open><summary>TC별 확인 조건·증거</summary>{detail.conditionAssessments.map((condition,index) => <p key={condition.conditionId}>{condition.conditionId} · {condition.requirementQuote} · {condition.outcome} · {condition.evidenceIds.join(', ') || '증거 없음'} · Jev <JevConfidence item={detail} conditionIndex={index} /></p>)}</details>}
    </details>
  </section>
}
