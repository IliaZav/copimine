"""Build CopiMine v6 as one self-contained offline HTML.

The distributable also contains the original source tree and the independent
production server. This file never contains runtime accounts or credentials.
"""
from pathlib import Path
from urllib.parse import urlsplit, unquote
import argparse
import base64
import json
import mimetypes
import re

ROOT = Path(__file__).resolve().parent
IMAGE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.svg', '.ico', '.gif', '.woff', '.woff2'}


def local_key(value, base=ROOT):
    parsed = urlsplit(value)
    if parsed.scheme or parsed.netloc or value.startswith('#'):
        return None
    path = (base / unquote(parsed.path)).resolve()
    if not path.is_relative_to(ROOT):
        raise ValueError('Asset escapes project: ' + value)
    return path.relative_to(ROOT).as_posix()


def build(output):
    assets = {}
    for path in sorted((ROOT / 'assets').rglob('*')):
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS:
            if path.is_symlink():
                raise ValueError('Symlink asset is not permitted: ' + str(path))
            mime = mimetypes.guess_type(path.name)[0] or 'application/octet-stream'
            assets[path.relative_to(ROOT).as_posix()] = 'data:' + mime + ';base64,' + base64.b64encode(path.read_bytes()).decode('ascii')
    html = (ROOT / 'index.html').read_text(encoding='utf-8')

    def stylesheet(match):
        source = ROOT / local_key(match.group(1))
        content = source.read_text(encoding='utf-8')
        def css_url(url):
            raw = url.group(1).strip().strip('\'"')
            key = local_key(raw, source.parent)
            if key is None:
                return url.group(0)
            if key not in assets:
                raise ValueError('Missing CSS asset: ' + str(key))
            return 'url("' + assets[key] + '")'
        content = re.sub(r'url\(([^)]+)\)', css_url, content)
        return '<style>' + re.sub(r'</style', '<\\/style', content, flags=re.I) + '</style>'
    html = re.sub(r'<link rel="stylesheet" href="([^"]+)">', stylesheet, html)

    def script(match):
        source = ROOT / local_key(match.group(1))
        content = source.read_text(encoding='utf-8')
        content = re.sub(r'</script', lambda _: '<\\/script', content, flags=re.I)
        return '<script>' + content + '</script>'
    html = re.sub(r'<script defer src="([^"]+)"></script>', script, html)

    def href(match):
        key = local_key(match.group(1))
        return 'href="' + assets[key] + '"' if key in assets else match.group(0)
    html = re.sub(r'href="([^"]+)"', href, html)
    payload = json.dumps(assets, ensure_ascii=True, separators=(',', ':'))
    html = html.replace('<head>', '<head><script>window.CM_ASSETS=' + payload + ';</script>', 1)
    if re.search(r'<script[^>]+src=|<link rel="stylesheet"', html):
        raise ValueError('An external script or stylesheet escaped embedding.')
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(html, encoding='utf-8')
    report = {'file': str(output.resolve()), 'bytes': output.stat().st_size, 'embeddedAssets': len(assets), 'mode': 'offline-preview'}
    print(json.dumps(report, ensure_ascii=False))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default=str(ROOT / 'CopiMine_v6.html'))
    build(parser.parse_args().output)
