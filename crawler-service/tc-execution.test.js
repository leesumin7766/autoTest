const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { open, step, sessions, safeUrl } = require('./tc-execution');

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
