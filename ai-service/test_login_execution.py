import json
import unittest
from unittest.mock import patch
import httpx
from test_execution import Execute, execute_feature
from execution_evidence import assess,gate
from execution_contract import performed_test

class LoginExecutionTest(unittest.IsolatedAsyncioTestCase):
    def request(self):
        return Execute(runId='run',attempt='attempt',targetUrl='http://fixture.local/',featureId='authentication:login',featurePath=['제품','로그인 화면','로그인'],initialEntry=True,
            setup={'loginMethod':'FORM_AUTO','automation':{'username':'test-account'}},password='test-secret',
            cases=[{'id':'login','ruleId':'LOGIN-04','scenario':'유효한 아이디와 비밀번호로 로그인','expectedResult':'로그인 후 대시보드가 표시된다.'}])

    async def run_login(self,outcome='AUTHENTICATED'):
        steps=[];calls=[]
        def transport(req):
            calls.append(req.url.path)
            if req.url.path.endswith('/open'):
                self.assertEqual(json.loads(req.content)['testRuleId'],'LOGIN-04')
                return httpx.Response(200,json={'sessionId':'s','observation':{'text':'Login Username Password','elements':[],
                    'entryState':{'atInitialUrl':True,'initialEntryRequired':True},'authentication':{'savedAccountAvailable':True,'dedicatedLoginAllowed':True,'loginAttempted':False}}})
            if req.url.path.endswith('/step'):
                actions=json.loads(req.content)['actions'];steps.extend(actions);self.assertEqual('login_saved_account',actions[0]['action'])
                self.assertNotIn('test-secret',req.content.decode())
                return httpx.Response(200,json={'records':[{'action':'login_saved_account','status':'COMPLETED','isNavigation':False,
                    'authenticationSteps':['USERNAME_FILLED','PASSWORD_FILLED','SUBMIT_ATTEMPTED','SUBMITTED']+(['AUTHENTICATED'] if outcome=='AUTHENTICATED' else []),'authenticationOutcome':outcome}],
                    'observation':{'text':'Dashboard' if outcome=='AUTHENTICATED' else 'Login denied','elements':[]}})
            return httpx.Response(200,json={})
        async def forbidden(_):self.fail('Normal login must not use the generic large planner')
        async def small(state):
            self.assertNotIn('test-secret',json.dumps(state));self.assertNotIn('test-account',json.dumps(state));return 'LOGIN_SAVED_ACCOUNT'
        def judge(*_):return {'case_0':{'choice':'P','confidence':.99},'scope_0':{'choice':'YES','confidence':.99},'coverage_0':{'choice':'COMPLETE','confidence':.99},'condition_0_0':{'choice':'PASS','confidence':.99}}
        with patch('test_execution.plan_saved_login',side_effect=small) as planner:
            result=await execute_feature(self.request(),forbidden,judge,httpx.MockTransport(transport),False)
        self.assertEqual(1,len(steps));self.assertEqual(1,planner.await_count);self.assertTrue(calls[-1].endswith('/close'));return result

    async def test_saved_account_is_submitted_once_then_evidence_is_judged(self):
        result=await self.run_login();self.assertEqual('P',result['items'][0]['result']);self.assertTrue(result['initialEntryVerified'])
        self.assertNotIn('test-secret',json.dumps(result));self.assertEqual(['QWEN_SAVED_LOGIN','CODE_LOGIN_FINISH'],[p['planner'] for p in result['plans']])

    async def test_even_positive_judge_cannot_pass_unconfirmed_authentication(self):
        result=await self.run_login('UNCONFIRMED');self.assertIsNone(result['items'][0]['result']);self.assertIn('인증 성공 증거 없음',result['items'][0]['reason'])

    async def test_missing_account_does_not_call_browser_or_model(self):
        async def forbidden(_):self.fail('No account should require input before model calls')
        result=await execute_feature(self.request().model_copy(update={'password':None}),forbidden,check_active=False)
        self.assertEqual('NEEDS_CONTEXT',result['items'][0]['executionStatus']);self.assertEqual([],result['evidence'])

    def test_plain_login_click_is_not_normal_login_execution_evidence(self):
        evidence=[{'kind':'BROWSER_ACTIONS','records':[{'action':'click','status':'COMPLETED','isNavigation':False}]}]
        self.assertFalse(performed_test(evidence,self.request().cases[0]));self.assertFalse(assess(self.request().cases[0],[],evidence)['performed'])

if __name__=='__main__':unittest.main()
