// Isolated UI test for page progress, clock, pause and resume. No model or real DB.
const {chromium}=require('../crawler-service/node_modules/playwright')
const assert=require('node:assert/strict')
;(async()=>{
  const browser=await chromium.launch({headless:true})
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1000}})
    page.setDefaultTimeout(10000)
    const errors=[];page.on('pageerror',error=>errors.push(error.message))
    let job=null,advanced=false,confirmStop=false,done=false,starts=0,pauses=0,resumes=0
    const update=()=>{
      if(!job)return
      if(job.status==='PAUSING'&&confirmStop)job={...job,status:'PAUSED',resumable:true,progress:{...job.progress,stage:'PAUSED',elapsedMs:68000}}
      if(job.status==='RUNNING'&&advanced)job={...job,progress:{...job.progress,totalPages:81,currentPage:3,completedPages:2,stage:'REVIEWING',elapsedMs:65000}}
      if(job.status==='RUNNING'&&done)job={...job,status:'COMPLETED',resumable:false,progress:{...job.progress,totalPages:81,currentPage:81,completedPages:81,stage:'COMPLETED',elapsedMs:90000}}
    }
    const view=()=>({job,history:job?[job]:[]})
    await page.route('**/api/**',route=>{
      const request=route.request(),path=new URL(request.url()).pathname
      const json=value=>route.fulfill({json:value})
      if(path.endsWith('/local/status'))return json({connected:true,busy:false,jobs:[]})
      if(path.endsWith('/workspace'))return json({id:'progress-qa',productId:1,name:'검토 진행 검증',lastPage:'manual-review'})
      if(path==='/api/submissions/progress-qa')return json({submissionId:'progress-qa',documents:[{fileId:'manual',role:'MANUAL',format:'PDF',originalFilename:'진행 검증.pdf',status:'PARSED',extractedText:'fixture'}],aiDelivery:{status:'NOT_READY',preflight:null}})
      if(path.endsWith('/pause')){pauses++;job={...job,status:'PAUSING',progress:{...job.progress,stage:'PAUSING',elapsedMs:67000}};return json(view())}
      if(path.endsWith('/resume')){resumes++;job={...job,status:'RUNNING',attempt:2,resumable:false,progress:{...job.progress,stage:'REVIEWING',elapsedMs:68000}};return json(view())}
      if(path.endsWith('/manual-reviews')){
        if(request.method()==='POST'){starts++;job={jobId:'job-1',manualFileId:'manual',filename:'진행 검증.pdf',status:'RUNNING',attempt:1,asOf:'2026-11-02',createdAt:new Date().toISOString(),stale:false,resumable:false,progress:{totalPages:0,currentPage:0,completedPages:0,stage:'COUNTING',elapsedMs:500}}}
        else update()
        return json(view())
      }
      return json({})
    })
    await page.goto(process.env.FRONTEND_QA_URL||'http://127.0.0.1:5173')
    await page.getByLabel('문서 세트 ID',{exact:true}).fill('progress-qa')
    await page.getByRole('button',{name:'세트 조회',exact:true}).click()
    await page.getByRole('button',{name:'매뉴얼 검토 시작',exact:true}).click()
    const progress=page.getByRole('region',{name:'매뉴얼 검토 진행 현황',exact:true})
    await progress.getByText('전체 페이지 수 확인 중',{exact:true}).waitFor()
    const clock=progress.locator('.run-clock b'),before=await clock.innerText()
    await page.waitForTimeout(1200)
    assert.notEqual(await clock.innerText(),before)
    advanced=true
    await progress.getByText(/현재 3 \/ 81페이지/).first().waitFor()
    await page.getByRole('button',{name:'검토 중단',exact:true}).click()
    await page.getByRole('heading',{name:'중단 확인 중',exact:true}).waitFor()
    assert.equal(await page.getByRole('button',{name:'이어서 검토',exact:true}).count(),0)
    confirmStop=true
    await page.getByRole('button',{name:'이어서 검토',exact:true}).waitFor()
    const frozen=await clock.innerText();await page.waitForTimeout(1200);assert.equal(await clock.innerText(),frozen)
    await page.screenshot({path:'output/manual-review-paused-desktop.png',fullPage:true})
    await page.reload()
    await page.getByRole('button',{name:'이어서 검토',exact:true}).waitFor()
    assert.equal(await clock.innerText(),frozen)
    await page.getByRole('button',{name:'이어서 검토',exact:true}).click()
    await progress.getByText(/현재 3 \/ 81페이지/).first().waitFor()
    assert.equal(starts,1);assert.equal(pauses,1);assert.equal(resumes,1)
    done=true
    await page.getByRole('heading',{name:'검사 완료',exact:true}).waitFor()
    assert.equal(await progress.getByRole('progressbar').getAttribute('value'),'81')
    await page.setViewportSize({width:390,height:844})
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
    await page.screenshot({path:'output/manual-review-progress-mobile.png',fullPage:true})
    assert.deepEqual(errors,[])
    console.log('PASS: count before processing, current/total, live clock, confirmed pause, frozen elapsed, reload and resume without new start, mobile')
  }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1})
