import { useCallback, useEffect, useRef, useState } from 'react'
import { apiFetch } from './apiFetch'
import { RunProgress } from './RunProgress'

export type ReviewManual = { fileId: string; originalFilename: string; status: string; format: string }
type Candidate = { id: string; type: 'TYPO' | 'EOL_SOFTWARE'; page: number; quote: string; title: string; reason: string; original?: string; suggestion?: string; reference?: { url: string; scope: string; supportEnd: string; checkedAt: string } }
type Review = { jobId: string; manualFileId: string; filename: string; status: string; createdAt: string; asOf: string; errorCode?: string; stale: boolean; decisions?: Record<string, Decision>; attempt?: number; resumable?: boolean; progress?: { totalPages: number; currentPage: number; completedPages: number; stage: string; elapsedMs: number }; output?: { pageCount: number; blockCount: number; reviewedChunks: number; failedChunks: number; catalogVersion: string; catalogProducts: string[]; candidates: Candidate[]; warnings: string[] } }
type Decision = { decision: 'CONFIRMED' | 'DISMISSED'; note?: string }
type View = { job: Review | null; history: Review[] }
const labels: Record<string, string> = { RUNNING: '매뉴얼 분석 중', PAUSING: '중단 확인 중', PAUSED: '매뉴얼 검토 중단됨', COMPLETED: '검사 완료', PARTIAL: '일부 검사 미완료', FAILED: '분석 실패', INTERRUPTED: '분석 중단' }
const stages: Record<string, string> = { EXTRACTING: 'PDF 텍스트·OCR 확인 중', REVIEWING: '오탈자·지원 종료 표기 검토 중', PAGE_DONE: '페이지 결과 저장 중', FINALIZING: '최종 결과 저장 중', PAUSING: '현재 처리 중단 확인 중', PAUSED: '중단됨', INTERRUPTED: '중단됨', FAILED: '처리 실패', COMPLETED: '검토 완료', PARTIAL: '검토 종료 · 확인 필요' }
const failures: Record<string, string> = { OCR_BUSY: '이전 OCR 작업이 처리 중입니다. 잠시 후 다시 시작해 주세요.', SERVER_RESTARTED: '서버가 재시작되어 중단됐습니다. 저장된 페이지부터 이어서 검토할 수 있습니다.', RESULT_TOO_LARGE: '결과 크기 한도를 초과했습니다. 문서를 나누어 다시 등록해 주세요.', TEXT_TOO_LARGE: '추출 텍스트 한도를 초과했습니다. 문서를 나누어 다시 등록해 주세요.', CONTEXT_CHANGED: '검사 기준 또는 모델 설정이 변경됐습니다. 기존 결과는 보존되며 새 검토가 필요합니다.', STOP_NOT_CONFIRMED: '처리 중단을 아직 확인하지 못했습니다. 중단 확인을 재시도해 주세요. 15분 안에 확인되지 않으면 자동으로 정리됩니다.', SERVICE_UNAVAILABLE: '매뉴얼 검토 서비스에 연결할 수 없어 중단했습니다. 서비스가 켜진 뒤 이어서 검토할 수 있습니다.', STOP_CONFIRMED_LATE: '처리 중단이 늦게 확인되어 정리했습니다. 저장된 페이지부터 이어서 검토할 수 있습니다.', STOP_TIMEOUT: '15분 동안 처리 중단을 확인하지 못해 정리했습니다. 저장된 페이지부터 이어서 검토할 수 있습니다.' }

