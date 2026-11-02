// Read-only review of the dedicated live QA workspace. Does not start OCR or TC jobs.
const {chromium}=require('../crawler-service/node_modules/playwright')
const fs=require('node:fs/promises')
const assert=require('node:assert/strict')
;(async()=>{
  const report=JSON.parse(await fs.readFile('.runtime/manual-review-checks/live-api.json','utf8'))
  const browser=await chromium.launch({headless:true})
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1000}})
    const errors=[],starts=[]
    page.on('pageerror',error=>errors.push(error.message))
    page.on('request',request=>{if(request.method()==='POST'&&/manual-reviews|executions/.test(request.url()))starts.push(request.url())})
    await page.goto(process.env.FRONTEND_QA_URL||'http://127.0.0.1:5173')
    await page.getByLabel('회원 ID',{exact:true}).fill('98011')
    await page.getByLabel('문서 세트 ID',{exact:true}).fill(report.submissionId)
    await page.getByRole('button',{name:'세트 조회',exact:true}).click()
    await page.getByRole('button',{name:'06 매뉴얼 검토',exact:true}).click()
    await page.getByText('일부 검사 미완료',{exact:true}).waitFor()
    await page.locator('.manual-review-candidates article').first().waitFor()
    assert.equal(await page.getByText('확인 필요',{exact:true}).count(),report.candidateCount)
    assert.equal(await page.getByRole('button',{name:'매뉴얼 검토 시작',exact:true}).isEnabled(),true)
    // Capture controls only; do not put vendor document quotations in verification artifacts.
    await page.screenshot({path:'.runtime/manual-review-checks/live-ui.png',clip:{x:0,y:0,width:1440,height:650}})
    await page.waitForTimeout(500)
    await page.reload()
    await page.getByText('일부 검사 미완료',{exact:true}).waitFor()
    await page.locator('.manual-review-candidates article').first().waitFor()
    assert.equal(starts.length,0)
    assert.deepEqual(errors,[])
    await fs.writeFile('.runtime/manual-review-checks/live-ui.json',JSON.stringify({passed:true,port:5173,candidates:report.candidateCount,automaticJobStarts:starts.length,pageErrors:errors,restoredAfterReload:true},null,2))
    console.log('PASS: real API result visible on 5173, persisted history restored, no automatic OCR/TC start')
  }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1})
