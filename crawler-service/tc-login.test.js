const {test}=require('node:test');const assert=require('node:assert/strict');const http=require('node:http');
const {open,step,sessions}=require('./tc-execution');
test('saved-account login fills both fields, submits once, masks secrets and blocks raw/repeated submission',async()=>{
 const received=[];const secret='fixture-password-!42';
 const server=http.createServer(async(req,res)=>{
  res.setHeader('Content-Type','text/html; charset=utf-8');
  if(req.method==='POST'){
   let body='';for await(const chunk of req)body+=chunk;received.push(new URLSearchParams(body));res.writeHead(302,{Location:'/dashboard'});return res.end();
  }
  if(req.url==='/dashboard')return res.end(`<nav><a href="/logout">로그아웃</a></nav><h1>Dashboard</h1><p>Do not expose ${secret}</p>`);
  res.end('<h1>Login</h1><form method="post" action="/login"><input name="username" aria-label="Username"><input type="password" name="password"><button>Login</button></form>');
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));let opened;
 try{
  opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}/`,testRuleId:'LOGIN-04',password:secret,setup:{loginMethod:'FORM_AUTO',automation:{username:'fixture'}}});const s=sessions.get(opened.sessionId);
  assert.equal(received.length,0);assert.equal(opened.observation.authentication.savedAccountAvailable,true);
  const raw=await step(s,[{action:'click',elementId:opened.observation.elements.find(e=>e.tag==='button').id}]);assert.equal(raw.records[0].errorCode,'EXECUTION_LOGIN_REQUIRES_SAVED_ACCOUNT');assert.equal(received.length,0);
  const result=await step(s,[{action:'login_saved_account'}]);assert.equal(result.records[0].status,'COMPLETED');assert.equal(result.records[0].authenticationOutcome,'AUTHENTICATED');
  assert.deepEqual(result.records[0].authenticationSteps,['USERNAME_FILLED','PASSWORD_FILLED','SUBMIT_ATTEMPTED','SUBMITTED','AUTHENTICATED']);
  assert.equal(received.length,1);assert.equal(received[0].get('username'),'fixture');assert.equal(received[0].get('password'),secret);assert.equal(result.observation.entryState.authenticationState,'AUTHENTICATED_BY_TEST');assert.equal(JSON.stringify(result).includes(secret),false);
  const repeat=await step(s,[{action:'login_saved_account'}]);assert.equal(repeat.records[0].errorCode,'EXECUTION_LOGIN_REPEAT_BLOCKED');assert.equal(received.length,1);
 }finally{if(opened){await sessions.get(opened.sessionId)?.browser.close();sessions.delete(opened.sessionId)}await new Promise(r=>server.close(r))}
});

test('missing credentials and cross-origin credential forms never submit a password',async()=>{
 let received=0;const server=http.createServer((req,res)=>{if(req.method==='POST')received++;res.setHeader('Content-Type','text/html');res.end('<form method="post" action="http://example.invalid/login"><input name="username"><input type="password"><button>Login</button></form>')});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const ids=[];
 try{
  for(const password of [undefined,'fixture-secret']){
   const opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}/`,testRuleId:'LOGIN-04',password,setup:{loginMethod:'FORM_AUTO',automation:{username:'fixture'}}});ids.push(opened.sessionId);
   const result=await step(sessions.get(opened.sessionId),[{action:'login_saved_account'}]);assert.equal(result.records[0].status,'ERROR');assert.equal(result.records[0].errorCode,password?'EXECUTION_SCOPE_BLOCKED':'EXECUTION_INPUT_CONTEXT_REQUIRED');assert.equal(received,0);
  }
 }finally{for(const id of ids){await sessions.get(id)?.browser.close();sessions.delete(id)}await new Promise(r=>server.close(r))}
});

test('rejected login preserves the first response without authentication success or resubmission',async()=>{
 let posts=0;const server=http.createServer((req,res)=>{
  if(req.method==='POST')posts++;
  res.setHeader('Content-Type','text/html; charset=utf-8');res.end(`${req.method==='POST'?'<p data-result>Login denied</p>':''}<form method="post" action="/login"><input name="username"><input type="password" name="password"><button>Login</button></form>`);
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));let opened;
 try{
  opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}/`,testRuleId:'LOGIN-04',password:'fixture-secret',setup:{loginMethod:'FORM_AUTO',automation:{username:'fixture',successSelector:'[data-result]'}}});const s=sessions.get(opened.sessionId);
  const result=await step(s,[{action:'login_saved_account'}]);assert.equal(result.records[0].status,'COMPLETED');assert.equal(result.records[0].authenticationOutcome,'UNCONFIRMED');assert.equal(result.records[0].authenticationError,'LOGIN_FAILED');assert.ok(!result.records[0].authenticationSteps.includes('AUTHENTICATED'));assert.match(result.observation.text,/Login denied/);
  const repeat=await step(s,[{action:'login_saved_account'}]);assert.equal(repeat.records[0].errorCode,'EXECUTION_LOGIN_REPEAT_BLOCKED');assert.equal(posts,1);
 }finally{if(opened){await sessions.get(opened.sessionId)?.browser.close();sessions.delete(opened.sessionId)}await new Promise(r=>server.close(r))}
});
