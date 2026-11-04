import { useEffect, useState } from 'react'
import { apiFetch } from './apiFetch'

type Oracle = { kind: string; quote: string; index?: number }
type Check = { id: string; requirementQuote: string; expected?: string; oracle?: Oracle | null }
type Spec = { sourceTcDigest: string; bindingRevision?: number; status: string; bindings: { target?: { menuKey: string; href: string; heading: string } | null; readContext?: { confirmed?: boolean; oracle: Oracle } | null }; checks: Check[]; missingBindings: { message: string }[] }
type Data = { stale: boolean; targets: { menuKey: string; href: string; label: string }[]; tc: { tcId: string; inputs: string; expectedResult: string; evidence: { quote?: string; sourceId?: string }[]; executionPlan: { specification: Spec } } }
export type ReadBindingCase = { id: string; tcId: string; featurePath: string[]; executionPlan?: { specificationVersion?: string } }

function OracleField({ label, options, value, onChange }: { label: string; options: Oracle[]; value?: Oracle | null; onChange: (value: Oracle | null) => void }) {
  const selected = value ? options.findIndex(option => option.kind === value.kind && option.index === value.index) : -1
  return <fieldset><legend>{label}</legend>
    <label className="tc-field">근거 출처<select aria-label={`${label} 출처`} value={selected} onChange={e => onChange(options[Number(e.target.value)] || null)}>
      <option value={-1}>미연결</option>{options.map((option, index) => <option key={index} value={index}>{option.kind === 'TC_INPUT' ? 'TC 입력·사전조건' : option.kind === 'TC_EXPECTED' ? 'TC 기대 결과' : `문서 근거 ${(option.index || 0) + 1}`}</option>)}
    </select></label>
    {selected >= 0 && <><blockquote>{options[selected].quote}</blockquote><label className="tc-field">원문 인용<textarea aria-label={`${label} 인용`} maxLength={4000} value={value?.quote || ''} onChange={e => onChange({ ...options[selected], quote: e.target.value })} /></label></>}
  </fieldset>
}

