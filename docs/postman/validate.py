"""Check collection syntax, upload fixtures and coverage of current controller routes."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def requests(items):
    for item in items:
        if 'request' in item:
            yield item
        yield from requests(item.get('item', []))


def canonical(path):
    path = path.replace('{{baseUrl}}', '').split('?', 1)[0]
    path = re.sub(r'\{\{[^}]+\}\}', '{}', path)
    return re.sub(r'\{[^}]*\}', '{}', path)


routes = set()
for file in (ROOT / 'backend/src/main/java').rglob('*Controller.java'):
    for method, path in re.findall(r'@(Get|Post|Put|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]+)"', file.read_text()):
        routes.add((method.upper(), canonical(path)))

main = json.loads((HERE / 'flying-angels-backend.postman_collection.json').read_text())
security = json.loads((HERE / 'flying-angels-security.postman_collection.json').read_text())
covered = set()
counts = []
for collection in [main, security]:
    entries = list(requests(collection['item']))
    counts.append(len(entries))
    assert collection['info']['schema'].endswith('/v2.1.0/collection.json')
    for item in entries:
        request = item['request']
        route = (request['method'], canonical(request['url']))
        matching = [candidate for candidate in routes if candidate[0] == route[0]
                    and re.fullmatch(re.escape(candidate[1]).replace(r'\{\}', r'[^/]+'), route[1])]
        assert matching, (item['name'], route)
        if collection is main:
            covered.update(matching)
        assert any(e['listen'] == 'test' for e in item.get('event', [])), item['name']
        body = request.get('body', {})
        if body.get('mode') == 'raw':
            rendered = re.sub(r'\{\{[^}]+\}\}', '1', body['raw'])
            json.loads(rendered)
        for field in body.get('formdata', []):
            if field.get('type') == 'file':
                assert (HERE / field['src']).is_file(), field['src']
assert routes == covered, ('Missing routes', routes - covered)
with zipfile.ZipFile(HERE / 'fixtures/athletes.xlsx') as workbook:
    for name in workbook.namelist():
        if name.endswith(('.xml', '.rels')):
            ET.fromstring(workbook.read(name))
print(f'Validated {counts[0]} functional requests, {counts[1]} security requests, all {len(routes)} endpoints, request JSON and upload fixtures.')
