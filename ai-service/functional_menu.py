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
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from product_description.api import require_internal_token
from product_description.llm import provider_from_env
from product_description.ollama_provider import OllamaLlmProvider
from web_menu import web_path, structure, anchor

ROOT = Path(__file__).parent / 'menu_templates' / 'functional-v1'
RULES = json.loads((ROOT / 'rules.json').read_text(encoding='utf-8'))
SCHEMA = json.loads((ROOT / 'output.schema.json').read_text(encoding='utf-8'))
EXAMPLES = json.loads((ROOT / 'examples.json').read_text(encoding='utf-8'))
OUTPUT_TOKENS = 2048
AUTO_INSTRUCTIONS = '\n'.join(RULES['instructions'])+'\n자동 생성 응답에서는 evidence 대신 evidenceSpanIds를 사용한다. sourceSpans에서 현재 source의 원문 구간 ID를 반드시 선택하고 인용문을 다시 작성하지 않는다.\nWeb-first: use only candidate webPath for menuPath. Do not invent menu groups. Unmatched document functions require separate placement review.'
router = APIRouter(prefix='/api/v1/functional-menus', dependencies=[Depends(require_internal_token)])
PROGRESS = OrderedDict()
LOCK = asyncio.Lock()
CHECKPOINT_ROOT = Path(__file__).parent / '.runtime' / 'functional-menu-jobs'

def checkpoint_path(job_id):
    return CHECKPOINT_ROOT / (hashlib.sha256(job_id.encode()).hexdigest()+'.json')

def save_checkpoint(request, rows, issues, completed, total, complete=False):
    merged=merge(rows)
    assigned={tuple(row['menuPath']) for row in merged if row.get('rowType')!='MENU_OBSERVATION' and row.get('placementStatus')=='WEB_ANCHORED'}
    merged=[row for row in merged if row.get('rowType')!='MENU_OBSERVATION' or tuple(row['menuPath']) not in assigned]
    reported=list(issues)
    if len(merged)>2000:reported.append({'code':'MENU_ROW_LIMIT','overflowRowCount':len(merged)-2000})
    output={'jobId':request.jobId,'schemaVersion':'functional-menu-v1','rulesVersion':RULES['version'],
        'generationMode':'REAL','completionStatus':'COMPLETE' if complete and not reported else 'PARTIAL',
        'progress':{'completed':completed,'total':total},'rows':merged[:2000],'issues':reported[:100],
        'issueCount':len(reported),'diagnosticsTruncated':len(reported)>100}
    output['webStructure']=request.webStructure or structure(request.evidence,request.productName)
    CHECKPOINT_ROOT.mkdir(parents=True,exist_ok=True,mode=0o700)
    path=checkpoint_path(request.jobId);temporary=path.with_suffix('.tmp')
    temporary.write_text(json.dumps(output,ensure_ascii=False),encoding='utf-8');temporary.chmod(0o600);temporary.replace(path)
    return output

def ground_proposal(raw, allowed, spans, required_source):
    if not isinstance(raw,dict):raise ValueError('MENU_SCHEMA_INVALID')
    row=json.loads(json.dumps(raw))
    by_id={span['spanId']:span for span in spans}
    selected=row.pop('evidenceSpanIds')
    if not isinstance(selected,list) or any(key not in by_id for key in selected):
        raise ValueError('MENU_SPAN_ID_INVALID')
    row['evidence']=[{'sourceId':by_id[key]['sourceId'],'quote':by_id[key]['quote']} for key in selected]
    if not any(ref['sourceId']==required_source for ref in row['evidence']):
        raise ValueError('MENU_CURRENT_SOURCE_REQUIRED')
    menu_sources={source['menuKey']:source for source in allowed.values() if source.get('menuKey')}
    keys=row.get('matchedMenuKeys',[])
    if not keys and row.get('menuPath'):
        keys=[key for key,source in menu_sources.items() if web_path(source)==row['menuPath']][:1]
    if not isinstance(keys,list) or any(not isinstance(key,str) for key in keys):raise ValueError('MENU_SCHEMA_INVALID')
    known=list(dict.fromkeys(key for key in keys if key in menu_sources))[:7]
    notes=list(row.get('reviewNotes',[]))[:5]
    if len(known)!=len(set(keys)):
        notes.append('제공된 화면 근거로 확인할 수 없는 메뉴 연결을 해제했습니다. 문서 기준으로 배치를 검토하세요.')
        # Unverified menu citations must not make the document row look screen-linked.
        row['evidence']=[ref for ref in row['evidence'] if not allowed[ref['sourceId']].get('menuKey') or allowed[ref['sourceId']]['menuKey'] in known]
    for key in known:
        source=menu_sources[key]
        if not any(ref['sourceId']==source['id'] for ref in row['evidence']):
            row['evidence'].append({'sourceId':source['id'],'quote':source['text'][:240]})
    # Keep the current document and every selected menu within the citation limit.
    required_ids={required_source,*[menu_sources[key]['id'] for key in known]}
    row['evidence']=sorted(row['evidence'],key=lambda ref:ref['sourceId'] not in required_ids)[:8]
    row['matchedMenuKeys']=known
    if known:notes.append('화면 메뉴 근거는 프로그램이 연결했습니다. 기능과 메뉴의 의미적 일치는 검토 전 연결 제안입니다.')
    row['reviewNotes']=notes[:8]
    if not isinstance(row.get('menuDescription'),str) or not row['menuDescription'].strip().endswith('기능'):
        raise ValueError('MENU_DESCRIPTION_SUFFIX_INVALID')
    return anchor(validate_rows([row],allowed,required_source)[0],allowed)

