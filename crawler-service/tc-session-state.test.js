const {test}=require('node:test');const assert=require('node:assert/strict');const http=require('node:http');
const {open,step,sessions}=require('./tc-execution');
const close=async opened=>{if(opened){await sessions.get(opened.sessionId)?.browser.close();sessions.delete(opened.sessionId)}};

test('login-form tests bypass setup login while logout tests begin authenticated',async()=>{
 let posts=0;const server=http.createServer((req,res)=>{
  res.setHeader('Content-Type','text/html');
  if(req.method==='POST'){posts++;res.writeHead(302,{Location:'/dashboard'});return res.end()}
  if(req.url==='/dashboard')return res.end('<nav><a href="/logout">Log out</a></nav><main><h1>Dashboard</h1></main>');
  res.end('<form method="post"><input name="username"><input name="password" type="password"><button>Login</button></form>');
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const base={targetUrl:`http://127.0.0.1:${server.address().port}/`,password:'fixture-secret',setup:{loginMethod:'FORM_AUTO',automation:{username:'fixture'}}};
 try{
  for(const testRuleId of ['LOGIN-03','LOGIN-05','LOGIN-06']){let opened;try{
   opened=await open({...base,testRuleId});assert.equal(posts,0);assert.equal(opened.observation.entryState.authenticationState,'LOGIN_FORM_OBSERVED');
   assert.equal(opened.observation.authentication.requiredStartState,'ANONYMOUS_LOGIN_FORM');assert.equal(opened.observation.authentication.startStateVerified,true);
  }finally{await close(opened)}}
  let opened;try{
   opened=await open({...base,testRuleId:'LOGIN-19'});assert.equal(posts,1);assert.equal(opened.observation.entryState.authenticationState,'AUTHENTICATED_BY_SETUP');
   const logout=opened.observation.elements.find(e=>e.name==='Log out');const result=await step(sessions.get(opened.sessionId),[{action:'click',elementId:logout.id}]);
   assert.equal(result.observation.entryState.authenticationState,'LOGIN_FORM_OBSERVED');
  }finally{await close(opened)}
 }finally{await new Promise(r=>server.close(r))}
});

test('anonymous login test refuses an unverified dashboard instead of submitting again',async()=>{
 const server=http.createServer((_,res)=>res.end('<main><h1>Dashboard</h1></main>'));await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try{await assert.rejects(open({targetUrl:`http://127.0.0.1:${server.address().port}/`,testRuleId:'LOGIN-05',setup:{loginMethod:'FORM_AUTO',automation:{username:'fixture'}},password:'fixture'}),{code:'EXECUTION_AUTH_START_NOT_CONFIRMED'})}
 finally{await new Promise(r=>server.close(r))}
});

test('opened dialog becomes the observation scope and excludes its background trigger',async()=>{
 const server=http.createServer((_,res)=>{res.setHeader('Content-Type','text/html');res.end(`<main><h1>Dashboard</h1><button onclick="document.querySelector('dialog').showModal()">Search</button></main><dialog><h2>Commands</h2><input aria-label="Command"><button onclick="document.querySelector('dialog').close()">Close</button></dialog>`)});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let opened;
 try{
  opened=await open({targetUrl:`http://127.0.0.1:${server.address().port}/`});const trigger=opened.observation.elements.find(e=>e.name==='Search');
  const result=await step(sessions.get(opened.sessionId),[{action:'click',elementId:trigger.id}]);assert.equal(result.records[0].status,'COMPLETED');
  assert.equal(result.observation.activeLayer.role,'dialog');assert.match(result.observation.mainText,/Commands/);
  assert.ok(!result.observation.elements.some(e=>e.name==='Search'));assert.ok(result.observation.elements.some(e=>e.name==='Close'));
 }finally{await close(opened);await new Promise(r=>server.close(r))}
});
