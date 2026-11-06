#!/usr/bin/env node
// 업무 허브 API v1 스모크 검사. 서버 주소와 키를 환경 변수로 받아 엔드포인트를 한 번씩 부르고
// docs/hub-api-v1.openapi.yaml 의 필드가 있는지 확인한다. 조회만 하며 데이터를 바꾸지 않는다.
//   AUTOTEST_URL=https://autotest.local AUTOTEST_API_KEY=atk_... HUB_VERSION=1.0.0 node scripts/verify-hub-api.mjs
// - AUTOTEST_URL: 서버 주소(http:// 또는 https://).
// - AUTOTEST_API_KEY: 허브용 API 키. 출력·로그·오류 메시지 어디에도 나오지 않는다.
// - HUB_VERSION: X-Hub-Version 값(x.y.z), 기본 0.0.0. 서버 minHubVersion 보다 낮으면 426 으로 FAIL 한다.
// - 사내 CA 인증서는 NODE_EXTRA_CA_CERTS=<CA 파일 경로> 로 지정한다. 인증서 검증을 끄는 옵션은 없다.
// 종료 코드: 0 = FAIL 없음, 1 = FAIL 있음, 2 = 사용법 오류.
import { randomBytes, randomUUID } from 'node:crypto'
import { pathToFileURL } from 'node:url'

const TIMEOUT_MS = 15000
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const USAGE = `사용법: AUTOTEST_URL=<서버 주소> AUTOTEST_API_KEY=<키> [HUB_VERSION=<x.y.z>] node scripts/verify-hub-api.mjs
  AUTOTEST_URL      예: http://localhost:8080 또는 https://autotest.local
  AUTOTEST_API_KEY  허브용 API 키(atk_...). 출력되지 않는다.
  HUB_VERSION       X-Hub-Version 값. 기본 0.0.0
  사내 CA 인증서는 NODE_EXTRA_CA_CERTS=<CA 파일 경로> 로 지정한다. 인증서 검증을 끄는 옵션은 없다.`

const TYPES = {
  string: v => typeof v === 'string',
  uuid: v => typeof v === 'string' && UUID_RE.test(v),
  int: v => Number.isInteger(v),
  bool: v => typeof v === 'boolean',
}

// docs/hub-api-v1.openapi.yaml 의 스키마. 필드 이름과 타입만 확인하며 값은 출력하지 않는다.
const SCHEMAS = {
  Error: { code: 'string', message: 'string', traceId: 'string' },
  WhoAmI: { client: 'string', memberId: 'string' },
  Health: { status: 'string', apiVersion: 'string', minHubVersion: 'string' },
  Counts: { documents: 'int', parsedDocuments: 'int', crawls: 'int', menus: 'int', tcSets: 'int', runs: 'int' },
  Workspace: {
    id: 'uuid', name: 'string', productId: 'int', lastPage: 'string', createdAt: 'string', updatedAt: 'string',
    documentDelivery: 'string', counts: 'Counts', webPath: 'string',
  },
  RunCounts: {
    total: 'int', completed: 'int', pass: 'int', fail: 'int', na: 'int', unjudged: 'int', pending: 'int',
    error: 'int', notExecuted: 'int', needsReview: 'int', excluded: 'int',
  },
  Run: {
    runId: 'uuid', status: 'string', active: 'bool', createdAt: 'string', updatedAt: 'string', elapsedMs: 'int',
    tcSetChanged: 'bool', counts: 'RunCounts', webPath: 'string',
  },
  RunItem: { runId: 'uuid', status: 'string', createdAt: 'string' },
}

// 빠진 필드·타입이 틀린 필드의 경로 목록. 값은 넣지 않는다. 모르는 추가 필드는 무시한다.
function problems(value, schemaName, path = schemaName) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return [`${path}: 객체 아님`]
  const out = []
  for (const [field, type] of Object.entries(SCHEMAS[schemaName])) {
    const at = `${path}.${field}`
    const v = value[field]
    if (SCHEMAS[type]) out.push(...problems(v, type, at))
    else if (!TYPES[type](v)) out.push(v === undefined ? `${at}: 없음` : `${at}: ${type} 아님`)
  }
  return out
}

const ok = detail => ({ status: 'PASS', detail })
const bad = detail => ({ status: 'FAIL', detail })
const skip = detail => ({ status: 'SKIP', detail })

