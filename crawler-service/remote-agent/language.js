// Language setup is separate from menu exploration. Never guess storage keys or change account settings.
function inspectLanguage() {
  const visible = node => node.getBoundingClientRect().width > 0 && node.getBoundingClientRect().height > 0;
  // Inspect UI labels, not table data or document content, which may be Korean on an English UI.
  const roots = [...document.querySelectorAll('nav,[role="navigation"],aside,h1,h2,label,button,[role="tab"]')].filter(visible);
  const text = roots.map(node => node.innerText).join(' ').slice(0, 12000);
  const korean = (text.match(/[가-힣]/g) || []).length, latin = (text.match(/[a-z]/gi) || []).length;
  const declared = (document.documentElement.lang || '').toLowerCase();
  return { detected: korean >= 8 ? 'ko' : latin >= 30 && korean < 8 ? 'en' : declared.startsWith('ko') ? 'ko' : declared.startsWith('en') ? 'en' : 'unknown' };
}
function languagePolicy(resume) {
  const pages = (resume?.pages || []).filter(page => page.transition?.scope !== 'PRE_AUTH');
  const prior = pages.map(page => page.transition?.collectionLanguage).find(Boolean);
  const legacy = !!resume?.pageCount && !prior;
  return { prior, legacy, browserLocale: legacy ? undefined : prior?.browserLocale || 'ko-KR', preferKorean: !legacy && (!prior || prior.detected === 'ko') };
}
async function preferKorean(page, policy, { timeout = 5000 } = {}) {
  const result = { requested: 'ko', browserLocale: policy.browserLocale || 'default', status: 'UNCONFIRMED', detected: 'unknown', switched: false };
  let opened=false;
  try {
    const before = await page.evaluate(inspectLanguage);
    if (policy.legacy) return { ...result, ...before, status: 'LEGACY_RESUME' };
    if (!policy.preferKorean) return { ...result, ...before, status: 'RESUME_PRESERVED' };
    if (before.detected === 'ko') return { ...result, ...before, status: 'KOREAN_CONFIRMED' };
    const korean = page.getByRole('button', { name: /^(한국어|Korean|KO)$/i }).or(page.getByRole('option', { name: /^(한국어|Korean|KO)$/i }))
      .or(page.getByRole('menuitem', { name: /^(한국어|Korean|KO)$/i }));
    let candidates = korean.locator('visible=true');
    if (await candidates.count() === 0) {
      const opener = page.getByRole('button', { name: /^(Language|언어|언어 선택|Select language)$/i }).locator('visible=true');
      if (await opener.count() === 1) { await opener.click({ timeout }); opened=true; candidates = korean.locator('visible=true'); }
    }
    if (await candidates.count() !== 1) return { ...result, ...before };
    const target = candidates.first();
    if(await target.evaluate(node=>node.tagName==='BUTTON'&&!!node.closest('form')&&node.type!=='button'))return {...result,...before};
    if (await target.isDisabled() || await target.getAttribute('aria-selected') === 'true' || await target.getAttribute('aria-pressed') === 'true') return { ...result, ...before };
    await target.click({ timeout });
    await page.waitForFunction(`(${inspectLanguage.toString()})().detected === 'ko'`, null, { timeout });
    const after = await page.evaluate(inspectLanguage);
    return { ...result, ...after, switched: true, status: after.detected === 'ko' ? 'KOREAN_CONFIRMED' : 'UNCONFIRMED' };
  } catch {
    if (page.isClosed()) throw new Error('BROWSER_PAGE_CLOSED');
    const actual = await page.evaluate(inspectLanguage).catch(() => ({ detected: 'unknown' }));
    return { ...result, ...actual };
  } finally { if(opened&&!page.isClosed())await page.keyboard.press('Escape').catch(()=>{}); }
}
module.exports = { inspectLanguage, languagePolicy, preferKorean };
