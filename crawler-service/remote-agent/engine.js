const { chromium } = require('playwright');

const dangerous = /(?:logout|signout|logoff|delete|remove|destroy|reset|payment|purchase|checkout|execute|terminate|삭제|탈퇴|결제|초기화|실행)/i;
function normalizedUrl(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error('CRAWL_SCOPE_INVALID');
  url.search = '';
  if (!url.hash.startsWith('#/')) url.hash = '';
  return url.href;
}
function glob(pattern, path) {
  const escaped = pattern.split('*').map(part => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('.*');
  return new RegExp(`^${escaped}$`).test(path);
}
function scope(job) {
  const options = job.automation;
  const origins = new Set([new URL(job.targetUrl).origin, ...options.allowedOrigins.map(value => new URL(value).origin)]);
  return {
    origins,
    page(value) {
      try {
        const url = new URL(value);
        return origins.has(url.origin) && !url.username && !url.password && !dangerous.test(url.pathname)
          && !options.excludePaths.some(pattern => glob(pattern, url.pathname))
          && (!options.includePaths.length || options.includePaths.some(pattern => glob(pattern, url.pathname)));
      } catch { return false; }
    },
  };
}
async function visible(locator) {
  for (let index = 0; index < Math.min(await locator.count(), 30); index++) {
    const candidate = locator.nth(index);
    if (await candidate.isVisible()) return candidate;
  }
  return null;
}
function failure(code) { return new Error(code); }

async function loadForCapture(page, url, options, deadline) {
  const seconds = options.pageLoadTimeoutSeconds ?? 30;
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 60) throw failure('CRAWL_SCOPE_INVALID');
  const end = Math.min(deadline, Date.now() + seconds * 1000);
  try {
    const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: Math.max(1, end - Date.now()) });
    await page.waitForLoadState('networkidle', { timeout: Math.max(1, end - Date.now()) });
    return response;
  } catch (error) {
    if (error.name === 'TimeoutError') throw failure('PAGE_LOAD_TIMEOUT');
    throw error;
  }
}

async function login(page, job, authPosts) {
  const options = job.automation;
  await page.goto(options.loginUrl || job.targetUrl, { waitUntil: 'domcontentloaded', timeout: 20000 });
  const loginPageUrl = page.url();
  const usernameCandidates = page.locator(options.usernameSelector
    || 'input[autocomplete="username"], input[type="email"], input[name*="user" i], input[name*="login" i], input[type="text"]');
  // Login forms may remain hidden while the product checks the current session.
  // Wait for a visible candidate instead of treating DOMContentLoaded as form readiness.
  await usernameCandidates.locator('visible=true').first().waitFor({ state: 'visible', timeout: 15000 });
  const username = await visible(usernameCandidates);
  if (!username) throw failure('LOGIN_FAILED');
  const formAction = await username.evaluate(input => input.form?.action || '');
  if (formAction) authPosts.add(new URL(formAction).pathname);
  await username.fill(options.username);
  let password = await visible(options.passwordSelector ? page.locator(options.passwordSelector) : page.locator('input[type="password"]'));
  if (!password) {
    const next = await visible(page.getByRole('button', { name: /^(다음|계속|next|continue)$/i }));
    if (!next) throw failure('LOGIN_FAILED');
    await next.click();
    await page.locator(options.passwordSelector || 'input[type="password"]').first().waitFor({ state: 'visible', timeout: 10000 });
    password = await visible(page.locator(options.passwordSelector || 'input[type="password"]'));
  }
  if (!password) throw failure('LOGIN_FAILED');
  await password.fill(job.password);
  const submit = await visible(options.submitSelector ? page.locator(options.submitSelector)
    : page.getByRole('button', { name: /로그인|log\s?in|sign\s?in|접속/i }));
  if (submit) await submit.click(); else await password.press('Enter');
  try {
    if (options.successUrl) {
      const expected = new URL(options.successUrl);
      await page.waitForURL(url => url.origin === expected.origin && url.pathname.startsWith(expected.pathname), { timeout: 15000 });
    }
    if (options.successSelector) await page.locator(options.successSelector).first().waitFor({ state: 'visible', timeout: 15000 });
    if (!options.successUrl && !options.successSelector) {
      await page.waitForFunction(previous => {
        const visible = element => element && element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0;
        const password = [...document.querySelectorAll('input[type="password"]')].some(visible);
        const logout = [...document.querySelectorAll('a,button')].some(node => visible(node) && /logout|sign.?out|로그아웃/i.test((node.getAttribute('href') || '') + ' ' + node.textContent));
        const navigation = [...document.querySelectorAll('nav,[role="navigation"]')].some(visible);
        return !password && (logout || (location.href !== previous && navigation));
      }, loginPageUrl, { timeout: 12000 }).catch(async () => {
        throw failure(await visible(page.locator(options.passwordSelector || 'input[type="password"]')) ? 'LOGIN_FAILED' : 'LOGIN_NOT_CONFIRMED');
      });
    }
    if (await visible(page.locator(options.passwordSelector || 'input[type="password"]'))) throw failure('LOGIN_FAILED');
  } catch (error) { throw failure(error.message === 'LOGIN_NOT_CONFIRMED' ? 'LOGIN_NOT_CONFIRMED' : 'LOGIN_FAILED'); }
}

