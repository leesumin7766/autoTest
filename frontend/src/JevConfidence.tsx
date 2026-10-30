type ScoreSource = {
  judgedBy?: string
  confidence?: number
  judgmentDetails?: Record<string, { choice?: string; confidence?: number }>
}

/** Display the saved Jev response; a code verdict's confidence is not a Jev score. */
export function JevConfidence({ item, conditionIndex }: { item: ScoreSource; conditionIndex?: number }) {
  const key=Object.keys(item.judgmentDetails || {}).find(key=>/^case_\d+$/.test(key))
  const answer=key ? item.judgmentDetails?.[conditionIndex===undefined ? key : `condition_${key.slice(5)}_${conditionIndex}`] : undefined
  const score=answer?.confidence ?? (conditionIndex===undefined && !item.judgedBy?.startsWith('CODE_') ? item.confidence : undefined)
  const valid=typeof score==='number' && Number.isFinite(score) && score>=0 && score<=1
  return <span className="jev-confidence" title={valid?'저장된 Jev 응답 신뢰도':'Jev 판정 점수 없음'}>
    {valid ? `${conditionIndex!==undefined && answer?.choice ? `${answer.choice} · ` : ''}${score.toFixed(2)}` : '—'}
  </span>
}
