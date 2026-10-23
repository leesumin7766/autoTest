// Read-only verification against the running Gateway and actual UI. No TC execution is started.
// --connection additionally probes the saved target, including its saved login when configured.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const {chromium}=require('../crawler-service/node_modules/playwright');
(async()=>{
 const submission=process.argv[2];if(!/^[0-9a-f-]{36}$/i.test(submission||''))throw Error('Provide a submission UUID');
 const member=process.env.AUTOTEST_MEMBER_ID||'1',base=process.env.AUTOTEST_API_URL||'http://127.0.0.1:8080';
 const results={checkedAt:new Date().toISOString(),submission,endpoints:{}};
 const get=async suffix=>{const response=await fetch(`${base}/api/submissions/${submission}${suffix}`,{headers:{'X-Member-Id':member}});results.endpoints[suffix||'/']=response.status;assert.equal(response.status,200,suffix);return response.json()};
 const docs=await get('');assert.equal(docs.submissionId,submission);
 const setup=await get('/playwright-setup');await get('/playwright-crawls');const menus=await get('/functional-menu');
 const tc=await get('/menu-test-cases'),execution=await get('/executions');
 assert(tc.set?.setId,'Missing generated TC set');assert(tc.history.some(row=>row.setId===tc.set.setId));
 assert.equal(tc.set.stale,false);assert.equal(tc.set.output.caseCount,tc.set.output.testCases.length);
 assert(menus.history.some(menu=>menu.jobId===tc.set.menuJobId),'TC source menu must exist');
 assert(execution.executorConnected&&execution.judgeConnected,JSON.stringify(execution.readinessIssues));
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(process.env.FRONTEND_QA_URL||'http://127.0.0.1:4173/');
  await page.getByLabel('문서 세트 ID').fill(submission);await page.getByRole('button',{name:'세트 조회',exact:true}).click();
  await page.getByRole('button',{name:'04 TC 생성',exact:true}).click();
  await page.getByText(`${tc.set.output.caseCount.toLocaleString()}개 생성 · 시험 미실행`,{exact:true}).waitFor();
  await page.getByRole('button',{name:'05 시험 실행',exact:true}).click();
  await page.waitForFunction(setId=>document.querySelector('select[aria-label="실행할 TC 세트"]')?.value===setId,tc.set.setId);
  assert.equal(await page.getByLabel('시험 대상 URL',{exact:true}).inputValue(),setup.setup?.targetUrl||'');
  assert(await page.getByRole('button',{name:'시험 실행 준비',exact:true}).isEnabled());
  if(process.argv.includes('--connection')){
   await page.getByRole('button',{name:'Test connection',exact:true}).click();
   const dialog=page.getByRole('dialog',{name:'연결 테스트',exact:true});
   await dialog.getByLabel('접속 권한이 있는 시험 환경입니다.').check();
   await dialog.getByRole('button',{name:'연결 테스트 시작',exact:true}).click();
   await dialog.getByText(/^연결(?:·로그인)? 성공 · 현재 서버 · HTTP 200/).waitFor({timeout:90000});
   results.connectionProbe='HTTP 200';await dialog.getByRole('button',{name:'닫기',exact:true}).click();
  }
  await fs.mkdir('output/pipeline-audit',{recursive:true});await page.screenshot({path:'output/pipeline-audit/live-execution-ready.png',fullPage:true});
  assert.deepEqual(errors,[]);results.caseCount=tc.set.output.caseCount;results.tcSetId=tc.set.setId;results.uiReady=true;results.readinessIssues=execution.readinessIssues;
  await fs.writeFile('output/pipeline-audit/runtime.json',JSON.stringify(results,null,2));console.log('PASS: Gateway APIs, persisted TC source, installed execution dependencies and live UI preparation enabled; no product test started.');
 }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});
