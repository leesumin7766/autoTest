import json
import unittest
from unittest.mock import patch
import httpx
from test_execution import Execute, Plan, execute_feature, judgments

class ExecutionTest(unittest.IsolatedAsyncioTestCase):
    async def test_blocked_request_preserves_the_failure_screen(self):
        def transport(req):
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'text':'Dashboard','elements':[],'screenshot':'before'}})
            if req.url.path.endswith('/step'):return httpx.Response(200,json={'records':[{'action':'click','status':'COMPLETED','isNavigation':True}],'blockedRequests':1,'observation':{'text':'Logs: 요청 차단','elements':[],'screenshot':'failure'}})
            return httpx.Response(200,json={})
        async def planner(_):return Plan(actions=[{'action':'click','elementId':'0'}],decision='EXECUTE',phase='NAVIGATE',missingInputs=[],explanation='Logs 이동')
        result=await execute_feature(self.request(),planner,transport=httpx.MockTransport(transport),check_active=False)
        self.assertEqual('ERROR',result['items'][0]['executionStatus'])
        self.assertEqual('Logs: 요청 차단',result['evidence'][-1]['text'])
        self.assertEqual('failure',result['evidence'][-1]['screenshot'])
        self.assertNotIn('screenshot',result['evidence'][0])
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
            done=bool(payload['previousActions'])
            return Plan(actions=[] if done else [{'action':'click','elementId':'0'}],decision='FINISH' if done else 'EXECUTE',phase='TEST',missingInputs=[],explanation='한 TC만 수행',conditions=[{'requirementQuote':expected,'method':'TEXT_PRESENT','expected':expected}])
        def judge(*_):return {'case_0':{'choice':'P','confidence':.99},'scope_0':{'choice':'YES','confidence':.99},'coverage_0':{'choice':'COMPLETE','confidence':.99}}
        request=self.request().model_copy(update={'cases':[{'id':'success','expectedResult':'성공'},{'id':'cancel','expectedResult':'취소'}]})
        result=await execute_feature(request,planner,judge,httpx.MockTransport(transport),False)
        self.assertEqual(2,opens);self.assertEqual('P',result['items'][0]['result']);self.assertIsNone(result['items'][1]['result'])
        first=set(result['items'][0]['evidenceIds']);second=set(result['items'][1]['evidenceIds']);self.assertFalse(first&second)

    async def test_mislabeled_menu_click_cannot_finish_a_test(self):
        rounds=0; judged=0
        def transport(req):
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'text':'Dashboard','elements':[]}})
            if req.url.path.endswith('/step'):
                return httpx.Response(200,json={'records':[{'action':'click','status':'COMPLETED','isNavigation':rounds==1}], 'observation':{'text':'Jobs' if rounds==1 else '업로드할 JSON 파일을 선택하세요.','elements':[]}})
            return httpx.Response(200,json={})
        async def planner(payload):
            nonlocal rounds
            rounds+=1
            return Plan(actions=[{'action':'click','elementId':'0'}] if rounds<3 else [],decision='EXECUTE' if rounds<3 else 'FINISH',phase='TEST',missingInputs=[],explanation='단계',conditions=[{'requirementQuote':'안내메시지를 명확하게 제공함','method':'TEXT_PRESENT','expected':'명확하게 제공함'}])
        def judge(cases,evidence,path):
            nonlocal judged
            judged+=1;self.assertEqual(3,rounds)
            self.assertEqual('SEMANTIC',cases[0]['conditionAssessments'][0]['method'])
            return {'case_0':{'choice':'P','confidence':.99},'scope_0':{'choice':'YES','confidence':.99},'coverage_0':{'choice':'COMPLETE','confidence':.99},'condition_0_0':{'choice':'PASS','confidence':.99}}
        request=self.request().model_copy(update={'cases':[{'id':'message','expectedResult':'안내메시지를 명확하게 제공함'}]})
        result=await execute_feature(request,planner,judge,httpx.MockTransport(transport),False)
        self.assertEqual(1,judged);self.assertEqual('P',result['items'][0]['result'])

    async def test_navigation_only_never_calls_judge(self):
        def transport(req):
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'text':'Jobs','elements':[]}})
            if req.url.path.endswith('/step'):return httpx.Response(200,json={'records':[{'action':'click','status':'COMPLETED','isNavigation':True}],'observation':{'text':'Jobs','elements':[]}})
            return httpx.Response(200,json={})
        async def planner(_):return Plan(actions=[{'action':'click','elementId':'0'}],decision='EXECUTE',phase='TEST',missingInputs=[],explanation='이동')
        def judge(*_):self.fail('Navigation is not test execution')
        result=await execute_feature(self.request(),planner,judge,httpx.MockTransport(transport),False)
        self.assertEqual('NEEDS_CONTEXT',result['items'][0]['executionStatus']);self.assertIsNone(result['items'][0]['result'])

    async def test_file_prerequisite_skips_browser_and_model(self):
        async def planner(_):self.fail('Missing file must be detected before inference')
        request=self.request().model_copy(update={'cases':[{'id':'upload','ruleId':'FEATURE-01','scopeDescription':'작업 파일을 업로드하는 기능','expectedResult':'정상 동작함'}]})
        result=await execute_feature(request,planner,check_active=False)
        self.assertEqual('NOT_EXECUTED',result['items'][0]['judgmentStatus']);self.assertEqual([],result['evidence'])

    async def test_initial_entry_redirect_is_recovered_before_any_test_plan(self):
        calls=[];payloads=[]
        def observation(matched):return {'text':'로그인' if matched else 'Dashboard','elements':[], 'entryState':{'atInitialUrl':matched,'initialEntryRequired':True,'requestedUrl':'http://fixture.local','actualUrl':'http://fixture.local' if matched else 'http://fixture.local/dashboard'}}
        def transport(req):
            calls.append(req.url.path)
            if req.url.path.endswith('/open'):
                self.assertTrue(json.loads(req.content)['initialEntry']);return httpx.Response(200,json={'sessionId':'s','observation':observation(False)})
            if req.url.path.endswith('/step'):
                self.assertEqual([{'action':'reset_session'}],json.loads(req.content)['actions'])
                return httpx.Response(200,json={'records':[{'action':'reset_session','status':'COMPLETED','isNavigation':True,'initializationOnly':True}],'observation':observation(True)})
            return httpx.Response(200,json={})
        async def planner(payload):
            payloads.append(payload);self.assertTrue(payload['observation']['entryState']['atInitialUrl'])
            return Plan(actions=[],decision='NEEDS_INPUT',missingInputs=['시험값'],explanation='초기 화면 확인')
        with patch('test_execution.plan_initial_entry',return_value='RESET_SESSION'):
            result=await execute_feature(self.request().model_copy(update={'initialEntry':True}),planner,transport=httpx.MockTransport(transport),check_active=False)
        self.assertTrue(result['initialEntryVerified']);self.assertEqual(1,len(payloads));self.assertIsNone(result['items'][0]['result'])
        self.assertEqual('QWEN_INITIAL_ENTRY',result['plans'][0]['planner'])

    async def test_persistent_initial_redirect_never_runs_test_or_grants_pass(self):
        def transport(req):
            observation={'text':'Dashboard','elements':[],'entryState':{'atInitialUrl':False,'initialEntryRequired':True}}
            return httpx.Response(200,json={'sessionId':'s','observation':observation,'records':[]})
        async def planner(_):self.fail('Initial URL mismatch must block TC execution')
        with patch('test_execution.plan_initial_entry',return_value='RESET_SESSION'):
            result=await execute_feature(self.request().model_copy(update={'initialEntry':True}),planner,transport=httpx.MockTransport(transport),check_active=False)
        self.assertFalse(result['initialEntryVerified']);self.assertEqual('EXECUTION_INITIAL_ENTRY_NOT_READY',result['items'][0]['reasonCode']);self.assertIsNone(result['items'][0]['result'])

    async def test_verified_login_redirect_does_not_trigger_recovery(self):
        def transport(req):return httpx.Response(200,json={'sessionId':'s','observation':{'text':'로그인','elements':[],'entryState':{'atInitialUrl':False,'initialScreenReady':True,'initialEntryRequired':True,'redirectDisposition':'LOGIN_ENTRY_REDIRECT'}}})
        async def planner(_):return Plan(actions=[],decision='NEEDS_INPUT',missingInputs=['시험값'],explanation='로그인 초기 화면 확인')
        with patch('test_execution.plan_initial_entry') as recovery:
            result=await execute_feature(self.request().model_copy(update={'initialEntry':True}),planner,transport=httpx.MockTransport(transport),check_active=False)
        recovery.assert_not_called();self.assertTrue(result['initialEntryVerified']);self.assertIsNone(result['items'][0]['result'])

if __name__=='__main__':unittest.main()
