const express = require('express');
const { chromium } = require('playwright');
const app = express();
app.use(express.json({ limit: '8mb' }));
const { timingSafeEqual } = require('node:crypto');
const { runJob } = require('./remote-agent/agent');
let localBusy = false;
app.post('/api/v1/browser-jobs', (req, res) => {
  const expected = Buffer.from(process.env.PRODUCT_DESCRIPTION_INTERNAL_TOKEN || '');
  const provided = Buffer.from(req.get('X-Internal-Token') || '');
  if (!expected.length || expected.length !== provided.length || !timingSafeEqual(expected, provided)) return res.sendStatus(401);
  if (localBusy) return res.sendStatus(429);
  const job = req.body;
  if (!job.jobId || !job.claimToken || !['CONNECTION', 'LOGIN', 'CRAWL'].includes(job.purpose)) return res.sendStatus(400);
  localBusy = true;
  res.status(202).json({ accepted: true });
  const send = async (_config, _action, body) => {
    const response = await fetch(`${process.env.MANAGEMENT_SERVICE_URL || 'http://test-management-service:8082'}/api/internal/playwright-crawls/events`, {
      method: 'POST', signal: AbortSignal.timeout(10000), redirect: 'error',
      headers: { 'Content-Type': 'application/json', 'X-Internal-Token': process.env.PRODUCT_DESCRIPTION_INTERNAL_TOKEN }, body: JSON.stringify(body),
    });
    if (!response.ok) { const error = new Error(response.status === 409 ? 'CRAWL_LEASE_LOST' : 'LOCAL_CALLBACK_FAILED'); error.status = response.status; throw error; }
    return response.json();
  };
  void runJob({}, job, { request: send }).finally(() => { localBusy = false; });
});
app.get('/', (req,res)=> res.json({status:'crawler-service running'}));
app.post('/crawl', async (req,res)=>{
  const { url } = req.body;
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const apis = [];
  page.on('request', r => apis.push({method: r.method(), url: r.url()}));
  await page.goto(url, {waitUntil:'networkidle'});
  await browser.close();
  res.json({apis: apis.slice(0,50)});
});
app.listen(8006, ()=> console.log('crawler on 8006'));
