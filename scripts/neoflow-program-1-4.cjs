// Execute the public application APIs; never auto-select hidden AUTO menu generation.
const fs=require('node:fs/promises');
const path=require('node:path');
const crypto=require('node:crypto');
const out='outputs/neoflow-program-1-4';
const submission='77ad4076-6afc-496e-b975-4c9b5231758e';
const base=`http://127.0.0.1:8080/api/submissions/${submission}`;
const headers={'X-Member-Id':'1'};
async function api(suffix,method='GET',body){const r=await fetch(base+suffix,{method,headers:{...headers,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});const b=await r.json();if(!r.ok)throw Error(`${r.status} ${b.code||''} ${b.message||''}`);return b;}
(async()=>{
 await fs.mkdir(out,{recursive:true});let state;
 try{state=JSON.parse(await fs.readFile(`${out}/workflow.json`));}catch{state={submissionId:submission,startedAt:new Date().toISOString()};}
 const save=()=>fs.writeFile(`${out}/workflow.json`,JSON.stringify(state,null,2));
 const action=process.argv[2]||'status';
 if(action==='documents'){
  const b=await api('');const docs=[];
  for(const d of b.documents||[]){if(!['FUNCTION_LIST','MANUAL'].includes(d.role))continue;
   const local=path.join('sample/sample3',d.originalFilename);const bytes=await fs.readFile(local);
   docs.push({role:d.role,filename:d.originalFilename,status:d.status,localBytes:bytes.length,localSha256:crypto.createHash('sha256').update(bytes).digest('hex')});
  }
  if(docs.length!==2||docs.some(d=>d.status!=='PARSED')||b.aiDelivery?.status!=='DELIVERED')throw Error('Required documents are not parsed/delivered');
  await fs.writeFile(`${out}/01-documents.json`,JSON.stringify({method:'existing uploaded documents reused; PARSED/DELIVERED verified',documents:docs,aiDelivery:b.aiDelivery?.status},null,2));state.documentsVerifiedAt=new Date().toISOString();await save();console.log('Documents verified:',docs.map(d=>d.role).join(', '));
 }
 if(action==='crawl'){
  if(state.crawlJobId)throw Error('This workflow already has a crawl; use status');
  const b=await api('/playwright-crawls','POST',{executionMode:'LOCAL',purpose:'CRAWL'});state.crawlJobId=b.job.jobId;await save();console.log('Crawl requested',state.crawlJobId);
 }
 if(action==='status'){
  if(!state.crawlJobId)throw Error('No crawl ID');const b=await api('/playwright-crawls');
  const j=[b.job,...(b.jobs||[])].find(j=>j?.jobId===state.crawlJobId);if(!j)throw Error('Crawl not returned');
  const safe=Object.fromEntries(['jobId','status','createdAt','updatedAt','startedAt','finishedAt','elapsedMs','stopReason','errorCode','pageCount','crawlResult','progress'].filter(k=>j[k]!==undefined).map(k=>[k,j[k]]));
  await fs.writeFile(`${out}/02-crawl.json`,JSON.stringify(safe,null,2));console.log(JSON.stringify({id:j.jobId,status:j.status,elapsedMs:j.elapsedMs,stopReason:j.stopReason,errorCode:j.errorCode,pages:j.pageCount,progress:j.progress,result:j.crawlResult}));
 }
 if(action==='resume'){
  const previous=state.crawlJobId;const b=await api(`/playwright-crawls/${previous}/resume`,'POST',{executionMode:'LOCAL'});
  state.previousCrawlJobIds=[...(state.previousCrawlJobIds||[]),previous];state.crawlJobId=b.job.jobId;await save();console.log('Incomplete crawl resumed',state.crawlJobId);
 }
 if(action==='prompt'){
  if(!state.menuJobId){const b=await api('/functional-menu','POST',{crawlJobId:state.crawlJobId,productName:'NeoFlow Server',mode:'EXTERNAL'});state.menuJobId=b.job.jobId;state.menuRevision=b.job.revision;await save();}
  const b=await api(`/functional-menu/${state.menuJobId}/prompt`,'POST');await fs.writeFile(`${out}/03-request.json`,JSON.stringify(b,null,2));await fs.writeFile(`${out}/03-제품별프롬프트.txt`,b.prompt);console.log('External JSON input ready',state.menuJobId);
 }
 if(action==='import'){
  const b=JSON.parse(await fs.readFile(`${out}/03-response.json`));const r=await api(`/functional-menu/${state.menuJobId}/import`,'POST',{revision:state.menuRevision,requestId:b.requestId,rows:b.rows});
  state.menuRevision=r.job.revision;await save();await fs.writeFile(`${out}/03-menu.json`,JSON.stringify(r,null,2));console.log('Imported',r.job.status,r.job.output.rows.length);
 }
 if(action==='approve'){
  const b=await api(`/functional-menu?job=${state.menuJobId}`);if(b.job.output.completionStatus==='PARTIAL')throw Error('Partial menu cannot be approved');
  const r=await api(`/functional-menu/${state.menuJobId}/approval`,'PUT',{revision:b.job.revision,rows:b.job.output.rows});state.menuRevision=r.job.revision;await save();await fs.writeFile(`${out}/03-menu.json`,JSON.stringify(r,null,2));console.log('Approved',state.menuJobId);
 }
 if(action==='tc'){
  const b=await api('/menu-test-cases','POST',{menuJobId:state.menuJobId,featureTypes:{}});state.tcSetId=b.set.setId;await save();await fs.writeFile(`${out}/04-tc.json`,JSON.stringify(b,null,2));console.log(JSON.stringify({tcSetId:state.tcSetId,template:b.set.output.templateVersion,count:b.set.output.caseCount,classification:b.set.output.summary}));
 }
 if(action==='export'){
  for(const [suffix,name] of [[`/functional-menu/${state.menuJobId}/download`,'03-메뉴트리.xlsx'],[`/menu-test-cases/${state.tcSetId}/download`,'04-TC.xlsx']]){const r=await fetch(base+suffix,{headers});if(!r.ok)throw Error(`${name}: ${r.status}`);await fs.writeFile(`${out}/${name}`,Buffer.from(await r.arrayBuffer()));}console.log('Application Excel files exported');
 }
})().catch(e=>{console.error(e.message);process.exitCode=1;});
