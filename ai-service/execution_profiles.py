"""Code-owned execution profiles and loop keys. No model decision can grant a pass."""
import hashlib
import json
import re
from urllib.parse import urlsplit, unquote


def profile(tc):
    scope = tc.get('scopeDescription') or ''
    plan = tc.get('executionPlan') or {}
    if tc.get('ruleId') == 'CRUD-SEARCH-01' and plan.get('selection', {}).get('scopeOperation') == 'READ':
        return 'READ_ONLY_SEARCH'
    if tc.get('ruleId') == 'CRUD-VIEW-01' and plan.get('selection', {}).get('scopeOperation') == 'READ':
        return 'READ_ONLY_VIEW'
    specified_read = (plan.get('specificationVersion') == 'execution-spec-v2-read'
                      and plan.get('specification', {}).get('profile') == 'READ_ONLY_VIEW'
                      and tc.get('ruleId') in {'FEATURE-01', 'CRUD-01', 'CRUD-08'}
                      and plan.get('selection', {}).get('scopeOperation') == 'READ')
    selected_read = (tc.get('ruleId') in {'CRUD-01', 'CRUD-08'}
                     and plan.get('selection', {}).get('scopeOperation') == 'READ'
                     and bool(re.search(r'조회|열람|\bview\b|\blist\b', scope, re.I)))
    readonly = specified_read or selected_read or (tc.get('ruleId') == 'FEATURE-01' and bool(re.search(r'조회|열람|\bview\b|\blist\b', scope, re.I)))
    interactive = re.search(r'검색|필터|업로드|다운로드|인쇄|내보내|선택|search|filter|edit|create|delete|upload|download|print|export', scope, re.I) or re.search(r'(등록|생성|수정|삭제|저장|변경|실행)(?:을|를)?\s*(?:하|할|하기|기능)',scope)
    return 'READ_ONLY_VIEW' if readonly and not interactive else 'INTERACTIVE'


def target_terms(feature_path, hints=()):
    # Product name is not a screen. Prefer the actual deepest observed menu.
    paths = [h.get('path', []) for h in hints if h.get('path')]
    terms = [p[-1] for p in paths] or list(feature_path[-2:])
    result = []
    for term in terms:
        term = re.sub(r'\s*(조회|화면|기능)$', '', term).strip()
        if term and term not in result: result.append(term)
    return result


def target_observed(observation, terms):
    # Sidebar text, document.title and a plan's assertion are never sufficient.
    main = (observation.get('mainText') or '').strip()
    body = main or (observation.get('text') or '').strip()
    if not body or observation.get('screenReady') is False: return False
    norm = lambda value: re.sub(r'[^\w가-힣]', '', value).casefold()
    targets = {norm(term) for term in terms if term}
    headings = {norm(x) for x in observation.get('headings', []) if x}
    parts = urlsplit(observation.get('url') or '')
    route = unquote((parts.fragment if parts.fragment.startswith('/') else parts.path).rstrip('/').split('/')[-1])
    # Some products have no semantic <main>. In that case require a visible
    # non-sidebar heading; body text and a route alone can include menu labels.
    return bool(targets & (headings | {norm(route)})) if main else bool(targets & headings)


def captured_screen_observed(observation, hints):
    """A screen-only check may bind to the captured URL across UI languages."""
    if observation.get('screenReady') is False or not observation.get('headings'):
        return False
    body=(observation.get('mainText') or '').strip()
    if len(body)<30 or re.search(r'\b(404|500|502|503)\b|페이지를 찾을 수 없|page not found|internal server error',body[:400],re.I):
        return False
    observed=urlsplit(observation.get('url') or '')
    for hint in hints:
        expected=urlsplit(hint.get('href') or '')
        if expected.scheme in {'http','https'} and expected.netloc==observed.netloc and expected.path.rstrip('/')==observed.path.rstrip('/') and expected.query==observed.query and expected.fragment==observed.fragment:
            return True
        if not hint.get('href') and hint.get('label') and any(
                el.get('name')==hint['label'] and el.get('role')=='tab' and el.get('selected') is True
                for el in observation.get('elements', [])):
            return True
    return False


