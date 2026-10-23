"""Opt-in real 8B/browser/Jev fault-injection suite. Disposable site, no product mutations."""
import asyncio
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
from test_execution import Execute, execute_feature, judge_with_jev


class Site(BaseHTTPRequestHandler):
    def log_message(self,*_):pass
    def do_GET(self):
        mode=parse_qs(urlsplit(self.path).query).get('mode',['good'])[0]
        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.end_headers()
        if self.path.startswith('/reports'):
            body='REPORT_READY' if mode=='good' else 'REPORT_BROKEN'
            html=f'<nav>Reports REPORT_READY</nav><main><h1>Reports</h1><p>{body}</p></main>'
        else:
            result='조회 완료' if mode=='good' else '조회 실패'
            html=f'''<main><h1>버튼 시험</h1><button onclick="document.querySelector('output').textContent='{result}'">조회</button><output>조회 전</output></main>'''
        self.wfile.write(('<!doctype html><html lang="ko"><meta charset="utf-8">'+html+'</html>').encode())


async def main():
    server=ThreadingHTTPServer(('0.0.0.0',0),Site);threading.Thread(target=server.serve_forever,daemon=True).start()
    output=Path(os.getenv('EXECUTION_VALIDATION_OUTPUT','/generation-cache/execution-v3-validation'));output.mkdir(parents=True,exist_ok=True)
    results=[]
    try:
        for kind,mode,expected in [('view','good','P'),('view','broken','F'),('click','good','P'),('click','broken','F')]:
            started=time.monotonic();base=f'http://ai-service:{server.server_port}'
            tc={'id':f'{kind}-{mode}','tcId':f'V3-{kind}-{mode}','ruleId':'FEATURE-01' if kind=='view' else 'FEATURE-02',
                'scopeDescription':'보고서를 조회하는 기능' if kind=='view' else '조회 버튼을 조작하는 기능',
                'scenario':'보고서 목록을 조회한다.' if kind=='view' else '조회 버튼을 클릭한다.',
                'inputs':'','expectedResult':'REPORT_READY' if kind=='view' else '조회 완료'}
            request=Execute(runId='v3-isolated',attempt='isolated',targetUrl=f'{base}/{"reports" if kind=="view" else "button"}?mode={mode}',featureId=tc['id'],featurePath=['제품','Reports' if kind=='view' else '버튼 시험'],cases=[tc],allowMutation=False)
            answers={}
            def judge(*args):
                value=judge_with_jev(*args);answers.update(value);return value
            result=await execute_feature(request,judge=judge,check_active=False)
            item=result['items'][0]
            record={'fixture':tc['id'],'expected':expected,'actual':item.get('result'),'judgmentStatus':item.get('judgmentStatus'),'executionStatus':item.get('executionStatus'),'reason':item.get('reason'),
                    'elapsedSeconds':round(time.monotonic()-started,2),'judgedBy':item.get('judgedBy','JEV'),'model':os.getenv('EXECUTION_QWEN_MODEL','qwen3:8b'),'qwenCalls':sum(p.get('planner')=='QWEN' for p in result['plans']),
                    'actions':[r for e in result['evidence'] if e.get('kind')=='BROWSER_ACTIONS' for r in e.get('records',[])],
                    'conditions':item.get('conditionAssessments'),'judgeAnswers':answers,'plans':result['plans']}
            results.append(record);(output/f'{tc["id"]}.json').write_text(json.dumps({**record,'evidence':result['evidence']},ensure_ascii=False,indent=2))
            print(json.dumps({k:v for k,v in record.items() if k not in ('actions','plans','judgeAnswers','conditions')},ensure_ascii=False),flush=True)
        report={'scope':'disposable synthetic site; real browser and Jev; interactive cases use real Qwen 8B','passed':all(r['actual']==r['expected'] for r in results),
                'failedProductNeverPassed':all(r['actual']!='P' for r in results if r['expected']=='F'),'results':results}
        (output/'summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        if not report['passed']:raise SystemExit(1)
    finally:server.shutdown()


if __name__=='__main__':asyncio.run(main())
