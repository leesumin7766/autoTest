// Isolated fixture: no real product or persisted user data is touched.
const {chromium}=require('playwright');const fs=require('node:fs/promises');const assert=require('node:assert/strict');
(async()=>{
 const html='frontend/login-route-qa.html';
 await fs.writeFile(html,`<html lang="ko"><meta charset="utf-8"><div id="root"></div><script type="module">import React from'react';import{createRoot}from'react-dom/client';import{FunctionalMenuPanel}from'/src/FunctionalMenuPanel.tsx';import{MenuTcPanel}from'/src/MenuTcPanel.tsx';createRoot(document.getElementById('root')).render(React.createElement('main',null,React.createElement(FunctionalMenuPanel,{submissionId:'qa',memberId:'1',ready:true}),React.createElement(MenuTcPanel,{submissionId:'qa',memberId:'1'})))</script></html>`);
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));let generated;
  const authentication={required:true,targetUrl:'https://example.com/',loginUrl:'https://example.com/login',policyNote:'정책 확인 필요'};
  const row=(id,name,path,included)=>({rowId:id,included,menuPath:path,functionName:name,menuDescription:name+'하는 기능',rowType:'FUNCTION',location:'CONTENT',placementStatus:included?'WEB_ANCHORED':'UNASSIGNED',verificationStatus:'DOCUMENT_ONLY',evidence:[],matchedMenuKeys:[],reviewNotes:[]});
  const job={jobId:'menu',productName:'제품',status:'APPROVED',revision:1,generationMode:'EXTERNAL',stale:false,warnings:[],sources:[],authentication,webStructure:{nodes:[{path:['작업'],kind:'MENU'}]},output:{rows:[row('read','작업 조회',['작업'],true),row('login','로그인',[],false)]}};
  await page.route('**/api/**',route=>{
   const req=route.request(),path=new URL(req.url()).pathname;
   if(path.endsWith('/functional-menu'))return route.fulfill({json:{job,history:[{jobId:'menu',productName:'제품',status:'APPROVED'}],crawls:[]}});
   if(path.endsWith('/menu-test-cases')) {
    if(req.method()==='POST')generated=req.postDataJSON();
    return route.fulfill({json:{set:generated?{setId:'tc',menuJobId:'menu',stale:false,output:{productName:'제품',featureCount:2,caseCount:111,templateVersion:'menu-tc-v3',testCases:[],summary:{},source:{authentication:{...authentication,loginUrl:generated.loginUrl}}}}:null,history:[]}});
   }
   throw Error('Unexpected '+path);
  });
  await page.goto('http://127.0.0.1:4174/login-route-qa.html');
  await page.getByLabel('2행 웹 메뉴 배치').selectOption(JSON.stringify(['로그인 화면']));
  assert(await page.getByRole('checkbox',{name:'2행 로그인 포함'}).isChecked());
  const tc=page.getByRole('region',{name:'메뉴트리 기반 TC 생성'});
  await tc.getByLabel('로그인 화면 주소').selectOption('https://example.com/');
  assert((await tc.innerText()).includes('예상 111개 TC'));
  await tc.getByRole('button',{name:'TC 초안 생성',exact:true}).click();
  await tc.getByText('111개 생성 · 시험 미실행').waitFor();
  assert.equal(generated.loginUrl,'https://example.com/');assert.deepEqual(Object.keys(generated.featureTypes),['read']);
  assert.deepEqual(errors,[]);console.log('PASS: login route outside sidebar, automatic 27-case count, base URL selection and generation payload');
 }finally{await browser.close();await fs.unlink(html);}
})().catch(e=>{console.error(e);process.exitCode=1});
