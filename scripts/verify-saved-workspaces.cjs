// Real Chromium + isolated API fixture. No product account, model call or operational DB mutation.
const {chromium}=require('../crawler-service/node_modules/playwright');
const assert=require('node:assert/strict');const fs=require('node:fs/promises');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const a='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',b='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';let forbidB=false,starts=0;
  const workspace=id=>({id,productId:id===a?11:22,name:id===a?'제품 A 시험':'제품 B 시험',lastPage:'documents',createdAt:'2026-10-28T00:00:00Z',updatedAt:'2026-10-28T00:00:00Z',documentCount:2,parsedDocumentCount:2,deliveryStatus:'DELIVERED',crawlCount:1,menuCount:1,tcSetCount:1,runCount:1});
  const saved={[a]:workspace(a),[b]:workspace(b)};
  const delivery={status:'DELIVERED',attempts:1,retryable:false,preflight:{decision:'READY',warnings:[],generationGate:{allowed:true}}};
  const document=(id,role)=>({fileId:id+role,role,fileType:'PDF',format:'PDF',originalFilename:`${id===a?'제품A':'제품B'}-${role}.pdf`,storedPath:'fixture',extractedText:'저장된 문서 원문',status:'PARSED',failureReason:null});
  const tc=id=>({id:'case-'+id,tcId:id===a?'TC-A':'TC-B',featureId:'feature',featurePath:[saved[id].name,'조회'],featureType:'CRUD',qualityCharacteristic:'기능적합성',subCharacteristic:'기능정확성',scenario:'목록 조회',expectedResult:'목록이 표시된다.',scopeDescription:'조회',inputs:'시험 입력',ruleId:'CRUD-01',evidence:[],defectExample:{summary:'예시',severity:'M',content:'예시'},executionPlan:{targetUrl:'https://fixture.invalid/',steps:['조회'],requiredContext:[],applicability:'조회'}});
  const set=id=>({setId:'set-'+id,stale:false,menuRevision:1,output:{productName:saved[id].name,featureCount:1,caseCount:1,templateVersion:'fixture',summary:{},testCases:[tc(id)]}});
  await page.route('**/api/**',async route=>{
   const request=route.request(),url=new URL(request.url()),p=url.pathname;const json=value=>route.fulfill({contentType:'application/json',body:JSON.stringify(value)});
   if(p==='/api/crawl-agents/local/status')return json({connected:true,busy:false,jobs:[]});
   if(p==='/api/submissions/workspaces')return json({workspaces:Object.values(saved),hasMore:false});
   const id=p.split('/')[3];if(!saved[id])throw Error('Unexpected fixture route '+p);
   if(p.endsWith('/workspace')){
    if(id===b&&forbidB)return route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({code:'RESOURCE_NOT_FOUND'})});
    if(request.method()==='PATCH')Object.assign(saved[id],request.postDataJSON());
    return json(saved[id]);
   }
   if(p===`/api/submissions/${id}`)return json({submissionId:id,status:'PARSED',documents:[document(id,'FUNCTION_LIST'),document(id,'MANUAL')],aiDelivery:delivery});
   if(p.endsWith('/functional-menu'))return json({job:null,history:[],crawls:[]});
   if(p.endsWith('/menu-test-cases'))return json({set:set(id),history:[{setId:'set-'+id,menuRevision:1,createdAt:saved[id].createdAt}]});
   if(p.endsWith('/playwright-setup/tls-policy'))return json({ignoreHTTPSErrors:false});
   if(p.endsWith('/playwright-setup'))return json({setup:null});
   if(p.endsWith('/executions'))return json({run:null,history:[],executorConnected:true,judgeConnected:true,planner:'fixture'});
   if(request.method()==='POST')starts++;
   throw Error('Unexpected mutation '+p);
  });
  const navigate=label=>page.getByRole('button',{name:label,exact:true}).click();
  await page.goto(process.env.FRONTEND_QA_URL||'http://127.0.0.1:5181/');
  await page.getByRole('button',{name:'저장된 시험 작업 불러오기',exact:true}).click();
  await page.getByRole('button',{name:'제품 A 시험 불러오기',exact:true}).click();
  await page.getByText('제품A-FUNCTION_LIST.pdf',{exact:true}).waitFor();
  await navigate('04 TC 생성');await page.getByRole('cell',{name:'TC-A',exact:true}).waitFor();
  await page.waitForFunction(()=>localStorage.getItem('autotest:active-workspace:v1:1')==='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
  await page.waitForTimeout(600);assert.equal(saved[a].lastPage,'tc-generate');
  await page.reload();await page.getByRole('cell',{name:'TC-A',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'04 TC 생성',exact:true}).getAttribute('aria-current'),'page');
  await page.getByLabel('시험 작업 이름',{exact:true}).fill('제품 A 회귀 시험');await page.getByRole('button',{name:'이름 저장',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.workspace-tab-caption')?.textContent==='제품 A 회귀 시험');assert.equal(saved[a].name,'제품 A 회귀 시험');
  await navigate('05 시험 실행');await page.waitForFunction(id=>document.querySelector('[aria-label="실행할 TC 세트"]')?.value===`set-${id}`,a);
  await page.waitForTimeout(500);await page.reload();await page.getByRole('heading',{name:'시험 실행',exact:true}).first().waitFor();
  assert.equal(await page.getByRole('button',{name:'05 시험 실행',exact:true}).getAttribute('aria-current'),'page');
  forbidB=true;await page.getByRole('button',{name:'저장된 시험 작업 불러오기',exact:true}).click();await page.getByRole('button',{name:'제품 B 시험 불러오기',exact:true}).click();
  await page.locator('.saved-workspace-dialog [role="alert"]').waitFor();assert.equal(await page.getByLabel('시험 작업 이름',{exact:true}).inputValue(),'제품 A 회귀 시험');
  forbidB=false;await page.getByRole('button',{name:'제품 B 시험 불러오기',exact:true}).click();await page.getByText('제품B-FUNCTION_LIST.pdf',{exact:true}).waitFor();
  assert.equal(await page.getByText('제품A-FUNCTION_LIST.pdf',{exact:true}).count(),0);
  await navigate('04 TC 생성');await page.getByRole('cell',{name:'TC-B',exact:true}).waitFor();assert.equal(await page.getByRole('cell',{name:'TC-A',exact:true}).count(),0);
  await page.getByRole('button',{name:'＋ 새 문서 세트',exact:true}).click();await page.reload();await page.getByLabel('시험 작업 이름',{exact:true}).waitFor();
  assert.equal(await page.getByLabel('시험 작업 이름',{exact:true}).inputValue(),'새 시험 작업');assert.equal(await page.getByRole('cell',{name:'TC-B',exact:true}).count(),0);
  await page.getByLabel('문서 세트 ID',{exact:true}).fill(a);await page.getByRole('button',{name:'세트 조회',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.workspace-tab-caption')?.textContent==='제품 A 회귀 시험');
  assert.equal(starts,0);assert.deepEqual(errors,[]);
  await fs.mkdir('outputs/saved-workspaces',{recursive:true});await page.getByRole('button',{name:'저장된 시험 작업 불러오기',exact:true}).click();await page.getByRole('button',{name:'제품 A 회귀 시험 불러오기',exact:true}).waitFor();await page.screenshot({path:'outputs/saved-workspaces/workspace-list.png',fullPage:true});
  console.log(JSON.stringify({passed:true,checks:['load saved documents','restore TC and page after refresh','rename persists','restore execution tab','forbidden load preserves current context','switch isolates artifacts','new workspace clears remembered selection','legacy ID lookup','no automatic execution'],automaticExecutionRequests:starts,pageErrors:errors},null,2));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
