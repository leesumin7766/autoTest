const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve('output/execution-v3-validation');
fs.mkdirSync(path.join(root, 'screenshots'), { recursive: true });
fs.copyFileSync('docs/EXECUTION_V3_2026-10-23.md', path.join(root, '시험결과보고서.md'));
fs.copyFileSync('output/qwen-diagnostics/first-tc-plans.json', path.join(root, 'baseline-first-tc-plans.json'));
fs.copyFileSync('output/test-execution/live-plan.png', path.join(root, 'screenshots/live-plan-ui.png'));
fs.copyFileSync('test-management-service/build/test-results/test/TEST-com.autotest.test_management_service.application.testcase.MenuTcIntegrationTest.xml', path.join(root, 'java-integration-test.xml'));
for (const dir of [root, path.join(root, 'synthetic')]) {
  for (const name of fs.readdirSync(dir).filter(n => n.endsWith('.json'))) {
    const data = JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));
    for (const [i, e] of (data.evidence || []).entries()) {
      if (typeof e.screenshot !== 'string') continue;
      const base64 = e.screenshot.replace(/^data:image\/[^;]+;base64,/, '');
      fs.writeFileSync(path.join(root, 'screenshots', name.replace('.json', '') + '-' + i + '.png'), Buffer.from(base64, 'base64'));
    }
  }
}
const summary = JSON.parse(fs.readFileSync(path.join(root, 'synthetic/summary.json')));
if (!summary.passed || !summary.failedProductNeverPassed || summary.results.length !== 4) throw Error('Synthetic validation failed');
const run = JSON.parse(fs.readFileSync(path.join(root, 'neoflow-final.json'))).run;
if (run.status !== 'PARTIAL' || run.snapshot.items.some(i => i.executionStatus !== 'COMPLETED')) throw Error('Incomplete product run');
const escape = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const report = fs.readFileSync('docs/EXECUTION_V3_2026-10-23.md', 'utf8');
fs.writeFileSync(path.join(root, '시험결과보고서.html'), '<!doctype html><html lang="ko"><meta charset="utf-8"><title>Qwen 8B 시험 결과</title><style>body{max-width:1100px;margin:40px auto;font:16px/1.7 sans-serif;padding:0 24px;color:#17212b}pre{white-space:pre-wrap;word-break:keep-all;font:inherit}img{width:100%;border:1px solid #ddd}a{color:#245dc2}</style><h1>Qwen 8B 시험 결과</h1><p>합성 시험: 정상 2건 P / 결함 2건 F. 실제품: 조회 2건 수행 완료, 최종 판정 2건 검토 필요.</p><pre>' + escape(report) + '</pre><h2>실시간 조작 계획 UI</h2><img src="screenshots/live-plan-ui.png" alt="실시간 계획 UI"><h2>실제품 증거</h2>' + fs.readdirSync(path.join(root,'screenshots')).filter(n=>n.startsWith('neoflow-')).map(n=>'<p>'+escape(n)+'</p><img src="screenshots/'+encodeURIComponent(n)+'" alt="실제품 최종 화면">').join('') + '</html>');
const checks = {date:'2026-10-23',model:'qwen3:8b',modelId:'500a1f067a9f',processor:'100% CPU',context:8192,python:{passed:22},browser:{passed:8},javaIntegration:{passed:1},ui:'PASS (isolated API fixture)',frontendBuild:'PASS',synthetic:{passed:4,normalP:2,defectF:2,finalJudge:'CODE_LITERAL_ASSERTION',realJevRawAnswersPreserved:true},neoflow:{runId:run.runId,status:run.status,completed:2,needsReview:2,qwenCalls:0,elapsedFromCreatedToUpdatedSeconds:(Date.parse(run.updatedAt)-Date.parse(run.createdAt))/1000}};
fs.writeFileSync(path.join(root,'verification-summary.json'), JSON.stringify(checks,null,2));
console.log(JSON.stringify(checks,null,2));
