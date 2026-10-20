const { createHash } = require('node:crypto');
const { normalizedUrl } = require('./engine');
const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');

async function visibleFrames(page, policy) {
  const frames = [];
  for (const frame of page.frames()) {
    try {
      if (!policy.origins.has(new URL(frame.url()).origin)) continue;
      let current = frame, shown = true;
      while (current.parentFrame()) {
        if (!(await (await current.frameElement()).isVisible())) { shown = false; break; }
        current = current.parentFrame();
      }
      if (shown) frames.push(frame);
    } catch { /* Detached or untrusted frame. */ }
  }
  return frames;
}

async function observe(page, policy) {
  const frames = [];
  for (const frame of await visibleFrames(page, policy)) {
    try {
      const data = await frame.evaluate(() => {
        const visible = node => { const r = node.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(node).visibility !== 'hidden'; };
        const clean = text => (text || '').trim().replace(/\s+/g, ' ');
        const blockers = [...document.querySelectorAll('[aria-busy="true"],[role="progressbar"]:not([aria-valuenow]),.spinner,.pf-v6-c-spinner,.pf-v5-c-spinner,.loading-mask,.loading-overlay')]
          .filter(node => visible(node) && (node.getAttribute('aria-busy') === 'true' || node.getBoundingClientRect().height >= 24))
          .map(node => node.getAttribute('aria-label') || node.getAttribute('role') || 'loading').slice(0, 10);
        const headings = [...document.querySelectorAll('main h1,main h2,[role="main"] h1,[role="main"] h2,h1')].filter(visible).map(node => clean(node.innerText).slice(0, 160)).slice(0, 20);
        const selected = [...document.querySelectorAll('[aria-selected="true"],[aria-current="page"],nav .active,nav .pf-m-current')].filter(visible).map(node => clean(node.innerText).slice(0, 160)).slice(0, 30);
        const text = (document.body?.innerText || '').slice(0, 32000);
        const elements = [...document.querySelectorAll('a,button,input,select,textarea,[role],h1,h2,h3')].filter(visible).slice(0, 500).map(node => ({ tag: node.tagName.toLowerCase(), role: node.getAttribute('role') || '', label: clean(node.getAttribute('aria-label') || (node.matches('input,textarea,select') ? node.getAttribute('placeholder') : node.innerText)).slice(0, 300) }));
        const links = [...document.querySelectorAll('a[href]')].filter(visible).slice(0, 500).map(node => ({ text: clean(node.innerText).slice(0, 300), href: node.href }));
        const pendingFrames = [...document.querySelectorAll('iframe[src]')].filter(visible).length;
        return { title: document.title.slice(0, 300), bodyText: text, headings, selected, blockers,
          ready: document.readyState !== 'loading' && Boolean(text.trim() || document.querySelector('canvas,svg,video,input,table,[role="grid"]')), pendingFrames, elements, links };
      });
      frames.push({ frame, rawUrl: frame.url(), url: normalizedUrl(frame.url()), ...data });
    } catch { /* Retry during the next readiness sample. */ }
  }
  return frames;
}

async function waitForScreen(page, policy, end) {
  let previous = '', stableSince = 0, frames = [];
  const began = Date.now();
  while (Date.now() < end) {
    frames = await observe(page, policy);
    const signature = hash(frames.map(frame => ({ url: frame.rawUrl, headings: frame.headings, selected: frame.selected,
      // Ignore live counters while still noticing a substantially changing layout.
      size: Math.floor(frame.bodyText.length / 100), elements: frame.elements.length })));
    const waitingForFrame = frames.some(frame => frame.pendingFrames) && frames.length < 2;
    const ready = frames.length > 0 && !waitingForFrame && frames.every(frame => frame.ready && !frame.blockers.length);
    if (ready && signature === previous) {
      if (Date.now() - stableSince >= 1000 && Date.now() - began >= 1500) return { frames, readiness: 'READY', waitedMs: Date.now() - began };
    } else stableSince = Date.now();
    previous = signature;
    await new Promise(resolve => setTimeout(resolve, Math.min(250, Math.max(1, end - Date.now()))));
  }
  return { frames, readiness: 'LOAD_TIMEOUT', waitedMs: Date.now() - began };
}

function screenState(result) {
  const frames = result.frames.map(frame => ({ url: frame.url, title: frame.title, headings: frame.headings, selected: frame.selected,
    blockers: frame.blockers, textLength: frame.bodyText.length }));
  // Raw URL/query state contributes only a hash; credentials and tokens are never persisted.
  const screenKey = hash(result.frames.map(frame => ({ url: frame.rawUrl, headings: frame.headings, selected: frame.selected,
    content: hash(frame.bodyText.replace(/\s+/g, ' ').trim()),
    structure: frame.elements.map(item => [item.tag, item.role, item.label.replace(/\d+/g, '#')]).slice(0, 100) })));
  return { screenKey, readiness: result.readiness, waitedMs: result.waitedMs, frames };
}

module.exports = { hash, visibleFrames, observe, waitForScreen, screenState };
