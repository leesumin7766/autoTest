const { test } = require('node:test');
const assert = require('node:assert/strict');
const https = require('node:https');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { probe } = require('./probe');
const { crawl } = require('./engine');

test('untrusted, expired and mismatched target certificate permits connection, login and capture', { timeout: 60000 }, async () => {
  // Public test-only key; never used for management-server communication.
  const server = https.createServer({
    key: readFileSync(join(__dirname, 'fixtures/untrusted-test-key.pem')),
    cert: readFileSync(join(__dirname, 'fixtures/untrusted-test-cert.pem')),
  }, (req, res) => {
    res.setHeader('Content-Type', 'text/html');
    if (req.url === '/login') return res.end('<form method="post" action="/session"><input name="username"><input type="password" name="password"><button>Sign in</button></form>');
    if (req.url === '/session') { res.writeHead(302, { Location: '/home', 'Set-Cookie': 'session=yes; Path=/' }); return res.end(); }
    if (!req.headers.cookie?.includes('session=yes')) { res.writeHead(302, { Location: '/login' }); return res.end(); }
    res.end('<title>Home</title><nav><a href="/logout">Sign out</a></nav>');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `https://127.0.0.1:${server.address().port}`;
  const job = { targetUrl: `${origin}/home`, loginMethod: 'FORM_AUTO', password: 'test-only', automation: {
    loginUrl: `${origin}/login`, username: 'tester', allowedOrigins: [], includePaths: [], excludePaths: ['/logout*'],
    maxPages: 1, maxDepth: 0, maxDurationSeconds: 30, viewportWidth: 800, viewportHeight: 600,
  } };
  try {
    assert.equal((await probe({ ...job, purpose: 'CONNECTION' })).httpStatus, 200);
    assert.equal((await probe({ ...job, purpose: 'LOGIN' })).loginVerified, true);
    const pages = [];
    await crawl(job, { onPage: async page => pages.push(page) });
    assert.equal(pages.length, 1);
    assert.equal(pages[0].title, 'Home');
  } finally { await new Promise(resolve => server.close(resolve)); }
});
