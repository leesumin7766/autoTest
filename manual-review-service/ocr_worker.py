"""One document per subprocess. Exit releases Paddle memory before Qwen is loaded."""
import json
import os
import sys
from pathlib import Path


def prepare():
    from paddleocr import PaddleOCRVL
    return PaddleOCRVL(pipeline_version='v1.6', device='cpu', cpu_threads=int(os.getenv('OCR_CPU_THREADS', '4')))


def extract(source, target, page_number=None):
    import fitz
    import numpy as np
    document = fitz.open(source)
    if document.needs_pass or document.page_count < 1 or document.page_count > 300:
        raise ValueError('PDF_LIMIT_OR_PASSWORD')
    pipeline = None
    blocks, empty_pages, native_pages, ocr_pages, image_pages = [], [], [], [], []
    if page_number is not None and not 1 <= page_number <= document.page_count:
        raise ValueError('PAGE_OUT_OF_RANGE')
    indices = range(document.page_count) if page_number is None else [page_number - 1]
    for index in indices:
        page = document.load_page(index)
        # Render one page at a time. Large or malformed pages fail explicitly.
        if page.rect.width * page.rect.height > 4_000_000:
            raise ValueError('PAGE_TOO_LARGE')
        native = page.get_text('text', sort=True)
        if sum(char.isalnum() for char in native) >= 20 and os.getenv('OCR_FORCE_ALL_PAGES') != '1':
            blocks.append({'page': index + 1, 'blockId': f'{index + 1}:text', 'text': native,
                           'bbox': list(page.rect), 'source': 'PDF_TEXT'})
            native_pages.append(index + 1)
            if page.get_images():
                image_pages.append(index + 1)
            if sum(len(b['text']) for b in blocks) > 2_000_000:
                raise ValueError('EXTRACTED_TEXT_TOO_LARGE')
            continue
        if pipeline is None:
            pipeline = prepare()
        ocr_pages.append(index + 1)
        pix = page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False)
        image = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
        count = len(blocks)
        for result in pipeline.predict(image):
            value = result.json
            if isinstance(value, str):
                value = json.loads(value)
            value = value.get('res', value)
            for part in value.get('parsing_res_list', []):
                text = part.get('block_content', '')
                if isinstance(text, str) and text.strip():
                    blocks.append({'page': index + 1, 'blockId': f'{index + 1}:{len(blocks) - count}',
                                   'text': text, 'bbox': part.get('block_bbox'), 'source': 'PaddleOCR-VL-1.6'})
        if len(blocks) == count:
            empty_pages.append(index + 1)
        if sum(len(b['text']) for b in blocks) > 2_000_000:
            raise ValueError('EXTRACTED_TEXT_TOO_LARGE')
    Path(target).write_text(json.dumps({'blocks': blocks, 'pageCount': document.page_count,
                                      'emptyPages': empty_pages, 'nativePages': native_pages,
                                      'ocrPages': ocr_pages, 'imagePages': image_pages}, ensure_ascii=False), encoding='utf-8')


if __name__ == '__main__':
    if sys.argv[1:] == ['--prepare']:
        prepare()
    else:
        extract(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else None)
