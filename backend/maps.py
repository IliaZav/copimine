"""BlueMap publication boundary: immutable, scoped snapshots and durable jobs.

The renderer writes only to its private work directory. A single worker waits for
the renderer to quiesce, copies changed content into immutable blobs, validates
both scopes, and atomically promotes ONE pointer for the complete publication.
No route exposes BlueMap's work directory or the publication control files.
"""
from pathlib import Path, PurePosixPath
import gzip
import hashlib
import json
import mimetypes
import os
import re
import shutil
import time
import uuid

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse, JSONResponse, Response
from .security import current_user, fail, throttle

router = APIRouter()
DIMENSIONS = [('overworld', 'Обычный мир'), ('nether', 'Незер'), ('end', 'Энд')]
ACTIVE = ('queued', 'waiting_for_resources', 'running', 'validating', 'publishing')
DEFAULTS = dict(enabled=True, autoRefresh=True, intervalMinutes=720, minAgeMinutes=0,
                showPlayers=False, positionDelaySeconds=0, perspective=False, firstPerson=False)
VERSION = re.compile(r'[a-f0-9]{32}\Z')


class PublicationError(Exception):
    pass


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name('.' + path.name + '.' + uuid.uuid4().hex)
    try:
        with temp.open('x', encoding='utf-8') as stream:
            stream.write(encoded(value)); stream.flush(); os.fsync(stream.fileno())
        os.replace(temp, path)
        if os.name != 'nt':
            fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try: os.fsync(fd)
            finally: os.close(fd)
    finally:
        temp.unlink(missing_ok=True)


