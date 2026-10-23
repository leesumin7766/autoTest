// Read-only check against the user's existing prepared run. Never starts a test.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1100}});page.setDefaultTimeout(15000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/**',async route=>{
   if(!['GET','HEAD','OPTIONS'].includes(route.request().method()))throw Error('Read-only UI QA attempted mutation: '+route.request().url());
   return route.continue();
  });
  await page.goto('http://127.0.0.1:4173/');
  await page.getByLabel('문서 세트 ID').fill('77ad4076-6afc-496e-b975-4c9b5231758e');
  await page.getByRole('button',{name:'세트 조회',exact:true}).click();
  await page.getByRole('button',{name:'05 시험 실행',exact:true}).click();
  const panel=page.getByRole('region',{name:'시험 실행 관리',exact:true});
  await panel.getByRole('button',{name:'실행 대기 1,176개',exact:true}).waitFor();
  assert.equal(await panel.getByRole('progressbar',{name:'TC 수행 완료율'}).getAttribute('value'),'0');
  assert.equal(await panel.locator('.run-clock b').innerText(),'00:00:00');
  assert.equal(await panel.getByRole('button',{name:'미완료만 이어서 준비',exact:true}).count(),0);
  assert(!(await panel.innerText()).includes('89 / 1265'));
  assert(!(await panel.innerText()).includes('완료된 미판정은 결과 검토'));
  await panel.getByRole('button',{name:'입력·환경 필요 21개',exact:true}).click();
  assert.equal(await panel.locator('.tc-table tbody tr').count(),20);
  await panel.getByRole('button',{name:'실행 상세',exact:true}).first().click();
  await panel.getByText('준비·수행 상태와 사유',{exact:true}).waitFor();
  assert((await panel.locator('.tc-case-detail').innerText()).includes('현재 파일 공급 기능 미지원'));
  await panel.getByRole('button',{name:'상세 닫기',exact:true}).click();
  await panel.getByRole('button',{name:'별도 수행 필요 68개',exact:true}).click();
  assert.equal(await panel.locator('.tc-table tbody tr').count(),20);
  assert((await panel.locator('.tc-table').innerText()).includes('별도 수행 필요'));
  await panel.getByRole('button',{name:'전체 1,265개',exact:true}).click();
  await fs.mkdir('output/test-execution',{recursive:true});
  await panel.screenshot({path:'output/test-execution/preparation-counts.png'});
  await panel.locator(':scope > details').nth(1).screenshot({path:'output/test-execution/preparation-summary.png'});
  assert.deepEqual(errors,[]);
  console.log('PASS: real 1265-TC prepared run: 0 performed; 1176 waiting; 21 input; 68 separate; filters and no resume/finished notice; no mutations.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
