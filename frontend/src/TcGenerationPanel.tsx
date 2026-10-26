import { apiFetch as fetch } from './apiFetch'
import { useCallback, useEffect, useRef, useState } from 'react'
import { PreflightWarningDetails, type PreflightWarning, type PreflightDiagnostics } from './PreflightWarningDetails'

type Tc = {
  testType: 'PRODUCT_BEHAVIOR' | 'DOCUMENT_CONFORMANCE'
  tcId: string; featurePath: string[]; qualityCharacteristic: string; subCharacteristic: string
  scenario: string; preconditions: string[]; steps: string[]; inputs: string; expectedResult: string
  reviewNotes: string[]; defectExampleSummary: string; defectExampleSeverity: string; defectExampleContent: string
  executionMode: string; result: null
  sources: { kind?: string; criterionId?: string; fileId: string; role: string; quote: string; characterStart?: number; characterEnd?: number }[]
}
type Job = {
  jobId: string; status: 'GENERATING' | 'COMPLETED' | 'FAILED' | 'CANCELED'
  completedChunks: number; totalChunks: number; errorCode: string | null
  output: { generationMode: string; testCases: Tc[] } | null
}
type MenuNode = {
  nodeId: string; title: string; classification: string; evidence: string[]
  menuDepth: number; reviewStatus: string; featurePath: string[]; included?: boolean
  children: MenuNode[]
}
type MenuTree = { schemaVersion: string; submissionId: string; manualFileId: string; status: string
  sourceType: 'DOM' | 'SCREENSHOT'; nodes: MenuNode[] }
type MenuTreeState = { status: 'DRAFT_NEEDS_REVIEW' | 'APPROVED' | 'STALE'; stale: boolean; tree: MenuTree }
type ExternalPrompt = { chunkIndex: number; totalChunks: number; start: number; end: number; characterCount: number
  sourceFilename: string; prompt: string }
type ExternalMenuPrompt = { sourceType: 'DOM' | 'SCREENSHOT'; sourceCharacterCount: number; promptCharacterCount: number; prompt: string }
const failureMessages: Record<string, string> = {
  TC_INPUT_BUDGET_INSUFFICIENT: '문서 경고와 생성 지침이 모델 입력 한도를 초과했습니다.',
  LLM_INPUT_BUDGET_EXCEEDED: 'TC 생성 요청이 모델 입력 한도를 초과했습니다.',
  LLM_OUTPUT_INCOMPLETE: '모델 응답이 출력 한도에서 중단됐습니다.',
  TC_FEATURE_PATH_INVALID: '생성된 TC의 기능 분류가 승인된 메뉴 트리와 일치하지 않아 저장되지 않았습니다.',
  TC_EVIDENCE_INVALID: '모델이 제시한 근거 인용을 원문에서 확인하지 못했습니다.',
  TC_SCHEMA_INVALID: '모델의 TC 응답 구조가 올바르지 않습니다.',
  TC_NO_GROUNDED_CASES: '원문 근거가 있는 TC를 생성하지 못했습니다.',
  INTERRUPTED: '서버 재시작이나 연결 중단으로 생성이 중단됐습니다.',
}
type Props = {
  submissionId: string; memberId: string; deliveryStatus: string; revision: string
  tcDocumentsReady: boolean
  preflight: { decision: string; warnings: PreflightWarning[]; diagnostics?: PreflightDiagnostics
    generationGate?: { allowed: boolean } } | null
}

