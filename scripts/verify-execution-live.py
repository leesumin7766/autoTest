"""Opt-in live Qwen + Playwright + Jev smoke against a disposable synthetic site.

Run inside the AI container. No product account or production DB is used.
This isolates actual model/browser/judge wiring; durable run transitions are
covered separately by MenuTcIntegrationTest. Only this test bypasses run fencing.
"""
import asyncio
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, '/app')
from test_execution import Execute, execute_feature, judge_with_jev


class Site(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write('''<!doctype html><html lang="ko"><meta charset="utf-8">
<title>연계 검증 전용 화면</title><h1>목록 조회</h1>
<button onclick="document.getElementById('status').textContent='조회 완료'">조회</button>
<p id="status" role="status">조회 전</p></html>'''.encode())


async def main():
    site = ThreadingHTTPServer(('0.0.0.0', 0), Site)
    threading.Thread(target=site.serve_forever, daemon=True).start()
    try:
        request = Execute(runId='isolated-live-smoke', attempt='isolated-live-smoke',
                          targetUrl=f'http://ai-service:{site.server_port}/',
                          featureId='smoke-query', featurePath=['목록 조회'],
                          allowMutation=False, cases=[{
                              'id': 'smoke-query', 'tcId': 'SMOKE-001',
                              'scenario': '조회 버튼을 클릭한다.', 'inputs': '',
                              'expectedResult': '조회 완료',
                          }])
        judge_answers = {}
        def recorded_judge(cases, evidence, path):
            answer = judge_with_jev(cases, evidence, path)
            judge_answers.update(answer)
            return answer
        result = await execute_feature(request, judge=recorded_judge, check_active=False)
        item = result['items'][0]
        summary = {
            'site': 'disposable synthetic fixture',
            'planner': 'real local Qwen', 'browser': 'real crawler API',
            'judge': 'real Jev', 'result': item.get('result'),
            'executionStatus': item.get('executionStatus'),
            'confidence': item.get('confidence'), 'proposedResult': item.get('proposedResult'),
            'conditions': item.get('conditionAssessments'),
            'judgeAnswers': judge_answers,
            'reason': item.get('reason'), 'evidenceCount': len(result.get('evidence', [])),
            'planCount': len(result.get('plans', [])),
            'actions': [a for plan in result.get('plans', []) for a in plan.get('actions', [])],
        }
        print(json.dumps(summary, ensure_ascii=False))
        with open('/tmp/execution-live.json', 'w', encoding='utf-8') as output:
            json.dump(summary, output, ensure_ascii=False, indent=2)
        if item.get('executionStatus') != 'COMPLETED' or not judge_answers:
            raise RuntimeError('Live execution pipeline failed')
        # Verdict quality and wiring are separate acceptance criteria.
        if '--require-pass' in sys.argv and item.get('result') != 'P':
            raise RuntimeError('Live smoke did not establish P; inspect the recorded actual outcome')
    finally:
        site.shutdown()


if __name__ == '__main__':
    asyncio.run(main())
