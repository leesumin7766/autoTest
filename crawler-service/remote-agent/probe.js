const { chromium } = require('playwright');
const { login, scope, normalizedUrl, dangerous, visible } = require('./engine');

async function probe(job, { signal, browserType = chromium } = {}) {
  const started = Date.now();
  const connection = job.purpose === 'CONNECTION';
  const options = job.automation;
  const policy = scope(job);
  const browser = await browserType.launch({ headless: true });
  const stop = () => { void browser.close().catch(() => {}); };
  signal?.addEventListener('abort', stop, { once: true });
  const timeout = setTimeout(stop, 60000);
  let blockedNavigation = false;
  try {
    if (signal?.aborted) throw new Error('CRAWL_LEASE_LOST');
    if (!connection && !['NONE', 'FORM_AUTO', 'BASIC_AUTH'].includes(job.loginMethod)) throw new Error('LOGIN_METHOD_UNSUPPORTED');
    const context = await browser.newContext({ serviceWorkers: 'block', acceptDownloads: false, ignoreHTTPSErrors: options.ignoreHTTPSErrors === true,
      locale: 'ko-KR',
      ...(!connection && job.loginMethod === 'BASIC_AUTH' ? { httpCredentials: {
        origin: new URL(job.targetUrl).origin, username: options.username, password: job.password,
      } } : {}),
    });
    const authPosts = new Set([new URL(options.loginUrl || job.targetUrl).pathname]);
    await context.route('**/*', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (!policy.origins.has(url.origin)) {
        if (request.isNavigationRequest()) blockedNavigation = true;
        return route.abort();
      }
      const authPost = !connection && job.loginMethod === 'FORM_AUTO' && request.method() === 'POST'
        && url.origin === new URL(options.loginUrl || job.targetUrl).origin
        && (authPosts.has(url.pathname) || /\/(login|signin|sign-in|authenticate|authentication|session|token)(\/|$)/i.test(url.pathname));
      if (dangerous.test(url.pathname) || (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && !authPost)) return route.abort();
      return route.continue();
    });
    const page = await context.newPage();
    page.setDefaultTimeout(10000);
    page.on('dialog', dialog => { void dialog.dismiss().catch(() => {}); });
    context.on('page', popup => { if (popup !== page) void popup.close().catch(() => {}); });
    let httpStatus = 0;
    page.on('response', response => { if (response.request().isNavigationRequest() && response.frame() === page.mainFrame()) httpStatus = response.status(); });
    if (!connection && job.loginMethod === 'FORM_AUTO') await login(page, job, authPosts);
    else {
      const response = await page.goto(job.targetUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });
      httpStatus = response?.status() || httpStatus;
      if (!connection && httpStatus >= 400) throw new Error('LOGIN_FAILED');
    }
    if (!policy.origins.has(new URL(page.url()).origin)) throw new Error('CONNECTION_SCOPE_BLOCKED');
    if (!connection && job.loginMethod !== 'NONE' && await visible(page.locator(options.passwordSelector || 'input[type="password"]')))
      throw new Error('LOGIN_FAILED');
    normalizedUrl(page.url()); // Validate without returning tokens or query parameters to the caller.
    return { httpStatus, elapsedMs: Date.now() - started, loginVerified: !connection };
  } catch (error) {
    if (signal?.aborted) throw new Error('CRAWL_LEASE_LOST');
    if (/CERT_|certificate/i.test(error.message)) throw new Error('CERTIFICATE_ERROR');
    if (blockedNavigation || error.message.includes('ERR_BLOCKED_BY_CLIENT')) throw new Error('CONNECTION_SCOPE_BLOCKED');
    if (/net::|ECONN|ENOTFOUND|Timeout|closed/i.test(error.message)) throw new Error('TARGET_UNREACHABLE');
    throw error;
  } finally { clearTimeout(timeout); signal?.removeEventListener('abort', stop); await browser.close().catch(() => {}); }
}
module.exports = { probe };
