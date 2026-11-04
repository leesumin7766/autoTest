// Run with Node 24; uses the crawler workspace's installed Playwright.
import { createRequire } from 'node:module'
import { writeFile, unlink } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
const root = fileURLToPath(new URL('../', import.meta.url))
const requireCrawler = createRequire(new URL('../../crawler-service/package.json', import.meta.url))
const { chromium } = requireCrawler('playwright')
const html = `${root}.self-llm-check.html`, entry = `${root}.self-llm-check.tsx`
let server, browser
try {
  await writeFile(html, '<div id="root"></div><script type="module" src="/.self-llm-check.tsx"></script>')
  await writeFile(entry, `import React from 'react';import{createRoot}from'react-dom/client';import{FunctionalMenuPanel}from'./src/FunctionalMenuPanel';createRoot(document.getElementById('root')!).render(<FunctionalMenuPanel submissionId="fixture" memberId="1" ready={true}/>);`)
  server = await createServer({root, configFile:`${root}vite.config.ts`,server:{port:5187,strictPort:true}})
  await server.listen()
  browser = await chromium.launch({headless:true})
  const context = await browser.newContext({permissions:['clipboard-read','clipboard-write']})
  const page = await context.newPage()
  const errors=[];page.on('pageerror', e=>errors.push(e.message))
  const job={jobId:'job-fixture',crawlJobId:'crawl-fixture',productName:'Fixture',status:'DRAFT',revision:0,stale:false,generationMode:'EXTERNAL',warnings:[],sources:[],output:{rows:[]},webStructure:{version:'web-first-v1',nodes:[{path:['운영','로그'],kind:'MENU'}]}}
  const view={job:null,history:[],crawls:[{jobId:'crawl-fixture',pageCount:3,createdAt:new Date().toISOString()}]}
  const prompt='규칙 버전: self-llm-v2\n전체 시험 자료\n# 전체 분석 자료 끝'
  let imports=0,creates=0
  await page.route('**/api/**',async route=>{
    const request=route.request(),url=new URL(request.url())
    let result=view
    if(url.pathname.endsWith('/prompt')) result={instructionVersion:'self-llm-v2',prompt}
    else if(url.pathname.endsWith('/import')) {assert.equal(request.postDataJSON().rows[0].functionName,'로그 조회');assert.equal(request.postDataJSON().requestId,'job-fixture');assert.deepEqual(request.postDataJSON().rows[0].evidenceRefIds,['E0001']);imports++;result={...view,job:{...job,output:{rows:[{rowId:'warning-row',included:true,menuPath:['운영','로그'],functionName:'로그 조회',menuDescription:'로그를 조회하는 기능',location:'CONTENT',verificationStatus:'UNVERIFIED',evidenceStatus:'UNVERIFIED',matchedMenuKeys:[],evidence:[{sourceId:'missing',quote:'일치하지 않는 인용'}],reviewNotes:[],validationIssues:[{code:'MENU_SOURCE_NOT_FOUND',sourceId:'missing',quote:'일치하지 않는 인용',message:'고정 입력에 없는 근거 ID입니다.'}]}]}}}}
    else if(request.method()==='POST'){assert.equal(request.postDataJSON().mode,'EXTERNAL');creates++;result={...view,job}}
    else if(url.searchParams.has('job'))result={...view,job}
    await route.fulfill({json:result})
  })
  await page.goto('http://localhost:5187/.self-llm-check.html')
  const md=page.waitForEvent('download');await page.getByText('공통 작성 규칙.md 다운로드',{exact:true}).click();assert.equal((await md).suggestedFilename(),'menu-tree-common-rules.md')
  await page.getByLabel('Excel 1depth 제품명').fill('Fixture')
  await page.getByRole('button',{name:'내 LLM용 제품 프롬프트 만들기',exact:true}).click()
  await page.getByLabel('제품별 메뉴트리 프롬프트').waitFor()
  assert.equal(await page.getByLabel('제품별 메뉴트리 프롬프트').inputValue(),prompt)
  await page.getByRole('button',{name:'제품 프롬프트 전체 복사',exact:true}).click()
  assert.equal((await page.evaluate(()=>navigator.clipboard.readText())).replace(/\r\n/g,'\n'),prompt)
  await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{value:{writeText:async()=>{throw new Error('denied')}}}))
  await page.getByRole('button',{name:'제품 프롬프트 전체 복사',exact:true}).click()
  await page.getByText('자동 복사를 사용할 수 없어 전체 내용을 선택했습니다. Ctrl+C로 복사하세요.',{exact:true}).waitFor()
  assert.equal(await page.getByLabel('제품별 메뉴트리 프롬프트').evaluate(el=>el.selectionEnd-el.selectionStart),prompt.length)
  const txt=page.waitForEvent('download');await page.getByRole('button',{name:'동일 내용 TXT 다운로드',exact:true}).click();assert.equal((await txt).suggestedFilename(),'menu-tree-job-fixture.txt')
  await page.getByLabel('LLM JSON 응답').fill('```json\n{"requestId":"job-fixture","rows":[{"functionName":"로그 조회","evidenceRefIds":["E0001"]}]}\n```')
  await page.getByRole('button',{name:'근거 검증 후 가져오기',exact:true}).click()
  await page.getByText('초안으로 가져왔습니다. 근거 미확인 1개 기능의 경고와 의미적 연결·누락 여부를 검토하세요.',{exact:true}).waitFor()
  await page.getByText('근거 검증 경고 · 1개 기능',{exact:true}).waitFor()
  assert.equal(imports,1);assert.equal(creates,1);assert.deepEqual(errors,[])
  console.log('PASS: MD download, external-only job, full prompt, clipboard and fallback, TXT download, fenced JSON import')
} finally {
  await browser?.close();await server?.close()
  await Promise.all([unlink(html).catch(()=>{}),unlink(entry).catch(()=>{})])
}
