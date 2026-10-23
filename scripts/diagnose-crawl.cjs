// Explicit live read-only reproduction. Credentials are read in memory, never logged or saved.
const {execFileSync}=require('node:child_process');
const crypto=require('node:crypto');
const Module=require('node:module');
process.env.NODE_PATH='C:/Users/tta/autoTest/crawler-service/node_modules';Module._initPaths();
const docker='C:/Users/tta/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
const id=process.argv[2];if(!/^[a-f0-9-]{36}$/.test(id||''))throw Error('Job ID required');
const query=`SELECT json_build_object('snapshot',j.setup_snapshot,'submissionId',j.submission_id,'ciphertext',s.password_ciphertext) FROM playwright_crawl_jobs j JOIN playwright_crawl_setups s ON s.submission_id=j.submission_id AND s.member_id=j.member_id WHERE j.job_id='${id}'`;
const stored=JSON.parse(execFileSync(docker,['exec','autotest-db-1','psql','-U','test','-d','autotest','-At','-c',query],{encoding:'utf8'}));
const key=execFileSync(docker,['exec','autotest-test-management-service-1','cat','/app/.runtime/playwright/credentials.key']);
const [,iv,data]=stored.ciphertext.split(':');const bytes=Buffer.from(data,'base64');
const decipher=crypto.createDecipheriv('aes-256-gcm',key,Buffer.from(iv,'base64'));decipher.setAAD(Buffer.from(stored.submissionId));decipher.setAuthTag(bytes.subarray(-16));
const password=Buffer.concat([decipher.update(bytes.subarray(0,-16)),decipher.final()]).toString('utf8');key.fill(0);
if(process.argv.includes('--container')){
 const runtime=require('node:fs').readFileSync(require('node:path').join(__dirname,'crawl-diagnostic-runtime.cjs'),'utf8');
 const child=require('node:child_process').spawn(docker,['exec','-i','autotest-crawler-1','node','-e',runtime],{stdio:['pipe','inherit','inherit']});
 child.stdin.end(JSON.stringify({...stored.snapshot,password,automation:{...stored.snapshot.automation,maxDurationSeconds:150}}));
 delete stored.ciphertext;child.on('exit',code=>{process.exitCode=code||0});
}else{
const engine=require('../crawler-service/remote-agent/engine');
const normalize=engine.normalizedUrl;
engine.normalizedUrl=value=>{try{return normalize(value)}catch(error){let protocol='invalid';try{protocol=new URL(value).protocol}catch{};console.log(JSON.stringify({event:'INVALID_NAVIGATION_URL',protocol}));throw error}};
const job={...stored.snapshot,password,automation:{...stored.snapshot.automation,maxDurationSeconds:150}};
let count=0,lastStage;
engine.crawl(job,{onPage:async()=>{count++},onProgress:async p=>{lastStage=p.stage;console.log(JSON.stringify({stage:p.stage,captures:count}))}})
 .then(result=>console.log(JSON.stringify({result})))
 .catch(error=>{console.log(JSON.stringify({failed:true,code:/^[A-Z_]{5,80}$/.test(error.message)?error.message:error.name,lastStage,count}));process.exitCode=1})
 .finally(()=>{delete job.password;delete stored.ciphertext});
}
