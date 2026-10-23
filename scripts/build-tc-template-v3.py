"""Versioned template migration; the reviewed v1 bank remains unchanged."""
import copy
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'test-management-service/src/main/resources/tc-templates'
bank = json.loads((folder / 'menu-tc-v1.json').read_text(encoding='utf-8'))
bank['version'] = 'menu-tc-v3'
bank['policy'] = {'generation': 'FULL_TEMPLATE', 'selection': 'QWEN_BEFORE_BROWSER', 'unknown': 'NEEDS_CONTEXT',
                  'counts': {'LOGIN': 27, 'FEATURE': 9, 'CRUD': 16, 'COMMON': 39, 'PRODUCT_DOCUMENT': 7, 'USER_MANUAL': 22}}
base = bank['groups']['FEATURE']
crud = copy.deepcopy(base[:5])
def row(scenario, inputs, expected, operation='', sub='기능정확성', quality='기능적합성'):
    return {'scenario': scenario, 'inputs': inputs, 'expectedResult': expected, 'qualityCharacteristic': quality,
            'subCharacteristic': sub, 'requiredOperation': operation,
            'defectExample': {'summary': '{기능명} 처리 오류', 'severity': 'M', 'content': '{기능명}에서 해당 시험 조건을 적용했으나 기대 결과와 실제 결과가 일치하지 않음. 입력값·실제 결과·증거를 기록한다.'}}
crud += [
    row('유효한 입력값으로 등록되는지 확인', '등록 가능한 유효 입력값과 생성 전 상태', '입력값으로 등록되고 다시 조회한 값이 일치함', 'CREATE'),
    row('유효한 입력값으로 수정되는지 확인', '수정할 시험 데이터와 변경값', '변경값으로 저장되고 다시 조회한 값이 일치함', 'UPDATE'),
    row('유효한 조건으로 조회되는지 확인', '조회할 시험 데이터와 조건별 기대 목록', '조회 조건에 맞는 데이터가 표시되고 조건 밖 데이터는 포함되지 않음', 'READ'),
    row('선택한 값이 삭제되는지 확인', '삭제 전용 시험 데이터와 식별값', '선택한 데이터가 삭제되고 재조회해도 표시되지 않으며 다른 데이터는 유지됨', 'DELETE'),
    row('삭제 시 오조작 방지 안내가 제공되는지 확인', '삭제 가능한 시험 데이터', '삭제 확정 전에 대상과 삭제 의사를 확인할 수 있는 안내가 제공됨', 'DELETE', '사용자오류방지성', '사용성'),
    row('문서에 명시된 CRUD 기능이 제공되는지 확인', '제품에서 제공한다고 명시한 생성·조회·수정·삭제 범위', '문서에 명시한 범위의 기능이 제공됨'),
    row('필수 입력값 정보가 제공되는지 확인', '필수 필드 목록과 화면·매뉴얼 근거', '필수 항목을 식별할 수 있고 문서와 화면의 필수 항목이 일치함', 'WRITE', '운영성', '사용성'),
    row('필수 입력필드를 비운 경우 저장이 거부되는지 확인', '필수 필드와 나머지 유효 입력값', '필수값 누락이 안내되고 데이터가 저장되지 않음', 'WRITE', '운영성', '사용성'),
    row('입력필드 제약사항 정보가 제공되는지 확인', '형식·길이·범위·중복 제약의 문서 근거', '해당 필드의 입력 제약이 제공되고 문서의 제약과 일치함', 'WRITE', '운영성', '사용성'),
    row('입력필드의 스크립트가 실행되지 않는지 확인', '전용 시험 데이터와 허용된 스크립트 입력 시험값', '입력값이 코드로 실행되지 않고 제품의 입력 정책에 따라 처리됨', 'WRITE', '무결성', '보안성'),
    row('삭제 확인에서 취소하는 경우 원본이 유지되는지 확인', '삭제 전용 시험 데이터와 삭제 확인·취소 방식', '삭제 취소 후 동일 데이터가 재조회되고 값이 변경되지 않음', 'DELETE', '사용자오류방지성', '사용성'),
]
for index, item in enumerate(crud, 1):
    item['ruleId'] = f'CRUD-{index:02}'
    item.setdefault('requiredOperation', '')
bank['groups']['CRUD'] = crud
for group, templates in bank['groups'].items():
    for template in templates:
        template.setdefault('requiredOperation', 'UPLOAD' if template['ruleId'] in ('FEATURE-06', 'FEATURE-07') else '')
        template['executionPolicy'] = 'KEEP_TC_SELECT_EXECUTION'
assert len(crud) == 16
(folder / 'menu-tc-v3.json').write_text(json.dumps(bank, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
