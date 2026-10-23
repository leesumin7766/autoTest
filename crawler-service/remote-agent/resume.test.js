const {test}=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const {crawl}=require('./walker');

test('resume preserves confirmed captures and discovers remaining pages with live stage updates', {timeout:60000}, async()=>{
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(`<title>${req.url}</title><h1>${req.url}</h1>${req.url==='/'?'<nav><a href="/a">화면 A</a><a href="/b">화면 B</a></nav>':'<p>조회 결과</p>'}`);});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const job={targetUrl:`http://127.0.0.1:${server.address().port}/`,loginMethod:'NONE',automation:{allowedOrigins:[],includePaths:[],excludePaths:[],maxPages:2,maxDepth:3,maxDurationSeconds:45,pageLoadTimeoutSeconds:5,viewportWidth:1000,viewportHeight:800}};
  try{
    const initial=[],stages=[];
    const stopped=await crawl(job,{onPage:async page=>initial.push(page),onProgress:async progress=>stages.push(progress.stage)});
    assert.equal(stopped.stopReason,'PAGE_LIMIT');assert.equal(initial.length,2);
    const added=[];
    const finished=await crawl({...job,automation:{...job.automation,maxPages:6},resume:{pageCount:initial.length,bytes:initial.reduce((n,p)=>n+Buffer.from(p.screenshot,'base64').length,0),pages:initial.map(p=>({pageIndex:p.pageIndex,screenState:p.screenState,transition:p.transition}))}},{onPage:async page=>added.push(page)});
    assert.equal(finished.stopReason,'DONE');assert.equal(added.length,1);assert.equal(added[0].pageIndex,2);assert.ok(added[0].url.endsWith('/b'));
    assert.equal(finished.uniqueScreenCount,3);assert.equal(finished.unresolvedCount,0);
    for(const stage of ['CONNECTING','NAVIGATING','LOADING','CAPTURING'])assert.ok(stages.includes(stage));
  }finally{await new Promise(resolve=>server.close(resolve));}
});