async def extract_source(provider, source, allowed, spans, schema, payload):
    accepted=[];issues=[];instructions=AUTO_INSTRUCTIONS
    for attempt in range(2):
        try:response=await provider._call(instructions,payload,schema,num_predict=OUTPUT_TOKENS)
        except Exception as error:
            match=re.match(r'([A-Z_]{5,80})(?:$|:)',str(error))
            issues.append({'sourceId':source['id'],'segmentStart':source.get('segmentStart',0),'attempt':attempt+1,
                'code':match.group(1) if match else 'MENU_PROVIDER_UNAVAILABLE','fatal':not isinstance(error,(ValueError,TypeError,KeyError))})
            return merge(accepted),issues
        pending=[]
        if not isinstance(response,dict) or not isinstance(response.get('rows'),list):
            issues.append({'sourceId':source['id'],'segmentStart':source.get('segmentStart',0),'code':'MENU_SCHEMA_INVALID'})
            return merge(accepted),issues
        for index,raw in enumerate(response['rows']):
            try:accepted.append(ground_proposal(raw,allowed,spans,source['id']))
            except (ValueError,TypeError,KeyError) as error:
                code=str(error) if re.fullmatch('[A-Z_]{5,80}',str(error)) else 'MENU_SCHEMA_INVALID'
                issue={'sourceId':source['id'],'segmentStart':source.get('segmentStart',0),'rowIndex':index,
                    'attempt':attempt+1,'code':code,'proposedRow':raw,'expectedSpanIds':[span['spanId'] for span in spans]}
                if isinstance(error,ValidationError):issue['fields']=[{'path':list(item['loc']),'type':item['type'],'message':item['msg']} for item in error.errors(include_input=False,include_context=False)]
                pending.append(issue)
        actions=set(re.findall(r'(조회|검색|추가|생성|수정|삭제|실행|다운로드|설정)(?:하는|할|하고)',source['text'])) if source.get('role')=='FUNCTION_LIST' else set()
        combined=' '.join(row['functionName']+' '+row['menuDescription'] for row in accepted)
        missing=sorted(action for action in actions if action not in combined)
        if not pending and not missing:return merge(accepted),[]
        if attempt==0:
            codes=sorted({issue['code'] for issue in pending})
            instructions+='\n검증 재시도: 현재 문서의 근거 구간 ID를 반드시 선택하세요. 오류: '+','.join(codes)+'. 누락 동작: '+','.join(missing)
            continue
        issues.extend(pending)
        for focus in missing:
            focused=json.loads(json.dumps(schema));focused['properties']['rows']['minItems']=1
            focused['properties']['rows']['items']['properties']['functionName']['pattern']=f'.*{focus}.*'
            try:
                targeted=await provider._call(AUTO_INSTRUCTIONS+f'\n현재 원문의 {focus} 기능만 작성하고 functionName에 {focus}를 포함하세요.',payload,focused,num_predict=OUTPUT_TOKENS)
                additions=[ground_proposal(raw,allowed,spans,source['id']) for raw in targeted['rows']]
                if not any(focus in row['functionName']+' '+row['menuDescription'] for row in additions):raise ValueError('MENU_FUNCTION_COVERAGE_INCOMPLETE')
                accepted.extend(additions)
            except (ValueError,TypeError,KeyError):
                issues.append({'sourceId':source['id'],'segmentStart':source.get('segmentStart',0),'code':'MENU_FUNCTION_COVERAGE_INCOMPLETE','action':focus})
    return merge(accepted),issues

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
    webStructure: dict | None = None

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

