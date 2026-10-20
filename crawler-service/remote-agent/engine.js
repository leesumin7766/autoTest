const { chromium } = require('playwright');

const dangerous = /(?:logout|signout|logoff|delete|remove|destroy|reset|payment|purchase|checkout|execute|terminate|삭제|탈퇴|결제|초기화|실행)/i;
function normalizedUrl(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error('CRAWL_SCOPE_INVALID');
  url.search = '';
  if (!url.hash.startsWith('#/')) url.hash = '';
  else url.hash = url.hash.split('?')[0];
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
        return origins.has(url.origin) && !url.username && !url.password && !dangerous.test(url.pathname + url.hash.split('?')[0])
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

async function crawl(job, dependencies) { return require('./walker').crawl(job, dependencies); }
module.exports = { crawl, scope, normalizedUrl, glob, login, visible, dangerous };