class MapStorage:
    def __init__(self, root, minimum_free=5 * 1024**3):
        self.root, self.minimum_free = Path(root), minimum_free
        self._source_index={}

    def _remove_generated(self,path):
        resolved=path.resolve()
        if path.is_symlink() or resolved==self.root.resolve() or not resolved.is_relative_to(self.root.resolve()):
            raise PublicationError('unsafe_cleanup_target')
        shutil.rmtree(resolved)

    def current(self):
        try:
            data = json.loads((self.root / 'current.json').read_text(encoding='utf-8'))
            return data if VERSION.fullmatch(data.get('version', '')) else {}
        except (OSError, ValueError):
            return {}

    @staticmethod
    def relative(asset):
        if not asset or any(c in asset for c in ('\\', '%', '\x00', ':')):
            raise PublicationError('invalid_path')
        parts = asset.split('/')
        if any(not p or p.startswith('.') for p in parts):
            raise PublicationError('invalid_path')
        return PurePosixPath(asset)

    def asset(self, scope, version, asset):
        if scope not in ('public', 'private') or not VERSION.fullmatch(version):
            raise PublicationError('invalid_path')
        relative = self.relative(asset)
        expected = 'public-' if scope == 'public' else 'admin-'
        if relative.parts[0] == 'maps' and (len(relative.parts) < 3 or relative.parts[1] not in {prefix+d for prefix in (expected, expected.replace('-', '_')) for d,_ in DIMENSIONS}):
            raise PublicationError('wrong_scope')
        base = self.root / 'versions' / version / scope
        path = base.joinpath(*relative.parts)
        if not path.resolve().is_relative_to(base.resolve()) or path.is_symlink():
            raise PublicationError('invalid_path')
        if not path.is_file() and path.with_name(path.name + '.gz').is_file():
            path = path.with_name(path.name + '.gz')
        if not path.is_file():
            raise PublicationError('missing_asset')
        return path

    def _blob(self, source, destination, stats):
        if source.is_symlink() or not source.is_file():
            raise PublicationError('unsafe_source')
        before = source.stat()
        identity=[before.st_size,before.st_mtime_ns,before.st_ctime_ns,before.st_ino]
        cache_key=str(source.resolve())
        cached=self._source_index.get(cache_key,{})
        if cached.get('identity')==identity and re.fullmatch('[a-f0-9]{64}',str(cached.get('hash',''))):
            blob=self.root/'blobs'/cached['hash']
            if blob.is_file() and blob.stat().st_size==before.st_size:
                destination.parent.mkdir(parents=True,exist_ok=True)
                os.link(blob,destination)
                stats['reusedFiles']+=1;stats['files']+=1
                return
        if before.st_size > 256 * 1024**2:
            raise PublicationError('oversized_asset')
        if shutil.disk_usage(self.root).free < self.minimum_free + before.st_size * 2:
            raise PublicationError('low_disk_space')
        temp = self.root / 'blobs' / ('.' + uuid.uuid4().hex)
        hasher = hashlib.sha256()
        try:
            with source.open('rb') as reader, temp.open('xb') as writer:
                while block := reader.read(1024 * 1024):
                    hasher.update(block); writer.write(block);stats['sourceBytesRead']+=len(block)
                writer.flush(); os.fsync(writer.fileno())
            after = source.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise PublicationError('source_changed_during_copy')
            if source.name.endswith(('.json', '.json.gz')):
                opener = gzip.open if source.suffix == '.gz' else open
                with opener(temp, 'rt', encoding='utf-8') as file:
                    json.load(file)
            elif source.name.endswith('.gz'):
                with gzip.open(temp, 'rb') as file:
                    while file.read(1024 * 1024): pass  # Verify gzip CRC before promotion.
            blob = temp.parent / hasher.hexdigest()
            if blob.exists():
                stats['reusedFiles'] += 1
            else:
                if os.name != 'nt': os.chmod(temp, 0o440)
                os.replace(temp, blob)
                stats['newBytes'] += before.st_size
            destination.parent.mkdir(parents=True, exist_ok=True)
            os.link(blob, destination)
            stats['files'] += 1
            self._source_index[cache_key]={'identity':identity,'hash':hasher.hexdigest()}
        except (OSError, ValueError, EOFError) as error:
            raise PublicationError('invalid_asset') from error
        finally:
            temp.unlink(missing_ok=True)

    def publish(self, source, version, on_phase=lambda phase: None):
        if not VERSION.fullmatch(version):
            raise PublicationError('invalid_version')
        source = Path(source)
        self.root.mkdir(parents=True, exist_ok=True)
        (self.root / 'blobs').mkdir(exist_ok=True)
        stage = self.root / 'staging' / version
        if stage.exists():
            self._remove_generated(stage)
        stage.mkdir(parents=True)
        stats = dict(version=version, publishedAt=int(time.time()), files=0, newBytes=0, reusedFiles=0, sourceBytesRead=0, dimensions={})
        try:self._source_index=json.loads((self.root/'source-index.json').read_text(encoding='utf-8'))
        except (OSError,ValueError):self._source_index={}
        try:
            if shutil.disk_usage(self.root).free < self.minimum_free:
                raise PublicationError('low_disk_space')
            settings = json.loads((source / 'settings.json').read_text(encoding='utf-8'))
            if not (source / 'index.html').is_file() or not isinstance(settings.get('maps'), list):
                raise PublicationError('missing_webapp')
            raw_ids = [m if isinstance(m, str) else m.get('id') for m in settings['maps']]
            for scope, prefix in [('public', 'public-'), ('private', 'admin-')]:
                target = stage / scope; target.mkdir()
                ids = [candidate for d,_ in DIMENSIONS for candidate in (prefix+d, prefix.replace('-', '_')+d) if candidate in raw_ids]
                if not ids:
                    raise PublicationError('no_maps_in_scope')
                # Explicit viewer settings only: no scripts, external map roots or private IDs.
                safe = {k: v for k, v in settings.items() if k in {'version', 'resolutionDefault', 'minZoomDistance', 'maxZoomDistance', 'hiresSliderMax', 'hiresSliderDefault', 'hiresSliderMin', 'lowresSliderMax', 'lowresSliderDefault', 'lowresSliderMin'}}
                safe.update(maps=ids, mapDataRoot='maps', liveDataRoot='maps', useCookies=False, scripts=[], styles=[], clientDecompression=False, defaultToFlatView=True)
                atomic_json(target / 'settings.json', safe)
                for file in source.rglob('*'):
                    if not file.is_file():
                        if file.is_symlink(): raise PublicationError('unsafe_source')
                        continue
                    rel = file.relative_to(source)
                    if file.is_symlink(): raise PublicationError('unsafe_source')
                    if any(p.startswith('.') for p in rel.parts): continue
                    if rel.parts[0] == 'maps':
                        if len(rel.parts) < 3 or rel.parts[1] not in ids: continue
                        if 'live' in rel.parts or file.name in ('players.json', 'markers.json', 'players.json.gz', 'markers.json.gz'): continue
                    elif rel.as_posix() == 'settings.json':
                        continue
                    elif rel.parts[0] not in ('assets', 'lang', 'index.html', 'favicon.png', 'favicon.ico', 'manifest.webmanifest', 'robots.txt'):
                        continue
                    if stats['files'] > 1000000: raise PublicationError('asset_limit')
                    self._blob(file, target / rel, stats)
                for map_id in ids:
                    metadata = target / 'maps' / map_id / 'settings.json'
                    if not metadata.is_file(): raise PublicationError('missing_map_metadata')
                    content = json.loads(metadata.read_text(encoding='utf-8'))
                    if not isinstance(content, dict): raise PublicationError('invalid_map_metadata')
                stats['dimensions'][scope] = ids
            on_phase('validating')
            atomic_json(stage / 'manifest.json', stats)
            (self.root / 'versions').mkdir(exist_ok=True)
            final = self.root / 'versions' / version
            if final.exists(): raise PublicationError('version_exists')
            os.replace(stage, final)
            on_phase('publishing')
            previous = self.current()
            if previous: atomic_json(self.root / 'previous.json', previous)
            atomic_json(self.root / 'current.json', stats)
            atomic_json(self.root / 'source-index.json', self._source_index)
            return stats
        except PublicationError:
            raise
        except (OSError, ValueError, KeyError) as error:
            raise PublicationError('invalid_publication') from error
        finally:
            if stage.exists(): self._remove_generated(stage)

    def cleanup(self, keep=2):
        """Delete only expired immutable publications, then unreachable immutable blobs."""
        versions = self.root / 'versions'
        current = self.current().get('version')
        if not versions.is_dir(): return
        ordered = sorted((p for p in versions.iterdir() if p.is_dir() and VERSION.fullmatch(p.name)), key=lambda p:p.stat().st_mtime, reverse=True)
        protected = {p.name for p in ordered[:max(2, keep)]} | {current}
        try: protected.add(json.loads((self.root/'previous.json').read_text())['version'])
        except (OSError,ValueError,KeyError): pass
        for path in ordered:
            if path.name not in protected: self._remove_generated(path)
        for blob in (self.root / 'blobs').iterdir():
            if re.fullmatch('[a-f0-9]{64}',blob.name) and blob.stat().st_nlink == 1:
                blob.unlink()


