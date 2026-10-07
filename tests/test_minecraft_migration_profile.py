"""Wrong launcher versions must be rejected before any downloads or writes."""
from pathlib import Path
import json
import shutil
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/minecraft/InstallMigrationProfile.ps1'

@pytest.mark.parametrize('sha1,loader,accepted', [
    ('wrong', '0.19.5', False),
    ('e877b6a07acd633fb3bb475002175cec036e7b87', '0.19.3', False),
    ('e877b6a07acd633fb3bb475002175cec036e7b87', '0.19.5', True),
])
def test_installation_is_bound_to_exact_client_and_loader(tmp_path, sha1, loader, accepted):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell:
        pytest.skip('PowerShell profile validation requires a Windows verification host')
    profile = tmp_path / 'Copimine'
    profile.mkdir()
    definition = {'downloads': {'client': {'sha1': sha1}},
                  'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
                  'javaVersion': {'majorVersion': 25},
                  'libraries': [{'name': 'net.fabricmc:fabric-loader:' + loader}]}
    path = profile / 'Copimine.json'
    original = json.dumps(definition).encode()
    path.write_bytes(original)
    result = subprocess.run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(SCRIPT),
                             '-ProfileDirectory', str(profile), '-DependenciesOnly', '-ValidateOnly'],
                            capture_output=True, text=True, timeout=30)
    assert (result.returncode == 0) == accepted, result.stdout + result.stderr
    assert list(profile.iterdir()) == [path]
    assert path.read_bytes() == original
