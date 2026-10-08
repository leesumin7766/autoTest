import { useCallback, useEffect, useRef, useState } from 'react'
import { PreflightWarningDetails, type PreflightWarning, type PreflightDiagnostics } from './PreflightWarningDetails'

type Tc = {
  tcId: string; featurePath: string[]; qualityCharacteristic: string; subCharacteristic: string
  scenario: string; preconditions: string[]; steps: string[]; inputs: string; expectedResult: string
  reviewNotes: string[]; defectExampleSummary: string; defectExampleSeverity: string; defectExampleContent: string
  executionMode: string; result: null
  sources: { fileId: string; role: string; quote: string; characterStart: number; characterEnd: number }[]
}
type Job = {
  jobId: string; status: 'GENERATING' | 'COMPLETED' | 'FAILED' | 'CANCELED'
  completedChunks: number; totalChunks: number; errorCode: string | null
  output: { generationMode: string; testCases: Tc[] } | null
}
const failureMessages: Record<string, string> = {
  TC_INPUT_BUDGET_INSUFFICIENT: '문서 경고와 생성 지침이 모델 입력 한도를 초과했습니다.',
  LLM_INPUT_BUDGET_EXCEEDED: 'TC 생성 요청이 모델 입력 한도를 초과했습니다.',
  LLM_OUTPUT_INCOMPLETE: '모델 응답이 출력 한도에서 중단됐습니다.',
  TC_EVIDENCE_INVALID: '모델이 제시한 근거 인용을 원문에서 확인하지 못했습니다.',
  TC_SCHEMA_INVALID: '모델의 TC 응답 구조가 올바르지 않습니다.',
  TC_NO_GROUNDED_CASES: '원문 근거가 있는 TC를 생성하지 못했습니다.',
  INTERRUPTED: '서버 재시작이나 연결 중단으로 생성이 중단됐습니다.',
}
type Props = {
  submissionId: string; memberId: string; deliveryStatus: string; revision: string
  preflight: { decision: string; warnings: PreflightWarning[]; diagnostics?: PreflightDiagnostics
    generationGate?: { allowed: boolean } } | null
}

