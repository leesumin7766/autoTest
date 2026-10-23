// Independent read-only diagnostic. This does not assign a TC P/F verdict.
const fs=require('node:fs/promises');
const crawlerRoot=process.env.AUTOTEST_CRAWLER_ROOT||'../crawler-service';
const {chromium}=require(crawlerRoot+'/node_modules/playwright');
const {login}=require(crawlerRoot+'/remote-agent/engine');
const {safeUrl}=require(crawlerRoot+'/tc-execution');
async function main(){
 if(!process.env.AUTOTEST_PASSWORD)throw Error('Runtime password is required');
 const root=process.env.AUTOTEST_OUTPUT_DIR||'output/sample3-validation-20261022';const state=JSON.parse(await fs.readFile(`${root}/workflow.json`));
 const input=JSON.parse(await fs.readFile(process.env.AUTOTEST_MENU_INPUT||'build/sample3-menu-input.json'));
 const response=await fetch(`${process.env.AUTOTEST_API_URL||'http://127.0.0.1:8080'}/api/submissions/${state.submissionId}/playwright-setup`,{headers:{'X-Member-Id':'1'}});const {setup}=await response.json();
 const browser=await chromium.launch();const result={method:'Independent read-only Playwright diagnostic; not the Qwen/Jev TC execution verdict',checkedAt:new Date().toISOString(),features:[]};
 try{
  const context=await browser.newContext({ignoreHTTPSErrors:setup.automation.ignoreHTTPSErrors,viewport:{width:1440,height:1000}});const page=await context.newPage();let authenticating=true;const origins=new Set([new URL(setup.targetUrl).origin]);
  let blocked=0;const blockedCalls=[];await context.route('**/*',route=>{try{safeUrl(route.request().url(),origins);if(!authenticating&&!['GET','HEAD','OPTIONS'].includes(route.request().method())){blocked++;blockedCalls.push({method:route.request().method(),path:new URL(route.request().url()).pathname});return route.abort()}return route.continue()}catch{return route.abort()}});
  await login(page,{targetUrl:setup.targetUrl,automation:setup.automation,password:process.env.AUTOTEST_PASSWORD},new Set());authenticating=false;result.loginConfirmed=true;
  await page.waitForTimeout(1500);
  const links={};for(const name of ['Jobs','Logs','Login History']){const link=page.getByRole('link',{name,exact:true});links[name]=await link.count()?await link.first().getAttribute('href'):input.evidence.find(s=>s.kind==='MENU'&&s.webPath?.at(-1)===name)?.url;if(!links[name])throw Error('No observed target URL for '+name)}
  for(const [index,name] of ['Jobs','Logs','Login History'].entries()){
   const target=new URL(links[name],setup.targetUrl).href;safeUrl(target,origins);const http=[];
   const listener=response=>{if(['xhr','fetch'].includes(response.request().resourceType()))http.push({path:new URL(response.url()).pathname,status:response.status(),method:response.request().method()})};page.on('response',listener);
   const beforeBlocked=blocked;await page.goto(target,{waitUntil:'domcontentloaded'});await page.waitForTimeout(2500);
   let action='';
   for(const label of ['Refresh','Search']){const button=page.getByRole('button',{name:label,exact:true});if(await button.count()&&await button.first().isVisible()){await button.first().click();action=label;break}}
   if(!action&&name==='Jobs'){
    const fields=page.getByPlaceholder(/search.*job|job.*search|작업.*검색/i);
    if(await fields.count()){await fields.first().fill('MY_B1');await fields.first().press('Enter');action='Search jobs: MY_B1 (기능리스트의 Job 이름 일부)'}
   }
   await page.waitForTimeout(2000);const text=await page.locator('body').innerText();const filename=`reference-${index+1}.png`;await page.screenshot({path:`${root}/${filename}`,fullPage:true});page.off('response',listener);
   result.features.push({name,path:new URL(page.url()).pathname,expectedObservedLinkPath:new URL(target).pathname,navigationMatched:new URL(page.url()).pathname===new URL(target).pathname,action,blockedRequests:blocked-beforeBlocked,blockedCalls:blockedCalls.slice(beforeBlocked),http,headings:await page.getByRole('heading').allTextContents(),tableRows:await page.locator('tbody tr').count(),observedText:text.slice(0,5000),screenshot:filename});
  }
 }finally{delete process.env.AUTOTEST_PASSWORD;await browser.close()}
 await fs.writeFile(`${root}/reference-diagnostic.json`,JSON.stringify(result,null,2));console.log(JSON.stringify(result.features.map(({name,path,action,navigationMatched,blockedRequests,http,tableRows})=>({name,path,action,navigationMatched,blockedRequests,http,tableRows}))));
}
main().catch(error=>{console.error(error.message);process.exitCode=1});
