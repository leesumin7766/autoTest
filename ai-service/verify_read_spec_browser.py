"""Real Chromium execution with a synthetic site and stub judge; no product or model calls."""
import asyncio
import json
import os
from test_execution import Execute, execute_feature
from test_execution_specification import sample_case


async def main():
    origin = os.environ['READ_SPEC_FIXTURE_ORIGIN']
    results = []
    for mode, verdict, expected in [('good', 'P', 'P'), ('broken', 'F', 'F'), ('broken', 'P', None)]:
        dashboard, reports = f'{origin}/fixture/{mode}/dashboard', f'{origin}/fixture/{mode}/reports'
        tc = sample_case()
        spec = tc['executionPlan']['specification']
        spec['start']['url'] = dashboard
        spec['bindings']['target']['href'] = reports
        request = Execute(runId='synthetic-read', attempt='fixture', targetUrl=dashboard, featureId='reports',
            featurePath=['제품', 'Reports', '보고서 조회'], cases=[tc], initialEntry=True,
            navigationHints=[{'menuKey': 'reports-menu', 'href': reports, 'label': 'Reports'}])
        async def planner(_):
            raise AssertionError('Unexpected Qwen call')
        def judge(*_):
            return {'case_0': {'choice': verdict, 'confidence': .99}, 'scope_0': {'choice': 'YES', 'confidence': .99},
                    'coverage_0': {'choice': 'COMPLETE', 'confidence': .99}}
        result = await execute_feature(request, planner, judge, check_active=False)
        item = result['items'][0]
        results.append({'site': mode, 'stubJudgeVerdict': verdict, 'expected': expected, 'actual': item['result'],
            'executionStatus': item['executionStatus'], 'judgmentStatus': item['judgmentStatus'],
            'plannerOrigins': [p['planner'] for p in result['plans']],
            'initialEntryVerified': result.get('initialEntryVerified'),
            'observedUrls': list(dict.fromkeys(e['url'] for e in result['evidence'] if e.get('url'))),
            'conditionOutcomes': [c['outcome'] for c in item.get('conditionAssessments', [])]})
    report = {'passed': all(r['actual'] == r['expected'] and r['initialEntryVerified'] for r in results),
              'browser': 'real Chromium', 'site': 'synthetic fixture', 'judge': 'stub (Jev not invoked)', 'results': results}
    print(json.dumps(report, ensure_ascii=False))
    if not report['passed']:
        raise SystemExit(1)


asyncio.run(main())