class MapJobs:
    def __init__(self, database, settings):
        self.database, self.settings = database, settings

    def config(self):
        return dict(DEFAULTS, **self.database.setting('map.settings', {}))

    def status(self):
        with self.database.transaction() as db:
            row = db.execute('SELECT * FROM cm_site_map_jobs ORDER BY created_s DESC,id DESC LIMIT 1').fetchone()
            success = db.execute("SELECT * FROM cm_site_map_jobs WHERE status='completed' ORDER BY updated_s DESC LIMIT 1").fetchone()
        return dict(settings=self.config(), job=dict(row) if row else None,
                    lastSuccessAt=success['updated_s'] if success else None,
                    nextScheduledAt=self.database.setting('map.next_due', 0) if self.config()['autoRefresh'] else None,
                    markerCapability=False)

    def configure(self, data, actor):
        data=dict(data)
        reason=data.pop('reason','Изменение настроек карты')
        if not isinstance(reason,str) or not 5 <= len(reason.strip()) <= 500: fail(400,'reason_required','Укажите причину изменения настроек карты.')
        if set(data) - set(DEFAULTS): fail(400,'map_settings','Неизвестный параметр карты.')
        config = self.config()
        for key in ('enabled','autoRefresh','showPlayers','perspective','firstPerson'):
            if key in data and type(data[key]) is not bool: fail(400,'map_settings','Ожидался переключатель.')
        for key, low, high in [('intervalMinutes',60,10080),('minAgeMinutes',0,1440),('positionDelaySeconds',0,3600)]:
            if key in data and (type(data[key]) is not int or not low <= data[key] <= high): fail(400,'map_settings','Проверьте интервал: от 60 минут до 7 дней; задержка публикации — до суток.')
        if data.get('showPlayers'): fail(409,'markers_unavailable','Метки игроков отключены до проверки vanish и приватности на сервере.')
        config.update(data)
        self.database.set_setting('map.settings',config,actor['id'])
        if 'intervalMinutes' in data or 'autoRefresh' in data:
            self.database.set_setting('map.next_due',int(time.time())+config['intervalMinutes']*60,actor['id'])
        self.database.audit(actor,'map.settings',reason=reason.strip(),details=config)
        return config

    def enqueue(self, actor, key, reason, now=None):
        if not isinstance(key,str) or not re.fullmatch(r'[A-Za-z0-9:_-]{8,120}',key): fail(400,'idempotency_key','Обновите страницу и повторите запрос.')
        if not isinstance(reason,str) or not 5 <= len(reason.strip()) <= 500: fail(400,'reason_required','Укажите причину обновления карты (5–500 символов).')
        now = int(time.time()) if now is None else now
        with self.database.transaction() as db:
            if not self.database.sqlite: db.execute('SELECT pg_advisory_xact_lock(718204631)')
            old = db.execute('SELECT * FROM cm_site_map_jobs WHERE actor_id=%s AND request_key=%s',(actor['id'],key)).fetchone()
            if old: return dict(old),True
            active = db.execute("SELECT * FROM cm_site_map_jobs WHERE status IN ('queued','waiting_for_resources','running','validating','publishing') ORDER BY created_s LIMIT 1").fetchone()
            if active: return dict(active),True
            job=dict(id=uuid.uuid4().hex,actor_id=actor['id'],actor_name=actor['name'],reason=reason.strip(),request_key=key,status='queued',created_s=now,updated_s=now,error='',result='{}')
            db.execute('INSERT INTO cm_site_map_jobs(id,actor_id,actor_name,reason,request_key,status,created_s,updated_s) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)',tuple(job[k] for k in ('id','actor_id','actor_name','reason','request_key','status','created_s','updated_s')))
        self.database.audit(actor,'map.request',job['id'],reason,{'jobId':job['id'],'status':'queued'})
        return job,False

    def schedule(self, now=None):
        now=int(time.time()) if now is None else now
        config=self.config()
        if not config['autoRefresh']: return None
        # Serialize schedule ownership with the same database advisory lock as enqueue.
        due=self.database.setting('map.next_due',0)
        if not due:
            self.database.set_setting('map.next_due',now+config['intervalMinutes']*60,'scheduler');return None
        if now < due: return None
        actor={'id':'scheduler','name':'Расписание'}
        job,duplicate=self.enqueue(actor,'schedule:'+str(due),'Обновление по расписанию',now)
        self.database.set_setting('map.next_due',now+config['intervalMinutes']*60,'scheduler')
        return None if duplicate else job

    def phase(self, job, status, error='', result=None):
        with self.database.transaction() as db:
            db.execute('UPDATE cm_site_map_jobs SET status=%s,updated_s=%s,error=%s,result=%s WHERE id=%s',(status,int(time.time()),error,encoded(result or {}),job['id']))


