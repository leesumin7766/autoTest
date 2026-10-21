"""Versioned, grounded functional-menu proposals. No training or product actions."""
import asyncio
import hashlib
import json
import os
import re
from collections import OrderedDict
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request as HttpRequest
from pydantic import BaseModel, ConfigDict, Field
from product_description.api import require_internal_token
from product_description.llm import provider_from_env
from product_description.ollama_provider import OllamaLlmProvider

ROOT = Path(__file__).parent / 'menu_templates' / 'functional-v1'
RULES = json.loads((ROOT / 'rules.json').read_text(encoding='utf-8'))
SCHEMA = json.loads((ROOT / 'output.schema.json').read_text(encoding='utf-8'))
EXAMPLES = json.loads((ROOT / 'examples.json').read_text(encoding='utf-8'))
OUTPUT_TOKENS = 2048
AUTO_INSTRUCTIONS = '\n'.join(RULES['instructions'])+'\n자동 생성 응답에서는 evidence 대신 evidenceSpanIds를 사용한다. sourceSpans에서 현재 source의 원문 구간 ID를 반드시 선택하고 인용문을 다시 작성하지 않는다.'
router = APIRouter(prefix='/api/v1/functional-menus', dependencies=[Depends(require_internal_token)])
PROGRESS = OrderedDict()
LOCK = asyncio.Lock()

class Proposal(BaseModel):
    model_config = ConfigDict(extra='forbid')
    menuPath: list[str] = Field(max_length=3)
    functionName: str = Field(min_length=1, max_length=160)
    menuDescription: str = Field(min_length=1, max_length=600)
    location: Literal['HEADER','FOOTER','SIDEBAR','CONTENT','UNKNOWN']
    matchedMenuKeys: list[str] = Field(max_length=8)
    evidence: list[dict[str,str]] = Field(min_length=1, max_length=8)
    reviewNotes: list[str] = Field(max_length=8)

class Request(BaseModel):
    model_config = ConfigDict(extra='forbid')
    jobId: str
    productName: str = Field(min_length=1, max_length=120)
    evidence: list[dict] = Field(min_length=1, max_length=5000)

def validate_rows(rows, sources, required_source=None):
    if not isinstance(rows, list) or len(rows) > 2000:
        raise ValueError('MENU_SCHEMA_INVALID')
    result = []
    for raw in rows:
        row = Proposal.model_validate(raw).model_dump()
        if any(not value.strip() or len(value) > 120 for value in row['menuPath']) or not row['menuDescription'].strip().endswith('기능'):
            raise ValueError('MENU_SCHEMA_INVALID')
        cited = set()
        allowed_keys = set()
        for reference in row['evidence']:
            source = sources.get(reference.get('sourceId'))
            quote = reference.get('quote', '').strip()
            if not source or not quote or len(quote) > 500 or quote not in source['text']:
                raise ValueError('MENU_EVIDENCE_INVALID')
            cited.add(source['id'])
            if source.get('menuKey'):
                allowed_keys.add(source['menuKey'])
        if required_source and required_source not in cited:
            raise ValueError('MENU_CURRENT_SOURCE_REQUIRED')
        if not set(row['matchedMenuKeys']).issubset(allowed_keys):
            raise ValueError('MENU_MATCH_EVIDENCE_INVALID')
        result.append(row)
    return result

def baseline(evidence):
    """Existence-only menu rows. Does not infer the behaviour behind a button."""
    rows = []
    for source in evidence:
        if source['kind'] not in ('MENU','CONTROL'):
            continue
        path = [part for part in source.get('path', []) if part not in RULES['structuralLabels']]
        name = path[-1] if path else source['text'][:120]
        rows.append({'menuPath': path[:-1][:3], 'functionName': name, 'menuDescription': f'{name} 메뉴를 제공하는 기능',
            'included': source['kind'] == 'MENU',
            'location': source.get('location', 'UNKNOWN'), 'matchedMenuKeys': [source['menuKey']],
            'evidence': [{'sourceId': source['id'], 'quote': source['text'][:300]}],
            'reviewNotes': ['메뉴 존재 근거이며 세부 동작은 문서 대조와 사용자 검토 필요'] + (['깊은 원본 경로의 재분류 검토 필요'] if len(path) > 4 else [])})
    return rows

def prompt(request, source=None, candidates=None):
    payload = request.model_dump() if source is None else {'productName': request.productName, 'source': source, 'menuCandidates': candidates}
    instructions = '\n'.join(RULES['instructions'])
    schema = json.loads(json.dumps(SCHEMA))
    if source is None:
        instructions += '\n외부 통합 작성에서는 현재 source 대신 전체 evidence 배열을 사용한다. 전체 자료를 대조해 최대 2,000개 행을 작성한다. 앞의 조각별 8개 제한은 적용하지 않는다.'
        schema['properties']['rows']['maxItems'] = 2000
    return instructions + '\n예시(실제 제품 정보가 아님):\n' + json.dumps(EXAMPLES,ensure_ascii=False) + '\n분석 자료:\n' + json.dumps(payload,ensure_ascii=False) + '\n출력 스키마:\n' + json.dumps(schema,ensure_ascii=False)