def verified_read_check(tc, evidence):
    if profile(tc) != 'READ_ONLY_VIEW': return False
    observations = {e.get('id'): e for e in evidence if e.get('kind') == 'BROWSER_OBSERVATION'}
    return any(e.get('kind') == 'READ_ONLY_CHECK' and e.get('caseId') == tc.get('id')
               and (captured_screen_observed(observations.get(e.get('observationId'), {}), [e.get('targetHint')])
                    if tc.get('ruleId') == 'CRUD-VIEW-01' and e.get('targetHint') else target_observed(observations.get(e.get('observationId'), {}), e.get('targetTerms', [])))
               for e in evidence)


def single_action_done(tc, evidence):
    """Only an explicitly single-step TC may complete without another model decision."""
    match=re.fullmatch(r'\s*([^\n.]{1,40}?)\s*버튼[을를]\s*클릭한다[.。]?\s*', tc.get('scenario') or '')
    if not match:return False
    label=match.group(1).strip(' "\'‘’“”')
    return any(record.get('action')=='click' and record.get('status')=='COMPLETED'
               and not record.get('isNavigation',False) and record.get('targetName','').strip()==label
               for item in evidence if item.get('kind')=='BROWSER_ACTIONS' for record in item.get('records',[]))


def literal_assertion(tc, assessment, evidence, feature_path):
    """Only fully specified literal outputs on a code-bound, completed test are decidable."""
    if not assessment.get('performed') or assessment.get('actionError') or assessment.get('requiresPersistence') or not assessment.get('completeRequirementCoverage'):
        return None
    if tc.get('ruleId') == 'CRUD-VIEW-01':
        observations=[e for e in evidence if e.get('kind')=='BROWSER_OBSERVATION']
        final=observations[-1] if observations else {}
        return 'P' if verified_read_check(tc,evidence) and len((final.get('mainText') or '').strip()) >= 30 else None
    checks=assessment.get('conditions',[])
    if not checks or any(not c.get('grounded') or c.get('method')!='TEXT_PRESENT' for c in checks):return None
    observations=[e for e in evidence if e.get('kind')=='BROWSER_OBSERVATION']
    final=observations[-1] if observations else {}
    bound=verified_read_check(tc,evidence) or (not (tc.get('inputs') or '').strip() and single_action_done(tc,evidence) and target_observed(final,target_terms(feature_path)))
    if not bound or not final.get('mainText'):return None
    return 'P' if all(c['expected'] in final['mainText'] for c in checks) else 'F'


def navigation_plan(observation, hints, attempted=()):
    elements = observation.get('elements', [])
    for hint in hints:
        path = hint.get('path') or [hint.get('label')]
        for label in reversed(path):
            if not label or label in attempted:continue
            matches = [el for el in elements if (el.get('navigation') or el.get('role')=='tab')
                       and el.get('name') == label
                       and not (el.get('expanded') is True or el.get('selected') is True)
                       and (label != path[-1] or not hint.get('href') or el.get('href') == hint['href'])]
            if len(matches) == 1:
                return {'action': 'click', 'elementId': matches[0]['id']}
    return None


def loop_key(actions, observation):
    elements = {str(el.get('id')): el for el in observation.get('elements', [])}
    resolved = []
    navigation = bool(actions)
    for action in actions:
        a = action.model_dump() if hasattr(action, 'model_dump') else action
        el = elements.get(str(a.get('elementId')), {})
        nav = a.get('action') == 'goto' or (a.get('action') == 'click' and el.get('navigation') is True)
        navigation &= nav
        resolved.append([a.get('action'), el.get('name'), el.get('context'), el.get('href') or a.get('url'), a.get('value')])
    # Navigation loops ignore live counters; interactive loops ignore only timestamps.
    state = '' if navigation else re.sub(r'\b\d{1,2}:\d{2}(?::\d{2})?(?:\.\d+)?\b', '<clock>', observation.get('mainText') or observation.get('text') or '')
    return hashlib.sha256(json.dumps([observation.get('url'), resolved, state], sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def public_trace(plan, observation, round_number, origin='QWEN'):
    elements = {str(e.get('id')): e for e in observation.get('elements', [])}
    return {'round': round_number, 'planner': origin, 'decision': plan.decision, 'phase': plan.phase,
            'explanation': plan.explanation[:1200],
            'actions': [{'action': a.action, 'elementId': a.elementId,
                         'targetName': elements.get(a.elementId, {}).get('name', '')[:160]}
                        for a in plan.actions]}
