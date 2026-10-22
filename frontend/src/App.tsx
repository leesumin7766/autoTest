import { useEffect, useRef, useState } from 'react'
import './App.css'
import './workspace.css'
import { Tabs, TabsContent, TabsList, TabsTrigger } from './components/ui/tabs'
import { PlaywrightSetupPanel } from './PlaywrightSetupPanel'
import { FunctionalMenuPanel } from './FunctionalMenuPanel'
import { MenuTcPanel } from './MenuTcPanel'
import { TestExecutionPanel } from './TestExecutionPanel'
import { PreflightWarningDetails, type PreflightDiagnostics, type PreflightWarning } from './PreflightWarningDetails'

type Role = 'AGREEMENT' | 'FUNCTION_LIST' | 'MANUAL'
type UploadState = 'idle' | 'uploading' | 'success' | 'error'
type SubmissionStatus = 'UPLOADED' | 'PARSED' | 'FAILED'

type DocumentResult = {
  fileId: string
  role: Role
  fileType: string
  format: string
  originalFilename: string
  storedPath: string
  extractedText: string
  status: SubmissionStatus
  failureReason: string | null
  aiDelivery?: AiDeliveryResult
}

type AiDeliveryResult = {
  status: 'NOT_READY' | 'PENDING' | 'DELIVERED' | 'FAILED' | 'BLOCKED'
  attempts: number
  lastError: string | null
  deliveredAt: string | null
  updatedAt: string | null
  retryable: boolean
  preflight: PreflightResult | null
}

type PreflightResult = {
  decision: 'BLOCKED' | 'READY_WITH_WARNINGS' | 'READY'
  warnings: PreflightWarning[]
  diagnostics?: PreflightDiagnostics
  generationGate?: { allowed: boolean; reasons: { code: string; role: Role | null; message: string }[] }
}

type SubmissionResponse = {
  submissionId: string
  status: SubmissionStatus
  failureReason: string | null
  documents: DocumentResult[]
  aiDelivery: AiDeliveryResult
}

const roles: { id: Role; title: string; number: string; description: string }[] = [
  { id: 'AGREEMENT', title: '시험합의서', number: '01', description: '시험 범위와 합의 기준' },
  { id: 'FUNCTION_LIST', title: '기능리스트', number: '02', description: '제품 기능과 요구사항' },
  { id: 'MANUAL', title: '제품 매뉴얼', number: '03', description: '사용 절차와 제품 안내' },
]

const acceptedFiles = '.pdf,.xls,.xlsx,.hwp,.hwpx,.doc,.docx'

const upcomingPages = [
  { id: 'tc-generate', title: 'TC 생성', step: '04', description: '승인된 메뉴트리와 문서 근거로 테스트케이스 초안을 생성하고, 검토·수정 및 자동·수동 시험 구분 후 승인합니다.' },
  { id: 'execution', title: '시험 실행', step: '05', description: '승인된 테스트케이스를 실행하고 실제 결과와 화면·API 증거를 기록합니다.' },
  { id: 'defects', title: '결함 검토', step: '06', description: '결함 후보와 재현 근거를 검토하고 확정·기각·추가 시험을 기록합니다.' },
  { id: 'reports', title: '리포트·재시험', step: '07', description: '확정 결함으로 기업용 리포트를 작성하고 패치 후 재시험 이력을 관리합니다.' },
]

const workflowPages = [
  { id: 'documents', title: '자료 등록', step: '01' },
  { id: 'crawl', title: '화면 수집', step: '02' },
  { id: 'menu', title: '메뉴트리', step: '03' },
  ...upcomingPages,
]