function redact(text, secret) {
  return secret ? String(text).split(secret).join('***') : String(text)
}

async function call(ctx, path, { auth = ctx.apiKey, version = false } = {}) {
  const headers = {}
  if (auth !== null) headers.Authorization = `Bearer ${auth}`
  if (version) headers['X-Hub-Version'] = ctx.hubVersion
  const res = await ctx.fetchImpl(ctx.baseUrl + path, {
    headers, redirect: 'manual', signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  const text = await res.text()
  let body = null
  try { body = JSON.parse(text) } catch { /* JSON 이 아니면 body 는 null */ }
  return { status: res.status, type: res.headers.get('content-type') ?? '', body }
}

const statusHint = status => (status === 426 ? ' (HUB_VERSION 이 서버 minHubVersion 보다 낮음)' : '')

export async function runChecks({ baseUrl, apiKey, hubVersion = '0.0.0', fetchImpl = fetch, log = console.log }) {
  const ctx = { baseUrl: baseUrl.replace(/\/+$/, ''), apiKey, hubVersion, fetchImpl, workspaces: null }
  const results = []
  const emit = (id, title, r) => {
    results.push({ id, title, status: r.status, detail: r.detail })
    log(redact(`[${r.status}] (${id}) ${title}${r.detail ? ` - ${r.detail}` : ''}`, apiKey))
  }
  const run = async (id, title, fn) => {
    let r
    try {
      r = await fn()
    } catch (e) {
      const code = e?.cause?.code ?? e?.code ?? e?.name ?? 'Error'
      r = bad(`요청 실패: ${code}`)
    }
    emit(id, title, r)
  }

  await run('1', '키 없이 whoami 호출 → 401 오류 형식', async () => {
    const r = await call(ctx, '/api/v1/hub/whoami', { auth: null })
    if (r.status !== 401) return bad(`HTTP ${r.status} (401 이어야 함)`)
    const p = problems(r.body, 'Error')
    return p.length ? bad(p.join(', ')) : ok(`401 ${r.body.code}`)
  })

  await run('2', '잘못된 키로 whoami 호출 → 401 API_KEY_INVALID', async () => {
    const wrongKey = 'atk_' + randomBytes(32).toString('base64url')
    const r = await call(ctx, '/api/v1/hub/whoami', { auth: wrongKey })
    if (r.status !== 401) return bad(`HTTP ${r.status} (401 이어야 함)`)
    if (r.body?.code !== 'API_KEY_INVALID') return bad(`code=${r.body?.code ?? '없음'} (API_KEY_INVALID 이어야 함)`)
    return ok('401 API_KEY_INVALID')
  })

  await run('3', '키로 whoami 호출 → 200 client·memberId', async () => {
    const r = await call(ctx, '/api/v1/hub/whoami')
    if (r.status !== 200) return bad(`HTTP ${r.status}`)
    const p = problems(r.body, 'WhoAmI')
    return p.length ? bad(p.join(', ')) : ok(`client=${r.body.client}, memberId=${r.body.memberId}`)
  })

  await run('4', 'health 호출 → 200 status·apiVersion·minHubVersion', async () => {
    const r = await call(ctx, '/api/v1/hub/health')
    if (r.status !== 200) return bad(`HTTP ${r.status}`)
    const p = problems(r.body, 'Health')
    return p.length ? bad(p.join(', ')) : ok(`status=${r.body.status}, apiVersion=${r.body.apiVersion}, minHubVersion=${r.body.minHubVersion}`)
  })

  await run('5', 'X-Hub-Version 없이 workspaces 호출 → 400 오류 형식', async () => {
    const r = await call(ctx, '/api/v1/hub/workspaces')
    if (r.status !== 400) return bad(`HTTP ${r.status} (400 이어야 함)`)
    const p = problems(r.body, 'Error')
    return p.length ? bad(p.join(', ')) : ok(`400 ${r.body.code}`)
  })

  await run('6', 'workspaces 호출 → 200 workspaces[]·hasMore 와 Workspace 필드', async () => {
    const r = await call(ctx, '/api/v1/hub/workspaces?offset=0&limit=20', { version: true })
    if (r.status !== 200) return bad(`HTTP ${r.status}${statusHint(r.status)}`)
    if (!Array.isArray(r.body?.workspaces)) return bad('workspaces 배열 없음')
    if (typeof r.body.hasMore !== 'boolean') return bad('hasMore 가 boolean 아님')
    ctx.workspaces = r.body.workspaces
    const errs = r.body.workspaces.flatMap((w, i) => problems(w, 'Workspace', `workspaces[${i}]`))
    if (errs.length) return bad(errs.slice(0, 5).join(', '))
    return ok(`${r.body.workspaces.length}건, hasMore=${r.body.hasMore}`)
  })

  await run('7', '첫 작업의 summary·runs, 첫 실행의 runs/{run}·report', async () => {
    if (!ctx.workspaces) return skip('(6) 목록 확인이 실패해 생략')
    if (ctx.workspaces.length === 0) return skip('작업이 없어 생략')
    const ws = encodeURIComponent(ctx.workspaces[0].id)
    const errs = []

    const s = await call(ctx, `/api/v1/hub/workspaces/${ws}/summary`, { version: true })
    if (s.status !== 200) errs.push(`summary HTTP ${s.status}${statusHint(s.status)}`)
    else {
      errs.push(...problems(s.body.workspace, 'Workspace', 'summary.workspace'))
      if (!('latestRun' in s.body)) errs.push('summary.latestRun: 없음')
      else if (s.body.latestRun !== null) errs.push(...problems(s.body.latestRun, 'Run', 'summary.latestRun'))
    }

    const list = await call(ctx, `/api/v1/hub/workspaces/${ws}/runs`, { version: true })
    let runs = []
    if (list.status !== 200) errs.push(`runs HTTP ${list.status}${statusHint(list.status)}`)
    else if (!Array.isArray(list.body?.runs)) errs.push('runs.runs 배열 없음')
    else {
      runs = list.body.runs
      errs.push(...runs.flatMap((x, i) => problems(x, 'RunItem', `runs[${i}]`)))
    }

    let note = `실행 ${runs.length}건`
    if (runs.length === 0) note += ' (runs/{run}·report 생략)'
    else {
      const rid = encodeURIComponent(runs[0].runId)
      const one = await call(ctx, `/api/v1/hub/workspaces/${ws}/runs/${rid}`, { version: true })
      if (one.status !== 200) errs.push(`runs/{run} HTTP ${one.status}`)
      else errs.push(...problems(one.body, 'Run', 'run'))

      const rep = await call(ctx, `/api/v1/hub/workspaces/${ws}/runs/${rid}/report`, { version: true })
      if (rep.status !== 200) errs.push(`report HTTP ${rep.status}`)
      else if (!rep.type.toLowerCase().startsWith('text/markdown')) errs.push('report Content-Type 이 text/markdown 아님')
    }

    return errs.length ? bad(errs.slice(0, 5).join(', ')) : ok(note)
  })

  await run('8', '없는 작업 UUID 로 summary 호출 → 404', async () => {
    const r = await call(ctx, `/api/v1/hub/workspaces/${randomUUID()}/summary`, { version: true })
    if (r.status !== 404) return bad(`HTTP ${r.status} (404 이어야 함)`)
    const p = problems(r.body, 'Error')
    return p.length ? bad(p.join(', ')) : ok('404')
  })

  const count = s => results.filter(r => r.status === s).length
  const summary = { passCount: count('PASS'), failCount: count('FAIL'), skipCount: count('SKIP') }
  log(`결과: PASS ${summary.passCount} · FAIL ${summary.failCount} · SKIP ${summary.skipCount}`)
  return { ...summary, results }
}

export async function main(env = process.env, { log = console.log, errorLog = console.error } = {}) {
  const baseUrl = (env.AUTOTEST_URL ?? '').trim()
  const apiKey = (env.AUTOTEST_API_KEY ?? '').trim()
  const hubVersion = (env.HUB_VERSION ?? '').trim() || '0.0.0'
  let url = null
  try { url = new URL(baseUrl) } catch { url = null }
  if (!apiKey || !url || !/^https?:$/.test(url.protocol)) {
    errorLog(USAGE)
    return 2
  }
  if (!/^\d+\.\d+\.\d+([-+].*)?$/.test(hubVersion)) {
    errorLog('HUB_VERSION 은 x.y.z 형식이어야 합니다.')
    return 2
  }
  const { failCount } = await runChecks({ baseUrl, apiKey, hubVersion, log })
  return failCount > 0 ? 1 : 0
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().then(code => { process.exitCode = code })
}