export function ManualReviewPanel({ manual, onDocuments, submissionId, memberId, active = true }: { manual?: ReviewManual; onDocuments: () => void; submissionId: string; memberId: string; active?: boolean }) {
  const [view, setView] = useState<View>({ job: null, history: [] })
  const [error, setError] = useState(''), [loading, setLoading] = useState(false), [starting, setStarting] = useState(false)
  const [selected, setSelected] = useState(''), [filter, setFilter] = useState('ALL')
  const action = useRef<AbortController | null>(null)
  const mutation = useRef(0)
  const [receivedAt, setReceivedAt] = useState(Date.now())
  const [pendingAction, setPendingAction] = useState('')
  const pdf = manual?.status === 'PARSED' && manual.format.toUpperCase() === 'PDF'
  const base = `/api/submissions/${submissionId}/manual-reviews`
  const load = useCallback(async (signal: AbortSignal) => {
    const generation = mutation.current
    setLoading(true)
    try {
      const response = await apiFetch(base + (selected ? `?job=${encodeURIComponent(selected)}` : ''), { signal, headers: { 'X-Member-Id': memberId } })
      const value = await response.json() as View
      if (!signal.aborted && generation === mutation.current) { setView({job:value.job ?? null,history:value.history ?? []}); setReceivedAt(Date.now()); setError('') }
    } catch (e) { if (!signal.aborted) setError(e instanceof Error ? e.message : '검토 이력을 불러오지 못했습니다.') }
    finally { if (!signal.aborted) setLoading(false) }
  }, [base, memberId, selected])
  useEffect(() => {
    if (!active || !manual || !submissionId) return
    const controller = new AbortController()
    void load(controller.signal)
    const timer = ['RUNNING', 'PAUSING'].includes(view.job?.status || '') ? setInterval(() => void load(controller.signal), 3000) : undefined
    return () => { controller.abort(); if (timer) clearInterval(timer) }
  }, [active, manual?.fileId, submissionId, load, view.job?.status])
  useEffect(() => () => action.current?.abort(), [])
  const start = async (kind = 'start') => {
    mutation.current++
    const controller = new AbortController(); action.current = controller
    setStarting(true); setPendingAction(kind); setError('')
    try {
      const target = kind === 'start' ? base : `${base}/${view.job!.jobId}/${kind}`
      const response = await apiFetch(target, { method: 'POST', signal: controller.signal, headers: { 'X-Member-Id': memberId } })
      const value = await response.json() as View
      if (!controller.signal.aborted) { mutation.current++; setView(value); setReceivedAt(Date.now()); setSelected(kind === 'start' ? '' : value.job!.jobId); if (kind === 'start') setFilter('ALL') }
    } catch (e) { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : '검토를 시작하지 못했습니다.') }
    finally { if (!controller.signal.aborted) { setStarting(false); setPendingAction('') } }
  }
  const decide = async (candidate: string, decision?: 'CONFIRMED' | 'DISMISSED') => {
    if (!view.job) return
    mutation.current++
    setError('')
    try {
      const target = `${base}/${view.job.jobId}/candidates/${encodeURIComponent(candidate)}/decision`
      const response = await apiFetch(target, decision
        ? { method: 'PUT', headers: { 'X-Member-Id': memberId, 'Content-Type': 'application/json' }, body: JSON.stringify({ decision }) }
        : { method: 'DELETE', headers: { 'X-Member-Id': memberId } })
      const value = await response.json() as View
      mutation.current++; setView(current => ({ job: value.job ?? current.job, history: current.history }))
    } catch (e) { setError(e instanceof Error ? e.message : '후보 판정을 저장하지 못했습니다.') }
  }
  if (!manual || manual.status !== 'PARSED') return <div className="workspace-empty">
    <h2>제품 매뉴얼을 등록해 주세요</h2>
    <p>01 자료 등록에서 제품 매뉴얼의 업로드와 문서 처리를 완료하면 검토 대상을 확인할 수 있습니다.</p>
    <button type="button" className="primary-button" onClick={onDocuments}>자료 등록으로 이동</button>
  </div>
  return <section className="manual-review-panel" aria-label="매뉴얼 검토 설정">
    <div className="manual-review-target">
      <div><h2>검토 대상 매뉴얼</h2><p>{manual.originalFilename}</p><small>{manual.format.toUpperCase()} · 등록 완료</small></div>
      <span className="workspace-badge">모든 결과는 확인 필요 후보</span>
    </div>
    <p>화면 수집이나 05 시험 실행 없이, 등록된 제품 매뉴얼을 대상으로 검토합니다.</p>
    <div className="manual-review-scopes">
      <article><h3>오탈자 검토</h3><p>철자 오류와 중복·누락된 글자 등 오탈자 후보를 확인합니다.</p><small>제품명·전문 용어는 문맥을 확인한 뒤 검토합니다.</small></article>
      <article><h3>지원 종료 SW 표기 검토</h3><p>지원 환경에 기재된 운영체제·소프트웨어의 지원 종료 여부를 확인합니다.</p><small>제품·버전·에디션·지원 유형과 기준일을 확인해 후보로 제시합니다.</small></article>
    </div>
    <div className="manual-review-pending">
      <h3>자동 검토</h3>
      <p>원문은 수정하지 않습니다. 텍스트가 있는 PDF 페이지는 직접 읽고, 스캔 페이지는 로컬 OCR로 처리합니다. 이미지 안의 글자를 검사하지 못한 경우 별도로 알립니다. 문서가 길면 처리에 시간이 걸립니다.</p>
      {!pdf && <p role="status">현재 자동 검토는 50 MiB 이하 PDF 매뉴얼을 지원합니다.</p>}
      <div className="manual-review-toolbar">
        <button type="button" className="primary-button" disabled={!pdf || loading || starting || ['RUNNING', 'PAUSING'].includes(view.job?.status || '')} onClick={() => void start()}>{pendingAction === 'start' ? '검토 요청 중…' : '매뉴얼 검토 시작'}</button>
        {['RUNNING', 'PAUSING'].includes(view.job?.status || '') && <button type="button" className="quiet-button" disabled={starting} onClick={() => void start('pause')}>{pendingAction === 'pause' ? '중단 요청 중…' : view.job?.status === 'PAUSING' ? '중단 확인 재시도' : '검토 중단'}</button>}
        {view.job?.resumable && !view.job.stale && !['RUNNING', 'PAUSING'].includes(view.job.status) && <button type="button" className="primary-button" disabled={!pdf || starting || loading} onClick={() => void start('resume')}>{pendingAction === 'resume' ? '이어 작업 요청 중…' : '이어서 검토'}</button>}
        <button type="button" className="quiet-button" disabled={loading || starting} onClick={() => { const controller=new AbortController(); action.current?.abort(); action.current=controller; void load(controller.signal) }}>검토 이력 새로고침</button>
      </div>
      {error && <p role="alert" className="error-copy">{error}</p>}
      {view.history.length > 0 && <label className="manual-review-history">검토 이력<select aria-label="검토 이력" value={selected} onChange={e => setSelected(e.target.value)}><option value="">최신 검토</option>{view.history.map(item => <option key={item.jobId} value={item.jobId}>{new Date(item.createdAt).toLocaleString('ko-KR')} · {labels[item.status] || item.status}</option>)}</select></label>}
    </div>
    {!view.job && !loading && <p>아직 검토 이력이 없습니다. 검토 시작 버튼을 눌러 분석하세요.</p>}
    {view.job && <section className="manual-review-results" aria-label="매뉴얼 검토 결과">
      <h3>{labels[view.job.status] || view.job.status}</h3>
      <p>검토 기준일 {view.job.asOf} · {view.job.filename}</p>
      {view.job.progress && <section aria-label="매뉴얼 검토 진행 현황"><RunProgress
        key={`${view.job.jobId}:${view.job.attempt}:${['RUNNING', 'PAUSING'].includes(view.job.status)}`}
        title={labels[view.job.status] || view.job.status} active={['RUNNING', 'PAUSING'].includes(view.job.status)}
        taskKey={`${view.job.jobId}:${view.job.attempt}`} receivedAt={receivedAt} elapsedMs={view.job.progress.elapsedMs}
        tone={view.job.status === 'COMPLETED' ? 'success' : ['RUNNING', 'PAUSING'].includes(view.job.status) ? 'active' : 'neutral'}
        completed={view.job.progress.completedPages} total={view.job.progress.totalPages || undefined}
        current={view.job.progress.stage === 'COUNTING' ? view.job.progress.totalPages ? '전체 페이지 수와 원본 확인 중' : '전체 페이지 수 확인 중' : `${stages[view.job.progress.stage] || '처리 중'} · 현재 ${view.job.progress.currentPage} / ${view.job.progress.totalPages}페이지`}
        progressText={view.job.progress.totalPages ? `완료 ${view.job.progress.completedPages} / ${view.job.progress.totalPages}페이지` : '페이지 수 확인 전'}
        progressAriaLabel="매뉴얼 페이지 검토 진행률"
        metrics={[{label:'완료 페이지',value:view.job.progress.completedPages},...(view.job.progress.totalPages ? [{label:'남은 페이지',value:Math.max(0,view.job.progress.totalPages-view.job.progress.completedPages)}] : [])]}
        note="완료한 페이지와 후보는 저장됩니다. 중단한 페이지는 이어서 검토할 때 다시 처리하며, 중단해 둔 시간은 실행 시간에 포함하지 않습니다."
      /></section>}
      {view.job.stale && <p role="status">이 결과는 이전 매뉴얼의 검토 이력입니다. 현재 매뉴얼을 새로 검토해 주세요.</p>}
      {view.job.status === 'RUNNING' && <p role="status"><span className="spinner" /> 로컬 OCR·오탈자·지원 종료 표기를 분석 중입니다. 다른 탭으로 이동해도 서버에서 계속 처리합니다.</p>}
      {['FAILED', 'INTERRUPTED'].includes(view.job.status) && <p role="status">{failures[view.job.errorCode || ''] || '로컬 OCR·Qwen 서비스 연결 또는 문서 처리에 실패했습니다. 검사 완료로 취급하지 않습니다.'}</p>}
      {view.job.status === 'PAUSING' && <p role="status">{failures[view.job.errorCode || ''] || '현재 OCR·LLM 처리의 중단을 확인하고 있습니다. 확인되면 이어서 검토할 수 있습니다.'}</p>}
      {view.job.output && <>
        <p>{view.job.output.pageCount}페이지 · 오탈자 검사 완료 {view.job.output.reviewedChunks}구간 · 미완료 {view.job.output.failedChunks}구간</p>
        <p>지원 종료 기준: {view.job.output.catalogProducts.join(', ')} · {view.job.output.catalogVersion}</p>
        {view.job.output.warnings.map((warning, index) => <p key={index} className="manual-review-warning">{warning}</p>)}
        <label className="manual-review-filter">후보 유형<select aria-label="후보 유형" value={filter} onChange={e => setFilter(e.target.value)}><option value="ALL">전체</option><option value="TYPO">오탈자</option><option value="EOL_SOFTWARE">지원 종료 SW</option></select></label>
        {view.job.output.candidates.length === 0 && <p>완료된 검사 범위에서 후보를 찾지 못했습니다. 문서 전체에 결함이 없다는 뜻은 아닙니다.</p>}
        <div className="manual-review-candidates">{view.job.output.candidates.filter(item => filter === 'ALL' || item.type === filter).map(item => <article key={item.id}>
          <div className="manual-review-target"><h4>{item.title}</h4><span className="workspace-badge">확인 필요</span></div>
          <p>PDF {item.page}페이지</p><blockquote>{item.quote}</blockquote>
          {item.type === 'TYPO' && <p><del>{item.original}</del> → <strong>{item.suggestion}</strong></p>}
          <p>{item.reason}</p>
          <div className="manual-review-toolbar" role="group" aria-label={`${item.title} 판정`}>
            {(['CONFIRMED', 'DISMISSED'] as const).map(kind => <button key={kind} type="button" className={view.job!.decisions?.[item.id]?.decision === kind ? 'primary-button' : 'quiet-button'} aria-pressed={view.job!.decisions?.[item.id]?.decision === kind} onClick={() => void decide(item.id, kind)}>{kind === 'CONFIRMED' ? '문서 결함으로 확인' : '문제 아님으로 기각'}</button>)}
            {view.job!.decisions?.[item.id] && <button type="button" className="quiet-button" onClick={() => void decide(item.id)}>판정 취소</button>}
          </div>
          {item.reference && <p>일반 지원 종료일 {item.reference.supportEnd} · 기준 확인일 {item.reference.checkedAt}<br />{item.reference.scope} · <a href={/^https:\/\/learn\.microsoft\.com\//.test(item.reference.url) ? item.reference.url : undefined} target="_blank" rel="noreferrer">공식 지원 기준</a></p>}
        </article>)}</div>
      </>}
    </section>}
  </section>
}
