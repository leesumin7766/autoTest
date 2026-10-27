// Run inside the development crawler container. No credentials are printed or submitted.
const assert=require('node:assert/strict');
(async()=>{
 const submission=process.argv[2]||'991e5ed2-3281-494b-a350-3fea864f9cfd';
 const setupResponse=await fetch(`http://test-management-service:8082/api/submissions/${submission}/playwright-setup`,{headers:{'X-Member-Id':'1'}});
 assert.equal(setupResponse.status,200);const {setup}=await setupResponse.json();
 const headers={'X-Internal-Token':process.env.PRODUCT_DESCRIPTION_INTERNAL_TOKEN,'Content-Type':'application/json'};
 let session;
 try{
  const response=await fetch('http://127.0.0.1:8006/api/v1/tc-browser/open',{method:'POST',headers,body:JSON.stringify({targetUrl:setup.targetUrl,setup,initialEntry:true,allowMutation:false})});
  const value=await response.json();assert.equal(response.status,200,value.code);session=value.sessionId;
  const state=value.observation.entryState;
  assert.equal(state.initialScreenReady,true);assert.equal(state.sessionMode,'FRESH_ANONYMOUS');assert.equal(state.authenticationState,'LOGIN_FORM_OBSERVED');
  console.log(JSON.stringify({passed:true,entryState:state,passwordFieldObserved:value.observation.elements.some(e=>e.type==='password')}));
 }finally{
  if(session){const response=await fetch(`http://127.0.0.1:8006/api/v1/tc-browser/${session}/close`,{method:'POST',headers,body:'{}'});assert.equal(response.status,200);console.log('Verification session closed');}
 }
})().catch(error=>{console.error(error.message);process.exitCode=1});
