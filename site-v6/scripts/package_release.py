#!/usr/bin/env python3
"""Package the completed site and backend source; exclude private runtime and raw worlds.

Run after the final build and verification. The output ZIP must be outside the project.
The release contains seed catalogs, never the running service's database or credentials.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

EXCLUDED_DIRS = {'.git', '__pycache__', 'node_modules', '.venv', 'venv', 'runtime',
                 'private-staging', 'sessions', 'playerdata', 'world', 'world_nether',
                 'world_the_end', 'local-worlds', 'recovery', 'copimine-site-v6-recovery'}
EXCLUDED_SUFFIXES = ('.sqlite', '.sqlite3', '.db', '.db3', '.pem', '.key', '.p12', '.pfx',
                     '.mca', '.mcc', '.dat', '.dat_old', '.tar', '.tar.gz', '.tgz', '.zip',
                     '.b64', '.log', '.pid', '.pyc', '.pyo', '.bak', '.dump', '.tmp')
PRIVATE_SEEDS = {'catalog.json', 'materials.json', 'admin-actions.json'}
STORED_SUFFIXES = {'.png', '.webp', '.jpg', '.jpeg', '.gif', '.woff2'}


def exclusion(relative):
    parts = tuple(part.lower() for part in relative.parts)
    name = parts[-1]
    if parts[:3] == ('docs', 'qa-v6', 'screenshots'):
        return 'internal-browser-screenshots'
    if any(part in EXCLUDED_DIRS or part.endswith('.render-checkpoints')
           or part.startswith('copimine-v6-state') for part in parts):
        return 'runtime-or-world-directory'
    if name.startswith('.env') and name != '.env.example':
        return 'environment-credentials'
    if name.endswith(EXCLUDED_SUFFIXES) or any(token in name for token in ('.sqlite-', '.db-', '.sqlite3-')):
        return 'runtime-binary-log-or-archive'
    if relative.as_posix() in ('data/prototype.sql', 'release-manifest.json') or (name.startswith('copimine_prototype_v') and name.endswith('.html')):
        return 'obsolete-generated-artifact'
    if len(parts) >= 3 and parts[:2] == ('server', 'private'):
        if len(parts) == 3 and name in PRIVATE_SEEDS:
            return None
        if len(parts) >= 4 and parts[2] == 'icons' and relative.suffix.lower() == '.png':
            return None
        return 'private-file-outside-seed-allowlist'
    return None


def inventory(root):
    files = []
    excluded = Counter()
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        reason = exclusion(relative)
        if reason:
            if path.is_file() or path.is_symlink():
                excluded[reason] += 1
            continue
        if path.is_symlink():
            raise ValueError('Symlinks are not packaged: ' + relative.as_posix())
        if path.is_file():
            files.append((relative, path))
    return files, excluded


def snapshot(path):
    before = path.stat()
    raw = path.read_bytes()
    after = path.stat()
    if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
        raise RuntimeError('A project file changed during packaging; rerun after editing is complete: ' + path.name)
    if len(raw) != after.st_size:
        raise RuntimeError('Incomplete package input: ' + path.name)
    return raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path)
    parser.add_argument('--check-only', action='store_true', help='Verify the file set without writing a ZIP')
    parser.add_argument('--folder', default='CopiMine_Studio_v6')
    parser.add_argument('--source-revision', default=None, help='Optional verified source revision for provenance')
    args = parser.parse_args()
    root = args.root.resolve()
    output = (args.output or root.parent / 'CopiMine_Site_v6.zip').resolve()
    if output.is_relative_to(root):
        parser.error('--output must be outside the project directory')
    if Path(args.folder).name != args.folder or args.folder in ('.', '..') or '/' in args.folder or '\\' in args.folder:
        parser.error('--folder must be a single directory name')
    for required in ('index.html', 'START.bat', 'data/map-manifest.json', 'data/materials-v6.json'):
        if not (root / required).is_file():
            parser.error('Required release file is missing: ' + required)
    files, excluded = inventory(root)
    records = []
    stamp = datetime.fromtimestamp(int(os.environ.get('SOURCE_DATE_EPOCH', datetime.now().timestamp())), timezone.utc)
    zip_stamp = (max(1980, min(2107, stamp.year)), stamp.month, stamp.day, stamp.hour, stamp.minute, stamp.second)
    temporary = None
    archive = None
    try:
        if not args.check_only:
            output.parent.mkdir(parents=True, exist_ok=True)
            descriptor, temporary = tempfile.mkstemp(prefix=output.name + '.', suffix='.tmp', dir=output.parent)
            os.close(descriptor)
            archive = zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6)
        for relative, path in files:
            raw = snapshot(path)
            records.append({'path': relative.as_posix(), 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})
            if archive:
                info = zipfile.ZipInfo(args.folder + '/' + relative.as_posix(), date_time=zip_stamp)
                info.compress_type = zipfile.ZIP_STORED if path.suffix.lower() in STORED_SUFFIXES else zipfile.ZIP_DEFLATED
                info.external_attr = (0o100755 if path.suffix.lower() == '.sh' else 0o100644) << 16
                archive.writestr(info, raw)
        map_manifest = json.loads((root / 'data/map-manifest.json').read_text(encoding='utf-8'))
        release = {'schemaVersion': 1, 'createdAt': stamp.isoformat(), 'sourceRevision': args.source_revision,
                   'files': records, 'fileCount': len(records), 'sourceBytes': sum(r['bytes'] for r in records),
                   'mapSourceSHA256': map_manifest['source']['sha256'],
                   'excludedByPolicy': dict(excluded),
                   'privateSeedFilesAllowed': sorted(PRIVATE_SEEDS),
                   'policy': 'No runtime database, sessions, credentials, private capture staging or raw world files.'}
        if archive:
            archive.writestr(args.folder + '/release-manifest.json', json.dumps(release, ensure_ascii=False, indent=2) + '\n')
            archive.close()
            archive = None
            with zipfile.ZipFile(temporary) as check:
                damaged = check.testzip()
                if damaged:
                    raise RuntimeError('ZIP verification failed: ' + damaged)
                if len(check.infolist()) != len(records) + 1:
                    raise RuntimeError('ZIP file inventory is incomplete')
            Path(temporary).replace(output)
            temporary = None
        summary = {key: release[key] for key in ('fileCount', 'sourceBytes', 'excludedByPolicy', 'mapSourceSHA256')}
        summary['mode'] = 'check-only' if args.check_only else 'written'
        if not args.check_only:
            with output.open('rb') as handle:
                summary.update({'zip': str(output), 'zipBytes': output.stat().st_size,
                                'zipSHA256': hashlib.file_digest(handle, 'sha256').hexdigest()})
        print(json.dumps(summary, ensure_ascii=False, indent=2))
    finally:
        if archive:
            archive.close()
        if temporary:
            Path(temporary).unlink(missing_ok=True)


if __name__ == '__main__':
    main()
