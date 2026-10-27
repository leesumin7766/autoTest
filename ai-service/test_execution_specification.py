import copy
import hashlib
import json
import unittest
import httpx

from execution_specification import VERSION, READ_ACTIONS, prepare_read
from test_execution import Execute, execute_feature


def sample_case():
    tc = {'id': 'read-case', 'tcId': 'TC-001-001', 'ruleId': 'CRUD-08', 'scenario': '보고서 조회',
          'inputs': '조건 입력 없이 조회한다.', 'expectedResult': 'REPORT_READY', 'scopeDescription': '보고서를 조회하는 기능',
          'evidence': [{'quote': '조건 입력 없이 조회한다.'}]}
    digest = hashlib.sha256(''.join(tc[k] for k in ('scenario', 'inputs', 'expectedResult')).encode()).hexdigest()
    target = {'menuKey': 'reports-menu', 'href': 'http://fixture/reports', 'heading': 'Reports'}
    tc['executionPlan'] = {'specificationVersion': VERSION, 'tcContentDigest': digest, 'selection': {'scopeOperation': 'READ'},
        'specification': {'schemaVersion': VERSION, 'sourceTcDigest': digest, 'profile': 'READ_ONLY_VIEW', 'status': 'READY', 'missingBindings': [], 'timeoutSeconds': 60,
            'start': {'url': 'http://fixture/dashboard', 'session': 'FRESH_PER_TC', 'authentication': 'SAVED_SETUP_REFERENCE'},
            'target': {'featurePath': ['제품', 'Reports', '보고서 조회'], 'menuKeys': ['reports-menu']},
            'bindings': {'target': target, 'readContext': {'mode': 'NO_FILTER', 'oracle': {'kind': 'DOCUMENT_EVIDENCE', 'index': 0, 'quote': '조건 입력 없이 조회한다.'}}},
            'steps': [{'action': action, 'status': 'BOUND'} for action in READ_ACTIONS],
            'checks': [{'requirementQuote': 'REPORT_READY', 'method': 'TEXT_PRESENT', 'expected': 'REPORT_READY', 'status': 'BOUND',
                        'oracle': {'kind': 'TC_EXPECTED', 'quote': 'REPORT_READY'}}]}}
    return tc


