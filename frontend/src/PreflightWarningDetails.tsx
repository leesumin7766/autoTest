import type { ReactNode } from 'react'

export type PreflightDiagnostic = {
  role: string
  suspiciousCharacterCount?: number
  suspiciousCharacterRatio?: number
  extractedCharacterCount?: number
  jevInputTruncated?: boolean
  jevSelectedCharacterCount?: number
  jevOmittedCharacterCount?: number
}

export type PreflightWarning = {
  code: string
  role: string | null
  severity: string
  message: string
  evidence?: Record<string, unknown>
}

export type PreflightDiagnostics = {
  jevEvaluated?: boolean
  jevDecisions?: Record<string, { choice: string; confidence: number }>
  documents?: PreflightDiagnostic[]
}

const roleLabel: Record<string, string> = {
  AGREEMENT: '시험합의서', FUNCTION_LIST: '기능리스트', MANUAL: '제품 매뉴얼',
}

const choiceLabel: Record<string, string> = {
  readable: '읽기 쉬움', partially_readable: '일부 손상 또는 판정 불확실', unusable: '사용하기 어려움',
  same: '같은 제품', different: '다른 제품', uncertain: '판정 불확실',
}

function diagnosticFor(role: string | null, diagnostics?: PreflightDiagnostics) {
  return diagnostics?.documents?.find((item) => item.role === role)
}

