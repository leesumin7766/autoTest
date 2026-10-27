const {test}=require('node:test');const assert=require('node:assert/strict');const http=require('node:http');
const {retryPlan}=require('./retry');const {crawl}=require('./walker');
test('retry plan separates transit, self, descendants and unrelated paths',()=>{
 const target={menuKey:'parent',path:['메뉴','관리']};
 const self=retryPlan({scope:'SELF',targets:[target]}),tree=retryPlan({scope:'SUBTREE',targets:[target]});
 const child={menuKey:'child',path:['메뉴','관리','목록']};
 assert.equal(self.visits({path:['메뉴']}),true);assert.equal(self.captures({menuKey:'root',path:['메뉴']}),false);
 assert.equal(self.captures(target),true);assert.equal(self.visits(child),false);assert.equal(tree.captures(child),true);
 assert.equal(tree.visits({menuKey:'other',path:['메뉴','기타']}),false);
 assert.throws(()=>retryPlan({scope:'SELF',targets:[{menuKey:'bad',path:[]}]}));
});
test('targeted retry captures only selected scope and preserves unrelated failure', {timeout:120000},async()=>{
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(`<title>${req.url}</title><h1>화면 ${req.url}</h1><nav aria-label="메뉴"><ul><li><a href="/a">관리</a><ul><li><a href="/child">하위</a></li></ul></li><li><a href="/other">기타</a></li></ul></nav><p>화면별 결과 ${req.url}</p>`)});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const job={targetUrl:`http://127.0.0.1:${server.address().port}/`,loginMethod:'NONE',automation:{allowedOrigins:[],includePaths:[],excludePaths:[],maxPages:15,maxDepth:4,maxDurationSeconds:50,pageLoadTimeoutSeconds:5,viewportWidth:1000,viewportHeight:800}};
 try{
  const initial=[];await crawl(job,{onPage:async p=>initial.push(p)});
  const parent=initial.find(p=>p.url.endsWith('/a')),child=initial.find(p=>p.url.endsWith('/child')),other=initial.find(p=>p.url.endsWith('/other'));assert.ok(parent&&child&&other);
  const failures=[parent,other].map(p=>({menuKey:p.transition.menuKey,path:p.transition.menuPath,stage:'CAPTURING',reason:'CAPTURE_TIMEOUT'}));
  for(const scope of ['SELF','SUBTREE']){
   const added=[];const result=await crawl({...job,retrySelection:{scope,targets:[{menuKey:parent.transition.menuKey,path:parent.transition.menuPath}]},resume:{pageCount:1,bytes:0,pages:[initial[0]],skipped:failures}},{onPage:async p=>added.push(p)});
   assert.ok(added.some(p=>p.url.endsWith('/a')));assert.equal(added.some(p=>p.url.endsWith('/child')),scope==='SUBTREE');
   assert.equal(added.some(p=>p.url.endsWith('/other')),false);assert.equal(added.some(p=>p.transition.menuKey===''),false);
   assert.equal(result.skipped.length,1);assert.equal(result.skipped[0].menuKey,other.transition.menuKey);assert.equal(result.coverageStatus,'INCOMPLETE');
  }
 }finally{await new Promise(r=>server.close(r))}
});
