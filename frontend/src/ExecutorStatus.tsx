import { useCallback, useEffect, useRef, useState } from 'react'
import { apiFetch } from './apiFetch'

type Job = { id: string; submissionId: string; kind: string; purpose: string; status: string; createdAt: string; pageCount: number }
type Status = { connected: boolean; busy: boolean; jobs: Job[] }
const title = (job: Job) => job.kind === 'EXECUTION' ? '시험 실행' : ({ CONNECTION: '연결 테스트', LOGIN: '로그인 테스트', CRAWL: '화면 수집' }[job.purpose] || '화면 수집')

export function ExecutorStatus({ memberId }: { memberId: string }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [state, setState] = useState<Status | null>(null)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<string | null>(null)
  const [stopping, setStopping] = useState(false)
  const [notice, setNotice] = useState('')
  const refresh = useCallback(async (signal?: AbortSignal) => {
    try {
      const response = await apiFetch('/api/crawl-agents/local/status', { headers: { 'X-Member-Id': memberId }, signal })
      const value: Status = await response.json()
      if (signal?.aborted) return
      setState(value); setError('')
    } catch (e) { if (!signal?.aborted) setError(e instanceof Error ? e.message : '실행기 상태를 확인하지 못했습니다.') }
  }, [memberId])
  useEffect(() => {
    setState(null); setSelected(null); setNotice('')
    const controller = new AbortController()
    void refresh(controller.signal)
    const timer = setInterval(() => void refresh(controller.signal), 5000)
    return () => { controller.abort(); clearInterval(timer) }
  }, [refresh])
  async function stop(job: Job) {
    setStopping(true); setNotice('')
    try {
      const path = job.kind === 'EXECUTION' ? `executions/${job.id}/cancel` : `playwright-crawls/${job.id}/cancel`
      await apiFetch(`/api/submissions/${job.submissionId}/${path}`, { method: 'POST', headers: { 'X-Member-Id': memberId } })
      setSelected(null); setNotice('중지를 요청했습니다. 실행기 점유 해제까지 잠시 걸릴 수 있습니다.')
      await refresh()
    } catch (e) { setError(e instanceof Error ? e.message : '중지를 요청하지 못했습니다.') }
    finally { setStopping(false) }
  }
  const label = error ? '확인 실패' : !state ? '확인 중' : !state.connected ? '연결 안 됨' : state.busy ? '사용 중' : '사용 가능'
  const tone = error || !state?.connected ? 'unknown' : state.busy ? 'busy' : 'ready'
  return <>
    <button className={`executor-status-button ${tone}`} aria-label="실행기 점유 현황" aria-haspopup="dialog" title="실행기 점유 현황" onClick={() => { dialog.current?.showModal(); void refresh() }}><span className={`executor-dot ${tone}`} aria-hidden="true" /><span>실행기 <strong>{label}</strong></span><svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m5 6 3 3 3-3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg></button>
    <dialog ref={dialog} className="executor-status-dialog" onCancel={() => setSelected(null)} aria-labelledby="executor-title">
      <div className="executor-heading"><h2 id="executor-title">실행기 점유 현황</h2><button className="quiet-button" onClick={() => { dialog.current?.close(); setSelected(null) }}>닫기</button></div>
      <p>현재 서버 · {label}</p><p>화면 수집·연결 테스트·시험 실행이 실행기 하나를 공유합니다. 5초마다 갱신됩니다.</p>
      {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
      {!error && state?.connected && !state.busy && <p className="executor-idle">실행기를 사용할 수 있습니다.</p>}
      {!error && state?.busy && state.jobs.length === 0 && <p>실행기가 작업을 정리 중이거나 다른 회원의 작업이 사용 중입니다. 현재 계정으로 중지할 수 있는 작업이 없습니다.</p>}
      {state?.jobs.map(job => <article className="executor-job" key={job.id}>
        <strong>{title(job)} · {job.status === 'RUNNING' ? '진행 중' : '실행 대기'}</strong>
        <dl><dt>문서 세트</dt><dd>{job.submissionId}</dd><dt>작업 ID</dt><dd>{job.id}</dd><dt>요청 시각</dt><dd>{new Date(job.createdAt).toLocaleString('ko-KR')}</dd>{job.kind === 'CRAWL' && <><dt>저장 화면</dt><dd>{job.pageCount}개</dd></>}</dl>
        {selected === job.id ? <div><p>이 작업을 중지할까요? 이미 저장된 결과는 보존됩니다.</p><button className="primary-button" disabled={stopping} onClick={() => void stop(job)}>{stopping ? '중지 요청 중' : '중지 확인'}</button> <button className="quiet-button" disabled={stopping} onClick={() => setSelected(null)}>계속 실행</button></div> : <button className="quiet-button" disabled={stopping} onClick={() => setSelected(job.id)}>작업 중지</button>}
      </article>)}
      <button className="quiet-button" onClick={() => void refresh()}>새로고침</button>
    </dialog>
  </>
}
