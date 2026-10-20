const { normalizedUrl, dangerous } = require('./engine');
const { visibleFrames, hash } = require('./screen');

async function navigation(page, policy) {
  const result = [];
  let contextPath = [];
  for (const frame of await visibleFrames(page, policy)) {
    try {
      const frameUrl = normalizedUrl(frame.url());
      const items = await frame.evaluate(() => {
        const selectors = 'nav,[role="navigation"],[role="menu"],[role="menubar"],[role="tablist"],aside,.sidebar,#sidebar,header,[role="banner"],.ct-topnav-content,[role="toolbar"],footer,[role="contentinfo"]';
        const visible = node => { const r = node.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(node).visibility !== 'hidden'; };
        const label = node => (node?.getAttribute('aria-label') || node?.innerText || '').trim().replace(/\s+/g, ' ').slice(0, 160);
        const ownLabel = container => [...container.querySelectorAll('a,button,[role="menuitem"],[role="tab"],span')]
          .find(node => node.closest('li,[role="treeitem"]') === container && visible(node) && label(node));
        const selector = node => {
          const parts = [];
          for (let current = node; current && current !== document.documentElement; current = current.parentElement) {
            if (current.id) { parts.unshift('#' + CSS.escape(current.id)); break; }
            const siblings = [...current.parentElement.children].filter(other => other.tagName === current.tagName);
            parts.unshift(current.tagName.toLowerCase() + ':nth-of-type(' + (siblings.indexOf(current) + 1) + ')');
          }
          return parts.join(' > ');
        };
        const entries = [], seen = new Set();
        for (const root of document.querySelectorAll(selectors)) {
          for (const node of root.querySelectorAll('a,button,[role="menuitem"],[role="tab"]')) {
            if (seen.has(node) || !visible(node) || !label(node) || node.getAttribute('aria-haspopup') === 'listbox') continue;
            if (node.closest(selectors) !== root) continue;
            seen.add(node);
            const parents = [];
            for (let ancestor = node.parentElement; ancestor && ancestor !== root; ancestor = ancestor.parentElement) {
              if (ancestor.matches('section[aria-labelledby],[role="group"][aria-labelledby]')) {
                const heading = document.getElementById(ancestor.getAttribute('aria-labelledby'));
                if (heading && label(heading)) parents.unshift(label(heading));
              } else if (ancestor.matches('li,[role="treeitem"]') && ownLabel(ancestor) !== node) {
                const parent = ownLabel(ancestor); if (parent) parents.unshift(label(parent));
              }
            }
            const href = node.getAttribute('href');
            const location = node.closest('header,[role="banner"],.ct-topnav-content') ? 'HEADER' : node.closest('footer,[role="contentinfo"]') ? 'FOOTER'
              : node.closest('aside,.sidebar,#sidebar') || root.matches('nav,[role="navigation"],[role="menu"],[role="menubar"]') ? 'SIDEBAR' : 'CONTENT';
            const group = node.hasAttribute('aria-expanded') && (node.getAttribute('aria-controls') || node.nextElementSibling?.matches('ul,[role="group"],[role="menu"]'));
            const tab = node.getAttribute('role') === 'tab' || root.matches('[role="tablist"],.pf-m-subnav');
            const section = root.getAttribute('aria-label') || (tab ? '탭' : '메뉴');
            entries.push({ path: [section.slice(0, 160), ...parents, label(node)].slice(0, 12),
              url: href && href !== '#' ? node.href : '',
              kind: group && !['HEADER','FOOTER'].includes(location) ? 'GROUP' : tab ? 'TAB' : href && href !== '#' ? 'LINK' : 'BUTTON',
              selected: node.getAttribute('aria-selected') === 'true' || node.getAttribute('aria-current') === 'page',
              location, canExplore: location !== 'HEADER' && !node.form && !root.matches('[role="toolbar"]') && !/administrat|elevat|superuser|권한|관리.*접근/i.test(label(node)),
              selector: selector(node), order: entries.length });
            if (entries.length >= 500) return entries;
          }
        }
        return entries;
      });
      if (frame === page.mainFrame()) contextPath = items.find(item => item.selected && item.kind === 'LINK')?.path || [];
      for (const item of items) {
        if (frame !== page.mainFrame() && contextPath.length) item.path = [...contextPath, ...item.path].slice(0, 12);
        if (item.path.some(part => dangerous.test(part))) item.canExplore = false;
        if (item.url) { try { if (!policy.page(item.url)) { item.canExplore = false; item.url = ''; } } catch { item.canExplore = false; item.url = ''; } }
        const key = hash({ path: item.path, kind: item.kind, control: item.selector,
          frame: frame === page.mainFrame() ? 'main' : frameUrl.split('#')[0] });
        const action = { menuKey: key, selector: item.selector, frameUrl, main: frame === page.mainFrame(), label: item.path.at(-1), kind: item.kind };
        delete item.selector;
        result.push({ ...item, url: item.url ? normalizedUrl(item.url) : '', frameUrl, menuKey: key, _action: action });
        if (result.length >= 500) return result;
      }
    } catch { /* Detached frames are observed after the next navigation. */ }
  }
  return result;
}

async function clickNavigation(page, action, policy, end) {
  if (action.kind === 'GROUP' || dangerous.test(action.label)) throw new Error('NAVIGATION_NOT_AVAILABLE');
  const frames = await visibleFrames(page, policy);
  const frame = action.main ? page.mainFrame() : frames.find(frame => normalizedUrl(frame.url()).split('#')[0] === action.frameUrl.split('#')[0]);
  if (!frame) throw new Error('NAVIGATION_NOT_AVAILABLE');
  const node = frame.locator(action.selector);
  const valid = await node.evaluate((node, label) => {
    const text = (node.getAttribute('aria-label') || node.innerText || '').trim().replace(/\s+/g, ' ').slice(0, 160);
    return text === label && !node.form && Boolean(node.closest('nav,[role="navigation"],[role="menu"],[role="menubar"],[role="tablist"],aside,.sidebar,#sidebar'));
  }, action.label);
  if (!valid) throw new Error('NAVIGATION_NOT_AVAILABLE');
  await node.click({ timeout: Math.max(1, Math.min(10000, end - Date.now())) });
}

async function expandNavigation(page, policy) {
  for (const frame of await visibleFrames(page, policy)) {
    const groups = frame.locator('nav button[aria-expanded="false"], [role="navigation"] button[aria-expanded="false"], aside button[aria-expanded="false"]');
    for (let index = 0; index < Math.min(await groups.count(), 20); index++) {
      const group = groups.nth(index);
      const safe = await group.evaluate(node => !node.form && !node.hasAttribute('aria-haspopup') &&
        (node.nextElementSibling?.matches('ul,[role="group"],[role="menu"]') || document.getElementById(node.getAttribute('aria-controls'))?.matches('ul,[role="group"],[role="menu"]'))).catch(() => false);
      if (safe && await group.isVisible() && !dangerous.test(await group.innerText())) await group.click({ timeout: 1500 }).catch(() => {});
    }
  }
}

module.exports = { navigation, clickNavigation, expandNavigation };
