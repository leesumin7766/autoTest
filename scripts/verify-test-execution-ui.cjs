// Isolated fixtures: no real browser target or LLM is called.
const { chromium } = require('../crawler-service/node_modules/playwright')
const fs = require('node:fs/promises')
const assert = require('node:assert/strict')
;(async () => {
  const output = JSON.parse(await fs.readFile('output/menu-tc-samples/product-1.json', 'utf8'))
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    page.setDefaultTimeout(15000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    let run = null, stale = false, prepared = 0, connection = null, connectionHttp = 200, connectionCount = 0
    const history = []
    const view = () => ({ run, history, executorConnected: false, judgeConnected: false })
    await page.route('**/api/**', async route => {
      const req = route.request(), url = new URL(req.url()), path = url.pathname
      const json = value => route.fulfill({ contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/api/crawl-agents') return json({ agents: [{ agentId: 'agent-1', name: '시험 PC', online: true, busy: false }] })
      if (path.endsWith('/playwright-crawls')) {
        if (req.method() === 'POST') {
          const payload = req.postDataJSON(); assert.equal(payload.purpose, 'CONNECTION'); assert.equal(payload.draft.targetUrl, 'https://example.com'); assert.equal(payload.draft.testEnvironmentConfirmed, true)
          connection = { jobId: `connection-${++connectionCount}`, status: 'QUEUED', agentName: '현재 서버', errorCode: null, result: null }
          return json({ checks: { CONNECTION: connection } })
        }
        connection = { ...connection, status: 'COMPLETED', result: { httpStatus: connectionHttp, elapsedMs: 125, loginVerified: false } }
        return json({ checks: { CONNECTION: connection } })
      }
      if (path === '/api/submissions/ui-execution') return json({ submissionId: 'ui-execution', documents: [], aiDelivery: { status: 'DELIVERED', preflight: { decision: 'READY', warnings: [], generationGate: { allowed: true } } } })
      if (path.endsWith('/menu-test-cases')) return json({ set: { setId: 'set-1', stale, output }, history: [{ setId: 'set-1', createdAt: '2026-10-22T00:00:00Z' }] })
      if (path.endsWith('/executions')) {
        if (req.method() === 'POST') {
          const payload = req.postDataJSON(); prepared++
          assert.equal(payload.tcSetId, 'set-1'); assert.equal(payload.targetUrl, 'https://example.com'); assert.equal(payload.caseIds.length, output.caseCount - 27)
          const cases = output.testCases.filter(tc => payload.caseIds.includes(tc.id))
          run = { runId: 'run-1', tcSetId: 'set-1', status: 'PREPARED', completedCount: 0, stale: false, snapshot: { productName: output.productName, targetUrl: payload.targetUrl, notes: payload.notes, caseCount: cases.length, groupCount: new Set(cases.map(tc => tc.featureId)).size, groups: [], items: cases.map(tc => ({ caseId: tc.id, tc, executionStatus: 'PENDING', judgmentStatus: 'PENDING', result: null, actualResult: null, reason: null, evidence: [] })) } }
          history.push({ runId: run.runId, status: run.status, createdAt: '2026-10-22T00:00:00Z' })
        }
        return json(view())
      }
      if (path.endsWith('/start') || path.endsWith('/cancel')) {
        run.status = path.endsWith('/start') ? 'WAITING_EXECUTOR' : 'CANCELED'; history[0].status = run.status
        return json(view())
      }
      throw new Error('Unexpected request ' + path)
    })
    await page.goto(process.env.FRONTEND_QA_URL || 'http://127.0.0.1:4173/')
    await page.getByLabel('문서 세트 ID').fill('ui-execution')
    await page.getByRole('button', { name: '세트 조회', exact: true }).click()
    await page.getByRole('button', { name: '05 시험 실행', exact: true }).click()
    await page.getByLabel('실행할 TC 세트').selectOption('set-1')
    await page.getByLabel('시험 대상 URL', { exact: true }).fill('https://example.com')
    await page.getByRole('button', { name: 'Test connection', exact: true }).click()
    const popup = page.getByRole('dialog', { name: '연결 테스트', exact: true })
    await popup.getByLabel('접속 권한이 있는 시험 환경입니다.').check()
    await popup.getByRole('button', { name: '연결 테스트 시작', exact: true }).click()
    await popup.getByText('연결 성공 · 현재 서버 · HTTP 200 · 125ms', { exact: true }).waitFor()
    await fs.mkdir('output/test-execution', { recursive: true })
    await page.screenshot({ path: 'output/test-execution/connection-dialog.png' })
    await popup.getByRole('button', { name: '닫기', exact: true }).click()
    await page.getByText('✓ 연결 성공 · HTTP 200 · 125ms', { exact: true }).waitFor()
    await page.getByLabel('시험 대상 URL', { exact: true }).fill('https://other.example.com')
    await page.getByText('연결 미확인', { exact: true }).waitFor()
    await page.getByLabel('시험 대상 URL', { exact: true }).fill('https://example.com')
    await page.getByRole('button', { name: 'Test connection', exact: true }).click()
    await popup.getByLabel('접속 권한이 있는 시험 환경입니다.').check()
    connectionHttp = 403
    await popup.getByRole('button', { name: '연결 테스트 시작', exact: true }).click()
    await popup.getByText('HTTP 403 응답입니다. 대상 주소와 접근 권한을 확인해주세요.', { exact: true }).waitFor()
    assert.equal(await page.locator('.connection-success').count(), 0)
    await popup.getByRole('button', { name: '닫기', exact: true }).click()
    await page.getByLabel('시험 데이터·환경 메모').fill('기본 기능 시험')
    await page.locator('summary').filter({ hasText: '기능별 시험 범위' }).click()
    await page.locator('.execution-groups input[type=checkbox]').first().uncheck()
    assert.equal(await page.locator('.execution-groups input[type=checkbox]:not(:checked)').count(), 1)
    await page.getByRole('button', { name: '시험 실행 준비', exact: true }).click()
    await page.getByRole('button', { name: '실행 요청', exact: true }).waitFor()
    assert.equal(prepared, 1)
    assert.equal(await page.locator('.execution-panel tbody tr').count(), 20)
    assert.equal(await page.locator('.execution-panel tbody tr').first().locator('td').nth(4).innerText(), '미판정')
    await page.getByRole('button', { name: '실행 상세', exact: true }).first().click()
    await page.getByText('아직 수행하지 않았습니다.', { exact: true }).waitFor()
    await page.getByRole('button', { name: '상세 닫기', exact: true }).click()
    await page.getByRole('button', { name: '다음', exact: true }).click()
    await page.getByRole('button', { name: '실행 요청', exact: true }).click()
    await page.locator('.execution-summary strong').filter({ hasText: '실행기 연결 대기' }).waitFor()
    assert(await page.getByRole('button', { name: '실행 요청', exact: true }).isDisabled())
    await page.getByRole('button', { name: '실행 중단', exact: true }).click()
    await page.locator('.execution-summary strong').filter({ hasText: '중단됨' }).waitFor()
    await page.getByRole('button', { name: '01 자료 등록' }).click()
    await page.getByRole('button', { name: '05 시험 실행', exact: true }).click()
    assert.equal(await page.getByLabel('시험 데이터·환경 메모').inputValue(), '기본 기능 시험')
    await page.getByRole('button', { name: '실행 정보 새로고침', exact: true }).click()
    await page.locator('.execution-summary strong').filter({ hasText: '중단됨' }).waitFor()
    await fs.mkdir('output/test-execution', { recursive: true })
    await page.screenshot({ path: 'output/test-execution/desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'No page overflow on mobile')
    await page.screenshot({ path: 'output/test-execution/mobile.png', fullPage: true })
    stale = true
    await page.getByRole('button', { name: '실행 정보 새로고침', exact: true }).click()
    await page.getByText('원본이 변경된 TC입니다. TC를 다시 생성하세요.', { exact: true }).waitFor()
    assert(await page.getByRole('button', { name: '시험 실행 준비', exact: true }).isDisabled())
    assert.deepEqual(errors, [])
    console.log('PASS: TC linking, feature selection, preparation, pending judgments, detail/pagination, request/cancel, retained history, stale guard and responsive layout.')
  } finally { await browser.close() }
})().catch(e => { console.error(e); process.exitCode = 1 })
