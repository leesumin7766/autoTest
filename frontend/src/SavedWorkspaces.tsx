import { useRef, useState } from 'react'
import { apiFetch } from './apiFetch'

export type SavedWorkspace = { id:string; productId:number; name:string; lastPage:string; createdAt:string; updatedAt:string;
  documentCount:number; parsedDocumentCount:number; deliveryStatus:string; crawlCount:number; menuCount:number; tcSetCount:number; runCount:number }

export function SavedWorkspaces({memberId,workspace,name,onNameChange,onRename,onOpen,busy}:{memberId:string;workspace:SavedWorkspace|null;
  name:string;onNameChange:(name:string)=>void;onRename:()=>Promise<void>;onOpen:(id:string)=>Promise<void>;busy:boolean}) {
  const dialog=useRef<HTMLDialogElement>(null)
  const [items,setItems]=useState<SavedWorkspace[]>([]),[offset,setOffset]=useState(0),[more,setMore]=useState(false)
  const [loading,setLoading]=useState(false),[error,setError]=useState(''),[lookup,setLookup]=useState('')
  const load=async(next:number)=>{setLoading(true);setError('');try{
    const response=await apiFetch(`/api/submissions/workspaces?offset=${next}&limit=20`,{headers:{'X-Member-Id':memberId}})
    const body=await response.json();setItems(body.workspaces);setMore(body.hasMore);setOffset(next)
  }catch(e){setError(e instanceof Error?e.message:'저장 작업을 불러오지 못했습니다.')}finally{setLoading(false)}}
  const open=async(id:string)=>{setError('');try{await onOpen(id);dialog.current?.close()}catch(e){setError(e instanceof Error?e.message:'작업 불러오기 실패')}}
  return <section className="saved-workspace" aria-label="저장된 시험 작업">
    <label><span>시험 작업 이름</span><input aria-label="시험 작업 이름" value={name} maxLength={120} disabled={busy} onChange={e=>onNameChange(e.target.value)} placeholder="예: NeoFlow Server 1.0.3" /></label>
    {workspace&&<><small className="workspace-id">작업 ID · {workspace.id}</small><button type="button" className="quiet-button" disabled={busy||!name.trim()||name.trim()===workspace.name} onClick={()=>void onRename()}>이름 저장</button></>}
    {!workspace&&<small>자료를 등록하면 고유 작업 ID로 저장됩니다.</small>}
    <button type="button" className="quiet-button" disabled={busy} onClick={()=>{setItems([]);setError('');dialog.current?.showModal();void load(0)}}>저장된 시험 작업 불러오기</button>
    <dialog ref={dialog} className="saved-workspace-dialog" aria-labelledby="saved-workspace-title">
      <div className="section-heading"><h2 id="saved-workspace-title">저장된 시험 작업</h2><button type="button" className="quiet-button" onClick={()=>dialog.current?.close()}>닫기</button></div>
      <p>최근 생성한 작업부터 표시합니다. 저장된 자료·수집·메뉴트리·TC·시험 이력을 불러옵니다.</p>
      {error&&<p role="alert" className="error-copy">{error}</p>}
      {(loading||busy)&&<p role="status"><span className="spinner" /> 불러오는 중…</p>}
      {!loading&&!items.length&&!error&&<p>저장된 시험 작업이 없습니다.</p>}
      <div className="saved-workspace-list">{items.map(item=><article key={item.id}>
        <div><strong>{item.name}</strong><small>{new Date(item.createdAt).toLocaleString('ko-KR')} · ID {item.id}</small>
          <p>자료 {item.parsedDocumentCount}/{item.documentCount} · 수집 {item.crawlCount} · 메뉴트리 {item.menuCount} · TC 세트 {item.tcSetCount} · 시험 이력 {item.runCount}</p></div>
        <button type="button" className="primary-button" aria-label={`${item.name} 불러오기`} disabled={busy||loading} onClick={()=>void open(item.id)}>불러오기</button>
      </article>)}</div>
      <div className="tc-pagination"><button type="button" className="quiet-button" disabled={loading||busy||offset===0} onClick={()=>void load(offset-20)}>이전</button><span>{offset/20+1}쪽</span><button type="button" className="quiet-button" disabled={loading||busy||!more} onClick={()=>void load(offset+20)}>다음</button></div>
      <details><summary>작업 ID로 직접 불러오기</summary><label>작업 ID<input aria-label="불러올 작업 ID" value={lookup} onChange={e=>setLookup(e.target.value)} /></label><button type="button" className="quiet-button" disabled={busy||loading||!lookup.trim()} onClick={()=>void open(lookup.trim())}>ID로 불러오기</button></details>
      <small>저장하지 않은 편집 내용·파일 선택은 복원되지 않습니다. 표시 개수는 저장 이력이며 승인·실행 준비 완료를 뜻하지 않습니다.</small>
    </dialog>
  </section>
}
