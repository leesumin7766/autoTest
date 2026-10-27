// Isolated UI fixture; never cancels a real job.
const {chromium}=require('playwright');const fs=require('node:fs/promises');const assert=require('node:assert/strict');
(async()=>{
 const file='frontend/executor-status-qa.html';
 await fs.writeFile(file,`<html lang="ko"><meta charset="utf-8"><div id="root"></div><script type="module">import React from'react';import{createRoot}from'react-dom/client';import{ExecutorStatus}from'/src/ExecutorStatus.tsx';import'/src/workspace.css';createRoot(document.getElementById('root')).render(React.createElement(ExecutorStatus,{memberId:'1'}))</script></html>`);
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();let stopped=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/**',route=>{const p=new URL(route.request().url()).pathname;
   if(p==='/api/crawl-agents/local/status')return route.fulfill({json:{connected:true,busy:!stopped,jobs:stopped?[]:[{id:'run',submissionId:'different-set',kind:'EXECUTION',purpose:'TEST',status:'RUNNING',createdAt:new Date().toISOString(),pageCount:0}]}});
   assert.equal(p,'/api/submissions/different-set/executions/run/cancel');assert.equal(route.request().method(),'POST');stopped=true;return route.fulfill({json:{}});
  });
  await page.goto('http://127.0.0.1:4174/executor-status-qa.html');
  const trigger=page.getByRole('button',{name:'실행기 점유 현황',exact:true});
  await trigger.getByText('사용 중',{exact:true}).waitFor();
  assert.equal(await trigger.innerText(),'실행기 사용 중');
  await trigger.click();
  await page.getByText('different-set',{exact:true}).waitFor();
  await page.getByRole('button',{name:'작업 중지',exact:true}).click();assert.equal(stopped,false);
  await page.getByRole('button',{name:'계속 실행',exact:true}).click();assert.equal(stopped,false);
  await page.getByRole('button',{name:'작업 중지',exact:true}).click();await page.getByRole('button',{name:'중지 확인',exact:true}).click();
  await page.getByText('실행기를 사용할 수 있습니다.',{exact:true}).waitFor();assert.equal(stopped,true);
  await page.getByRole('button',{name:'닫기',exact:true}).click();assert.equal(await page.locator('dialog').isVisible(),false);
  assert.deepEqual(errors,[]);console.log('PASS: cross-set job display, stop confirmation/cancel, correct job endpoint, released status, modal close');
 }finally{await browser.close();await fs.unlink(file)}
})().catch(e=>{console.error(e);process.exitCode=1});
