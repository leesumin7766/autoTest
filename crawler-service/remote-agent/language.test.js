const { test }=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const {chromium}=require('playwright');
const {preferKorean,languagePolicy}=require('./language');
const {crawl}=require('./walker');

test('explicit Korean control switches once and absent or ambiguous controls do not invent success',{timeout:30000},async()=>{
 const browser=await chromium.launch({headless:true});
 try {
  const context=await browser.newContext({locale:'ko-KR'}),page=await context.newPage();
  assert.equal(await page.evaluate(()=>navigator.language),'ko-KR');
  await page.setContent('<html lang="en"><main><h1>Dashboard overview and system performance statistics</h1><button onclick="document.querySelector(\'h1\').textContent=\'대시보드 작업 실행 현황과 시스템 상태 확인\'">한국어</button></main></html>');
  const korean=await preferKorean(page,languagePolicy());assert.equal(korean.status,'KOREAN_CONFIRMED');assert(korean.switched);
  const again=await preferKorean(page,languagePolicy());assert.equal(again.status,'KOREAN_CONFIRMED');assert(!again.switched);
  await page.setContent('<html lang="en"><main><h1>Dashboard overview and system performance statistics</h1></main></html>');
  assert.equal((await preferKorean(page,languagePolicy())).status,'UNCONFIRMED');
  await page.setContent('<html lang="en"><main><h1>Dashboard overview and system performance statistics</h1><p>한국어로 작성된 고객 데이터 내용은 화면 언어 판정 근거가 아닙니다.</p></main></html>');
  assert.equal((await preferKorean(page,languagePolicy())).detected,'en');
  await page.setContent('<main><h1>Dashboard overview and system performance statistics</h1><button>한국어</button><button>한국어</button></main>');
  assert.equal((await preferKorean(page,languagePolicy())).status,'UNCONFIRMED');
 }finally{await browser.close();}
});
test('resume policy does not silently change legacy or English captures',()=>{
 assert(languagePolicy().preferKorean);
 assert.equal(languagePolicy({pageCount:2,pages:[]}).browserLocale,undefined);
 assert(!languagePolicy({pageCount:2,pages:[]}).preferKorean);
 assert(!languagePolicy({pageCount:1,pages:[{transition:{collectionLanguage:{detected:'en',browserLocale:'ko-KR'}}}]}).preferKorean);
});
test('crawl captures Korean after local language switch and restores it on menu replay',{timeout:45000},async()=>{
 let accept='';const pages=[];
 const server=http.createServer((req,res)=>{
  accept=req.headers['accept-language']||'';res.setHeader('Content-Type','text/html; charset=utf-8');
  res.end(`<html lang="en"><main><h1>Dashboard overview and system performance statistics</h1><nav><a href="/jobs" id="jobs">Jobs</a></nav><button id="lang" onclick="localStorage.setItem('appLang','ko');render()">한국어</button></main><script>function render(){if(localStorage.getItem('appLang')==='ko'){document.documentElement.lang='ko';document.querySelector('h1').textContent='작업 실행 현황과 시스템 상태를 확인하는 화면';document.querySelector('#jobs').textContent='작업 목록';document.querySelector('#lang').textContent='English';}}render();</script></html>`);
 });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try {
  const result=await crawl({targetUrl:`http://127.0.0.1:${server.address().port}/`,loginMethod:'NONE',automation:{allowedOrigins:[],includePaths:[],excludePaths:[],maxPages:3,maxDepth:2,maxDurationSeconds:35,pageLoadTimeoutSeconds:3,viewportWidth:900,viewportHeight:700}},{onPage:async page=>pages.push(page)});
  assert.match(accept,/ko-KR/);assert.equal(result.collectionLanguage.status,'KOREAN_CONFIRMED');assert(result.collectionLanguage.switched);
  assert(pages.length>=2);for(const page of pages){assert(page.bodyText.includes('작업 실행 현황'));assert.equal(page.transition.collectionLanguage.detected,'ko');}
  assert(pages.some(page=>page.url.endsWith('/jobs')));
 }finally{await new Promise(resolve=>server.close(resolve));}
});
