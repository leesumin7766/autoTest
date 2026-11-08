"""2026-11-08: 시험원이 TC 별로 적은 시험 자료(userContext)를 계획·판정에 넘긴다. 관찰 증거로 쓰지 않는다."""
import json
import unittest
from unittest.mock import patch

import test_execution
from test_execution import INSTRUCTIONS, compact_case, plan_with_qwen, user_context_text


def case(**extra):
    tc = {'id': 'tc', 'tcId': 'TC-1', 'ruleId': 'CRUD-02', 'scenario': '사용자 등록', 'inputs': '필수값 입력',
          'expectedResult': '등록되었습니다. 문구가 표시된다.', 'scopeDescription': '사용자 등록', 'executionPlan': {}}
    tc.update(extra)
    return tc


CONTEXT = {'source': 'TESTER_SUPPLIED', 'items': [{'need': '필드별 유효값', 'value': '아이디 qa_user01 / 이름 시험계정'}], 'note': '권한은 사용자'}


class UserContextTest(unittest.IsolatedAsyncioTestCase):
    def test_compact_case_keeps_user_context_only_when_supplied(self):
        self.assertNotIn('userContext', compact_case(case()))
        self.assertEqual(CONTEXT, compact_case(case(userContext=CONTEXT))['userContext'])

    def test_user_context_text_joins_values_and_note(self):
        self.assertEqual('아이디 qa_user01 / 이름 시험계정\n권한은 사용자', user_context_text(case(userContext=CONTEXT)))
        self.assertEqual('', user_context_text(case()))
        self.assertEqual('', user_context_text(case(userContext={'items': ['broken', {'value': '  '}]})))

    def test_instructions_say_it_is_not_evidence(self):
        self.assertIn('userContext', INSTRUCTIONS)
        self.assertIn('NOT an observation', INSTRUCTIONS)

    async def test_planner_receives_context_and_may_use_its_values_as_expected(self):
        captured = {}

        class Response:
            def raise_for_status(self): pass
            def json(self): return {'message': {'content': json.dumps({'actions': [], 'decision': 'FINISH', 'phase': 'TEST', 'missingInputs': [], 'explanation': 'x', 'conditions': []})}}

        class Client:
            def __init__(self, *args, **kwargs): pass
            async def __aenter__(self): return self
            async def __aexit__(self, *args): return False
            async def post(self, url, json=None):
                captured['body'] = json
                return Response()

        with patch.object(test_execution.httpx, 'AsyncClient', Client):
            await plan_with_qwen({'cases': [compact_case(case(userContext=CONTEXT))], 'observation': {}})
        body = captured['body']
        user = json.loads(body['messages'][1]['content'])
        self.assertEqual(CONTEXT, user['cases'][0]['userContext'])
        allowed = body['format']['$defs']['Condition']['properties']['expected']['enum']
        self.assertIn('qa_user01', allowed, '시험원이 준 값은 기대값으로 쓸 수 있다')

        with patch.object(test_execution.httpx, 'AsyncClient', Client):
            await plan_with_qwen({'cases': [compact_case(case())], 'observation': {}})
        allowed = captured['body']['format']['$defs']['Condition']['properties']['expected']['enum']
        self.assertNotIn('qa_user01', allowed, '시험 자료가 없으면 그 값을 만들어 쓸 수 없다')


if __name__ == '__main__':
    unittest.main()
