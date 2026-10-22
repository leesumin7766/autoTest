import { useCallback, useEffect, useRef, useState } from 'react'

type Row = { rowId: string; originRowId?: string; included: boolean; menuPath: string[]; functionName: string; menuDescription: string
  validationIssues?: {code:string;message:string;sourceId?:string;quote?:string}[]
  location: string; verificationStatus: string; rowType?: string; placementStatus?: string; evidenceStatus?: string; connectionStatus?: string; evidence: { sourceId: string; quote: string }[]; matchedMenuKeys: string[]; reviewNotes: string[] }
type Source = { id: string; kind: string; filename?: string; role?: string; pageIndex?: number; text?: string }
type Job = { jobId: string; crawlJobId: string; productName: string; status: string; revision: number; stale: boolean; generationMode: string
  newerCrawlAvailable?: boolean
  targetUrl?: string
  webStructure?: { version: string; nodes: { path: string[]; kind: string; depthReviewRequired?: boolean }[] }
  errorCode?: string; warnings: string[]; sources: Source[]; progress?: { completed: number; total: number }; output?: { rows: Row[]; completionStatus?: string; issueCount?: number; diagnosticsTruncated?: boolean; issues?: { sourceId?: string; segmentStart?: number; code: string; rowIndex?: number; proposedRow?: unknown; expectedSpanIds?: string[]; fields?: unknown }[] } }
type View = { job: Job | null; history: { jobId: string; productName: string; status: string }[]; crawls: { jobId: string; pageCount: number; createdAt: string; targetUrl?: string }[] }
const locations: Record<string,string> = { HEADER: '상단', FOOTER: '하단', SIDEBAR: '측면', CONTENT: '본문·탭', UNKNOWN: '미분류' }
const verification: Record<string,string> = { UNVERIFIED:'근거 미확인 · 사용자 판단 필요', DOCUMENT_ONLY:'문서 근거 확인 · 화면 연결 미확인', SCREEN_OBSERVED:'화면에서 발견', MATCH_PROPOSED:'문서·화면 연결 제안', INCOMPLETE:'화면 확인 불완전' }
type MenuBranch={title:string;children:MenuBranch[]}
function observedBranches(paths:string[][]){const roots:MenuBranch[]=[];for(const path of paths){let level=roots;for(const title of path){let item=level.find(node=>node.title===title);if(!item){item={title,children:[]};level.push(item)}level=item.children}}return roots}
function renderBranches(nodes:MenuBranch[]){return <ul>{nodes.map(node=><li key={node.title}>{node.title}{node.children.length>0&&renderBranches(node.children)}</li>)}</ul>}

