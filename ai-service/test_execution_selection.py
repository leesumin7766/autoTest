import copy
import unittest
from execution_selection import preflight
from test_execution import Execute, execute_feature


class SelectionTests(unittest.IsolatedAsyncioTestCase):
    def case(self):
        return {'id':'create-on-delete', 'ruleId':'CRUD-06','scenario':'사용자 등록 확인','scopeDescription':'선택한 사용자를 삭제하는 기능',
                'executionPlan':{'selection':{'exclusionCandidate':True,'requiredOperation':'CREATE','scopeOperation':'DELETE','scopeName':'사용자 삭제'}}}

    async def test_confirmed_mismatch_preserves_case_and_never_opens_browser_or_grants_na(self):
        tc=self.case();original=copy.deepcopy(tc)
        async def selector(payload):return {'decision':'NOT_APPLICABLE','confidence':.99,'scopeQuote':payload['scopeName'],'reason':'삭제 기능에 등록 시험은 적용되지 않습니다.'}
        async def planner(_):self.fail('Must not plan browser actions')
        request=Execute(runId='r',attempt='a',targetUrl='http://unreachable.invalid',featureId='f',featurePath=['제품','사용자 삭제'],cases=[tc])
        result=await execute_feature(request,planner=planner,selector=selector,check_active=False)
        item=result['items'][0]
        self.assertEqual('EXCLUDED',item['executionStatus']);self.assertIsNone(item['result'])
        self.assertEqual('NEEDS_REVIEW',item['judgmentStatus']);self.assertEqual(original,tc)
        self.assertEqual('QWEN_SELECTION',result['plans'][0]['planner'])

    async def test_unknown_low_confidence_wrong_quote_and_failure_do_not_exclude(self):
        for decision,confidence,quote in [('UNKNOWN',.99,'사용자 삭제'),('NOT_APPLICABLE',.89,'사용자 삭제'),('NOT_APPLICABLE',.99,'다른 기능'),('APPLICABLE',.99,'사용자 삭제')]:
            async def selector(_):return {'decision':decision,'confidence':confidence,'scopeQuote':quote,'reason':'판단'}
            result=await preflight(self.case(),selector)
            self.assertEqual('NEEDS_CONTEXT',result['items'][0]['executionStatus']);self.assertIsNone(result['items'][0]['result'])
        async def broken(_):raise RuntimeError('provider error')
        self.assertEqual('NEEDS_CONTEXT',(await preflight(self.case(),broken))['items'][0]['executionStatus'])

    async def test_applicable_or_combined_scope_cannot_be_excluded_by_model(self):
        async def selector(_):self.fail('No operation mismatch; do not ask model to exclude')
        tc=self.case();tc['executionPlan']['selection']['scopeOperation']='CREATE'
        self.assertIsNone(await preflight(tc,selector))
        tc['executionPlan']['selection']['scopeOperation']=''
        self.assertIsNone(await preflight(tc,selector))
        tc['executionPlan']['selection'].update(requiredOperation='WRITE',scopeOperation='UPDATE')
        self.assertIsNone(await preflight(tc,selector))

    async def test_missing_internal_evidence_is_not_browser_test_or_na(self):
        tc={'id':'storage','automationMode':'ADDITIONAL_EVIDENCE'}
        request=Execute(runId='r',attempt='a',targetUrl='http://unreachable.invalid',featureId='f',featurePath=['제품'],cases=[tc])
        result=await execute_feature(request,check_active=False)
        self.assertEqual('NEEDS_CONTEXT',result['items'][0]['executionStatus']);self.assertIsNone(result['items'][0]['result'])


if __name__=='__main__':unittest.main()
