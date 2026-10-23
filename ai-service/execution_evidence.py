"""Program gates for a proposed verdict. A generated assertion is not execution evidence."""
import math
import re
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field
from execution_profiles import verified_read_check

class Condition(BaseModel):
    model_config = ConfigDict(extra='forbid')
    requirementQuote: str = Field(min_length=1, max_length=1000)
    method: Literal['TEXT_PRESENT', 'TEXT_ABSENT', 'SEMANTIC', 'VALUE_AFTER_RELOAD', 'DOWNLOAD_TEXT']
    expected: str = Field(min_length=1, max_length=1000)

def semantic_requirement(text):
    return bool(re.search(r'동작|기능|제공|표시|명확|목적|제약|여부|정보|취소되|업로드 되|정상적으로|한다|된다|됨$|함$|그리고| 및 ',text))

def assess(tc, conditions, evidence):
    expected = tc.get('expectedResult') or ''
    coverage = [not ch.isalnum() for ch in expected]
    for connector in re.finditer(r'\s+(?:그리고|및|또한)\s+',expected):
        for position in range(connector.start(),connector.end()):coverage[position]=True
    observations = [e for e in evidence if e.get('kind') == 'BROWSER_OBSERVATION']
    actions = [r for e in evidence if e.get('kind') == 'BROWSER_ACTIONS' for r in e.get('records', [])]
    final = observations[-1] if observations else {}
    initial = observations[0] if observations else {}
    rechecks = [e for e in evidence if e.get('kind') == 'PERSISTENCE_RECHECK']
    downloads = [d for e in evidence if e.get('kind') == 'BROWSER_ACTIONS' for d in e.get('downloads', [])]
    results = []
    for i, condition in enumerate(conditions):
        c = condition.model_dump() if isinstance(condition, Condition) else condition
        quote, value, method = c['requirementQuote'], c['expected'], c['method']
        start = expected.find(quote)
        grounded = start >= 0 and not (method in ('TEXT_PRESENT','TEXT_ABSENT') and (semantic_requirement(quote) or value != quote))
        if grounded:
            for j in range(start, start + len(quote)): coverage[j] = True
        # Concrete values must come from TC expected text or supplied input, never a model invention.
        value_grounded = value in quote or value in (tc.get('inputs') or '')
        outcome = 'UNKNOWN'; ids = []
        if grounded and (value_grounded or method == 'SEMANTIC'):
            if method in ('TEXT_PRESENT', 'TEXT_ABSENT') and final:
                matched = value in (final.get('mainText', '') if verified_read_check(tc,evidence) else final.get('text', ''))
                outcome = 'PASS' if matched == (method == 'TEXT_PRESENT') else 'FAIL'
                ids = [final['id']]
            elif method == 'VALUE_AFTER_RELOAD' and rechecks:
                # A toast after reload or an already-existing row is not proof of a new write.
                meaningful = not re.search(r'완료|성공|success|saved', value, re.I)
                bound = any(r.get('value') == value for r in actions if r.get('action') in ('fill', 'select'))
                if meaningful and bound and value not in initial.get('text', ''):
                    outcome = 'PASS' if value in rechecks[-1].get('text', '') else 'FAIL'
                    ids = [rechecks[-1]['id']]
            elif method == 'DOWNLOAD_TEXT':
                inspected = [d for d in downloads if d.get('status') == 'COMPLETED' and d.get('text') is not None and not d.get('truncated')]
                if inspected:
                    outcome = 'PASS' if any(value in d['text'] for d in inspected) else 'FAIL'
                    ids = [e['id'] for e in evidence if e.get('downloads')]
        results.append({'conditionId': f'C{i+1}', **c, 'grounded': grounded and (value_grounded or method == 'SEMANTIC'), 'outcome': outcome, 'evidenceIds': ids})
    return {'conditions': results, 'completeRequirementCoverage': bool(expected.strip()) and bool(results) and all(coverage),
            'performed': verified_read_check(tc,evidence) or any(r.get('status') == 'COMPLETED' and r.get('action') != 'wait' and not r.get('isNavigation',False) for r in actions) or bool(downloads),
            'actionError': any(r.get('status') == 'ERROR' for r in actions),
            'requiresPersistence': bool(re.search(r'재조회|반영|실제.*저장|저장된|삭제된|저장.*데이터|데이터.*저장', tc.get('scenario') or '')) or (tc.get('ruleId') == 'FEATURE-01' and bool(re.search(r'저장|등록|삭제|수정|생성|편집|적용|업로드', tc.get('scopeDescription') or ''))),
            'hasPersistenceEvidence': any(c['method'] == 'VALUE_AFTER_RELOAD' and c['outcome'] == 'PASS' for c in results)}

def gate(tc, proposed, assessment, answers, index, threshold):
    scope = answers.get(f'scope_{index}', {})
    coverage = answers.get(f'coverage_{index}', {})
    reasons = []
    if proposed.get('result') == 'P':
        if not assessment['performed']: reasons.append('TC 동작 수행 증거 없음')
        if assessment['actionError']: reasons.append('미해결 조작 오류')
        if not assessment['completeRequirementCoverage']: reasons.append('기대 조건 전체에 대한 근거 미확보')
        if scope.get('choice') != 'YES' or scope.get('confidence', 0) < threshold: reasons.append('대상 화면·항목 연결 미확정')
        if coverage.get('choice') != 'COMPLETE' or coverage.get('confidence', 0) < threshold: reasons.append('TC 전체 절차·조건 수행 미확정')
        for j, condition in enumerate(assessment['conditions']):
            outcome = condition['outcome']
            if condition['method'] == 'SEMANTIC':
                semantic = answers.get(f'condition_{index}_{j}', {})
                outcome = 'PASS' if semantic.get('choice') == 'PASS' and semantic.get('confidence', 0) >= threshold else 'UNKNOWN'
                condition['outcome']=outcome;condition['evaluatedBy']='JEV';condition['confidence']=semantic.get('confidence',0)
                condition['evidenceIds']=proposed.get('evidenceIds',[])
            if not condition['grounded'] or outcome != 'PASS': reasons.append(f"{condition['conditionId']} 확인 미완료 또는 반대 증거")
        if assessment['requiresPersistence'] and not assessment['hasPersistenceEvidence']: reasons.append('실제 저장·변경 반영 재확인 필요')
    if proposed.get('result') == 'F' and (not assessment['performed'] or scope.get('choice') != 'YES' or scope.get('confidence', 0) < threshold):
        reasons.append('제품 결함으로 구분할 대상·수행 근거 부족')
    if reasons:
        proposed.update(result=None, judgmentStatus='NEEDS_REVIEW', defectCandidate=False, reason=' / '.join(dict.fromkeys(reasons)))
    proposed['conditionAssessments'] = assessment['conditions']
    proposed['requirementCoverageComplete'] = assessment['completeRequirementCoverage']
    return proposed