export function FunctionalMenuPanel({ submissionId, memberId, ready }: { submissionId: string; memberId: string; ready: boolean }) {
  const base = `/api/submissions/${submissionId}/functional-menu`
  const [view, setView] = useState<View>({ job:null, history:[], crawls:[] })
  const [selected, setSelected] = useState(''), [crawl, setCrawl] = useState(''), [product, setProduct] = useState('')
  const [rows, setRows] = useState<Row[]>([]), [dirty, setDirty] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [prompt, setPrompt] = useState(''), [external, setExternal] = useState(''), [source, setSource] = useState<Source | null>(null), [image, setImage] = useState('')
  const [notice, setNotice] = useState('')
  const promptArea = useRef<HTMLTextAreaElement>(null)
  const modal = useRef<HTMLDialogElement>(null)
  const job = view.job, running = job?.status === 'GENERATING'
  const request = useCallback(async (url: string, options?: RequestInit) => {
    const response = await fetch(url, { ...options, headers: { 'X-Member-Id': memberId, ...(options?.body ? {'Content-Type':'application/json'} : {}) } })
    let body
    try { body = await response.json() } catch { throw new Error('서비스에 다시 연결하고 있습니다. 잠시 후 다시 시도해 주세요.') }
    if (!response.ok) throw new Error(body.message || '통합 메뉴트리 요청을 처리하지 못했습니다.')
    return body
  }, [memberId])
  const apply = (next: View) => { setView(next); setRows(next.job?.output?.rows || []); setDirty(false) }
  const refresh = useCallback(async (signal?: AbortSignal) => {
    const next = await request(`${base}${selected ? `?job=${selected}` : ''}`, { signal }) as View
    if (!signal?.aborted) { setView(next); if (!dirty) setRows(next.job?.output?.rows || []) }
  }, [request, base, selected, dirty])
  useEffect(() => { if (!ready) return; const controller = new AbortController(); void refresh(controller.signal).catch(failure => { if (!controller.signal.aborted) setError(String(failure.message)) }); return () => controller.abort() }, [ready, refresh])
  useEffect(() => { if (!running) return; const timer = window.setInterval(() => { void refresh().catch(failure => setError(failure.message)) }, 3000); return () => window.clearInterval(timer) }, [running, refresh])
  useEffect(() => () => { if (image) URL.revokeObjectURL(image) }, [image])
  const action = async (work: () => Promise<void>) => { setBusy(true); setError(''); try { await work() } catch (failure) { setError(failure instanceof Error ? failure.message : '요청 실패') } finally { setBusy(false) } }
  const makePrompt = async (jobId: string) => { setPrompt(''); setNotice(''); const result=await request(`${base}/${jobId}/prompt`,{method:'POST'}); if(result.instructionVersion!=='self-llm-v2')throw new Error('공통 지침 버전이 다릅니다. 화면과 서비스를 최신 버전으로 갱신하세요.'); setPrompt(result.prompt) }
  const start = async (mode: string) => { const next = await request(base,{method:'POST',body:JSON.stringify({ productName:product, crawlJobId:crawl || view.crawls[0]?.jobId, mode })}); setSelected(next.job.jobId); apply(next); setPrompt(''); setExternal(''); setNotice(''); if(mode==='EXTERNAL')await makePrompt(next.job.jobId) }
  const copyPrompt = async () => { try { await navigator.clipboard.writeText(prompt); setNotice('제품별 프롬프트 전체를 복사했습니다. 공통 지침을 등록한 LLM에 붙여넣으세요.') } catch { promptArea.current?.focus(); promptArea.current?.select(); setNotice('자동 복사를 사용할 수 없어 전체 내용을 선택했습니다. Ctrl+C로 복사하세요.') } }
  const savePrompt = () => { const url=URL.createObjectURL(new Blob([prompt],{type:'text/plain;charset=utf-8'})); const link=document.createElement('a');link.href=url;link.download=`menu-tree-${job?.jobId || 'request'}.txt`;link.click();window.setTimeout(()=>URL.revokeObjectURL(url),1000) }
  const save = async (approve: boolean) => { if (!job) return; apply(await request(`${base}/${job.jobId}/${approve ? 'approval' : 'draft'}`,{method:'PUT',body:JSON.stringify({revision:job.revision,rows})})) }
  const update = (index: number, values: Partial<Row>) => { setRows(current => current.map((row,i) => i===index ? {...row,...values} : row)); setDirty(true) }
  const pathChange = (index: number, depth: number, value: string) => { const path = [0,1,2].map(i => rows[index].menuPath[i] || ''); path[depth]=value; while(path.length && !path.at(-1)) path.pop(); update(index,{menuPath:path}) }
  const download = async (draft: boolean) => { if (!job) return; const response = await fetch(`${base}/${job.jobId}/download?draft=${draft}`,{headers:{'X-Member-Id':memberId}}); if(!response.ok){const body=await response.json();throw new Error(body.message)} const url=URL.createObjectURL(await response.blob()); const anchor=document.createElement('a');anchor.href=url;anchor.download=`${job.productName}-메뉴트리${draft?'-초안':''}.xlsx`;anchor.click();window.setTimeout(()=>URL.revokeObjectURL(url),1000) }
  const showEvidence = async (id: string) => { if (!job) return; const evidence=await request(`${base}/${job.jobId}/evidence?sourceId=${encodeURIComponent(id)}`);setSource(evidence);setImage('');modal.current?.showModal(); if(evidence.pageIndex!==undefined){const response=await fetch(`/api/submissions/${submissionId}/playwright-crawls/${job.crawlJobId}/pages/${evidence.pageIndex}/screenshot`,{headers:{'X-Member-Id':memberId}});if(response.ok)setImage(URL.createObjectURL(await response.blob()))} }
  if (!ready) return null
  return <section className="functional-menu-panel" aria-label="통합 메뉴트리 작성">
    <h2>통합 메뉴트리 · 기능 설명 · Excel</h2>
    <aside><h3>내 LLM으로 메뉴트리 작성</h3>
      <p>① 공통 지침을 LLM에 한 번 등록 → ② 화면 수집 완료 후 제품별 프롬프트 복사 → ③ LLM JSON 응답 가져오기 → ④ 검토·Excel 출력</p>
      <a className="quiet-button" href="/downloads/menu-tree-common-rules.md" download>공통 작성 규칙.md 다운로드</a>
      <p>지침 버전 self-llm-v2 · 기존 v1 지침은 새 MD로 교체하세요. LLM은 E0001 같은 근거 번호만 선택하고 원문 인용은 프로그램이 복원합니다. 화면 이미지 자체는 포함되지 않습니다.</p>
    </aside>
    <p>화면 수집 결과 + 기능리스트 + 매뉴얼을 고정해 작성합니다. 제품명은 1depth, 분류는 2~4depth, 마지막 기능은 5depth에 출력합니다.</p>
    <div className="playwright-grid"><label>Excel 1depth 제품명<input value={product} maxLength={120} onChange={event=>setProduct(event.target.value)} disabled={busy||running} /></label>
      <label>분석할 화면 수집 실행<select value={crawl || view.crawls[0]?.jobId || ''} disabled={busy||running} onChange={event=>setCrawl(event.target.value)}>
        {!view.crawls.length && <option value="">완료된 화면 수집이 필요합니다</option>}{view.crawls.map(item=><option key={item.jobId} value={item.jobId}>{item.targetUrl} · {new Date(item.createdAt).toLocaleString()} · {item.pageCount}개 · {item.jobId.slice(0,8)}</option>)}
      </select></label></div>
    <div className="dialog-actions"><button type="button" className="primary-button" disabled={busy||running||dirty||!product.trim()||!view.crawls.length} onClick={()=>void action(()=>start('EXTERNAL'))}>내 LLM용 제품 프롬프트 만들기</button>
      <button type="button" className="quiet-button" disabled={busy||running||dirty||!product.trim()||!view.crawls.length} onClick={()=>void action(()=>start('AUTO'))}>로컬 LLM 자동 생성</button>
      <button type="button" disabled={busy||dirty} onClick={()=>void action(()=>refresh())}>수집 결과·작업 새로고침</button></div>
    <p>업로드한 기능리스트·매뉴얼과 같은 제품의 수집 대상을 선택하세요.</p>
    {error&&<p className="error-copy" role="alert">{error}</p>}
    {notice&&<p role="status">{notice}</p>}
    {view.history.length>0&&<label>작성 이력<select value={selected||job?.jobId||''} disabled={busy||dirty} onChange={event=>{setSelected(event.target.value);setPrompt('');setExternal('')}}>{view.history.map(item=><option key={item.jobId} value={item.jobId}>{item.productName} · {item.status} · {item.jobId.slice(0,8)}</option>)}</select></label>}
    {job&&<>
      <p role="status">{job.productName} · {job.status} · {job.generationMode}{running&&` · ${job.progress?.completed||0}/${job.progress?.total||job.sources.length}개 근거 분석`}</p>
      {job.targetUrl&&<p>고정한 수집 대상: {job.targetUrl}</p>}
      {job.webStructure&&<details open><summary>실제 웹 메뉴 구조 · {job.productName}</summary>
        <p>화면에서 관찰한 순서와 그룹이 분류 기준입니다. 상단바·하단바는 실제 영역이 확인된 경우에만 포함됩니다.</p>
        {renderBranches(observedBranches(job.webStructure.nodes.map(node=>node.path)))}
        <p>웹 위치 미확인 기능 {rows.filter(row=>row.placementStatus==='UNASSIGNED').length}개는 별도 배치 검토 대상입니다.</p></details>}
      {job.stale&&<p className="error-copy">문서가 변경됐습니다. 새 문서와 수집 결과로 다시 생성하세요.</p>}
      {job.newerCrawlAvailable&&<p className="functional-menu-warning">더 최신 수집 실행이 있습니다. 이 초안은 선택했던 실행 결과를 유지합니다.</p>}
      {job.generationMode==='MOCK'&&<p className="error-copy">개발용 모의 결과입니다. 문서를 실제로 분석한 결과가 아니며 최종 승인할 수 없습니다.</p>}
      {job.errorCode&&<p className="error-copy">생성 오류: {job.errorCode}. 근거 자료는 보존됩니다. 외부 LLM 프롬프트로 이어갈 수 있습니다.</p>}
      {job.output?.completionStatus==='PARTIAL'&&<p className="functional-menu-warning">부분 초안입니다. 정상 행은 보존되며 미완료·검증 실패 구간은 아래 진단에서 확인하세요. 전체 결과를 보완하기 전 최종 승인할 수 없습니다.</p>}
      {!!job.output?.issues?.length&&<details><summary>미확인 구간·검증 진단 ({job.output.issueCount || job.output.issues.length}건)</summary>
        {job.output.issues.map((issue,index)=><article key={index}><p>{issue.code} · {issue.sourceId || '생성 행'}{issue.segmentStart!==undefined&&` · 구간 내 위치 ${issue.segmentStart}`}{issue.rowIndex!==undefined&&` · ${issue.rowIndex+1}행`}</p>
          {issue.sourceId&&<button type="button" onClick={()=>void action(()=>showEvidence(issue.sourceId!))}>입력 근거 보기</button>}
          {issue.proposedRow!==undefined&&<pre>{JSON.stringify({검증필드:issue.fields,허용근거ID:issue.expectedSpanIds,생성응답:issue.proposedRow},null,2)}</pre>}</article>)}
        {job.output.diagnosticsTruncated&&<p>화면 진단은 최대 100건이며 전체 실패 건수는 위에 표시됩니다.</p>}</details>}
      {job.warnings?.map((warning,index)=><p className="functional-menu-warning" key={index}>{warning}</p>)}
      {running&&<button type="button" disabled={busy} onClick={()=>void action(async()=>apply(await request(`${base}/${job.jobId}/cancel`,{method:'POST'})))}>생성 중단</button>}
      {!running&&<details open={job.generationMode==='EXTERNAL'||!!prompt}><summary>제품별 프롬프트·JSON 가져오기</summary><p>공통 작성 규칙 self-llm-v2을 먼저 등록하세요. 아래 요청에는 제품명·고정된 전체 문서 및 수집 근거·웹 구조가 포함됩니다. 자료는 자동으로 외부 LLM에 전송되지 않습니다.</p>
        <button type="button" disabled={busy||dirty} onClick={()=>void action(()=>makePrompt(job.jobId))}>이 실행의 제품 프롬프트 다시 만들기</button>
        {prompt&&<><p>전체 {prompt.length.toLocaleString()}자 · LLM 입력 한도를 확인하세요. 자료를 임의로 줄이지 않았습니다.</p><textarea ref={promptArea} readOnly rows={10} value={prompt} aria-label="제품별 메뉴트리 프롬프트"/><button type="button" onClick={()=>void copyPrompt()}>제품 프롬프트 전체 복사</button><button type="button" onClick={savePrompt}>동일 내용 TXT 다운로드</button></>}
        <label>LLM JSON 응답<textarea rows={5} value={external} onChange={event=>setExternal(event.target.value)} placeholder={'{"requestId":"요청 ID","rows":[...]}'} /></label>
        <p>인용 불일치·없는 근거 ID는 경고로 표시하고 초안을 작성합니다. JSON 형식·길이·depth 제한 오류는 수정이 필요합니다. 경고가 있는 기능도 사용자가 포함 여부를 판단할 수 있습니다.</p>
        <button type="button" disabled={busy||dirty||!external.trim()} onClick={()=>void action(async()=>{const text=external.trim().replace(/^```(?:json)?\s*/i,'').replace(/\s*```$/,''); const parsed=JSON.parse(text);if(!Array.isArray(parsed.rows)||!parsed.rows.length)throw new Error('rows 배열에 기능 행이 포함된 JSON 응답을 붙여넣으세요.');const next=await request(`${base}/${job.jobId}/import`,{method:'POST',body:JSON.stringify({revision:job.revision,rows:parsed.rows,requestId:parsed.requestId})});apply(next);const count=next.job.output.rows.filter((row:Row)=>row.evidenceStatus==='UNVERIFIED').length;setNotice(`초안으로 가져왔습니다. 근거 미확인 ${count}개 기능의 경고와 의미적 연결·누락 여부를 검토하세요.`)})}>근거 검증 후 가져오기</button></details>}
      {rows.length>0&&<><p>{rows.length}개 기능 · {rows.filter(row=>row.included).length}개 포함{dirty?' · 저장하지 않은 변경이 있습니다':''}</p>
        {rows.some(row=>row.evidenceStatus==='UNVERIFIED')&&<details open><summary>근거 검증 경고 · {rows.filter(row=>row.evidenceStatus==='UNVERIFIED').length}개 기능</summary>
          <p>아래 행은 검증 통과가 아닙니다. 원본 인용을 유지한 검토 초안이며, 포함 여부는 사용자가 판단합니다. 경고는 저장·Excel·승인 후에도 유지됩니다.</p>
          {rows.filter(row=>row.evidenceStatus==='UNVERIFIED').map(row=><article key={row.rowId}><strong>{row.functionName}</strong>{row.validationIssues?.map((issue,i)=><p key={i}>{issue.message} {issue.sourceId&&`근거 ID: ${issue.sourceId}`} {issue.quote&&`인용: ${issue.quote}`}</p>)}</article>)}</details>}
        <div className="functional-menu-table-wrap"><table className="functional-menu-table"><thead><tr><th>포함</th><th>1depth</th><th>2depth</th><th>3depth</th><th>4depth</th><th>5depth 기능</th><th>메뉴 설명</th><th>위치·확인 상태·근거</th></tr></thead>
          <tbody>{rows.map((row,index)=><tr key={row.rowId}><td><input type="checkbox" checked={row.included} disabled={busy||running} onChange={event=>update(index,{included:event.target.checked})}/></td><td>{job.productName}</td>
            {job.webStructure&&row.placementStatus==='UNASSIGNED'?<td colSpan={3}><select aria-label={`${index+1}행 웹 메뉴 배치`} disabled={busy||running} value={JSON.stringify(row.menuPath)} onChange={event=>{const path=JSON.parse(event.target.value);update(index,{menuPath:path,included:path.length>0})}}>
              <option value="[]">웹 위치 미확인 · 문서 검토 항목</option>{job.webStructure.nodes.filter(node=>node.path.length<=3).map(node=><option key={JSON.stringify(node.path)} value={JSON.stringify(node.path)}>{node.path.join(' / ')}</option>)}</select></td>:
              [0,1,2].map(depth=><td key={depth}><input aria-label={`${index+1}행 ${depth+2}depth`} value={row.menuPath[depth]||''} placeholder="-" maxLength={120} disabled={busy||running||!!job.webStructure} onChange={event=>pathChange(index,depth,event.target.value)}/></td>)}
            <td><input aria-label={`${index+1}행 기능명`} value={row.rowType==='MENU_OBSERVATION'?'':row.functionName} placeholder={row.rowType==='MENU_OBSERVATION'?'메뉴 구조 항목':''} maxLength={160} disabled={busy||running||row.rowType==='MENU_OBSERVATION'} onChange={event=>update(index,{functionName:event.target.value})}/><button type="button" disabled={busy||running||row.rowType==='MENU_OBSERVATION'} onClick={()=>{setRows(current=>[...current.slice(0,index+1),{...row,rowId:crypto.randomUUID(),originRowId:row.originRowId||row.rowId},...current.slice(index+1)]);setDirty(true)}}>기능 분리</button></td>
            <td><textarea aria-label={`${index+1}행 메뉴 설명`} rows={3} value={row.menuDescription} maxLength={600} disabled={busy||running} onChange={event=>update(index,{menuDescription:event.target.value})}/></td>
            <td><select value={row.location} disabled={busy||running} onChange={event=>update(index,{location:event.target.value})}>{Object.entries(locations).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select>
              <small>{verification[row.verificationStatus]||row.verificationStatus}</small><small>{row.evidenceStatus==='VALID'?'근거 참조 정상 · ':row.evidenceStatus==='UNVERIFIED'?'⚠ 근거 미확인 · 사용자 검토 필요 · ':''}{row.connectionStatus==='PROPOSED'?'의미적 연결 검토 필요':row.connectionStatus==='NOT_LINKED'?'화면 메뉴 연결 미확인':''}</small>{row.evidence.map((ref,i)=><button type="button" className="text-button" key={i} disabled={busy||!job.sources.some(source=>source.id===ref.sourceId)} onClick={()=>void action(()=>showEvidence(ref.sourceId))}>근거 {i+1}: {ref.quote.slice(0,24)}</button>)}
              {row.reviewNotes.map((note,i)=><small key={i}>{note}</small>)}</td></tr>)}</tbody></table></div>
        <div className="dialog-actions"><button type="button" disabled={busy||running||job.stale||!dirty} onClick={()=>void action(()=>save(false))}>수정 초안 저장</button>
          <button type="button" className="primary-button" disabled={busy||running||job.stale||job.generationMode==='MOCK'||job.output?.completionStatus==='PARTIAL'} onClick={()=>void action(()=>save(true))}>포함한 기능을 검토 완료·승인</button>
          <button type="button" disabled={busy||running||dirty||job.stale||job.status!=='DRAFT'} onClick={()=>void action(()=>download(true))}>초안 Excel</button>
          <button type="button" disabled={busy||dirty||job.stale||job.status!=='APPROVED'} onClick={()=>void action(()=>download(false))}>승인 Excel 다운로드</button>
          {dirty&&<button type="button" onClick={()=>{setDirty(false);setRows(job.output?.rows||[])}}>편집 취소</button>}</div>
      </>}
    </>}
    <dialog ref={modal} className="crawl-preview"><h3>고정된 입력 근거</h3><button type="button" onClick={()=>modal.current?.close()}>닫기</button>{source&&<><p>{source.kind} · {source.filename||source.id}</p><pre>{source.text}</pre></>}{image&&<img src={image} alt="기능 근거 화면"/>}</dialog>
  </section>
}
