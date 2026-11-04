"""Check Java binding output against the real Python preparation gate (no browser/model)."""
import json
import sys
from execution_specification import prepare_read

with open(sys.argv[1], encoding='utf-8') as source:
    fixture = json.load(source)
tc = fixture['tc']
prepared, issues = prepare_read(tc, tc['featurePath'], fixture['hints'], tc['executionPlan']['targetUrl'])
assert prepared is not None and not issues, issues
unbound, issues = prepare_read(fixture['unbound'], tc['featurePath'], fixture['hints'], tc['executionPlan']['targetUrl'])
assert unbound is None and issues
wrong_screen, issues = prepare_read(tc, tc['featurePath'], [], tc['executionPlan']['targetUrl'])
assert wrong_screen is None and any(issue['code'] == 'TARGET_BINDING_REQUIRED' for issue in issues)
print('PASS: Java-saved bindings accepted by Python; unbound and missing screen evidence rejected')
