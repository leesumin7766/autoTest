import { useState } from 'react'
import './App.css'
import { ProductDescriptionPanel } from './ProductDescriptionPanel'
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

function App() {
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
  const [aiDelivery, setAiDelivery] = useState<AiDeliveryResult>({
    status: 'NOT_READY', attempts: 0, lastError: null, deliveredAt: null, updatedAt: null, retryable: false, preflight: null,
  })

  const updateAiDelivery = (delivery?: AiDeliveryResult) => {
    if (delivery) setAiDelivery(delivery)
  }

  const updateDocument = (document: DocumentResult) => {
    setDocuments((current) => ({ ...current, [document.role]: document }))
    setStates((current) => ({ ...current, [document.role]: document.status === 'FAILED' ? 'error' : 'success' }))
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
        nextStates[document.role] = document.status === 'FAILED' ? 'error' : 'success'
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
  const isReplaceable = (document?: DocumentResult) =>
    Boolean(document && (document.status === 'FAILED' || aiDelivery.status === 'BLOCKED'))

  return (
    <main className="workspace">
      <header className="topbar">
        <a className="wordmark" href="/" aria-label="AutoTest 홈">
          <span className="wordmark-mark">A</span>
          <span>autotest<span className="wordmark-dot">.</span></span>
        </a>
        <div className="topbar-label">문서 제출</div>
        <span className="system-state"><span /> 문서 처리 준비</span>
      </header>

      <section className="intro">
        <div className="intro-copy">
          <p className="eyebrow">DOCUMENT INTAKE <span>01 / 03</span></p>
          <h1>시험 문서<br /><em>업로드</em></h1>
          <p className="intro-description">제품 검토에 필요한 세 문서를 역할별로 등록합니다.<br />한 번에 선택하거나 각 문서를 나누어 올릴 수 있습니다.</p>
        </div>
        <div className="intro-stamp" aria-hidden="true">
          <span className="stamp-line" />
          <span>REVIEW<br />READY</span>
          <span className="stamp-count">03</span>
        </div>
      </section>

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

      <section className="document-section">
        <div className="section-heading">
          <div>
            <p className="eyebrow">REQUIRED DOCUMENTS</p>
            <h2>검토 자료 <span>{registeredCount.toString().padStart(2, '0')} / 03</span></h2>
          </div>
          {submissionId && <button className="text-button" type="button" onClick={startNewSet} disabled={busy}>새 문서 세트</button>}
        </div>

        <div className="document-list">
          {roles.map(({ id, title, number, description }) => {
            const state = states[id] ?? 'idle'
            const document = documents[id]
            const selectedFile = files[id]
            return (
              <article className={`document-row state-${state}`} key={id}>
                <div className="document-number">{number}</div>
                <div className="document-info">
                  <div className="document-title-line">
                    <h3>{title}</h3>
                    <span className={`status-label status-${state}`}>
                      {state === 'uploading' ? '업로드 중' : state === 'success' ? '완료' : state === 'error' ? '오류' : '필수'}
                    </span>
                  </div>
                  <p>{description}</p>
                  {document && (
                    <div className="result-detail">
                      <span>{document.originalFilename}</span>
                      <span>{document.format} · {document.fileType}</span>
                      {document.status === 'FAILED' ? <span className="error-copy">{document.failureReason}</span> : <span>텍스트 {document.extractedText.length.toLocaleString()}자 추출</span>}
                    </div>
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
                    <button className="upload-one" type="button" disabled={busy} onClick={() => void uploadRoles([id])}>업로드</button>
                  )}
                </div>
                {selectedFile && <div className="selected-file">{selectedFile.name} <span>{(selectedFile.size / 1024 / 1024).toFixed(2)} MB</span></div>}
              </article>
            )
          })}
        </div>

        <div className="upload-footer">
          <div className="format-note">
            <span className="note-mark">i</span>
            <span>PDF · Excel · HWP · Word <small>파일당 최대 100 MB</small></span>
          </div>
          <button className="primary-button" type="button" disabled={busy || selectedCount === 0} onClick={() => void uploadRoles(roles.map(({ id }) => id))}>
            {busy ? '업로드 중…' : `선택한 문서 업로드${selectedCount ? ` · ${selectedCount}` : ''}`}
            <span aria-hidden="true">↗</span>
          </button>
        </div>
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
            {aiDelivery.preflight && (
              <div className={`preflight-result preflight-${aiDelivery.preflight.decision.toLowerCase()}`}>
                <strong>LLM 사전 점검: {aiDelivery.preflight.decision === 'BLOCKED' ? '중단 권고' : aiDelivery.preflight.decision === 'READY_WITH_WARNINGS' ? '경고 후 진행' : '통과'}</strong>
                <PreflightWarningDetails warnings={aiDelivery.preflight.warnings}
                  diagnostics={aiDelivery.preflight.diagnostics} />
              </div>
            )}
          </div>
        )}
        {submissionId && (
          <ProductDescriptionPanel
            submissionId={submissionId}
            memberId={memberId}
            deliveryStatus={aiDelivery.status}
            preflight={aiDelivery.preflight}
          />
        )}
      </section>
      <footer className="page-footer"><span>AUTOTEST DOCUMENT INTAKE</span><span>SECURE FILE VALIDATION · TEXT EXTRACTION</span></footer>
    </main>
  )
}

export default App
