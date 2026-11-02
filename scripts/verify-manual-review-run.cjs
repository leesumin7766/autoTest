// Isolated acceptance test. No real OCR/LLM call or operational database write.
const { chromium } = require('../crawler-service/node_modules/playwright')
const assert = require('node:assert/strict')
;(async () => {
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}})
    page.setDefaultTimeout(8000)
    const errors=[]; page.on('pageerror', e=>errors.push(e.message))
    let job=null, finish=false, starts=0, unavailable=false
    const view=()=>({job,history:job?[{...job,output:undefined}]:[]})
    await page.route('**/api/**', route=>{
      const request=route.request(), path=new URL(request.url()).pathname
      const json=value=>route.fulfill({json:value})
      if(path.endsWith('/local/status'))return json({connected:true,busy:false,jobs:[]})
      if(path.endsWith('/workspace'))return json({id:'manual-run',productId:1,name:'매뉴얼 시험',lastPage:'manual-review'})
      if(path==='/api/submissions/manual-run')return json({submissionId:'manual-run',documents:[{fileId:'manual-file',role:'MANUAL',format:'PDF',originalFilename:'검증 매뉴얼.pdf',status:'PARSED',extractedText:'원문'}],aiDelivery:{status:'NOT_READY',preflight:null}})
      if(path.endsWith('/manual-reviews')){
        if(request.method()==='POST'){
          starts++
          if(unavailable)return route.fulfill({status:503,json:{code:'MANUAL_REVIEW_UNAVAILABLE'}})
          job={jobId:'review-1',manualFileId:'manual-file',filename:'검증 매뉴얼.pdf',status:'RUNNING',createdAt:new Date().toISOString(),asOf:'2026-11-02',stale:false}
        }else if(job&&finish)job={...job,status:'PARTIAL',output:{pageCount:2,blockCount:3,reviewedChunks:2,failedChunks:1,catalogVersion:'test-v1',catalogProducts:['Windows 10'],warnings:['1개 구간 오탈자 검사 미완료'],candidates:[{id:'eol-1',type:'EOL_SOFTWARE',status:'NEEDS_REVIEW',page:2,title:'Windows 10 지원 표기 확인',quote:'Windows 10 Pro 지원',reason:'일반 지원 종료 기준 확인',reference:{supportEnd:'2025-10-14',checkedAt:'2026-11-02',url:'https://learn.microsoft.com/en-us/lifecycle/products/windows-10-home-and-pro',scope:'일반 지원'}},{id:'typo-1',type:'TYPO',status:'NEEDS_REVIEW',page:1,title:'오탈자 의심',quote:'저장됬습니다.',original:'저장됬습니다',suggestion:'저장되었습니다',reason:'원본 확인 필요'}]}}
        return json(view())
      }
      return json({})
    })
    await page.goto(process.env.FRONTEND_QA_URL||'http://127.0.0.1:5173')
    await page.getByLabel('문서 세트 ID',{exact:true}).fill('manual-run')
    await page.getByRole('button',{name:'세트 조회',exact:true}).click()
    await page.getByRole('button',{name:'매뉴얼 검토 시작',exact:true}).click()
    await page.getByText('매뉴얼 분석 중',{exact:true}).waitFor()
    assert.equal(starts,1)
    assert.equal(await page.getByRole('button',{name:'매뉴얼 검토 시작',exact:true}).isEnabled(),false)
    finish=true
    await page.getByText('일부 검사 미완료',{exact:true}).waitFor()
    await page.getByText('Windows 10 Pro 지원',{exact:true}).waitFor()
    await page.getByText('저장되었습니다',{exact:true}).waitFor()
    assert.equal(await page.getByText('확인 필요',{exact:true}).count(),2)
    assert.match(await page.getByRole('link',{name:'공식 지원 기준'}).getAttribute('href'),/^https:\/\/learn.microsoft.com\//)
    await page.reload()
    await page.getByText('Windows 10 Pro 지원',{exact:true}).waitFor()
    assert.equal(starts,1,'새로고침은 검사를 다시 시작하지 않는다')
    unavailable=true
    await page.getByRole('button',{name:'매뉴얼 검토 시작',exact:true}).click()
    await page.getByRole('alert').filter({hasText:'로컬 매뉴얼 검토 서비스'}).waitFor()
    await page.getByText('Windows 10 Pro 지원',{exact:true}).waitFor()
    await page.setViewportSize({width:390,height:844})
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
    await page.screenshot({path:'output/manual-review-results-mobile.png',fullPage:true})
    await page.setViewportSize({width:1440,height:1000})
    await page.screenshot({path:'output/manual-review-results-desktop.png',fullPage:true})
    assert.deepEqual(errors,[])
    console.log('PASS: explicit start, polling, partial coverage, grounded candidates, source link, restore, service error preserves results and mobile')
  } finally {await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1})
