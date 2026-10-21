import asyncio
import os
import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from functional_menu import Request, baseline, validate_rows, prompt, generate, budget_segments, call_spec, request_size, AUTO_INSTRUCTIONS, ground_proposal, checkpoint_path
from tempfile import TemporaryDirectory
from pathlib import Path
from web_menu import structure, anchor
from product_description.ollama_provider import OllamaLlmProvider
from main import app

class FunctionalMenuTest(unittest.TestCase):
    def test_web_hierarchy_owns_groups_and_optional_bars(self):
        evidence=[{'id':'live','kind':'MENU','menuKey':'live','path':['메뉴','운영','실시간'],'text':'운영 실시간','location':'SIDEBAR'},
            {'id':'theme','kind':'MENU','menuKey':'theme','path':['메뉴','테마 전환'],'text':'테마 전환','location':'SIDEBAR'},
            {'id':'search','kind':'MENU','menuKey':'search','path':['메뉴','검색'],'text':'검색','location':'HEADER','barConfirmed':True}]
        tree=structure(evidence,'NeoFlow Server');paths=[node['path'] for node in tree['nodes']]
        self.assertIn(['운영','실시간'],paths);self.assertIn(['테마 전환'],paths);self.assertIn(['상단바','검색'],paths)
        self.assertFalse(any('하단바' in path for path in paths))
        row=anchor({'menuPath':['Excel 1depth'],'matchedMenuKeys':['live'],'reviewNotes':[]},{s['id']:s for s in evidence})
        self.assertEqual(row['menuPath'],['운영','실시간'])
        unknown=anchor({'menuPath':['임의 그룹'],'matchedMenuKeys':[],'reviewNotes':[]},{})
        self.assertFalse(unknown['included']);self.assertEqual(unknown['placementStatus'],'UNASSIGNED')
    def test_selected_menu_gets_program_evidence_without_semantic_confirmation(self):
        request=Request(jobId='grounding',productName='Example',evidence=self.sources)
        allowed,spans,_,_=call_spec(request,self.sources[1],[self.sources[0]])
        raw={key:value for key,value in self.row.items() if key!='evidence'}
        raw['evidenceSpanIds']=[next(span['spanId'] for span in spans if span['sourceId']=='doc-a')]
        result=ground_proposal(raw,allowed,spans,'doc-a')
        self.assertEqual(result['matchedMenuKeys'],['a'])
        self.assertEqual({ref['sourceId'] for ref in result['evidence']},{'menu-a','doc-a'})
        self.assertTrue(any('검토 전' in note for note in result['reviewNotes']))
        raw['matchedMenuKeys']=['invented-menu']
        document_only=ground_proposal(raw,allowed,spans,'doc-a')
        self.assertEqual(document_only['matchedMenuKeys'],[])
        self.assertEqual([ref['sourceId'] for ref in document_only['evidence']],['doc-a'])

    def test_invalid_row_does_not_discard_valid_rows_and_checkpoint(self):
        class MixedModel(OllamaLlmProvider):
            def __init__(self):pass
            def input_budget_for(self,*args):return 100000
            async def _call(self,instructions,payload,schema,num_predict=None):
                row={'menuPath':[],'functionName':'서비스 조회','menuDescription':'서비스 목록을 조회하는 기능','location':'UNKNOWN','matchedMenuKeys':[],
                    'evidenceSpanIds':[payload['sourceSpans'][0]['spanId']],'reviewNotes':[]}
                return {'rows':[row,{**row,'evidenceSpanIds':['nonexistent-span']}]}
        request=Request(jobId='partial-test',productName='Example',evidence=self.sources)
        with TemporaryDirectory() as directory,patch('functional_menu.CHECKPOINT_ROOT',Path(directory)),patch('functional_menu.provider_from_env',return_value=MixedModel()):
            result=asyncio.run(generate(request))
            self.assertEqual(result['completionStatus'],'PARTIAL')
            self.assertTrue(any(row['functionName']=='서비스 조회' for row in result['rows']))
            self.assertEqual(result['issues'][0]['code'],'MENU_SPAN_ID_INVALID')
            self.assertTrue(checkpoint_path(request.jobId).exists())
    def test_long_korean_sources_fit_model_budget_without_losing_text(self):
        text='작업의 스케줄을 조회하는 기능입니다.\n'*120
        source={'id':'doc:long-korean','kind':'DOCUMENT','role':'MANUAL','text':text}
        request=Request(jobId='budget',productName='NeoFlow Server',evidence=[source])
        parts=budget_segments(request,source,5632)
        self.assertGreater(len(parts),1)
        covered=set()
        for part in parts:
            start=part.get('segmentStart',0)
            self.assertEqual(part['text'],text[start:start+len(part['text'])])
            covered.update(range(start,start+len(part['text'])))
            _,_,schema,payload=call_spec(request,part,[])
            self.assertLessEqual(request_size(AUTO_INSTRUCTIONS,payload,schema)+600,5632)
        self.assertEqual(len(covered),len(text))
    def setUp(self):
        self.sources=[{'id':'menu-a','kind':'MENU','menuKey':'a','path':['Global','System','Services'],'location':'SIDEBAR','text':'Global → System → Services'},
                      {'id':'doc-a','kind':'DOCUMENT','role':'FUNCTION_LIST','text':'서비스 목록을 조회하고 서비스를 검색할 수 있다.'}]
        self.row={'menuPath':['System','Services'],'functionName':'서비스 조회','menuDescription':'서비스 목록을 조회하는 기능','location':'SIDEBAR','matchedMenuKeys':['a'],
                  'evidence':[{'sourceId':'menu-a','quote':'Services'},{'sourceId':'doc-a','quote':'서비스 목록을 조회'}],'reviewNotes':[]}
    def test_exact_quotes_and_menu_match_are_required(self):
        sources={source['id']:source for source in self.sources}
        self.assertEqual(len(validate_rows([self.row],sources,'doc-a')),1)
        bad={**self.row,'evidence':[{'sourceId':'doc-a','quote':'존재하지 않는 기능'}]}
        with self.assertRaises(ValueError):validate_rows([bad],sources)
        with self.assertRaises(ValueError):validate_rows([{**self.row,'matchedMenuKeys':['unknown']}],sources)
        with self.assertRaises(ValueError):validate_rows([{**self.row,'menuPath':['1','2','3','4']}],sources)
    def test_schema_examples_and_mock_are_explicit(self):
        request=Request(jobId='test',productName='Example',evidence=self.sources)
        self.assertEqual(len(baseline(self.sources)),1)
        self.assertIn('스케줄',prompt(request))
        with patch.dict(os.environ,{'LLM_MODE':'mock'}):
            result=asyncio.run(generate(request))
        self.assertEqual(result['generationMode'],'MOCK')
        self.assertTrue(result['rows'])
    def test_internal_authentication_and_safe_errors(self):
        with patch.dict(os.environ,{'PRODUCT_DESCRIPTION_INTERNAL_TOKEN':'test-functional','LLM_MODE':'mock'}):
            client=TestClient(app)
            body={'jobId':'test','productName':'Example','evidence':self.sources}
            self.assertEqual(client.post('/api/v1/functional-menus/generate',json=body).status_code,403)
            response=client.post('/api/v1/functional-menus/generate',json=body,headers={'X-Internal-Token':'test-functional'})
            self.assertEqual(response.status_code,200)
            self.assertEqual(response.json()['generationMode'],'MOCK')
    def test_missing_documented_action_is_recovered_with_grounded_span_ids(self):
        class SmallModel(OllamaLlmProvider):
            def __init__(self):pass
            def input_budget_for(self,*args):return 100000
            async def _call(self,instructions,payload,schema,num_predict=None):
                focused=schema['properties']['rows']['items']['properties']['functionName'].get('pattern','')
                verb='조회' if '조회' in focused else '삭제'
                return {'rows':[{'menuPath':[],'functionName':'스케줄 '+verb,'menuDescription':'스케줄을 '+verb+'하는 기능','location':'UNKNOWN','matchedMenuKeys':[],
                    'evidenceSpanIds':[payload['sourceSpans'][0]['spanId']],'reviewNotes':[]}]}
        request=Request(jobId='coverage-test',productName='Example',evidence=[{'id':'doc','kind':'DOCUMENT','role':'FUNCTION_LIST','text':'스케줄을 조회하고 스케줄을 삭제할 수 있습니다.'}])
        with patch('functional_menu.provider_from_env',return_value=SmallModel()):
            result=asyncio.run(generate(request))
        descriptions=' '.join(row['menuDescription'] for row in result['rows'])
        self.assertIn('조회',descriptions);self.assertIn('삭제',descriptions)
        self.assertTrue(all(row['evidence'][0]['sourceId']=='doc' for row in result['rows']))

if __name__=='__main__':unittest.main()
