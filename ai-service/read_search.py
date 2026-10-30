"""A narrow, read-only check of visible list search behavior."""
import re


def visible_names(observation):
    for table in observation.get('tables') or []:
        headers=table.get('headers') or []
        index=next((i for i,header in enumerate(headers) if re.fullmatch(r'\s*(이름|명칭|사용자명|작업명|name|username|job name)\s*',header or '',re.I)),None)
        if index is None:continue
        names=[str(row[index]).strip() for row in table.get('rows') or [] if len(row)>index and str(row[index]).strip()]
        if names:return names
    return []


def prepare_search(observation):
    """Use an already visible item; never invent product-specific test data."""
    names=visible_names(observation)
    unique=list(dict.fromkeys(names))
    fields=[el for el in observation.get('elements') or [] if el.get('tag')=='input'
            and el.get('type') in ('','text','search') and re.search(r'검색|search',el.get('name') or '',re.I)]
    if observation.get('screenReady') is False or len(unique)<2 or len(fields)!=1:
        return None
    def candidates(name):
        if '\n' not in name:return [name]
        lines=[line.strip() for line in name.splitlines() if line.strip()]
        # User tables often show a display name and an @account; the search
        # control may index only the account rather than the display name.
        return [line[1:] for line in lines if line.startswith('@')] + [line for line in lines if not line.startswith('@')]
    choice=next(((name, part) for name in unique for part in candidates(name)
                 if 3<=len(part)<=120
                 and not any(part.casefold() in other.casefold() for other in unique if other != name)),None)
    return {'inputId':fields[0]['id'],'query':choice[1],'selectedName':choice[0],'beforeNames':names} if choice else None


def search_result(binding, observation):
    """P/F only when the original row and a stable before/after list can be compared."""
    if not binding or observation.get('screenReady') is False:return None
    before=binding['beforeNames'];after=visible_names(observation)
    if not after or binding['selectedName'] not in after:return None
    if len(after)<len(before) and all(binding['query'].casefold() in name.casefold() for name in after):return 'P'
    # An unchanged list may require Enter or a product-specific Search button.
    # This generic check has no evidence that the search was submitted.
    return None