def merge(rows):
    result = OrderedDict()
    for row in rows:
        key = json.dumps([row['menuPath'], row['functionName'], row['menuDescription'], sorted(row['matchedMenuKeys'])],ensure_ascii=False)
        if key not in result:
            result[key] = row
        else:
            prior = result[key]
            prior['menuDescription'] = row['menuDescription']
            for field in ('evidence','reviewNotes'):
                for value in row[field]:
                    if value not in prior[field] and len(prior[field]) < 8:
                        prior[field].append(value)
    return list(result.values())

def call_spec(request, source, candidates):
    allowed = {item['id']:item for item in [source,*candidates]}
    spans = []
    for item in allowed.values():
        text=item['text']
        for start in range(0,len(text),200):
            quote=text[start:start+240]
            if quote.strip():
                spans.append({'spanId':f"{item['id']}@{item.get('segmentStart',0)+start}",'sourceId':item['id'],'quote':quote})
    schema=json.loads(json.dumps(SCHEMA))
    row=schema['properties']['rows']['items']
    row['required'].remove('evidence');row['required'].append('evidenceSpanIds');del row['properties']['evidence']
    row['properties']['evidenceSpanIds']={'type':'array','minItems':1,'maxItems':8,'items':{'type':'string','enum':[span['spanId'] for span in spans]}}
    keys=[item['menuKey'] for item in allowed.values() if item.get('menuKey')]
    if keys:
        row['properties']['matchedMenuKeys']['items']['enum']=keys
    else:
        row['properties']['matchedMenuKeys']['maxItems']=0
    compact=lambda item:{key:value for key,value in item.items() if key!='text'}
    payload={'productName':request.productName,'source':compact(source),'menuCandidates':[compact(item) for item in candidates],
        'sourceSpans':spans,'styleExamples':[{'functionName':'스케줄 삭제','menuDescription':'스케줄을 삭제하는 기능'}]}
    return allowed,spans,schema,payload

def request_size(instructions, payload, schema):
    messages=[{'role':'system','content':instructions},{'role':'user','content':json.dumps(payload,ensure_ascii=False)}]
    return len(json.dumps(messages,ensure_ascii=False).encode())+len(json.dumps(schema).encode())

