"""Explicitly authorized, read-only NeoFlow smoke test. Credentials arrive on stdin only.

Input: {targetUrl, username, password, sourcePath, outputPath}.
Source JSON contains documents and cases extracted from user-provided originals.
Never stores credentials, page contents or product records in the compact report.
"""
import asyncio
import json
import os
import sys
import time
from pathlib import Path
from test_execution import Execute, execute_feature


async def main():
    config = json.load(sys.stdin)
    source = json.loads(Path(config['sourcePath']).read_text(encoding='utf-8'))
    os.environ['CRAWLER_SERVICE_URL'] = 'http://crawler-service:8017'
    results = []
    for tc in source['cases']:
        started = time.monotonic()
        # Match the worker's feature-bound excerpt policy rather than sending every
        # unrelated document section into the local model's finite context window.
        term = 'DAG' if 'DAG' in tc['featurePath'][-1] else 'Job 목록'
        documents = []
        for document in source['documents']:
            text = document.get('text', '')
            position = text.find(term)
            if position >= 0:
                start, end = max(0, position-150), min(len(text), position+1800)
                documents.append({**document, 'text': text[start:end], 'start': start, 'end': end, 'partial': True})
        request = Execute(
            runId='neoflow-readonly-verification', attempt='isolated',
            targetUrl=config['targetUrl'], featureId=tc['featureId'], featurePath=tc['featurePath'],
            cases=[tc], documents=documents, allowMutation=False,
            setup={'loginMethod': 'FORM_AUTO', 'automation': {'username': config['username'],
                   'ignoreHTTPSErrors': config.get('ignoreHTTPSErrors') is True}},
            password=config['password'])
        result = await execute_feature(request, check_active=False)
        item = result['items'][0]
        entry = {key: item.get(key) for key in ('result', 'proposedResult', 'executionStatus', 'judgmentStatus', 'reason', 'confidence', 'conditionAssessments')}
        entry.update(tcId=tc['tcId'], featurePath=tc['featurePath'], elapsedSeconds=round(time.monotonic()-started, 2),
                     plannerCalls=len(result.get('plans', [])), evidenceCount=len(result.get('evidence', [])))
        results.append(entry)
        print(json.dumps(entry, ensure_ascii=False), flush=True)
    report = {'scope': 'real NeoFlow read-only; original TC and supplied Server documents',
              'planner': 'local qwen3:4b', 'judge': 'Jev', 'allowMutation': False, 'results': results}
    Path(config['outputPath']).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    asyncio.run(main())
