import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

type FeatureType = '로그인' | '주요기능' | '부가기능' | 'CRUD'
type MenuRow = { rowId: string; included: boolean; rowType?: string; functionName: string; menuPath: string[]; menuDescription: string; featureType?: FeatureType }
type Menu = { jobId: string; productName: string; status: string; revision: number; stale: boolean; generationMode: string; output?: { rows: MenuRow[]; completionStatus?: string } }
type MenuView = { job: Menu | null; history: { jobId: string; productName: string; status: string }[] }
type Tc = { id: string; tcId: string; featureId: string; featurePath: string[]; featureType: string; qualityCharacteristic: string; subCharacteristic: string; scenario: string; inputs: string; expectedResult: string; result: null; scopeDescription: string; ruleId: string; evidence: unknown[]; defectExample: { summary: string; severity: string; content: string }; executionPlan: { targetUrl: string; steps: string[]; applicability: string; requiredContext: string[] } }
type Output = { productName: string; featureCount: number; caseCount: number; templateVersion: string; testCases: Tc[]; summary: Record<string, number> }
type SetView = { set: { setId: string; stale: boolean; menuJobId: string; menuRevision: number; output: Output } | null; history: { setId: string; createdAt: string; menuRevision: number }[] }
const featureTypes: FeatureType[] = ['로그인', '주요기능', '부가기능', 'CRUD']
const statusLabels: Record<string, string> = { APPROVED: '승인 완료', DRAFT: '초안', GENERATING: '생성 중', FAILED: '실패', CANCELED: '중단' }

