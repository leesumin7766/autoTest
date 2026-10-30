// Isolated UI acceptance tests: no real document, OCR, LLM or production DB.
const { chromium } = require('../crawler-service/node_modules/playwright')
const assert = require('node:assert/strict')
const origin = process.env.FRONTEND_QA_URL || 'http://127.0.0.1:4173'
;(async () => {
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    page.setDefaultTimeout(5000)
    const errors = [], calls = []
    page.on('pageerror', e => errors.push(e.message))
    let manualStatus = 'PARSED', lastPage = 'documents'
    await page.route('**/api/**', route => {
      const req = route.request(), path = new URL(req.url()).pathname
      calls.push(path)
      const json = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) })
      if (path === '/api/crawl-agents/local/status') return json({ connected: true, busy: false, jobs: [] })
      if (path.endsWith('/workspace')) {
        if (req.method() === 'PATCH') lastPage = req.postDataJSON().lastPage || lastPage
        return json({ id: 'manual-qa', productId: 1, name: '매뉴얼 검증 작업', lastPage })
      }
      if (path === '/api/submissions/manual-qa') return json({ submissionId: 'manual-qa', documents: manualStatus ? [{ fileId: 'manual-1', role: 'MANUAL', originalFilename: '관리자매뉴얼.pdf', format: 'pdf', fileType: 'PDF', status: manualStatus, extractedText: '시험 문서', failureReason: null }] : [], aiDelivery: { status: 'NOT_READY', preflight: null } })
      return json({})
    })
    await page.goto(origin)
    await page.getByRole('heading', { name: '자료 등록', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '매뉴얼 검토로 이동', exact: true }).count(), 0)
    await page.getByRole('button', { name: '06 매뉴얼 검토', exact: true }).click()
    await page.getByRole('heading', { name: '제품 매뉴얼을 등록해 주세요' }).waitFor()
    await page.getByRole('button', { name: '자료 등록으로 이동', exact: true }).click()
    await page.getByLabel('문서 세트 ID', { exact: true }).fill('manual-qa')
    await page.getByRole('button', { name: '세트 조회', exact: true }).click()
    await page.getByRole('button', { name: '매뉴얼 검토로 이동', exact: true }).click()
    await page.getByRole('heading', { name: '검토 대상 매뉴얼' }).waitFor()
    await page.getByText('관리자매뉴얼.pdf', { exact: true }).last().waitFor()
    await page.getByRole('heading', { name: '오탈자 검토', exact: true }).waitFor()
    await page.getByRole('heading', { name: '지원 종료 SW 표기 검토', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '자동 검토 준비 중' }).isDisabled(), true)
    await page.waitForTimeout(500)
    assert.equal(lastPage, 'manual-review')
    await page.reload()
    await page.getByRole('heading', { name: '검토 대상 매뉴얼' }).waitFor()
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.screenshot({ path: 'output/manual-review-mobile.png', fullPage: true })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.screenshot({ path: 'output/manual-review-desktop.png', fullPage: true })
    for (const status of ['FAILED', 'UPLOADED', null]) {
      manualStatus = status
      await page.reload()
      await page.getByRole('heading', { name: '제품 매뉴얼을 등록해 주세요' }).waitFor()
      await page.getByRole('button', { name: /^01 자료 등록/ }).click()
      assert.equal(await page.getByRole('button', { name: '매뉴얼 검토로 이동', exact: true }).count(), 0)
    }
    await page.getByRole('button', { name: '07 결함 검토', exact: true }).waitFor()
    await page.getByRole('button', { name: '08 리포트·재시험', exact: true }).waitFor()
    assert.equal(calls.some(path => /executions|manual-reviews/.test(path)), false)
    assert.deepEqual(errors, [])
    console.log('PASS: manual review navigation, document gates, restore, mobile and isolation')
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
