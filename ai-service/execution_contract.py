"""Deterministic execution requirements; descriptions are never literal UI strings."""
import re
import difflib
from execution_evidence import Condition, semantic_requirement
from execution_profiles import verified_read_check
from login_execution import login_submitted


def requirements(tc):
    text = (tc.get('expectedResult') or '').strip()
    # Keep the original substrings so evidence can always be traced to the TC.
    return [part.strip() for part in re.split(r'\n+|[;；]|\s+(?:그리고|및|또한)\s+|(?<=[.!?。])\s+', text) if part.strip()][:16]


def descriptive(text):
    return semantic_requirement(text)


def compiled_conditions(tc, proposed):
    result = []
    for requirement in requirements(tc):
        matching = [c for c in proposed if c.requirementQuote == requirement]
        # Persistence and download checks are still bound to supplied values by assess().
        concrete = [c for c in matching if c.method in ('VALUE_AFTER_RELOAD', 'DOWNLOAD_TEXT')]
        if concrete:
            result.extend(concrete)
            if descriptive(requirement):result.append(Condition(requirementQuote=requirement,method='SEMANTIC',expected=requirement))
        elif descriptive(requirement) or len(requirements(tc)) > 1:
            result.append(Condition(requirementQuote=requirement, method='SEMANTIC', expected=requirement))
        elif matching:
            # A positive literal expectation cannot be satisfied by its absence.
            result.extend(Condition(requirementQuote=requirement,method='TEXT_PRESENT',expected=requirement)
                          if c.method in ('TEXT_PRESENT','TEXT_ABSENT') else c for c in matching)
        else:
            result.append(Condition(requirementQuote=requirement, method='SEMANTIC', expected=requirement))
    return result


def prerequisites(tc):
    scenario = tc.get('scenario') or ''
    scope = tc.get('scopeDescription') or ''
    upload_test = (tc.get('ruleId') == 'FEATURE-01' and re.search(r'업로드|upload', scope, re.I))
    file_limits = re.search(r'확장자|스크립트 파일|최대 개수|파일의.*크기', scenario)
    if upload_test or (file_limits and re.search(r'업로드|upload|import|가져오|파일',scope,re.I)):
        return ['시험용 파일과 기대 확장자·크기·개수: 현재 파일 공급 기능 미지원']
    return []


def navigation_record(record, phase):
    if record.get('isNavigation') is not None:
        return record['isNavigation']
    return phase == 'NAVIGATE'


def performed_test(evidence, tc=None):
    if tc and tc.get('ruleId')=='LOGIN-04':return login_submitted(evidence)
    return bool(tc and verified_read_check(tc,evidence)) or any(item.get('downloads') for item in evidence) or any(record.get('status') == 'COMPLETED' and record.get('action') != 'wait'
               and not record.get('isNavigation', False)
               for item in evidence if item.get('kind') == 'BROWSER_ACTIONS'
               for record in item.get('records', []))


def planner_history(evidence):
    """Avoid resending full before/after pages. Full evidence still goes to the judge/storage."""
    history=[]
    for item in evidence:
        if item.get('kind')!='BROWSER_ACTIONS':continue
        records=[]
        for record in item.get('records',[]):
            compact={k:v for k,v in record.items() if k not in ('text','beforeText','screenshot')}
            changes='\n'.join(difflib.unified_diff((record.get('beforeText') or '').splitlines(),(record.get('text') or '').splitlines(),n=1,lineterm=''))
            compact['observedChanges']=changes[:12000]
            compact['changesTruncated']=len(changes)>12000
            records.append(compact)
        history.append({'id':item.get('id'),'records':records,'downloads':item.get('downloads',[]),'dialogs':item.get('dialogs',[])})
    return history
