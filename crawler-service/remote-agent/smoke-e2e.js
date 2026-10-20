// Optional cross-language smoke check. Requires the isolated test server and a seeded test submission.
const http = require('node:http');
const assert = require('node:assert/strict');
const { request } = require('./protocol');
const { runJob } = require('./agent');

async function main() {
  const serverUrl = process.argv[2];
  const submission = process.argv[3];
  if (new URL(serverUrl).port !== '18082' || !/^[a-f0-9-]{36}$/i.test(submission))
    throw new Error('Use isolated server port 18082 and a seeded autotest_test submission only');
  const base = `${serverUrl}/api/submissions/${submission}`;
  const admin = async (url, method = 'GET', body) => {
    const response = await fetch(url, { method, headers: { 'X-Member-Id': '8101', 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}) });
    if (!response.ok) throw new Error(`SMOKE_API_FAILED_${response.status}`);
    return response.json();
  };
  const site = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    if (req.url === '/login') return res.end('<title>Login</title><form method="post" action="/session"><input name="username"><input type="password" name="password"><button>Sign in</button></form>');
    if (req.url === '/session' && req.method === 'POST') {
      req.resume(); req.on('end', () => { res.writeHead(302, { Location: '/dashboard', 'Set-Cookie': 'session=fixture; Path=/; HttpOnly' }); res.end(); }); return;
    }
    if (!req.headers.cookie?.includes('session=fixture')) { res.writeHead(302, { Location: '/login' }); return res.end(); }
    if (req.url === '/dashboard') return res.end('<title>Fixture dashboard</title><h1>Overview</h1><a href="/reports">Reports</a><input value="masked">');
    if (req.url === '/reports') return res.end('<title>Fixture reports</title><h1>Report list</h1>');
    res.statusCode = 404; res.end('not found');
  });
  await new Promise(resolve => site.listen(0, process.env.AUTOTEST_SMOKE_LOCAL ? '0.0.0.0' : '127.0.0.1', resolve));
  let agent;
  try {
    const target = `http://${process.env.AUTOTEST_SMOKE_SITE_HOST || '127.0.0.1'}:${site.address().port}`;
    agent = await admin(`${serverUrl}/api/crawl-agents`, 'POST', { name: 'isolated cross-language smoke' });
    const config = { serverUrl, agentId: agent.agent.agentId, token: agent.token };
    const draft = { targetUrl: `${target}/dashboard`, loginMethod: 'FORM_AUTO',
      password: 'fixture-test-password', testEnvironmentConfirmed: true, automation: {
        productVersion: 'smoke', username: 'fixture', loginUrl: `${target}/login`, successUrl: `${target}/dashboard`,
        usernameSelector: '', passwordSelector: '', submitSelector: '', successSelector: '', allowedOrigins: [],
        includePaths: [], excludePaths: ['/logout*'], maxPages: 5, maxDepth: 3, maxDurationSeconds: 30, viewportWidth: 800, viewportHeight: 600,
      } };
    await admin(`${base}/playwright-setup`, 'PUT', draft);
    await request(config, 'heartbeat');
    for (const purpose of ['CONNECTION', 'LOGIN']) {
      await admin(`${base}/playwright-crawls`, 'POST', { agentId: config.agentId, executionMode: 'REMOTE', purpose, draft });
      await runJob(config, await request(config, 'claim'));
      const checked = (await admin(`${base}/playwright-crawls`)).checks[purpose];
      assert.equal(checked.status, 'COMPLETED', checked.errorCode);
    }
    if (process.env.AUTOTEST_SMOKE_LOCAL) {
      for (const purpose of ['CONNECTION', 'LOGIN']) {
        const launched = await admin(`${base}/playwright-crawls`, 'POST', { executionMode: 'LOCAL', purpose, draft });
        let checked = launched.checks[purpose];
        const deadline = Date.now() + 30000;
        while (['QUEUED', 'RUNNING'].includes(checked.status) && Date.now() < deadline) {
          await new Promise(resolve => setTimeout(resolve, 300));
          checked = (await admin(`${base}/playwright-crawls`)).checks[purpose];
        }
        assert.equal(checked.status, 'COMPLETED', checked.errorCode);
      }
    }
    const started = await admin(`${base}/playwright-crawls`, 'POST', { agentId: config.agentId });
    assert.equal(started.job.status, 'QUEUED');
    const job = await request(config, 'claim');
    assert.equal(job.password, 'fixture-test-password');
    await runJob(config, job);
    const result = await admin(`${base}/playwright-crawls`);
    assert.equal(result.job.status, 'COMPLETED', result.job.errorCode);
    assert.equal(result.job.pageCount, 2);
    const screenshot = await fetch(`${base}/playwright-crawls/${result.job.jobId}/pages/0/screenshot`, { headers: { 'X-Member-Id': '8101' } });
    assert.equal(screenshot.status, 200);
    assert.equal(screenshot.headers.get('content-type'), 'image/jpeg');
    assert.ok((await screenshot.arrayBuffer()).byteLength > 100);
    console.log(`Cross-language E2E passed: ${process.env.AUTOTEST_SMOKE_LOCAL ? 'local + ' : ''}remote connection/login checks, capture and screenshot retrieval.`);
  } finally {
    if (agent) await admin(`${serverUrl}/api/crawl-agents/${agent.agent.agentId}/revoke`, 'POST').catch(() => {});
    await new Promise(resolve => site.close(resolve));
  }
}
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
