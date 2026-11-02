"""Local sample excerpt -> upload -> review -> persisted history. No external model API."""
import json
import os
import re
import time
from pathlib import Path
import fitz
import httpx

folder = Path('/checks')
source = fitz.open('/sample.pdf')
index = next(i for i in range(len(source)) if re.search(r'Windows\s*(?:10|8\.1|7)\b', source[i].get_text(), re.I))
target = folder / 'manual-review-excerpt.pdf'
existing = os.getenv('REVIEW_TEST_SUBMISSION')
if not existing:
    excerpt = fitz.open()
    excerpt.insert_pdf(source, from_page=index, to_page=index)
    excerpt.save(target)
    excerpt[0].get_pixmap(matrix=fitz.Matrix(1, 1), alpha=False).save(str(folder / 'manual-review-excerpt.png'))
    assert len(fitz.open(target)) == 1
started = time.monotonic()
with httpx.Client(base_url='http://host.docker.internal:8080', timeout=90, trust_env=False, headers={'X-Member-Id': '98011'}) as client:
    if existing:
        submission_id = existing
    else:
        uploaded = client.post('/api/submissions', data={'productId': '1', 'role': 'MANUAL', 'workspaceName': '매뉴얼 검토 실연결 검증'}, files={'file': ('manual-review-excerpt.pdf', target.read_bytes(), 'application/pdf')})
        uploaded.raise_for_status()
        submission = uploaded.json()
        assert submission['documents'][0]['status'] == 'PARSED'
        assert submission['aiDelivery']['status'] == 'NOT_READY', 'No Jev/external document intake should run'
        submission_id = submission['submissionId']
    response = client.post(f'/api/submissions/{submission_id}/manual-reviews')
    response.raise_for_status()
    job_id = response.json()['job']['jobId']
    print(json.dumps({'stage': 'RUNNING', 'submissionId': submission_id, 'jobId': job_id}), flush=True)
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        response = client.get(f'/api/submissions/{submission_id}/manual-reviews', params={'job': job_id})
        response.raise_for_status()
        job = response.json()['job']
        if job['status'] != 'RUNNING':
            break
        time.sleep(3)
    assert job['status'] in ('COMPLETED', 'PARTIAL'), job['status']
    output = job['output']
    assert output['pageCount'] == 1
    assert output['nativePageCount'] == 1
    assert all(item['status'] == 'NEEDS_REVIEW' for item in output['candidates'])
    assert any(item['type'] == 'EOL_SOFTWARE' for item in output['candidates'])
    denied = client.get(f'/api/submissions/{submission_id}/manual-reviews', headers={'X-Member-Id': '98012'})
    assert denied.status_code == 403
    report = {'submissionId': submission_id, 'jobId': job_id, 'status': job['status'], 'sourceOriginalPage': index + 1,
              'testedPages': 1, 'nativePageCount': output['nativePageCount'], 'ocrPageCount': output['ocrPageCount'],
              'candidateCount': len(output['candidates']), 'failedChunks': output['failedChunks'],
              'retriedChunks': output.get('retriedChunks', 0), 'failureCodes': output.get('failureCodes', {}),
              'allNeedReview': True, 'foreignMemberDenied': True, 'elapsedSeconds': round(time.monotonic() - started, 2)}
    (folder / 'live-api.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report), flush=True)
    assert output['failedChunks'] == 0, 'Some LLM chunks did not complete; details are recorded without document content'
