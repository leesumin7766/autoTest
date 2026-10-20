const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { probe } = require('./probe');
const { screenState } = require('./screen');

test('same URL and controls with different body content have different state fingerprints', () => {
  const frame = { url: 'https://example.test/', rawUrl: 'https://example.test/', title: 'Product', headings: [], selected: [], blockers: [], elements: [], bodyText: 'Record 1' };
  const first = screenState({ frames: [frame], readiness: 'READY', waitedMs: 1500 });
  const second = screenState({ frames: [{ ...frame, bodyText: 'Record 2' }], readiness: 'READY', waitedMs: 1500 });
  assert.notEqual(first.screenKey, second.screenKey);
  assert.equal(JSON.stringify(first).includes('Record 1'), false);
});

test('connection probe is unauthenticated; login probe confirms login without capturing pages', { timeout: 60000 }, async () => {
  let posts = 0;
  const server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    if (req.url === '/denied') { res.statusCode = 403; return res.end('access denied'); }
    if (req.url === '/login') return res.end('<form hidden method="post" action="/session"><input name="username"><input type="password" name="password"><button>Sign in</button></form><script>setTimeout(() => document.querySelector("form").hidden = false, 800)</script>');
    if (req.url === '/session') {
      posts++; let body = ''; req.on('data', chunk => { body += chunk; }); req.on('end', () => {
        if (!body.includes('correct-secret')) { res.writeHead(302, { Location: '/login' }); return res.end(); }
        res.writeHead(302, { Location: '/home', 'Set-Cookie': 'session=yes; Path=/' }); res.end();
      }); return;
    }
    if (req.url === '/home' && req.headers.cookie?.includes('session=yes')) return res.end('<title>Home</title><nav><a href="/logout">Sign out</a></nav>');
    res.writeHead(302, { Location: '/login' }); res.end();
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const job = { targetUrl: `${origin}/home`, loginMethod: 'FORM_AUTO', password: 'correct-secret',
    automation: { loginUrl: `${origin}/login`, username: 'tester', successUrl: '', successSelector: '',
      usernameSelector: '', passwordSelector: '', submitSelector: '', allowedOrigins: [] } };
  try {
    const connection = await probe({ ...job, purpose: 'CONNECTION' });
    assert.equal(connection.httpStatus, 200); assert.equal(connection.loginVerified, false); assert.equal(posts, 0);
    const denied = await probe({ ...job, purpose: 'CONNECTION', targetUrl: `${origin}/denied` });
    assert.equal(denied.httpStatus, 403);
    const loggedIn = await probe({ ...job, purpose: 'LOGIN' });
    assert.equal(loggedIn.loginVerified, true); assert.equal(loggedIn.httpStatus, 200); assert.equal(posts, 1);
    await assert.rejects(probe({ ...job, purpose: 'LOGIN', password: 'wrong' }), /LOGIN_FAILED/);
    await assert.rejects(probe({ ...job, purpose: 'CONNECTION', targetUrl: 'http://127.0.0.1:1/' }), /TARGET_UNREACHABLE/);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
