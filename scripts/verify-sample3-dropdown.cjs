// Actual target, read-only post-fix check; no TC verdict is assigned.
const fs=require('node:fs/promises');
const {open,step,sessions}=require('/app/tc-execution');
(async()=>{
 const response=await fetch('http://test-management-service:8082/api/submissions/fb53af4b-add4-4b8f-86cf-0d9687d9109e/playwright-setup',{headers:{'X-Member-Id':'1'}});const {setup}=await response.json();
 let opened;
 try{
  opened=await open({targetUrl:setup.targetUrl,setup,password:process.env.AUTOTEST_PASSWORD,allowMutation:false});delete process.env.AUTOTEST_PASSWORD;
  const s=sessions.get(opened.sessionId);const nav=opened.observation.elements.find(el=>el.name==='Login History');if(!nav)throw Error('Observed menu missing');
  let result=await step(s,[{action:'click',elementId:nav.id}]);const trigger=result.observation.elements.find(el=>el.name==='All Statuses');if(!trigger)throw Error('Observed filter missing');
  result=await step(s,[{action:'click',elementId:trigger.id}]);const options=result.observation.elements.filter(el=>el.role==='option');if(!options.length)throw Error('No custom options observed');
  const option=options.find(el=>el.name==='All Statuses')||options[0];result=await step(s,[{action:'click',elementId:option.id}]);
  if(result.records.some(r=>r.status==='ERROR'))throw Error('Option action failed');
  const summary={checkedAt:new Date().toISOString(),postFix:true,method:'Actual crawler observation and action, no LLM verdict',url:result.observation.url,options:options.map(el=>el.name),selected:option.name,records:result.records.map(r=>({action:r.action,targetName:r.targetName,status:r.status,isNavigation:r.isNavigation})),blockedRequests:result.blockedRequests};
  await fs.writeFile('/tmp/sample3-reference/dropdown-postfix.json',JSON.stringify(summary,null,2));await fs.writeFile('/tmp/sample3-reference/dropdown-postfix.png',Buffer.from(result.observation.screenshot,'base64'));console.log(JSON.stringify(summary));
 }finally{if(opened){await sessions.get(opened.sessionId).browser.close();sessions.delete(opened.sessionId)}}
})().catch(error=>{console.error(error.message);process.exitCode=1});
