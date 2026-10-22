const { chromium } = require('../crawler-service/node_modules/playwright')
const fs = require('node:fs/promises')
const assert = require('node:assert/strict')
;(async () => {
  const output = JSON.parse(await fs.readFile('output/menu-tc-samples/product-1.json', 'utf8'))
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    let set = null, generated = 0, failure = false, uploadStatus = 200, uploaded = false
    const job = { jobId: 'menu-qa', productName: output.productName, revision: 1, status: 'APPROVED', stale: false, generationMode: 'REAL', sources: [], warnings: [], output: { rows: output.features.map(f => ({ rowId: f.featureId, included: true, functionName: f.functionName, menuPath: f.featurePath.slice(0, -1), menuDescription: f.description, featureType: f.featureType })) } }
    const menus = { job, history: [{ jobId: job.jobId, productName: job.productName, status: job.status }], crawls: [] }
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname
      const json = value => route.fulfill({ contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/api/submissions/ui-tc') return json({ submissionId: 'ui-tc', documents: [], aiDelivery: { status: 'DELIVERED', retryable: false, preflight: { decision: 'READY', warnings: [], generationGate: { allowed: true } } } })
      if (path.endsWith('/functional-menu/upload')) {
        if (uploadStatus !== 200) return route.fulfill({ status: uploadStatus, contentType: 'application/json', body: '{}' })
        uploaded = true
        return json({ job: { ...job, jobId: 'uploaded-menu', productName: '업로드한 제품' } })
      }
      if (path.endsWith('/functional-menu')) {
        const selected = new URL(request.url()).searchParams.get('job')
        if (uploaded && selected !== 'uploaded-menu') throw new Error('Upload must not reload the previous menu')
        return json(uploaded ? { ...menus, job: { ...job, jobId: 'uploaded-menu', productName: '업로드한 제품' }, history: [...menus.history, { jobId: 'uploaded-menu', productName: '업로드한 제품', status: 'APPROVED' }] } : menus)
      }
      if (path.endsWith('/menu-test-cases')) {
        if (failure) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ message: '검증용 오류: 원본이 변경되었습니다.' }) })
        if (request.method() === 'POST') {
          generated++; const payload = request.postDataJSON(); assert.equal(payload.menuJobId, 'menu-qa'); assert.equal(Object.keys(payload.featureTypes).length, 133)
          set = { setId: 'tc-qa', stale: false, menuRevision: 1, output }
        }
        return json({ set, history: set ? [{ setId: 'tc-qa', menuRevision: 1, createdAt: '2026-10-21T00:00:00Z' }] : [] })
      }
      if (path.endsWith('/download')) return route.fulfill({ contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: await fs.readFile('output/menu-tc-samples/product-1.xlsx') })
      throw new Error('Unexpected request: ' + path)
    })
    await page.goto(process.env.FRONTEND_QA_URL || 'http://127.0.0.1:4173/')
    await page.getByLabel('문서 세트 ID').fill('ui-tc')
    await page.getByRole('button', { name: '세트 조회', exact: true }).click()
    await page.getByRole('button', { name: '04 TC 생성', exact: true }).click()
    await page.getByRole('button', { name: 'TC 초안 생성', exact: true }).waitFor()
    await page.getByText('133개 기능 · 예상 1,283개 TC', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'TC 초안 생성', exact: true }).click()
    await page.getByText('1,283개 생성 · 시험 미실행', { exact: true }).waitFor()
    assert.equal(generated, 1)
    assert.equal(await page.locator('.menu-tc-panel tbody tr').count(), 25)
    await page.getByLabel('TC 검색').fill('TC-001')
    assert.equal(await page.locator('.menu-tc-panel tbody tr').count(), 25)
    await page.getByRole('button', { name: '다음', exact: true }).click()
    assert.equal(await page.locator('.menu-tc-panel tbody tr').count(), 2)
    await page.getByRole('button', { name: '상세 보기', exact: true }).first().click()
    await page.getByText('결함 작성 예시 · 실제 결함 아님', { exact: true }).waitFor()
    await page.getByRole('button', { name: '01 자료 등록' }).click()
    await page.getByRole('button', { name: '04 TC 생성', exact: true }).click()
    assert.equal(await page.getByLabel('TC 검색').inputValue(), 'TC-001')
    const jsonDownload = page.waitForEvent('download')
    await page.getByRole('button', { name: '실행 입력 JSON', exact: true }).click()
    assert.match((await jsonDownload).suggestedFilename(), /실행입력\.json$/)
    const excelDownload = page.waitForEvent('download')
    await page.getByRole('button', { name: '엑셀 다운로드', exact: true }).click()
    assert.match((await excelDownload).suggestedFilename(), /TC-초안\.xlsx$/)
    await page.getByLabel('TC 검색').fill('')
    await page.getByRole('button', { name: '상세 닫기', exact: true }).click()
    await page.screenshot({ path: 'output/menu-tc-samples/tc-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'No horizontal page overflow')
    await page.screenshot({ path: 'output/menu-tc-samples/tc-mobile.png', fullPage: true })
    failure = true
    await page.getByRole('button', { name: '메뉴트리·TC 새로고침', exact: true }).click()
    await page.getByText('검증용 오류: 원본이 변경되었습니다.', { exact: true }).waitFor()
    failure = false; set.stale = true
    await page.getByRole('button', { name: '메뉴트리·TC 새로고침', exact: true }).click()
    await page.getByText(/원본 메뉴트리나 문서가 변경되었습니다/).waitFor()
    assert(await page.getByRole('button', { name: '엑셀 다운로드', exact: true }).isDisabled())
    const file = { name: '검증-메뉴.json', mimeType: 'application/json', buffer: Buffer.from('{"rows":[]}') }
    uploadStatus = 404
    await page.getByLabel('메뉴트리 파일 업로드').setInputFiles(file)
    await page.getByRole('alert').filter({ hasText: '현재 서버에 메뉴트리 업로드 API가 반영되지 않았습니다' }).waitFor()
    uploadStatus = 200
    await page.getByLabel('메뉴트리 파일 업로드').setInputFiles(file)
    await page.getByText('검증-메뉴.json 업로드 완료', { exact: true }).waitFor()
    await page.getByText(/업로드한 제품 · 승인 완료 · 버전 1/).waitFor()
    assert.equal(await page.getByLabel('메뉴트리 선택').inputValue(), 'uploaded-menu')
    assert.deepEqual(errors, [])
    console.log('PASS: real generated sample preview, 133 feature assignments, 1283 case view, search/pagination/detail, state retention, Excel/JSON download, API error, stale-source guard and mobile layout.')
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
