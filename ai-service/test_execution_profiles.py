import json
import unittest
import httpx
from test_execution import Execute, Plan, execute_feature
from execution_profiles import profile, target_observed


class ProfilesTest(unittest.IsolatedAsyncioTestCase):
    def request(self):
        return Execute(runId='test',attempt='attempt',targetUrl='http://fixture/reports',featureId='report',featurePath=['제품','Reports','보고서 조회'],cases=[{'id':'case','ruleId':'FEATURE-01','scopeDescription':'보고서를 조회하는 기능','expectedResult':'REPORT_READY'}])

    def answers(self,choice='P'):
        return {'case_0':{'choice':choice,'confidence':.99},'scope_0':{'choice':'YES','confidence':.99},'coverage_0':{'choice':'COMPLETE','confidence':.99}}

    async def test_view_check_does_not_require_repeated_menu_clicks(self):
        async def planner(_):self.fail('Code should handle an observed read-only view')
        for body,proposed,expected in [('REPORT_READY','P','P'),('REPORT_BROKEN','F','F'),('REPORT_BROKEN','P',None)]:
            def transport(req):
                if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'url':'http://fixture/reports','text':'REPORT_READY in sidebar\n'+body,'mainText':body,'headings':['Reports'],'screenReady':True,'elements':[]}})
                return httpx.Response(200,json={})
            result=await execute_feature(self.request(),planner,lambda *_:self.answers(proposed),httpx.MockTransport(transport),False)
            self.assertEqual(expected,result['items'][0]['result'])
            self.assertEqual('CODE_READ_CHECK',result['plans'][0]['planner'])

    async def test_sidebar_alone_cannot_pass_a_read_test(self):
        def transport(req):
            return httpx.Response(200,json={'sessionId':'s','observation':{'url':'http://fixture/dashboard','text':'Reports REPORT_READY','mainText':'','headings':[],'elements':[]}} if req.url.path.endswith('/open') else {})
        async def planner(_):return Plan(actions=[],decision='FINISH',missingInputs=[],explanation='사이드바만 보임')
        def judge(*_):self.fail('An unrelated sidebar is not execution proof')
        result=await execute_feature(self.request(),planner,judge,httpx.MockTransport(transport),False)
        self.assertIsNone(result['items'][0]['result'])

    async def test_recorded_route_is_matched_against_current_elements(self):
        steps=[]
        def transport(req):
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':{'url':'http://fixture/dashboard','mainText':'Dashboard','headings':['Dashboard'],'elements':[{'id':'55','name':'Reports','navigation':True,'href':'http://fixture/reports'}]}})
            if req.url.path.endswith('/step'):
                steps.append(json.loads(req.content));return httpx.Response(200,json={'records':[{'action':'click','isNavigation':True,'status':'COMPLETED'}],'observation':{'url':'http://fixture/reports','mainText':'REPORT_READY','text':'REPORT_READY','headings':['Reports'],'elements':[]}})
            return httpx.Response(200,json={})
        async def planner(_):self.fail('A unique observed route must not require inference')
        request=self.request().model_copy(update={'navigationHints':[{'path':['Reports'],'label':'Reports','href':'http://fixture/reports'}]})
        result=await execute_feature(request,planner,lambda *_:self.answers(),httpx.MockTransport(transport),False)
        self.assertEqual('55',steps[0]['actions'][0]['elementId']);self.assertEqual('P',result['items'][0]['result'])
        steps.clear()
        without_crawl_ids=self.request()
        result=await execute_feature(without_crawl_ids,planner,lambda *_:self.answers(),httpx.MockTransport(transport),False)
        self.assertEqual('55',steps[0]['actions'][0]['elementId']);self.assertEqual('P',result['items'][0]['result'])

    async def test_live_clock_changes_cannot_defeat_navigation_loop_guard(self):
        steps=0
        def transport(req):
            nonlocal steps
            observation={'url':'http://fixture/dashboard','text':f'Dashboard 12:00:{steps:02d}','elements':[{'id':'1','name':'Dashboard','navigation':True,'href':'http://fixture/dashboard'}]}
            if req.url.path.endswith('/open'):return httpx.Response(200,json={'sessionId':'s','observation':observation})
            if req.url.path.endswith('/step'):
                steps+=1;return httpx.Response(200,json={'records':[{'action':'click','status':'COMPLETED','isNavigation':True}],'observation':observation})
            return httpx.Response(200,json={})
        async def planner(_):return Plan(actions=[{'action':'click','elementId':'1'}],decision='EXECUTE',missingInputs=[],explanation='반복 이동')
        request=self.request().model_copy(update={'cases':[{'id':'case','scenario':'검색 메시지','expectedResult':'READY'}]})
        result=await execute_feature(request,planner,transport=httpx.MockTransport(transport),check_active=False)
        self.assertEqual(1,steps);self.assertEqual('EXECUTION_NO_PROGRESS',result['items'][0]['reasonCode'])
        self.assertIsNone(result['items'][0]['result'])

    def test_interaction_is_not_reclassified_as_a_view(self):
        self.assertEqual('INTERACTIVE',profile({'ruleId':'FEATURE-01','scopeDescription':'사용자를 조회하고 수정하는 기능'}))
        self.assertEqual('READ_ONLY_VIEW',profile({'ruleId':'FEATURE-01','scopeDescription':'작업 실행 현황 통계와 시스템 상태를 요약한 대시보드를 조회하는 기능'}))
        self.assertEqual('INTERACTIVE',profile({'ruleId':'FEATURE-01','scopeDescription':'대시보드를 PDF 파일로 인쇄하는 기능'}))
        self.assertFalse(target_observed({'url':'http://fixture/reports','mainText':'','headings':['Reports']},['Reports']))


if __name__=='__main__':unittest.main()