export function TcGenerationPanel({ submissionId, memberId, deliveryStatus, preflight, revision, tcDocumentsReady }: Props) {
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [promptBusy, setPromptBusy] = useState(false)
  const [externalPrompt, setExternalPrompt] = useState<ExternalPrompt | null>(null)
  const [promptCopied, setPromptCopied] = useState(false)
  const [promptError, setPromptError] = useState('')
  const [page, setPage] = useState(0)
  const [menuTreeState, setMenuTreeState] = useState<MenuTreeState | null>(null)
  const [editingApprovedTree, setEditingApprovedTree] = useState(false)
  const [menuTreeLoaded, setMenuTreeLoaded] = useState(false)
  const [menuTreeBusy, setMenuTreeBusy] = useState(false)
  const [menuTreeError, setMenuTreeError] = useState('')
  const [menuPrompt, setMenuPrompt] = useState<ExternalMenuPrompt | null>(null)
  const [menuPromptCopied, setMenuPromptCopied] = useState(false)
  const [menuPromptBusy, setMenuPromptBusy] = useState(false)
  const [menuResponse, setMenuResponse] = useState('')
  const [menuSourceType, setMenuSourceType] = useState<'DOM' | 'SCREENSHOT'>('DOM')
  const [menuSourceText, setMenuSourceText] = useState('')
  const [preflightDialog, setPreflightDialog] = useState<'blocked' | 'ready' | null>(null)
  const [treeProgress, setTreeProgress] = useState('')
  const preflightPromptedFor = useRef('')
  const base = `/api/submissions/${submissionId}/test-cases`
  const ready = deliveryStatus === 'DELIVERED' && ['READY', 'READY_WITH_WARNINGS'].includes(preflight?.decision ?? '')
    && preflight?.generationGate?.allowed !== false
  const active = job?.status === 'GENERATING'
  const menuTreeBase = `/api/submissions/${submissionId}/menu-tree`
  const menuTreeApproved = menuTreeState?.status === 'APPROVED' && !menuTreeState.stale && !editingApprovedTree

  useEffect(() => {
    setExternalPrompt(null); setPromptCopied(false); setPromptError('')
    setMenuPrompt(null); setMenuPromptCopied(false); setMenuResponse('')
  }, [submissionId, revision, ready, menuTreeApproved])

  const refresh = useCallback(async (signal?: AbortSignal) => {
    const response = await fetch(`/api/submissions/${submissionId}/test-cases`, { headers: { 'X-Member-Id': memberId }, signal })
    if (!response.ok) throw new Error('TC 작업 조회에 실패했습니다. 회원 ID와 문서 세트를 확인해 주세요.')
    const data = await response.json() as { job: Job | null }
    if (!signal?.aborted) { setJob(data.job); setError('') }
  }, [submissionId, memberId])

  const refreshMenuTree = useCallback(async (signal?: AbortSignal) => {
    const response = await fetch(menuTreeBase, { headers: { 'X-Member-Id': memberId }, signal })
    if (!response.ok) throw new Error('메뉴 트리 상태를 불러오지 못했습니다.')
    const data = await response.json() as { menuTree: MenuTreeState | null }
    if (!signal?.aborted) { setMenuTreeState(data.menuTree); setMenuTreeLoaded(true); setMenuTreeError('') }
  }, [menuTreeBase, memberId])

  useEffect(() => {
    const controller = new AbortController()
    void refresh(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message) })
    return () => controller.abort()
  }, [refresh])
  useEffect(() => {
    const controller = new AbortController()
    void refreshMenuTree(controller.signal).catch((failure: Error) => {
      if (!controller.signal.aborted) { setMenuTreeError(failure.message); setMenuTreeLoaded(true) }
    })
    return () => controller.abort()
  }, [refreshMenuTree])
  useEffect(() => {
    if (!menuTreeLoaded || deliveryStatus !== 'DELIVERED' || !preflight) return
    const promptId = `${submissionId}:${revision}`
    if (sessionStorage.getItem(`jev-result-seen:${promptId}`)) {
      preflightPromptedFor.current = promptId
      return
    }
    if (preflightPromptedFor.current === promptId) return
    if (preflight.decision === 'BLOCKED') {
      preflightPromptedFor.current = promptId
      sessionStorage.setItem(`jev-result-seen:${promptId}`, '1')
      setPreflightDialog('blocked')
    } else if (ready && !menuTreeState) {
      preflightPromptedFor.current = promptId
      sessionStorage.setItem(`jev-result-seen:${promptId}`, '1')
      setPreflightDialog('ready')
    }
  }, [menuTreeLoaded, deliveryStatus, preflight, ready, menuTreeState, submissionId, revision])
  const generateMenuTree = async () => {
    if (!ready || menuTreeBusy || menuTreeState) return
    setPreflightDialog(null); setMenuTreeBusy(true); setMenuTreeError('')
    setMenuPromptBusy(true); setMenuPrompt(null); setMenuPromptCopied(false); setMenuResponse('')
    setTreeProgress('화면 자료를 복사용 프롬프트에 구성하고 있습니다…')
    try {
      const response = await fetch(`${menuTreeBase}/external-prompt`, { method: 'POST', headers: {
        'X-Member-Id': memberId, 'Content-Type': 'application/json', 'Cache-Control': 'no-cache' },
        body: JSON.stringify({ sourceType: menuSourceType, sourceText: menuSourceType === 'DOM' ? menuSourceText : '' }) })
      const data = await response.json() as ExternalMenuPrompt & { message?: string }
      if (!response.ok) throw new Error(data.message || '외부 LLM용 메뉴 트리 프롬프트를 만들지 못했습니다.')
      setMenuPrompt(data)
      setTreeProgress('프롬프트가 준비되었습니다. 복사해 외부 LLM에 붙여 넣고 JSON 응답을 가져오세요.')
    } catch (failure) {
      setMenuTreeError(failure instanceof Error ? failure.message : '메뉴 트리 생성에 실패했습니다.')
      setTreeProgress('프롬프트 준비에 실패했습니다.')
    } finally {
      setMenuTreeBusy(false)
      setMenuPromptBusy(false)
    }
  }
  useEffect(() => {
    if (!active) return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      void refresh(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message) })
    }, 3000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [active, refresh])
  const updateMenuNode = (nodes: MenuNode[], section: string, change: (node: MenuNode) => MenuNode): MenuNode[] =>
    nodes.map(node => node.nodeId === section ? change(node) : { ...node, children: updateMenuNode(node.children ?? [], section, change) })
  const editMenuTitle = (section: string, title: string) => {
    if (!menuTreeState) return
    setMenuTreeState({ ...menuTreeState, tree: { ...menuTreeState.tree,
      nodes: recalculateMenuPaths(updateMenuNode(menuTreeState.tree.nodes, section, node => ({ ...node, title }))) } })
  }
  const toggleMenuNode = (section: string, included: boolean) => {
    if (!menuTreeState) return
    setMenuTreeState({ ...menuTreeState, tree: { ...menuTreeState.tree,
      nodes: updateMenuNode(menuTreeState.tree.nodes, section, node => ({ ...node, included })) } })
  }
  const moveMenuNode = (section: string, newParent: string) => {
    if (!menuTreeState || newParent === section) return
    const nodes = structuredClone(menuTreeState.tree.nodes) as MenuNode[]
    let moving: MenuNode | undefined
    const isWithin = (node: MenuNode, sectionToFind: string): boolean => node.nodeId === sectionToFind
      || (node.children ?? []).some(child => isWithin(child, sectionToFind))
    const remove = (items: MenuNode[]): MenuNode[] => items.filter(node => {
      if (node.nodeId === section) { moving = node; return false }
      node.children = remove(node.children ?? [])
      return true
    })
    const remaining = remove(nodes)
    if (!moving || (newParent && isWithin(moving, newParent))) return
    if (!newParent) remaining.push(moving)
    else {
      const parent = findMenuNode(remaining, newParent)
      if (!parent) return
      parent.children = [...(parent.children ?? []), moving]
    }
    setMenuTreeState({ ...menuTreeState, tree: { ...menuTreeState.tree, nodes: recalculateMenuPaths(remaining) } })
  }
  const allMenuNodes = flattenMenuNodes(menuTreeState?.tree.nodes ?? [])
  const approveMenuTree = async () => {
    if (!menuTreeState || menuTreeBusy) return
    setMenuTreeBusy(true); setMenuTreeError('')
    try {
      const response = await fetch(`${menuTreeBase}/approval`, { method: 'PUT', headers: {
        'X-Member-Id': memberId, 'Content-Type': 'application/json' }, body: JSON.stringify({ tree: menuTreeState.tree }) })
      const data = await response.json() as { menuTree?: MenuTreeState; message?: string }
      if (!response.ok) throw new Error(data.message || '메뉴 트리를 승인하지 못했습니다.')
      setMenuTreeState(data.menuTree ?? null)
      setEditingApprovedTree(false)
      setExternalPrompt(null); setPromptCopied(false); setPromptError('')
    } catch (failure) { setMenuTreeError(failure instanceof Error ? failure.message : '메뉴 트리 승인 실패') }
    finally { setMenuTreeBusy(false) }
  }
  const copyMenuPrompt = async () => {
    if (!menuPrompt) return
    try {
      await navigator.clipboard.writeText(menuPrompt.prompt)
      setMenuPromptCopied(true); setMenuTreeError('')
    } catch { setMenuTreeError('자동 복사에 실패했습니다. 프롬프트 상자에서 Ctrl+A, Ctrl+C를 눌러 복사해 주세요.') }
  }
  const importMenuResponse = async () => {
    if (!menuPrompt || menuTreeBusy || !menuResponse.trim()) return
    let tree: unknown
    try { tree = JSON.parse(menuResponse) }
    catch { setMenuTreeError('외부 LLM 응답이 JSON이 아닙니다. 코드 펜스와 설명을 제거하고 JSON 객체만 붙여 넣어 주세요.'); return }
    setMenuTreeBusy(true); setMenuTreeError('')
    try {
      const response = await fetch(`${menuTreeBase}/external-draft`, { method: 'POST', headers: {
        'X-Member-Id': memberId, 'Content-Type': 'application/json' }, body: JSON.stringify({
          sourceType: menuSourceType, sourceText: menuSourceType === 'DOM' ? menuSourceText : '', tree }) })
      const data = await response.json() as { menuTree?: MenuTreeState; message?: string }
      if (!response.ok) throw new Error(data.message || '외부 LLM 메뉴 트리를 초안으로 저장하지 못했습니다.')
      setMenuTreeState(data.menuTree ?? null)
      setMenuPrompt(null); setMenuResponse(''); setEditingApprovedTree(false)
    } catch (failure) { setMenuTreeError(failure instanceof Error ? failure.message : '외부 LLM 응답 저장 실패') }
    finally { setMenuTreeBusy(false) }
  }
  const uploadMenuTree = async (file: File | undefined) => {
    if (!file || !ready || menuTreeBusy) return
    let tree: unknown
    try { tree = JSON.parse(await file.text()) }
    catch { setMenuTreeError('업로드한 파일이 올바른 JSON이 아닙니다.'); return }
    setPreflightDialog(null); setMenuTreeBusy(true); setMenuTreeError(''); setMenuPrompt(null)
    setTreeProgress('업로드한 메뉴 트리를 확인하고 있습니다…')
    try {
      const response = await fetch(`${menuTreeBase}/external-draft`, { method: 'POST', headers: {
        'X-Member-Id': memberId, 'Content-Type': 'application/json' }, body: JSON.stringify({
          sourceType: 'UPLOADED', sourceText: '', tree }) })
      const data = await response.json() as { menuTree?: MenuTreeState; message?: string }
      if (!response.ok) throw new Error(data.message || '업로드한 메뉴 트리를 저장하지 못했습니다.')
      setMenuTreeState(data.menuTree ?? null); setEditingApprovedTree(false)
    } catch (failure) { setMenuTreeError(failure instanceof Error ? failure.message : '메뉴 트리 업로드 실패') }
    finally { setMenuTreeBusy(false) }
  }
  const loadExternalPrompt = async (chunkIndex = 0) => {
    if (promptBusy || !ready || !menuTreeApproved || !tcDocumentsReady) return
    setPromptBusy(true); setPromptError(''); setPromptCopied(false)
    try {
      const response = await fetch(`${base}/external-prompt?chunkIndex=${chunkIndex}`, {
        headers: { 'X-Member-Id': memberId, 'Cache-Control': 'no-cache' },
      })
      const data = await response.json() as ExternalPrompt & { message?: string }
      if (!response.ok) throw new Error(data.message || '복사용 LLM 프롬프트를 만들지 못했습니다.')
      setExternalPrompt(data)
    } catch (failure) { setPromptError(failure instanceof Error ? failure.message : '프롬프트 생성 실패') }
    finally { setPromptBusy(false) }
  }
  const copyExternalPrompt = async () => {
    if (!externalPrompt) return
    try {
      await navigator.clipboard.writeText(externalPrompt.prompt)
      setPromptCopied(true); setPromptError('')
    } catch {
      setPromptError('자동 복사에 실패했습니다. 프롬프트 상자 안을 클릭하고 Ctrl+A, Ctrl+C를 눌러 복사해 주세요.')
    }
  }
  const cancel = async () => {
    if (!job || busy) return
    setBusy(true)
    try {
      const response = await fetch(`${base}/${job.jobId}/cancel`, { method: 'POST', headers: { 'X-Member-Id': memberId } })
      if (!response.ok) throw new Error('TC 생성을 중단하지 못했습니다.')
      await refresh()
    } catch (failure) { setError(failure instanceof Error ? failure.message : '중단 요청 실패') }
    finally { setBusy(false) }
  }
  const cases = job?.output?.testCases ?? []
  const download = async () => {
    if (!job || busy) return
    setBusy(true); setError('')
    try {
      const response = await fetch(`${base}/${job.jobId}/download`, { headers: { 'X-Member-Id': memberId } })
      if (!response.ok) {
        const body = await response.json() as { message?: string }
        throw new Error(body.message || 'Excel 다운로드에 실패했습니다.')
      }
      const url = URL.createObjectURL(await response.blob())
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `TC-${submissionId}.xlsx`
      document.body.appendChild(anchor); anchor.click(); anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Excel 다운로드 실패') }
    finally { setBusy(false) }
  }
  return <section className="tc-panel" aria-label="TC 생성">
    <h2>테스트케이스 초안</h2>
    <p>Jev 검증 후 화면 캡처나 F12 Elements를 근거로 외부 LLM이 메뉴 트리를 작성합니다. 결과를 검토·승인한 뒤 TC 생성용 프롬프트를 복사합니다. 이 화면은 LLM을 호출하지 않습니다.</p>
    {!ready && <p>문서 사전 검증을 통과하면 메뉴 트리를 준비할 수 있습니다.</p>}
    {ready && menuTreeBusy && <div className="menu-tree-progress" role="status"><span className="spinner" />
      <div><strong>{menuPromptBusy ? '메뉴 트리 프롬프트 준비 중' : '메뉴 트리 응답 저장 중'}</strong><p>{treeProgress}</p><progress aria-label="메뉴 트리 처리 진행" /></div></div>}
    {menuTreeError && <p className="error-copy" role="alert">{menuTreeError}
      <button onClick={() => void refreshMenuTree().catch((e: Error) => setMenuTreeError(e.message))}>다시 조회</button>
      {ready && !menuTreeState && <button onClick={() => { setMenuTreeLoaded(true); setMenuTreeError('') }}>프롬프트 다시 만들기</button>}</p>}
    {ready && menuTreeState?.stale && <div className="menu-tree-review" role="status">
      <h3>기존 메뉴 트리 재작성 필요</h3>
      <p>이 트리는 새 화면 자료 기반 형식이 아니거나 원본 문서가 변경되어 TC 생성에 사용할 수 없습니다. F12 Elements 또는 화면 캡처를 근거로 다시 작성하세요.</p>
      <button className="primary-button" disabled={menuTreeBusy} onClick={() => {
        setMenuTreeState(null); setMenuTreeLoaded(true); setMenuPrompt(null); setMenuResponse('')
      }}>화면 자료로 메뉴 트리 다시 작성</button>
    </div>}
    {ready && menuTreeState && !menuTreeState.stale && <div className="menu-tree-review">
      <h3>메뉴 트리 {editingApprovedTree ? '수정 중' : menuTreeApproved ? '승인됨' : menuTreeState.stale ? '재생성 필요' : '검토 필요'}</h3>
      <p>메뉴 순서와 상하 관계, 각 항목의 화면 근거를 확인하세요. 화면과 맞지 않는 항목은 제외하거나 이름·위치를 고친 뒤 승인하세요.</p>
      <MenuTreeEditor nodes={menuTreeState.tree.nodes} allNodes={allMenuNodes}
        disabled={(menuTreeState.status === 'APPROVED' && !editingApprovedTree) || menuTreeState.stale || menuTreeBusy}
        onTitle={editMenuTitle} onInclude={toggleMenuNode} onMove={moveMenuNode} />
      {editingApprovedTree && <button className="quiet-button" disabled={menuTreeBusy} onClick={() => {
        setEditingApprovedTree(false)
        void refreshMenuTree().catch((failure: Error) => setMenuTreeError(failure.message))
      }}>수정 취소</button>}
      {(!menuTreeApproved || editingApprovedTree) && !menuTreeState.stale && <button className="primary-button" disabled={menuTreeBusy} onClick={() => void approveMenuTree()}>
        {menuTreeBusy ? '저장 중…' : editingApprovedTree ? '수정한 메뉴 트리 다시 승인' : '검토한 메뉴 트리 승인'}</button>}
    </div>}
    {ready && !menuTreeState && !menuPrompt && !menuTreeBusy && !menuTreeError && <button className="primary-button" onClick={() => setPreflightDialog('ready')}>외부 LLM 메뉴 트리 프롬프트 만들기</button>}
    {ready && menuPrompt && !menuTreeState && <div className="external-prompt-panel">
      <div className="external-prompt-heading"><div><h3>외부 LLM 메뉴 트리 프롬프트</h3>
        <p>{menuPrompt.sourceType === 'DOM' ? `Elements ${menuPrompt.sourceCharacterCount.toLocaleString()}자` : '화면 캡처 첨부 방식'} · 프롬프트 {menuPrompt.promptCharacterCount.toLocaleString()}자</p></div>
        <div className="dialog-actions"><button className="quiet-button" onClick={() => {
          setMenuPrompt(null); setMenuPromptCopied(false); setMenuResponse(''); setPreflightDialog('ready')
        }}>화면 자료 변경</button>
        <button className="primary-button" onClick={() => void copyMenuPrompt()}>{menuPromptCopied ? '복사 완료' : '메뉴 트리 프롬프트 복사'}</button></div>
      </div>
      <p className="external-data-note">{menuPrompt.sourceType === 'DOM'
        ? '붙여 넣은 Elements 내용이 프롬프트에 포함됩니다. 외부 LLM에 프롬프트를 붙여 넣으면 해당 화면 구조가 전달됩니다.'
        : '프롬프트에는 이미지가 포함되지 않습니다. 복사한 프롬프트와 함께 화면 캡처를 외부 LLM에 직접 첨부하세요.'}</p>
      <textarea aria-label="복사용 메뉴 트리 프롬프트" readOnly value={menuPrompt.prompt} rows={16}
        onFocus={event => event.currentTarget.select()} />
      <label className="external-response-label" htmlFor="external-menu-tree-response">외부 LLM의 JSON 응답을 붙여 넣으세요</label>
      <textarea id="external-menu-tree-response" aria-label="외부 LLM 메뉴 트리 JSON 응답" value={menuResponse} rows={10}
        placeholder={'{"schemaVersion":"ui-menu-tree-v1","nodes":[…]}'}
        onChange={event => setMenuResponse(event.target.value)} />
      <div className="dialog-actions"><button className="primary-button" disabled={menuTreeBusy || !menuResponse.trim()} onClick={() => void importMenuResponse()}>
        {menuTreeBusy ? '근거 확인 중…' : '응답을 근거 확인 후 초안으로 불러오기'}</button>
        <p>{menuSourceType === 'DOM' ? '각 노드의 화면 근거가 붙여 넣은 Elements 내용에 포함되는지 확인합니다.' : '캡처 근거는 자동 판독하지 않으므로 화면과 직접 대조해 검토하세요.'} 저장 후 메뉴 경로를 검토·승인해야 TC 프롬프트를 만들 수 있습니다.</p></div>
    </div>}
    {ready && !menuTreeState && !menuPrompt && !menuTreeBusy && <div className="external-prompt-actions">
      <label htmlFor="menu-tree-upload">이미 만든 메뉴 트리(JSON)가 있다면 업로드하세요. 프롬프트 생성 없이 검토·승인 후 TC 초안을 만들 수 있습니다.</label>
      <input id="menu-tree-upload" type="file" accept=".json,application/json"
        onChange={event => { const input = event.currentTarget; void uploadMenuTree(input.files?.[0]).finally(() => { input.value = '' }) }} />
    </div>}
    {ready && menuTreeApproved && !tcDocumentsReady && <p role="status">TC 프롬프트를 만들려면 기능리스트와 매뉴얼이 정상 파싱되어야 합니다.</p>}
    {ready && menuTreeApproved && tcDocumentsReady && <div className="external-prompt-actions">
      <button className="primary-button" disabled={promptBusy} onClick={() => void loadExternalPrompt(0)}>
        {promptBusy ? '프롬프트 준비 중…' : externalPrompt ? '프롬프트 다시 만들기' : '외부 LLM용 프롬프트 만들기'}</button>
      <p>승인된 메뉴 경로와 사전 점검 경고, 매뉴얼 청크만 포함합니다. 프롬프트를 직접 복사해 원하는 LLM에 붙여 넣으세요. 외부 전송은 이 앱에서 일어나지 않습니다.</p>
    </div>}
    {promptError && <p className="error-copy" role="alert">{promptError}</p>}
    {externalPrompt && <div className="external-prompt-panel">
      <div className="external-prompt-heading"><div><h3>외부 LLM 입력 프롬프트</h3>
        <p>매뉴얼 청크 {externalPrompt.chunkIndex + 1} / {externalPrompt.totalChunks} · {externalPrompt.sourceFilename} · 원문 문자 {externalPrompt.start}–{externalPrompt.end} · 프롬프트 {externalPrompt.characterCount.toLocaleString()}자</p></div>
        <div className="dialog-actions"><button className="quiet-button" disabled={promptBusy || externalPrompt.chunkIndex === 0}
          onClick={() => void loadExternalPrompt(externalPrompt.chunkIndex - 1)}>이전 청크</button>
          <button className="quiet-button" disabled={promptBusy || externalPrompt.chunkIndex + 1 >= externalPrompt.totalChunks}
            onClick={() => void loadExternalPrompt(externalPrompt.chunkIndex + 1)}>다음 청크</button>
          <button className="primary-button" onClick={() => void copyExternalPrompt()}>{promptCopied ? '복사 완료' : '프롬프트 복사'}</button></div>
      </div>
      <textarea aria-label="복사용 외부 LLM 프롬프트" readOnly value={externalPrompt.prompt} rows={22}
        onFocus={event => event.currentTarget.select()} />
      <p>청크마다 프롬프트를 복사해 별도로 실행하세요. 각 응답을 보관하면 어느 청크에서 결과가 달라졌는지 비교하기 쉽습니다.</p>
    </div>}
    {error && <p className="error-copy" role="alert">{error} <button onClick={() => void refresh().catch((e: Error) => setError(e.message))}>다시 조회</button></p>}
    {active && <div role="status"><p>TC 생성 중 · {job.completedChunks} / {job.totalChunks || '준비 중'} 청크</p>
      {job.totalChunks > 0 && <progress value={job.completedChunks} max={job.totalChunks} />}
      <button className="quiet-button" disabled={busy} onClick={() => void cancel()}>생성 중단</button></div>}
    {job?.status === 'FAILED' && <div className="tc-failure" role="alert">
      <p><strong>{failureMessages[job.errorCode ?? ''] ?? 'TC 생성 요청을 처리하지 못했습니다.'}</strong></p>
      {job.errorCode === 'TC_FEATURE_PATH_INVALID' ? <>
        <p>생성된 TC의 기능 경로가 승인 메뉴 트리에 있는 경로와 정확히 맞지 않았습니다. 이 TC는 저장하지 않았습니다.</p>
        <p>메뉴 트리와 외부 LLM 응답의 기능 경로를 비교해 주세요. 메뉴 트리가 정확하면 아래 프롬프트를 복사해 다른 모델에서도 같은 입력으로 시험할 수 있습니다.</p>
        {menuTreeState?.status === 'APPROVED' && !menuTreeState.stale && !editingApprovedTree &&
          <button className="quiet-button" onClick={() => {
            setEditingApprovedTree(true); setExternalPrompt(null); setPromptCopied(false)
          }}>메뉴 트리 검토·수정</button>}
      </> : <p>이 작업은 이전 로컬 LLM 실행 기록입니다. 현재는 승인된 입력으로 프롬프트를 복사해 외부에서 시험할 수 있습니다.</p>}
      <details><summary>오류 코드 보기</summary><code>{job.errorCode ?? 'UNKNOWN'}</code></details>
    </div>}
    {job?.status === 'CANCELED' && <p>TC 생성이 중단되었습니다. 다시 승인하면 완료 청크를 재사용합니다.</p>}
    {job?.status === 'COMPLETED' && <>
      <button className="primary-button" disabled={busy} onClick={() => void download()}>{busy ? '다운로드 준비 중…' : 'Excel 다운로드 (.xlsx)'}</button>
      <p role="status">TC 초안 {cases.length}개 생성 완료 · {job.output?.generationMode === 'MOCK' ? '개발용 모의 결과' : '실제 LLM 결과'} · 담당자 검토 필요</p>
      <p>결함 열은 작성 예시입니다. 실제 발견된 결함이 아닙니다. 보안·성능 시험은 수동 대상으로 표시됩니다.</p>
      <div className="tc-table-scroll"><table className="tc-table"><thead><tr>
        {['1depth', '2depth', '3depth', '4depth', '5depth', 'TC-ID', '품질특성', '하위 특성', '테스트 시나리오', '입력·사전조건', '기대 출력·사후조건', '결과', '결함요약 예시', '결함정도 예시', '결함내용 예시', '절차·근거·검토'].map(t => <th key={t}>{t}</th>)}
      </tr></thead><tbody>{cases.slice(page * 20, (page + 1) * 20).map(tc => <tr key={tc.tcId}>
        {[0,1,2,3,4].map(i => <td key={i}>{tc.featurePath[i] || '—'}</td>)}
        <td>{tc.tcId}</td><td>{tc.qualityCharacteristic}</td><td>{tc.subCharacteristic}</td><td>{tc.scenario}</td>
        <td>{tc.preconditions.join('\n')}<br />{tc.inputs}</td><td>{tc.expectedResult}</td><td aria-label="결과 미기록">—</td>
        <td>{tc.defectExampleSummary}</td><td>{tc.defectExampleSeverity}</td><td>{tc.defectExampleContent}</td>
        <td><details><summary>절차·근거 보기</summary><p>{tc.testType === 'DOCUMENT_CONFORMANCE' ? '문서 적합성 시험' : '제품 동작 시험'} · {tc.executionMode === 'MANUAL' ? '수동 시험' : '브라우저 실행 가능 여부 검토 필요'}</p>
          <ol>{tc.steps.map((s,i) => <li key={i}>{s}</li>)}</ol>
          {tc.sources.map((s,i) => <p key={i}>{s.kind === 'PROJECT_CRITERION' ? `프로젝트 기준 · ${s.criterionId}` : `${s.role} · 문자 ${s.characterStart}–${s.characterEnd}`}<br />{s.quote}</p>)}
          {tc.reviewNotes.map((s,i) => <p key={i}>{s}</p>)}</details></td>
      </tr>)}</tbody></table></div>
      <div className="dialog-actions"><button disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
        <span>{page + 1} / {Math.max(1, Math.ceil(cases.length / 20))}</span>
        <button disabled={(page + 1) * 20 >= cases.length} onClick={() => setPage(page + 1)}>다음</button></div>
    </>}
    <dialog open={preflightDialog !== null} className="dialog tc-dialog" aria-labelledby="preflight-result-title" onCancel={event => {
      event.preventDefault(); setPreflightDialog(null)
    }}>
      {preflightDialog === 'blocked' ? <>
        <h2 id="preflight-result-title">사전 점검에서 거부되었습니다</h2>
        <p>문서 검증 결과 생성이 차단되었습니다. 아래 사유를 확인하고 해당 문서를 교체한 뒤 다시 점검해 주세요.</p>
        {preflight && <PreflightWarningDetails warnings={preflight.warnings} diagnostics={preflight.diagnostics} />}
        <div className="dialog-actions"><button className="primary-button" onClick={() => setPreflightDialog(null)}>확인</button></div>
      </> : <>
        <h2 id="preflight-result-title">사전 점검 완료</h2>
        <p>메뉴 트리의 근거가 될 화면 자료를 선택하세요. 실제 화면에 보이는 순서를 유지해 작성하도록 프롬프트를 만들겠습니다.</p>
        {preflight && <PreflightWarningDetails warnings={preflight.warnings} diagnostics={preflight.diagnostics} />}
        <label className="menu-source-label" htmlFor="menu-source-type">화면 자료 방식</label>
        <select id="menu-source-type" value={menuSourceType} disabled={menuTreeBusy || menuPromptBusy}
          onChange={event => { const value = event.target.value as 'DOM' | 'SCREENSHOT'; setMenuSourceType(value); if (value === 'SCREENSHOT') setMenuSourceText('') }}>
          <option value="DOM">F12 Elements 복사</option><option value="SCREENSHOT">화면 캡처 첨부</option>
        </select>
        {menuSourceType === 'DOM' ? <>
          <label className="menu-source-label" htmlFor="menu-source-text">메뉴 영역의 Elements 내용을 붙여 넣으세요</label>
          <textarea className="menu-source-input" id="menu-source-text" value={menuSourceText} rows={8}
            placeholder="F12 → Elements에서 nav, aside 또는 메뉴 영역 요소를 복사해 붙여 넣으세요."
            onChange={event => setMenuSourceText(event.target.value)} />
          <p>F12 → Elements에서 메뉴의 nav·aside 요소를 선택하고 우클릭 → Copy → Copy outerHTML로 복사하세요. 여러 화면은 화면 이름과 구분선을 넣어 순서대로 붙이세요. 계정명이나 고객 데이터가 섞였는지 확인하고 민감한 값은 지우세요. DOM 자료는 이 앱을 거쳐 프롬프트에 포함되고, 외부 LLM에 붙여 넣으면 그 제공자에게 전달됩니다.</p>
        </> : <p>프롬프트를 복사한 뒤 외부 LLM에 화면 캡처를 직접 첨부하세요. 캡처 파일은 이 앱으로 전송되지 않습니다.</p>}
        <div className="dialog-actions"><button className="primary-button" disabled={menuTreeBusy || menuPromptBusy
          || (menuSourceType === 'DOM' && !menuSourceText.trim())} onClick={() => void generateMenuTree()}>
          {menuPromptBusy ? '프롬프트 준비 중…' : '화면 기반 메뉴 트리 프롬프트 만들기'}</button>
          <button className="quiet-button" onClick={() => setPreflightDialog(null)}>취소</button></div>
      </>}
    </dialog>
  </section>
}

