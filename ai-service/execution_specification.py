"""Validate versioned read recipes before opening a browser. No inferred bindings."""
import hashlib
from dataclasses import dataclass
from urllib.parse import urlsplit

from execution_evidence import Condition, assess
from execution_profiles import profile, target_observed

VERSION = 'execution-spec-v2-read'
READ_ACTIONS = ['NAVIGATE_TARGET', 'PREPARE_READ_CONTEXT', 'WAIT_TARGET_READY', 'COMPARE_RESULTS']


@dataclass
class PreparedRead:
    target: dict
    hints: list
    conditions: list[Condition]
    timeout_seconds: int

    def reached(self, observation):
        def route(value):
            url = urlsplit(value)
            return url.scheme, url.netloc, url.path.rstrip('/'), url.query, url.fragment
        heading = self.target['heading'].strip().casefold()
        headings = [value.strip().casefold() for value in observation.get('headings', []) if isinstance(value, str)]
        return (route(observation.get('url', '')) == route(self.target['href']) and heading in headings
                and target_observed(observation, [self.target['heading']]))

    def navigation_action(self, observation):
        matches = [e for e in observation.get('elements', []) if e.get('navigation')
                   and e.get('href') == self.target['href'] and e.get('name') == self.target['heading']]
        return {'action': 'click', 'elementId': matches[0]['id']} if len(matches) == 1 else None


def prepare_read(tc, feature_path, navigation_hints, target_url=None):
    """None with no issues means a legacy TC. New recipes never silently use the legacy planner."""
    try:
        return _prepare_read(tc, feature_path, navigation_hints, target_url)
    except (TypeError, AttributeError, KeyError, IndexError, ValueError):
        return None, [{'code': 'SPEC_INVALID', 'field': 'specification', 'message': '실행 명세 형식이 올바르지 않습니다. 명세를 다시 검토해야 합니다.'}]


