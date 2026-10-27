import { useRef, useEffect, useState } from 'react'
export type SkippedMenu = { menuKey?: string; path: string[]; stage: string; reason: string }
const prefix = (a: string[], b: string[]) => a.length <= b.length && a.every((part, i) => part === b[i])
const reasons: Record<string, string> = { SCREEN_CONTEXT_CHANGED: '화면 전환 중 상태 확인 실패', RETRY_PENDING: '재수집 미완료', CRAWL_LANGUAGE_CHANGED: '화면 언어 변경', CAPTURE_CONTEXT_CHANGED: '캡처 중 화면 전환', CAPTURE_TIMEOUT: '캡처 시간 초과', BROWSER_ERROR_PAGE: '브라우저 오류 화면', CONNECTION_SCOPE_BLOCKED: '허용 범위 밖 주소', SOURCE_LOAD_TIMEOUT: '이동 전 로딩 시간 초과', NAVIGATION_NOT_AVAILABLE: '메뉴를 찾지 못함', CLICK_TIMEOUT: '메뉴 클릭 시간 초과', NAVIGATION_FAILED: '화면 이동 실패', ERR_BLOCKED_BY_CLIENT: '네트워크 정책 차단', ERR_ABORTED: '화면 이동 중단', ERR_CONNECTION_RESET: '서버 연결 끊김', ERR_CONNECTION_REFUSED: '서버 연결 거부', ERR_TIMED_OUT: '서버 응답 시간 초과' }
function Choice({ mixed, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { mixed: boolean }) {
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => { if (ref.current) ref.current.indeterminate = mixed }, [mixed])
  return <input {...props} type="checkbox" ref={ref} />
}
export function SkippedMenuRetry({ items, disabled, onRetry }: { items: SkippedMenu[]; disabled: boolean; onRetry: (keys: string[], scope: 'SELF' | 'SUBTREE') => Promise<void> }) {
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [scope, setScope] = useState<'SELF' | 'SUBTREE'>('SUBTREE')
  const eligible = items.filter(item => item.menuKey && item.path.length).filter((item,i,all)=>all.findIndex(other=>other.menuKey===item.menuKey)===i)
  const keys = eligible.map(item => item.menuKey!)
  const chosen = keys.filter(key => selected.has(key))
  function toggle(item: SkippedMenu, checked: boolean) {
    setSelected(previous => {
      const next = new Set(previous)
      for (const child of eligible) if (prefix(item.path, child.path)) { if (checked) next.add(child.menuKey!); else next.delete(child.menuKey!) }
      return next
    })
  }
  return <details className="skipped-menu-retry">
    <summary>수집하지 못한 메뉴·사유 <span>{items.length}건</span></summary>
    <div className="retry-toolbar">
      <label><Choice aria-label="미완료 메뉴 전체 선택" disabled={disabled || !keys.length} checked={keys.length > 0 && chosen.length === keys.length} mixed={chosen.length > 0 && chosen.length < keys.length} onChange={e => setSelected(e.target.checked ? new Set(keys) : new Set())} />전체 선택</label>
      <label className="retry-scope">범위 <select aria-label="재수집 범위" value={scope} disabled={disabled} onChange={e => setScope(e.target.value as 'SELF' | 'SUBTREE')}><option value="SELF">선택 화면만</option><option value="SUBTREE">선택 화면 + 하위 메뉴</option></select></label>
      <button type="button" className="primary-button" disabled={disabled || !chosen.length} onClick={() => void onRetry(chosen, scope)}>선택 {chosen.length}개 재수집</button>
    </div>
    <p className="retry-hint">상위 항목 선택 시 목록의 하위 항목도 함께 선택됩니다. 기존 화면은 보존합니다.</p>
    <ul className="retry-list">{items.map((item,index) => {
      const children = eligible.filter(child => prefix(item.path, child.path))
      const checkedCount = children.filter(child => selected.has(child.menuKey!)).length
      return <li key={`${item.menuKey}:${index}`}>
        <Choice aria-label={`${item.path.join(' → ') || '시작 화면'} 재수집 선택`} disabled={disabled || !item.menuKey || !item.path.length} checked={!!item.menuKey && selected.has(item.menuKey)} mixed={checkedCount > 0 && checkedCount < children.length} onChange={e => toggle(item, e.target.checked)} />
        <details><summary title={item.path.join(' → ')}><span>{item.path.join(' → ') || '시작 화면'}</span><small>{reasons[item.reason] || item.reason}</small></summary><p>{item.reason} · 단계: {item.stage}{!item.menuKey || !item.path.length ? ' · 경로 정보 없음: 전체 이어서 수집을 이용하세요.' : ''}</p></details>
      </li>
    })}</ul>
  </details>
}
