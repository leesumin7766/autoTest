export type FeatureType = '로그인' | '주요기능' | '부가기능' | 'CRUD'

// Keep aligned with MenuTcGenerator.classify. Only the fallback is inferred.
export function classifyTcFeature(name: string, selected?: FeatureType, existing?: FeatureType): FeatureType {
  if (selected) return selected
  if (existing) return existing
  const normalized = name.trim()
  if (/로그인|^login$/i.test(normalized) && !/기록|이력|조회|history|audit/i.test(normalized)) return '로그인'
  return /(?:^|\s)(조회|생성|삭제|등록|수정)$/u.test(normalized) ? 'CRUD' : '주요기능'
}