function App() {
  const [page, setPage] = useState('documents')
  const [visited, setVisited] = useState<Set<string>>(() => new Set(['documents']))
  const navigate = (next: string) => { setPage(next); setVisited(current => new Set([...current, next])) }
  const [productId, setProductId] = useState('1')
  const [memberId, setMemberId] = useState('1')
  const [submissionId, setSubmissionId] = useState('')
  const [fileSelectionGeneration, setFileSelectionGeneration] = useState(0)
  const [files, setFiles] = useState<Partial<Record<Role, File>>>({})
  const [states, setStates] = useState<Partial<Record<Role, UploadState>>>({})
  const [documents, setDocuments] = useState<Partial<Record<Role, DocumentResult>>>({})
  const [errors, setErrors] = useState<Partial<Record<Role, string>>>({})
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [missingCoreRoles, setMissingCoreRoles] = useState<Role[]>([])
  const [pendingUploadRoles, setPendingUploadRoles] = useState<Role[]>([])
  const missingDocumentsDialog = useRef<HTMLDialogElement>(null)
  const [aiDelivery, setAiDelivery] = useState<AiDeliveryResult>({
    status: 'NOT_READY', attempts: 0, lastError: null, deliveredAt: null, updatedAt: null, retryable: false, preflight: null,
  })

  useEffect(() => {
    if (!submissionId || aiDelivery.status !== 'PENDING') return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      void fetch(`/api/submissions/${submissionId}`, { signal: controller.signal })
        .then(async response => {
          if (response.ok) {
            const value = await response.json() as SubmissionResponse
            if (!controller.signal.aborted) setAiDelivery(value.aiDelivery)
          }
        }).catch(() => undefined)
    }, 3000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [submissionId, aiDelivery.status])

  const updateAiDelivery = (delivery?: AiDeliveryResult) => {
    if (delivery) setAiDelivery(delivery)
  }

  const updateDocument = (document: DocumentResult) => {
    setDocuments((current) => ({ ...current, [document.role]: document }))
    setStates((current) => ({ ...current, [document.role]: document.status === 'FAILED' ? 'error' : document.status === 'PARSED' ? 'success' : 'idle' }))
    setErrors((current) => ({ ...current, [document.role]: document.failureReason ?? '' }))
  }

  const sendFile = async (role: Role, file: File, activeSubmissionId: string): Promise<string> => {
    setStates((current) => ({ ...current, [role]: 'uploading' }))
    setErrors((current) => ({ ...current, [role]: '' }))

    const form = new FormData()
    form.append('file', file)
    form.append('role', role)

    let response: Response
    if (activeSubmissionId && (documents[role]?.status === 'FAILED' || (documents[role] && aiDelivery.status === 'BLOCKED'))) {
      response = await fetch(`/api/submissions/${activeSubmissionId}/files/${role}/replace`, {
        method: 'POST',
        headers: { 'X-Member-Id': memberId },
        body: form,
      })
    } else if (activeSubmissionId) {
      response = await fetch(`/api/submissions/${activeSubmissionId}/files`, {
        method: 'POST',
        headers: { 'X-Member-Id': memberId },
        body: form,
      })
    } else {
      form.append('productId', productId)
      response = await fetch('/api/submissions', {
        method: 'POST',
        headers: { 'X-Member-Id': memberId },
        body: form,
      })
    }

    const responseBody = await response.text()
    if (!response.ok) throw new Error(responseBody || '문서 업로드에 실패했습니다.')

    const payload = JSON.parse(responseBody) as SubmissionResponse | DocumentResult
    if ('submissionId' in payload) {
      const createdSubmissionId = payload.submissionId
      setSubmissionId(createdSubmissionId)
      updateAiDelivery(payload.aiDelivery)
      const result = payload.documents.find((document) => document.role === role)
      if (result) updateDocument(result)
      return createdSubmissionId
    }

    updateDocument(payload)
    updateAiDelivery(payload.aiDelivery)
    return activeSubmissionId
  }

  const uploadRoles = async (selectedRoles: Role[]) => {
    if (!productId.trim() || !memberId.trim()) {
      setNotice('제품 ID와 회원 ID를 입력해 주세요.')
      return
    }
    setBusy(true)
    setNotice('')
    let activeSubmissionId = submissionId
    for (const role of selectedRoles) {
      const file = files[role]
      if (!file) continue
      try {
        activeSubmissionId = await sendFile(role, file, activeSubmissionId)
        setSubmissionId(activeSubmissionId)
        setFiles((current) => ({ ...current, [role]: undefined }))
      } catch (error) {
        const message = error instanceof Error ? error.message : '문서 업로드에 실패했습니다.'
        setStates((current) => ({ ...current, [role]: 'error' }))
        setErrors((current) => ({ ...current, [role]: message }))
        setNotice('일부 문서를 업로드하지 못했습니다. 오류가 있는 역할을 확인해 주세요.')
      }
    }
    setBusy(false)
  }

  const requestUpload = (selectedRoles: Role[]) => {
    const rolesToUpload = selectedRoles.filter((role) => files[role])
    if (!rolesToUpload.length) return
    const missing = (['FUNCTION_LIST', 'MANUAL'] as Role[]).filter((role) => {
      const alreadyParsed = documents[role]?.status === 'PARSED'
      return !alreadyParsed && !rolesToUpload.includes(role)
    })
    if (missing.length) {
      setMissingCoreRoles(missing)
      setPendingUploadRoles(rolesToUpload)
      missingDocumentsDialog.current?.showModal()
      return
    }
    void uploadRoles(rolesToUpload)
  }

  const continueUploadWithMissingDocuments = () => {
    const rolesToUpload = pendingUploadRoles
    setPendingUploadRoles([])
    missingDocumentsDialog.current?.close()
    void uploadRoles(rolesToUpload)
  }

  const loadSubmission = async () => {
    if (!submissionId.trim()) return
    setBusy(true)
    setNotice('')
    try {
      const response = await fetch(`/api/submissions/${submissionId.trim()}`)
      if (!response.ok) throw new Error(response.status === 404 ? '문서 세트를 찾을 수 없습니다.' : '조회에 실패했습니다.')
      const result = await response.json() as SubmissionResponse
      const nextDocuments: Partial<Record<Role, DocumentResult>> = {}
      const nextStates: Partial<Record<Role, UploadState>> = {}
      const nextErrors: Partial<Record<Role, string>> = {}
      result.documents.forEach((document) => {
        nextDocuments[document.role] = document
        nextStates[document.role] = document.status === 'FAILED' ? 'error' : document.status === 'PARSED' ? 'success' : 'idle'
        nextErrors[document.role] = document.failureReason ?? ''
      })
      setDocuments(nextDocuments)
      setStates(nextStates)
      setErrors(nextErrors)
      setSubmissionId(result.submissionId)
      updateAiDelivery(result.aiDelivery)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '조회에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const retryAiDelivery = async () => {
    if (!submissionId) return
    setBusy(true)
    try {
      const response = await fetch(`/api/submissions/${submissionId}/ai-delivery/retry`, {
        method: 'POST',
        headers: { 'X-Member-Id': memberId },
      })
      if (!response.ok) throw new Error('AI 전달 재시도에 실패했습니다.')
      updateAiDelivery(await response.json() as AiDeliveryResult)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'AI 전달 재시도에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const startNewSet = () => {
    navigate('documents')
    setSubmissionId('')
    setFileSelectionGeneration((generation) => generation + 1)
    setFiles({})
    setStates({})
    setDocuments({})
    setErrors({})
    setNotice('')
    setAiDelivery({ status: 'NOT_READY', attempts: 0, lastError: null, deliveredAt: null, updatedAt: null, retryable: false, preflight: null })
  }

  const selectedCount = roles.filter(({ id }) => files[id]).length
  const registeredCount = Object.keys(documents).length
  const validating = aiDelivery.status === 'PENDING' && !aiDelivery.retryable
  const validationReady = aiDelivery.status === 'DELIVERED' && ['READY', 'READY_WITH_WARNINGS'].includes(aiDelivery.preflight?.decision || '')
  const pageTitle = workflowPages.find(item => item.id === page)?.title || '자료 등록'
  const validationLabel = validating ? 'Jev 문서 검증 중' : aiDelivery.status === 'BLOCKED' ? '검증 차단' : aiDelivery.status === 'FAILED' ? '전달 실패' : validationReady ? aiDelivery.preflight?.decision === 'READY_WITH_WARNINGS' ? '검증 완료 · 경고 있음' : '검증 완료' : '문서 준비 대기'
  const isReplaceable = (document?: DocumentResult) =>
    Boolean(document && (document.status === 'FAILED' || aiDelivery.status === 'BLOCKED'))

  return (
    <div className="workspace">
      <header className="topbar">
        <a className="wordmark" href="/" aria-label="AutoTest 홈">
          <span className="wordmark-mark">A</span>
          <span>autotest<span className="wordmark-dot">.</span></span>
        </a>
        <div className="topbar-label">시험 작업 공간</div>
        <span className="system-state"><span /> {validationLabel}</span>
      </header>

      <div className="app-shell">
      <aside className="workspace-sidebar" aria-label="작업 탐색">
        <div className="sidebar-title">시험 문서 작업</div>
        <nav className="workspace-nav" aria-label="작업 단계">
          {workflowPages.map(({ id: value, title: label, step }) =>
            <button key={value} type="button" aria-current={page === value ? 'page' : undefined} onClick={() => navigate(value)}>
              <span className="nav-step">{step}</span>{label}
              {value === 'documents' && <span className="nav-count">{registeredCount}/3</span>}
            </button>)}
        </nav>
        <details className="workspace-connection" open>
        <summary>문서 세트 · 연결 정보</summary>
      <section className="submission-meta" aria-label="제출 정보">
        <label>
          <span>제품 ID</span>
          <input value={productId} onChange={(event) => setProductId(event.target.value)} inputMode="numeric" />
        </label>
        <label>
          <span>회원 ID</span>
          <input value={memberId} onChange={(event) => setMemberId(event.target.value)} inputMode="numeric" />
        </label>
        <div className="set-lookup">
          <label>
            <span>문서 세트 ID</span>
            <input value={submissionId} onChange={(event) => setSubmissionId(event.target.value)} placeholder="업로드 후 자동 생성" />
          </label>
          <button className="quiet-button" type="button" onClick={loadSubmission} disabled={busy || !submissionId.trim()}>세트 조회</button>
        </div>
      </section>
        </details>
        <button className="quiet-button new-set-button" type="button" onClick={startNewSet} disabled={busy}>＋ 새 문서 세트</button>
        <p className="sidebar-help">기능리스트·매뉴얼 필수<br />시험합의서 선택</p>
      </aside>
      <main className="workspace-main">
        <div className="workspace-document-tab"><span className="tab-marker" />{pageTitle}<span className="workspace-tab-caption">{submissionId ? '문서 세트 연결됨' : '새 문서 세트'}</span></div>
        <div className="workspace-page-heading"><div><p>시험 작업 / {pageTitle}</p><h1>{pageTitle}</h1></div>
          <span className={`workspace-badge ${validationReady ? 'badge-success' : validating ? 'badge-pending' : ''}`} role="status">{validating && <span className="spinner" aria-hidden="true" />}{validationLabel}</span>
        </div>
      <section className="document-section">
      <div hidden={page !== 'documents'}>
        <div className="section-heading">
          <div>
            <h2>문서 선택 <span>{registeredCount} / 3</span></h2>
          </div>
          {submissionId && <button className="text-button" type="button" onClick={startNewSet} disabled={busy}>새 문서 세트</button>}
        </div>

        <div className="document-list">
          {roles.map(({ id, title }) => {
            const state = states[id] ?? 'idle'
            const document = documents[id]
            const selectedFile = files[id]
            return (
              <article className={`document-row state-${state} ${validating && document?.status === 'PARSED' ? 'is-validating' : ''}`} key={id}>
                <div className="document-info">
                  <div className="document-title-line">
                    <h3>{title}</h3>
                    <span className={`status-label status-${state}`}>
                      {state === 'uploading' ? '업로드 중' : state === 'success' ? '완료' : state === 'error' ? '오류' : id === 'AGREEMENT' ? '선택' : '필수'}
                    </span>
                  </div>
                  <p className="document-filename">{selectedFile?.name || document?.originalFilename || '선택한 파일 없음'}</p>
                  {document && (
                    <details className="document-result-details"><summary>문서 처리 상세</summary><div className="result-detail">
                      <span>{document.format} · {document.fileType}</span>
                      {document.status === 'FAILED' ? <span className="error-copy">{document.failureReason}</span> : <span>텍스트 {document.extractedText.length.toLocaleString()}자 추출</span>}
                    </div>
                    </details>
                  )}
                  {document?.status === 'PARSED' && (
                    <details className="document-preview">
                      <summary>추출 텍스트 및 저장 경로 보기</summary>
                      <pre>{document.extractedText || '추출된 텍스트가 없습니다.'}</pre>
                      <code>{document.storedPath}</code>
                    </details>
                  )}
                  {errors[id] && <p className="error-copy" role="alert">{errors[id]}</p>}
                </div>
                <div className="document-actions">
                  <label className={`file-picker ${document ? 'is-complete' : ''}`}>
                    <input
                      key={`${id}-${fileSelectionGeneration}`}
                      type="file"
                      accept={acceptedFiles}
                      disabled={busy || Boolean(document && !isReplaceable(document))}
                      onChange={(event) => {
                        const nextFile = event.target.files?.[0]
                        if (nextFile) {
                          setFiles((current) => ({ ...current, [id]: nextFile }))
                          setStates((current) => ({ ...current, [id]: 'idle' }))
                          setErrors((current) => ({ ...current, [id]: '' }))
                        }
                      }}
                    />
                    <span>{selectedFile ? '파일 변경' : document?.status === 'FAILED' ? '실패 문서 교체' : document && aiDelivery.status === 'BLOCKED' ? '문서 교체' : document ? '등록 완료' : '파일 선택'}</span>
                  </label>
                  {selectedFile && (!document || isReplaceable(document)) && (
                    <button className="upload-one" type="button" disabled={busy} onClick={() => requestUpload([id])}>업로드</button>
                  )}
                </div>
                {state === 'uploading' && <p className="document-progress" role="status"><span className="spinner" aria-hidden="true" /> 문서 업로드·텍스트 추출 중…</p>}
                {validating && document?.status === 'PARSED' && <p className="document-progress" role="status"><span className="spinner" aria-hidden="true" /> Jev 가독성·제품 일치 여부 검증 중…</p>}
              </article>
            )
          })}
        </div>

        <div className="upload-footer">
          <div className="format-note">
            <span className="note-mark">i</span>
            <span>PDF · Excel · HWP · Word <small>파일당 최대 100 MB</small></span>
          </div>
          <button className="primary-button" type="button" disabled={busy || selectedCount === 0} onClick={() => requestUpload(roles.map(({ id }) => id))}>
            {busy ? '업로드·추출 중…' : `자료 업로드·검증${selectedCount ? ` · ${selectedCount}` : ''}`}
          </button>
        </div>
        <dialog ref={missingDocumentsDialog} className="dialog tc-dialog" aria-labelledby="missing-documents-title" onCancel={() => setPendingUploadRoles([])}>
          <h2 id="missing-documents-title">필수 문서가 아직 없습니다</h2>
          <p>{missingCoreRoles.map((role) => roles.find((item) => item.id === role)?.title).join(', ')} 문서가 업로드되지 않았습니다.</p>
          <p>선택한 파일은 계속 업로드할 수 있습니다. 기능리스트와 매뉴얼이 모두 정상 파싱된 뒤 Jev 사전 점검을 진행합니다.
            시험합의서는 선택 문서이며, 없으면 사전 점검 결과에 경고로 표시됩니다.</p>
          <div className="dialog-actions">
            <button className="primary-button" onClick={continueUploadWithMissingDocuments}>선택한 문서 계속 업로드</button>
            <button className="quiet-button" onClick={() => { setPendingUploadRoles([]); missingDocumentsDialog.current?.close() }}>취소</button>
          </div>
        </dialog>
        </div>
        <div className="workflow-page" hidden={page !== 'crawl'}>
          {!submissionId && <div className="workspace-empty"><h2>등록된 문서 세트가 없습니다</h2><p>자료를 등록하거나 왼쪽에서 기존 문서 세트를 조회하세요.</p><button className="primary-button" onClick={() => navigate('documents')}>자료 등록으로 이동</button></div>}
          {submissionId && visited.has('crawl') && <PlaywrightSetupPanel key={`${submissionId}:${memberId}`} submissionId={submissionId} memberId={memberId} deliveryStatus={aiDelivery.status} preflight={aiDelivery.preflight} />}
        </div>
        <div className="workflow-page" hidden={page !== 'menu'}>
          {(!validationReady || aiDelivery.preflight?.generationGate?.allowed === false) && <div className="workspace-empty"><h2>{submissionId ? '문서 검증을 먼저 완료해주세요' : '등록된 문서 세트가 없습니다'}</h2><p>기능리스트·매뉴얼의 Jev 검증을 완료한 뒤 화면 수집 결과로 메뉴트리를 작성할 수 있습니다.</p><button className="primary-button" onClick={() => navigate('documents')}>자료 등록·검증으로 이동</button></div>}
          {submissionId && visited.has('menu') && <FunctionalMenuPanel key={`functional:${submissionId}:${memberId}`} submissionId={submissionId} memberId={memberId} ready={validationReady && aiDelivery.preflight?.generationGate?.allowed !== false} />}
        </div>
        <div className="workflow-page" hidden={page !== 'tc-generate'}>
          {!submissionId && <div className="workspace-empty"><h2>등록된 문서 세트가 없습니다</h2><p>자료 등록과 메뉴트리 승인을 완료한 뒤 TC를 생성하세요.</p><button className="primary-button" onClick={() => navigate('documents')}>자료 등록으로 이동</button></div>}
          {submissionId && visited.has('tc-generate') && <MenuTcPanel key={`menu-tc:${submissionId}:${memberId}`} submissionId={submissionId} memberId={memberId} />}
        </div>
        <div className="workflow-page" hidden={page !== 'execution'}>
          {!submissionId && <div className="workspace-empty"><h2>등록된 문서 세트가 없습니다</h2><p>자료 등록과 TC 생성을 완료한 뒤 시험을 준비하세요.</p><button className="primary-button" onClick={() => navigate('documents')}>자료 등록으로 이동</button></div>}
          {submissionId && visited.has('execution') && <TestExecutionPanel key={`execution:${submissionId}:${memberId}`} submissionId={submissionId} memberId={memberId} />}
        </div>
        {upcomingPages.filter(item => item.id !== 'tc-generate' && item.id !== 'execution').map(item => <section key={item.id} className="workflow-page workspace-empty" hidden={page !== item.id} aria-label={item.title}>
          <span className="workspace-badge">준비 중</span>
          <h2>{item.title}</h2>
          <p>{item.description}</p>
          <p>메뉴 구성 확인용 화면입니다. 기능 연결은 다음 작업에서 진행합니다.</p>
        </section>)}
      </section>
      <section className="workspace-response" aria-label="처리 결과">
        <div className="response-heading"><h2>처리 결과</h2><div className="response-metrics"><span className={`workspace-badge ${validationReady ? 'badge-success' : validating ? 'badge-pending' : ''}`}>{validationLabel}</span><span>등록 문서 <strong>{registeredCount} / 3</strong></span><span>선택 파일 <strong>{selectedCount}</strong></span></div></div>
        <Tabs defaultValue="status">
          <TabsList aria-label="처리 결과 보기"><TabsTrigger value="status">진행 상태</TabsTrigger><TabsTrigger value="diagnostics">Jev 검증·진단</TabsTrigger></TabsList>
          <TabsContent value="status">
        {notice && <p className="global-notice" role="alert">{notice}</p>}
        {submissionId && <p className="set-confirmation"><span /> 문서 세트 연결됨 <code>{submissionId}</code></p>}
        {submissionId && (
          <div className={`ai-delivery ai-delivery-${aiDelivery.status.toLowerCase()}`} role="status">
            <span>AI 전달: {aiDelivery.status === 'DELIVERED' ? '완료' : aiDelivery.status === 'BLOCKED' ? '사전 점검으로 차단됨' : aiDelivery.status === 'FAILED' ? '실패' : aiDelivery.status === 'PENDING' ? aiDelivery.retryable ? '중단됨' : '진행 중' : '문서 준비 대기'}</span>
            {aiDelivery.status === 'BLOCKED' && <span>문서 원본과 추출 텍스트는 보관됩니다. 아래 사유를 확인하고 문서를 교체하면 같은 세트가 다시 점검됩니다.</span>}
            {aiDelivery.status === 'FAILED' && <span>{aiDelivery.lastError}</span>}
            {aiDelivery.status === 'PENDING' && aiDelivery.retryable && <span>오래된 전달 시도를 복구할 수 있습니다.</span>}
            {aiDelivery.retryable && <button className="quiet-button" type="button" onClick={() => void retryAiDelivery()} disabled={busy}>{aiDelivery.status === 'PENDING' ? '전달 복구' : '전달 재시도'}</button>}
            {aiDelivery.status === 'DELIVERED' && <span>문서 업로드 완료 · AI 전달 완료</span>}
            {validating && <p className="document-progress"><span className="spinner" aria-hidden="true" /> Jev에서 문서 가독성과 동일 제품 여부를 확인하고 있습니다.</p>}
          </div>
        )}
        {!submissionId && <p className="response-empty">문서를 등록하면 업로드 상태와 Jev 검증 결과가 여기에 표시됩니다.</p>}
          </TabsContent>
          <TabsContent value="diagnostics">
            {aiDelivery.preflight ? (
              <div className={`preflight-result preflight-${aiDelivery.preflight.decision.toLowerCase()}`}>
                <strong>LLM 사전 점검: {aiDelivery.preflight.decision === 'BLOCKED' ? '중단 권고' : aiDelivery.preflight.decision === 'READY_WITH_WARNINGS' ? '경고 후 진행' : '통과'}</strong>
                <PreflightWarningDetails warnings={aiDelivery.preflight.warnings}
                  diagnostics={aiDelivery.preflight.diagnostics} />
              </div>
            ) : <p className="response-empty">{validating ? 'Jev 검증 중입니다. 완료되면 진단을 표시합니다.' : '아직 검증 결과가 없습니다.'}</p>}
          </TabsContent>
        </Tabs>
      </section>
      <footer className="page-footer"><span>AutoTest · 시험 문서 작업</span><span>문서 등록 → 화면 수집 → 메뉴트리</span></footer>
      </main>
      </div>
    </div>
  )
}

export default App
