const {test}=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const {crawl}=require('./walker');

test('a non-HTTP error destination is recorded without aborting other menus',{timeout:45000},async()=>{
 const server=http.createServer((req,res)=>{
  res.setHeader('Content-Type','text/html; charset=utf-8');
  res.end(req.url==='/good'?'<h1>정상 화면</h1>':'<h1>시작</h1><nav><button onclick="location.href=\'about:blank\'">오류 화면</button><a href="/good">정상 메뉴</a></nav>');
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const pages=[];
 try{
  const result=await crawl({targetUrl:`http://127.0.0.1:${server.address().port}/`,loginMethod:'NONE',automation:{allowedOrigins:[],includePaths:[],excludePaths:[],maxPages:10,maxDepth:2,maxDurationSeconds:35,pageLoadTimeoutSeconds:3,viewportWidth:900,viewportHeight:700}}, {onPage:async p=>pages.push(p)});
  assert.equal(result.stopReason,'DONE');
  assert.ok(pages.some(p=>p.url.endsWith('/good')));
  assert.equal(result.navigationFailureCount,1);
  assert.equal(result.coverageStatus,'INCOMPLETE');
  assert.equal(result.skipped[0].reason,'CONNECTION_SCOPE_BLOCKED');
  assert.ok(result.skipped[0].path.includes('오류 화면'));
 }finally{await new Promise(resolve=>server.close(resolve))}
});

test('capture context loss preserves masking and skips only the unstable screen',{timeout:45000},async()=>{
 const {chromium}=require('playwright');
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(req.url==='/'?'<h1>시작</h1><nav><a href="/unstable">전환 화면</a><a href="/good">정상 메뉴</a></nav>':`<h1>${req.url}</h1>`)});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const pages=[];let attempts=0,sourceAttempts=0;
 const browserType={launch:async options=>{
  const browser=await chromium.launch(options),context=browser.newContext.bind(browser);
  browser.newContext=async options=>{
   const ctx=await context(options),newPage=ctx.newPage.bind(ctx);
   ctx.newPage=async()=>{const page=await newPage(),screenshot=page.screenshot.bind(page),goto=page.goto.bind(page);page.goto=async(...args)=>{if(++sourceAttempts===2)throw Error('Navigation interrupted by another navigation');return goto(...args)};page.screenshot=async options=>{assert.ok(Array.isArray(options.mask));if(++attempts===2)throw Error('Execution context was destroyed, most likely because of a navigation');return screenshot(options)};return page};return ctx;
  };return browser;
 }};
 try{
  const result=await crawl({targetUrl:`http://127.0.0.1:${server.address().port}/`,loginMethod:'NONE',automation:{allowedOrigins:[],includePaths:[],excludePaths:[],maxPages:10,maxDepth:2,maxDurationSeconds:35,pageLoadTimeoutSeconds:3,viewportWidth:900,viewportHeight:700}}, {browserType,onPage:async p=>pages.push(p)});
  assert.equal(result.stopReason,'DONE');assert.equal(result.coverageStatus,'INCOMPLETE');
  assert.ok(pages.some(p=>p.url.endsWith('/good')));assert.ok(!pages.some(p=>p.url.endsWith('/unstable')));
  assert.equal(result.skipped[0].reason,'CAPTURE_CONTEXT_CHANGED');assert.equal(result.unresolvedCount,1);
  assert.ok(sourceAttempts>=3);assert.equal(result.navigationFailureCount,1,'A recovered source navigation must not be marked failed');
 }finally{await new Promise(resolve=>server.close(resolve))}
});
