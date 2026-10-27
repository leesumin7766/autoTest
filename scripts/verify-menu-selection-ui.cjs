// Isolated component fixture. Does not access or mutate user submissions.
const { chromium } = require('playwright')
const fs = require('node:fs/promises')
const assert = require('node:assert/strict')
;(async()=>{
 const html='frontend/menu-selection-qa.html'
 await fs.writeFile(html,`<html lang="ko"><head><meta charset="UTF-8"></head><body><div id="root"></div><script type="module">import React from 'react';import{createRoot}from'react-dom/client';import{FunctionalMenuPanel}from'/src/FunctionalMenuPanel.tsx';import'/src/App.css';import'/src/workspace.css';createRoot(document.getElementById('root')).render(React.createElement('main',{className:'workspace'},React.createElement(FunctionalMenuPanel,{submissionId:'selection-qa',memberId:'1',ready:true})))</script></body></html>`)
 const browser=await chromium.launch({headless:true})
 try {
  const page=await browser.newPage({viewport:{width:1600,height:1050}});page.setDefaultTimeout(10000)
  const errors=[];page.on('pageerror',e=>errors.push(e.message))
  const row=(id,name,path,included)=>({rowId:id,included,menuPath:path,functionName:name,menuDescription:name+'하는 기능',location:'SIDEBAR',verificationStatus:'DOCUMENT_ONLY',evidenceStatus:id==='b'?'UNVERIFIED':'VALID',connectionStatus:'NOT_LINKED',placementStatus:path.length?'WEB_ANCHORED':'UNASSIGNED',rowType:'FUNCTION',evidence:[{sourceId:'doc',quote:'기능 근거 원문'}],matchedMenuKeys:[],reviewNotes:['원문 확인 필요']})
  const initial=[row('a','사용자 조회',['관리'],true),row('b','사용자 등록',['관리'],false),row('c','위치 미확인',[],false)]
  initial[0].placementReview={method:'EXACT_PATH_DOCUMENT_REFERENCE',proposedMenuPath:['관리'],reason:'경로와 문서 근거를 대조한 자동 배치 제안입니다.'}
  let job={jobId:'job',crawlJobId:'crawl',productName:'검증 제품',status:'DRAFT',revision:1,stale:false,generationMode:'EXTERNAL',warnings:[],sources:[{id:'doc',kind:'DOCUMENT'}],webStructure:{version:'test',nodes:[{path:['관리'],kind:'MENU'}]},output:{rows:structuredClone(initial)}}
  const writes=[]
  await page.route('**/api/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname
   if(request.method()==='PUT') {
    assert(path.endsWith('/draft')||path.endsWith('/approval'));const payload=request.postDataJSON();writes.push({path,payload})
    job={...job,revision:job.revision+1,status:path.endsWith('/approval')?'APPROVED':'DRAFT',output:{rows:payload.rows}};
   } else if(path.endsWith('/evidence'))return route.fulfill({json:{id:'doc',kind:'DOCUMENT',text:'기능 근거 원문'}})
   else assert.equal(request.method(),'GET')
   return route.fulfill({json:{job,history:[{jobId:'job',productName:'검증 제품',status:job.status}],crawls:[]}})
  })
  await page.goto('http://127.0.0.1:4174/menu-selection-qa.html')
  const table=page.locator('.functional-menu-table'),toolbar=page.getByRole('group',{name:'메뉴트리 포함 선택'})
  await table.locator('tr.is-included').first().waitFor();assert.equal(await table.locator('tr.is-included').count(),1)
  assert.equal(await page.getByText('자동 배치 · 검토 필요',{exact:true}).count(),1)
  assert((await toolbar.innerText()).includes('포함 기능 1개'))
  assert(await page.getByRole('checkbox',{name:'3행 위치 미확인 포함'}).isDisabled())
  await toolbar.getByRole('button',{name:'전체 포함',exact:true}).click()
  assert.equal(await table.locator('tr.is-included').count(),2);assert.equal(writes.length,0)
  assert(await toolbar.getByRole('button',{name:'전체 포함',exact:true}).isDisabled())
  const tint=await table.locator('tr.is-included td').first().evaluate(el=>getComputedStyle(el).backgroundColor)
  assert.equal(tint,'rgb(240, 253, 250)')
  assert.equal(await page.getByText('⚠ 근거 미확인 · 사용자 검토 필요 · ',{exact:false}).count(),1)
  await page.getByRole('button',{name:'편집 취소',exact:true}).click();assert.equal(await table.locator('tr.is-included').count(),1)
  await page.getByRole('checkbox',{name:'2행 사용자 등록 포함'}).check();assert.equal(await table.locator('tr.is-included').count(),2)
  await page.getByRole('button',{name:'수정 초안 저장',exact:true}).click()
  await page.getByRole('button',{name:'수정 초안 저장',exact:true}).waitFor();assert.equal(writes.length,1)
  for(let i=0;i<3;i++)assert.deepEqual({...writes[0].payload.rows[i],included:initial[i].included},initial[i])
  assert.equal(writes[0].payload.rows[2].included,false)
  await page.getByRole('checkbox',{name:'2행 사용자 등록 포함'}).uncheck();assert.equal(await table.locator('tr.is-excluded').count(),2)
  await page.getByRole('button',{name:'편집 취소',exact:true}).click()
  await page.getByLabel('3행 웹 메뉴 배치').selectOption(JSON.stringify(['관리']))
  assert(await page.getByRole('checkbox',{name:'3행 위치 미확인 포함'}).isChecked())
  await page.getByRole('button',{name:'포함한 기능을 검토 완료·승인',exact:true}).click()
  await page.waitForFunction(()=>!document.body.innerText.includes('저장하지 않은 변경'))
  assert.equal(writes.length,2);assert(writes[1].path.endsWith('/approval'));assert.equal(writes[1].payload.rows.length,3)
  assert.equal(await table.locator('tr.is-included').count(),3)
  await fs.mkdir('output/menu-selection-ui',{recursive:true});await page.screenshot({path:'output/menu-selection-ui/desktop.png',fullPage:true})
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:'output/menu-selection-ui/mobile.png',fullPage:true})
  assert.deepEqual(errors,[])
  console.log('PASS: bulk inclusion, row colors, individual toggles, placement protection, cancel, exact save payload, warnings, approval and responsive rendering')
 } finally {await browser.close();await fs.unlink(html)}
})().catch(e=>{console.error(e);process.exitCode=1})
