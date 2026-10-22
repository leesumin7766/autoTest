// UI regression checks with isolated API fixtures; never calls a real crawler or LLM.
// Run against a local Vite server: node scripts/verify-frontend.cjs
const { chromium } = require('../crawler-service/node_modules/playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const origin = process.env.FRONTEND_QA_URL || 'http://127.0.0.1:4173'

;(async () => {
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = [], calls = [], documents = []
    page.on('pageerror', error => errors.push(error.message))
    let delivery = { status: 'PENDING', attempts: 1, lastError: null, deliveredAt: null, updatedAt: null, retryable: false, preflight: null }
    let setup = null, checks = {}, crawl = null
    const row = { rowId: 'row-1', included: true, menuPath: ['사용자 관리'], functionName: '사용자 조회', menuDescription: '등록된 사용자를 조회한다.', location: 'SIDEBAR', verificationStatus: 'MATCH_PROPOSED', evidenceStatus: 'VALID', connectionStatus: 'PROPOSED', evidence: [{ sourceId: 'source-1', quote: '사용자를 조회할 수 있습니다.' }], matchedMenuKeys: [], reviewNotes: [] }
    let job = { jobId: 'menu-1', crawlJobId: 'crawl-1', productName: '검증용 제품', status: 'DRAFT', revision: 1, stale: false, generationMode: 'REAL', warnings: [], sources: [{ id: 'source-1', kind: 'DOCUMENT' }], output: { rows: [row] } }
    const view = () => ({ job, history: [{ jobId: job.jobId, productName: job.productName, status: job.status }], crawls: [{ jobId: 'crawl-1', pageCount: 1, createdAt: '2026-10-21T00:00:00Z', targetUrl: 'https://example.com' }] })
    const submission = () => ({ submissionId: 'qa-set', status: 'PARSED', failureReason: null, documents, aiDelivery: delivery })
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname
      calls.push([request.method(), path])
      const json = value => route.fulfill({ contentType: 'application/json', body: JSON.stringify(value) })
      if (request.method() === 'POST' && (path === '/api/submissions' || path === '/api/submissions/qa-set/files')) {
        const role = request.postDataBuffer().toString().match(/name="role"\r\n\r\n(\w+)/)[1]
        const doc = { role, fileId: role, fileType: 'PDF', format: 'pdf', originalFilename: role + '.pdf', storedPath: '/qa/' + role, extractedText: '사용자를 조회할 수 있습니다.', status: 'PARSED', failureReason: null, aiDelivery: delivery }
        documents.push(doc)
        return json(path === '/api/submissions' ? submission() : doc)
      }
      if (path === '/api/submissions/qa-set') return json(submission())
      if (path.endsWith('/ai-delivery/retry')) { delivery = { ...delivery, status: 'PENDING', retryable: false }; return json(delivery) }
      if (path.includes('/files/') && path.endsWith('/replace')) {
        const role = path.split('/files/')[1].split('/')[0]
        return json({ ...documents.find(doc => doc.role === role), aiDelivery: delivery })
      }
      if (path.endsWith('/playwright-setup')) {
        if (request.method() === 'PUT') setup = { ...request.postDataJSON(), passwordConfigured: false, stale: false, status: 'CONFIGURED' }
        return json({ setup })
      }
      if (path === '/api/crawl-agents') return json({ agents: [] })
      if (path.endsWith('/playwright-crawls')) {
        if (request.method() === 'POST') {
          const body = request.postDataJSON()
          const result = { jobId: 'check-' + body.purpose, status: 'COMPLETED', agentName: '현재 서버', pageCount: 0, errorCode: null, stopReason: 'DONE', result: { httpStatus: 200, elapsedMs: 234, loginVerified: true } }
          if (body.purpose === 'CRAWL') crawl = result
          else checks = { ...checks, [body.purpose]: result }
        }
        return json({ job: crawl, pages: [], checks })
      }
      if (path.endsWith('/evidence')) return json({ id: 'source-1', kind: 'DOCUMENT', filename: '매뉴얼.pdf', text: '사용자를 조회할 수 있습니다.' })
      if (path.endsWith('/download')) return route.fulfill({ contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: 'fixture' })
      if (path.endsWith('/prompt')) return json({ instructionVersion: 'self-llm-v2', requestId: 'qa-request', prompt: '검증용 외부 LLM 프롬프트' })
      if (/\/functional-menu\/menu-1\/(draft|approval|import)$/.test(path)) {
        job = { ...job, status: path.endsWith('/approval') ? 'APPROVED' : 'DRAFT', revision: job.revision + 1, output: { rows: request.postDataJSON().rows } }
        return json(view())
      }
      if (path.endsWith('/functional-menu')) return json(view())
      throw new Error('Unhandled fixture request: ' + request.method() + ' ' + path)
    })
    await page.goto(origin)
    await page.getByRole('heading', { name: '자료 등록', exact: true }).waitFor()
    assert.equal(await page.locator('input[type=file]').count(), 3)
    assert.equal(await page.locator('.workspace-sidebar').evaluate(el => el.getBoundingClientRect().width), 280)
    await page.getByRole('button', { name: '02 화면 수집' }).click()
    await page.getByRole('heading', { name: '등록된 문서 세트가 없습니다' }).waitFor()
    await page.getByRole('button', { name: '01 자료 등록' }).click()
    const inputs = page.locator('input[type=file]')
    for (let i = 0; i < 3; i++) await inputs.nth(i).setInputFiles({ name: '문서-' + i + '.pdf', mimeType: 'application/pdf', buffer: Buffer.from('test fixture') })
    await page.getByRole('button', { name: '03 메뉴트리' }).click()
    await page.getByRole('button', { name: '01 자료 등록' }).click()
    assert.equal(await inputs.nth(1).evaluate(el => el.files[0].name), '문서-1.pdf')
    await page.getByRole('button', { name: '자료 업로드·검증' }).click()
    await page.locator('.document-row.state-success').nth(2).waitFor()
    await page.getByText('Jev에서 문서 가독성과 동일 제품 여부를 확인하고 있습니다.').waitFor()
    delivery = { ...delivery, status: 'DELIVERED', preflight: { decision: 'READY', warnings: [], generationGate: { allowed: true, reasons: [] } } }
    await page.locator('.workspace-page-heading .badge-success').waitFor({ timeout: 10000 })
    assert.equal(await page.locator('.document-row.state-success').first().evaluate(el => getComputedStyle(el).borderTopColor), 'rgb(22, 163, 74)')
    await page.getByRole('tab', { name: 'Jev 검증·진단' }).click()
    await page.getByText('LLM 사전 점검: 通過'.replace('通過', '통과')).waitFor()
    await page.getByRole('tab', { name: '진행 상태' }).click()
    await page.getByRole('button', { name: '02 화면 수집' }).click()
    await page.getByLabel('화면 수집 시작 URL').fill('https://example.com')
    await page.getByLabel('접속·로그인·화면 수집 권한이 있는 테스트 환경과 계정입니다.').check()
    await page.getByRole('button', { name: '연결 테스트', exact: true }).click()
    await page.getByText(/연결 확인 · HTTP 200/).waitFor()
    await page.locator('summary').filter({ hasText: /^로그인 정보 · 로그인 테스트$/ }).click()
    await page.getByLabel('로그인 방식').selectOption('NONE')
    await page.getByRole('button', { name: '로그인·화면 수집 설정 저장' }).click()
    await page.getByText('설정을 저장했습니다.', { exact: false }).waitFor()
    await page.getByRole('button', { name: '로그인하고 화면 수집 시작' }).click()
    await page.getByText(/현재 서버 · 수집 완료/).waitFor()
    await page.getByRole('button', { name: '03 메뉴트리' }).click()
    await page.getByLabel('1행 기능명').fill('사용자 검색')
    await page.getByRole('button', { name: '02 화면 수집' }).click()
    assert.equal(await page.getByLabel('화면 수집 시작 URL').inputValue(), 'https://example.com')
    await page.getByRole('button', { name: '03 메뉴트리' }).click()
    assert.equal(await page.getByLabel('1행 기능명').inputValue(), '사용자 검색')
    await page.getByRole('button', { name: '수정 초안 저장' }).click()
    await page.getByRole('button', { name: '포함한 기능을 검토 완료·승인' }).click()
    await page.getByRole('button', { name: '승인 Excel 다운로드' }).waitFor()
    const download = page.waitForEvent('download')
    await page.getByRole('button', { name: '승인 Excel 다운로드' }).click()
    assert.match((await download).suggestedFilename(), /메뉴트리\.xlsx$/)
    await page.getByRole('button', { name: /근거 1:/ }).click()
    await page.getByRole('heading', { name: '고정된 입력 근거' }).waitFor()
    await page.getByRole('button', { name: '닫기', exact: true }).click()
    await page.getByRole('button', { name: '기능 분리', exact: true }).first().click()
    await page.getByLabel('2행 기능명').fill('사용자 상세 조회')
    const included = page.locator('.functional-menu-table tbody tr').nth(1).locator('input[type=checkbox]')
    await included.uncheck()
    await page.getByRole('button', { name: '수정 초안 저장' }).click()
    assert.equal(job.output.rows.length, 2)
    assert.equal(job.output.rows[1].included, false)
    const draftDownload = page.waitForEvent('download')
    await page.getByRole('button', { name: '초안 Excel', exact: true }).click()
    assert.match((await draftDownload).suggestedFilename(), /초안\.xlsx$/)
    await page.locator('summary').filter({ hasText: /^제품별 프롬프트·JSON 가져오기$/ }).click()
    await page.getByRole('button', { name: '이 실행의 제품 프롬프트 다시 만들기', exact: true }).click()
    assert.equal(await page.getByLabel('제품별 메뉴트리 프롬프트').inputValue(), '검증용 외부 LLM 프롬프트')
    await page.getByLabel('LLM JSON 응답').fill(JSON.stringify({ requestId: 'qa-request', rows: [{ ...row, functionName: '외부 응답 기능' }] }))
    await page.getByRole('button', { name: '근거 검증 후 가져오기' }).click()
    await page.waitForFunction(() => document.querySelector('input[aria-label="1행 기능명"]')?.value === '외부 응답 기능')
    assert.equal(await page.getByLabel('1행 기능명').inputValue(), '외부 응답 기능')
    job = { ...job, output: { ...job.output, completionStatus: 'PARTIAL' } }
    await page.getByRole('button', { name: '수집 결과·작업 새로고침' }).click()
    await page.getByText(/부분 초안입니다/).waitFor()
    assert(await page.getByRole('button', { name: '포함한 기능을 검토 완료·승인' }).isDisabled())
    await page.getByRole('button', { name: '01 자료 등록' }).click()
    await fs.mkdir('output/frontend-ui', { recursive: true })
    await page.screenshot({ path: 'output/frontend-ui/documents-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile page must not overflow horizontally')
    await page.screenshot({ path: 'output/frontend-ui/documents-mobile.png', fullPage: true })
    await page.getByRole('button', { name: '＋ 새 문서 세트' }).click()
    assert.equal(await page.locator('.document-row.state-success').count(), 0)
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.screenshot({ path: 'output/frontend-ui/empty-desktop.png', fullPage: true })
    delivery = { ...delivery, status: 'BLOCKED', retryable: true, preflight: { decision: 'BLOCKED', warnings: [], generationGate: { allowed: false, reasons: [] } } }
    await page.getByLabel('문서 세트 ID').fill('qa-set')
    await page.getByRole('button', { name: '세트 조회', exact: true }).click()
    await page.getByText('검증 차단', { exact: true }).first().waitFor()
    await page.locator('input[type=file]').nth(1).setInputFiles({ name: '교체.pdf', mimeType: 'application/pdf', buffer: Buffer.from('replacement') })
    await page.locator('.document-row').nth(1).getByRole('button', { name: '업로드', exact: true }).click()
    await page.getByRole('button', { name: '전달 재시도', exact: true }).click()
    await page.getByText('Jev에서 문서 가독성과 동일 제품 여부를 확인하고 있습니다.').waitFor()
    assert.equal(documents.length, 3)
    assert(calls.some(([method, path]) => method === 'PUT' && path.endsWith('/approval')))
    assert.deepEqual(errors, [])
    assert(calls.some(([method, path]) => method === 'POST' && path.endsWith('/files/FUNCTION_LIST/replace')))
    console.log('PASS: empty state, separate uploads, Jev pending/ready/blocked/retry, document replacement, green borders, navigation state retention, connection test, save, crawl, menu edit/split/exclude/save/approval, draft/approved Excel, evidence dialog, external prompt/import, partial approval gate, reset, mobile overflow; no browser errors.')
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
