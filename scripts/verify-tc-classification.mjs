import assert from 'node:assert/strict'
import { classifyTcFeature } from '../frontend/src/tcClassification.ts'

let checked = 0
for (const word of ['조회', '생성', '삭제', '등록', '수정']) {
  assert.equal(classifyTcFeature(`  사용자 ${word}  `), 'CRUD')
  assert.equal(classifyTcFeature(`사용자 ${word}`, undefined, '부가기능'), '부가기능')
  assert.equal(classifyTcFeature(`사용자 ${word}`, '주요기능', 'CRUD'), '주요기능')
  checked += 3
}
for (const name of ['작업 실행', '삭제 취소', '사용자조회', '등록 내역 확인', '']) {
  assert.equal(classifyTcFeature(name), '주요기능'); checked++
}
for (const name of ['로그인', ' LOGIN ', '로그인 생성']) {
  assert.equal(classifyTcFeature(name), '로그인'); checked++
}
assert.equal(classifyTcFeature('로그인 기록 조회'), 'CRUD')
assert.equal(classifyTcFeature('삭제'), 'CRUD')
assert.equal(classifyTcFeature('사용자\t수정'), 'CRUD')
console.log(`PASS: ${checked + 3} classification checks (priority, final word, login, whitespace)`)