def budget_segments(request, source, budget):
    """Preserve full text and original source IDs; overlap protects split sentences."""
    _,_,schema,payload=call_spec(request,source,[])
    if request_size(AUTO_INSTRUCTIONS,payload,schema)+600 <= budget:
        return [source]
    text=source['text']
    if len(text)<120:
        raise ValueError('LLM_INPUT_BUDGET_EXCEEDED')
    middle=len(text)//2
    boundary=text.rfind('\n',len(text)//3,middle+1)
    if boundary>0:middle=boundary+1
    overlap=min(40,len(text)//8)
    left={**source,'text':text[:middle+overlap]}
    right={**source,'text':text[middle:],'segmentStart':source.get('segmentStart',0)+middle}
    return budget_segments(request,left,budget)+budget_segments(request,right,budget)

@router.post('/prompt')
async def external_prompt(request: Request):
    schema=json.loads(json.dumps(SCHEMA));schema['properties']['rows']['maxItems']=2000
    return {'rulesVersion': RULES['version'], 'schemaVersion': 'functional-menu-v1', 'prompt': prompt(request), 'schema': schema}

@router.get('/progress/{job_id}')
async def progress(job_id: str):
    return PROGRESS.get(job_id, {'completed':0,'total':0})

async def generate(request: Request):
    provider = provider_from_env()
    sources = {item['id']: item for item in request.evidence}
    if len(sources) != len(request.evidence):
        raise HTTPException(422, 'MENU_DUPLICATE_SOURCE')
    rows = baseline(request.evidence)
    if provider.mode == 'MOCK':
        return {'schemaVersion':'functional-menu-v1','rulesVersion':RULES['version'],'generationMode':'MOCK','rows':merge(rows)}
    rows=baseline(request.evidence)
    if not isinstance(provider, OllamaLlmProvider):
        raise HTTPException(503, 'MENU_PROVIDER_UNAVAILABLE')
    menus = [source for source in request.evidence if source['kind'] in ('MENU','CONTROL')]
    screens = [source for source in request.evidence if source['kind']=='SCREEN']
    analysis = [part for source in request.evidence if source['kind']=='DOCUMENT'
                for part in budget_segments(request,source,provider.input_budget_for(OUTPUT_TOKENS))]
    PROGRESS[request.jobId] = {'completed':0,'total':len(analysis)}
    while len(PROGRESS) > 1024:
        PROGRESS.popitem(last=False)
    try:
        async with LOCK:
            for index, source in enumerate(analysis):
                tokens = set(re.findall(r'\w+',source['text'].lower()))
                candidates = sorted(menus,key=lambda menu:len(tokens.intersection(re.findall(r'\w+',menu['text'].lower()))),reverse=True)[:8]
                relevant=sorted(screens,key=lambda screen:len(tokens.intersection(re.findall(r'\w+',screen['text'].lower()))),reverse=True)
                if relevant and tokens.intersection(re.findall(r'\w+',relevant[0]['text'].lower())):
                    candidates.append(relevant[0])
                instructions = AUTO_INSTRUCTIONS
                # Candidate retrieval may shrink; the current source is never truncated.
                while True:
                    allowed,spans,schema,payload=call_spec(request,source,candidates)
                    estimated=request_size(instructions,payload,schema)
                    if estimated+600 <= provider.input_budget_for(OUTPUT_TOKENS) or not candidates:
                        break
                    candidates.pop()
                for attempt in range(2):
                    response = await provider._call(instructions,payload,schema,num_predict=OUTPUT_TOKENS)
                    try:
                        by_id={span['spanId']:span for span in spans}
                        for row in response['rows']:
                            selected=row.pop('evidenceSpanIds')
                            row['evidence']=[{'sourceId':by_id[span]['sourceId'],'quote':by_id[span]['quote']} for span in selected]
                        proposed=validate_rows(response.get('rows'),allowed,source['id'])
                        if source.get('role') == 'FUNCTION_LIST':
                            actions=set(re.findall(r'(조회|검색|추가|생성|수정|삭제|실행|다운로드|설정)(?:하는|할|하고)',source['text']))
                            combined=' '.join(row['functionName']+' '+row['menuDescription'] for row in proposed)
                            missing=[action for action in actions if action not in combined]
                            if missing:
                                raise ValueError('MENU_FUNCTION_COVERAGE_INCOMPLETE')
                        break
                    except (ValueError,TypeError,KeyError) as invalid:
                        if attempt:
                            if 'MENU_FUNCTION_COVERAGE_INCOMPLETE' in str(invalid):
                                # A small model may repeatedly omit one verb. Ask a bounded,
                                # schema-constrained question for each missing documented action.
                                for focus in missing:
                                    focused=json.loads(json.dumps(schema))
                                    focused['properties']['rows']['minItems']=1
                                    focused['properties']['rows']['items']['properties']['functionName']['pattern']=f'.*{focus}.*'
                                    targeted=await provider._call(instructions+f'\n이번 요청은 현재 원문의 {focus} 기능만 작성한다. functionName에 {focus}를 포함한다.',payload,focused,num_predict=OUTPUT_TOKENS)
                                    for row in targeted['rows']:
                                        row['evidence']=[{'sourceId':by_id[span]['sourceId'],'quote':by_id[span]['quote']} for span in row.pop('evidenceSpanIds')]
                                    additions=validate_rows(targeted['rows'],allowed,source['id'])
                                    if not any(focus in row['functionName']+' '+row['menuDescription'] for row in additions):
                                        raise ValueError('MENU_FUNCTION_COVERAGE_INCOMPLETE')
                                    proposed.extend(additions)
                                break
                            raise ValueError('MENU_FUNCTION_COVERAGE_INCOMPLETE' if 'MENU_FUNCTION_COVERAGE_INCOMPLETE' in str(invalid) else 'MENU_EVIDENCE_INVALID') from None
                        instructions += '\n검증 재시도: 제공된 원문 구간 ID만 선택하고, 현재 기능리스트에 명시된 조회·검색·추가·생성·수정·삭제·실행·다운로드·설정 중 존재하는 독립 기능을 빠짐없이 각각 별도 행으로 작성하세요.'
                rows.extend(proposed)
                if len(rows) > 4000:
                    raise ValueError('MENU_ROW_LIMIT')
                PROGRESS[request.jobId] = {'completed':index+1,'total':len(analysis)}
        return {'schemaVersion':'functional-menu-v1','rulesVersion':RULES['version'],'generationMode':'REAL','rows':merge(rows)}
    except Exception as error:
        # Never return provider/source text in error responses.
        code = next((name for name in ('LLM_INPUT_BUDGET_EXCEEDED','LLM_OUTPUT_INCOMPLETE','MENU_EVIDENCE_INVALID','MENU_SCHEMA_INVALID','MENU_CURRENT_SOURCE_REQUIRED','MENU_MATCH_EVIDENCE_INVALID','MENU_ROW_LIMIT','MENU_FUNCTION_COVERAGE_INCOMPLETE') if name in str(error)), 'MENU_GENERATION_FAILED')
        raise HTTPException(422,code) from None

@router.post('/generate')
async def generate_http(request: Request, connection: HttpRequest):
    task = asyncio.create_task(generate(request))
    try:
        while not task.done():
            if await connection.is_disconnected():
                task.cancel()
                raise HTTPException(499, 'MENU_CANCELED')
            await asyncio.wait({task}, timeout=0.5)
        return await task
    finally:
        if not task.done():
            task.cancel()
