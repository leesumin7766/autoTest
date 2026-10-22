import { useEffect, useRef, useState } from 'react'
import type { BrowserCheck } from './RemoteCrawlPanel'
import { browserCheckMessage } from './browserCheckMessages'

type Agent = { agentId: string; name: string; online: boolean; busy: boolean }
export function ExecutionConnectionTest({ submissionId, memberId, targetUrl, onUrlChange, onBusyChange }: {
  submissionId: string; memberId: string; targetUrl: string; onUrlChange: (url: string) => void; onBusyChange: (busy: boolean) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [mode, setMode] = useState<'LOCAL' | 'REMOTE'>('LOCAL')
  const [agents, setAgents] = useState<Agent[]>([]), [agent, setAgent] = useState('')
  const [confirmed, setConfirmed] = useState(false), [busy, setBusy] = useState(false)
  const [check, setCheck] = useState<BrowserCheck | null>(null), [error, setError] = useState('')
  const [issued, setIssued] = useState<{ id: string; url: string } | null>(null)
  const base = `/api/submissions/${submissionId}/playwright-crawls`
  const request = async (url: string, options: RequestInit = {}) => {
    const response = await fetch(url, { ...options, headers: { 'X-Member-Id': memberId, ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    const data = await response.json()
    if (!response.ok) throw new Error(data.message || '연결 테스트 요청에 실패했습니다.')
    return data
  }
  useEffect(() => { onBusyChange(busy); return () => onBusyChange(false) }, [busy, onBusyChange])
  useEffect(() => { setCheck(null); setError(''); setConfirmed(false) }, [targetUrl, mode, agent])
  useEffect(() => {
    if (!issued) return
    const controller = new AbortController(); const deadline = Date.now() + 90000
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const response = await fetch(base, { headers: { 'X-Member-Id': memberId }, signal: controller.signal })
        const body = await response.json()
        if (!response.ok) throw new Error(body.message || '연결 테스트 상태를 확인하지 못했습니다.')
        if (controller.signal.aborted) return
        const next: BrowserCheck | undefined = body.checks?.CONNECTION
        if (next?.jobId !== issued.id) throw new Error('다른 연결 테스트가 시작되었습니다. 다시 테스트해주세요.')
        setCheck(next)
        if (!['QUEUED', 'RUNNING'].includes(next.status)) { setBusy(false); setIssued(null); return }
        if (Date.now() >= deadline) throw new Error('상태 확인 시간이 초과되었습니다. 화면 수집 탭에서 진행 상태를 확인해주세요.')
        timer = setTimeout(() => void poll(), 2000)
      } catch (failure) {
        if (!controller.signal.aborted) { setError(failure instanceof Error ? failure.message : '상태 조회 실패'); setBusy(false); setIssued(null) }
      }
    }
    timer = setTimeout(() => void poll(), 1000)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [issued, base, memberId])
  const start = async () => {
    setBusy(true); setError(''); setCheck(null)
    try {
      const body = await request(base, { method: 'POST', body: JSON.stringify({ executionMode: mode, agentId: mode === 'REMOTE' ? agent : null, purpose: 'CONNECTION', draft: { targetUrl: targetUrl.trim(), loginMethod: 'NONE', testEnvironmentConfirmed: confirmed } }) })
      const next = body.checks?.CONNECTION as BrowserCheck | undefined
      if (!next) throw new Error('연결 테스트 정보를 받지 못했습니다.')
      setCheck(next)
      if (['QUEUED', 'RUNNING'].includes(next.status)) setIssued({ id: next.jobId, url: targetUrl.trim() })
      else setBusy(false)
    } catch (failure) { setError(failure instanceof Error ? failure.message : '연결 요청 실패'); setBusy(false) }
  }
  const success = check?.status === 'COMPLETED' && !error && (check.result?.httpStatus || 0) >= 200 && (check.result?.httpStatus || 0) < 400
  const open = () => {
    dialog.current?.showModal()
    void request('/api/crawl-agents').then(body => setAgents(body.agents || [])).catch(failure => setError(failure.message))
  }
  return <div className="execution-connection">
    <div className="dialog-actions"><button type="button" className="quiet-button" onClick={open}>Test connection</button>
      {success ? <span className="connection-success" role="status">✓ 연결 성공 · HTTP {check!.result?.httpStatus} · {check!.result?.elapsedMs}ms</span> : <span role="status">{busy ? '연결 테스트 중…' : check?.status === 'COMPLETED' ? `HTTP ${check.result?.httpStatus} · 응답 확인 필요` : check?.status === 'FAILED' || error ? '연결 확인 실패 · 다시 테스트해주세요' : '연결 미확인'}</span>}
    </div>
    <dialog ref={dialog} className="dialog execution-connection-dialog" aria-labelledby="execution-connection-title">
      <div className="section-heading"><h2 id="execution-connection-title">연결 테스트</h2><button type="button" className="quiet-button" onClick={() => dialog.current?.close()}>닫기</button></div>
      <p>화면 수집과 같은 Playwright 연결 검사를 수행합니다. 로그인과 TC 시험은 실행하지 않습니다.</p>
      <label className="tc-field">테스트할 URL<input type="url" value={targetUrl} disabled={busy} onChange={e => onUrlChange(e.target.value)} /></label>
      <label className="tc-field">실행 위치<select value={mode} disabled={busy} onChange={e => setMode(e.target.value as 'LOCAL' | 'REMOTE')}><option value="LOCAL">현재 서버</option><option value="REMOTE">원격 실행 PC</option></select></label>
      {mode === 'REMOTE' && <><label className="tc-field">실행 PC<select value={agent} disabled={busy} onChange={e => setAgent(e.target.value)}><option value="">실행 PC 선택</option>{agents.map(item => <option key={item.agentId} value={item.agentId} disabled={!item.online || item.busy}>{item.name} · {item.online ? item.busy ? '작업 중' : '연결됨' : '연결 안 됨'}</option>)}</select></label><p>실행 PC 등록과 설치는 화면 수집 탭에서 진행해주세요.</p></>}
      <label className="playwright-confirmation"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} />접속 권한이 있는 시험 환경입니다.</label>
      <div className="dialog-actions"><button type="button" className="primary-button" disabled={busy || !confirmed || !targetUrl.trim() || (mode === 'REMOTE' && !agents.some(item => item.agentId === agent && item.online && !item.busy))} onClick={() => void start()}>{busy ? '연결 테스트 중…' : '연결 테스트 시작'}</button></div>
      {busy && <p role="status"><span className="spinner" /> {check?.status === 'QUEUED' ? '실행 대기 중…' : 'Playwright로 접속을 확인하고 있습니다…'}</p>}
      {success && <p className="connection-success" role="status">연결 성공 · {check!.agentName} · HTTP {check!.result?.httpStatus} · {check!.result?.elapsedMs}ms</p>}
      {check?.status === 'COMPLETED' && !success && <p role="status">HTTP {check.result?.httpStatus} 응답입니다. 대상 주소와 접근 권한을 확인해주세요.</p>}
      {check?.status === 'FAILED' && <p role="alert" className="error-copy">{browserCheckMessage(check.errorCode)}</p>}
      {check?.status === 'CANCELED' && <p role="status">연결 테스트가 중단되었습니다.</p>}
      {error && <p role="alert" className="error-copy">{error}</p>}
    </dialog>
  </div>
}
