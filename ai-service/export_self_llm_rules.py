"""Regenerate the downloadable common instructions from the shared contract."""
import json
from pathlib import Path
from self_llm_prompt import common_rules

ROOT = Path(__file__).parent


def document():
    template = ROOT / 'menu_templates' / 'functional-v1'
    load = lambda name: json.loads((template / name).read_text(encoding='utf-8'))
    return common_rules(load('rules.json'), load('output.schema.json'), load('examples.json'))


if __name__ == '__main__':
    target = ROOT.parent / 'frontend' / 'public' / 'downloads' / 'menu-tree-common-rules.md'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(document(), encoding='utf-8', newline='\n')
