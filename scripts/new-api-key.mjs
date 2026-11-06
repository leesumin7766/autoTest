#!/usr/bin/env node
// 외부 프로그램(업무 허브 등)용 API 키를 발급한다. 서버에는 해시만 저장한다.
//   node scripts/new-api-key.mjs <이름> <회원ID>
//   예: node scripts/new-api-key.mjs hub-kim 3
// 출력된 키 원문은 허브 설정 화면에 한 번 입력하고 어디에도 저장하지 않는다.
// 출력된 항목은 .env 의 AUTOTEST_API_KEYS 에 쉼표로 이어 붙이고 api-gateway 를 다시 만든다.
import { createHash, randomBytes } from 'node:crypto'

const [name, memberId] = process.argv.slice(2)
if (!/^[a-z0-9][a-z0-9-]{0,39}$/.test(name ?? '') || !/^[1-9][0-9]{0,17}$/.test(memberId ?? '')) {
  console.error('사용법: node scripts/new-api-key.mjs <이름: 영문 소문자·숫자·-, 40자 이내> <회원ID: 양의 정수>')
  process.exit(2)
}
const key = 'atk_' + randomBytes(32).toString('base64url')
const hash = createHash('sha256').update(key, 'ascii').digest('hex')
console.log(`API 키(이번 한 번만 표시): ${key}`)
console.log(`AUTOTEST_API_KEYS 항목:     ${name}:${memberId}:${hash}`)
