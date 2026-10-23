"""Opt-in real Qwen selection check; never opens or modifies a browser target."""
import asyncio
import json
from pathlib import Path
from execution_selection import preflight


async def main():
    source=Path('/tmp/dev6-product-4.json')
    product=json.loads(source.read_text())
    candidate=next(tc for tc in product['testCases'] if tc['ruleId']=='CRUD-06' and tc['executionPlan']['selection']['scopeOperation']=='DELETE')
    result=await preflight(candidate)
    record={'tcId':candidate['tcId'],'featurePath':candidate['featurePath'],'originalTcPreserved':True,'result':result}
    Path('/generation-cache/dev6-selection-smoke.json').write_text(json.dumps(record,ensure_ascii=False,indent=2))
    print(json.dumps(record,ensure_ascii=False))
    assert result['items'][0]['executionStatus']=='EXCLUDED'
    assert result['items'][0]['result'] is None


asyncio.run(main())
