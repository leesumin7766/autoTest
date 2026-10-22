import json
import unittest
from unittest.mock import patch
import httpx
from test_execution import Execute, Plan, execute_feature, judgments

class ExecutionTest(unittest.IsolatedAsyncioTestCase):
    def request(self):
        return Execute(runId='run', attempt='attempt', targetUrl='http://fixture.local', featureId='feature', featurePath=['기능'], cases=[{'id': 'tc', 'scenario': '저장', 'expectedResult': '저장 완료 표시'}])

    async def test_real_observations_only_and_close(self):
        calls=[]
        def transport(req):
            calls.append(req.url.path)
            if req.url.path.endswith('/open'): return httpx.Response(200,json={'sessionId':'session','observation':{'text':'저장 완료','elements':[],'screenshot':'image'}})
            return httpx.Response(200,json={})
        async def planner(payload):
            self.assertNotIn('password', payload)
            return Plan(actions=[],decision='FINISH',missingInputs=[],explanation='완료 상태 확인')
        def judge(cases,evidence,path):
            self.assertNotIn('screenshot',evidence[0]);self.assertNotIn('plans',evidence[0])
            return {'case_0':{'choice':'P','confidence':.99}}
        result=await execute_feature(self.request(),planner,judge,httpx.MockTransport(transport),False)
        self.assertIsNone(result['items'][0]['result']);self.assertTrue(calls[-1].endswith('/close'))

    async def test_action_error_never_becomes_product_failure(self):
        def transport(req):
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'text':'화면','elements':[]}})
            if req.url.path.endswith('/step'):return httpx.Response(200,json={'records':[{'status':'ERROR'}],'observation':{'text':'동일 화면'},'blockedRequests':0})
            return httpx.Response(200,json={})
        async def planner(_):return Plan(actions=[{'action':'click','elementId':'1'}],decision='FINISH',missingInputs=[],explanation='클릭')
        def judge(*_):self.fail('A browser error must not be judged F')
        result=await execute_feature(self.request(),planner,judge,httpx.MockTransport(transport),False)
        self.assertIsNone(result['items'][0]['result']);self.assertEqual('ERROR',result['items'][0]['executionStatus'])

    async def test_cancellation_after_inference_prevents_action(self):
        active_calls=0;paths=[]
        def transport(req):
            nonlocal active_calls
            paths.append(req.url.path)
            if req.url.path.endswith('/active'):
                active_calls+=1;return httpx.Response(200,json={'active':active_calls==1})
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'text':'화면','elements':[]}})
            return httpx.Response(200,json={})
        async def planner(_):return Plan(actions=[{'action':'click','elementId':'1'}],decision='FINISH',missingInputs=[],explanation='클릭')
        result=await execute_feature(self.request(),planner,transport=httpx.MockTransport(transport))
        self.assertIsNone(result['items'][0]['result']);self.assertFalse(any(p.endswith('/step') for p in paths));self.assertTrue(paths[-1].endswith('/close'))

    def test_confidence_and_insufficient_evidence(self):
        for answer in ({'choice':'F','confidence':.4},{'choice':'INSUFFICIENT','confidence':1}):
            self.assertIsNone(judgments(self.request().cases,{'case_0':answer},['observed'])[0]['result'])
        self.assertEqual('N/A',judgments(self.request().cases,{'case_0':{'choice':'N_A','confidence':.99}},['observed'])[0]['result'])

    async def test_one_success_cannot_pass_another_tc(self):
        opens=0
        def transport(req):
            nonlocal opens
            if req.url.path.endswith('/open'):
                opens+=1;return httpx.Response(200,json={'sessionId':str(opens),'observation':{'text':'시험 화면','elements':[{'id':'0','name':'버튼'}]}})
            if req.url.path.endswith('/step'):return httpx.Response(200,json={'records':[{'status':'COMPLETED','action':'click'}],'observation':{'text':'성공','elements':[]}})
            return httpx.Response(200,json={})
        async def planner(payload):
            self.assertEqual(1,len(payload['cases']));expected=payload['cases'][0]['expectedResult']
            return Plan(actions=[{'action':'click','elementId':'0'}],decision='EXECUTE',phase='TEST',missingInputs=[],explanation='한 TC만 수행',conditions=[{'requirementQuote':expected,'method':'TEXT_PRESENT','expected':expected}])
        def judge(*_):return {'case_0':{'choice':'P','confidence':.99},'scope_0':{'choice':'YES','confidence':.99},'coverage_0':{'choice':'COMPLETE','confidence':.99}}
        request=self.request().model_copy(update={'cases':[{'id':'success','expectedResult':'성공'},{'id':'cancel','expectedResult':'취소'}]})
        result=await execute_feature(request,planner,judge,httpx.MockTransport(transport),False)
        self.assertEqual(2,opens);self.assertEqual('P',result['items'][0]['result']);self.assertIsNone(result['items'][1]['result'])
        first=set(result['items'][0]['evidenceIds']);second=set(result['items'][1]['evidenceIds']);self.assertFalse(first&second)

if __name__=='__main__':unittest.main()