def map_service(request):
    return MapJobs(request.app.state.database,request.app.state.settings)


@router.get('/api/v6/map')
def public_metadata(request: Request):
    settings=request.app.state.settings
    config=map_service(request).config()
    current=MapStorage(settings.map_root).current()
    ids=current.get('dimensions',{}).get('public',[]) if config['enabled'] else []
    version=current.get('version')
    return dict(enabled=config['enabled'],players=[],publishedAt=current.get('publishedAt'),version=version,
                perspective=bool(config['perspective']),firstPerson=bool(config['firstPerson']),
                dimensions=[dict(id=d,name=name,available=any(x+d in ids for x in ('public-','public_'))) for d,name in DIMENSIONS],
                viewerUrl=f'/maps/public/{version}/index.html' if version and config['enabled'] else None,
                privacy='Показаны чанки с сохранённым временем присутствия игроков поблизости. Это общая освоенная область сервера.')


@router.get('/api/v6/admin/map')
def admin_metadata(request: Request):
    current_user(request,'map.view')
    current=MapStorage(request.app.state.settings.map_root).current()
    version=current.get('version')
    return dict(map_service(request).status(),publication=current,
                viewerUrl=f'/maps/private/{version}/index.html' if version else None)


@router.post('/api/v6/admin/map/settings')
async def configure_map(request: Request):
    actor=current_user(request,'map.manage',fresh=True)
    return dict(settings=map_service(request).configure(await request.json(),actor),ok=True)


