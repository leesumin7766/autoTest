"""One page per cancellable process group. OCR child exits before local Qwen runs."""
import json
import subprocess
import sys
from datetime import date
from pathlib import Path
from app import qwen
from review import review_blocks


def phase(path, stage, page):
    target = Path(path)
    temporary = target.with_suffix('.tmp')
    temporary.write_text(json.dumps({'stage': stage, 'currentPage': page}), encoding='utf-8')
    temporary.replace(target)


def analyze_page(source, target, page, as_of, progress):
    phase(progress, 'EXTRACTING', page)
    raw = str(target) + '.ocr.json'
    subprocess.run([sys.executable, str(Path(__file__).with_name('ocr_worker.py')), str(source), raw, str(page)],
                   check=True, timeout=21600, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    extracted = json.loads(Path(raw).read_text(encoding='utf-8'))
    phase(progress, 'REVIEWING', page)
    result = review_blocks(extracted['blocks'], qwen, as_of)
    result.update({'page': page, 'pageCount': extracted['pageCount'], 'ocrModel': 'PaddleOCR-VL-1.6',
                   'nativePageCount': len(extracted['nativePages']), 'ocrPageCount': len(extracted['ocrPages']),
                   'extractedCharacterCount': sum(len(block['text']) for block in extracted['blocks']),
                   'emptyPages': extracted['emptyPages']})
    if extracted['imagePages']:
        result['status'] = 'PARTIAL'
        result['warnings'].append(f'이미지 포함 {page}페이지는 텍스트층만 검사했습니다. 이미지 안의 글자는 별도 확인이 필요합니다.')
    if extracted['emptyPages']:
        result['status'] = 'PARTIAL'
        result['warnings'].append(f'{page}페이지의 텍스트를 확인하지 못했습니다.')
    Path(target).write_text(json.dumps(result, ensure_ascii=False), encoding='utf-8')
    phase(progress, 'PAGE_DONE', page)


if __name__ == '__main__':
    analyze_page(sys.argv[1], sys.argv[2], int(sys.argv[3]), date.fromisoformat(sys.argv[4]), sys.argv[5])
