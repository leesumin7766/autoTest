"""Grounded review candidates only. No model can confirm or modify a document."""
import hashlib
import json
import re
from difflib import SequenceMatcher
from datetime import date
from pathlib import Path


class ReviewFailure(ValueError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def load_catalog():
    return json.loads(Path(__file__).with_name('lifecycle.json').read_text(encoding='utf-8'))


def candidate(block, kind, quote, start, end):
    identity = f"{block['page']}:{block['blockId']}:{kind}:{start}:{end}"
    return {'id': hashlib.sha256(identity.encode()).hexdigest()[:20], 'type': kind,
            'status': 'NEEDS_REVIEW', 'page': block['page'], 'blockId': block['blockId'],
            'bbox': block.get('bbox'), 'quote': quote, 'characterStart': start, 'characterEnd': end}


def lifecycle_candidates(block, as_of):
    text, out = block['text'], []
    for product in load_catalog()['products']:
        if as_of <= date.fromisoformat(product['supportEnd']):
            continue
        for match in re.finditer(product['pattern'], text):
            # Suppress direct negative claims, not an arbitrary negative elsewhere on the page.
            following = text[match.end():match.end() + 55]
            if re.match(r'\s*(?:은|는|을|를)?\s*(?:지원\s*(?:하지\s*않|안\s*함|불가|종료)|미지원|not\s+supported|unsupported)', following, re.I):
                continue
            context = text[max(0, match.start() - 100):min(len(text), match.end() + 100)]
            special = bool(re.search(r'LTSC|LTSB|IoT|ESU|Embedded', context, re.I))
            unspecified = product['id'] == 'windows-10' and not re.search(r'\b(?:Home|Pro)\b', context, re.I)
            item = candidate(block, 'EOL_SOFTWARE', context, match.start(), match.end())
            item.update({'title': f"{product['name']} 지원 표기 확인", 'product': product['name'],
                         'reasonCode': 'CHECK_EDITION' if special or unspecified else 'SUPPORT_ENDED',
                         'reason': '에디션·버전·지원 계약과 해당 문맥을 확인하세요. 문서의 언급만으로 결함을 확정하지 않습니다.' if special or unspecified else '일반 지원 종료 기준을 지난 소프트웨어의 언급입니다. 실제 지원 대상 표기인지 확인하세요.',
                         'reference': {key: product[key] for key in ('supportEnd', 'scope', 'url', 'checkedAt')}})
            out.append(item)
    return out


def typo_candidates(block, supplied, offset=0):
    if not isinstance(supplied, list) or len(supplied) > 50:
        raise ValueError('INVALID_LLM_OUTPUT')
    out = []
    for item in supplied:
        if not isinstance(item, dict):
            continue
        quote, wrong, correction = (item.get(k) for k in ('quote', 'wrong', 'correction'))
        if not all(isinstance(v, str) and v.strip() for v in (quote, wrong, correction)):
            continue
        if len(quote) > 500 or len(wrong) > 60 or len(correction) > 60 or wrong == correction:
            continue
        if re.sub(r'\s+', '', wrong) == re.sub(r'\s+', '', correction):
            continue  # Spacing/style editing is outside this release.
        if SequenceMatcher(None, wrong, correction).ratio() < 0.45 or re.search(r'https?://|[<>]', wrong):
            continue
        if quote not in block['text'] or wrong not in quote:
            continue
        start = block['text'].index(quote) + quote.index(wrong)
        found = candidate(block, 'TYPO', quote, start + offset, start + offset + len(wrong))
        found.update({'title': '오탈자 의심', 'original': wrong, 'suggestion': correction,
                      'reason': '문맥·전문 용어와 원본 페이지를 대조해 확인하세요. OCR 인식 오류일 수도 있습니다.'})
        out.append(found)
    return out


def chunks(text, limit=1200):
    for start in range(0, len(text), limit):
        yield start, min(start + limit, len(text))


def review_blocks(blocks, suggest, as_of):
    found, failed, reviewed, retried = [], 0, 0, 0
    failure_codes = {}

    def inspect(block, start, end):
        nonlocal failed, reviewed, retried
        part = dict(block, text=block['text'][start:end])
        try:
            found.extend(typo_candidates(part, suggest(part['text']), start))
            reviewed += 1
        except Exception as error:
            code = getattr(error, 'code', 'LLM_CHECK_FAILED')
            if code not in {'LLM_OUTPUT_TRUNCATED', 'LLM_OUTPUT_INVALID', 'LLM_CHECK_FAILED'}:
                code = 'LLM_CHECK_FAILED'
            if code in {'LLM_OUTPUT_TRUNCATED', 'LLM_OUTPUT_INVALID'} and end - start > 300:
                retried += 1
                middle = start + (end - start) // 2
                inspect(block, start, middle)
                inspect(block, middle, end)
            else:
                failed += 1
                failure_codes[code] = failure_codes.get(code, 0) + 1

    for block in blocks:
        found.extend(lifecycle_candidates(block, as_of))
        for start, end in chunks(block['text']):
            inspect(block, start, end)
    catalog = load_catalog()
    return {'status': 'PARTIAL' if failed else 'COMPLETED', 'asOf': as_of.isoformat(),
            'catalogVersion': catalog['version'], 'catalogProducts': [p['name'] for p in catalog['products']],
            'candidates': list({c['id']: c for c in found}.values()),
            'reviewedChunks': reviewed, 'failedChunks': failed, 'retriedChunks': retried,
            'failureCodes': failure_codes, 'blockCount': len(blocks),
            'warnings': ['Qwen 검사를 완료하지 못한 구간이 있습니다. 오탈자 없음으로 판단하지 마세요.'] if failed else []}
