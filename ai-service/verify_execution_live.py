"""Run real local Qwen + Jev against the synthetic Playwright fixture, not a user product."""
import asyncio
import json
import os
import time
import sys
from pathlib import Path
from test_execution import Execute, execute_feature

async def main():
    os.environ['CRAWLER_SERVICE_URL']='http://crawler-service:8017'
    report=[]
    extended='--extended' in sys.argv
    only_missing='--only-missing' in sys.argv
    for mode,expected in ([('missing',None)] if only_missing else [('goodread','P'),('badread','F'),('missing',None)] if extended else [('good','P'),('broken','F')]):
        started=time.monotonic()
        request=Execute(runId='synthetic',attempt='isolated',targetUrl=f'http://crawler-service:8017/fixture/{mode}',featureId='save',featurePath=['저장 기능 시험','저장'],allowMutation=True,
            cases=[{'id':'save-case','tcId':'TC-SMOKE-001','scenario':'저장 버튼을 클릭한 후 저장 완료 문구가 표시되는지 확인','inputs':'저장 버튼 클릭. 추가 데이터 입력 없음.','expectedResult':'저장 버튼 클릭 후 화면에 저장 완료 문구가 표시된다.'}])
        if mode.endswith('read'):
            request.featurePath=['신청 상세 조회'];request.allowMutation=False
            request.cases=[{'id':'detail-case','tcId':'TC-SMOKE-002','scenario':'상세 보기 버튼을 눌러 신청 상태를 확인한다.','inputs':'추가 입력 없음. 신청 상태의 정답은 승인이다.','expectedResult':'상세 보기 클릭 후 상태: 승인이 표시된다.'}]
        if mode=='missing':
            request.featurePath=['파일 전송'];request.allowMutation=True
            request.cases=[{'id':'file-case','tcId':'TC-SMOKE-003','scenario':'제공된 시험 파일을 전송한다.','inputs':'시험 파일이 필요하지만 현재 파일을 제공하지 않았다.','expectedResult':'선택한 시험 파일이 전송된다.'}]
        result=await execute_feature(request,check_active=False)
        item=result['items'][0]
        entry={'fixture':mode,'expected':expected,'actual':item.get('result'),'executionStatus':item.get('executionStatus'),'reason':item.get('reason'),'confidence':item.get('confidence'),
            'elapsedSeconds':round(time.monotonic()-started,2),'plans':result['plans'],'observedTexts':[e.get('text') for e in result['evidence'] if e.get('text')]}
        report.append(entry)
        print(json.dumps(entry,ensure_ascii=False),flush=True)
    summary={'passed':all(r['actual']==r['expected'] and (r['expected'] is not None or r['executionStatus']=='NEEDS_CONTEXT') for r in report),'planner':'qwen3:4b (local)','judge':'Jev (real API)','results':report}
    filename='live-missing.json' if only_missing else 'live-extended.json' if extended else 'live-basic.json'
    Path('/tmp/execution-dev5/'+filename).write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False),flush=True)

if __name__=='__main__':asyncio.run(main())
