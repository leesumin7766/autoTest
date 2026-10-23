// Container-side diagnostic: receives the job on stdin; never prints input or credentials.
let input='';process.stdin.setEncoding('utf8');process.stdin.on('data',chunk=>{input+=chunk});
process.stdin.on('end',async()=>{
 let job;try{
  job=JSON.parse(input);input='';
  const engine=require('/app/remote-agent/engine');const original=engine.normalizedUrl;
  engine.normalizedUrl=value=>{try{return original(value)}catch(error){let protocol='invalid';try{protocol=new URL(value).protocol}catch{};console.log(JSON.stringify({event:'INVALID_NAVIGATION_URL',protocol}));throw error}};
  let count=0;
  const browserType={launch:async options=>{
    const browser=await require('/app/node_modules/playwright').chromium.launch(options),newContext=browser.newContext.bind(browser);
    browser.newContext=async options=>{const context=await newContext(options),newPage=context.newPage.bind(context);context.newPage=async()=>{const page=await newPage(),goto=page.goto.bind(page);page.on('crash',()=>console.log('BROWSER_PAGE_CRASHED'));page.goto=async(...args)=>{try{return await goto(...args)}catch(error){console.log(JSON.stringify({event:'GOTO_FAILED',code:error.message.match(/net::(ERR_[A-Z_]+)/)?.[1]||error.name,closed:page.isClosed(),connected:browser.isConnected(),interrupted:/interrupted|ERR_ABORTED/i.test(error.message)}));throw error}};return page};return context};return browser;
  }};
  try{const result=await engine.crawl(job,{browserType,onPage:async()=>{count++},onProgress:async p=>{console.log(JSON.stringify({stage:p.stage,captures:count}))}});console.log(JSON.stringify({result}));}
  catch(error){console.log(JSON.stringify({failed:true,code:/^[A-Z_]{5,80}$/.test(error.message)?error.message:error.name,count,locations:(error.stack||'').split('\n').map(line=>line.match(/(?:walker|navigation|screen|engine)\.js:\d+:\d+/)?.[0]).filter(Boolean),detached:/detach/i.test(error.message),closed:/closed/i.test(error.message),screenshot:/screenshot/i.test(error.message),timeout:/timeout/i.test(error.message),contextDestroyed:/context.*destroy/i.test(error.message)}));process.exitCode=1}
 }catch{console.log('DIAGNOSTIC_FAILED');process.exitCode=1}
 finally{if(job)delete job.password;input=''}
});
