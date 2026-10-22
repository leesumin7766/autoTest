// A bounded browser session. Model output can select observed elements, never execute JS.
const { chromium } = require('playwright');
const { randomUUID } = require('node:crypto');
const { login } = require('./remote-agent/engine');
const mutation = /delete|remove|destroy|reset|purchase|payment|execute|terminate|kill|save|submit|upload|create|삭제|초기화|결제|실행|종료|저장|등록|추가|업로드|적용|승인/i;
const sessions = new Map();
const failure = code => Object.assign(new Error(code), { code });
async function observe(s) {
  const observation = await s.page.evaluate(() => {
    const visible = el => !!(el.getBoundingClientRect().width && el.getBoundingClientRect().height);
    const elements = [...document.querySelectorAll('a,button,input,select,textarea,[role="button"],[role="tab"]')].filter(visible).slice(0, 120).map((el, i) => {
      el.setAttribute('data-autotest-execution-id', String(i));
      return { id: String(i), tag: el.tagName.toLowerCase(), type: el.getAttribute('type') || '', name: (el.getAttribute('aria-label') || el.labels?.[0]?.innerText || el.getAttribute('placeholder') || el.innerText || '').trim().slice(0, 160), href: el.tagName === 'A' ? el.href : '', options: el.tagName === 'SELECT' ? [...el.options].slice(0, 30).map(x => ({ label: x.text, value: x.value })) : undefined };
    });
    return { url: location.href, title: document.title, text: document.body.innerText.slice(0, 10000), elements, iframeCount: document.querySelectorAll('iframe').length };
  });
  s.observation = observation;
  let screenshot = null;
  const png = await s.page.screenshot({ fullPage: false }).catch(() => null);
  if (png && png.length < 750000) screenshot = png.toString('base64');
  return { ...observation, screenshot };
}
function safeUrl(value, origins) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || !origins.has(url.origin)) throw failure('EXECUTION_SCOPE_BLOCKED');
  return url.href;
}
async function open(input) {
  const origins = new Set([new URL(input.targetUrl).origin, ...(input.setup?.automation?.allowedOrigins || [])]);
  safeUrl(input.targetUrl, origins);
  const browser = await chromium.launch({ headless: true });
  try {
    const setup = input.setup || { loginMethod: 'NONE', automation: {} };
    if (!['NONE', 'FORM_AUTO', 'BASIC_AUTH'].includes(setup.loginMethod)) throw failure('EXECUTION_LOGIN_UNSUPPORTED');
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true,
      ignoreHTTPSErrors: setup.automation?.ignoreHTTPSErrors === true,
      ...(setup.loginMethod === 'BASIC_AUTH' ? { httpCredentials: { username: setup.automation.username, password: input.password } } : {}) });
    const page = await context.newPage(); page.setDefaultTimeout(8000);
    const s = { browser, context, page, origins, effects: new Set(), allowMutation: input.allowMutation === true, observation: null, busy: false, downloads: [], dialogs: [], touched: Date.now(), authenticating: setup.loginMethod === 'FORM_AUTO', blockedRequests: 0 };
    await context.route('**/*', route => {
      try {
        safeUrl(route.request().url(), origins);
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
    if (setup.loginMethod === 'FORM_AUTO') {
      if (!input.password) throw failure('EXECUTION_PASSWORD_REQUIRED');
      await login(page, { targetUrl: input.targetUrl, automation: setup.automation, password: input.password }, new Set());
    } else await page.goto(input.targetUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    s.authenticating = false;
    if (page.url() === 'about:blank') throw failure('EXECUTION_TARGET_UNREACHABLE');
    const observation = await observe(s);
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
      record.beforeText=(await s.page.locator('body').innerText()).slice(0,5000);
      if (action.action === 'reload') await s.page.reload({waitUntil:'domcontentloaded',timeout:20000});
      else if (action.action === 'wait') await s.page.waitForTimeout(Math.min(2000, Math.max(0, Number(action.milliseconds) || 500)));
      else if (action.action === 'goto') {
        const href = safeUrl(action.url, s.origins);
        if (!(s.observation.elements || []).some(el => el.href === href)) throw failure('EXECUTION_UNOBSERVED_URL');
        if (!s.allowMutation && mutation.test(href)) throw failure('EXECUTION_MUTATION_NOT_ALLOWED');
        await s.page.goto(href, { waitUntil: 'domcontentloaded', timeout: 20000 });
      } else {
        const el = s.observation.elements.find(x => x.id === action.elementId);
        if (!el || !/^\d+$/.test(el.id)) throw failure('EXECUTION_ELEMENT_UNKNOWN');
        record.targetName=el.name;record.value=action.action==='fill'||action.action==='select'?String(action.value||''):undefined;
        if (el.type === 'password' || el.type === 'file') throw failure('EXECUTION_INPUT_CONTEXT_REQUIRED');
        if (!s.allowMutation && mutation.test(el.name + ' ' + el.href)) throw failure('EXECUTION_MUTATION_NOT_ALLOWED');
        const locator = s.page.locator(`[data-autotest-execution-id="${el.id}"]`);
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
      await s.page.waitForTimeout(500);
      record.url = s.page.url(); record.text = (await s.page.locator('body').innerText()).slice(0, 5000);
      records.push(record);
    } catch (error) { records.push({ ...record, status: 'ERROR', errorCode: error.code || 'EXECUTION_ACTION_FAILED' }); break; }
  }
  s.touched = Date.now();
  await Promise.all(s.downloads.map(d=>d.promise));
  return { records, downloads: s.downloads.map(({promise,...value})=>value), dialogs: s.dialogs, blockedRequests: s.blockedRequests, needsDialogInput: s.dialogs.some(d=>d.type==='confirm'||d.type==='prompt'), observation: await observe(s) };
}
function register(app, auth, reserve, release) {
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
}
module.exports = { register, open, step, sessions, safeUrl };
