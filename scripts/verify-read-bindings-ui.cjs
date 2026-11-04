// Synthetic browser fixtures only; all API requests are intercepted.
const { chromium } = require('../crawler-service/node_modules/playwright')
const assert = require('node:assert/strict')
;(async () => {
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    const tc = { id: 'read-1', tcId: 'READ-01', featureId: 'reports', featurePath: ['Reports'], scenario: 'Open reports', inputs: 'No filter required', expectedResult: 'Report list', evidence: [], executionPlan: { specificationVersion: 'execution-spec-v2-read', specification: { sourceTcDigest: 'fixture-digest', status: 'NEEDS_CONTEXT', bindingRevision: 0, bindings: {}, checks: [{ id: 'C1', requirementQuote: 'Report list' }], missingBindings: [{ message: '조회 근거 연결 필요' }] } } }
    const targets = [{ menuKey: 'reports', href: 'https://example.test/reports', label: 'Reports' }]
    let writes = 0, conflict = false
    await page.route('**/api/**', async route => {
      const req = route.request(), path = new URL(req.url()).pathname
      const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
      if (path === '/api/crawl-agents/local/status') return json({ connected: false, busy: false, jobs: [] })
      if (path.endsWith('/workspace')) return json({ id: 'ui-bindings', name: 'Synthetic', productId: 1, lastPage: 'documents' })
      if (path.endsWith('/read-bindings')) {
        if (req.method() === 'PUT') {
          if (conflict) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'READ_BINDING_CONFLICT' }) })
          const body = req.postDataJSON(); assert.equal(body.revision, writes); assert.equal(body.sourceTcDigest, 'fixture-digest')
          assert.equal(body.target.menuKey, 'reports'); assert(body.readContext.confirmed); assert.equal(body.checks[0].expected, 'Report list')
          assert.equal(body.readContext.oracle.quote, 'No filter required'); writes++
          tc.executionPlan.specification = { ...tc.executionPlan.specification, bindingRevision: writes, status: 'READY', missingBindings: [], bindings: { target: body.target, readContext: body.readContext }, checks: [{ ...tc.executionPlan.specification.checks[0], oracle: body.checks[0].oracle }] }
        }
        return json({ stale: false, tc, targets })
      }
      if (path === '/api/submissions/ui-bindings') return json({ submissionId: 'ui-bindings', documents: [], aiDelivery: { status: 'DELIVERED', preflight: { decision: 'READY' } } })
      if (path.endsWith('/menu-test-cases')) return json({ set: { setId: 'set-1', stale: false, output: { productName: 'Synthetic', testCases: [tc] } }, history: [{ setId: 'set-1', createdAt: '2026-11-04T00:00:00Z' }] })
      if (path.endsWith('/executions')) return json({ run: null, history: [], executorConnected: false, judgeConnected: false })
      if (path.endsWith('/playwright-setup')) return json({ setup: null })
      return json({ history: [], workspaces: [], job: null })
    })
    await page.goto(process.env.FRONTEND_QA_URL || 'http://127.0.0.1:5173/')
    await page.getByLabel('문서 세트 ID').fill('ui-bindings')
    await page.getByRole('button', { name: '세트 조회', exact: true }).click()
    await page.getByRole('button', { name: '05 시험 실행', exact: true }).click()
    await page.getByText('조회 실행 명세 근거 연결 · 1개 TC', { exact: true }).click()
    await page.getByLabel('근거 연결할 조회 TC').selectOption('read-1')
    await page.getByLabel('조회 대상 화면').selectOption('0')
    await page.getByLabel('조회 전제 출처').selectOption('0')
    await page.getByLabel('선택한 근거를 검토했으며 조건 입력 없이 조회하는 화면임을 확인했습니다.').check()
    await page.getByLabel('C1 기대 조건 출처').selectOption('0')
    await page.getByRole('button', { name: '조회 근거 연결 저장', exact: true }).click()
    await page.getByText('저장 상태: 근거 연결 완료', { exact: true }).waitFor()
    assert.equal(writes, 1)
    await page.getByRole('button', { name: '저장된 연결 다시 불러오기', exact: true }).click()
    await page.getByText('저장 상태: 근거 연결 완료', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('조회 전제 인용').inputValue(), 'No filter required')
    conflict = true
    await page.getByRole('button', { name: '조회 근거 연결 저장', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '저장된 연결을 다시 불러온 뒤' }).waitFor()
    assert.equal(writes, 1); assert.deepEqual(errors, [])
    console.log('PASS: read bindings selection, save, reload and conflict UI; synthetic API only')
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
