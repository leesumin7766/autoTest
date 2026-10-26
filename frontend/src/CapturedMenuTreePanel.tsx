import { apiFetch as fetch } from './apiFetch'
import { useState } from 'react'

type Node = { nodeId: string; title: string; kind: 'GROUP' | 'LINK' | 'TAB' | 'BUTTON'; url: string
  status: string; pageIndex?: number; children: Node[]
  screens?: { pageIndex: number; screenKey: string; url: string; status: string }[]
  sources: { pageIndex: number; frameUrl: string }[] }
type Tree = { nodes: Node[]; nodeCount: number; pagesWithoutNavigation: number; truncated: boolean; crawlStatus: string }

export function CapturedMenuTreePanel({ base, jobId, memberId, busy, showPage }: {
  base: string; jobId: string; memberId: string; busy: boolean
  showPage: (page: { pageIndex: number; title: string; url: string }) => Promise<void>
}) {
  const [tree, setTree] = useState<Tree | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const load = async () => {
    setLoading(true); setError('')
    try {
      const response = await fetch(`${base}/${jobId}/menu-tree`, { headers: { 'X-Member-Id': memberId } })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message || '메뉴트리를 불러오지 못했습니다.')
      setTree(body)
    } catch (failure) { setError(failure instanceof Error ? failure.message : '메뉴트리 조회 실패') }
    finally { setLoading(false) }
  }
  const render = (nodes: Node[]) => <ul className="captured-menu-tree">{nodes.map(node => <li key={node.nodeId}>
    <div><strong>{node.title}</strong> <span className={`menu-observation-${node.status.toLowerCase()}`}>
      {({ CAPTURED: '클릭 후 화면 확인', GROUP: '메뉴 그룹', UNVISITED: '메뉴만 발견됨', UNVERIFIED: '이전 수집 · 이동 근거 없음',
        LOAD_TIMEOUT: '로딩 미완료', NO_STATE_CHANGE: '클릭 후 변화 미확인' } as Record<string,string>)[node.status] || node.status}</span>
      {node.kind === 'TAB' && <small> · 탭</small>}
      {node.pageIndex !== undefined && <button type="button" className="text-button" disabled={busy}
        onClick={() => void showPage({ pageIndex: node.pageIndex!, title: node.title, url: node.url })}>연결 화면 보기</button>}
      {node.screens?.filter(screen => screen.status !== 'CAPTURED' || screen.pageIndex !== node.pageIndex).map(screen => <button
        key={screen.pageIndex} type="button" className="text-button" disabled={busy}
        onClick={() => void showPage({ pageIndex: screen.pageIndex, title: node.title, url: screen.url })}>
        화면 {screen.pageIndex + 1} · {screen.status === 'CAPTURED' ? '확인됨' : '진단 캡처'}</button>)}
      {node.url && <small className="captured-menu-url">{node.url}</small>}
      {node.sources.length > 0 && <small>발견 근거: {node.sources.map(source => `화면 ${source.pageIndex + 1}`).join(', ')}</small>}
    </div>
    {node.children.length > 0 && <details open><summary>하위 메뉴 {node.children.length}개</summary>{render(node.children)}</details>}
  </li>)}</ul>
  return <details className="captured-menu-panel" aria-label="수집 기반 메뉴트리" onToggle={event => { if (event.currentTarget.open && !tree && !loading) void load() }}>
    <summary>수집 기반 메뉴트리 초안{tree && ` · ${tree.nodeCount}개 항목`}</summary>
    <p>메뉴를 실제로 클릭한 결과와 연결합니다. 같은 URL이라도 탭·iframe·화면 상태가 다르면 별도로 보존합니다. 로딩 미완료·변화 미확인은 정상 화면과 구분합니다.</p>
    <button type="button" className="quiet-button" disabled={loading} onClick={() => void load()}>
      {loading ? '불러오는 중…' : tree ? '메뉴트리 새로고침' : '메뉴트리 보기'}</button>
    {error && <p className="error-copy" role="alert">{error}</p>}
    {tree && <>
      <p role="status">{tree.nodeCount}개 항목 · 검토 필요{['QUEUED', 'RUNNING'].includes(tree.crawlStatus) ? ' · 수집 중인 시점의 결과입니다. 완료 후 새로고침해 주세요.' : ''}</p>
      {tree.truncated && <p>표시 한도 2,000개에 도달했습니다. 저장된 원본 메뉴 근거는 화면별로 유지됩니다.</p>}
      {tree.pagesWithoutNavigation > 0 && <p>{tree.pagesWithoutNavigation}개 화면에는 메뉴 계층 근거가 없습니다. 이전 실행기로 수집한 결과는 최신 실행기로 다시 수집해야 합니다.</p>}
      {tree.nodes.length ? render(tree.nodes) : <p>저장된 메뉴 계층이 없습니다. 메뉴가 있는 화면을 최신 실행기로 수집해 주세요.</p>}
    </>}
  </details>
}
