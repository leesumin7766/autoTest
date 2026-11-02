"""Read-only sample-page smoke test. Run in the CPU image with --network none."""
import json
import re
import time
from pathlib import Path
import fitz
import numpy as np
from ocr_worker import prepare
from review import lifecycle_candidates
from datetime import date

source = Path('/sample.pdf')
document = fitz.open(source)
index = next((i for i in range(len(document)) if re.search(r'Windows\s*(?:10|8\.1|7)\b', document[i].get_text(), re.I)), 0)
started = time.monotonic()
pipeline = prepare()
pix = document[index].get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False)
pixels = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
blocks = []
for value in pipeline.predict(pixels):
    value = value.json
    if isinstance(value, str):
        value = json.loads(value)
    for part in value.get('res', value).get('parsing_res_list', []):
        if part.get('block_content', '').strip():
            blocks.append({'page': index + 1, 'blockId': str(len(blocks)), 'text': part['block_content'], 'bbox': part.get('block_bbox')})
assert blocks, 'No OCR text extracted'
found = [item for block in blocks for item in lifecycle_candidates(block, date(2026, 11, 2))]
report = {'network': 'none', 'device': 'cpu', 'model': 'PaddleOCR-VL-1.6', 'sourcePage': index + 1,
          'sourcePageCount': len(document), 'testedPages': 1, 'blockCount': len(blocks),
          'characterCount': sum(len(b['text']) for b in blocks), 'lifecycleCandidateCount': len(found),
          'elapsedSeconds': round(time.monotonic() - started, 2)}
Path('/checks/ocr-runtime.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report))
