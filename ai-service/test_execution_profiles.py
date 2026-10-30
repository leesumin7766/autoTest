import json
import unittest
import httpx
from test_execution import Execute, Plan, execute_feature
from execution_profiles import profile, target_observed, captured_screen_observed, navigation_plan
from execution_contract import prerequisites


class ProfilesTest(unittest.IsolatedAsyncioTestCase):
    def request(self):
        return Execute(runId='test',attempt='attempt',targetUrl='http://fixture/reports',featureId='report',featurePath=['제품','Reports','보고서 조회'],cases=[{'id':'case','ruleId':'FEATURE-01','scopeDescription':'보고서를 조회하는 기능','expectedResult':'REPORT_READY'}])

    def answers(self,choice='P'):
        return {'case_0':{'choice':choice,'confidence':.99},'scope_0':{'choice':'YES','confidence':.99},'coverage_0':{'choice':'COMPLETE','confidence':.99}}

    async def test_crud_screen_check_passes_only_a_reached_content_screen(self):
        tc={'id':'view','ruleId':'CRUD-VIEW-01','scopeDescription':'실행 현황과 통계를 조회하는 기능',
            'scenario':'대시보드 조회 화면 표시','inputs':'저장된 계정으로 대시보드 열기',
            'expectedResult':'연결된 조회 화면의 제목과 기본 본문이 표시된다.',
            'executionPlan':{'selection':{'scopeOperation':'READ'}}}
        request=self.request().model_copy(update={'cases':[tc], 'featurePath':['제품','대시보드','대시보드 조회'],
            'navigationHints':[{'path':['대시보드'],'label':'대시보드','href':'http://fixture/dashboard'}]})
        async def planner(_):self.fail('A bound read view must not call Qwen')
        def judge(*_):self.fail('The simple screen check must not call Jev')
        def transport(req):
            if req.url.path.endswith('/open'):
                return httpx.Response(200,json={'sessionId':'s','observation':{'url':'http://fixture/dashboard',
                    'text':'대시보드 실행 중 0 오늘 성공 291 전체 작업 19',
                    'mainText':'대시보드 실행 중 0 오늘 성공 291 전체 작업 19',
                    'headings':['대시보드'],'screenReady':True,'elements':[]}})
            return httpx.Response(200,json={})
        result=await execute_feature(request,planner,judge,httpx.MockTransport(transport),False)
        self.assertEqual('P',result['items'][0]['result'])
        self.assertEqual('CODE_READ_VIEW',result['items'][0]['judgedBy'])

    def test_captured_screen_check_rejects_wrong_route_or_error_content(self):
        hints=[{'href':'https://fixture.test/jobs'}]
        screen={'url':'https://fixture.test/jobs','headings':['Jobs'],'mainText':'Jobs 291 total jobs 10 running 281 completed','screenReady':True}
        self.assertTrue(captured_screen_observed(screen,hints))
        self.assertFalse(captured_screen_observed({**screen,'url':'https://fixture.test/dashboard'},hints))
        self.assertFalse(captured_screen_observed({**screen,'mainText':'404 page not found. Request failed on this page'},hints))
        self.assertFalse(captured_screen_observed({**screen,'headings':[]},hints))
        tab={**screen,'url':'https://fixture.test/performance','elements':[{'name':'개요','role':'tab','selected':True}]}
        self.assertTrue(captured_screen_observed(tab,[{'href':'','label':'개요'}]))
        self.assertFalse(captured_screen_observed({**tab,'elements':[{'name':'개요','role':'tab','selected':False}]},[{'href':'','label':'개요'}]))

    def test_generic_crud_read_does_not_start_browser_without_baseline(self):
        tc={'ruleId':'CRUD-01','inputs':'시험 데이터와 비교할 기대 데이터를 준비한다.',
            'expectedResult':'표시 항목이 기준 시점의 기대 데이터에 일치한다.',
            'executionPlan':{'selection':{'scopeOperation':'READ'}}}
        self.assertTrue(prerequisites(tc))
        self.assertFalse(prerequisites({**tc,'ruleId':'CRUD-VIEW-01'}))
        self.assertFalse(prerequisites({**tc,'inputs':'대상 ID 42','expectedResult':'상태 ACTIVE가 표시된다.'}))

    def test_nested_captured_menu_opens_parent_before_leaf(self):
        hint=[{'path':['설정 관리','설정','공통 설정'],'label':'공통 설정','href':'https://fixture.test/settings/global-properties'}]
        first={'elements':[{'id':'12','name':'설정 관리','navigation':True,'role':''}]}
        self.assertEqual('12',navigation_plan(first,hint)['elementId'])
        second={'elements':[{'id':'13','name':'설정 관리','navigation':True,'role':''},
                            {'id':'24','name':'설정','navigation':True,'role':''}]}
        self.assertEqual('24',navigation_plan(second,hint,{'설정 관리'})['elementId'])
        self.assertEqual('24',navigation_plan(second,hint)['elementId'])
        third={'elements':[{'id':'30','name':'공통 설정','navigation':True,'role':'','href':'https://fixture.test/settings/global-properties'}]}
        self.assertEqual('30',navigation_plan(third,hint,{'설정 관리','설정'})['elementId'])
        already_open={'elements':[{'id':'12','name':'설정 관리','navigation':True,'role':'','expanded':True},
                                  {'id':'13','name':'사용자 관리','navigation':True,'role':''}]}
        self.assertIsNone(navigation_plan(already_open,hint))

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
        self.assertEqual('READ_ONLY_VIEW',profile({'ruleId':'CRUD-01','scopeDescription':'Job 목록을 조회하는 기능','executionPlan':{'selection':{'scopeOperation':'READ'}}}))
        self.assertEqual('READ_ONLY_VIEW',profile({'ruleId':'CRUD-08','scopeDescription':'실행 현황 및 통계를 조회하는 기능','executionPlan':{'selection':{'scopeOperation':'READ'}}}))
        self.assertEqual('INTERACTIVE',profile({'ruleId':'CRUD-08','scopeDescription':'검색·필터 조건을 선택하여 조회하는 기능','executionPlan':{'selection':{'scopeOperation':'READ'}}}))
        self.assertEqual('INTERACTIVE',profile({'ruleId':'CRUD-01','scopeDescription':'Job 목록을 조회하고 삭제하는 기능','executionPlan':{'selection':{'scopeOperation':'READ'}}}))
        self.assertFalse(target_observed({'url':'http://fixture/reports','mainText':'','headings':['Reports']},['Reports']))
        self.assertTrue(target_observed({'url':'http://fixture/dashboard','text':'대시보드 실행 중 2건','mainText':'','headings':['대시보드']},['대시보드']))
        self.assertFalse(target_observed({'url':'http://fixture/dashboard','text':'사이드바 대시보드, 오류 화면','mainText':'','headings':['오류']},['대시보드']))


if __name__=='__main__':unittest.main()
