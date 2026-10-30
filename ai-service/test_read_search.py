import unittest

from read_search import prepare_search, search_result
from execution_profiles import profile


class ReadSearchTest(unittest.TestCase):
    def setUp(self):
        self.observation = {
            'screenReady': True,
            'elements': [{'id': '7', 'tag': 'input', 'type': 'text', 'name': '작업 검색'}],
            'tables': [{'headers': ['이름', '상태'], 'rows': [['Alpha job', '성공'], ['Beta job', '실패']]}],
        }

    def test_search_baseline_and_narrowed_result(self):
        binding = prepare_search(self.observation)
        self.assertEqual('7', binding['inputId'])
        self.assertEqual('Alpha job', binding['query'])
        after = {**self.observation, 'tables': [{'headers': ['이름', '상태'], 'rows': [['Alpha job', '성공']]}]}
        self.assertEqual('P', search_result(binding, after))

    def test_unchanged_list_needs_activation_evidence(self):
        self.assertIsNone(search_result(prepare_search(self.observation), self.observation))

    def test_uncertain_result_is_not_passed(self):
        binding = prepare_search(self.observation)
        self.assertIsNone(search_result(binding, {**self.observation, 'tables': []}))
        self.assertIsNone(search_result(binding, {**self.observation, 'tables': [{'headers': ['이름'], 'rows': [['Other']]}]}))
        self.assertIsNone(prepare_search({**self.observation, 'elements': []}))
        self.assertIsNone(prepare_search({**self.observation, 'tables': [{'headers': ['이름'], 'rows': [['Alpha job']]}]}))

    def test_profile_is_read_only_search(self):
        self.assertEqual('READ_ONLY_SEARCH', profile({'ruleId': 'CRUD-SEARCH-01', 'executionPlan': {'selection': {'scopeOperation': 'READ'}}}))

    def test_multiline_username_cell_uses_unique_visible_name(self):
        screen={**self.observation, 'elements': [{'id':'8','tag':'input','type':'','name':'사용자 검색...'}],
                'tables':[{'headers':['사용자명','이메일'], 'rows':[['PP\n\npig pig\n\n@pig','pig@example.com'],['CT\n\ncrawl test\n\n@crawl','crawl@example.com']]}]}
        binding=prepare_search(screen)
        self.assertEqual('pig',binding['query'])
        after={**screen,'tables':[{'headers':['사용자명','이메일'],'rows':[screen['tables'][0]['rows'][0]]}]}
        self.assertEqual('P',search_result(binding,after))


if __name__ == '__main__':
    unittest.main()
