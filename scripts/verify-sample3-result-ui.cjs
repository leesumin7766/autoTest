const fs=require('node:fs/promises');
const assert=require('node:assert/strict');
const {chromium}=require('../crawler-service/node_modules/playwright');
(async()=>{
 const browser=await chromium.launch();try{
  const page=await browser.newPage({viewport:{width:1440,height:1100}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:4173/');await page.getByLabel('문서 세트 ID').fill('fb53af4b-add4-4b8f-86cf-0d9687d9109e');await page.getByRole('button',{name:'세트 조회',exact:true}).click();await page.getByRole('button',{name:'05 시험 실행',exact:true}).click();
  const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:'TC-007-001',exact:true})});await row.waitFor();
  const pending=page.waitForResponse(response=>response.url().includes('/evidence?caseId='));await row.getByRole('button',{name:'실행 상세',exact:true}).click();const response=await pending;assert.equal(response.status(),200);assert.equal(response.request().headers()['x-member-id'],'1');
  const detail=page.locator('.tc-case-detail');await detail.getByText('Qwen 실행 계획',{exact:true}).waitFor();await detail.locator('summary').filter({hasText:'BROWSER_OBSERVATION'}).last().click();await detail.locator('img').last().waitFor();
  await page.screenshot({path:'output/sample3-validation-20261022/05-실제결과상세.png',fullPage:true});assert.deepEqual(errors,[]);console.log('PASS: actual failed run and authenticated lazy evidence load, plans and screenshot render without UI exceptions');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
