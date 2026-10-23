const { chromium } = require('playwright');
const { scope, normalizedUrl, dangerous, login, visible } = require('./engine');
const { navigation, clickNavigation, expandNavigation } = require('./navigation');
const { waitForScreen, screenState, observe } = require('./screen');

async function crawl(job, { signal, onPage, onProgress = async () => {}, browserType = chromium } = {}) {
  if (!['NONE', 'FORM_AUTO', 'BASIC_AUTH'].includes(job.loginMethod)) throw new Error('LOGIN_METHOD_UNSUPPORTED');
  const options = job.automation, policy = scope(job);
  const seconds = options.pageLoadTimeoutSeconds ?? 60;
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 60) throw new Error('CRAWL_SCOPE_INVALID');
  const deadline = Date.now() + options.maxDurationSeconds * 1000;
  const browser = await browserType.launch({ headless: true });
  const stop = () => { void browser.close().catch(() => {}); };
  const timer = setTimeout(stop, options.maxDurationSeconds * 1000);
  signal?.addEventListener('abort', stop, { once: true });
  let count = job.resume?.pageCount || 0, bytes = job.resume?.bytes || 0, depthLimited = false, incomplete = 0, failed = 0, noChange = 0;
  const prior = new Map();
  for (const page of job.resume?.pages || []) prior.set(page.transition?.menuKey || '', page);
  const pendingPrior = new Set([...prior].filter(([,page]) => page.screenState?.readiness !== 'READY' || page.transition?.outcome !== 'CONFIRMED').map(([key]) => key));
  const uniqueScreens = new Set();
  for (const page of prior.values()) if(page.screenState?.readiness === 'READY' && page.transition?.outcome === 'CONFIRMED')uniqueScreens.add(page.screenState.screenKey);
  const skipped = [];
  let authenticating = job.loginMethod === 'FORM_AUTO';
  const authPosts = new Set([new URL(options.loginUrl || job.targetUrl).pathname]);
  const redact = value => typeof value === 'string' ? (job.password ? value.replaceAll(job.password, '[REDACTED]') : value)
    : Array.isArray(value) ? value.map(redact) : value && typeof value === 'object'
      ? Object.fromEntries(Object.entries(value).map(([key, item]) => [key, redact(item)])) : value;
  const summary = stopReason => ({ pageCount: count, stopReason, incompleteCount: incomplete, unresolvedCount: pendingPrior.size, navigationFailureCount: failed,
    noChangeCount: noChange, uniqueScreenCount: uniqueScreens.size, skipped,
    coverageStatus: failed || incomplete || noChange || pendingPrior.size || stopReason !== 'DONE' ? 'INCOMPLETE' : 'COMPLETE' });
  try {
    await onProgress({stage:'CONNECTING',current:'브라우저 접속 준비',remaining:0});
    const context = await browser.newContext({ viewport: { width: options.viewportWidth, height: options.viewportHeight },
      ignoreHTTPSErrors: options.ignoreHTTPSErrors === true, serviceWorkers: 'block', acceptDownloads: false,
      ...(job.loginMethod === 'BASIC_AUTH' ? { httpCredentials: { origin: new URL(job.targetUrl).origin, username: options.username, password: job.password } } : {}) });
    await context.route('**/*', async route => {
      try {
        const request = route.request(), url = new URL(request.url());
        const post = authenticating && request.method() === 'POST' && url.origin === new URL(options.loginUrl || job.targetUrl).origin
          && (authPosts.has(url.pathname) || /\/(login|signin|sign-in|authenticate|authentication|session|token)(\/|$)/i.test(url.pathname));
        if (!policy.origins.has(url.origin) || dangerous.test(url.pathname) || (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && !post)) return route.abort('blockedbyclient');
        return route.continue();
      } catch { return route.abort('blockedbyclient'); }
    });
    const page = await context.newPage();
    let navigationFailureCode = null;
    page.on('requestfailed', request => {
      if(request.isNavigationRequest() && request.frame()===page.mainFrame()) {
        const code=request.failure()?.errorText?.match(/^net::(ERR_[A-Z_]+)$/)?.[1];
        if(code)navigationFailureCode=code;
      }
    });
    page.setDefaultTimeout(10000);
    page.on('dialog', dialog => {
      // Leaving a terminal/editor may ask whether to navigate away. Dismissing it
      // cancels all subsequent replay attempts; accept only this browser event.
      void (dialog.type() === 'beforeunload' ? dialog.accept() : dialog.dismiss()).catch(() => {});
    });
    context.on('page', popup => { if (popup !== page) void popup.close().catch(() => {}); });
    if (authenticating) {
      await onProgress({stage:'LOGIN',current:'로그인 전 화면 수집·인증 확인',remaining:0});
      await login(page, job, authPosts, async () => {
        const key=require('node:crypto').createHash('sha256').update('PRE_AUTH:'+normalizedUrl(page.url())).digest('hex');
        if(prior.has(key)||count>=options.maxPages)return;
        const frames=await observe(page,policy);
        const state=screenState({frames,readiness:'READY',waitedMs:0});
        const screenshot=await page.screenshot({type:'jpeg',quality:70,mask:frames.map(frame=>frame.frame.locator('input,textarea,[contenteditable="true"]')),timeout:10000});
        if(screenshot.length>2*1024*1024||bytes+screenshot.length>64*1024*1024)throw new Error('CAPTURE_TOO_LARGE');
        await onPage({...redact({title:frames[0]?.title||'로그인',bodyText:frames.map(frame=>frame.bodyText).join('\n\n').slice(0,32000),
          elements:frames.flatMap(frame=>frame.elements.map(item=>({...item,frameUrl:frame.url}))).slice(0,500),
          links:[],navigation:[],screenState:state,transition:{menuKey:key,menuPath:[],scope:'PRE_AUTH',outcome:'CONFIRMED',steps:[],finalUrl:normalizedUrl(page.url())}}),
          url:normalizedUrl(page.url()),pageIndex:count,screenshot:screenshot.toString('base64')});
        count++;bytes+=screenshot.length;uniqueScreens.add(state.screenKey);
      });
      authenticating = false;
    }
    if (page.url() === 'about:blank') await page.goto(job.targetUrl, { waitUntil: 'domcontentloaded', timeout: seconds * 1000 });
    // Replay starts with the same browser preferences as the authenticated entry.
    const entryStorage = await page.evaluate(() => ({ origin: location.origin, local: Object.entries(localStorage), session: Object.entries(sessionStorage) }));
    await context.addInitScript(state => {
      if (location.origin !== state.origin) return;
      localStorage.clear();for (const [key,value] of state.local) localStorage.setItem(key,value);
      sessionStorage.clear();for (const [key,value] of state.session) sessionStorage.setItem(key,value);
    }, entryStorage);
    const queue = [{ steps: [], via: null, depth: 0 }];
    const visited = new Set(), captured = new Set();
    while (queue.length && count < options.maxPages && Date.now() < deadline) {
      if (signal?.aborted) throw new Error('CRAWL_LEASE_LOST');
      const item = queue.shift(), visitKey = item.via?.menuKey || 'entry';
      if (visited.has(visitKey)) continue;
      visited.add(visitKey);
      let result, stage = 'LOAD_SOURCE';
      navigationFailureCode=null;
      try {
        await onProgress(redact({stage:'NAVIGATING',current:item.via?.path?.join(' → ') || '시작 화면',remaining:queue.length+1}));
        const initialEnd = Math.min(deadline, Date.now() + seconds * 1000);
        let response;
        for(let attempt=0;attempt<2;attempt++) {
          try {response=await page.goto(job.targetUrl,{waitUntil:'domcontentloaded',timeout:Math.max(1,initialEnd-Date.now())});break;}
          catch(error) {
            if(attempt || page.isClosed() || Date.now()>=initialEnd || !/ERR_ABORTED|interrupted by another navigation|context.*destroy/i.test(error.message))throw error;
            await page.waitForLoadState('domcontentloaded',{timeout:Math.max(1,Math.min(3000,initialEnd-Date.now()))}).catch(()=>{});
            await new Promise(resolve=>setTimeout(resolve,Math.min(250,Math.max(0,initialEnd-Date.now()))));
          }
        }
        if (response?.status() >= 400) throw new Error('TARGET_UNREACHABLE');
        await onProgress(redact({stage:'LOADING',current:item.via?.path?.join(' → ') || '시작 화면',remaining:queue.length+1}));
        result = await waitForScreen(page, policy, initialEnd);
        for (const step of item.steps) {
          if (result.readiness !== 'READY') throw new Error('SOURCE_LOAD_TIMEOUT');
          stage = 'EXPAND_SOURCE';
          await expandNavigation(page, policy);
          const end = Math.min(deadline, Date.now() + seconds * 1000);
          stage = 'CLICK_MENU';
          await onProgress(redact({stage:'NAVIGATING',current:item.via?.path?.join(' → ') || '시작 화면',remaining:queue.length+1}));
          await clickNavigation(page, step, policy, end);
          stage = 'WAIT_SCREEN';
          await onProgress(redact({stage:'LOADING',current:item.via?.path?.join(' → ') || '시작 화면',remaining:queue.length+1}));
          result = await waitForScreen(page, policy, end);
        }
      } catch (error) {
        if (signal?.aborted) throw new Error('CRAWL_LEASE_LOST');
        if (!count && !item.via) throw error;
        failed++;
        pendingPrior.add(item.via?.menuKey || '');
        if (skipped.length < 500) skipped.push({ menuKey: item.via?.menuKey || '', path: item.via?.path || [], stage,
          reason: error.message === 'SOURCE_LOAD_TIMEOUT' ? 'SOURCE_LOAD_TIMEOUT' : error.message === 'NAVIGATION_NOT_AVAILABLE' ? 'NAVIGATION_NOT_AVAILABLE' : error.message.match(/net::(ERR_[A-Z_]+)/)?.[1] || (page.isClosed()?'BROWSER_PAGE_CLOSED':error.name === 'TimeoutError' ? 'CLICK_TIMEOUT' : 'NAVIGATION_FAILED') });
        continue;
      }
      // Chromium may replace a failed navigation with chrome-error://chromewebdata/.
      // This is a failed menu, not an invalid crawl configuration or a fatal runner error.
      const rawUrl=page.url();
      if(!policy.page(rawUrl)) {
        failed++;pendingPrior.add(item.via?.menuKey || '');
        const reason=rawUrl.startsWith('chrome-error:') ? navigationFailureCode || 'BROWSER_ERROR_PAGE' : 'CONNECTION_SCOPE_BLOCKED';
        if(skipped.length<500)skipped.push({menuKey:item.via?.menuKey || '',path:item.via?.path || [],stage:'WAIT_SCREEN',reason});
        continue;
      }
      const current = normalizedUrl(rawUrl);
      if (job.loginMethod === 'FORM_AUTO' && await visible(page.locator(options.passwordSelector || 'input[type="password"]'))) throw new Error('LOGIN_FAILED');
      await expandNavigation(page, policy);
      // Observe again so expanded navigation and captured evidence describe the same state.
      result.frames = await observe(page, policy);
      const state = screenState(result), captureKey = visitKey + ':' + state.screenKey;
      if (captured.has(captureKey)) continue;
      const menu = await navigation(page, policy);
      const confirmed = !item.via || state.screenKey !== item.sourceScreenKey || menu.some(node => node.menuKey === item.via.menuKey && node.selected);
      const outcome = state.readiness !== 'READY' ? 'LOAD_TIMEOUT' : confirmed ? 'CONFIRMED' : 'NO_STATE_CHANGE';
      const previous=prior.get(item.via?.menuKey || '');
      const retained=previous?.screenState?.readiness==='READY'&&previous?.transition?.outcome==='CONFIRMED'&&previous?.screenState?.screenKey===state.screenKey;
      if(state.readiness==='READY'&&confirmed)pendingPrior.delete(item.via?.menuKey || '');
      else pendingPrior.add(item.via?.menuKey || '');
      const frames = result.frames;
      const main = frames.find(frame => frame.frame === page.mainFrame());
      const evidence = { title: main?.title || '', bodyText: frames.map(frame => `[${frame.url}]\n${frame.bodyText}`).join('\n\n').slice(0, 32000),
        elements: frames.flatMap(frame => frame.elements.map(item => ({ ...item, frameUrl: frame.url }))).slice(0, 500),
        links: frames.flatMap(frame => frame.links).flatMap(link => {
          try { return policy.page(link.href) && !dangerous.test(link.text) ? [{ ...link, href: normalizedUrl(link.href) }] : []; } catch { return []; }
        }).slice(0, 500), navigation: menu.map(({ _action, ...node }) => node),
        screenState: state, transition: { menuKey: item.via?.menuKey || '', menuPath: item.via?.path || [],
          sourcePageIndex: item.sourcePageIndex ?? -1, sourceScreenKey: item.sourceScreenKey || '', finalUrl: current, outcome,
          steps: item.steps.map(step => ({ menuKey: step.menuKey, label: step.label, frameUrl: step.frameUrl })) } };
      let sourcePageIndex = previous?.pageIndex;
      if(!retained){
      await onProgress(redact({stage:'CAPTURING',current:item.via?.path?.join(' → ') || '시작 화면',remaining:queue.length}));
      const masks = frames.flatMap(frame => [frame.frame.locator('input,textarea,[contenteditable="true"]'), ...(job.password ? [frame.frame.getByText(job.password, { exact: false })] : [])]);
      let screenshot;
      try {
        screenshot = await page.screenshot({ type: 'jpeg', quality: 70, mask: masks, timeout: 10000 });
      } catch(error) {
        const changed=/context.*destroy|frame.*detach|while navigating/i.test(error.message);
        if(!changed && error.name!=='TimeoutError')throw error;
        failed++;pendingPrior.add(item.via?.menuKey || '');
        if(skipped.length<500)skipped.push({menuKey:item.via?.menuKey || '',path:item.via?.path || [],stage:'CAPTURING',reason:changed?'CAPTURE_CONTEXT_CHANGED':'CAPTURE_TIMEOUT'});
        continue;
      }
      if (screenshot.length > 2 * 1024 * 1024) throw new Error('CAPTURE_TOO_LARGE');
      if (bytes + screenshot.length > 64 * 1024 * 1024) return summary('STORAGE_LIMIT');
      await onPage({ ...redact(evidence), url: current, pageIndex: count, screenshot: screenshot.toString('base64') });
      sourcePageIndex = count++;
      bytes += screenshot.length;
      }
      captured.add(captureKey);
      if (state.readiness !== 'READY') { incomplete++; continue; }
      if (!confirmed) { noChange++; continue; }
      uniqueScreens.add(state.screenKey);
      const candidates = menu.filter(node => node.kind !== 'GROUP' && node.canExplore !== false && !visited.has(node.menuKey));
      if (item.depth < options.maxDepth) {
        for (const node of candidates) if (queue.length < 5000) queue.push({ steps: [...item.steps, node._action], via: node,
          depth: item.depth + 1, sourcePageIndex, sourceScreenKey: state.screenKey });
      } else if (candidates.length) depthLimited = true;
    }
    return summary(Date.now() >= deadline ? 'TIME_LIMIT' : count >= options.maxPages ? 'PAGE_LIMIT' : depthLimited ? 'DEPTH_LIMIT' : 'DONE');
  } catch (error) {
    if (signal?.aborted) throw new Error('CRAWL_LEASE_LOST');
    if (Date.now() >= deadline && count) return summary('TIME_LIMIT');
    if (/CERT_|certificate/i.test(error.message)) throw new Error('CERTIFICATE_ERROR');
    if (/net::|ECONN|ENOTFOUND|Timeout/i.test(error.message)) throw new Error('TARGET_UNREACHABLE');
    throw error;
  } finally { clearTimeout(timer); signal?.removeEventListener('abort', stop); await browser.close().catch(() => {}); }
}

module.exports = { crawl };
