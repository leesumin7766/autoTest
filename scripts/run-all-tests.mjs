#!/usr/bin/env node
// 전체 자동 테스트를 한 번에 실행한다 (2026-11-08).
//  - Java: 일회용 테스트 DB 컨테이너(127.0.0.1:55432, autotest_test)를 띄워 ./gradlew test, 끝나면 지운다
//  - crawler·ai-service·manual-review: 현재 compose 이미지로 일회용 컨테이너에서 실행(운영 컨테이너·볼륨은 건드리지 않음)
//  - 허브 API 스크립트 테스트, frontend lint·build
// 사용: node scripts/run-all-tests.mjs [java crawler ai manual hub frontend]  (생략하면 전부)
import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const DB_NAME = 'autotest-testdb-runall'
const DB_PORT = 55432
const isWin = process.platform === 'win32'

/** origin/main 에서도 실패하는 기존 문제. 새 실패와 구분해서 보여 준다 */
const KNOWN_JAVA_FAILURES = {
  'TcReadSpecificationTest.readRecipeHasStableStepsAndSpecificMissingBindingsWithoutInventedData': '기존 문제(84 기대·85 실제)',
  'MenuTcGeneratorTest.fourProductSampleSheetsProduceIndependentTraceableTcWorkbooks': '사용자 sample 통합문서가 이 PC 에 없음'
}

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { cwd: ROOT, encoding: 'utf8', shell: isWin && !cmd.includes('docker'), maxBuffer: 256 * 1024 * 1024, ...opts })
  return { code: r.status ?? 1, out: `${r.stdout ?? ''}${r.stderr ?? ''}` }
}

/** 호스트에서 TCP 로 열리는지 (Windows Docker 는 --network host 가 호스트 포트로 이어지지 않으므로 node 로 직접 확인) */
function hostPortOpen(port) {
  const r = spawnSync(process.execPath, ['-e', `require('net').connect(${port},'127.0.0.1').on('connect',()=>process.exit(0)).on('error',()=>process.exit(1));setTimeout(()=>process.exit(1),2000)`])
  return r.status === 0
}

function pause(sec) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, sec * 1000)
}

function composeImage(service) {
  const r = run('docker', ['compose', 'config', '--format', 'json'])
  if (r.code !== 0) throw new Error(`docker compose config 실패\n${r.out.slice(-500)}`)
  const img = JSON.parse(r.out.slice(r.out.indexOf('{'))).services?.[service]?.image
  if (!img) throw new Error(`${service} 이미지 이름을 찾지 못함 (프로필이 꺼져 있으면 COMPOSE_PROFILES 확인)`)
  return img
}

function imageExists(img) {
  return run('docker', ['image', 'inspect', img]).code === 0
}

/** node --test 출력의 tests/pass/fail */
function nodeCounts(out) {
  const n = (k) => Number((out.match(new RegExp(`^[#ℹ] ${k} (\\d+)`, 'm')) ?? [])[1] ?? NaN)
  return { total: n('tests'), pass: n('pass'), fail: n('fail') }
}

/** pytest 마지막 줄 */
function pytestCounts(out) {
  const line = out.trim().split('\n').reverse().find((l) => /\d+ (passed|failed)/.test(l)) ?? ''
  const g = (k) => Number((line.match(new RegExp(`(\\d+) ${k}`)) ?? [])[1] ?? 0)
  return { total: g('passed') + g('failed') + g('error'), pass: g('passed'), fail: g('failed') + g('error'), skip: g('skipped') }
}