function detailsFor(warning: PreflightWarning, diagnostics?: PreflightDiagnostics): ReactNode[] {
  const evidence = warning.evidence
  if (evidence?.type === 'readability') {
    const choice = String(evidence.choice ?? 'unknown')
    const confidence = Number(evidence.confidence)
    const count = Number(evidence.suspiciousCharacterCount ?? diagnosticFor(warning.role, diagnostics)?.suspiciousCharacterCount ?? 0)
    const total = Number(evidence.extractedCharacterCount ?? diagnosticFor(warning.role, diagnostics)?.extractedCharacterCount ?? 0)
    const ratio = total > 0 ? `${(count / total * 100).toFixed(1)}%` : null
    return [
      <>Jev 가독성 판정: <strong>{choiceLabel[choice] ?? choice}</strong>{Number.isFinite(confidence) ? ` (신뢰도 ${(confidence * 100).toFixed(0)}%)` : ''}</>,
      <>대체·제어 문자 의심: {count.toLocaleString()}개{total > 0 ? ` / 추출 문자 ${total.toLocaleString()}개 (${ratio})` : ''}. 이 수치는 참고 진단이며, 이 경고는 Jev가 ‘읽기 쉬움’으로 충분히 판정하지 못해 발생했습니다.</>,
      <>Jev 응답에는 판정 이유나 원문 인용이 포함되지 않아 어떤 문구가 영향을 줬는지는 확인할 수 없습니다.</>,
    ]
  }
  if (evidence?.type === 'product_identity') {
    const left = String(evidence.leftRole ?? '')
    const right = String(evidence.rightRole ?? '')
    const choice = String(evidence.choice ?? 'unknown')
    const confidence = Number(evidence.confidence)
    return [
      <>{roleLabel[left] ?? left}와 {roleLabel[right] ?? right} 비교에서 Jev 판정: <strong>{choiceLabel[choice] ?? choice}</strong>{Number.isFinite(confidence) ? ` (신뢰도 ${(confidence * 100).toFixed(0)}%)` : ''}.</>,
      <>이 경고는 두 문서가 다르다고 확정한 뜻이 아니라, 같은 제품이라고 확인할 근거가 설정된 신뢰도 기준(80%)에 도달하지 못했다는 뜻입니다.</>,
      <>Jev가 비교에 사용한 제품명이나 근거 문장은 저장되지 않아 구체적인 불일치 지점을 제시할 수 없습니다.</>,
    ]
  }
  if (evidence?.type === 'role_match') {
    const choice = String(evidence.choice ?? 'unknown')
    const confidence = Number(evidence.confidence)
    const threshold = Number(evidence.blockConfidenceThreshold)
    return [
      <>Jev 역할 판정: <strong>{choiceLabel[choice] ?? choice}</strong>{Number.isFinite(confidence) ? ` (신뢰도 ${(confidence * 100).toFixed(0)}%)` : ''}.</>,
      Number.isFinite(threshold) ? <>단일 문서 차단 기준: 불일치 신뢰도 {(threshold * 100).toFixed(0)}% 이상.</> : null,
      <>Jev는 선택과 신뢰도만 반환합니다. 비교한 원문 문구는 저장되지 않았습니다.</>,
    ]
  }
  if (evidence?.type === 'multiple_role_mismatches') {
    const roles = Array.isArray(evidence.roles) ? evidence.roles as { role: string; confidence: number }[] : []
    return [
      <>역할 불일치 판정 {Number(evidence.count)}건(기준: {Number(evidence.minimumCount)}건 이상), 각 신뢰도 {(Number(evidence.minimumConfidence) * 100).toFixed(0)}% 이상.</>,
      ...roles.map((item) => <span key={item.role}>{roleLabel[item.role] ?? item.role}: 불일치 (신뢰도 {(item.confidence * 100).toFixed(0)}%)</span>),
      <>여러 문서에서 같은 유형의 역할 불일치가 겹쳐 차단했습니다. Jev가 비교한 구체 문구는 저장되지 않았습니다.</>,
    ]
  }
  if (evidence?.type === 'truncation' || warning.code === 'JEV_INPUT_TRUNCATED') {
    const diagnostic = diagnosticFor(warning.role, diagnostics)
    const total = Number(evidence?.extractedCharacterCount ?? diagnostic?.extractedCharacterCount)
    const selected = Number(evidence?.jevSelectedCharacterCount ?? diagnostic?.jevSelectedCharacterCount ?? 12_000)
    const omitted = Number(evidence?.jevOmittedCharacterCount ?? diagnostic?.jevOmittedCharacterCount)
    return [
      <>{Number.isFinite(total) && total > 0 ? `추출 텍스트 ${total.toLocaleString()}자 중 ` : ''}Jev에는 최대 {selected.toLocaleString()}자만 전달하고 앞·뒤 부분을 판정에 사용했습니다.</>,
      Number.isFinite(omitted) && omitted >= 0
        ? <>중간 생략량: 약 {omitted.toLocaleString()}자.</>
        : <>이전 점검 기록에는 전체 길이와 생략량이 저장되지 않아 정확한 수치는 복원할 수 없습니다.</>,
      <>이 제한은 Jev 사전 점검 입력에만 적용됩니다. 제품 설명 생성에서는 추출 텍스트 전체를 청크로 나누어 처리합니다.</>,
    ]
  }
  if (warning.code === 'DOCUMENT_PARTIALLY_READABLE') {
    const diagnostic = diagnosticFor(warning.role, diagnostics)
    if (diagnostic) {
      const count = diagnostic.suspiciousCharacterCount ?? 0
      const total = diagnostic.extractedCharacterCount
      const ratio = diagnostic.suspiciousCharacterRatio
      return [
        <>저장된 진단값: 대체·제어 문자 의심 {count.toLocaleString()}개{total ? ` / 추출 문자 ${total.toLocaleString()}개` : ''}{ratio !== undefined ? ` (${(ratio * 100).toFixed(1)}%)` : ''}.</>,
        <>이전 점검 결과에는 Jev의 세부 선택/신뢰도가 없어 이 가독성 경고의 Jev 근거는 복원할 수 없습니다.</>,
      ]
    }
  }
  if (warning.code === 'PRODUCT_IDENTITY_UNCERTAIN') {
    const pair = warning.message.match(/(AGREEMENT|FUNCTION_LIST|MANUAL) 문서와 (AGREEMENT|FUNCTION_LIST|MANUAL) 문서/)
    const left = pair?.[1] ?? ''
    const right = pair?.[2] ?? ''
    const decision = left && right ? diagnostics?.jevDecisions?.[`same_product_${left}_${right}`] : undefined
    if (decision) return [
      <>Jev 판정: <strong>{choiceLabel[decision.choice] ?? decision.choice}</strong> (신뢰도 {(decision.confidence * 100).toFixed(0)}%).</>,
      <>구체적인 비교 문장이나 원문 인용은 이 점검 기록에 저장되지 않았습니다.</>,
    ]
    return ['이전 점검 결과에는 Jev의 제품 일치 선택과 신뢰도가 저장되지 않아 세부 판단을 복원할 수 없습니다.',
      '이 경고는 불일치 확정이 아니라, 같은 제품이라고 확인할 근거가 부족했다는 뜻입니다.']
  }
  if (warning.code === 'JEV_INPUT_TRUNCATED') {
    const diagnostic = diagnosticFor(warning.role, diagnostics)
    if (diagnostic?.jevInputTruncated) return [
      '저장된 진단에서 Jev 입력 생략이 확인됩니다.',
      '이전 점검 결과에는 전체 글자 수와 생략량이 저장되지 않았습니다.',
    ]
  }
  return ['이 경고의 추가 근거값은 현재 점검 결과에 저장되어 있지 않습니다.']
}

export function PreflightWarningDetails({ warnings, diagnostics, className = '' }: {
  warnings: PreflightWarning[]
  diagnostics?: PreflightDiagnostics
  className?: string
}) {
  return (
    <ul className={`preflight-warning-list ${className}`}>
      {warnings.map((warning, index) => (
        <li key={`${warning.code}-${warning.role ?? 'set'}-${index}`}>
          <div className="preflight-warning-heading">
            <span>{warning.role ? `${roleLabel[warning.role] ?? warning.role}: ` : ''}{warning.message}</span>
            <details className="preflight-evidence">
              <summary>근거 보기</summary>
              <ul>{detailsFor(warning, diagnostics).map((detail, detailIndex) => <li key={detailIndex}>{detail}</li>)}</ul>
            </details>
          </div>
        </li>
      ))}
    </ul>
  )
}
