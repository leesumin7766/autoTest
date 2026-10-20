const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { crawl, normalizedUrl, scope, glob } = require('./engine');

function options(origin) {
  return { targetUrl: `${origin}/dashboard`, loginMethod: 'FORM_AUTO', password: 'test-secret-!42', automation: {
    username: 'qa-user', loginUrl: `${origin}/login`, successUrl: `${origin}/dashboard`, successSelector: '',
    usernameSelector: '', passwordSelector: '', submitSelector: '', allowedOrigins: [], includePaths: [],
    excludePaths: ['/private*', '/logout*'], maxPages: 10, maxDepth: 3, maxDurationSeconds: 30, viewportWidth: 800, viewportHeight: 600,
  } };
}
test('scope limits origins, excludes paths, strips query secrets and preserves hash routes', () => {
  const job = options('https://example.test');
  const policy = scope(job);
  assert.equal(policy.page('https://example.test/dashboard'), true);
  for (const url of ['https://other.test/', 'https://example.test/logout', 'https://example.test/private/1', 'https://example.test/delete/1'])
    assert.equal(policy.page(url), false);
  assert.equal(normalizedUrl('https://example.test/?token=secret#/reports'), 'https://example.test/#/reports');
  assert.equal(glob('/a.*', '/abc'), false);
});
test('real browser logs in, captures linked screens, masks input and blocks writes/outside hosts', { timeout: 60000 }, async () => {
  let writes = 0, logouts = 0, outsideHits = 0;
  const outside = http.createServer((_req, res) => { outsideHits++; res.end('outside'); });
  await new Promise(resolve => outside.listen(0, '127.0.0.1', resolve));
  const outsideOrigin = `http://127.0.0.1:${outside.address().port}`;
  const server = http.createServer((req, res) => {
    if (req.url === '/login') { res.setHeader('Content-Type', 'text/html'); return res.end('<form method="POST" action="/session"><input name="username"><input name="password" type="password"><button>로그인</button></form>'); }
    if (req.url === '/session' && req.method === 'POST') {
      let body = ''; req.on('data', chunk => { body += chunk; }); req.on('end', () => {
        if (!body.includes('qa-user') || !body.includes('test-secret-')) { res.statusCode = 401; return res.end('bad login'); }
        res.writeHead(302, { 'Set-Cookie': 'session=test; Path=/; HttpOnly', Location: '/dashboard' }); res.end();
      }); return;
    }
    if (!req.headers.cookie?.includes('session=test')) { res.writeHead(302, { Location: '/login' }); return res.end(); }
    if (req.url === '/write') { writes++; return res.end('should not happen'); }
    if (req.url === '/logout') { logouts++; return res.end('should not happen'); }
    res.setHeader('Content-Type', 'text/html');
    if (req.url === '/dashboard') return res.end(`<title>Dashboard</title><h1>Overview</h1><nav><a href="/reports">Reports</a><a href="/logout">Logout</a><a href="/private">Private</a></nav><input value="test-secret-!42"><img src="${outsideOrigin}/pixel"><script>fetch('/write',{method:'POST'})</script>`);
    if (req.url === '/reports') return res.end('<title>Reports</title><h1>Report list</h1><a href="/dashboard">Home</a>');
    res.statusCode = 404; res.end('not found');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const job = options(`http://127.0.0.1:${server.address().port}`);
  const pages = [];
  try {
    const result = await crawl(job, { onPage: async page => pages.push(page) });
    assert.equal(result.pageCount, 2);
    assert.deepEqual(pages.map(page => page.title), ['Dashboard', 'Reports']);
    assert.ok(Buffer.from(pages[0].screenshot, 'base64').length > 100);
    assert.equal(JSON.stringify(pages).includes(job.password), false);
    assert.equal(writes, 0); assert.equal(logouts, 0); assert.equal(outsideHits, 0);
    const limited = [];
    const result2 = await crawl({ ...job, automation: { ...job.automation, maxPages: 1 } }, { onPage: async page => limited.push(page) });
    assert.equal(result2.stopReason, 'PAGE_LIMIT'); assert.equal(limited.length, 1);
  } finally { await Promise.all([new Promise(resolve => server.close(resolve)), new Promise(resolve => outside.close(resolve))]); }
});
