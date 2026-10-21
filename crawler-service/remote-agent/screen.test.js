const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { crawl } = require('./engine');
const { navigation, clickNavigation } = require('./navigation');
const { chromium } = require('playwright');

test('preferences remain observed and semantic menu replay survives DOM reordering', async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    await page.route('https://fixture.example/**', route => route.fulfill({body:'<html></html>',contentType:'text/html'}));
    await page.goto('https://fixture.example/');
    await page.setContent('<aside><button>Collapse sidebar</button><button>한국어</button><button>Dark Mode</button><nav><a href="/jobs">Jobs</a><button role="tab">Details</button></nav></aside><nav aria-label="pagination"><button>Next page</button></nav><h1>Initial</h1>');
    await page.locator('[role="tab"]').evaluate(node => node.onclick=()=>document.querySelector('h1').textContent='Details loaded');
    const policy = { origins: new Set(['https://fixture.example']), page: () => true };
    const nodes = await navigation(page, policy);
    assert.ok(!nodes.some(node=>node.path.at(-1)==='Next page'));
    for (const label of ['Collapse sidebar','한국어','Dark Mode']) assert.equal(nodes.find(node=>node.path.at(-1)===label).canExplore,false);
    const details=nodes.find(node=>node.path.at(-1)==='Details');
    await page.locator('aside nav').evaluate(node=>node.prepend(document.createElement('button')));
    await clickNavigation(page,details._action,policy,Date.now()+5000);
    assert.equal(await page.locator('h1').innerText(),'Details loaded');
  } finally { await browser.close(); }
});

test('same-URL iframe tabs keep separate click evidence and await asynchronous content', { timeout: 90000 }, async () => {
  const server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'text/html');
    if (req.url === '/frame') return res.end(`<div class="spinner" style="width:40px;height:40px">Loading...</div><main hidden>
      <nav aria-label="Views"><button role="tab" id="summary" aria-selected="true">Summary</button><button role="tab" id="history" aria-selected="false">History</button><button role="tab" id="details" aria-selected="false">Details</button></nav>
      <h1 id="heading">Summary records</h1><p>Loaded iframe content</p></main><script>
      setTimeout(()=>{document.querySelector('.spinner').remove();document.querySelector('main').hidden=false},2200);
      for(const button of document.querySelectorAll('button')) button.onclick=()=>{
        document.querySelectorAll('button').forEach(n=>n.setAttribute('aria-selected',String(n===button)));
        document.getElementById('heading').textContent=button.textContent+' records';
        window.onbeforeunload=button.id==='history'?()=> 'Leave this view?':null;
      };</script>`);
    res.end('<title>Product</title><header><button>Help</button><button>Administrative access</button></header><nav aria-label="Global"><section aria-labelledby="system"><h2 id="system">System</h2><a href="/" aria-current="page">Views</a></section></nav><iframe src="/frame" style="width:800px;height:500px"></iframe><footer><a href="/policy">Policy</a></footer>');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const targetUrl = `http://127.0.0.1:${server.address().port}/`;
  const job = { targetUrl, loginMethod: 'NONE', automation: { allowedOrigins: [], includePaths: [], excludePaths: [],
    maxPages: 8, maxDepth: 2, maxDurationSeconds: 60, pageLoadTimeoutSeconds: 10, viewportWidth: 1000, viewportHeight: 800 } };
  try {
    const pages = [];
    await crawl(job, { onPage: async page => pages.push(page) });
    const summary = pages.find(page => page.transition.menuPath.at(-1) === 'Summary');
    const history = pages.find(page => page.transition.menuPath.at(-1) === 'History');
    assert.ok(pages.some(page => page.transition.menuPath.at(-1) === 'Details'), 'navigation must leave a view with a beforeunload confirmation');
    assert.ok(summary && history);
    assert.equal(summary.url, history.url);
    assert.notEqual(summary.screenState.screenKey, history.screenState.screenKey);
    assert.notEqual(summary.transition.menuKey, history.transition.menuKey);
    assert.equal(history.transition.outcome, 'CONFIRMED');
    assert.ok(history.bodyText.includes('History records'));
    assert.ok(pages[0].bodyText.includes('Loaded iframe content'));
    assert.equal(pages[0].screenState.readiness, 'READY');
    assert.ok(pages[0].screenState.waitedMs >= 2200);
    assert.ok(pages[0].navigation.some(node => node.path.join('/') === 'Global/System/Views/Views/History'));
    assert.ok(pages[0].navigation.some(node => node.location === 'HEADER' && node.path.at(-1) === 'Administrative access' && node.canExplore === false));
    assert.ok(pages[0].navigation.some(node => node.location === 'FOOTER' && node.path.at(-1) === 'Policy'));
    const timed = [];
    const result = await crawl({ ...job, automation: { ...job.automation, pageLoadTimeoutSeconds: 1 } }, { onPage: async page => timed.push(page) });
    assert.equal(timed[0].screenState.readiness, 'LOAD_TIMEOUT');
    assert.equal(result.incompleteCount, 1);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
