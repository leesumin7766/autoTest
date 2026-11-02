"""Local synthetic smoke test, never submits vendor documents to an external API."""
import json
import time
from pathlib import Path
from datetime import date
from app import qwen
from review import review_blocks

started = time.monotonic()
block = {'page': 1, 'blockId': 'synthetic:1', 'text': '운영체제: Windows 10 Pro\n설정이 저장됬습니다.'}
result = review_blocks([block], qwen, date(2026, 11, 2))
report = {'model': 'qwen3:4b', 'status': result['status'], 'failedChunks': result['failedChunks'],
          'typoCandidates': sum(c['type'] == 'TYPO' for c in result['candidates']),
          'lifecycleCandidates': sum(c['type'] == 'EOL_SOFTWARE' for c in result['candidates']),
          'allNeedReview': all(c['status'] == 'NEEDS_REVIEW' for c in result['candidates']),
          'elapsedSeconds': round(time.monotonic() - started, 2)}
assert result['failedChunks'] == 0, 'Local Qwen did not complete the synthetic check'
assert report['lifecycleCandidates'] == 1
Path('/checks/qwen-runtime.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report))