class ReadSpecificationTests(unittest.IsolatedAsyncioTestCase):
    def request(self, tc=None, **updates):
        return Execute(runId='spec-test', attempt='attempt', targetUrl='http://fixture/dashboard', featureId='reports',
            featurePath=['제품', 'Reports', '보고서 조회'], cases=[tc or sample_case()],
            navigationHints=[{'menuKey': 'reports-menu', 'href': 'http://fixture/reports', 'label': 'Reports', 'path': ['Reports']}], **updates)

    def answers(self, verdict='P'):
        return {'case_0': {'choice': verdict, 'confidence': .99}, 'scope_0': {'choice': 'YES', 'confidence': .99},
                'coverage_0': {'choice': 'COMPLETE', 'confidence': .99}}

    async def no_planner(self, _):
        self.fail('A versioned specification must not fall back to Qwen')

    async def test_prepared_recipe_navigates_and_checks_without_model_planning(self):
        for body, verdict, expected in [('REPORT_READY', 'P', 'P'), ('REPORT_BROKEN', 'F', 'F'), ('REPORT_BROKEN', 'P', None)]:
            with self.subTest(body=body, verdict=verdict):
                steps = []
                def transport(req):
                    if req.url.path.endswith('/open'):
                        self.assertFalse(json.loads(req.content)['allowMutation'])
                        return httpx.Response(200, json={'sessionId': 's', 'observation': {'url': 'http://fixture/dashboard', 'mainText': 'Dashboard',
                            'headings': ['Dashboard'], 'elements': [{'id': '10', 'navigation': True, 'name': 'Reports', 'href': 'http://fixture/reports'}]}})
                    if req.url.path.endswith('/step'):
                        steps.append(json.loads(req.content))
                        return httpx.Response(200, json={'records': [{'action': 'click', 'status': 'COMPLETED', 'isNavigation': True}],
                            'observation': {'url': 'http://fixture/reports', 'mainText': body, 'text': body, 'headings': ['Reports'], 'elements': [], 'screenReady': True}})
                    return httpx.Response(200, json={})
                result = await execute_feature(self.request(allowMutation=True), self.no_planner, lambda *_: self.answers(verdict), httpx.MockTransport(transport), False)
                self.assertEqual(expected, result['items'][0]['result'])
                self.assertEqual(1, len(steps))
                self.assertTrue(all(p['planner'] == 'CODE_EXECUTION_SPEC' for p in result['plans']))

    async def test_unbound_and_stale_recipes_never_open_browser_or_call_models(self):
        base = sample_case()
        cases = []
        unbound = copy.deepcopy(base); unbound['executionPlan']['specification']['bindings']['target'] = None; cases.append(unbound)
        stale = copy.deepcopy(base); stale['expectedResult'] += '\nANOTHER_CONDITION'; cases.append(stale)
        unresolved = copy.deepcopy(base); unresolved['executionPlan']['specification']['status'] = 'NEEDS_CONTEXT'; cases.append(unresolved)
        unsupported = copy.deepcopy(base); unsupported['executionPlan']['specification']['steps'][1]['action'] = 'FILL'; cases.append(unsupported)
        missing_check = copy.deepcopy(base); missing_check['executionPlan']['specification']['checks'] = []; cases.append(missing_check)
        invented_source = copy.deepcopy(base); invented_source['executionPlan']['specification']['bindings']['readContext']['oracle']['quote'] = 'invented'; cases.append(invented_source)
        unknown = copy.deepcopy(base); unknown['executionPlan']['specificationVersion'] = 'execution-spec-future'; cases.append(unknown)
        malformed = copy.deepcopy(base); malformed['executionPlan']['specification']['bindings'] = 'invalid'; cases.append(malformed)
        for tc in cases:
            with self.subTest(tc=tc):
                def transport(_): self.fail('Unprepared recipe must be stopped before browser open')
                def judge(*_): self.fail('Unperformed TC must not be judged')
                result = await execute_feature(self.request(tc, initialEntry=True), self.no_planner, judge, httpx.MockTransport(transport), False)
                self.assertEqual('NEEDS_CONTEXT', result['items'][0]['executionStatus'])
                self.assertEqual('EXECUTION_SPEC_NEEDS_CONTEXT', result['items'][0]['reasonCode'])
                self.assertIsNone(result['items'][0]['result'])
                self.assertTrue(result['items'][0]['missingBindings'])
                self.assertNotIn('initialEntryVerified', result)

    async def test_same_label_with_other_url_and_sidebar_only_cannot_pass(self):
        for observation in [
            {'url': 'http://fixture/dashboard', 'mainText': 'Dashboard', 'headings': ['Dashboard'], 'elements': [
                {'id': '9', 'name': 'Reports', 'navigation': True, 'href': 'http://fixture/wrong'}]},
            {'url': 'http://fixture/reports', 'mainText': '', 'text': 'Reports REPORT_READY', 'headings': [], 'elements': []},
            {'url': 'http://fixture/reports', 'mainText': 'REPORT_READY', 'headings': ['Dashboard'], 'elements': []}]:
            def transport(req):
                if req.url.path.endswith('/open'): return httpx.Response(200, json={'sessionId': 's', 'observation': observation})
                self.assertTrue(req.url.path.endswith('/close'))
                return httpx.Response(200, json={})
            def judge(*_): self.fail('Wrong screen cannot be judged')
            result = await execute_feature(self.request(), self.no_planner, judge, httpx.MockTransport(transport), False)
            self.assertEqual('NEEDS_CONTEXT', result['items'][0]['executionStatus'])
            self.assertIsNone(result['items'][0]['result'])

    def test_legacy_recipe_remains_on_existing_path(self):
        tc = sample_case(); tc['executionPlan']['specificationVersion'] = 'execution-spec-v1'
        self.assertEqual((None, []), prepare_read(tc, [], []))


if __name__ == '__main__':
    unittest.main()
