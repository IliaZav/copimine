"""Inspect the reviewed, bundled catalogue; never silently pull a different branch."""
from pathlib import Path
import json
root = Path(__file__).resolve().parents[1]
rows = json.loads((root / 'data/catalog-current.json').read_text('utf-8'))
assert len(rows) == 41 and len({x['id'] for x in rows}) == 41
assert not any('compass' in x['id'] for x in rows)
assert all((root / x['image']).is_file() for x in rows)
print(json.dumps({'total': len(rows), 'public': sum(x['visibility']=='public' for x in rows)}, indent=2))
