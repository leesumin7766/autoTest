"""Real local page processing -> pause -> resume -> pause. Leaves only a paused QA job."""
import json
import time
from pathlib import Path
import httpx

folder = Path('/checks')
with httpx.Client(base_url='http://host.docker.internal:8080', timeout=45, trust_env=False, headers={'X-Member-Id': '98111'}) as client:
    uploaded = client.post('/api/submissions', data={'productId': '1', 'role': 'MANUAL', 'workspaceName': '매뉴얼 진행·중단·재개 검증'},
                           files={'file': ('manual-progress-sample.pdf', Path('/sample.pdf').read_bytes(), 'application/pdf')})
    uploaded.raise_for_status()
    submission = uploaded.json()
    assert submission['aiDelivery']['status'] == 'NOT_READY'
    base = f"/api/submissions/{submission['submissionId']}/manual-reviews"
    started = client.post(base); started.raise_for_status()
    job_id = started.json()['job']['jobId']
    (folder / 'progress-live-job.json').write_text(json.dumps({'submissionId': submission['submissionId'], 'jobId': job_id}), encoding='utf-8')
    print(json.dumps({'stage': 'RUNNING', 'jobId': job_id}), flush=True)

    def get():
        response = client.get(base, params={'job': job_id}); response.raise_for_status()
        return response.json()['job']

    def wait_for(predicate, timeout=180):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            job = get()
            if predicate(job):
                return job
            assert job['status'] not in ('FAILED', 'INTERRUPTED'), job.get('errorCode')
            time.sleep(0.2)
        raise AssertionError('Progress condition timed out')

    def pause():
        begin = time.monotonic()
        response = client.post(base + f'/{job_id}/pause'); response.raise_for_status()
        stopped = wait_for(lambda job: job['status'] == 'PAUSED', 15)
        return stopped, round(time.monotonic() - begin, 3)

    try:
        running = wait_for(lambda job: job['progress']['totalPages'] > 0 and job['progress']['completedPages'] >= 1 and job['progress']['stage'] == 'REVIEWING')
        total = running['progress']['totalPages']
        first, pause_seconds = pause()
        saved = {item['page']: item['updatedAt'] for item in first['checkpoints'] if item['complete']}
        assert saved
        frozen = first['progress']['elapsedMs']; time.sleep(1)
        assert get()['progress']['elapsedMs'] == frozen
        response = client.post(base + f'/{job_id}/resume'); response.raise_for_status()
        resumed = response.json()['job']
        assert resumed['attempt'] == first['attempt'] + 1
        assert resumed['progress']['elapsedMs'] >= frozen
        wait_for(lambda job: job['progress']['stage'] == 'REVIEWING')
        final, second_pause_seconds = pause()
        final_saved = {item['page']: item['updatedAt'] for item in final['checkpoints'] if item['complete']}
        assert all(final_saved.get(page) == stamp for page, stamp in saved.items()), 'Completed page was reprocessed'
        assert final['progress']['totalPages'] == total
        denied = client.post(base + f'/{job_id}/resume', headers={'X-Member-Id': '98112'})
        assert denied.status_code == 403
        candidates = final.get('output', {}).get('candidates', [])
        assert len({item['id'] for item in candidates}) == len(candidates)
        report = {'submissionId': submission['submissionId'], 'jobId': job_id, 'totalPages': total,
                  'firstPausedPage': first['progress']['currentPage'], 'firstCompletedPages': len(saved),
                  'finalPausedPage': final['progress']['currentPage'], 'finalCompletedPages': len(final_saved),
                  'completedCheckpointsUnchanged': True, 'elapsedFrozenWhilePaused': True,
                  'attempt': final['attempt'], 'status': final['status'], 'pauseSeconds': pause_seconds,
                  'secondPauseSeconds': second_pause_seconds, 'noDuplicateCandidates': True, 'foreignMemberDenied': True}
        (folder / 'progress-live.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        print(json.dumps(report), flush=True)
    finally:
        if get()['status'] in ('RUNNING', 'PAUSING'):
            pause()
