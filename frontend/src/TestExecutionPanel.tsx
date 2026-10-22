import { useCallback, useEffect, useMemo, useState } from 'react'
import { ExecutionConnectionTest } from './ExecutionConnectionTest'

type Tc = { id: string; tcId: string; featureId: string; featurePath: string[]; scenario: string; expectedResult: string; inputs: string }
type TcSet = { setId: string; stale: boolean; output: { productName: string; testCases: Tc[] } }
type Item = { caseId: string; tc: Tc; executionStatus: string; judgmentStatus: string; result: 'P' | 'F' | 'N/A' | null; actualResult: string | null; reason: string | null; evidence: unknown[] }
type Evidence = { id: string; kind: string; url?: string; text?: string; screenshot?: string; records?: unknown[] }
type Run = { runId: string; tcSetId: string; status: string; stale: boolean; completedCount: number; passCount?: number; failCount?: number; naCount?: number; unjudgedCount?: number; snapshot: { productName: string; targetUrl: string; notes: string; errorCode?: string; currentFeatureId?: string; caseCount: number; groupCount: number; groupResults?: { featureId: string; evidence: Evidence[]; plans: unknown[] }[]; groups: { featureId: string; kind: string; featurePath: string[]; caseIds: string[] }[]; items: Item[] } }
type View = { run: Run | null; history: { runId: string; status: string; createdAt: string }[]; executorConnected: boolean; judgeConnected: boolean }
const labels: Record<string, string> = { PREPARED: '준비 완료', WAITING_EXECUTOR: '실행 대기', RUNNING: '시험 중', COMPLETED: '완료', PARTIAL: '일부 미판정', FAILED: '실행 실패', INTERRUPTED: '실행 중단·복구 필요', ERROR: '수행 오류', NEEDS_CONTEXT: '추가 정보 필요', NEEDS_REVIEW: '검토 필요', CANCELED: '중단됨', PENDING: '대기' }

