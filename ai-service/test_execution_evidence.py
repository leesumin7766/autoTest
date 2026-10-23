import unittest
from execution_evidence import Condition, assess, gate

class EvidenceTest(unittest.TestCase):
    def test_planner_history_preserves_failure_without_repeating_unchanged_pages(self):
        from execution_contract import planner_history
        text='unchanged\n'*1000
        source=[{'id':'first','kind':'BROWSER_ACTIONS','records':[{'status':'COMPLETED','beforeText':text+'저장 전','text':text+'저장 실패','isNavigation':False}]}]
        history=planner_history(source)
        self.assertIn('저장 실패',history[0]['records'][0]['observedChanges'])
        self.assertLess(len(str(history)),1000)
        self.assertIn('저장 실패',source[0]['records'][0]['text'])
    def test_positive_literal_cannot_be_compiled_as_absence(self):
        from execution_contract import compiled_conditions
        condition=Condition(requirementQuote='성공',method='TEXT_ABSENT',expected='성공')
        self.assertEqual('TEXT_PRESENT',compiled_conditions({'expectedResult':'성공'},[condition])[0].method)
    def evidence(self,final='성공',action=True):
        return [{'id':'before','kind':'BROWSER_OBSERVATION','text':'시험 화면'}, {'id':'action','kind':'BROWSER_ACTIONS','records':[{'action':'click','status':'COMPLETED'}] if action else []}, {'id':'after','kind':'BROWSER_OBSERVATION','text':final}]
    def verdict(self,tc,conditions,evidence,scope='YES',coverage='COMPLETE'):
        proposed={'result':'P','judgmentStatus':'COMPLETED','defectCandidate':False}
        return gate(tc,proposed,assess(tc,conditions,evidence),{'scope_0':{'choice':scope,'confidence':.99},'coverage_0':{'choice':coverage,'confidence':.99}},0,.9)
    def test_explicit_matching_output_can_pass(self):
        tc={'expectedResult':'성공'};c=[Condition(requirementQuote='성공',method='TEXT_PRESENT',expected='성공')]
        self.assertEqual('P',self.verdict(tc,c,self.evidence())['result'])
    def test_false_pass_injections(self):
        c=[Condition(requirementQuote='성공',method='TEXT_PRESENT',expected='성공')]
        scenarios=[({'expectedResult':'성공'},c,self.evidence('실패'),'YES','COMPLETE'),
          ({'expectedResult':'성공 그리고 취소'},c,self.evidence(),'YES','COMPLETE'),
          ({'expectedResult':'성공'},c,self.evidence(action=False),'YES','COMPLETE'),
          ({'expectedResult':'성공'},c,self.evidence(),'NO','COMPLETE'),
          ({'expectedResult':'성공'},c,self.evidence(),'YES','PARTIAL'),
          ({'expectedResult':'성공'},[],self.evidence(),'YES','COMPLETE')]
        for tc,checks,observed,scope,coverage in scenarios:
            with self.subTest(tc=tc,scope=scope,coverage=coverage):self.assertIsNone(self.verdict(tc,checks,observed,scope,coverage)['result'])
    def test_whole_requirement_quote_does_not_turn_one_word_into_full_coverage(self):
        tc={'expectedResult':'성공 그리고 취소가 정상적으로 동작함'}
        condition=Condition(requirementQuote=tc['expectedResult'],method='TEXT_PRESENT',expected='성공')
        self.assertIsNone(self.verdict(tc,[condition],self.evidence())['result'])
        condition=Condition(requirementQuote=tc['expectedResult'],method='TEXT_ABSENT',expected='취소가 정상적으로 동작함')
        self.assertIsNone(self.verdict(tc,[condition],self.evidence())['result'])
    def test_save_toast_does_not_prove_persistence(self):
        tc={'expectedResult':'성공','scenario':'데이터 저장 후 재조회'}
        c=[Condition(requirementQuote='성공',method='TEXT_PRESENT',expected='성공')]
        self.assertIsNone(self.verdict(tc,c,self.evidence())['result'])
    def test_bound_value_must_survive_reload_and_not_preexist(self):
        tc={'expectedResult':'alpha-123','inputs':'alpha-123','scenario':'데이터 저장 후 재조회'}
        c=[Condition(requirementQuote='alpha-123',method='VALUE_AFTER_RELOAD',expected='alpha-123')]
        evidence=self.evidence('성공');evidence[1]['records'].append({'action':'fill','value':'alpha-123','status':'COMPLETED'})
        evidence.append({'id':'reload','kind':'PERSISTENCE_RECHECK','text':'alpha-123'})
        self.assertEqual('P',self.verdict(tc,c,evidence)['result'])
        evidence[-1]['text']='데이터 없음';self.assertIsNone(self.verdict(tc,c,evidence)['result'])
        evidence[-1]['text']='alpha-123';evidence[0]['text']='alpha-123';self.assertIsNone(self.verdict(tc,c,evidence)['result'])
    def test_download_event_does_not_prove_contents(self):
        tc={'expectedResult':'alpha-123'};c=[Condition(requirementQuote='alpha-123',method='DOWNLOAD_TEXT',expected='alpha-123')]
        evidence=self.evidence();evidence[1]['downloads']=[{'status':'COMPLETED','filename':'data.csv'}]
        self.assertIsNone(self.verdict(tc,c,evidence)['result'])
        evidence[1]['downloads'][0]['text']='id\nalpha-123';self.assertEqual('P',self.verdict(tc,c,evidence)['result'])
if __name__=='__main__':unittest.main()