function flattenMenuNodes(nodes: MenuNode[]): MenuNode[] { return nodes.flatMap(node => [node, ...flattenMenuNodes(node.children ?? [])]) }
function recalculateMenuPaths(nodes: MenuNode[], parentPath: string[] = []): MenuNode[] {
  return nodes.map(node => {
    const featurePath = [...parentPath, node.title]
    return { ...node, featurePath, menuDepth: featurePath.length, children: recalculateMenuPaths(node.children ?? [], featurePath) }
  })
}
function findMenuNode(nodes: MenuNode[], section: string): MenuNode | undefined {
  for (const node of nodes) { if (node.nodeId === section) return node
    const child = findMenuNode(node.children ?? [], section); if (child) return child }
  return undefined
}

function MenuTreeEditor({ nodes, allNodes, disabled, onTitle, onInclude, onMove }: { nodes: MenuNode[]; allNodes: MenuNode[]; disabled: boolean
  onTitle: (nodeId: string, title: string) => void; onInclude: (nodeId: string, included: boolean) => void
  onMove: (nodeId: string, parentId: string) => void }) {
  return <ul className="menu-tree-list">{nodes.map(node => <li key={node.nodeId}>
    <div className="menu-tree-node">
      <label><input type="checkbox" checked={node.included !== false} disabled={disabled}
        onChange={event => onInclude(node.nodeId, event.target.checked)} /> 포함</label>
      <input aria-label={`${node.nodeId} 기능명`} value={node.title} disabled={disabled}
        onChange={event => onTitle(node.nodeId, event.target.value)} />
      <select aria-label={`${node.title} 상위 기능`} disabled={disabled} value={findMenuParentSection(allNodes, node.nodeId)}
        onChange={event => onMove(node.nodeId, event.target.value)}>
        <option value="">최상위 기능</option>{allNodes.filter(candidate => candidate.nodeId !== node.nodeId
          && !isMenuDescendant(node, candidate.nodeId) && candidate.included !== false)
          .map(candidate => <option key={candidate.nodeId} value={candidate.nodeId}>{candidate.featurePath.join(' › ')}</option>)}
      </select>
      <small>화면 요소 {node.nodeId}</small>
      {node.evidence.map((item, index) => <small key={index}>근거: {item}</small>)}
    </div>
    {node.children?.length > 0 && <MenuTreeEditor nodes={node.children} allNodes={allNodes} disabled={disabled}
      onTitle={onTitle} onInclude={onInclude} onMove={onMove} />}
  </li>)}</ul>
}
function isMenuDescendant(node: MenuNode, section: string): boolean {
  return (node.children ?? []).some(child => child.nodeId === section || isMenuDescendant(child, section))
}
function findMenuParentSection(nodes: MenuNode[], section: string): string {
  for (const node of nodes) {
    if ((node.children ?? []).some(child => child.nodeId === section)) return node.nodeId
    const parent = findMenuParentSection(node.children ?? [], section)
    if (parent) return parent
  }
  return ''
}