export function TestExecutionPanel({ submissionId, memberId }: { submissionId: string; memberId: string }) {
  const [source, setSource] = useState<TcSet | null>(null)
  const [sets, setSets] = useState<{ setId: string; createdAt: string }[]>([])
  const [selectedSet, setSelectedSet] = useState('')
  const [view, setView] = useState<View>({ run: null, history: [], executorConnected: false, judgeConnected: false })
  const [targetUrl, setTargetUrl] = useState(''), [notes, setNotes] = useState('')
  const [connectionBusy, setConnectionBusy] = useState(false)
  const [allowMutation, setAllowMutation] = useState(false)
  const [excluded, setExcluded] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [filter, setFilter] = useState(''), [page, setPage] = useState(0), [detail, setDetail] = useState<Item | null>(null)
  const base = `/api/submissions/${submissionId}`
  const request = useCallback(async (path: string, options: RequestInit = {}) => {
    const response = await fetch(path, { ...options, headers: { 'X-Member-Id': memberId, ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    let data
    try { data = await response.json() } catch { throw new Error('시험 실행 서버 응답을 읽지 못했습니다.') }
    if (!response.ok) throw new Error(data.message || '시험 실행 서버 업데이트가 필요하거나 요청에 실패했습니다.')
    return data
  }, [memberId])
  const load = useCallback(async (signal?: AbortSignal) => {
    const [tc, runs] = await Promise.all([request(`${base}/menu-test-cases${selectedSet ? `?set=${selectedSet}` : ''}`, { signal }), request(`${base}/executions`, { signal })])
    if (signal?.aborted) return
    setSource(tc.set); setSets(tc.history); setView(runs)
  }, [base, request, selectedSet])
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(''); setExcluded(new Set())
    void load(controller.signal).catch(e => { if (!controller.signal.aborted) setError(e.message) }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [load])
  const action = async (work: () => Promise<void>) => { setBusy(true); setError(''); try { await work() } catch (e) { setError(e instanceof Error ? e.message : '요청 실패') } finally { setBusy(false) } }
  const groups = useMemo(() => {
    const grouped = new Map<string, { path: string[]; cases: Tc[] }>()
    for (const tc of source?.output.testCases || []) { if (!grouped.has(tc.featureId)) grouped.set(tc.featureId, { path: tc.featurePath, cases: [] }); grouped.get(tc.featureId)!.cases.push(tc) }
    return [...grouped].map(([id, group]) => ({ id, ...group }))
  }, [source])
  const caseIds = groups.filter(g => !excluded.has(g.id)).flatMap(g => g.cases.map(tc => tc.id))
  const run = view.run
  useEffect(() => {
    if (!run || !['WAITING_EXECUTOR', 'RUNNING'].includes(run.status)) return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      void request(`${base}/executions?run=${run.runId}`, { signal: controller.signal }).then(next => { if (!controller.signal.aborted) setView(next) }).catch(e => { if (!controller.signal.aborted) setError(e.message) })
    }, 3000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [base, request, run?.runId, run?.status])
  const detailGroup = run?.snapshot.groupResults?.find(group => group.featureId === detail?.tc.featureId)
  const items = (run?.snapshot.items || []).filter(item => `${item.tc.tcId} ${item.tc.featurePath.join(' ')} ${item.tc.scenario}`.toLowerCase().includes(filter.toLowerCase()))
  const pages = Math.max(1, Math.ceil(items.length / 20)), activePage = Math.min(page, pages - 1)
  const showRun = (next: View) => { setView(next); setPage(0); setDetail(null) }
  return <section className="menu-tc-panel execution-panel" aria-label="시험 실행 관리">
    <div className="section-heading"><h2>시험 실행</h2><button className="quiet-button" disabled={busy || loading} onClick={() => void action(() => load())}>실행 정보 새로고침</button></div>
    <p>5depth 기능별 TC → Playwright 수행·증거 수집 → LLM 판정 → 결과 검토</p>
    <p className="functional-menu-warning">로컬 Qwen이 실행 계획을 작성하고 Playwright가 수행한 증거를 Jev가 판정합니다. 정보·증거가 부족한 항목은 미판정으로 남깁니다. 로그인은 화면 수집 탭에 저장한 같은 대상의 설정을 사용합니다.</p>
    {!loading && !view.executorConnected && <p role="status">현재 서버는 실행 준비 버전입니다. dev5의 시험 실행 서버를 반영한 후 실제 실행을 요청할 수 있습니다.</p>}
    {error && <p role="alert" className="global-notice error-copy">{error}</p>}
    {loading && <p role="status"><span className="spinner" /> 실행 정보를 불러오고 있습니다…</p>}
    <details open><summary>1. 시험 준비</summary>
      <label className="tc-field">TC 세트<select aria-label="실행할 TC 세트" value={selectedSet || source?.setId || ''} disabled={busy || loading} onChange={e => setSelectedSet(e.target.value)}><option value="" disabled>TC 세트를 선택하세요</option>{sets.map(set => <option key={set.setId} value={set.setId}>{new Date(set.createdAt).toLocaleString('ko-KR')} · {set.setId.slice(0, 8)}</option>)}</select></label>
      {!loading && !source && <p>TC 생성 탭에서 먼저 TC를 생성하세요.</p>}
      {source?.stale && <p role="alert">원본이 변경된 TC입니다. TC를 다시 생성하세요.</p>}
      <label className="tc-field">시험 대상 URL<input type="url" placeholder="https://시험대상주소" value={targetUrl} disabled={connectionBusy} maxLength={2000} onChange={e => setTargetUrl(e.target.value)} /></label>
      <ExecutionConnectionTest submissionId={submissionId} memberId={memberId} targetUrl={targetUrl} onUrlChange={setTargetUrl} onBusyChange={setConnectionBusy} />
      <label className="tc-field">시험 데이터·환경 메모<textarea rows={3} maxLength={4000} value={notes} placeholder="필요한 시험 데이터와 환경을 적어주세요. 비밀번호는 입력하지 마세요." onChange={e => setNotes(e.target.value)} /></label>
      <label className="playwright-confirmation"><input type="checkbox" checked={allowMutation} onChange={e => setAllowMutation(e.target.checked)} />선택한 시험 범위에서 데이터 생성·수정·삭제와 작업 실행 허용</label>
      <details><summary>기능별 시험 범위 · {groups.length - excluded.size}개 그룹 / {caseIds.length.toLocaleString()}개 TC 선택</summary>
        <div className="execution-groups">{groups.map(group => <label key={group.id}><input type="checkbox" checked={!excluded.has(group.id)} onChange={e => setExcluded(previous => { const next = new Set(previous); if (e.target.checked) next.delete(group.id); else next.add(group.id); return next })} /><span>{group.id.startsWith('scope:') ? '공통·문서 시험 · ' : ''}{group.path.join(' > ')}<small>{group.cases.length}개 TC</small></span></label>)}</div>
      </details>
      <div className="tc-generate-toolbar"><button className="primary-button" disabled={busy || loading || !source || source.stale || !targetUrl.trim() || !caseIds.length} onClick={() => void action(async () => { showRun(await request(`${base}/executions`, { method: 'POST', body: JSON.stringify({ tcSetId: source!.setId, targetUrl, notes, caseIds, allowMutation }) })) })}>시험 실행 준비</button><span>TC 원본을 보존하고 별도 실행 이력을 만듭니다.</span></div>
    </details>
    <details open><summary>2. 실행 진행</summary>
      <label className="tc-field">실행 이력<select aria-label="시험 실행 이력" value={run?.runId || ''} disabled={busy || loading} onChange={e => void action(async () => showRun(await request(`${base}/executions?run=${e.target.value}`)))}><option value="" disabled>실행 이력이 없습니다</option>{view.history.map(item => <option key={item.runId} value={item.runId}>{new Date(item.createdAt).toLocaleString('ko-KR')} · {labels[item.status] || item.status} · {item.runId.slice(0, 8)}</option>)}</select></label>
      {run && <>
        <div className="execution-summary" role="status"><strong>{labels[run.status] || run.status}</strong><span>{run.snapshot.productName}</span><span>{run.snapshot.groupCount}개 그룹 · {run.completedCount} / {run.snapshot.caseCount}개 처리</span><span>P {run.passCount || 0} · F {run.failCount || 0} · N/A {run.naCount || 0} · 미판정 {run.unjudgedCount ?? run.snapshot.caseCount}</span></div>
        <p>{run.snapshot.targetUrl}</p>{run.snapshot.notes && <p className="execution-notes">{run.snapshot.notes}</p>}
        {run.stale && <p role="alert">원본이 변경되어 이 실행을 시작할 수 없습니다. 새 TC로 실행을 준비하세요.</p>}
        {run.snapshot.errorCode && <p role="alert">실행 오류: {run.snapshot.errorCode}</p>}
        {run.snapshot.currentFeatureId && <p role="status">현재 시험 기능: {run.snapshot.groups.find(group => group.featureId === run.snapshot.currentFeatureId)?.featurePath.join(' > ')}</p>}
        <ol className="execution-pipeline"><li>TC·기능 연결</li><li>Qwen 계획·Playwright 수행</li><li>Jev의 P/F/N/A 판정</li><li>결과·결함 후보 검토</li></ol>
        <div className="tc-output-toolbar"><button className="primary-button" disabled={busy || !view.executorConnected || run.status !== 'PREPARED' || run.stale} onClick={() => void action(async () => showRun(await request(`${base}/executions/${run.runId}/start`, { method: 'POST' })))}>실행 요청</button><button className="quiet-button" disabled={busy || !['PREPARED', 'WAITING_EXECUTOR', 'RUNNING'].includes(run.status)} onClick={() => void action(async () => showRun(await request(`${base}/executions/${run.runId}/cancel`, { method: 'POST' })))}>실행 중단</button></div>
      </>}
    </details>
    <details open><summary>3. TC별 수행·판정 결과</summary>
      <label className="tc-field">TC 검색<input type="search" aria-label="실행 TC 검색" value={filter} onChange={e => { setFilter(e.target.value); setPage(0) }} /></label>
      <div className="table-scroll"><table className="tc-table"><thead><tr><th>TC</th><th>기능 경로</th><th>시험 항목</th><th>수행 상태</th><th>LLM 판정</th><th>근거</th></tr></thead><tbody>{items.slice(activePage * 20, activePage * 20 + 20).map(item => <tr key={item.caseId}><td>{item.tc.tcId}</td><td>{item.tc.featurePath.join(' > ')}</td><td>{item.tc.scenario}</td><td>{labels[item.executionStatus] || item.executionStatus}</td><td>{item.result || '미판정'}</td><td><button className="quiet-button" onClick={() => setDetail(item)}>실행 상세</button></td></tr>)}</tbody></table></div>
      {!items.length && <p>표시할 시험 항목이 없습니다.</p>}
      <div className="tc-pagination"><button className="quiet-button" disabled={!activePage} onClick={() => setPage(activePage - 1)}>이전</button><span>{activePage + 1} / {pages}</span><button className="quiet-button" disabled={activePage + 1 >= pages} onClick={() => setPage(activePage + 1)}>다음</button></div>
      {detail && <article className="tc-case-detail"><div className="section-heading"><h3>{detail.tc.tcId} · 실행 상세</h3><button className="quiet-button" onClick={() => setDetail(null)}>상세 닫기</button></div><dl><dt>시험 항목</dt><dd>{detail.tc.scenario}</dd><dt>입력·조건</dt><dd>{detail.tc.inputs}</dd><dt>기대 결과</dt><dd>{detail.tc.expectedResult}</dd><dt>실제 결과</dt><dd>{detail.actualResult || '아직 수행하지 않았습니다.'}</dd><dt>Jev 판정·이유</dt><dd>{detail.result || '미판정'} · {detail.reason || '판정 근거 대기'}</dd><dt>증거</dt><dd>{detailGroup?.evidence?.length ? detailGroup.evidence.map(evidence => <details key={evidence.id}><summary>{evidence.id} · {evidence.kind}</summary><p>{evidence.url}</p>{evidence.text && <pre>{evidence.text}</pre>}{evidence.records && <pre>{JSON.stringify(evidence.records, null, 2)}</pre>}{evidence.screenshot && <img className="execution-evidence-image" src={`data:image/png;base64,${evidence.screenshot}`} alt={`${evidence.id} 수행 화면`} />}</details>) : '수집된 증거가 없습니다.'}</dd></dl>{detailGroup?.plans && <details><summary>Qwen 실행 계획</summary><pre>{JSON.stringify(detailGroup.plans, null, 2)}</pre></details>}</article>}
    </details>
  </section>
}
