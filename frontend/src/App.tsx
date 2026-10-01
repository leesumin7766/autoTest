import { useState } from 'react'
import './App.css'

type Role = 'AGREEMENT' | 'FUNCTION_LIST' | 'MANUAL'
type UploadState = 'idle' | 'uploading' | 'success' | 'error'

type DocumentResult = {
  role: Role
  fileType: string
  format: string
  originalFilename: string
  storedPath: string
  extractedText: string
  status: 'UPLOADED' | 'PARSED' | 'FAILED'
  failureReason: string | null
}

type SubmissionResponse = {
  submissionId: string
  status: string
  failureReason: string | null
  documents: DocumentResult[]
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
  const [files, setFiles] = useState<Partial<Record<Role, File>>>({})
  const [states, setStates] = useState<Partial<Record<Role, UploadState>>>({})
  const [documents, setDocuments] = useState<Partial<Record<Role, DocumentResult>>>({})
  const [errors, setErrors] = useState<Partial<Record<Role, string>>>({})
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')

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
    if (activeSubmissionId) {
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
      const result = payload.documents.find((document) => document.role === role)
      if (result) updateDocument(result)
      return createdSubmissionId
    }

    updateDocument(payload)
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
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '조회에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const startNewSet = () => {
    setSubmissionId('')
    setFiles({})
    setStates({})
    setDocuments({})
    setErrors({})
    setNotice('')
  }

  const selectedCount = roles.filter(({ id }) => files[id]).length
  const registeredCount = Object.keys(documents).length

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
                      type="file"
                      accept={acceptedFiles}
                      disabled={busy || Boolean(document)}
                      onChange={(event) => {
                        const nextFile = event.target.files?.[0]
                        if (nextFile) {
                          setFiles((current) => ({ ...current, [id]: nextFile }))
                          setStates((current) => ({ ...current, [id]: 'idle' }))
                          setErrors((current) => ({ ...current, [id]: '' }))
                        }
                      }}
                    />
                    <span>{selectedFile ? '파일 변경' : document ? '등록 완료' : '파일 선택'}</span>
                  </label>
                  {selectedFile && !document && (
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
      </section>
      <footer className="page-footer"><span>AUTOTEST DOCUMENT INTAKE</span><span>SECURE FILE VALIDATION · TEXT EXTRACTION</span></footer>
    </main>
  )
}

export default App
