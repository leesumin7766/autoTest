import { useCallback, useEffect, useRef, useState } from 'react'
import { PreflightWarningDetails, type PreflightDiagnostics, type PreflightWarning } from './PreflightWarningDetails'

type JobStatus = 'GENERATING_CONTENT' | 'RENDERING' | 'COMPLETED' | 'CONTENT_FAILED' | 'CONTENT_PAUSED' | 'RENDER_FAILED' | 'CANCELED'

export type ProductDescriptionJob = {
  jobId: string
  status: JobStatus
  stage: 'CONTENT' | 'OUTPUT' | 'DONE'
  active: boolean
  resumable: boolean
  completedChunks: number
  totalChunks: number
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
  warnings: PreflightWarning[]
  diagnostics?: PreflightDiagnostics
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
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [jobStartedAt, setJobStartedAt] = useState(0)
  const [memberHasActiveJob, setMemberHasActiveJob] = useState(false)
  const [memberActiveChecked, setMemberActiveChecked] = useState(false)
  const [error, setError] = useState('')
  const autoStartedFor = useRef('')
  const autoOpenedFor = useRef('')
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
  const ready = deliveryStatus === 'DELIVERED' && (preflight?.decision === 'READY' || preflight?.decision === 'READY_WITH_WARNINGS')
  useEffect(() => {
    if (active && view?.job && autoOpenedFor.current !== view.job.jobId) {
      autoOpenedFor.current = view.job.jobId
      setPopupOpen(true)
      setJobStartedAt(Date.now())
    }
  }, [active, view?.job?.jobId])
  useEffect(() => {
    if (!active) return
    const startedAt = jobStartedAt || Date.now()
    const tick = () => setElapsedSeconds(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)))
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [active, view?.job?.jobId, jobStartedAt])
  useEffect(() => {
    if (!active) return
    const timer = window.setInterval(() => void refresh().catch(() => undefined), POLL_MS)
    return () => window.clearInterval(timer)
  }, [active, refresh])

  useEffect(() => {
    if (!ready) { setMemberHasActiveJob(false); setMemberActiveChecked(true); return }
    let alive = true
    const check = async () => {
      try {
        const response = await fetch('/api/product-descriptions/active', { headers })
        if (response.ok && alive) {
          const activeJob = await response.json() as ProductDescriptionJob | null
          setMemberHasActiveJob(activeJob?.active === true && activeJob.jobId !== view?.job?.jobId)
          setMemberActiveChecked(true)
        }
      } catch { if (alive) setMemberActiveChecked(true) /* The server still enforces the one-job limit. */ }
    }
    void check()
    const timer = window.setInterval(() => void check(), POLL_MS)
    return () => { alive = false; window.clearInterval(timer) }
  }, [ready, memberId, view?.job?.jobId])

  const post = async (path: string, failure: string) => {
    setBusy(true)
    setError('')
    try {
      const response = await fetch(`${base}${path}`, { method: 'POST', headers })
      if (!response.ok) throw new Error(await readError(response, failure))
      setView(await response.json() as StatusView)
      if (path === '' || path.endsWith('/cancel')) setJobStartedAt(Date.now())
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

  useEffect(() => {
    if (!autoStart || !ready || !memberActiveChecked || memberHasActiveJob || autoStartedFor.current === submissionId) return
    autoStartedFor.current = submissionId
    onAutoStarted()
    void start()
    // start depends only on stable inputs for this submission.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoStart, ready, memberActiveChecked, memberHasActiveJob, submissionId])

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
  const chunkProgress = job && job.totalChunks > 0
    ? `${job.completedChunks} / ${job.totalChunks} 청크 완료` : ''
  const estimatedRemaining = job && job.completedChunks > 0 && job.totalChunks > job.completedChunks
    ? Math.max(1, Math.ceil((elapsedSeconds / job.completedChunks) * (job.totalChunks - job.completedChunks))) : null
  const mockLabel = job?.generationLabel ?? lastCompleted?.generationLabel ?? null
  const failedTitle = job?.status === 'CONTENT_PAUSED' || job?.status === 'CANCELED'
    ? '진행 상황을 저장하고 생성을 중단했습니다.'
    : job?.status === 'RENDER_FAILED' ? 'PDF 출력에 실패했습니다. 생성된 내용은 보관되어 있습니다.' : '문서 생성에 실패했습니다.'

  return (
    <>
      <div className="description-panel" role="status">
        <strong>제품 설명 문서</strong>
        <span>
          {!job ? '아직 생성하지 않았습니다.' : job.active ? `생성 중 · ${stageText}`
            : job.status === 'COMPLETED' ? '생성 완료' : job.status === 'CANCELED' || job.status === 'CONTENT_PAUSED' ? '중단됨 · 이어서 가능' : '생성 실패'}
        </span>
        {mockLabel && <span className="mock-badge">{mockLabel}</span>}
        {job && <button className="quiet-button" type="button" onClick={() => setPopupOpen(true)}>상태 보기</button>}
        {!job && ready && <button className="quiet-button" type="button" disabled={busy || active || memberHasActiveJob} onClick={() => void start()}>제품 설명 문서 생성</button>}
        {memberHasActiveJob && <span>다른 제품 설명 문서 생성 작업이 진행 중입니다.</span>}
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

            {((!job && busy) || job?.active) && (
              <>
                <p>{preflight?.decision === 'READY_WITH_WARNINGS' ? WARNING_MESSAGE : READY_MESSAGE}</p>
                <div className="dialog-progress" aria-live="polite">
                  <span className="spinner" aria-hidden="true" />
                  <span>{job ? stageText : '생성 작업을 요청하는 중'}</span>
                </div>
                {job && job.stage === 'CONTENT' && job.totalChunks > 0 && (
                  <div className="chunk-progress" aria-live="polite">
                    <div className="chunk-progress-label"><span>{chunkProgress}</span>
                      {estimatedRemaining !== null && <span>예상 남은 시간 약 {estimatedRemaining >= 60
                        ? `${Math.floor(estimatedRemaining / 60)}분 ${estimatedRemaining % 60}초` : `${estimatedRemaining}초`}</span>}
                    </div>
                    <progress max={job.totalChunks} value={Math.min(job.completedChunks, job.totalChunks)} />
                    <small>이 PC의 처리 속도를 바탕으로 계산한 참고값입니다.</small>
                  </div>
                )}
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
                <PreflightWarningDetails warnings={preflight.warnings} diagnostics={preflight.diagnostics} />
              </details>
            )}
            {error && <p className="error-copy" role="alert">{error}</p>}

            <div className="dialog-actions">
              {job?.active && (
                <button className="quiet-button danger" type="button" disabled={busy}
                  onClick={() => void post(`/${job.jobId}/cancel`, '생성을 중단하지 못했습니다.')}>진행을 저장하고 중단</button>
              )}
              {job?.status === 'COMPLETED' && (
                <button className="primary-button" type="button" disabled={busy} onClick={() => void download(job)}>PDF 다운로드</button>
              )}
              {job?.canRerender && (
                <button className="quiet-button" type="button" disabled={busy}
                  onClick={() => void post(`/${job.jobId}/rerender`, 'PDF를 다시 출력하지 못했습니다.')}>PDF 다시 출력</button>
              )}
              {job?.resumable && (
                <button className="primary-button" type="button" disabled={busy || memberHasActiveJob} onClick={() => void start()}>
                  {job.completedChunks > 0 ? `저장된 ${job.completedChunks}개 청크부터 계속` : '다시 생성'}
                </button>
              )}
              {((job && !job.active && !job.resumable) || (!job && !busy && error)) && (
                <button className="quiet-button" type="button" disabled={busy || memberHasActiveJob} onClick={() => void start()}>다시 생성</button>
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
