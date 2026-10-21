"""Independent read-only check of production Java generator outputs against the user's workbook.
Run with the bundled Python runtime (openpyxl required). No application server or LLM needed.
"""
import hashlib
import json
from collections import Counter
from pathlib import Path
import openpyxl

root = Path(__file__).resolve().parents[1]
source = root / 'sample/menu sample/menu sample.xlsx'
directory = root / 'output/menu-tc-samples'
report = json.loads((directory / 'generation-report.json').read_text(encoding='utf-8'))
assert hashlib.sha256(source.read_bytes()).hexdigest() == report['sourceSha256']
book = openpyxl.load_workbook(source, read_only=True, data_only=True)
results, all_ids = [], set()
for item in report['products']:
    rows = list(book[item['sheet']].iter_rows(values_only=True))
    headers = rows[0]
    rows = [tuple(row) + (None,) * max(0, len(headers) - len(row)) for row in rows]
    dc, fc = headers.index('메뉴 설명'), headers.index('기능구분')
    # Include classified rows even when their description is missing.
    expected = {i: row for i, row in enumerate(rows[1:], 2)
                if str(row[dc] or '').strip() not in ('.', '-', '·')
                and (str(row[dc] or '').strip() or str(row[fc] or '').strip())}
    types = Counter(str(row[fc]).strip() for row in expected.values())
    expected_cases = sum(27 if row[fc] == '로그인' else 9 for row in expected.values()) + 68
    output = json.loads((directory / item['json']).read_text(encoding='utf-8'))
    assert len(output['features']) == len(expected) == output['featureCount']
    assert {feature['sourceRow'] for feature in output['features']} == set(expected)
    assert len(output['testCases']) == expected_cases == output['caseCount']
    coverage = Counter(tc['featureId'] for tc in output['testCases'])
    for feature in output['features']:
        row = expected[feature['sourceRow']]
        assert coverage[feature['featureId']] == (27 if row[fc] == '로그인' else 9)
        assert feature['featureType'] == row[fc]
        assert feature['evidence'][0]['row'] == feature['sourceRow']
    for tc in output['testCases']:
        assert tc['id'] not in all_ids
        all_ids.add(tc['id'])
        assert tc['result'] is None
        assert tc['source']['sheet'] == item['sheet']
        assert tc['executionReadiness'] == 'NEEDS_CONTEXT'
    for key, field in [('json', 'jsonSha256'), ('workbook', 'workbookSha256')]:
        assert hashlib.sha256((directory / item[key]).read_bytes()).hexdigest() == item[field]
    generated = openpyxl.load_workbook(directory / item['workbook'], read_only=False, data_only=False)
    sheet = generated['TestCase']
    assert sheet.max_row - 1 == expected_cases
    assert sheet.max_column == 16
    assert all(sheet.cell(i, 13).value in (None, '') for i in range(2, sheet.max_row + 1))
    assert 'P,F,N/A' in str(next(iter(sheet.data_validations.dataValidation)).formula1)
    assert generated['실행 정보'].max_row - 1 == expected_cases
    assert not any(cell.data_type == 'f' for ws in generated for row in ws for cell in row)
    generated.close()
    results.append({'sheet': item['sheet'], 'features': len(expected), 'types': dict(types),
                    'testCases': expected_cases, 'missingDescriptionFeaturesPreserved': sum(not str(r[dc] or '').strip() for r in expected.values()),
                    'allFeaturesCovered': True, 'allResultsBlank': True, 'excelAndJsonConsistent': True})
book.close()
assert len(all_ids) == 4277
summary = {'sourceSha256': report['sourceSha256'], 'products': results, 'totalTestCases': len(all_ids), 'passed': True}
(directory / 'verification-report.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False, indent=2))
