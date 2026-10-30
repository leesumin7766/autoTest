import { useEffect, useRef, useState } from 'react'

export function duration(ms: number) {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  return `${Math.floor(seconds / 3600).toString().padStart(2, '0')}:${Math.floor(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}

export function RunProgress({ title, active, elapsedMs, receivedAt, taskKey, startedAt, endedAt, tone, completed, total, current, metrics, note, progressText, progressAriaLabel }: {
  title: string; active: boolean; elapsedMs?: number; receivedAt: number; taskKey: string; startedAt?: string | number; endedAt?: string; tone?: 'active' | 'success' | 'danger' | 'neutral'; completed: number; total?: number;
  current: string; metrics: { label: string; value: number; tone?: string }[]; note?: string
  progressText?: string; progressAriaLabel?: string
}) {
  const [, tick] = useState(0)
  const clock = useRef<{ key: string; at: number; value: number; active: boolean } | null>(null)
  const now = Date.now(), start = typeof startedAt === 'number' ? startedAt : Date.parse(startedAt || '')
  const end = active ? now : Date.parse(endedAt || '')
  const fromStart = Number.isFinite(start) && Number.isFinite(end) ? Math.max(0, end-start) : 0
  const sample = elapsedMs === undefined ? 0 : Math.max(0,elapsedMs)+(active ? Math.max(0,now-receivedAt) : 0)
  if (!clock.current || clock.current.key !== taskKey) clock.current = { key: taskKey, at: now, value: Math.max(sample,fromStart), active }
  else {
    const previous = clock.current.value+(clock.current.active ? Math.max(0,now-clock.current.at) : 0)
    clock.current = { key: taskKey, at: now, value: Math.max(previous,sample,fromStart), active }
  }
  useEffect(() => { if (!active) return; const timer = window.setInterval(() => tick(value => value+1),1000); return () => window.clearInterval(timer) }, [active,taskKey])
  const elapsed = clock.current.value
  const percentage = total ? Math.min(100, Math.round(completed / total * 100)) : null
  return <div className={`run-progress is-${tone || (active ? 'active' : 'neutral')} ${active ? 'is-running' : ''}`} aria-label="작업 진행 현황">
    <div className="run-progress-heading"><div><span className="run-status-dot" /><strong>{title}</strong></div><div className="run-clock"><span>소요 시간</span><b>{duration(elapsed)}</b></div></div>
    <div className="run-progress-current" role="status">{active && <span className="spinner" />}<span>{current}</span></div>
    <div className="run-progress-track"><span className="run-progress-bar">{total ? <progress aria-label={progressAriaLabel || '전체 항목 처리율'} value={completed} max={total} /> : <progress aria-label={active ? '화면 탐색 진행 중 · 전체 수 미확정' : '화면 탐색 종료'} {...(active ? {} : { value: 0, max: 1 })} />}<span className="run-progress-shimmer" aria-hidden="true" /></span><span>{progressText || (percentage === null ? `${completed.toLocaleString()}개 캡처 저장 · ${active ? '전체 수 탐색 중' : '탐색 종료'}` : `${completed.toLocaleString()} / ${total!.toLocaleString()}개 처리 · ${percentage}%`)}</span></div>
    <div className="run-progress-metrics">{metrics.map(metric => <div key={metric.label} className={metric.tone || ''}><b>{metric.value.toLocaleString()}</b><span>{metric.label}</span></div>)}</div>
    {note && <p className="run-progress-note">{note}</p>}
  </div>
}