const steps = {
  java() {
    run('docker', ['rm', '-f', DB_NAME])
    const img = composeImage('db')
    // 이 PC 의 Docker 는 포트 바인딩이 가끔 'invalid pointer address'(EFAULT)로 실패한다. 잠시 뒤 다시 시도
    let start = { code: 1, out: '' }
    for (let i = 0; i < 30; i++) {
      start = run('docker', ['run', '-d', '--name', DB_NAME, '-e', 'POSTGRES_USER=test', '-e', 'POSTGRES_PASSWORD=test', '-e', 'POSTGRES_DB=autotest_test', '-p', `127.0.0.1:${DB_PORT}:5432`, img])
      if (start.code === 0 || !/invalid pointer address|Ports are not available/.test(start.out)) break
      run('docker', ['rm', '-f', DB_NAME])
      pause(1)
    }
    if (start.code !== 0) return { ok: false, detail: `테스트 DB 시작 실패: ${start.out.slice(-300)}` }
    try {
      // postgres 이미지는 초기화용 임시 서버를 한 번 띄웠다가 다시 시작한다. 임시 서버에도 pg_isready 가 성공하므로
      // '준비 완료' 로그가 두 번 나온 뒤(초기화가 끝난 본 서버) 호스트에서 포트로 접속되는지까지 확인한다
      let ready = false
      for (let i = 0; i < 90 && !ready; i++) {
        const logs = run('docker', ['logs', DB_NAME]).out
        const finalServer = (logs.match(/ready to accept connections/g) ?? []).length >= 2
        ready = finalServer && hostPortOpen(DB_PORT)
        if (!ready) pause(1)
      }
      if (!ready) return { ok: false, detail: '테스트 DB 준비 시간 초과' }
      const gradlew = isWin ? 'gradlew.bat' : './gradlew'
      run(gradlew, ['test', '--continue'], { env: { ...process.env, AUTOTEST_TEST_DATABASE_URL: `jdbc:postgresql://localhost:${DB_PORT}/autotest_test` } })
    } finally {
      run('docker', ['rm', '-f', DB_NAME])
    }
    // 모듈별 결과 XML 집계
    let total = 0, fail = 0, skip = 0
    const failed = []
    for (const mod of readdirSync(ROOT)) {
      const dir = join(ROOT, mod, 'build', 'test-results', 'test')
      if (!existsSync(dir)) continue
      for (const f of readdirSync(dir).filter((x) => x.endsWith('.xml'))) {
        const xml = readFileSync(join(dir, f), 'utf8')
        const head = xml.match(/<testsuite [^>]*>/)?.[0] ?? ''
        const a = (k) => Number(head.match(new RegExp(`${k}="(\\d+)"`))?.[1] ?? 0)
        total += a('tests'); fail += a('failures') + a('errors'); skip += a('skipped')
        for (const m of xml.matchAll(/<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<(failure|error)/g)) {
          failed.push(`${m[2].split('.').pop()}.${m[1].replace(/\(\)$/, '')}`)
        }
      }
    }
    const unknown = failed.filter((f) => !KNOWN_JAVA_FAILURES[f])
    const known = failed.filter((f) => KNOWN_JAVA_FAILURES[f]).map((f) => `${f} — ${KNOWN_JAVA_FAILURES[f]}`)
    return {
      ok: total > 0 && unknown.length === 0,
      detail: `${total}건 중 ${total - fail - skip} 통과, ${fail} 실패(기존 ${known.length}), ${skip} 건너뜀`,
      lines: [...unknown.map((f) => `새 실패: ${f}`), ...known.map((f) => `기존: ${f}`)]
    }
  },

  crawler() {
    const img = composeImage('crawler-service')
    if (!imageExists(img)) return { ok: false, detail: `이미지 없음: ${img} (docker compose build crawler-service)` }
    const r = run('docker', ['run', '--rm', '--network', 'none', '--entrypoint', 'node', img, '--test'])
    const c = nodeCounts(r.out)
    return { ok: r.code === 0 && c.fail === 0, detail: `${c.total}건 중 ${c.pass} 통과, ${c.fail} 실패`, lines: r.code === 0 ? [] : [r.out.slice(-800)] }
  },

  ai() {
    return pytestIn('ai-service', 'python -m pytest -q -p no:cacheprovider')
  },

  manual() {
    return pytestIn('manual-review-service', 'python -m pytest -q -p no:cacheprovider test_api.py')
  },

  hub() {
    const r = run(process.execPath, ['--test', 'scripts/verify-hub-api.test.mjs'], { shell: false })
    const c = nodeCounts(r.out)
    return { ok: r.code === 0 && c.fail === 0, detail: `${c.total}건 중 ${c.pass} 통과, ${c.fail} 실패` }
  },

  frontend() {
    const cwd = join(ROOT, 'frontend')
    const lint = run('npm', ['run', 'lint'], { cwd })
    const build = run('npm', ['run', 'build'], { cwd })
    const warnings = (lint.out.match(/^\S+:\d+:\d+: warning /gm) ?? []).length
    return { ok: lint.code === 0 && build.code === 0, detail: `lint ${lint.code === 0 ? '통과' : '실패'}(경고 ${warnings}), build ${build.code === 0 ? '성공' : '실패'}`, lines: build.code === 0 ? [] : [build.out.slice(-800)] }
  }
}

/** pytest 는 운영 이미지에 없으므로 일회용 컨테이너에서 설치 후 실행(인터넷 필요). 운영 컨테이너는 그대로 */
function pytestIn(service, cmd) {
  const img = composeImage(service)
  if (!imageExists(img)) return { ok: false, detail: `이미지 없음: ${img} (docker compose build ${service})` }
  const r = run('docker', ['run', '--rm', '--user', '0', '--entrypoint', 'sh', '-e', 'LLM_MODE=mock', img, '-c', `pip install -q --disable-pip-version-check pytest >/dev/null 2>&1; ${cmd}`])
  const c = pytestCounts(r.out)
  return { ok: r.code === 0 && c.total > 0, detail: `${c.total}건 중 ${c.pass} 통과, ${c.fail} 실패, ${c.skip} 건너뜀`, lines: r.code === 0 ? [] : [r.out.slice(-800)] }
}

const want = process.argv.slice(2)
const names = want.length ? want : Object.keys(steps)
const results = []
for (const name of names) {
  if (!steps[name]) { console.error(`모르는 항목: ${name} (가능: ${Object.keys(steps).join(' ')})`); process.exit(2) }
  const t0 = Date.now()
  process.stdout.write(`▶ ${name} … `)
  let r
  try { r = steps[name]() } catch (e) { r = { ok: false, detail: String(e.message ?? e) } }
  const sec = Math.round((Date.now() - t0) / 1000)
  console.log(`${r.ok ? '통과' : '실패'} — ${r.detail} (${sec}초)`)
  for (const l of r.lines ?? []) console.log(`    ${l}`)
  results.push(r.ok)
}
const ok = results.every(Boolean)
console.log(ok ? '\n전체 통과' : '\n실패 항목이 있습니다')
process.exit(ok ? 0 : 1)