def baseline(evidence, product=''):
    rows=OrderedDict()
    for source in evidence:
        if source['kind']!='MENU':continue
        path=web_path(source,product)
        if not path:continue
        key=tuple(path)
        if key in rows:
            prior=rows[key]
            if len(prior['matchedMenuKeys'])<7:
                prior['matchedMenuKeys'].append(source['menuKey'])
                prior['evidence'].append({'sourceId':source['id'],'quote':source['text'][:240]})
            continue
        rows[key]={'menuPath':path[:3],'functionName':path[-1],'menuDescription':f'{path[-1]} 화면을 제공하는 기능',
            'included':len(path)<=3,'rowType':'MENU_OBSERVATION','placementStatus':'WEB_ANCHORED' if len(path)<=3 else 'UNASSIGNED',
            'location':source.get('location','UNKNOWN'),'matchedMenuKeys':[source['menuKey']],
            'evidence':[{'sourceId':source['id'],'quote':source['text'][:240]}],
            'reviewNotes':['웹에서 관찰한 메뉴입니다. 문서 기능을 연결하기 전의 구조 항목입니다.']}
    return list(rows.values())

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
    paths=[[]]
    for item in candidates:
        path=web_path(item,request.productName)
        if path and len(path)<=3 and path not in paths:paths.append(path)
    row['properties']['menuPath']['enum']=paths
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
    return {'instructionVersion': 'legacy-functional-menu-v1', 'rulesVersion': RULES['version'], 'schemaVersion': 'functional-menu-v1', 'prompt': prompt(request), 'schema': schema}

@router.get('/progress/{job_id}')
async def progress(job_id: str):
    return PROGRESS.get(job_id, {'completed':0,'total':0})

@router.get('/checkpoint/{job_id}')
async def checkpoint(job_id: str):
    path=checkpoint_path(job_id)
    if not path.exists():raise HTTPException(404,'MENU_CHECKPOINT_NOT_FOUND')
    return json.loads(path.read_text(encoding='utf-8'))

async def generate(request: Request):
    provider = provider_from_env()
    sources = {item['id']: item for item in request.evidence}
    if len(sources) != len(request.evidence):
        raise HTTPException(422, 'MENU_DUPLICATE_SOURCE')
    rows = baseline(request.evidence,request.productName)
    if provider.mode == 'MOCK':
        return {'schemaVersion':'functional-menu-v1','rulesVersion':RULES['version'],'generationMode':'MOCK','webStructure':request.webStructure or structure(request.evidence,request.productName),'rows':merge(rows)}
    rows=baseline(request.evidence,request.productName)
    issues=[]
    if not isinstance(provider, OllamaLlmProvider):
        raise HTTPException(503, 'MENU_PROVIDER_UNAVAILABLE')
    menus = [source for source in request.evidence if source['kind'] in ('MENU','CONTROL')]
    screens = [source for source in request.evidence if source['kind']=='SCREEN']
    analysis = [part for source in request.evidence if source['kind']=='DOCUMENT'
                for part in budget_segments(request,source,provider.input_budget_for(OUTPUT_TOKENS))]
    PROGRESS[request.jobId] = {'completed':0,'total':len(analysis)}
    while len(PROGRESS) > 1024:
        PROGRESS.popitem(last=False)
    save_checkpoint(request,rows,issues,0,len(analysis))
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
                rejected=[]
                try:
                    proposed, rejected = await extract_source(provider,source,allowed,spans,schema,payload)
                    issues.extend(rejected)
                except (ValueError,TypeError,KeyError) as invalid:
                    proposed=[]
                    code=str(invalid) if re.fullmatch('[A-Z_]{5,80}',str(invalid)) else 'MENU_SCHEMA_INVALID'
                    issues.append({'sourceId':source['id'],'segmentStart':source.get('segmentStart',0),'code':code})
                rows.extend(proposed)
                if len(rows) > 4000:
                    raise ValueError('MENU_ROW_LIMIT')
                PROGRESS[request.jobId] = {'completed':index+1,'total':len(analysis)}
                save_checkpoint(request,rows,issues,index+1,len(analysis))
                if any(issue.get('fatal') for issue in rejected):
                    return save_checkpoint(request,rows,issues,index+1,len(analysis))
        return save_checkpoint(request,rows,issues,len(analysis),len(analysis),complete=True)
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
