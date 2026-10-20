const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { crawl } = require('./engine');

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