export function ReadBindingsEditor({ submissionId, memberId, setId, cases, disabled, onSaved }: { submissionId: string; memberId: string; setId: string; cases: ReadBindingCase[]; disabled: boolean; onSaved: () => Promise<void> }) {
  const candidates = cases.filter(tc => tc.executionPlan?.specificationVersion === 'execution-spec-v2-read')
  const [caseId, setCaseId] = useState(''), [data, setData] = useState<Data | null>(null)
  const [target, setTarget] = useState(-1), [context, setContext] = useState<Oracle | null>(null), [confirmed, setConfirmed] = useState(false)
  const [oracles, setOracles] = useState<Record<string, Oracle | null>>({})
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('')
  const [reload, setReload] = useState(0)
  const url = `/api/submissions/${submissionId}/menu-test-cases/${setId}/cases/${encodeURIComponent(caseId)}/read-bindings`
  const populate = (next: Data) => {
    setData(next)
    const spec = next.tc.executionPlan.specification, saved = spec.bindings.target
    setTarget(saved ? next.targets.findIndex(item => item.menuKey === saved.menuKey && item.href === saved.href && item.label === saved.heading) : -1)
    setContext(spec.bindings.readContext?.oracle || null); setConfirmed(spec.bindings.readContext?.confirmed === true)
    setOracles(Object.fromEntries(spec.checks.map(check => [check.id, check.oracle || null])))
  }
  useEffect(() => {
    setData(null); setMessage(''); setError('')
    if (!caseId) return
    const controller = new AbortController(); setBusy(true)
    void apiFetch(url, { signal: controller.signal, headers: { 'X-Member-Id': memberId } }).then(async response => {
      const next = await response.json(); if (!response.ok) throw new Error(next.message || '연결 정보를 불러오지 못했습니다.')
      if (!controller.signal.aborted) populate(next)
    }).catch(e => { if (!controller.signal.aborted) setError(e.message) }).finally(() => { if (!controller.signal.aborted) setBusy(false) })
    return () => controller.abort()
  }, [caseId, url, memberId, reload])
  if (!candidates.length) return null
  const docs: Oracle[] = (data?.tc.evidence || []).flatMap((item, index) => item.quote ? [{ kind: 'DOCUMENT_EVIDENCE', index, quote: item.quote }] : [])
  const save = async () => {
    if (!data) return
    setBusy(true); setError(''); setMessage('')
    try {
      const spec = data.tc.executionPlan.specification, selected = data.targets[target]
      const response = await apiFetch(url, { method: 'PUT', headers: { 'X-Member-Id': memberId, 'Content-Type': 'application/json' }, body: JSON.stringify({
        revision: spec.bindingRevision || 0, sourceTcDigest: spec.sourceTcDigest,
        target: selected ? { menuKey: selected.menuKey, href: selected.href, heading: selected.label } : null,
        readContext: context ? { mode: 'NO_FILTER', confirmed, oracle: context } : null,
        checks: spec.checks.filter(check => oracles[check.id]).map(check => ({ id: check.id, expected: check.requirementQuote, oracle: oracles[check.id] })),
      }) })
      const next = await response.json(); if (!response.ok) throw new Error(next.message || '근거 연결을 저장하지 못했습니다.')
      populate(next); setMessage('저장했습니다. 아래 시험 준비에서 새 실행을 준비하면 반영됩니다. 기존 실행 이력은 유지됩니다.')
      await onSaved()
    } catch (e) { setError(e instanceof Error ? e.message : '저장 실패') } finally { setBusy(false) }
  }
  return <details><summary>조회 실행 명세 근거 연결 · {candidates.length}개 TC</summary>
    <p>조건 입력 없는 조회의 화면·전제·기대 조건을 연결합니다. 설명형 조건과 데이터 비교는 추가 정보 필요로 남습니다.</p>
    <label className="tc-field">조회 TC<select aria-label="근거 연결할 조회 TC" value={caseId} disabled={busy || disabled} onChange={e => setCaseId(e.target.value)}><option value="">TC 선택</option>{candidates.map(tc => <option key={tc.id} value={tc.id}>{tc.tcId} · {tc.featurePath.join(' → ')}</option>)}</select></label>
    {busy && <p role="status">연결 정보 처리 중…</p>}{error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {caseId && <button type="button" className="quiet-button" disabled={busy} onClick={() => setReload(value => value + 1)}>저장된 연결 다시 불러오기</button>}
    {data && <fieldset disabled={busy || disabled || data.stale}><legend>{data.tc.tcId} · 근거 검토</legend>
      {data.stale && <p role="alert">원본이 변경되었습니다. 새 TC를 생성하세요.</p>}
      <label className="tc-field">대상 화면<select aria-label="조회 대상 화면" value={target} onChange={e => setTarget(Number(e.target.value))}><option value={-1}>미연결</option>{data.targets.map((item, index) => <option key={index} value={index}>{item.label} · {item.href}</option>)}</select></label>
      {!data.targets.length && <p>이 TC에 연결된 수집 화면 근거가 없습니다. 화면 수집·메뉴 연결을 보완한 뒤 TC를 다시 생성하세요.</p>}
      <OracleField label="조회 전제" options={[{ kind: 'TC_INPUT', quote: data.tc.inputs }, ...docs]} value={context} onChange={setContext} />
      <label><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} />선택한 근거를 검토했으며 조건 입력 없이 조회하는 화면임을 확인했습니다.</label>
      {data.tc.executionPlan.specification.checks.map(check => <div key={check.id}><p><strong>{check.id} 기대 조건</strong> · {check.requirementQuote}</p><OracleField label={`${check.id} 기대 조건`} options={[{ kind: 'TC_EXPECTED', quote: data.tc.expectedResult }, { kind: 'TC_INPUT', quote: data.tc.inputs }, ...docs]} value={oracles[check.id]} onChange={value => setOracles(current => ({ ...current, [check.id]: value }))} /></div>)}
      <p>저장 상태: {data.tc.executionPlan.specification.status === 'READY' ? '근거 연결 완료' : '추가 정보 필요'}</p>
      <ul>{data.tc.executionPlan.specification.missingBindings.map((issue, index) => <li key={index}>{issue.message}</li>)}</ul>
      <button type="button" className="quiet-button" onClick={() => void save()}>조회 근거 연결 저장</button>
    </fieldset>}
  </details>
}
