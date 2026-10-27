// A bounded browser session. Model output can select observed elements, never execute JS.
const { chromium } = require('playwright');
const { randomUUID } = require('node:crypto');
const { login } = require('./remote-agent/engine');
const mutation = /delete|remove|destroy|reset|purchase|payment|execute|terminate|kill|save|submit|upload|create|삭제|초기화|결제|실행|종료|저장|등록|추가|업로드|적용|승인/i;
const sessions = new Map();
const failure = code => Object.assign(new Error(code), { code });
function redact(value,s){const secret=s.openInput?.password;if(typeof value==='string')return secret?value.replaceAll(secret,'[REDACTED]'):value;if(Array.isArray(value))return value.map(v=>redact(v,s));if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,k==='screenshot'?v:redact(v,s)]));return value;}
function sameEntry(actual,expected){const a=new URL(actual),b=new URL(expected);return a.origin===b.origin&&a.pathname.replace(/\/$/,'')===b.pathname.replace(/\/$/,'')&&a.search===b.search&&a.hash===b.hash;}
function publicUrl(value){try{const url=new URL(value);for(const key of [...url.searchParams.keys()])if(/token|password|secret|session|authorization|api.?key|code/i.test(key))url.searchParams.set(key,'REDACTED');return url.href}catch{return ''}}
async function ready(s){
  const deadline=Date.now()+Math.min(60000,Math.max(1000,(s.loadTimeout||15)*1000));
  let last='',since=Date.now();const start=Date.now();
  while(Date.now()<deadline){
    const state=await s.page.evaluate(()=>({text:document.body?.innerText||'',loading:[...document.querySelectorAll('[aria-busy="true"],.loading,.spinner,.animate-spin,[role="progressbar"][aria-label]')].some(el=>!!(el.getBoundingClientRect().width&&el.getBoundingClientRect().height)&& (el.matches('[aria-busy="true"],.loading,.spinner,.animate-spin')||/loading|로딩|불러오|처리 중/i.test(el.getAttribute('aria-label')||'')))}));
    if(state.text!==last||state.loading||s.pendingRequests.size){last=state.text;since=Date.now()}
    if(!state.loading&&!s.pendingRequests.size&&Date.now()-since>=700&&Date.now()-start>=1200)return;
    await s.page.waitForTimeout(150);
  }
  throw failure('EXECUTION_SCREEN_NOT_READY');
}
async function observe(s) {
  const parts=[];s.frames=new Map();
  for(const frame of s.page.frames()){
    if(frame!==s.page.mainFrame()){
      try{if(!s.origins.has(new URL(frame.url()).origin)||!await(await frame.frameElement()).isVisible())continue}catch{continue}
    }
    const frameIndex=s.frames.size;s.frames.set(frameIndex,frame);
    const part=await frame.evaluate(({offset,frameIndex}) => {
    const visible = el => !!(el.getBoundingClientRect().width && el.getBoundingClientRect().height);
    const elements = [...document.querySelectorAll('a,button,input,select,textarea,[role="button"],[role="tab"],[role="option"],[role="menuitem"],[role="checkbox"],[role="radio"],[role="switch"],[role="combobox"]')].filter(visible).slice(0, 120).map((el, i) => {
      el.setAttribute('data-autotest-execution-id', String(offset+i));
      return { id: String(offset+i),frameIndex,context:el.closest('tr')?.innerText.slice(0,240)||'', tag: el.tagName.toLowerCase(),role:el.getAttribute('role')||'', type: el.getAttribute('type') || '', navigation:!!el.closest('nav,aside,[role="navigation"]') || (el.tagName==='A'&&!el.hasAttribute('download')), name: (el.getAttribute('aria-label') || el.labels?.[0]?.innerText || el.getAttribute('placeholder') || el.innerText || '').trim().slice(0, 160), href: el.tagName === 'A' ? el.href : '', options: el.tagName === 'SELECT' ? [...el.options].slice(0, 30).map(x => ({ label: x.text, value: x.value })) : undefined };
    });
    const main=document.querySelector('main,[role="main"]');
    const headings=[...(main||document.body).querySelectorAll('h1,h2,h3')].filter(el=>visible(el)&&!el.closest('nav,aside,header,footer,[role="navigation"]')).map(el=>el.innerText.trim()).filter(Boolean).slice(0,20);
    return { url: location.href, title: document.title, text: document.body.innerText.slice(0, 10000), mainText:main?.innerText.slice(0,8000)||'',headings,screenReady:true,elements, iframeCount: document.querySelectorAll('iframe').length };
    },{offset:parts.reduce((count,part)=>count+part.elements.length,0),frameIndex});parts.push(part);
  }
  const observation={...parts[0],text:parts.map((part,index)=>index?`\n[frame ${index}]\n${part.text}`:part.text).join('').slice(0,16000),elements:parts.flatMap(part=>part.elements),supportedFrameCount:parts.length-1};
  observation.url=publicUrl(observation.url);
  const atInitialUrl=sameEntry(s.page.url(),s.openInput.targetUrl);
  const loginEntry=s.openInput.setup?.loginMethod==='FORM_AUTO'&&new URL(s.page.url()).origin===new URL(s.openInput.targetUrl).origin
    &&observation.elements.some(el=>el.type==='password')&&observation.elements.some(el=>el.tag==='input'&&['','text','email'].includes(el.type))
    &&observation.elements.some(el=>(el.tag==='button'||el.type==='submit')&&/로그인|log\s?in|sign\s?in|접속/i.test(el.name));
  observation.entryState={requestedUrl:publicUrl(s.openInput.targetUrl),actualUrl:observation.url,atInitialUrl,initialScreenReady:atInitialUrl||loginEntry,redirectDisposition:atInitialUrl?'NONE':loginEntry?'LOGIN_ENTRY_REDIRECT':'UNEXPECTED_REDIRECT',initialEntryRequired:s.openInput.initialEntry===true,
    sessionMode:s.openInput.initialEntry?'FRESH_ANONYMOUS':'SETUP',authenticationState:s.authenticatedBySetup?'AUTHENTICATED_BY_SETUP':observation.elements.some(el=>el.type==='password')?'LOGIN_FORM_OBSERVED':'UNKNOWN',sessionResets:s.entryResetCount};
  if(s.authenticatedByTest)observation.entryState.authenticationState='AUTHENTICATED_BY_TEST';
  observation.authentication={savedAccountAvailable:!!(s.openInput.password&&s.openInput.setup?.automation?.username),dedicatedLoginAllowed:s.positiveLogin,loginAttempted:s.loginAttempted,outcome:s.loginOutcome||'NOT_ATTEMPTED'};
  for(const el of observation.elements)if(el.href&&publicUrl(el.href)!==el.href)el.href='';
  s.observation = observation;
  let screenshot = null;
  const masks=[s.page.locator('input,textarea,[contenteditable="true"]')];
  if(s.openInput.password)masks.push(s.page.getByText(s.openInput.password,{exact:false}));
  const png = await s.page.screenshot({ fullPage: false,mask:masks }).catch(() => null);
  if (png && png.length < 750000) screenshot = png.toString('base64');
  return redact({ ...observation, screenshot },s);
}
function safeUrl(value, origins) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || !origins.has(url.origin)) throw failure('EXECUTION_SCOPE_BLOCKED');
  return url.href;
}
async function open(input) {
  const origins = new Set([input.targetUrl, ...(input.setup?.automation?.allowedOrigins || [])].map(value=>new URL(value).origin));
  safeUrl(input.targetUrl, origins);
  const browser = await chromium.launch({ headless: true });
  try {
    const setup = input.setup || { loginMethod: 'NONE', automation: {} };
    const positiveLogin=input.testRuleId==='LOGIN-04';
    const anonymous=input.initialEntry===true||positiveLogin;
    if (!['NONE', 'FORM_AUTO', 'BASIC_AUTH'].includes(setup.loginMethod)) throw failure('EXECUTION_LOGIN_UNSUPPORTED');
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true,
      ignoreHTTPSErrors: setup.automation?.ignoreHTTPSErrors === true,
      ...(!anonymous && setup.loginMethod === 'BASIC_AUTH' ? { httpCredentials: { username: setup.automation.username, password: input.password } } : {}) });
    const page = await context.newPage(); page.setDefaultTimeout(8000);
    const s = { browser, context, page, origins, effects: new Set(), pendingRequests:new Set(),loadTimeout:setup.automation?.pageLoadTimeoutSeconds||15,allowMutation: input.allowMutation === true, observation: null, busy: false, downloads: [], dialogs: [], touched: Date.now(), authenticating: !input.initialEntry && setup.loginMethod === 'FORM_AUTO', blockedRequests: 0 };
    s.openInput=input;s.entryResetCount=input.entryResetCount||0;s.entryGateOpen=false;s.authenticatedBySetup=false;s.positiveLogin=positiveLogin;s.loginAttempted=false;
    s.authenticating=!anonymous&&setup.loginMethod==='FORM_AUTO';
    page.on('request',request=>{if(['document','xhr','fetch'].includes(request.resourceType()))s.pendingRequests.add(request)});
    page.on('requestfinished',request=>s.pendingRequests.delete(request));page.on('requestfailed',request=>s.pendingRequests.delete(request));
    await context.route('**/*', route => {
      try {
        safeUrl(route.request().url(), origins);
        if(s.dedicatedAuthentication&&!['GET','HEAD','OPTIONS'].includes(route.request().method())){
          const u=new URL(route.request().url());
          if(u.origin!==s.authenticationOrigin||!(s.authPosts.has(u.pathname)||/\/(login|signin|sign-in|authenticate|authentication|session|token)(\/|$)/i.test(u.pathname))){s.blockedRequests++;return route.abort();}
        }
        if (!s.authenticating && !s.allowMutation && !['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) { s.blockedRequests++; return route.abort(); }
        return route.continue();
      } catch { return route.abort(); }
    });
    page.on('dialog', dialog => { s.dialogs.push({ type: dialog.type(), text: dialog.message(), dismissed: true }); void dialog.dismiss(); });
    page.on('download', download => {
      const entry={ filename: download.suggestedFilename(), status: 'STARTED' };s.downloads.push(entry);
      entry.promise=(async()=>{try{
        if(await download.failure())throw new Error('DOWNLOAD_FAILED');
        const stream=await download.createReadStream();let size=0;const chunks=[];
        for await(const chunk of stream){size+=chunk.length;if(size>1048576){entry.truncated=true;stream.destroy();break;}chunks.push(chunk);}
        const bytes=Buffer.concat(chunks);entry.bytes=size;entry.sha256=require('node:crypto').createHash('sha256').update(bytes).digest('hex');
        if(/\.(csv|txt|json|tsv)$/i.test(entry.filename)&&!bytes.includes(0))entry.text=bytes.toString('utf8');
        entry.status='COMPLETED';
      }catch{entry.status='FAILED';}})();
    });
    if (!anonymous && setup.loginMethod === 'FORM_AUTO') {
      if (!input.password) throw failure('EXECUTION_PASSWORD_REQUIRED');
      await login(page, { targetUrl: input.targetUrl, automation: setup.automation, password: input.password }, new Set());
      s.authenticatedBySetup=true;
    } else await page.goto(input.targetUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    s.authenticating = false;
    s.runId=input.runId;s.attempt=input.attempt;
    if (page.url() === 'about:blank') throw failure('EXECUTION_TARGET_UNREACHABLE');
    await ready(s);
    const observation = await observe(s);
    s.entryGateOpen=input.initialEntry===true&&!observation.entryState.initialScreenReady;
    const sessionId = randomUUID(); sessions.set(sessionId, s);
    return { sessionId, observation };
  } catch (error) {
    await browser.close();
    if (/net::ERR_CERT_/.test(error.message || '')) throw failure('EXECUTION_TLS_UNTRUSTED');
    throw error;
  }
}
async function step(s, actions) {
  if (!Array.isArray(actions) || actions.length > 8) throw failure('EXECUTION_PLAN_INVALID');
  const records = [];
  for (const action of actions) {
    const record = { action: action.action, elementId: action.elementId || null, status: 'COMPLETED' };
    try {
      if(s.entryGateOpen&&action.action!=='reset_session')throw failure('EXECUTION_INITIAL_ENTRY_NOT_READY');
      record.beforeText=(await s.page.locator('body').innerText()).slice(0,5000);
      if(action.action==='reset_session'){
        if(!s.entryGateOpen||s.entryResetCount>=1)throw failure('EXECUTION_INITIAL_ENTRY_NOT_READY');
        const previousBrowser=s.browser;
        const replacement=await open({...s.openInput,initialEntry:true,entryResetCount:s.entryResetCount+1});
        const fresh=sessions.get(replacement.sessionId);sessions.delete(replacement.sessionId);const wasBusy=s.busy;Object.assign(s,fresh);s.busy=wasBusy;
        await previousBrowser.close();record.isNavigation=true;record.initializationOnly=true;
      }
      else if(action.action==='login_saved_account'){
        if(!s.positiveLogin||s.openInput.setup?.loginMethod!=='FORM_AUTO')throw failure('EXECUTION_LOGIN_ACTION_NOT_ALLOWED');
        if(s.loginAttempted)throw failure('EXECUTION_LOGIN_REPEAT_BLOCKED');
        if(!s.openInput.password||!s.openInput.setup?.automation?.username)throw failure('EXECUTION_INPUT_CONTEXT_REQUIRED');
        s.loginAttempted=true;record.targetName='저장된 시험 계정으로 로그인';record.credentialRef='SAVED_TEST_ACCOUNT';record.authenticationSteps=[];record.isNavigation=false;
        s.authPosts=new Set();s.authenticationOrigin=new URL(s.openInput.setup.automation.loginUrl||s.openInput.targetUrl).origin;s.dedicatedAuthentication=true;s.authenticating=true;
        try{
          const verifyLoginOrigin=async page=>{
            const origin=new URL(page.url()).origin;
            const forms=await page.locator('input[type="password"]').evaluateAll(inputs=>inputs.map(el=>el.form?.action||location.href));
            if(origin!==s.authenticationOrigin||!s.origins.has(origin)||forms.some(url=>new URL(url).origin!==origin))throw failure('EXECUTION_SCOPE_BLOCKED');
            for(const url of forms)s.authPosts.add(new URL(url).pathname);
          };
          await login(s.page,{targetUrl:s.openInput.targetUrl,automation:s.openInput.setup.automation,password:s.openInput.password},s.authPosts,verifyLoginOrigin,phase=>record.authenticationSteps.push(phase),verifyLoginOrigin);
          s.authenticatedByTest=true;s.loginOutcome='AUTHENTICATED';
        }catch(error){
          s.loginOutcome='UNCONFIRMED';
          if(!record.authenticationSteps.includes('SUBMITTED'))throw failure(error.code||'EXECUTION_LOGIN_ACTION_FAILED');
          record.authenticationError=['LOGIN_FAILED','LOGIN_NOT_CONFIRMED'].includes(error.message)?error.message:'EXECUTION_LOGIN_ACTION_FAILED';
        }finally{s.authenticating=false;s.dedicatedAuthentication=false;}
        record.authenticationOutcome=s.loginOutcome;
      }
      else if (action.action === 'reload') await s.page.reload({waitUntil:'domcontentloaded',timeout:20000});
      else if (action.action === 'wait') await s.page.waitForTimeout(Math.min(2000, Math.max(0, Number(action.milliseconds) || 500)));
      else if (action.action === 'goto') {
        record.isNavigation=true;
        const href = safeUrl(action.url, s.origins);
        if (!(s.observation.elements || []).some(el => el.href === href)) throw failure('EXECUTION_UNOBSERVED_URL');
        if (!s.allowMutation && mutation.test(href)) throw failure('EXECUTION_MUTATION_NOT_ALLOWED');
        await s.page.goto(href, { waitUntil: 'domcontentloaded', timeout: 20000 });
      } else {
        const el = s.observation.elements.find(x => x.id === action.elementId);
        if (!el || !/^\d+$/.test(el.id)) throw failure('EXECUTION_ELEMENT_UNKNOWN');
        if(s.positiveLogin&&action.action==='click'&&/로그인|log\s?in|sign\s?in|접속/i.test(el.name))throw failure('EXECUTION_LOGIN_REQUIRES_SAVED_ACCOUNT');
        record.isNavigation=action.action==='click'&&el.navigation===true;
        record.targetName=el.name;record.targetContext=el.context;record.frameIndex=el.frameIndex;record.value=action.action==='fill'||action.action==='select'?String(action.value||''):undefined;
        if (el.type === 'password' || el.type === 'file') throw failure('EXECUTION_INPUT_CONTEXT_REQUIRED');
        if (!s.allowMutation && mutation.test(el.name + ' ' + el.href)) throw failure('EXECUTION_MUTATION_NOT_ALLOWED');
        const frame=s.frames.get(el.frameIndex);if(!frame||frame.isDetached())throw failure('EXECUTION_FRAME_CHANGED');
        const locator = frame.locator(`[data-autotest-execution-id="${el.id}"]`);
        if(action.action==='click'&&mutation.test(el.name+' '+el.href)){
          const fingerprint=JSON.stringify([s.page.url(),el.name,el.href]);
          if(s.effects.has(fingerprint))throw failure('EXECUTION_REPEAT_REQUIRES_REVIEW');
          s.effects.add(fingerprint);
        }
        if (action.action === 'click') await locator.click();
        else if (action.action === 'fill') await locator.fill(String(action.value || '').slice(0, 2000));
        else if (action.action === 'select') await locator.selectOption(String(action.value || ''));
        else throw failure('EXECUTION_ACTION_UNSUPPORTED');
      }
      await s.page.waitForLoadState('domcontentloaded', { timeout: 3000 }).catch(() => {});
      await ready(s);
      record.url = publicUrl(s.page.url()); record.text = (await s.page.locator('body').innerText()).slice(0, 5000);
      records.push(record);
    } catch (error) { records.push({ ...record, status: 'ERROR', errorCode: error.code || 'EXECUTION_ACTION_FAILED' }); break; }
  }
  s.touched = Date.now();
  await Promise.all(s.downloads.map(d=>d.promise));
  return redact({ records, downloads: s.downloads.map(({promise,...value})=>value), dialogs: s.dialogs, blockedRequests: s.blockedRequests, needsDialogInput: s.dialogs.some(d=>d.type==='confirm'||d.type==='prompt'), observation: await observe(s) },s);
}
function register(app, auth, reserve, release, isBusy=()=>false) {
  app.get('/api/v1/tc-browser/capabilities',auth,(_req,res)=>res.json({protocolVersion:'tc-browser-v2',busy:isBusy(),browserAvailable:require('node:fs').existsSync(chromium.executablePath()),actions:['click','fill','select','goto','wait','reload','login_saved_account']}));
  app.post('/api/v1/tc-browser/open', auth, async (req, res) => {
    if (!reserve()) return res.status(429).json({ code: 'EXECUTION_BROWSER_BUSY' });
    try { res.json(await open(req.body)); } catch (error) { release(); res.status(422).json({ code: error.code || 'EXECUTION_OPEN_FAILED' }); }
  });
  app.post('/api/v1/tc-browser/:session/:operation', auth, async (req, res) => {
    const s = sessions.get(req.params.session);
    if (!s) return res.status(404).json({ code: 'EXECUTION_SESSION_NOT_FOUND' });
    if (s.busy) return res.status(409).json({ code: 'EXECUTION_SESSION_BUSY' });
    s.busy = true;
    try {
      if (req.params.operation === 'close') { sessions.delete(req.params.session); await s.browser.close(); release(); return res.json({ closed: true }); }
      if (req.params.operation !== 'step') return res.sendStatus(404);
      res.json(await step(s, req.body.actions));
    } catch (error) { res.status(422).json({ code: error.code || 'EXECUTION_STEP_FAILED' }); }
    finally { s.busy = false; }
  });
  const timer = setInterval(() => { for (const [id, s] of sessions) if (!s.busy && Date.now() - s.touched > 600000) { sessions.delete(id); void s.browser.close().finally(release); } }, 30000);
  timer.unref();
  const cancellation=setInterval(()=>{for(const [id,s] of sessions)if(s.runId&&!s.checking){s.checking=true;
    void fetch(`${process.env.MANAGEMENT_SERVICE_URL||'http://test-management-service:8082'}/api/internal/test-executions/${s.runId}/active?attempt=${s.attempt}`,{headers:{'X-Internal-Token':process.env.PRODUCT_DESCRIPTION_INTERNAL_TOKEN||''},signal:AbortSignal.timeout(4000)})
      .then(async response=>{if(response.ok&&!(await response.json()).active&&sessions.get(id)===s){sessions.delete(id);await s.browser.close();release()}}).catch(()=>{}).finally(()=>{s.checking=false});
  }},5000);cancellation.unref();
}
module.exports = { register, open, step, sessions, safeUrl };
