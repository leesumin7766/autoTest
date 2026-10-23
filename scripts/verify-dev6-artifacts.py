"""Read-only independent verification of the production exporter outputs."""
import collections
import hashlib
import json
from pathlib import Path
import openpyxl

root=Path(__file__).resolve().parents[1]/'outputs/dev6-tc-samples'
report=json.loads((root/'generation-report.json').read_text(encoding='utf-8'))
expected_counts=[1283,842,1670,671]
seen=set(); results=[]
for index,item in enumerate(report['products']):
    data=json.loads((root/item['json']).read_text(encoding='utf-8'))
    book=openpyxl.load_workbook(root/item['workbook'],read_only=False,data_only=False)
    rows=list(book['TestCase'].values); details=list(book['실행 정보'].values)
    assert len(rows[0])==16 and len(details[0])==20
    assert len(rows)-1==len(details)-1==data['caseCount']==expected_counts[index]
    grouped=collections.defaultdict(list)
    for row,detail,tc in zip(rows[1:],details[1:],data['testCases']):
        assert row[5]==detail[0]==tc['tcId']
        assert row[9]==tc['scenario'] and row[10]==tc['inputs'] and row[11]==tc['expectedResult']
        assert row[12] is None and tc['result'] is None
        assert detail[11]==tc['id'] and detail[15]=='PENDING_LLM'
        assert tc['id'] not in seen;seen.add(tc['id']);grouped[tc['featureId']].append(tc)
        assert 'AAA' not in str(tc['defectExample'])
    for fid,tcs in grouped.items():
        expected=({'scope:COMMON':39,'scope:PRODUCT_DOCUMENT':7,'scope:USER_MANUAL':22}.get(fid)
                  or {'로그인':27,'주요기능':9,'부가기능':9,'CRUD':16}[tcs[0]['featureType']])
        assert len(tcs)==expected
    source_ids={f['featureId'] for f in data['features']}
    assert source_ids=={fid for fid in grouped if not fid.startswith('scope:')}
    assert all(c.data_type!='f' for sh in book for row in sh for c in row)
    for key in ['workbook','json']:
        assert hashlib.sha256((root/item[key]).read_bytes()).hexdigest()==item[key+'Sha256']
    results.append({'sheet':item['sheet'],'featureCount':len(source_ids),'caseCount':len(rows)-1,'allTemplateCountsCorrect':True,
                    'allResultCellsBlank':True,'allFeaturesCovered':True,'excelMatchesJson':True,'noFormulaCells':True,
                    'selectionCandidates':sum(tc['executionPlan']['selection']['exclusionCandidate'] for tc in data['testCases'])})
    book.close()
summary={'passed':True,'products':results,'totalUniqueCases':len(seen),'generationModel':'NONE (versioned template program)',
         'selectionModel':'qwen3:8b at execution time; one real sample smoke test provided separately'}
(root/'artifact-verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
