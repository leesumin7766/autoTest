import { useCallback, useEffect, useRef, useState } from 'react'

type JobStatus = 'GENERATING_CONTENT' | 'RENDERING' | 'COMPLETED' | 'CONTENT_FAILED' | 'RENDER_FAILED' | 'CANCELED'

export type ProductDescriptionJob = {
  jobId: string
  status: JobStatus
  stage: 'CONTENT' | 'OUTPUT' | 'DONE'
  active: boolean
  errorCode: string | null
  errorMessage: string | null
  generationMode: 'MOCK' | 'REAL' | null
  generationLabel: string | null
  downloadable: boolean
  canRerender: boolean
}

type StatusView = { job: ProductDescriptionJob | null; lastCompleted: ProductDescriptionJob | null }

type Preflight = {
  decision: 'BLOCKED' | 'READY_WITH_WARNINGS' | 'READY'
  warnings: { code: string; role: string | null; severity: string; message: string }[]
} | null

type Props = {
  submissionId: string
  memberId: string
  deliveryStatus: string
  preflight: Preflight
  autoStart: boolean
  onAutoStarted: () => void
}

const POLL_MS = 2000
const READY_MESSAGE = '문서 검증이 완료되었습니다. 제품 설명 문서를 생성하고 있습니다.'
const WARNING_MESSAGE = '문서 검증이 경고와 함께 완료되었습니다. 제품 설명 문서를 생성하고 있습니다.'

async function readError(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json() as { message?: string }
    return body.message || fallback
  } catch {
    return fallback
  }
}