/** Navigational capture only. Never submits product forms after login. */
async function crawl(job, { signal, onPage, browserType = chromium } = {}) {
  if (!['NONE', 'FORM_AUTO', 'BASIC_AUTH'].includes(job.loginMethod)) throw failure('LOGIN_METHOD_UNSUPPORTED');
  const options = job.automation;
  const policy = scope(job);
  const deadline = Date.now() + options.maxDurationSeconds * 1000;
  const browser = await browserType.launch({ headless: true });
  let timeout;
  const stop = () => { void browser.close().catch(() => {}); };
  signal?.addEventListener('abort', stop, { once: true });
  timeout = setTimeout(stop, options.maxDurationSeconds * 1000);
  let authenticating = job.loginMethod === 'FORM_AUTO';
  const authPosts = new Set([new URL(options.loginUrl || job.targetUrl).pathname]);
  let count = 0, imageBytes = 0, depthLimited = false;
  try {
    if (signal?.aborted) throw failure('CRAWL_LEASE_LOST');
    const context = await browser.newContext({ viewport: { width: options.viewportWidth, height: options.viewportHeight },
      serviceWorkers: 'block', acceptDownloads: false, ignoreHTTPSErrors: true,
      ...(job.loginMethod === 'BASIC_AUTH' ? { httpCredentials: {
        origin: new URL(job.targetUrl).origin, username: options.username, password: job.password,
      } } : {}),
    });
    await context.route('**/*', async route => {
      try {
        const request = route.request();
        const url = new URL(request.url());
        const loginOrigin = new URL(options.loginUrl || job.targetUrl).origin;
        const permittedMethod = ['GET', 'HEAD', 'OPTIONS'].includes(request.method())
          || (authenticating && request.method() === 'POST' && url.origin === loginOrigin
            && (authPosts.has(url.pathname) || /\/(login|signin|sign-in|authenticate|authentication|session|token)(\/|$)/i.test(url.pathname)));
        if (!policy.origins.has(url.origin) || !permittedMethod || dangerous.test(url.pathname)) return route.abort('blockedbyclient');
        return route.continue();
      } catch { return route.abort('blockedbyclient'); }
    });
    const page = await context.newPage();
    page.setDefaultTimeout(10000);
    page.on('dialog', dialog => { void dialog.dismiss().catch(() => {}); });
    context.on('page', popup => { if (popup !== page) void popup.close().catch(() => {}); });
    if (authenticating) { await login(page, job, authPosts); authenticating = false; }
    const queue = [{ url: normalizedUrl(job.targetUrl), depth: 0 }];
    const visited = new Set();
    const captured = new Set();
    while (queue.length && count < options.maxPages && Date.now() < deadline) {
      if (signal?.aborted) throw failure('CRAWL_LEASE_LOST');
      const item = queue.shift();
      if (visited.has(item.url) || !policy.page(item.url)) continue;
      visited.add(item.url);
      let response;
      try {
        response = await loadForCapture(page, item.url, options, deadline);
      } catch (error) {
        if (!count) throw error;
        continue;
      }
      if (response && response.status() >= 400) { if (!count) throw failure('TARGET_UNREACHABLE'); continue; }
      const current = normalizedUrl(page.url());
      if (!policy.page(current) || captured.has(current)) continue;
      if (job.loginMethod === 'FORM_AUTO' && await visible(page.locator(options.passwordSelector || 'input[type="password"]')))
        throw failure('LOGIN_FAILED');
      // Expand navigation groups only; regular action buttons and data-entry forms remain untouched.
      const groups = page.locator('nav button[aria-expanded], [role="navigation"] button[aria-expanded]');
      for (let index = 0; index < Math.min(await groups.count(), 20); index++) {
        const group = groups.nth(index);
        if (await group.isVisible() && await group.getAttribute('aria-expanded') === 'false'
            && !dangerous.test(await group.innerText())) await group.click({ timeout: 1500 }).catch(() => {});
      }
      const evidence = await page.evaluate(() => {
        const visible = node => { const rect = node.getBoundingClientRect(); return rect.width > 0 && rect.height > 0 && getComputedStyle(node).visibility !== 'hidden'; };
        const links = [...document.querySelectorAll('a[href]')].filter(visible).slice(0, 500)
          .map(node => ({ text: (node.innerText || node.getAttribute('aria-label') || '').trim().slice(0, 300), href: node.href }));
        const elements = [...document.querySelectorAll('a,button,input,select,textarea,[role],h1,h2,h3')].filter(visible).slice(0, 500)
          .map(node => ({ tag: node.tagName.toLowerCase(), role: node.getAttribute('role') || '',
            label: (node.getAttribute('aria-label') || (node.matches('input,textarea,select') ? node.getAttribute('placeholder') : node.innerText) || '').trim().slice(0, 300) }));
        return { title: document.title.slice(0, 300), bodyText: document.body.innerText.slice(0, 32000), elements, links };
      });
      evidence.links = evidence.links.flatMap(link => {
        try { const href = normalizedUrl(link.href); return policy.page(href) && !dangerous.test(link.text) ? [{ ...link, href }] : []; }
        catch { return []; }
      });
      const masks = [page.locator('input,textarea,[contenteditable="true"]')];
      if (job.password) masks.push(page.getByText(job.password, { exact: false }));
      const screenshot = await page.screenshot({ type: 'jpeg', quality: 70, mask: masks, timeout: 10000 });
      if (screenshot.length > 2 * 1024 * 1024) throw failure('CAPTURE_TOO_LARGE');
      if (imageBytes + screenshot.length > 64 * 1024 * 1024) return { pageCount: count, stopReason: 'STORAGE_LIMIT' };
      const redact = value => typeof value === 'string' ? (job.password ? value.replaceAll(job.password, '[REDACTED]') : value)
        : Array.isArray(value) ? value.map(redact) : value && typeof value === 'object'
          ? Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, redact(entry)])) : value;
      const clean = redact(evidence);
      await onPage({ ...clean, url: current, pageIndex: count, screenshot: screenshot.toString('base64') });
      captured.add(current);
      imageBytes += screenshot.length; count++;
      if (item.depth < options.maxDepth) {
        for (const link of evidence.links) if (!visited.has(link.href) && queue.length < 5000) queue.push({ url: link.href, depth: item.depth + 1 });
      } else if (evidence.links.some(link => !visited.has(link.href))) depthLimited = true;
    }
    return { pageCount: count, stopReason: Date.now() >= deadline ? 'TIME_LIMIT' : count >= options.maxPages ? 'PAGE_LIMIT' : depthLimited ? 'DEPTH_LIMIT' : 'DONE' };
  } catch (error) {
    if (signal?.aborted) throw failure('CRAWL_LEASE_LOST');
    if (Date.now() >= deadline && count) return { pageCount: count, stopReason: 'TIME_LIMIT' };
    if (/CERT_|certificate/i.test(String(error.message))) throw failure('CERTIFICATE_ERROR');
    if (/net::|ECONN|ENOTFOUND|Timeout/i.test(String(error.message))) throw failure('TARGET_UNREACHABLE');
    throw error;
  } finally {
    clearTimeout(timeout); signal?.removeEventListener('abort', stop);
    await browser.close().catch(() => {});
  }
}
module.exports = { crawl, scope, normalizedUrl, glob, login, visible, dangerous, loadForCapture };