export function MenuTcPanel({ submissionId, memberId }: { submissionId: string; memberId: string }) {
  const [menuView, setMenuView] = useState<MenuView>({ job: null, history: [] })
  const [selectedMenu, setSelectedMenu] = useState('')
  const [types, setTypes] = useState<Record<string, FeatureType>>({})
  const [view, setView] = useState<SetView>({ set: null, history: [] })
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [search, setSearch] = useState(''), [page, setPage] = useState(0), [detail, setDetail] = useState<Tc | null>(null)
  const menuVersion = useRef('')
  const base = `/api/submissions/${submissionId}/menu-test-cases`
  const request = useCallback(async (url: string, options: RequestInit = {}) => {
    const response = await fetch(url, { ...options, headers: { 'X-Member-Id': memberId, ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    let body
    try { body = await response.json() } catch { throw new Error('TC 서비스에 연결하지 못했습니다. 최신 서버가 실행 중인지 확인해주세요.') }
    if (!response.ok) throw new Error(body.message || 'TC 요청을 처리하지 못했습니다.')
    return body
  }, [memberId])
  const load = useCallback(async (signal?: AbortSignal) => {
    const [menus, sets] = await Promise.all([
      request(`/api/submissions/${submissionId}/functional-menu${selectedMenu ? `?job=${encodeURIComponent(selectedMenu)}` : ''}`, { signal }) as Promise<MenuView>,
      request(base, { signal }) as Promise<SetView>,
    ])
    if (signal?.aborted) return
    setMenuView(menus); setView(sets)
    const version = `${menus.job?.jobId}:${menus.job?.revision}`
    if (version !== menuVersion.current) { setTypes({}); menuVersion.current = version }
    setDetail(null); setPage(0)
  }, [base, request, selectedMenu, submissionId])
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('')
    void load(controller.signal).catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [load])
  const action = async (work: () => Promise<void>) => { setBusy(true); setError(''); try { await work() } catch (failure) { setError(failure instanceof Error ? failure.message : '요청 실패') } finally { setBusy(false) } }
  const menu = menuView.job
  const features = useMemo(() => menu?.output?.rows.filter(row => row.included && row.rowType !== 'MENU_OBSERVATION') || [], [menu])
  const typeFor = (row: MenuRow): FeatureType => types[row.rowId] || row.featureType || (/로그인|로그아웃|^login$|^logout$/i.test(row.functionName) ? '로그인' : '주요기능')
  const loginCount = features.filter(row => typeFor(row) === '로그인').length
  const expectedCount = loginCount * 27 + (features.length - loginCount) * 9 + 68
  const ready = menu?.status === 'APPROVED' && !menu.stale && menu.generationMode !== 'MOCK' && menu.output?.completionStatus !== 'PARTIAL' && features.length > 0
  const output = view.set?.output
  const filtered = useMemo(() => (output?.testCases || []).filter(tc => `${tc.tcId} ${tc.featurePath.join(' ')} ${tc.scenario} ${tc.qualityCharacteristic}`.toLowerCase().includes(search.toLowerCase())), [output, search])
  const pages = Math.max(1, Math.ceil(filtered.length / 25)), activePage = Math.min(page, pages - 1)
  const saveBlob = (blob: Blob, filename: string) => { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
  return <section className="menu-tc-panel" aria-label="메뉴트리 기반 TC 생성">
    <div className="section-heading"><h2>메뉴트리 기반 TC 생성</h2><button className="quiet-button" disabled={busy || loading} onClick={() => void action(() => load())}>메뉴트리·TC 새로고침</button></div>
    <p>메뉴트리의 기능구분에 따라 시험 항목을 생성합니다. 비적용 항목도 포함하며 P/F/N/A는 시험 실행 후 판정합니다.</p>
    {error && <p className="global-notice error-copy" role="alert">{error}</p>}
    {loading && <p role="status"><span className="spinner" /> 승인 메뉴트리와 TC를 확인하고 있습니다…</p>}
    <div className="tc-field"><span>이미 만들어진 메뉴트리 업로드</span>
      <input type="file" accept=".xlsx,.json" aria-label="메뉴트리 파일 업로드" disabled={busy || loading} onChange={event => {
        const input = event.currentTarget, file = input.files?.[0]; if (!file) return
        void action(async () => {
          const form = new FormData(); form.append('file', file)
          const response = await fetch(`/api/submissions/${submissionId}/functional-menu/upload`, { method: 'POST', headers: { 'X-Member-Id': memberId }, body: form })
          let body; try { body = await response.json() } catch { throw new Error('메뉴트리 업로드 응답을 읽지 못했습니다.') }
          if (!response.ok) throw new Error(body.message || '메뉴트리를 업로드하지 못했습니다.')
          setSelectedMenu(body.job.jobId); await load()
        }).finally(() => { input.value = '' })
      }} />
      <small>메뉴트리 탭에서 내려받은 .xlsx 또는 rows가 담긴 .json을 올리면 화면 수집 없이 TC 생성에 사용합니다. 자료 등록은 필요합니다.</small></div>
    <label className="tc-field">메뉴트리 선택<select value={selectedMenu || menu?.jobId || ''} disabled={busy || loading} onChange={event => setSelectedMenu(event.target.value)}>
      {!menuView.history.length && <option value="">작성한 메뉴트리가 없습니다</option>}
      {menuView.history.map(item => <option key={item.jobId} value={item.jobId}>{item.productName} · {statusLabels[item.status] || item.status} · {item.jobId.slice(0, 8)}</option>)}
    </select></label>
    {menu && <p className="menu-job-status">{menu.productName} · {statusLabels[menu.status] || menu.status} · 버전 {menu.revision}{menu.stale ? ' · 원본 변경됨' : ''}</p>}
    {!ready && !loading && <p className="functional-menu-warning">기능이 포함된 완전한 메뉴트리를 검토·승인한 뒤 생성할 수 있습니다.</p>}
    {!!features.length && <details className="menu-generation-settings"><summary>기능구분 확인 · {features.length}개 기능</summary>
      <p>원본에 구분이 없으면 로그인 또는 주요기능으로 제안합니다. 필요하면 변경하세요. CRUD 기능은 행별 9개 시험을 적용하며 존재하지 않는 동작을 추가하지 않습니다.</p>
      <div className="tc-feature-list">{features.map(row => <label key={row.rowId}><span>{[...row.menuPath, row.functionName].join(' / ')}<small>{row.menuDescription}</small></span><select aria-label={`${row.functionName} 기능구분`} value={typeFor(row)} disabled={busy || loading} onChange={event => setTypes(current => ({ ...current, [row.rowId]: event.target.value as FeatureType }))}>{featureTypes.map(type => <option key={type}>{type}</option>)}</select></label>)}</div>
    </details>}
    <div className="tc-generate-toolbar"><span>{features.length ? `${features.length}개 기능 · 예상 ${expectedCount.toLocaleString()}개 TC` : '메뉴트리 선택 필요'}</span><button className="primary-button" disabled={!ready || busy || loading} onClick={() => void action(async () => {
      const featureTypes = Object.fromEntries(features.map(row => [row.rowId, typeFor(row)]))
      setView(await request(base, { method: 'POST', body: JSON.stringify({ menuJobId: menu!.jobId, featureTypes }) })); setSearch(''); setPage(0); setDetail(null)
    })}>{busy ? '처리 중…' : 'TC 초안 생성'}</button></div>
    {output && <>
      <div className="response-heading"><h3>{output.productName} · TC 초안</h3><span className="workspace-badge badge-success">{output.caseCount.toLocaleString()}개 생성 · 시험 미실행</span></div>
      {view.set?.stale && <p className="functional-menu-warning">원본 메뉴트리나 문서가 변경되었습니다. 이 결과는 이전 초안이며 새 승인본으로 다시 생성해야 합니다.</p>}
      <div className="tc-output-toolbar">
        <label>생성 이력<select aria-label="TC 생성 이력" value={view.set?.setId || ''} disabled={busy || loading} onChange={event => { const id = event.target.value; void action(async () => { setView(await request(`${base}?set=${encodeURIComponent(id)}`)); setPage(0); setDetail(null) }) }}>{view.history.map(item => <option key={item.setId} value={item.setId}>{new Date(item.createdAt).toLocaleString('ko-KR')} · 메뉴트리 버전 {item.menuRevision}</option>)}</select></label>
        <button className="quiet-button" disabled={busy || view.set?.stale} onClick={() => void action(async () => { const response = await fetch(`${base}/${view.set!.setId}/download`, { headers: { 'X-Member-Id': memberId } }); if (!response.ok) { const body = await response.json(); throw new Error(body.message || '다운로드 실패') } saveBlob(await response.blob(), `${output.productName}-TC-초안.xlsx`) })}>엑셀 다운로드</button>
        <button className="quiet-button" disabled={busy || view.set?.stale} onClick={() => saveBlob(new Blob([JSON.stringify(output, null, 2)], { type: 'application/json' }), `${output.productName}-TC-실행입력.json`)}>실행 입력 JSON</button>
      </div>
      <label className="tc-field">TC 검색<input type="search" value={search} placeholder="기능·시험 항목·품질특성 검색" onChange={event => { setSearch(event.target.value); setPage(0) }} /></label>
      <div className="tc-table-scroll"><table className="tc-table"><thead><tr>{['TC-ID', '기능 경로', '기능구분', '품질특성', '시험 시나리오', '기대 결과', '결과', '상세'].map(title => <th key={title}>{title}</th>)}</tr></thead><tbody>{filtered.slice(activePage * 25, (activePage + 1) * 25).map(tc => <tr key={tc.id}><td>{tc.tcId}</td><td>{tc.featurePath.join(' / ')}</td><td>{tc.featureType}</td><td>{tc.qualityCharacteristic}<br /><small>{tc.subCharacteristic}</small></td><td>{tc.scenario}</td><td>{tc.expectedResult || '실행 전 보완 필요'}</td><td>미실행</td><td><button className="quiet-button" onClick={() => setDetail(tc)}>상세 보기</button></td></tr>)}</tbody></table></div>
      <div className="tc-pagination"><button className="quiet-button" disabled={activePage === 0} onClick={() => setPage(activePage - 1)}>이전</button><span aria-live="polite">{activePage + 1} / {pages}쪽 · {filtered.length}개</span><button className="quiet-button" disabled={activePage + 1 >= pages} onClick={() => setPage(activePage + 1)}>다음</button></div>
      {detail && <section className="tc-case-detail" aria-label="TC 상세"><div className="section-heading"><h3>{detail.tcId} · 상세</h3><button className="quiet-button" onClick={() => setDetail(null)}>상세 닫기</button></div><p>{detail.featurePath.join(' → ')} · {detail.scopeDescription}</p><dl><dt>입력·사전조건</dt><dd>{detail.inputs || '보완 필요'}</dd><dt>기대 결과</dt><dd>{detail.expectedResult || '보완 필요'}</dd><dt>실행 절차</dt><dd><ol>{detail.executionPlan.steps.map((step, i) => <li key={i}>{step}</li>)}</ol></dd><dt>실행 시 필요한 정보</dt><dd>{detail.executionPlan.requiredContext.join(' · ')}</dd><dt>적용 조건</dt><dd>{detail.executionPlan.applicability}</dd><dt>결함 작성 예시 · 실제 결함 아님</dt><dd>{detail.defectExample.severity} · {detail.defectExample.summary}<pre>{detail.defectExample.content}</pre></dd></dl><details><summary>생성 규칙·원본 근거</summary><p>{detail.ruleId} · {detail.featureId}</p><pre>{JSON.stringify(detail.evidence, null, 2)}</pre></details></section>}
      <p className="response-empty">템플릿 {output.templateVersion} · 생성·저장·조회·출력까지 연결했습니다. TC 수정·최종 승인과 Playwright 자동 실행·판정은 다음 단계입니다.</p>
    </>}
  </section>
}