export function ProductDescriptionPanel({ submissionId, memberId, deliveryStatus, preflight, autoStart, onAutoStarted }: Props) {
  const [view, setView] = useState<StatusView | null>(null)
  const [popupOpen, setPopupOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const autoStartedFor = useRef('')
  const base = `/api/submissions/${submissionId}/product-description`
  const headers = { 'X-Member-Id': memberId }

  const refresh = useCallback(async () => {
    const response = await fetch(`/api/submissions/${submissionId}/product-description`, { headers: { 'X-Member-Id': memberId } })
    if (response.ok) setView(await response.json() as StatusView)
  }, [submissionId, memberId])

  useEffect(() => {
    setView(null)
    setPopupOpen(false)
    setError('')
    void refresh().catch(() => undefined)
  }, [refresh])

  const active = view?.job?.active === true
  useEffect(() => {
    if (!active) return
    const timer = window.setInterval(() => void refresh().catch(() => undefined), POLL_MS)
    return () => window.clearInterval(timer)
  }, [active, refresh])

  const post = async (path: string, failure: string) => {
    setBusy(true)
    setError('')
    try {
      const response = await fetch(`${base}${path}`, { method: 'POST', headers })
      if (!response.ok) throw new Error(await readError(response, failure))
      setView(await response.json() as StatusView)
      return true
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : failure)
      return false
    } finally {
      setBusy(false)
    }
  }

  const start = async () => {
    setPopupOpen(true)
    await post('', '제품 설명 문서 생성을 시작하지 못했습니다.')
  }

  const ready = deliveryStatus === 'DELIVERED' && (preflight?.decision === 'READY' || preflight?.decision === 'READY_WITH_WARNINGS')

  useEffect(() => {
    if (!autoStart || !ready || autoStartedFor.current === submissionId) return
    autoStartedFor.current = submissionId
    onAutoStarted()
    void start()
    // start depends only on stable inputs for this submission.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoStart, ready, submissionId])

  const download = async (job: ProductDescriptionJob) => {
    setBusy(true)
    setError('')
    try {
      const response = await fetch(`${base}/${job.jobId}/download`, { headers })
      if (!response.ok) throw new Error(await readError(response, '다운로드에 실패했습니다.'))
      const url = URL.createObjectURL(await response.blob())
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = '제품 설명 문서.pdf'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '다운로드에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const job = view?.job ?? null
  const lastCompleted = view?.lastCompleted ?? null
  if (!ready && !job) return null

  const stageText = job?.stage === 'OUTPUT' ? 'PDF 파일을 만드는 중' : '문서 내용을 생성하는 중'
  const mockLabel = job?.generationLabel ?? lastCompleted?.generationLabel ?? null
  const failedTitle = job?.status === 'CANCELED' ? '생성이 중단되었습니다.'
    : job?.status === 'RENDER_FAILED' ? 'PDF 출력에 실패했습니다. 생성된 내용은 보관되어 있습니다.' : '문서 생성에 실패했습니다.'

  return (
    <>
      <div className="description-panel" role="status">
        <strong>제품 설명 문서</strong>
        <span>
          {!job ? '아직 생성하지 않았습니다.' : job.active ? `생성 중 · ${stageText}`
            : job.status === 'COMPLETED' ? '생성 완료' : job.status === 'CANCELED' ? '중단됨' : '생성 실패'}
        </span>
        {mockLabel && <span className="mock-badge">{mockLabel}</span>}
        {job && <button className="quiet-button" type="button" onClick={() => setPopupOpen(true)}>상태 보기</button>}
        {!job && ready && <button className="quiet-button" type="button" disabled={busy} onClick={() => void start()}>제품 설명 문서 생성</button>}
        {lastCompleted && !job?.active && (
          <button className="quiet-button" type="button" disabled={busy} onClick={() => void download(lastCompleted)}>PDF 다운로드</button>
        )}
        {!popupOpen && error && <span className="error-copy" role="alert">{error}</span>}
      </div>

      {popupOpen && (
        <div className="dialog-backdrop">
          <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="description-dialog-title"
            onKeyDown={(event) => { if (event.key === 'Escape') setPopupOpen(false) }}>
            <h2 id="description-dialog-title">제품 설명 문서</h2>
            {mockLabel && <p><span className="mock-badge">{mockLabel}</span></p>}

            {(!job || job.active) && (
              <>
                <p>{preflight?.decision === 'READY_WITH_WARNINGS' ? WARNING_MESSAGE : READY_MESSAGE}</p>
                <div className="dialog-progress" aria-live="polite">
                  <span className="spinner" aria-hidden="true" />
                  <span>{job ? stageText : '생성 작업을 요청하는 중'}</span>
                </div>
              </>
            )}
            {job?.status === 'COMPLETED' && <p>제품 설명 문서가 생성되었습니다.</p>}
            {job && !job.active && job.status !== 'COMPLETED' && (
              <div className="dialog-failure" role="alert">
                <strong>{failedTitle}</strong>
                {job.errorMessage && <span>{job.errorMessage}</span>}
              </div>
            )}

            {preflight?.decision === 'READY_WITH_WARNINGS' && preflight.warnings.length > 0 && (
              <details className="dialog-warnings">
                <summary>경고 상세 {preflight.warnings.length}건</summary>
                <ul>
                  {preflight.warnings.map((warning, index) => (
                    <li key={`${warning.code}-${index}`}>{warning.role ? `${warning.role}: ` : ''}{warning.message}</li>
                  ))}
                </ul>
              </details>
            )}
            {error && <p className="error-copy" role="alert">{error}</p>}

            <div className="dialog-actions">
              {job?.active && (
                <button className="quiet-button danger" type="button" disabled={busy}
                  onClick={() => void post(`/${job.jobId}/cancel`, '생성을 중단하지 못했습니다.')}>생성 중단</button>
              )}
              {job?.status === 'COMPLETED' && (
                <button className="primary-button" type="button" disabled={busy} onClick={() => void download(job)}>PDF 다운로드</button>
              )}
              {job?.canRerender && (
                <button className="quiet-button" type="button" disabled={busy}
                  onClick={() => void post(`/${job.jobId}/rerender`, 'PDF를 다시 출력하지 못했습니다.')}>PDF 다시 출력</button>
              )}
              {job && !job.active && (
                <button className="quiet-button" type="button" disabled={busy} onClick={() => void start()}>다시 생성</button>
              )}
              <button className="text-button" type="button" onClick={() => setPopupOpen(false)}>
                {job?.active ? '닫기 (생성은 계속됩니다)' : '닫기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
