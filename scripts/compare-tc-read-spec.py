"""Read-only comparison of generated workbooks; no Excel files are rewritten."""
import json
import sys
from pathlib import Path
import warnings
import openpyxl

warnings.filterwarnings('ignore', message='Workbook contains no default style')
before, after, destination = map(Path, sys.argv[1:])
products = []
for old_file in sorted(before.glob('product-*.xlsx')):
    new_file = after / old_file.name
    with old_file.open('rb') as old_stream, new_file.open('rb') as new_stream:
        old = openpyxl.load_workbook(old_stream, read_only=True, data_only=False)
        new = openpyxl.load_workbook(new_stream, read_only=True, data_only=False)
        old_rows = list(old['TestCase'].values)
        new_rows = list(new['TestCase'].values)
        equal = old_rows == new_rows
        old.close(); new.close()
    output = json.loads(new_file.with_suffix('.json').read_text(encoding='utf-8'))
    recipes = [tc for tc in output['testCases'] if tc['executionPlan']['specificationVersion'] == 'execution-spec-v2-read']
    products.append({'product': old_file.stem, 'testCases': len(new_rows) - 1, 'testCaseCellsIdentical': equal,
        'readRecipes': len(recipes), 'readyRecipes': sum(tc['executionPlan']['specification']['status'] == 'READY' for tc in recipes),
        'specificMissingBindings': sum(len(tc['executionPlan']['specification']['missingBindings']) for tc in recipes)})
report = {'passed': len(products) == 4 and all(p['testCaseCellsIdentical'] for p in products), 'products': products}
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
if not report['passed']:
    raise SystemExit(1)