export function TcGenerationPanel({ submissionId, memberId, deliveryStatus, preflight, revision }: Props) {
  const [job, setJob] = useState<Job | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [page, setPage] = useState(0)
  const dialog = useRef<HTMLDialogElement>(null)
  const approveButton = useRef<HTMLButtonElement>(null)
  const base = `/api/submissions/${submissionId}/test-cases`
  const promptKey = `tc-consent:${memberId}:${submissionId}:${revision}`
  const ready = deliveryStatus === 'DELIVERED' && ['READY', 'READY_WITH_WARNINGS'].includes(preflight?.decision ?? '')
    && preflight?.generationGate?.allowed !== false
  const active = job?.status === 'GENERATING'

  const refresh = useCallback(async (signal?: AbortSignal) => {
    const response = await fetch(`/api/submissions/${submissionId}/test-cases`, { headers: { 'X-Member-Id': memberId }, signal })
    if (!response.ok) throw new Error('TC 작업 조회에 실패했습니다. 회원 ID와 문서 세트를 확인해 주세요.')
    const data = await response.json() as { job: Job | null }
    if (!signal?.aborted) { setJob(data.job); setLoaded(true); setError('') }
  }, [submissionId, memberId])

  useEffect(() => {
    const controller = new AbortController()
    void refresh(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message) })
    return () => controller.abort()
  }, [refresh])
  useEffect(() => {
    if (!active) return
    const controller = new AbortController()
    const timer = window.setInterval(() => {
      void refresh(controller.signal).catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message) })
    }, 3000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [active, refresh])
  useEffect(() => {
    if (loaded && ready && !job && !sessionStorage.getItem(promptKey)) {
      dialog.current?.showModal()
      approveButton.current?.focus()
    }
  }, [loaded, ready, job, promptKey])
  useEffect(() => { if (!ready) dialog.current?.close() }, [ready])

  const postpone = () => { sessionStorage.setItem(promptKey, 'later'); dialog.current?.close() }
  const start = async () => {
    if (busy || !ready) return
    setBusy(true); setError('')
    try {
      const response = await fetch(base, { method: 'POST', headers: { 'X-Member-Id': memberId, 'Content-Type': 'application/json' },
        body: JSON.stringify({ approved: true }) })
      const data = await response.json() as { job?: Job; message?: string }
      if (!response.ok) throw new Error(data.message || 'TC 생성을 시작하지 못했습니다.')
      setJob(data.job ?? null); sessionStorage.setItem(promptKey, 'approved'); dialog.current?.close()
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'TC 생성 요청 실패') }
    finally { setBusy(false) }
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
    <p>검증된 문서를 바탕으로 TC와 결함 작성 예시를 생성합니다. 시험 결과는 생성하지 않습니다.</p>
    {!ready && <p>문서 사전 검증을 통과하면 TC 생성 여부를 선택할 수 있습니다.</p>}
    {ready && loaded && !active && job?.status !== 'COMPLETED' &&
      <button className="primary-button" disabled={busy} onClick={() => dialog.current?.showModal()}>TC 생성 여부 선택</button>}
    {error && <p className="error-copy" role="alert">{error} <button onClick={() => void refresh().catch((e: Error) => setError(e.message))}>다시 조회</button></p>}
    {active && <div role="status"><p>TC 생성 중 · {job.completedChunks} / {job.totalChunks || '준비 중'} 청크</p>
      {job.totalChunks > 0 && <progress value={job.completedChunks} max={job.totalChunks} />}
      <button className="quiet-button" disabled={busy} onClick={() => void cancel()}>생성 중단</button></div>}
    {job?.status === 'FAILED' && <p role="alert">TC 생성 실패: {job.errorCode}. {failureMessages[job.errorCode ?? ''] ?? 'TC 생성 요청을 처리하지 못했습니다.'} 위 버튼에서 다시 승인하여 재시도할 수 있습니다.</p>}
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
        <td><details><summary>절차·근거 보기</summary><p>{tc.executionMode === 'MANUAL' ? '수동 시험' : '브라우저 실행 가능 여부 검토 필요'}</p>
          <ol>{tc.steps.map((s,i) => <li key={i}>{s}</li>)}</ol>
          {tc.sources.map((s,i) => <p key={i}>{s.role} · 문자 {s.characterStart}–{s.characterEnd}<br />{s.quote}</p>)}
          {tc.reviewNotes.map((s,i) => <p key={i}>{s}</p>)}</details></td>
      </tr>)}</tbody></table></div>
      <div className="dialog-actions"><button disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
        <span>{page + 1} / {Math.max(1, Math.ceil(cases.length / 20))}</span>
        <button disabled={(page + 1) * 20 >= cases.length} onClick={() => setPage(page + 1)}>다음</button></div>
    </>}
    <dialog ref={dialog} className="dialog tc-dialog" aria-labelledby="tc-confirm-title" onCancel={event => {
      event.preventDefault(); if (!busy) postpone()
    }}>
      <h2 id="tc-confirm-title">문서 검증 완료 · TC를 생성할까요?</h2>
      <p>세 문서에서 테스트케이스 초안과 결함 작성 예시를 생성합니다. 생성된 TC는 담당자 검토가 필요하며 자동 시험은 실행하지 않습니다.</p>
      {preflight && <PreflightWarningDetails warnings={preflight.warnings} diagnostics={preflight.diagnostics} />}
      {error && <p className="error-copy" role="alert">{error}</p>}
      <div className="dialog-actions"><button ref={approveButton} className="primary-button" disabled={busy || !ready} onClick={() => void start()}>
        {busy ? '요청 중…' : '승인하고 TC 생성'}</button><button className="quiet-button" disabled={busy} onClick={postpone}>나중에</button></div>
    </dialog>
  </section>
}
