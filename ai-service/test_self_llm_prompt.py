import json
from pathlib import Path
import unittest
from export_self_llm_rules import document
from self_llm_prompt import build_prompt, VERSION


class SelfLlmPromptTest(unittest.TestCase):
    def test_download_matches_contract_and_contains_no_product_data(self):
        path = Path(__file__).parent.parent / 'frontend/public/downloads/menu-tree-common-rules.md'
        text = document()
        self.assertEqual(path.read_text(encoding='utf-8'), text)
        self.assertIn(VERSION, text)
        self.assertIn('2000', text)
        self.assertNotIn('최대 8개 행', text)
        self.assertNotIn('현재 source와 menuCandidates', text)
        self.assertIn('evidence', text)

    def test_product_request_keeps_all_sources_and_structure_without_common_schema(self):
        request = {'jobId': 'fixture', 'productName': '시험품',
                   'webStructure': {'nodes': [{'path': ['운영', '로그']}]},
                   'evidence': [{'id': 'doc:1', 'text': '원문 시작\n' + '한글 자료' * 12000 + '\n원문 끝'}]}
        request['evidenceCatalog']=[{'refId':'E0001','text':request['evidence'][0]['text']}]
        text = build_prompt(request, {}, {}, {})
        data = text.split('# 전체 분석 자료 시작\n', 1)[1].split('\n# 전체 분석 자료 끝', 1)[0]
        payload=json.loads(data)
        self.assertEqual(payload['requestId'],request['jobId'])
        self.assertEqual(payload['evidenceCatalog'],request['evidenceCatalog'])
        self.assertNotIn('doc:1',text)
        self.assertNotIn('matchedMenuKeys',text)
        self.assertIn(VERSION, text)
        self.assertNotIn('# 출력 JSON 스키마', text)
        self.assertIn('끝 표식', text)
