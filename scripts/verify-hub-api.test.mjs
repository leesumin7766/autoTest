// node --test scripts/verify-hub-api.test.mjs
// 로컬 가짜 서버(127.0.0.1, 임의 포트)만 부른다. 실제 서버·실제 키는 쓰지 않는다.
import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { createServer } from 'node:http'
import { after, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'
import { runChecks } from './verify-hub-api.mjs'

const execFileAsync = promisify(execFile)
const SCRIPT = fileURLToPath(new URL('./verify-hub-api.mjs', import.meta.url))
const FAKE_KEY = 'atk_' + 'K'.repeat(43)
const WS = '11111111-1111-4111-8111-111111111111'
const RUN = '22222222-2222-4222-8222-222222222222'

// omit: { whoami|health|workspace|run: [필드, ...] } 로 응답에서 필드를 빼 실패 경로를 만든다.
// broken: true 면 키를 확인하지 않고 항상 200 을 준다(잘못 만든 서버).
function startFakeServer({ broken = false, omit = {} } = {}) {
  const drop = (obj, fields = []) => {
    const copy = { ...obj }
    for (const f of fields) delete copy[f]
    return copy
  }
  const workspace = {
    id: WS, name: '가짜 작업', productId: 1, lastPage: 'execution',
    createdAt: '2026-11-01T09:00:00Z', updatedAt: '2026-11-02T09:00:00Z', documentDelivery: 'DELIVERED',
    counts: { documents: 1, parsedDocuments: 1, crawls: 1, menus: 1, tcSets: 1, runs: 1 },
    webPath: `/?member=3&workspace=${WS}&page=execution`,
  }
  const run = {
    runId: RUN, status: 'COMPLETED', active: false, createdAt: '2026-11-02T10:00:00Z', updatedAt: '2026-11-02T10:05:00Z',
    elapsedMs: 300000, tcSetChanged: false,
    counts: { total: 3, completed: 3, pass: 2, fail: 1, na: 0, unjudged: 0, pending: 0, error: 0, notExecuted: 0, needsReview: 0, excluded: 0 },
    webPath: `/?member=3&workspace=${WS}&page=execution`,
  }
  const send = (res, status, body, type = 'application/json') => {
    res.writeHead(status, { 'Content-Type': type })
    res.end(type === 'application/json' ? JSON.stringify(body) : body)
  }
  const error = (res, status, code, message) => send(res, status, { code, message, traceId: 'trace-fake' })

  const server = createServer((req, res) => {
    const path = new URL(req.url, 'http://fake').pathname
    if (!path.startsWith('/api/v1/hub/')) return error(res, 404, 'NOT_FOUND', '없음')
    if (!broken) {
      if (!req.headers.authorization) return error(res, 401, 'API_KEY_REQUIRED', 'API 키가 필요합니다.')
      if (req.headers.authorization !== `Bearer ${FAKE_KEY}`) return error(res, 401, 'API_KEY_INVALID', 'API 키가 올바르지 않습니다.')
    }
    if (path === '/api/v1/hub/whoami') return send(res, 200, { client: 'hub-test', memberId: '3' })
    if (path === '/api/v1/hub/health') {
      return send(res, 200, drop({ status: 'UP', apiVersion: '1', minHubVersion: '0.0.0' }, omit.health))
    }
    if (path === '/api/v1/hub/workspaces' || path.startsWith('/api/v1/hub/workspaces/')) {
      if (!req.headers['x-hub-version']) return error(res, 400, 'HUB_VERSION_REQUIRED', 'X-Hub-Version 헤더가 필요합니다.')
    }
    if (path === '/api/v1/hub/workspaces') {
      return send(res, 200, { workspaces: [drop(workspace, omit.workspace)], hasMore: false })
    }
    const base = '/api/v1/hub/workspaces/'
    if (path.startsWith(base)) {
      const parts = path.slice(base.length).split('/')
      if (parts[0] !== WS) return error(res, 404, 'RESOURCE_NOT_FOUND', '작업을 찾을 수 없습니다.')
      if (parts.length === 2 && parts[1] === 'summary') return send(res, 200, { workspace: drop(workspace, omit.workspace), latestRun: run })
      if (parts.length === 2 && parts[1] === 'runs') {
        return send(res, 200, { runs: [{ runId: RUN, status: 'COMPLETED', createdAt: '2026-11-02T10:00:00Z' }] })
      }
      if (parts.length === 3 && parts[1] === 'runs' && parts[2] === RUN) return send(res, 200, drop(run, omit.run))
      if (parts.length === 4 && parts[1] === 'runs' && parts[2] === RUN && parts[3] === 'report') {
        return send(res, 200, '# 시험 보고서\n\n- 합계 3건\n', 'text/markdown;charset=UTF-8')
      }
    }
    return error(res, 404, 'NOT_FOUND', '없음')
  })

  return new Promise(resolve => {
    // 127.0.0.1 대신 localhost 에 바인드한다. 이 환경에서 127.0.0.1 바인드가 EFAULT 로 간헐 실패했다.
    server.listen(0, 'localhost', () => {
      resolve({
        url: `http://localhost:${server.address().port}`,
        close: () => new Promise(done => { server.closeAllConnections(); server.close(done) }),
      })
    })
  })
}

async function checkWith(server, { apiKey = FAKE_KEY } = {}) {
  const lines = []
  const result = await runChecks({
    baseUrl: server.url, apiKey, hubVersion: '1.0.0', log: line => lines.push(line),
  })
  return { ...result, output: lines.join('\n') }
}

function statusOf(result, id) {
  return result.results.find(r => r.id === id)?.status
}

async function runCli(env) {
  try {
    const { stdout, stderr } = await execFileAsync(process.execPath, [SCRIPT], { env, timeout: 60000 })
    return { code: 0, out: stdout + stderr }
  } catch (e) {
    return { code: e.code, out: `${e.stdout ?? ''}${e.stderr ?? ''}` }
  }
}

const servers = []
after(async () => { await Promise.all(servers.map(s => s.close())) })
async function serve(options) {
  const s = await startFakeServer(options)
  servers.push(s)
  return s
}

describe('verify-hub-api runChecks', () => {
  test('정상 서버: 8개 검사 모두 PASS, FAIL 0', async () => {
    const server = await serve()
    const r = await checkWith(server)
    assert.equal(r.failCount, 0, r.output)
    assert.deepEqual(r.results.map(x => x.status), Array(8).fill('PASS'), r.output)
  })

  test('health 에 minHubVersion 이 없으면 (4) FAIL, 나머지는 PASS', async () => {
    const server = await serve({ omit: { health: ['minHubVersion'] } })
    const r = await checkWith(server)
    assert.equal(statusOf(r, '4'), 'FAIL', r.output)
    assert.equal(statusOf(r, '3'), 'PASS')
    assert.equal(r.failCount, 1, r.output)
  })

  test('작업 항목에서 documentDelivery 가 없으면 (6) FAIL', async () => {
    const server = await serve({ omit: { workspace: ['documentDelivery'] } })
    const r = await checkWith(server)
    assert.equal(statusOf(r, '6'), 'FAIL', r.output)
    assert.match(r.output, /documentDelivery/)
  })

  test('실행 상세에서 counts 가 없으면 (7) FAIL', async () => {
    const server = await serve({ omit: { run: ['counts'] } })
    const r = await checkWith(server)
    assert.equal(statusOf(r, '7'), 'FAIL', r.output)
  })

  test('키 확인 없이 200 을 주는 서버면 (1) FAIL', async () => {
    const server = await serve({ broken: true })
    const r = await checkWith(server)
    assert.equal(statusOf(r, '1'), 'FAIL', r.output)
    assert.equal(r.failCount > 0, true)
  })

  test('출력에 키 원문이 나오지 않는다(정상·깨진 서버 모두)', async () => {
    const good = await checkWith(await serve())
    const bad = await checkWith(await serve({ broken: true }))
    const missing = await checkWith(await serve({ omit: { health: ['status'] } }))
    for (const r of [good, bad, missing]) assert.equal(r.output.includes(FAKE_KEY), false)
  })
})

describe('verify-hub-api CLI', () => {
  test('정상 서버면 종료 코드 0, 키는 출력하지 않는다', async () => {
    const server = await serve()
    const r = await runCli({ ...process.env, AUTOTEST_URL: server.url, AUTOTEST_API_KEY: FAKE_KEY, HUB_VERSION: '1.0.0' })
    assert.equal(r.code, 0, r.out)
    assert.equal(r.out.includes(FAKE_KEY), false)
    assert.match(r.out, /결과: PASS 8 · FAIL 0/)
  })

  test('깨진 서버면 종료 코드 1', async () => {
    const server = await serve({ broken: true })
    const r = await runCli({ ...process.env, AUTOTEST_URL: server.url, AUTOTEST_API_KEY: FAKE_KEY, HUB_VERSION: '1.0.0' })
    assert.equal(r.code, 1, r.out)
    assert.equal(r.out.includes(FAKE_KEY), false)
  })

  test('환경 변수가 없으면 사용법을 내고 종료 코드 2', async () => {
    const r = await runCli({ ...process.env, AUTOTEST_URL: '', AUTOTEST_API_KEY: '' })
    assert.equal(r.code, 2, r.out)
    assert.match(r.out, /NODE_EXTRA_CA_CERTS/)
  })
})
