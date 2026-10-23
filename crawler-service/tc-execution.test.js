const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { open, step, sessions, safeUrl } = require('./tc-execution');

test('custom dropdown options and context-menu actions are observable test controls',async()=>{
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end('<p id="state">All</p><button onclick="document.querySelector(\'#choices\').hidden=false">All Statuses</button><div id="choices" role="listbox" hidden><div role="option" onclick="document.querySelector(\'#state\').textContent=\'Active\';document.querySelector(\'#choices\').hidden=true">Active</div></div><div role="menu"><div role="menuitem">Filter</div></div>')});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let opened;
  try{opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`});const s=sessions.get(opened.sessionId);let result=await step(s,[{action:'click',elementId:opened.observation.elements.find(el=>el.name==='All Statuses').id}]);const option=result.observation.elements.find(el=>el.role==='option'&&el.name==='Active');assert(option);result=await step(s,[{action:'click',elementId:option.id}]);assert.equal(result.records[0].isNavigation,false);assert.match(result.observation.text,/Active/);assert.equal(result.observation.elements.find(el=>el.role==='menuitem').navigation,false)}
  finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId)}await new Promise(resolve=>server.close(resolve))}
});

test('static dashboard progress bars do not represent loading',async()=>{
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end('<div role="progressbar" aria-valuenow="30">30%</div><div role="progressbar" aria-label="CPU">10%</div><button>Query</button>')});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let opened;
  try{opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`});assert(opened.observation.elements.some(el=>el.name==='Query'))}
  finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId)}await new Promise(resolve=>server.close(resolve))}
});

test('explicit busy progress bars still block unstable evidence',async()=>{
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end('<div role="progressbar" aria-busy="true">Loading</div>')});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{await assert.rejects(open({targetUrl:`http://127.0.0.1:${server.address().port}`,setup:{loginMethod:'NONE',automation:{pageLoadTimeoutSeconds:1}}}),{code:'EXECUTION_SCREEN_NOT_READY'})}
  finally{await new Promise(resolve=>server.close(resolve))}
});

test('same-origin iframe controls have unique frame-bound IDs',async()=>{
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(req.url==='/frame'?'<p id="state">프레임 대기</p><button onclick="document.querySelector(\'#state\').textContent=\'프레임 조회 완료\'">조회</button>':'<button>외부 버튼</button><iframe src="/frame"></iframe>')});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let opened;
  try{opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`});const s=sessions.get(opened.sessionId);const el=opened.observation.elements.find(el=>el.name==='조회');assert.equal(el.frameIndex,1);assert.equal(new Set(opened.observation.elements.map(el=>el.id)).size,opened.observation.elements.length);const result=await step(s,[{action:'click',elementId:el.id}]);assert.match(result.observation.text,/프레임 조회 완료/);assert.equal(result.records[0].frameIndex,1)}
  finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId)}await new Promise(resolve=>server.close(resolve))}
});

test('menu navigation is marked and asynchronous data is awaited before evidence',async()=>{
  const server=http.createServer((req,res)=>{
    res.setHeader('Content-Type','text/html; charset=utf-8');
    if(req.url==='/data'){setTimeout(()=>res.end('실제 조회 완료'),1800);return;}
    res.end('<nav><a href="/jobs">Jobs</a></nav><p id="status">조회 전</p><button onclick="fetch(\'/data\').then(r=>r.text()).then(t=>document.querySelector(\'#status\').textContent=t)">조회</button>');
  });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let opened;
  try{
    opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`});const s=sessions.get(opened.sessionId);
    let result=await step(s,[{action:'click',elementId:opened.observation.elements.find(el=>el.name==='Jobs').id}]);assert.equal(result.records[0].isNavigation,true);
    const started=Date.now();result=await step(s,[{action:'click',elementId:result.observation.elements.find(el=>el.name==='조회').id}]);
    assert.equal(result.records[0].isNavigation,false);assert.match(result.observation.text,/실제 조회 완료/);assert(Date.now()-started>=1800);
  }finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId)}await new Promise(resolve=>server.close(resolve))}
});

test('bounded actions use observed elements; mutations, credentials and cross-origin URLs are fenced', async () => {
  const server = http.createServer((_,res) => {res.setHeader('Content-Type','text/html; charset=utf-8');res.end('<h1>시험</h1><p id="count">개수: 0</p><button onclick="document.querySelector(\'#count\').textContent=\'개수: 1\'">조회</button><button>삭제</button><input type="password" aria-label="비밀번호">');});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  let session;
  try {
    const opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`,allowMutation:false});
    session=sessions.get(opened.sessionId);
    const result=await step(session,[{action:'click',elementId:opened.observation.elements.find(x=>x.name==='조회').id}]);
    assert.match(result.observation.text,/개수: 1/);
    const blocked=await step(session,[{action:'click',elementId:result.observation.elements.find(x=>x.name==='삭제').id}]);
    assert.equal(blocked.records[0].errorCode,'EXECUTION_MUTATION_NOT_ALLOWED');
    const invalid=await step(session,[{action:'click',elementId:'9999'}]);assert.equal(invalid.records[0].errorCode,'EXECUTION_ELEMENT_UNKNOWN');
    const secret=await step(session,[{action:'fill',elementId:session.observation.elements.find(x=>x.type==='password').id,value:'never-use'}]);
    assert.equal(secret.records[0].errorCode,'EXECUTION_INPUT_CONTEXT_REQUIRED');
    assert.throws(()=>safeUrl('http://external.example',session.origins));
  } finally {if(session){await session.browser.close();for(const [id,s] of sessions)if(s===session)sessions.delete(id);}await new Promise(resolve=>server.close(resolve));}
});

test('repeated writes retain first observation and reload exposes missing persistence', async () => {
  const server=http.createServer((_,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end('<p id="value">저장 전</p><button onclick="document.querySelector(\'#value\').textContent=\'저장 완료\'">저장</button>');});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let opened;
  try{
    opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`,allowMutation:true});const session=sessions.get(opened.sessionId);
    const result=await step(session,[{action:'click',elementId:'0'},{action:'click',elementId:'0'}]);
    assert.equal(result.records[0].status,'COMPLETED');assert.match(result.records[0].text,/저장 완료/);
    assert.equal(result.records[1].errorCode,'EXECUTION_REPEAT_REQUIRES_REVIEW');assert.match(result.records[0].beforeText,/저장 전/);
    const checked=await step(session,[{action:'reload'}]);assert.match(checked.observation.text,/저장 전/);
  }finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId);}await new Promise(resolve=>server.close(resolve));}
});

test('download text is collected rather than treating an event as data correctness', async()=>{
  const server=http.createServer((req,res)=>{if(req.url==='/data'){res.setHeader('Content-Disposition','attachment; filename=items.csv');res.end('id,value\n1,alpha-123');return;}res.setHeader('Content-Type','text/html');res.end('<a href="/data">Download</a>');});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let opened;
  try{opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}`});const result=await step(sessions.get(opened.sessionId),[{action:'click',elementId:'0'}]);assert.equal(result.downloads[0].status,'COMPLETED');assert.match(result.downloads[0].text,/alpha-123/);assert.equal(result.downloads[0].sha256.length,64);}
  finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId);}await new Promise(resolve=>server.close(resolve));}
});
