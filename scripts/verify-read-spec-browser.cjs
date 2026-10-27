// Isolated browser/API wiring check; no live product, stored account, Qwen, or Jev calls.
const express = require('express');
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const { register, sessions } = require('../crawler-service/tc-execution');

(async () => {
  const app = express(); app.use(express.json({ limit: '8mb' }));
  let busy = false;
  register(app, (req, res, next) => req.get('X-Internal-Token') === 'read-spec-fixture' ? next() : res.sendStatus(403),
    () => { if (busy) return false; busy = true; return true; }, () => { busy = false; });
  app.get('/fixture/:mode/:screen', (req, res) => {
    if (req.params.screen === 'reports') return res.type('html').send(`<main><h1>Reports</h1><p>${req.params.mode === 'good' ? 'REPORT_READY' : 'REPORT_BROKEN'}</p></main>`);
    res.type('html').send(`<nav><a href="/fixture/${req.params.mode}/reports">Reports</a></nav><main><h1>Dashboard</h1></main>`);
  });
  const server = app.listen(0, '0.0.0.0');
  await new Promise(resolve => server.once('listening', resolve));
  const port = server.address().port;
  try {
    const args = ['run', '--rm', '--mount', `type=bind,source=${path.resolve('ai-service')},target=/test,readonly`, '--workdir', '/test',
      '-e', `CRAWLER_SERVICE_URL=http://host.docker.internal:${port}`, '-e', `READ_SPEC_FIXTURE_ORIGIN=http://127.0.0.1:${port}`,
      '-e', 'PRODUCT_DESCRIPTION_INTERNAL_TOKEN=read-spec-fixture', '--entrypoint', 'python', 'autotest-ai-service', 'verify_read_spec_browser.py'];
    const child = spawn(process.env.DOCKER_EXE || 'docker', args, { windowsHide: true });
    let output = ''; child.stdout.on('data', chunk => { output += chunk; }); child.stderr.on('data', chunk => process.stderr.write(chunk));
    const code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', resolve); });
    if (output) {
      const report = JSON.parse(output);
      const destination = path.resolve('outputs/read-specification-20261027/browser-validation.json');
      await fs.mkdir(path.dirname(destination), { recursive: true });
      await fs.writeFile(destination, JSON.stringify(report, null, 2));
      console.log(JSON.stringify(report, null, 2));
    }
    if (code) throw new Error(`Fixture validation failed (${code})`);
  } finally {
    for (const session of sessions.values()) await session.browser.close();
    sessions.clear();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
