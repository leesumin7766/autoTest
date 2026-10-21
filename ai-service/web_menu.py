"""Observed hierarchy is authoritative. No product-specific inferred menu groups."""
from collections import OrderedDict

CONTAINERS={'메뉴','탭','Global','Local','Breadcrumb','Excel 1depth'}

def web_path(source, product=''):
    if 'webPath' in source:return list(source['webPath'])
    path=[]
    for label in source.get('path',[]):
        if label and label not in CONTAINERS and label!=product and (not path or path[-1]!=label):path.append(label)
    if source.get('barConfirmed') and source.get('location') in ('HEADER','FOOTER'):
        path.insert(0,'상단바' if source['location']=='HEADER' else '하단바')
    return path

def structure(evidence, product):
    nodes=OrderedDict()
    for source in evidence:
        if source['kind'] not in ('MENU','MENU_GROUP'):continue
        path=web_path(source,product)
        if path:nodes.setdefault(tuple(path),{'path':path,'menuKey':source.get('menuKey'),'sourceId':source['id'],
            'kind':source['kind'],'location':source.get('location','UNKNOWN'),'depthReviewRequired':len(path)>3})
    return {'version':'web-first-v1','productName':product,'nodes':list(nodes.values())}

def anchor(row, sources, product=''):
    row['rowType']='FUNCTION'
    linked=[s for s in sources.values() if s.get('menuKey') in row.get('matchedMenuKeys',[])]
    paths={tuple(web_path(s,product)) for s in linked if web_path(s,product)}
    deepest=max(paths,key=len) if paths else ()
    valid=bool(deepest) and len(deepest)<=3 and all(deepest[:len(p)]==p for p in paths)
    notes=list(row.get('reviewNotes',[]))[:6]
    if not valid:
        row.update(menuPath=[],matchedMenuKeys=[],included=False,placementStatus='UNASSIGNED')
        notes.append('웹 메뉴에서 배치를 확인하지 못했습니다. 문서 전용 검토 항목이며 메뉴트리에 자동 포함하지 않습니다.')
    else:
        row.update(menuPath=list(deepest),placementStatus='WEB_ANCHORED')
    row['reviewNotes']=notes
    return row
