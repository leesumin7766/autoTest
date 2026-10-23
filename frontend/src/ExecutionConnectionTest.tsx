import { useEffect, useRef, useState } from 'react'
import type { BrowserCheck } from './RemoteCrawlPanel'
import { browserCheckMessage } from './browserCheckMessages'
import { automationDefaults, type Automation, type Setup } from './PlaywrightSetupPanel'

type Agent = { agentId: string; name: string; online: boolean; busy: boolean }
export function ExecutionConnectionTest({ submissionId, memberId, targetUrl, ignoreHTTPSErrors = false, onUrlChange, onBusyChange }: {
  submissionId: string; memberId: string; targetUrl: string; ignoreHTTPSErrors?: boolean; onUrlChange: (url: string) => void; onBusyChange: (busy: boolean) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [mode, setMode] = useState<'LOCAL' | 'REMOTE'>('LOCAL')
  const [agents, setAgents] = useState<Agent[]>([]), [agent, setAgent] = useState('')
  const [confirmed, setConfirmed] = useState(false), [busy, setBusy] = useState(false)
  const [check, setCheck] = useState<BrowserCheck | null>(null), [error, setError] = useState('')
  const [issued, setIssued] = useState<{ id: string; purpose: 'CONNECTION' | 'LOGIN' } | null>(null)
  const [loginRequired,setLoginRequired]=useState(false),[loginMethod,setLoginMethod]=useState<'FORM_AUTO'|'BASIC_AUTH'>('FORM_AUTO')
  const [automation,setAutomation]=useState<Automation>({...automationDefaults}),[password,setPassword]=useState('')
  const [saved,setSaved]=useState<Setup|null>(null),[setupLoading,setSetupLoading]=useState(false),[savedSuccessfully,setSavedSuccessfully]=useState(false)
  const pendingDraft=useRef<Record<string,unknown>|null>(null)
  const configVersion=useRef('')
  const [tlsAllowed, setTlsAllowed] = useState(ignoreHTTPSErrors), [policyLoading, setPolicyLoading] = useState(false)
  const base = `/api/submissions/${submissionId}/playwright-crawls`
  const request = async (url: string, options: RequestInit = {}) => {
    const response = await fetch(url, { ...options, headers: { 'X-Member-Id': memberId, ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    const data = await response.json()
    if (!response.ok) throw new Error(data.message || '연결 테스트 요청에 실패했습니다.')
    return data
  }
  useEffect(() => { onBusyChange(busy); return () => onBusyChange(false) }, [busy, onBusyChange])
  useEffect(() => { setCheck(null); setError(''); setConfirmed(false);setSavedSuccessfully(false) }, [targetUrl, mode, agent, ignoreHTTPSErrors])
  const invalidate=()=>{setCheck(null);setError('');setSavedSuccessfully(false)}
  const finish=async(next:BrowserCheck)=>{
    if(next.status==='COMPLETED'&&(next.result?.httpStatus||0)>=200&&(next.result?.httpStatus||0)<400&&pendingDraft.current){
      if(pendingDraft.current.loginMethod!=='NONE'&&!next.result?.loginVerified)throw Error('로그인 성공을 확인하지 못했습니다. 로그인 설정을 확인하세요.')
      const response=await request(`/api/submissions/${submissionId}/playwright-setup`,{method:'PUT',body:JSON.stringify(pendingDraft.current)})
      configVersion.current=response.configVersion||'';setSaved(response.setup);setPassword('');setSavedSuccessfully(true)
    }
    pendingDraft.current=null;setBusy(false);setIssued(null)
  }
  useEffect(() => {
    setTlsAllowed(ignoreHTTPSErrors)
    if(!targetUrl.trim()){setPolicyLoading(false);return}
    const controller=new AbortController();setPolicyLoading(true)
    void fetch(`/api/submissions/${submissionId}/playwright-setup/tls-policy?targetUrl=${encodeURIComponent(targetUrl.trim())}`,{headers:{'X-Member-Id':memberId},signal:controller.signal})
      .then(async response=>{if(!response.ok)throw Error('policy unavailable');return response.json()})
      .then(value=>{if(!controller.signal.aborted)setTlsAllowed(ignoreHTTPSErrors || value.ignoreHTTPSErrors===true)})
      .catch(()=>{}).finally(()=>{if(!controller.signal.aborted)setPolicyLoading(false)})
    return ()=>controller.abort()
  },[submissionId,memberId,targetUrl,ignoreHTTPSErrors])
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
        const next: BrowserCheck | undefined = body.checks?.[issued.purpose]
        if (next?.jobId !== issued.id) throw new Error('다른 연결 테스트가 시작되었습니다. 다시 테스트해주세요.')
        setCheck(next)
        if (!['QUEUED', 'RUNNING'].includes(next.status)) { await finish(next); return }
        if (Date.now() >= deadline) throw new Error('상태 확인 시간이 초과되었습니다. 화면 수집 탭에서 진행 상태를 확인해주세요.')
        timer = setTimeout(() => void poll(), 2000)
      } catch (failure) {
        if (!controller.signal.aborted) { pendingDraft.current=null;setError(failure instanceof Error ? failure.message : '상태 조회 실패'); setBusy(false); setIssued(null) }
      }
    }
    timer = setTimeout(() => void poll(), 1000)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [issued, base, memberId])
  const start = async () => {
    setBusy(true); setError(''); setCheck(null);setSavedSuccessfully(false)
    try {
      const purpose=loginRequired?'LOGIN':'CONNECTION'
      const draft={targetUrl:targetUrl.trim(),loginMethod:loginRequired?loginMethod:'NONE',testEnvironmentConfirmed:confirmed,
        loginInstructions:saved?.loginInstructions||'',testAccountLabel:saved?.testAccountLabel||'',crawlScopeNotes:saved?.crawlScopeNotes||'',testDataNotes:saved?.testDataNotes||'',
        automation:{...automation,ignoreHTTPSErrors:tlsAllowed},...(loginRequired&&password?{password}:{}),clearPassword:!loginRequired}
      pendingDraft.current=draft
      const body = await request(base, { method: 'POST', body: JSON.stringify({
        executionMode: mode, agentId: mode === 'REMOTE' ? agent : null, purpose, draft,
      }) })
      const next = body.checks?.[purpose] as BrowserCheck | undefined
      if (!next) throw new Error('연결 테스트 정보를 받지 못했습니다.')
      setCheck(next)
      if (['QUEUED', 'RUNNING'].includes(next.status)) setIssued({ id: next.jobId, purpose })
      else await finish(next)
    } catch (failure) { pendingDraft.current=null;setError(failure instanceof Error ? failure.message : '연결 요청 실패'); setBusy(false) }
  }
  const success = savedSuccessfully && check?.status === 'COMPLETED' && !error && (!loginRequired||check.result?.loginVerified) && (check.result?.httpStatus || 0) >= 200 && (check.result?.httpStatus || 0) < 400
  const open = () => {
    dialog.current?.showModal()
    void request('/api/crawl-agents').then(body => setAgents(body.agents || [])).catch(failure => setError(failure.message))
    setSetupLoading(true)
    void request(`/api/submissions/${submissionId}/playwright-setup`).then(body=>{
      if(configVersion.current!==(body.configVersion||''))invalidate()
      configVersion.current=body.configVersion||''
      const setup=body.setup as Setup|null
      let matches=false;try{matches=!!setup&&new URL(setup.targetUrl).origin===new URL(targetUrl).origin}catch{ /* URL is being edited. */ }
      if(matches&&setup){setSaved(setup);setAutomation({...automationDefaults,...setup.automation});setLoginRequired(['FORM_AUTO','BASIC_AUTH'].includes(setup.loginMethod));setLoginMethod(setup.loginMethod==='BASIC_AUTH'?'BASIC_AUTH':'FORM_AUTO')}
      else{setSaved(null);setAutomation({...automationDefaults});setLoginRequired(false)}
    }).catch(failure=>setError(failure.message)).finally(()=>setSetupLoading(false))
  }
  return <div className="execution-connection">
    <div className="dialog-actions"><button type="button" className="quiet-button" onClick={open}>Test connection</button>
      {success ? <span className="connection-success" role="status">✓ {loginRequired?'연결·로그인 성공':'연결 성공'} · HTTP {check!.result?.httpStatus} · {check!.result?.elapsedMs}ms</span> : <span role="status">{busy ? '연결 테스트 중…' : check?.status === 'COMPLETED' ? `HTTP ${check.result?.httpStatus} · 응답 확인 필요` : check?.status === 'FAILED' || error ? '연결 확인 실패 · 다시 테스트해주세요' : '연결 미확인'}</span>}
    </div>
    <dialog ref={dialog} className="dialog execution-connection-dialog" aria-labelledby="execution-connection-title">
      <div className="section-heading"><h2 id="execution-connection-title">연결 테스트</h2><button type="button" className="quiet-button" onClick={() => dialog.current?.close()}>닫기</button></div>
      <p>로그인이 필요하면 계정으로 로그인까지 확인합니다. 성공한 접속 설정은 시험 실행에 사용하도록 저장합니다.</p>
      <label className="tc-field">테스트할 URL<input type="url" value={targetUrl} disabled={busy} onChange={e => onUrlChange(e.target.value)} /></label>
      <label className="playwright-confirmation"><input type="checkbox" checked={tlsAllowed} disabled={busy || policyLoading} onChange={e=>{setTlsAllowed(e.target.checked);setCheck(null);setError('')}} />이 시험 환경의 인증서 오류 허용</label>
      <label className="playwright-confirmation"><input type="checkbox" checked={loginRequired} disabled={busy||setupLoading} onChange={e=>{setLoginRequired(e.target.checked);invalidate()}} />로그인 필요</label>
      {loginRequired&&<fieldset disabled={busy||setupLoading}>
        <label className="tc-field">로그인 방식<select value={loginMethod} onChange={e=>{setLoginMethod(e.target.value as 'FORM_AUTO'|'BASIC_AUTH');invalidate()}}><option value="FORM_AUTO">일반 로그인</option><option value="BASIC_AUTH">Basic 인증</option></select></label>
        <label className="tc-field">아이디<input autoComplete="off" value={automation.username} onChange={e=>{setAutomation(a=>({...a,username:e.target.value}));invalidate()}} /></label>
        <label className="tc-field">비밀번호<input type="password" autoComplete="new-password" value={password} placeholder={saved?.passwordConfigured?'저장된 비밀번호 사용 · 변경할 때만 입력':''} onChange={e=>{setPassword(e.target.value);invalidate()}} /></label>
        {loginMethod==='FORM_AUTO'&&<details><summary>고급 로그인 설정</summary>{([['loginUrl','로그인 URL'],['successUrl','로그인 성공 URL'],['usernameSelector','아이디 입력칸 선택자'],['passwordSelector','비밀번호 입력칸 선택자'],['submitSelector','로그인 버튼 선택자'],['successSelector','로그인 성공 확인 요소']] as const).map(([key,label])=><label className="tc-field" key={key}>{label}<input value={automation[key]} onChange={e=>{setAutomation(a=>({...a,[key]:e.target.value}));invalidate()}} /></label>)}</details>}
      </fieldset>}
      <label className="tc-field">실행 위치<select value={mode} disabled={busy} onChange={e => setMode(e.target.value as 'LOCAL' | 'REMOTE')}><option value="LOCAL">현재 서버</option><option value="REMOTE">원격 실행 PC</option></select></label>
      {mode === 'REMOTE' && <><label className="tc-field">실행 PC<select value={agent} disabled={busy} onChange={e => setAgent(e.target.value)}><option value="">실행 PC 선택</option>{agents.map(item => <option key={item.agentId} value={item.agentId} disabled={!item.online || item.busy}>{item.name} · {item.online ? item.busy ? '작업 중' : '연결됨' : '연결 안 됨'}</option>)}</select></label><p>실행 PC 등록과 설치는 화면 수집 탭에서 진행해주세요.</p></>}
      <label className="playwright-confirmation"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} />접속 권한이 있는 시험 환경입니다.</label>
      <div className="dialog-actions"><button type="button" className="primary-button" disabled={busy || policyLoading || setupLoading || !confirmed || !targetUrl.trim() || (loginRequired&&(!automation.username.trim()||(!password&&!saved?.passwordConfigured))) || (mode === 'REMOTE' && !agents.some(item => item.agentId === agent && item.online && !item.busy))} onClick={() => void start()}>{busy ? '연결 테스트 중…' : '연결 테스트 시작'}</button></div>
      {busy && <p role="status"><span className="spinner" /> {check?.status === 'QUEUED' ? '실행 대기 중…' : 'Playwright로 접속을 확인하고 있습니다…'}</p>}
      {success && <p className="connection-success" role="status">{loginRequired?'연결·로그인 성공':'연결 성공'} · {check!.agentName} · HTTP {check!.result?.httpStatus} · {check!.result?.elapsedMs}ms · 시험 접속 설정 저장 완료</p>}
      {check?.status === 'COMPLETED' && !success && <p role="status">HTTP {check.result?.httpStatus} 응답입니다. 대상 주소와 접근 권한을 확인해주세요.</p>}
      {check?.status === 'FAILED' && <p role="alert" className="error-copy">{browserCheckMessage(check.errorCode)}</p>}
      {check?.status === 'CANCELED' && <p role="status">연결 테스트가 중단되었습니다.</p>}
      {error && <p role="alert" className="error-copy">{error}</p>}
    </dialog>
  </div>
}
