"""Ensure a distributable archive cannot absorb runtime credentials or worlds."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/package_release.py'


class ReleasePackageTests(unittest.TestCase):
    def test_archive_keeps_seed_catalogs_and_excludes_runtime(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = base / 'site'
            keep = {'index.html': '<html>Ready</html>', 'START.bat': '@echo off',
                    'data/materials-v6.json': '[]',
                    'data/map-manifest.json': json.dumps({'source': {'sha256': 'verified-terrain'}}),
                    'server/private/catalog.json': '[]', 'server/private/materials.json': '[]',
                    'server/private/admin-actions.json': '[]', 'server/private/icons/example.png': 'public-icon',
                    'server/http.py': '# source code', '.env.example': 'SERVER_KEY=', 'data/schema.sql': '-- schema'}
            forbidden = ['CopiMine_Prototype_v5.html', '.env', '.env.production', 'data/state.sqlite', 'data/state.sqlite-wal',
                         'server/private/unapproved.json', 'server/private/icons/private.key',
                         'private-staging/data/map-manifest.json', 'world/region/r.0.0.mca',
                         'copimine-v6-state/sessions.json', 'data/prototype.sql', 'source.tar.gz',
                         'revision.render-checkpoints/render-errors.json']
            secret = 'SHOULD_NOT_LEAVE_PRIVATE_RUNTIME_97131'
            for name, content in {**keep, **dict.fromkeys(forbidden, secret)}.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            output = base / 'release.zip'
            run = subprocess.run([sys.executable, str(SCRIPT), '--root', str(root), '--output', str(output)],
                                 capture_output=True, text=True, timeout=30)
            self.assertEqual(run.returncode, 0, run.stderr)
            with zipfile.ZipFile(output) as archive:
                prefix = 'CopiMine_Studio_v6/'
                self.assertEqual(set(archive.namelist()), {prefix + p for p in keep} | {prefix + 'release-manifest.json'})
                for entry in archive.namelist():
                    self.assertNotIn(secret.encode(), archive.read(entry))
                release = json.loads(archive.read(prefix + 'release-manifest.json'))
                self.assertEqual(release['fileCount'], len(keep))
                self.assertEqual(sum(release['excludedByPolicy'].values()), len(forbidden))
                self.assertEqual(release['mapSourceSHA256'], 'verified-terrain')
                self.assertEqual(len(release['files']), len(keep))


if __name__ == '__main__':
    unittest.main()
