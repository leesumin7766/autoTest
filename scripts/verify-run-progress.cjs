// Isolated UI fixtures: no product, account, crawler or model calls.
const {chromium}=require('../crawler-service/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
(async()=>{
  const output=JSON.parse(await fs.readFile('output/menu-tc-samples/product-1.json','utf8'));
  const cases=output.testCases.filter(tc=>!tc.featureId.startsWith('scope:')).slice(0,3);
  let crawl={jobId:'crawl-old',status:'RUNNING',agentName:'현재 서버',pageCount:33,createdAt:new Date(Date.now()-65000).toISOString(),resumeAvailable:false,crawlResult:{uniqueScreenCount:23,incompleteCount:2,navigationFailureCount:1,noChangeCount:0,progress:{stage:'LOADING',current:'작업 관리 → 목록',remaining:4}}};
  let run={runId:'tc-old',tcSetId:'set-1',status:'CANCELED',completedCount:1,passCount:1,elapsedMs:125000,stale:false,snapshot:{productName:'검증용 제품',caseCount:3,groupCount:1,groups:[],targetUrl:'https://example.com',items:cases.map((tc,i)=>({caseId:tc.id,tc,executionStatus:i===0?'COMPLETED':i===1?'ERROR':'PENDING',judgmentStatus:'PENDING',result:i===0?'P':null,evidence:[]}))}};
  const captures=Array.from({length:33},(_,i)=>({pageIndex:i,url:'https://example.com',title:'화면 '+i,screenState:{readiness:i<31?'READY':'LOAD_TIMEOUT',screenKey:i<31?'screen-'+(i%23):'diagnostic-'+i},transition:{menuKey:'menu-'+i,outcome:i<31?'CONFIRMED':'LOAD_TIMEOUT'}}));
  const oldRun=structuredClone(run),errors=[];
  const browser=await chromium.launch({headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(12000);page.on('pageerror',e=>errors.push(e.message));
    await page.route('**/api/**',async route=>{
      const req=route.request(),path=new URL(req.url()).pathname;
      const json=data=>route.fulfill({contentType:'application/json',body:JSON.stringify(data)});
      if(path.endsWith('/tls-policy'))return json({ignoreHTTPSErrors:false});
      if(path==='/api/submissions/progress')return json({submissionId:'progress',documents:[],aiDelivery:{status:'DELIVERED',preflight:{decision:'READY',warnings:[],generationGate:{allowed:true}}}});
      if(path.endsWith('/menu-tree'))return json({nodes:[{nodeId:'group',title:'작업 관리',kind:'GROUP',status:'GROUP',url:'',sources:[],children:[{nodeId:'list',title:'목록',kind:'LINK',status:'CAPTURED',url:'',sources:[],children:[]}]}],nodeCount:2,pagesWithoutNavigation:0,truncated:false,crawlStatus:crawl.status});
      if(path.endsWith('/playwright-setup'))return json({setup:{targetUrl:'https://example.com',loginMethod:'NONE',automation:{},stale:false}});
      if(path==='/api/crawl-agents')return json({agents:[]});
      if(path.endsWith('/playwright-crawls'))return json({job:crawl,pages:captures,checks:{}});
      if(path.endsWith('/crawl-old/cancel')){crawl={...crawl,status:'CANCELED',resumeAvailable:true};return json({job:crawl,pages:captures,checks:{}});}
      if(path.endsWith('/crawl-old/resume')){assert.equal(req.postDataJSON().executionMode,'LOCAL');crawl={...crawl,jobId:'crawl-next',status:'RUNNING',resumeAvailable:false,crawlResult:{...crawl.crawlResult,resumedFrom:'crawl-old',preservedCount:3,progress:{stage:'NAVIGATING',current:'설정 → 조회',remaining:2}}};return json({job:crawl,pages:captures,checks:{}});}
      if(path.endsWith('/menu-test-cases'))return json({set:{setId:'set-1',output:{...output,testCases:cases}},history:[]});
      const view=()=>({run,history:[{runId:run.runId,status:run.status,createdAt:new Date().toISOString()}],executorConnected:true,judgeConnected:true});
      if(path.endsWith('/executions'))return json(view());
      if(path.endsWith('/tc-old/resume')){run={...run,runId:'tc-next',status:'PREPARED',completedCount:0,passCount:0,elapsedMs:0,snapshot:{...run.snapshot,resumedFrom:'tc-old',preservedCount:1,priorElapsedMs:125000,caseCount:2,items:oldRun.snapshot.items.slice(1).map(item=>({...item,executionStatus:'PENDING'}))}};return json(view());}
      if(path.endsWith('/tc-next/start')){run={...run,status:'RUNNING',snapshot:{...run.snapshot,currentCaseId:cases[1].id,progress:{stage:'JUDGING',round:1}}};return json(view());}
      throw Error('Unexpected fixture request '+path);
    });
    await page.goto(process.env.FRONTEND_QA_URL||'http://127.0.0.1:4173/');
    await page.getByLabel('문서 세트 ID').fill('progress');await page.getByRole('button',{name:'세트 조회',exact:true}).click();
    await page.getByRole('button',{name:'02 화면 수집',exact:true}).click();
    const collection=page.locator('.run-progress:visible');await collection.getByText('화면 로딩 대기 · 작업 관리 → 목록',{exact:true}).waitFor();
    assert.match(await collection.locator('.run-clock b').innerText(),/00:01:0[5-9]/);
    const seconds=async()=>{const parts=(await collection.locator('.run-clock b').innerText()).split(':').map(Number);return parts[0]*3600+parts[1]*60+parts[2];};
    const first=await seconds();await page.waitForTimeout(6200);assert(await seconds()>=first+6,'Clock must cross a five-second polling boundary without resetting');
    crawl={...crawl,elapsedMs:0};const beforeZero=await seconds();await page.waitForTimeout(5200);assert(await seconds()>=beforeZero+5,'Stale zero elapsed sample must not reset the clock');
    assert(await collection.evaluate(el=>el.classList.contains('is-active')));
    for(const [label,value] of [['저장 캡처','33'],['정상 확인 캡처','31'],['진단 캡처','2'],['상태 정보 없는 캡처','0']])assert.equal(await collection.locator('.run-progress-metrics>div').filter({has:page.getByText(label,{exact:true})}).locator('b').innerText(),value);
    await collection.getByText(/고유 화면 23개 · 동일 상태 재캡처 8개/).waitFor();
    await collection.getByText(/캡처 전 이동 실패 1건은 저장 수와 별도/).waitFor();
    const tree=page.locator('.captured-menu-panel');assert.equal(await tree.getAttribute('open'),null);await tree.locator(':scope > summary').click();await tree.getByText('작업 관리',{exact:true}).waitFor();await tree.locator(':scope > summary').click();assert.equal(await tree.getAttribute('open'),null);
    await fs.mkdir('output/test-execution',{recursive:true});await collection.screenshot({path:'output/test-execution/progress-crawl.png'});
    await page.getByRole('button',{name:'수집 중단',exact:true}).click();
    await collection.getByText('수집 중단 · 결과 보존됨',{exact:true}).waitFor();assert(await collection.evaluate(el=>el.classList.contains('is-danger')));
    const frozen=await collection.locator('.run-clock b').innerText();await page.waitForTimeout(1100);assert.equal(await collection.locator('.run-clock b').innerText(),frozen);
    crawl={...crawl,status:'FAILED',errorCode:'CRAWL_FAILED',crawlResult:{...crawl.crawlResult,skipped:[{path:['메뉴','Jobs'],stage:'WAIT_SCREEN',reason:'ERR_BLOCKED_BY_CLIENT'}]}};
    await collection.getByText(/중단 사유:.*CRAWL_FAILED/).waitFor();
    await collection.getByText(/마지막 진행 위치: 작업 관리 → 목록/).waitFor();
    await page.getByText(/메뉴 → Jobs.*실행기의 네트워크 정책/).waitFor();
    crawl={...crawl,status:'COMPLETED',stopReason:'DONE',errorCode:null};
    await collection.getByText('수집 종료 · 미완료 항목 있음',{exact:true}).waitFor();
    assert(await collection.evaluate(el=>el.classList.contains('is-danger')),'Unresolved menus must not be shown as normal success');
    await page.getByRole('button',{name:'미완료 화면 이어서 수집',exact:true}).click();await collection.getByText(/이전 화면 3개 보존/).waitFor();crawl={...crawl,status:'COMPLETED',stopReason:'DONE',errorCode:null,crawlResult:{...crawl.crawlResult,navigationFailureCount:0,incompleteCount:0,noChangeCount:0,unresolvedCount:0,skipped:[]}};await page.waitForFunction(()=>!!document.querySelector('.run-progress.is-success'));await collection.getByText('탐색 정상 종료',{exact:true}).waitFor();await collection.screenshot({path:'output/test-execution/progress-crawl-complete.png'});const completedClock=await collection.locator('.run-clock b').innerText();await page.reload();await page.getByLabel('문서 세트 ID').fill('progress');await page.getByRole('button',{name:'세트 조회',exact:true}).click();await page.getByRole('button',{name:'02 화면 수집',exact:true}).click();await collection.getByText('탐색 정상 종료',{exact:true}).waitFor();assert.equal(await collection.locator('.run-clock b').innerText(),completedClock,'Completed clock survives reload without elapsedMs from server');
    await page.getByRole('button',{name:'05 시험 실행',exact:true}).click();
    const execution=page.getByRole('region',{name:'시험 실행 관리'});
    await execution.getByRole('button',{name:'미완료만 이어서 준비',exact:true}).click();
    await execution.getByText(/이전 실행 결과 1개 보존/).waitFor();assert.equal(run.snapshot.items.length,2);assert.equal(oldRun.snapshot.items[0].result,'P');
    await execution.getByRole('button',{name:'실행 요청',exact:true}).click();
    await execution.getByText(/Jev가 수행 증거 판정 중/).waitFor();
    await execution.locator('.run-progress').screenshot({path:'output/test-execution/progress-execution.png'});
    await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await execution.locator('.run-progress').screenshot({path:'output/test-execution/progress-mobile.png'});
    assert.deepEqual(errors,[]);console.log('PASS: clock survives repeated polling and stale zero samples, correct 33=31+2 capture accounting, tree toggle, orange/red/green lifecycle, resume and mobile layout.');
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
