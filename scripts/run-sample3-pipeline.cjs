// Opt-in actual sample3 API workflow. Never stores login credentials.
const fs=require('node:fs/promises');
const path=require('node:path');
const crypto=require('node:crypto');
const root=path.resolve(__dirname,'..');
const out=path.join(root,'output','sample3-validation-20261022');
const base='http://127.0.0.1:8080/api/submissions';
const headers={'X-Member-Id':'1'};
async function api(url,method='GET',body){const r=await fetch(url,{method,headers:{...headers,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});if(!r.ok)throw Error(`${r.status}: ${await r.text()}`);return r.json()}
async function main(){
 await fs.mkdir(out,{recursive:true});
 const stateFile=path.join(out,'workflow.json');
 let state;try{state=JSON.parse(await fs.readFile(stateFile))}catch{state={startedAt:new Date().toISOString(),targetUrl:'https://211.233.211.228/',sources:[]}}
 const save=()=>fs.writeFile(stateFile,JSON.stringify(state,null,2));
 const action=process.argv[2]||'status';
 if(action==='upload'){
  if(state.submissionId)throw Error('Already uploaded: use status');
  const dir=path.join(root,'sample','sample3'),names=await fs.readdir(dir);
  for(const [role,name] of [['FUNCTION_LIST',names.find(n=>n.endsWith('.xlsx'))],['MANUAL',names.find(n=>n.includes('Server')&&n.endsWith('.pdf'))]]){
   const bytes=await fs.readFile(path.join(dir,name));const form=new FormData();form.append('file',new Blob([bytes],{type:name.endsWith('.pdf')?'application/pdf':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}),name);form.append('role',role);if(!state.submissionId)form.append('productId','1');
   const response=await fetch(state.submissionId?`${base}/${state.submissionId}/files`:base,{method:'POST',headers,body:form});
   if(!response.ok)throw Error(`${response.status}: ${await response.text()}`);const data=await response.json();
   state.submissionId ||= data.submissionId;state.sources.push({role,name,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});await save();
  }
 }
 if(!state.submissionId)throw Error('Upload first');
 const url=`${base}/${state.submissionId}`;
 if(action==='crawl'){
  const r=await api(`${url}/playwright-crawls`,'POST',{executionMode:'LOCAL',purpose:'CRAWL'});
  await fs.writeFile(path.join(out,'02-crawl.json'),JSON.stringify(r,null,2));
  state.crawlJobId=r.job?.jobId;await save();console.log(JSON.stringify({crawlJobId:state.crawlJobId}));return;
 }
 if(action==='crawl-status'){
  const r=await api(`${url}/playwright-crawls`);await fs.writeFile(path.join(out,'02-crawl.json'),JSON.stringify(r,null,2));
  const job=r.jobs?.find(j=>j.jobId===state.crawlJobId)||r.job;console.log(JSON.stringify(job?{...job,setup:undefined,setupSnapshot:undefined,crawlResult:job.crawlResult?{...job.crawlResult,pages:undefined}:undefined}:r));return;
 }
 if(action==='menu'){
  const crawl=await api(`${url}/playwright-crawls`);const completed=crawl.job?.jobId===state.crawlJobId?crawl.job:crawl.jobs?.find(j=>j.jobId===state.crawlJobId);
  if(!completed||completed.status!=='COMPLETED')throw Error('Crawl not complete');
  state.crawlJobId=completed.jobId;const r=await api(`${url}/functional-menu`,'POST',{crawlJobId:state.crawlJobId,productName:'NeoFlow Server v1.0.3',mode:'AUTO'});
  state.menuJobId=r.job.jobId;await save();console.log(JSON.stringify({menuJobId:state.menuJobId}));return;
 }
 if(action==='menu-status'){
  const r=await api(`${url}/functional-menu?job=${state.menuJobId}`);await fs.writeFile(path.join(out,'03-menu.json'),JSON.stringify(r,null,2));
  console.log(JSON.stringify({status:r.job?.status,progress:r.job?.progress,completion:r.job?.output?.completionStatus,rows:r.job?.output?.rows?.length,issues:r.job?.output?.issues}));return;
 }
 if(action==='prompt'){
  const r=await api(`${url}/functional-menu/${state.menuJobId}/prompt`,'POST');await fs.writeFile(path.join(out,'03-source-contract.json'),JSON.stringify(r,null,2));console.log('Saved source contract');return;
 }
 if(action==='approve'){
  const r=await api(`${url}/functional-menu?job=${state.menuJobId}`);if(r.job?.output?.completionStatus==='PARTIAL')throw Error('Partial result cannot be approved');
  const approved=await api(`${url}/functional-menu/${state.menuJobId}/approval`,'PUT',{revision:r.job.revision,rows:r.job.output.rows});await fs.writeFile(path.join(out,'03-menu.json'),JSON.stringify(approved,null,2));console.log('Approved reviewed menu');return;
 }
 if(action==='tc'){
  const r=await api(`${url}/menu-test-cases`,'POST',{menuJobId:state.menuJobId,featureTypes:{}});state.tcSetId=r.set.setId;await save();await fs.writeFile(path.join(out,'04-test-cases.json'),JSON.stringify(r,null,2));console.log(JSON.stringify({tcSetId:state.tcSetId,caseCount:r.set.output.caseCount}));return;
 }
 if(action==='export'){
  for(const [suffix,name] of [[`/functional-menu/${state.menuJobId}/download`,'03-메뉴트리.xlsx'],[`/menu-test-cases/${state.tcSetId}/download`,'04-TC.xlsx']]){
   const r=await fetch(url+suffix,{headers});if(!r.ok)throw Error(`${r.status}: ${await r.text()}`);await fs.writeFile(path.join(out,name),Buffer.from(await r.arrayBuffer()));
  }console.log('Exported original application Excel files');return;
 }
 if(action==='execute'){
  const tcs=JSON.parse(await fs.readFile(path.join(out,'04-test-cases.json'))).set.output.testCases;
  const names=['Job 목록 조회','실행 로그 조회','로그인 기록','Job 파일 업로드'];
  const selected=tcs.filter(t=>t.featureId.startsWith('scope:')||(t.ruleId==='FEATURE-01'&&names.includes(t.featurePath.at(-1))));
  if(selected.length!==72)throw Error('Unexpected pilot scope '+selected.length);
  const r=await api(`${url}/executions`,'POST',{tcSetId:state.tcSetId,targetUrl:state.targetUrl,allowMutation:false,productVersion:'미확인',notes:'조회 3기능, 파일 필요 1건, 공통 68건의 연계 검증. 문서 버전 1.0.3과 제품 버전은 구분. 나머지 TC는 이번 실행 선택 범위가 아님.',caseIds:selected.map(t=>t.id)});
  state.executionRunId=r.run.runId;state.selectedCaseCount=selected.length;await save();
  const started=await api(`${url}/executions/${state.executionRunId}/start`,'POST');await fs.writeFile(path.join(out,'05-execution.json'),JSON.stringify(started,null,2));console.log(JSON.stringify({runId:state.executionRunId,status:started.run.status,selected:selected.length}));return;
 }
 if(action==='execution-status'){
  const r=await api(`${url}/executions?run=${state.executionRunId}`);await fs.writeFile(path.join(out,'05-execution.json'),JSON.stringify(r,null,2));
  console.log(JSON.stringify({runId:state.executionRunId,status:r.run.status,elapsedMs:r.run.elapsedMs,processed:r.run.completedCount,pending:r.run.pendingCount,notExecuted:r.run.notExecutedCount,review:r.run.reviewCount,error:r.run.errorCount,progress:r.run.snapshot.progress}));return;
 }
 const docs=await api(url);await fs.writeFile(path.join(out,'01-documents.json'),JSON.stringify(docs,null,2));
 console.log(JSON.stringify({submissionId:state.submissionId,documentStatus:docs.status,delivery:docs.aiDelivery},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1});
