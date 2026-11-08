#!/usr/bin/env node
// 전체 자동 테스트를 한 번에 실행한다 (2026-11-08).
//  - Java: 전용 Docker 네트워크에 일회용 테스트 DB(autotest_test)와 JDK 컨테이너를 띄워 gradle test (호스트 포트 사용 안 함), 끝나면 지운다
//  - crawler·ai-service·manual-review: 현재 compose 이미지로 일회용 컨테이너에서 실행(운영 컨테이너·볼륨은 건드리지 않음)
//  - 허브 API 스크립트 테스트, frontend lint·build
// 사용: node scripts/run-all-tests.mjs [java crawler ai manual hub frontend]  (생략하면 전부)
import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const DB_NAME = 'autotest-testdb-runall'
const TEST_NET = 'autotest-test-net'
const JDK_IMAGE = 'eclipse-temurin:21-jdk'
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
    // 테스트 DB 와 Gradle 을 모두 전용 Docker 네트워크 안에서 실행한다. 호스트 포트를 쓰지 않으므로
    // 이 PC 의 포트 바인딩 오류(EFAULT, 재시작 뒤 접속 거부)와 무관하다. 저장소는 컨테이너 안으로 복사해 빌드하고
    // 결과 XML 만 각 모듈의 build/test-results/test 로 되돌려 놓는다(Windows 빌드 산출물과 섞지 않음)
    const img = composeImage('db')
    run('docker', ['rm', '-f', DB_NAME])
    run('docker', ['network', 'rm', TEST_NET])
    const net = run('docker', ['network', 'create', '--internal', TEST_NET])
    if (net.code !== 0) return { ok: false, detail: `테스트 네트워크 생성 실패: ${net.out.slice(-200)}` }
    try {
      const db = run('docker', ['run', '-d', '--name', DB_NAME, '--network', TEST_NET, '-e', 'POSTGRES_USER=test', '-e', 'POSTGRES_PASSWORD=test', '-e', 'POSTGRES_DB=autotest_test', img, 'postgres', '-c', 'max_connections=400'])
      // 테스트마다 Spring 컨텍스트(연결 풀)가 따로 떠서 기본 100 연결을 넘는다
      if (db.code !== 0) return { ok: false, detail: `테스트 DB 시작 실패: ${db.out.slice(-300)}` }
      // postgres 이미지는 초기화용 임시 서버를 한 번 띄웠다가 다시 시작한다. '준비 완료' 로그가 두 번 나와야 본 서버다
      let ready = false
      for (let i = 0; i < 90 && !ready; i++) {
        ready = (run('docker', ['logs', DB_NAME]).out.match(/ready to accept connections/g) ?? []).length >= 2
        if (!ready) pause(1)
      }
      if (!ready) return { ok: false, detail: '테스트 DB 준비 시간 초과' }
      for (const mod of readdirSync(ROOT)) { const dir = join(ROOT, mod, 'build', 'test-results', 'test'); if (existsSync(dir)) rmSync(dir, { recursive: true, force: true }) }
      const script = [
        'set -e',
        "tar -C /src --exclude=./.git --exclude='*/node_modules' --exclude='*/build' --exclude=./.gradle --exclude=./frontend/dist -cf - . | tar -C /work -xf -",
        'cd /work',
        'set +e',
        'java -Xmx384m -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --continue --no-daemon -q --console=plain > /work/gradle.log 2>&1',
        'echo "gradle exit $?"',
        'tail -5 /work/gradle.log',
        'for d in /work/*/build/test-results/test; do m=$(basename $(dirname $(dirname $(dirname $d)))); mkdir -p /src/$m/build/test-results/test && cp -r $d/. /src/$m/build/test-results/test/; done',
      ].join('\n')
      // Gradle 의존성은 볼륨에 보관해 두 번째부터 내려받지 않는다. 처음 한 번은 인터넷이 필요하다(내부 네트워크와 별도로 bridge 연결)
      const G = `${DB_NAME}-gradle`
      run('docker', ['rm', '-f', G])
      const created = run('docker', ['create', '--name', G, '--network', 'bridge', '-v', `${ROOT.replace(/\/$/, '')}:/src`, '-v', 'autotest-test-gradle-cache:/root/.gradle',
        '--tmpfs', '/work:exec,size=3g', '-e', 'GRADLE_OPTS=-Xmx384m', '-e', `AUTOTEST_TEST_DATABASE_URL=jdbc:postgresql://${DB_NAME}:5432/autotest_test`,
        '--entrypoint', 'sh', JDK_IMAGE, '-c', script])
      if (created.code !== 0) return { ok: false, detail: `Gradle 컨테이너 생성 실패(${JDK_IMAGE}): ${created.out.slice(-300)}` }
      try {
        run('docker', ['network', 'connect', TEST_NET, G])
        const gradle = run('docker', ['start', '-a', G])
        if (!/gradle exit \d+/.test(gradle.out)) return { ok: false, detail: `Gradle 실행 실패: ${gradle.out.slice(-500)}` }
      } finally { run('docker', ['rm', '-f', G]) }
    } finally {
      run('docker', ['rm', '-f', DB_NAME])
      run('docker', ['network', 'rm', TEST_NET])
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