def _prepare_read(tc, feature_path, navigation_hints, target_url):
    plan = tc.get('executionPlan') or {}
    version = plan.get('specificationVersion')
    if version in (None, '', 'execution-spec-v1'):
        return None, []
    issues = []

    def issue(code, field, message):
        issues.append({'code': code, 'field': field, 'message': message})

    if version != VERSION:
        issue('SPEC_VERSION_UNSUPPORTED', 'specificationVersion', '지원하지 않는 실행 명세 버전입니다.')
        return None, issues
    spec = plan.get('specification') or {}
    digest = hashlib.sha256(''.join(tc.get(k) or '' for k in ('scenario', 'inputs', 'expectedResult')).encode()).hexdigest()
    if spec.get('schemaVersion') != VERSION or spec.get('sourceTcDigest') != digest or plan.get('tcContentDigest') != digest:
        issue('SPEC_SOURCE_CHANGED', 'sourceTcDigest', 'TestCase 내용과 실행 명세의 버전이 일치하지 않습니다. 명세를 다시 검토해야 합니다.')
    if spec.get('profile') != 'READ_ONLY_VIEW' or profile(tc) != 'READ_ONLY_VIEW' or plan.get('selection', {}).get('exclusionCandidate'):
        issue('SPEC_PROFILE_UNSUPPORTED', 'profile', '이번 명세 실행은 조건 입력 없는 조회 TC만 지원합니다.')
    if spec.get('target', {}).get('featurePath') != feature_path:
        issue('TARGET_SCOPE_MISMATCH', 'target.featurePath', '실행 대상 기능 경로와 명세의 경로가 다릅니다.')
    start = spec.get('start') or {}
    if (target_url is not None and start.get('url') != target_url) or start.get('session') != 'FRESH_PER_TC' or start.get('authentication') != 'SAVED_SETUP_REFERENCE':
        issue('START_BINDING_REQUIRED', 'start', '시작 주소·독립 세션·저장 접속 설정과 실행 요청을 연결해야 합니다.')
    timeout = spec.get('timeoutSeconds')
    if type(timeout) is not int or not 1 <= timeout <= 60:
        issue('WAIT_POLICY_INVALID', 'timeoutSeconds', '조회 명세의 대기 한도는 1~60초여야 합니다.')
    if spec.get('status') != 'READY' or spec.get('missingBindings'):
        issue('SPEC_BINDINGS_UNRESOLVED', 'status', '실행 명세에 연결하지 못한 정보가 남아 있습니다.')

    bindings = spec.get('bindings') or {}
    target = bindings.get('target') or {}
    keys = spec.get('target', {}).get('menuKeys', [])
    hints = [h for h in navigation_hints if h.get('menuKey') == target.get('menuKey') and h.get('href') == target.get('href')
             and h.get('label') == target.get('heading')]
    if not target.get('menuKey') or target['menuKey'] not in keys or not hints or not target.get('href') or not target.get('heading'):
        issue('TARGET_BINDING_REQUIRED', 'bindings.target', '수집 근거의 메뉴·주소·화면 제목과 조회 대상을 연결해야 합니다.')
    context = bindings.get('readContext') or {}
    if context.get('mode') != 'NO_FILTER' or (context.get('oracle') or {}).get('kind') not in {'TC_INPUT', 'DOCUMENT_EVIDENCE'} or not grounded_oracle(tc, context.get('oracle')):
        issue('READ_CONTEXT_REQUIRED', 'bindings.readContext', '조건 입력 없는 조회라는 전제와 근거를 연결해야 합니다.')

    steps = spec.get('steps') or []
    if [s.get('action') for s in steps] != READ_ACTIONS or any(s.get('status') != 'BOUND' for s in steps):
        issue('STEP_BINDING_REQUIRED', 'steps', '조회 화면 이동·조회 전제·로딩 확인·결과 비교 단계를 연결해야 합니다.')
    conditions = []
    checks = spec.get('checks') or []
    if not 1 <= len(checks) <= 16:
        issue('CHECK_COUNT_UNSUPPORTED', 'checks', '이번 조회 실행 명세는 기대 조건 1~16개를 지원합니다.')
    for index, check in enumerate(checks):
        try:
            condition = Condition.model_validate({k: check.get(k) for k in ('requirementQuote', 'method', 'expected')})
            # Broader comparisons and inferred semantic oracles are a subsequent increment.
            if condition.method != 'TEXT_PRESENT' or check.get('status') != 'BOUND' or not grounded_oracle(tc, check.get('oracle')):
                raise ValueError('unbound')
            conditions.append(condition)
        except (ValueError, TypeError):
            issue('CHECK_BINDING_REQUIRED', f'checks[{index}]', f'C{index + 1}의 명시적 기대 문구·비교 방법·원문 근거를 연결해야 합니다.')
    assessment = assess(tc, conditions, [])
    if not assessment['completeRequirementCoverage'] or any(not c['grounded'] for c in assessment['conditions']):
        issue('CHECK_COVERAGE_REQUIRED', 'checks', 'TestCase의 모든 기대 조건을 확인할 근거가 필요합니다. 일부 조건만으로 실행 준비를 완료할 수 없습니다.')
    return (None, issues) if issues else (PreparedRead(target, hints, conditions, timeout), [])


def grounded_oracle(tc, oracle):
    if not isinstance(oracle, dict) or not isinstance(oracle.get('quote'), str) or not oracle['quote'].strip():
        return False
    kind, quote = oracle.get('kind'), oracle['quote']
    if kind == 'TC_INPUT':
        return quote in (tc.get('inputs') or '')
    if kind == 'TC_EXPECTED':
        return quote in (tc.get('expectedResult') or '')
    if kind == 'DOCUMENT_EVIDENCE':
        index = oracle.get('index')
        evidence = tc.get('evidence') or []
        return type(index) is int and 0 <= index < len(evidence) and quote in (evidence[index].get('quote') or '')
    return False