@router.post('/api/v6/admin/map/refresh',status_code=202)
async def refresh_map(request: Request):
    actor=current_user(request,'map.manage',fresh=True)
    data=await request.json()
    throttle(request.app.state.database,'map-refresh:'+actor['id'],30,60)
    job,duplicate=map_service(request).enqueue(actor,data.get('key'),data.get('reason'))
    return dict(job=job,duplicate=duplicate,message='Обновление уже выполняется.' if duplicate else 'Поставлено в очередь.')


@router.get('/maps/{scope}/{version}/{asset:path}')
def map_asset(scope: str, version: str, asset: str, request: Request):
    if scope == 'private': current_user(request,'map.view')
    elif scope != 'public': fail(404,'not_found','Карта не найдена.')
    config=map_service(request).config()
    if scope == 'public' and not config['enabled']: fail(404,'map_disabled','Публичная карта отключена.')
    # Live streams, player positions and markers are unavailable in immutable maps.
    if '/live/' in '/'+asset or asset.endswith(('/players.json','/markers.json')):
        return Response(status_code=204,headers={'Cache-Control':'no-store'})
    try: path=MapStorage(request.app.state.settings.map_root).asset(scope,version,asset)
    except PublicationError as error:
        if str(error)=='missing_asset' and '/tiles/' in asset:
            return Response(status_code=204,headers={'Cache-Control':'private, no-store' if scope=='private' else 'public, max-age=31536000, immutable'})
        fail(404,'not_found','Файл карты не найден.')
    # Publication flags are applied per request so the admin toggles work without a re-render.
    if scope == 'public' and path.name == 'settings.json':
        try: metadata = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, ValueError): fail(404,'not_found','Файл карты не найден.')
        if not isinstance(metadata, dict): fail(404,'not_found','Файл карты не найден.')
        if asset == 'settings.json':
            metadata['defaultToFlatView'] = not bool(config['perspective'])
        else:
            metadata['perspectiveView'] = bool(config['perspective'])
            metadata['freeFlightView'] = bool(config['firstPerson'])
            metadata['flatView'] = True
            # В Незере и Энде нет небесного света, поэтому при ambientLight 0 карта чёрная.
            if asset.split('/')[1].endswith(('_nether', '_end')):
                metadata['ambientLight'] = max(float(metadata.get('ambientLight') or 0), 0.7)
        return JSONResponse(metadata, headers={'Cache-Control':'no-store','X-Frame-Options':'SAMEORIGIN'})
    headers={'Cache-Control':'private, no-store' if scope=='private' else 'public, max-age=31536000, immutable',
             'X-Frame-Options':'SAMEORIGIN','Content-Security-Policy':"default-src 'self' data: blob:; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'self'"}
    name=path.name
    if name.endswith('.gz'):
        headers['Content-Encoding']='gzip';name=name[:-3]
    media=mimetypes.guess_type(name)[0] or ('application/octet-stream' if name.endswith('.prbm') else None)
    return FileResponse(path,media_type=media,headers=headers)
