import { useCallback, useEffect, useState } from 'react'
import type { Item, View } from './TestExecutionPanel'

export function ExecutionReviewPanel({ submissionId, memberId, reports = false }: { submissionId: string; memberId: string; reports?: boolean }) {
  const [view, setView] = useState<View>({ run: null, history: [], executorConnected: false, judgeConnected: false })
  const [selectedRun, setSelectedRun] = useState(''), [selected, setSelected] = useState<Set<string>>(new Set())
  const [detail, setDetail] = useState<Item | null>(null), [verdict, setVerdict] = useState('UNJUDGED'), [reason, setReason] = useState(''), [disposition, setDisposition] = useState('PENDING')
  const [version, setVersion] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const [page, setPage] = useState(0)
  const base = `/api/submissions/${submissionId}/executions`
  const request = useCallback(async (path: string, options: RequestInit = {}) => {
    const response = await fetch(path, { ...options, headers: { 'X-Member-Id': memberId, ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    let value; try { value = await response.json() } catch { throw new Error('검토 서비스 응답을 읽지 못했습니다.') }
    if (!response.ok) throw new Error(value.message || '검토 요청 실패')
    return value
  }, [memberId])
  const load = useCallback(async (signal?: AbortSignal) => {
    const next: View = await request(`${base}${selectedRun ? `?run=${selectedRun}` : ''}`, { signal })
    if (!signal?.aborted) { setView(next); setVersion(next.run?.snapshot.productVersion || ''); setPage(0); setSelected(new Set()); setDetail(null) }
  }, [base, request, selectedRun])
  useEffect(() => { const controller = new AbortController(); void load(controller.signal).catch(e => { if (!controller.signal.aborted) setError(e.message) }); return () => controller.abort() }, [load])
  const action = async (work: () => Promise<void>) => { setBusy(true); setError(''); setNotice(''); try { await work() } catch (e) { setError(e instanceof Error ? e.message : '요청 실패') } finally { setBusy(false) } }
  const run = view.run, closed = !!run && !['PREPARED', 'WAITING_EXECUTOR', 'RUNNING'].includes(run.status)
  const items = (run?.snapshot.items || []).filter(item => reports || item.result === 'F' || item.defectCandidate || item.judgmentStatus === 'NEEDS_REVIEW' || item.result === null)
  const pages = Math.max(1, Math.ceil(items.length / 20)), activePage = Math.min(page, pages - 1)
  const detailEvidence = run?.snapshot.groupResults?.find(group => group.caseId ? group.caseId === detail?.caseId : group.featureId === detail?.tc.featureId)
  const inspect = (item: Item) => { setDetail(item); setVerdict(item.result || 'UNJUDGED'); setReason(''); setDisposition(item.defectDisposition || 'PENDING') }
  const download = () => action(async () => {
    const response = await fetch(`${base}/${run!.runId}/report`, { headers: { 'X-Member-Id': memberId } }); if (!response.ok) throw new Error('보고서 다운로드 실패')
    const url = URL.createObjectURL(await response.blob()), link = document.createElement('a'); link.href = url; link.download = `${run!.snapshot.productName}-시험보고서-초안.md`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  })
  return <section className="menu-tc-panel execution-panel" aria-label={reports ? '리포트·재시험 관리' : '결함·판정 검토'}>
    <div className="section-heading"><h2>{reports ? '리포트·재시험' : '결함·판정 검토'}</h2><button className="quiet-button" disabled={busy} onClick={() => void action(() => load())}>검토 정보 새로고침</button></div>
    <p>자동 판정과 사용자 판정을 구분해 보존합니다. 이전 실패 후 성공은 이전 실행과 함께 검토하세요. 보고서는 확정 전 초안입니다.</p>
    {error && <p role="alert" className="error-copy">{error}</p>}{notice && <p role="status">{notice}</p>}
    <label className="tc-field">검토할 실행<select value={selectedRun || run?.runId || ''} disabled={busy} onChange={e => setSelectedRun(e.target.value)}><option value="" disabled>실행 이력 없음</option>{view.history.map(entry => <option key={entry.runId} value={entry.runId}>{new Date(entry.createdAt).toLocaleString('ko-KR')} · {entry.status} · {entry.runId.slice(0, 8)}</option>)}</select></label>
    {run && <><p>{run.snapshot.productName} · {run.snapshot.productVersion || '버전 미입력'} · {run.status} · P {run.passCount || 0} / F {run.failCount || 0} / N/A {run.naCount || 0} / 미판정 {run.unjudgedCount ?? 0}</p>
      <label className="tc-field">재시험 제품·패치 버전<input value={version} maxLength={120} onChange={e => setVersion(e.target.value)} /></label>
      <div className="tc-output-toolbar"><button className="primary-button" disabled={busy || !closed || run.stale} onClick={() => void action(async () => { const next: View = await request(`${base}/${run.runId}/retry`, { method: 'POST', body: JSON.stringify({ caseIds: selected.size ? [...selected] : null, productVersion: version }) }); setNotice(`재시험 준비 완료 · ${next.run!.runId} · 05 시험 실행 탭에서 실행 정보를 새로고침하고 시작하세요.`) })}>선택 항목 재시험 준비</button><span>미선택 시 F·미판정 항목을 준비합니다.</span><button className="quiet-button" disabled={busy} onClick={() => void download()}>시험 보고서 초안 다운로드</button></div>
      <div className="table-scroll"><table className="tc-table"><thead><tr><th>선택</th><th>TC</th><th>시험 항목</th><th>판정</th><th>검토 상태</th><th>상세</th></tr></thead><tbody>{items.slice(activePage * 20, activePage * 20 + 20).map(item => <tr key={item.caseId}><td><input type="checkbox" aria-label={`${item.tc.tcId} 재시험 선택`} checked={selected.has(item.caseId)} onChange={e => setSelected(previous => { const next = new Set(previous); if (e.target.checked) next.add(item.caseId); else next.delete(item.caseId); return next })} /></td><td>{item.tc.tcId}</td><td>{item.tc.scenario}</td><td>{item.result || '미판정'}</td><td>{item.defectDisposition || item.judgmentStatus}</td><td><button className="quiet-button" onClick={() => inspect(item)}>판정 검토</button></td></tr>)}</tbody></table></div>
      <div className="tc-pagination"><button className="quiet-button" disabled={!activePage} onClick={() => setPage(activePage - 1)}>이전</button><span>{activePage + 1} / {pages}</span><button className="quiet-button" disabled={activePage + 1 >= pages} onClick={() => setPage(activePage + 1)}>다음</button></div>
      {detail && <article className="tc-case-detail"><div className="section-heading"><h3>{detail.tc.tcId} 판정 검토</h3><button className="quiet-button" onClick={() => setDetail(null)}>검토 닫기</button></div><dl><dt>기대 결과</dt><dd>{detail.tc.expectedResult}</dd><dt>실제 결과</dt><dd>{detail.actualResult || '미수행'}</dd><dt>현재 사유</dt><dd>{detail.reason}</dd></dl>
        <details><summary>수행 화면·조작·확인 조건</summary>{detailEvidence?.evidence.map(evidence => <details key={evidence.id}><summary>{evidence.id} · {evidence.kind}</summary>{evidence.text && <pre>{evidence.text}</pre>}{evidence.records && <pre>{JSON.stringify(evidence.records, null, 2)}</pre>}{evidence.screenshot && <img className="execution-evidence-image" src={`data:image/png;base64,${evidence.screenshot}`} alt={`${evidence.id} 수행 화면`} />}</details>)}{!detailEvidence && <p>수집된 증거가 없습니다.</p>}<pre>{JSON.stringify(detail.conditionAssessments, null, 2)}</pre></details>
        <label className="tc-field">최종 판정<select value={verdict} onChange={e => setVerdict(e.target.value)}><option>P</option><option>F</option><option>N/A</option><option value="UNJUDGED">미판정</option></select></label>
        <label className="tc-field">결함 검토<select aria-label="결함 검토" value={disposition} onChange={e => setDisposition(e.target.value)}><option value="PENDING">검토 대기</option><option value="CONFIRMED">결함 확정</option><option value="REJECTED">결함 후보 기각</option><option value="RETEST">추가 시험 필요</option></select></label>
        <label className="tc-field">검토 사유<textarea rows={3} maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} /></label>
        <button className="primary-button" disabled={busy || !closed || !reason.trim()} onClick={() => void action(async () => { const next: View = await request(`${base}/${run.runId}/review`, { method: 'POST', body: JSON.stringify({ caseId: detail.caseId, revision: run.snapshot.reviewRevision || 0, result: verdict, reason, disposition }) }); setView(next); setDetail(null); setNotice('사용자 검토를 저장했습니다. 자동 판정과 변경 이력을 보존했습니다.') })}>검토 판정 저장</button>
      </article>}
      <details><summary>이전 실패·검토 이력</summary><pre>{JSON.stringify({ retryOf: run.snapshot.retryOf, previousAttempts: run.snapshot.previousAttempts, reviews: run.snapshot.reviews }, null, 2)}</pre></details>
    </>}
  </section>
}
