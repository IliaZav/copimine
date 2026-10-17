"""Local CopiMine design preview. Standard library only. NOT a production web server.
Reads only allowlisted mods and day-map tiles from the user's own profile.
No Minecraft launch, settings changes, credentials, waypoints or live player positions.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, unquote
import argparse
import hashlib
import io
import json
import os
import struct
import threading
import time
import webbrowser
import zipfile

ROOT = Path(__file__).resolve().parent


def metadata(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return json.loads(archive.read('fabric.mod.json'))


def inspect_materials(profile):
    result = {'map': {'available': False, 'tiles': []}, 'modpack': {'available': False, 'files': [], 'version': ''}}
    blobs = {}
    if not profile or not profile.is_dir():
        return result, blobs
    mods = profile / 'mods'
    candidates = []
    for pattern in ('CopiMineClient-*.jar', 'voicechat-fabric-*.jar'):
        files = sorted(mods.glob(pattern), key=lambda p: p.stat().st_mtime, reverse=True)
        if not files:
            break
        candidates.append(files[0])
    if len(candidates) == 2:
        try:
            payloads = [(p.name, p.read_bytes()) for p in candidates if p.stat().st_size < 64 * 1024 * 1024]
            infos = [metadata(data) for _, data in payloads]
            versions = [str(j.get('depends', {}).get('minecraft', '')).replace('.x', '') for j in infos]
            if len(payloads) == 2 and all(versions) and versions[0] == versions[1]:
                installed = {j['id'] for j in infos}
                for _, data in payloads:
                    with zipfile.ZipFile(io.BytesIO(data)) as z:
                        info = json.loads(z.read('fabric.mod.json'))
                        for item in info.get('jars', []):
                            try:
                                installed.add(metadata(z.read(item['file']))['id'])
                            except (KeyError, zipfile.BadZipFile):
                                pass
                missing = sorted({key for j in infos for key in j.get('depends', {}) if key not in {'java', 'minecraft', 'fabricloader'} and key not in installed})
                if missing:
                    result['modpack']['error'] = 'Missing dependencies: ' + ', '.join(missing)
                else:
                    result['modpack'] = {'available': True, 'files': [n for n, _ in payloads], 'version': versions[0], 'java': '25+', 'loader': 'Fabric Loader 0.19.5+', 'source': 'Your local profile; personal packaging only.'}
                    blobs['mods'] = payloads
        except (OSError, KeyError, ValueError, zipfile.BadZipFile) as exc:
            result['modpack']['error'] = str(exc)[:160]
    mp = profile / 'journeymap' / 'data' / 'mp'
    roots = [mp / '~Minecraft' / 'overworld' / 'day', mp / 'CopiMine~26~3~Local' / 'overworld' / 'day']
    day = next((p for p in roots if p.is_dir()), None)
    if day:
        captures = []
        for x, z in ((-1, -1), (0, -1), (-1, 0), (0, 0)):
            file = day / f'{x},{z}.png'
            if not file.is_file() or file.stat().st_size > 4 * 1024 * 1024:
                continue
            captured_at = file.stat().st_mtime
            if captured_at > time.time() - 12 * 3600:
                continue  # Never expose fresh data, even in local preview.
            content = file.read_bytes()
            if not content.startswith(b'\x89PNG\r\n\x1a\n') or len(content) < 24:
                continue
            width, height = struct.unpack('>II', content[16:24])
            if (width, height) != (512, 512):
                continue
            idx = len(captures)
            blobs[f'tile-{idx}'] = content  # frozen at startup, not a live filesystem view
            captures.append({'url': f'/local-map/tile-{idx}.png', 'x': x * 512, 'z': z * 512, 'width': width, 'height': height, 'capturedAt': int(captured_at * 1000)})
        result['map'] = {'available': bool(captures), 'tiles': captures, 'source': 'JourneyMap day tiles, explored local test world only', 'capturedAt': max((x['capturedAt'] for x in captures), default=None)}
    return result, blobs


class PreviewHandler(SimpleHTTPRequestHandler):
    materials = {}
    material_blobs = {}

    def permitted_request(self):
        host = self.headers.get('Host', '').split(':')[0]
        return host in {'127.0.0.1', 'localhost'} and self.headers.get('Sec-Fetch-Site') != 'cross-site'

    def send_head(self):
        if not self.permitted_request():
            self.send_error(403)
            return None
        path = unquote(urlsplit(self.path).path)
        if path == '/':
            path = '/index.html'
        relative = Path(path.lstrip('/'))
        root = Path(self.directory).resolve()
        target = (root / relative).resolve()
        top = path in {'/index.html', '/CopiMine_v6.html'}
        asset = path.startswith('/assets/') and target.suffix.lower() in {'.css', '.js', '.png', '.jpg', '.jpeg', '.webp', '.svg', '.ico', '.woff', '.woff2'}
        data = path in {'/data/map-manifest.json', '/data/catalog-v6-public.json', '/data/content-v6-public.json', '/data/materials-v6.json'}
        if '\\' in path or '..' in relative.parts or any(part.startswith('.') for part in relative.parts) or not target.is_relative_to(root) or not (top or asset or data) or not target.is_file():
            self.send_error(404)
            return None
        return super().send_head()

    def list_directory(self, path):
        self.send_error(404)
        return None

    def end_headers(self):
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('X-Frame-Options', 'DENY')
        self.send_header('Referrer-Policy', 'no-referrer')
        super().end_headers()

    def do_GET(self):
        if not self.permitted_request():
            self.send_error(403)
            return
        path = urlsplit(self.path).path
        if path == '/api/local-materials':
            return self.reply(json.dumps(self.materials, ensure_ascii=False).encode(), 'application/json; charset=utf-8')
        if path.startswith('/local-map/'):
            key = path.rsplit('/', 1)[-1].removesuffix('.png')
            data = self.material_blobs.get(key)
            if not isinstance(data, bytes):
                self.send_error(404)
                return
            return self.reply(data, 'image/png')
        if path == '/downloads/copimine-mods.zip':
            mods = self.material_blobs.get('mods')
            if not mods:
                self.send_error(404, 'No compatible local mod files found')
                return
            output = io.BytesIO()
            with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
                for name, data in mods:
                    z.writestr('mods/' + name, data)
                z.writestr('SHA256SUMS.txt', '\n'.join(hashlib.sha256(d).hexdigest() + '  mods/' + n for n, d in mods))
                info = self.materials['modpack']
                z.writestr('README.txt', 'CopiMine local mod archive\nMinecraft ' + info['version'] + '\n' + info['loader'] + '\nJava ' + info['java'] + '\nCopy mods into a matching Fabric profile. Server: copimine.ru\nThese are your existing local files, not a newly verified public release.\n')
            return self.reply(output.getvalue(), 'application/zip', 'CopiMineMods-local.zip')
        if path.startswith('/api/'):
            self.send_error(404, 'Use server.py for authenticated server mode')
            return
        super().do_GET()

    def reply(self, content, mime, filename=None):
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(content)))
        self.send_header('Cache-Control', 'no-store')
        if filename:
            self.send_header('Content-Disposition', f'attachment; filename="{filename}"')
        self.end_headers()
        self.wfile.write(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--no-browser', action='store_true')
    parser.add_argument('--profile', type=Path)
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error('Invalid port')
    if args.profile:
        profile = args.profile
    else:
        # Default: the three complete, rendered world maps are packaged.
        # Optional home-only import: python serve.py --profile PATH_TO_PROFILE
        profile = None
    PreviewHandler.materials, PreviewHandler.material_blobs = inspect_materials(profile)
    handler = partial(PreviewHandler, directory=str(ROOT))
    try:
        server = ThreadingHTTPServer(('127.0.0.1', args.port), handler)
    except OSError:
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler)
    url = f'http://127.0.0.1:{server.server_port}/index.html'
    print('CopiMine v6 preview: ' + url, flush=True)
    print('For authenticated accounts and integrations, run server.py; see README.md.', flush=True)
    print('Local map found: ' + str(PreviewHandler.materials['map']['available']), flush=True)
    print('Compatible personal mods found: ' + str(PreviewHandler.materials['modpack']['available']), flush=True)
    if not args.no_browser:
        threading.Timer(.5, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
