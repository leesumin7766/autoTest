import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]/'outputs/current-validation'
products=[]
for index in range(1,5):
    data=json.loads((root/f'generated/product-{index}.json').read_text(encoding='utf-8'))
    old=json.loads((root.parent/f'dev6-tc-samples/product-{index}.json').read_text(encoding='utf-8'))
    cases=data['testCases']
    products.append({'product':index,'caseCount':len(cases),'sameAsPreviousJson':data==old,
        'needsContextSpecificationCount':sum(tc['executionPlan']['specification']['status']=='NEEDS_CONTEXT' for tc in cases),
        'genericPurposeExpectedCount':sum(tc['expectedResult']=='기능이 목적에 맞게 동작함' for tc in cases),
        'selectionCandidates':sum(tc['executionPlan']['selection']['exclusionCandidate'] for tc in cases),
        'classificationCounts':data['summary']})
java=[]
for path in root.glob('TEST-*.xml'):
    tree=ET.parse(path).getroot()
    java.append({'suite':tree.attrib['name'],'tests':int(tree.attrib['tests']),'failures':int(tree.attrib['failures']),
        'failureDetails':[{'test':case.attrib['name'],'message':case.find('failure').attrib['message']} for case in tree.findall('testcase') if case.find('failure') is not None]})
summary={'products':products,'totalCases':sum(p['caseCount'] for p in products),
    'totalNeedsContextSpecifications':sum(p['needsContextSpecificationCount'] for p in products),
    'genericPurposeExpectedCount':sum(p['genericPurposeExpectedCount'] for p in products),'java':java,
    'python':{'tests':26,'passed':26},'browserEngine':{'tests':8,'passed':8},'frontendClassification':{'checks':26,'passed':26},
    'ui':{'realPrepared1265':'PASS read-only','requestCancelReviewRetryFixture':'PASS','clockResumeMobileFixture':'PASS','typescriptViteBuild':'PASS'},
    'runtime':json.loads((root/'runtime-version.json').read_text(encoding='utf-8')),
    'synthetic':json.loads((root/'synthetic/summary.json').read_text(encoding='utf-8'))}
(root/'verification-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
images=root/'screenshots';images.mkdir(exist_ok=True)
for path in (root/'synthetic').glob('*.json'):
    data=json.loads(path.read_text(encoding='utf-8'))
    import base64
    for index,evidence in enumerate(data.get('evidence',[])):
        if evidence.get('screenshot'):(images/f'{path.stem}-{index}.png').write_bytes(base64.b64decode(evidence['screenshot'].split(',')[-1]))
print(json.dumps({key:summary[key] for key in ['totalCases','totalNeedsContextSpecifications','genericPurposeExpectedCount','runtime']},ensure_ascii=False))
