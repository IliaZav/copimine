"""Wrong launcher versions must be rejected before any downloads or writes."""
from pathlib import Path
import io
import json
import hashlib
import os
import re
import shutil
import subprocess
import zipfile
import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/minecraft/InstallMigrationProfile.ps1'
LEGACY_PACK_SHA256 = 'a' * 64
CLIENT_SHA1 = 'e877b6a07acd633fb3bb475002175cec036e7b87'
PINNED_DOWNLOAD_HELPERS = ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1'


def test_migration_gradle_pin_is_compatible_with_java_25_and_security_patch():
    profile = json.loads((ROOT / 'tools/minecraft-26.3/profile.lock.json').read_text(encoding='utf-8'))
    gradle = profile['gradle']

    assert gradle['version'] == '9.8.1'
    assert gradle['url'] == 'https://services.gradle.org/distributions/gradle-9.8.1-bin.zip'
    assert gradle['sha256'] == 'dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f'
    assert gradle['size'] == 151_620_101


def test_gitlab_and_github_share_the_pinned_java_plugin_release_gate():
    gitlab=(ROOT/'.gitlab-ci.yml').read_text(encoding='utf-8')
    github=(ROOT/'.github/workflows/ci.yml').read_text(encoding='utf-8')
    release_gate=ROOT/'scripts/minecraft/RunJavaPluginCi.ps1'
    release_gate_source=release_gate.read_text(encoding='utf-8') if release_gate.is_file() else ''

    assert release_gate.is_file()
    assert 'RunJavaPluginCi.ps1' in gitlab
    assert 'RunJavaPluginCi.ps1' in github
    assert "21.0.12.1" in gitlab and "21.0.12.1" in github and "21.0.12.1" in release_gate_source
    assert "25.0.4.1" in github and "25.0.4.1" in gitlab
    assert "python-3.13.16-amd64.exe" in gitlab and "3.13.16" in github
    assert 'Python 3\\.13\\.16' in release_gate_source
    assert 'm2-repository' in release_gate_source
    assert 'fb4f9f5d438b2396da0086dc70b935c530cb578e37adc6d354f7ad2037fee83b' in gitlab
    assert 'java-plugin-compile-dependencies.lock.json' in release_gate_source

    postgres_job = gitlab.split('migration-postgres-contract:', 1)[1].split('\nminecraft-26-3-migration:', 1)[0]
    assert 'image: python:3.13-slim' in postgres_job
    assert 'apt-get install --yes --no-install-recommends git' in postgres_job
    assert 'git rev-parse HEAD' in postgres_job


def test_client_mod_lock_ids_are_explicit_and_candidate_artifacts_are_checked():
    lock = json.loads((ROOT / 'tools/minecraft-26.3/client-mods.lock.json').read_text(encoding='utf-8'))
    candidate_builder = (ROOT / 'scripts/minecraft/PrepareMigrationCandidates.ps1').read_text(encoding='utf-8')
    profile_installer = SCRIPT.read_text(encoding='utf-8')
    module_ids = [module['modId'] for module in lock['modules']]

    assert lock['schemaVersion'] == 1
    assert all(re.fullmatch(r'[a-z0-9_.-]{1,64}', mod_id) for mod_id in module_ids)
    assert len(module_ids) == len(set(module_ids))
    assert all(module.get('fabricMetadataVersion') for module in lock['modules'])
    assert set(module_ids) == {
        'appleskin', 'betterf3', 'cloth-config', 'customskinloader-bootstrap', 'emotecraft',
        'fabric-api', 'fpsreducer', 'iris', 'journeymap', 'mc-armor-hud',
        'player_animation_library', 'sodium', 'voicechat',
    }
    assert "$modMetadata.id -cne $clientMod.modId" in candidate_builder
    assert "$modMetadata.version -cne $clientMod.fabricMetadataVersion" in candidate_builder
    assert "$modMetadata.id -cne $module.modId" in profile_installer
    assert "$modMetadata.version -cne $lockedFabricVersion" in profile_installer
    assert "$clientMod.url -cnotin $trustedForgeCdnSources" in candidate_builder
    assert '9021/707/mc-armor-hud-8.1.1.6-26.3-fabric.jar' in candidate_builder


def test_incomplete_profile_rollback_reports_original_and_rollback_errors():
    source = SCRIPT.read_text(encoding='utf-8')

    assert "Original migration error: ' + $originalError.Message" in source
    assert "Recovery snapshots remain at ' +\n            $transactionRoot" in source
    assert "Rollback errors: ' + ($rollbackErrors -join '; ')" in source


def test_shared_java_gate_uses_its_pinned_bounded_compile_dependencies():
    release_gate=(ROOT/'scripts/minecraft/RunJavaPluginCi.ps1').read_text(encoding='utf-8')
    event_gate=(ROOT/'tests/RunEndRiftEventChecks.ps1').read_text(encoding='utf-8')

    assert '$env:COPIMINE_MAVEN_REPOSITORY = $mavenRepository' in release_gate
    assert '$env:PAPER_COMPILE_DEPS = $compileClasspath' in release_gate
    assert '$compileClasspath.Length -gt 16000' in release_gate
    assert "DownloadPinnedArtifact.ps1'" in release_gate
    assert 'java-plugin-compile-dependencies.lock.json' in release_gate
    assert '$dependencySha256 = (Get-FileHash -Algorithm SHA256' in release_gate
    assert 'Save-PinnedArtifact -Uri $artifactUri' in release_gate
    assert '$compileLock.artifactRepository' in release_gate
    assert '$env:PAPER_PLACEHOLDER_API_JAR = $placeholderCandidate' in release_gate
    assert '$placeholder = $env:PAPER_PLACEHOLDER_API_JAR' in (ROOT/'copimine-admin-plugin/build-plugin.ps1').read_text(encoding='utf-8')
    assert '$env:PAPER_VOICECHAT_API_JAR = $voicechatCandidate' in release_gate
    assert '$env:COPIMINE_MAVEN_REPOSITORY' in event_gate


def test_paper_26_3_end_event_source_set_excludes_deferred_gameplay_protocol_policy():
    gradle = (ROOT/'tools/minecraft-26.3/build.gradle').read_text(encoding='utf-8')

    assert "sourceSets[name].java.srcDir(new File(checkout, 'tools/minecraft-26.3/protocol/src/main/java'))" not in gradle


def test_java_compile_dependency_lock_pins_exact_classpath_content_and_order():
    lock=json.loads((ROOT/'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json').read_text(encoding='utf-8'))
    dependencies=lock['dependencies']
    paths=[entry['path'] for entry in dependencies]

    assert lock['schemaVersion']==1
    assert len(dependencies)==18
    assert len(paths)==len(set(paths))
    assert all(path.endswith('.jar') and not path.startswith('/') and '..' not in path.split('/') for path in paths)
    assert all(entry['size']>0 and len(entry['sha256'])==64 and entry['sha256']==entry['sha256'].lower() for entry in dependencies)


def test_java_plugin_build_scripts_prefer_the_isolated_maven_repository():
    scripts=[
        'copimine-world-core/build-plugin.ps1',
        'copimine-artifacts/build-plugin.ps1',
        'copimine-end-event/build-plugin.ps1',
        'copimine-economy-core/build-plugin.ps1',
        'copimine-election-core/build-plugin.ps1',
        'copimine-narcotics/build-plugin.ps1',
        'copimine-admin-plugin/build-plugin.ps1',
        'minecraft/server/plugins/AuthEffects/build-plugin.ps1',
    ]
    for relative in scripts:
        source=(ROOT/relative).read_text(encoding='utf-8')
        assert '$mavenRepo = if ($env:COPIMINE_MAVEN_REPOSITORY)' in source, relative
        assert "Join-Path $env:USERPROFILE '.m2\\repository'" in source, relative
        locked_branch=source.index('if ($env:PAPER_COMPILE_DEPS)')
        assert source.index('$cp += $env:PAPER_COMPILE_DEPS', locked_branch) > locked_branch, relative
        fallback_branch=source.index('} else {', locked_branch)
        for ambient_marker in ("Get-ChildItem -Path $groupPath", "Get-ChildItem -Path (Join-Path $serverDir 'libraries')", "Get-ChildItem -Path $serverLibraries"):
            ambient_index=source.find(ambient_marker)
            if ambient_index >= 0:
                assert ambient_index > fallback_branch, (relative, ambient_marker)


def test_new_setup_java_action_reference_is_immutable():
    github=(ROOT/'.github/workflows/ci.yml').read_text(encoding='utf-8')

    assert 'actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961' in github


def test_atomic_text_write_keeps_existing_profile_options_on_replacement_failure(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if os.name != 'nt' or not shell:
        pytest.skip('Atomic replacement failure semantics require Windows PowerShell')

    destination = tmp_path / 'options.txt'
    destination.write_text('resourcePacks:["vanilla"]', encoding='utf-8')
    helper_path = str(PINNED_DOWNLOAD_HELPERS).replace("'", "''")
    destination_path = str(destination).replace("'", "''")
    script = f"""
. '{helper_path}'
$path = '{destination_path}'
$locked = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
$replacementFailed = $false
try {{
    if (-not (Get-Command -Name Save-TextFileAtomically -CommandType Function -ErrorAction SilentlyContinue)) {{
        throw 'Save-TextFileAtomically was not loaded'
    }}
    try {{
        Save-TextFileAtomically -Path $path -Content 'resourcePacks:["candidate"]' -Encoding ([Text.UTF8Encoding]::new($false))
    }} catch [System.Management.Automation.CommandNotFoundException] {{
        throw
    }} catch {{
        $replacementFailed = $true
    }}
}} finally {{ $locked.Dispose() }}
if (-not $replacementFailed) {{ throw 'Expected atomic replacement to fail while the destination is locked' }}
if ([IO.File]::ReadAllText($path) -cne 'resourcePacks:["vanilla"]') {{ throw 'Original options were changed after failed replacement' }}
$leftovers = @(Get-ChildItem -LiteralPath (Split-Path -Parent $path) -Filter 'options.txt.candidate.*.tmp')
if ($leftovers.Count -ne 0) {{ throw 'Temporary options file was not cleaned up' }}
"""
    result = subprocess.run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script],
                            capture_output=True, text=True, timeout=30)

    assert result.returncode == 0, result.stdout + result.stderr


def write_migration_pack(root):
    pack = root / 'build/minecraft-26.3/CopiMineResourcePack-26.3.zip'
    pack.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(pack, 'w') as archive:
        archive.writestr('pack.mcmeta', json.dumps({
            'pack': {'min_format': [97, 1], 'max_format': [97, 1]},
        }))
        archive.writestr('assets/copimine/manifests/minecraft_26_3_migration.json', json.dumps({
            'minecraftVersion': '26.3',
            'resourceFormat': [97, 1],
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'vanillaClientSha1': CLIENT_SHA1,
        }))
    return pack, hashlib.sha256(pack.read_bytes()).hexdigest()


def pin_test_client_artifact(migration, client_jar):
    lock_path = migration / 'profile.lock.json'
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    payload = client_jar.read_bytes()
    lock['clientArtifact'] = {
        'filename': client_jar.name,
        'size': len(payload),
        'sha512': hashlib.sha512(payload).hexdigest(),
    }
    lock_path.write_text(json.dumps(lock), encoding='utf-8')

@pytest.mark.parametrize('sha1,loader,accepted', [
    ('wrong', '0.19.5', False),
    ('e877b6a07acd633fb3bb475002175cec036e7b87', '0.19.3', False),
    ('e877b6a07acd633fb3bb475002175cec036e7b87', '0.19.5', True),
])
def test_installation_is_bound_to_exact_client_and_loader(tmp_path, sha1, loader, accepted):
    shell = shutil.which('pwsh') or shutil.which('powershell')
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
                             '-ProfileDirectory', str(profile), '-DependenciesOnly', '-ValidateDefinitionOnly'],
                            capture_output=True, text=True, timeout=30)
    assert (result.returncode == 0) == accepted, result.stdout + result.stderr
    assert list(profile.iterdir()) == [path]
    assert path.read_bytes() == original


def test_validation_modes_cannot_be_combined(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile validation requires a Windows verification host')
    profile = tmp_path / 'Copimine'
    profile.mkdir()
    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(SCRIPT),
        '-ProfileDirectory', str(profile), '-ValidateOnly', '-ValidateDefinitionOnly',
    ], capture_output=True, text=True, timeout=30)
    assert result.returncode != 0
    assert 'cannot be combined' in result.stdout + result.stderr
    assert list(profile.iterdir()) == []


@pytest.mark.parametrize('filename,url,project_id,version_id,accepted', [
    ('CustomSkinLoader_Universal-15.1.jar', 'https://edge.forgecdn.net/files/9023/814/CustomSkinLoader_Universal-15.1.jar', '286924', '9023814', True),
    ('mc-armor-hud-8.1.1.6-26.3-fabric.jar', 'https://edge.forgecdn.net/files/9021/707/mc-armor-hud-8.1.1.6-26.3-fabric.jar', '1155218', '9021707', True),
    ('mc-armor-hud-8.1.1.6-26.3-fabric.jar', 'https://edge.forgecdn.net/files/9021/708/mc-armor-hud-8.1.1.6-26.3-fabric.jar', '1155218', '9021708', False),
    ('CustomSkinLoader_Universal-15.1.jar', 'https://malicious.example/files/CustomSkinLoader_Universal-15.1.jar', '9023814', '9023814', False),
    ('FixtureMod-1.0.jar', 'https://cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar', 'A1b2C3d4', 'Z9y8X7w6', True),
    ('FixtureMod-1.0.jar', 'https://cdn.modrinth.com/data/E5f6G7h8/versions/Z9y8X7w6/FixtureMod-1.0.jar', 'A1b2C3d4', 'Z9y8X7w6', False),
    ('FixtureMod-1.0.jar', 'https://cdn.modrinth.com/data/A1b2C3d4/versions/J1k2L3m4/FixtureMod-1.0.jar', 'A1b2C3d4', 'Z9y8X7w6', False),
    ('FixtureMod-1.0.jar', 'https://cdn.modrinth.com.evil/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar', 'A1b2C3d4', 'Z9y8X7w6', False),
    ('FixtureMod-1.0.jar', 'https://cdn.modrinth.com:444/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar', 'A1b2C3d4', 'Z9y8X7w6', False),
    ('FixtureMod-1.0.jar', 'https://cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar?redirect=evil', 'A1b2C3d4', 'Z9y8X7w6', False),
    ('FixtureMod-1.0.jar', 'https://user@cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar', 'A1b2C3d4', 'Z9y8X7w6', False),
])
def test_mod_source_validation_accepts_only_pinned_mod_hosts(tmp_path, filename, url, project_id, version_id, accepted):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile validation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    minecraft = root / 'tools/minecraft-26.3'
    minecraft.mkdir(parents=True)
    _, candidate_sha256 = write_migration_pack(root)
    (minecraft / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': 'e877b6a07acd633fb3bb475002175cec036e7b87'},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': False},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': candidate_sha256,
        },
    }), encoding='utf-8')
    source_module = {
        'filename': filename,
        'url': url,
        'projectId': project_id,
        'versionId': version_id,
        'sha512': 'a' * 128,
        'size': 1,
    }
    if filename == 'CustomSkinLoader_Universal-15.1.jar':
        source_module['fileId'] = version_id
    (minecraft / 'client-mods.lock.json').write_text(json.dumps({'modules': [source_module]}), encoding='utf-8')

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    definition = {
        'downloads': {'client': {'sha1': 'e877b6a07acd633fb3bb475002175cec036e7b87'}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }
    (profile / 'Copimine.json').write_text(json.dumps(definition), encoding='utf-8')

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-DependenciesOnly', '-ValidateDefinitionOnly',
    ], capture_output=True, text=True, timeout=30)

    assert (result.returncode == 0) == accepted, result.stdout + result.stderr
    assert list(profile.iterdir()) == [profile / 'Copimine.json']


@pytest.mark.parametrize('initial_resource_packs', [['vanilla'], []])
def test_profile_install_activates_migration_pack_and_preserves_other_options(tmp_path, initial_resource_packs):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    pack, candidate_sha256 = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': 'e877b6a07acd633fb3bb475002175cec036e7b87'},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': True},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': candidate_sha256,
        },
    }), encoding='utf-8')
    module_buffer = io.BytesIO()
    with zipfile.ZipFile(module_buffer, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'fixturemod', 'version': '1.0'}))
    module_bytes = module_buffer.getvalue()
    module_filename = 'FixtureMod-1.0.jar'
    module_sha512 = hashlib.sha512(module_bytes).hexdigest()
    (migration / 'client-mods.lock.json').write_text(json.dumps({'schemaVersion': 1, 'modules': [{
        'filename': module_filename,
        'modId': 'fixturemod',
        'fabricMetadataVersion': '1.0',
        'projectId': 'A1b2C3d4',
        'versionId': 'Z9y8X7w6',
        'url': 'https://cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar',
        'sha512': module_sha512,
        'size': len(module_bytes),
    }]}), encoding='utf-8')
    module_stage = root / 'build/minecraft-26.3/client-mods'
    module_stage.mkdir(parents=True)
    (module_stage / module_filename).write_bytes(module_bytes)

    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.1+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient',
            'version': '0.1.1+26.3',
            'depends': {'minecraft': '26.3'},
            'entrypoints': {'client': ['me.copimine.client.CopiMineClient']},
            'mixins': ['copimineclient-26.3.mixins.json'],
        }))
        archive.writestr('copimineclient-26.3.mixins.json', json.dumps({
            'package': 'me.copimine.client.mixin',
            'client': [],
        }))
        archive.writestr('me/copimine/client/CopiMineClient.class', b'fixture entrypoint')
        archive.writestr('me/copimine/client/ClientBridgeProtocol.class', b'fixture bridge')
        archive.writestr('me/copimine/client/BridgePayload.class', b'fixture payload')
    pin_test_client_artifact(migration, client_jar)
    profile = tmp_path / 'Copimine'
    profile.mkdir()
    (profile / 'Copimine.json').write_text(json.dumps({
        'downloads': {'client': {'sha1': 'e877b6a07acd633fb3bb475002175cec036e7b87'}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    original_options = [
        'soundCategory_music:0.25',
        'resourcePacks:' + json.dumps(initial_resource_packs, separators=(',', ':')),
        'incompatibleResourcePacks:["file/CopiMineResourcePack-26.3.zip","file/OtherOldPack.zip"]',
    ]
    (profile / 'options.txt').write_text('\n'.join(original_options) + '\n', encoding='utf-8')
    malformed_mods = profile / 'mods'
    malformed_mods.mkdir()
    for filename, metadata in (
        ('InvalidUtf8.jar', b'\xff'),
        ('OversizedMetadata.jar', b' ' * (256 * 1024 + 1)),
        ('MalformedJson.jar', b'{not-json'),
    ):
        with zipfile.ZipFile(malformed_mods / filename, 'w') as archive:
            archive.writestr('fabric.mod.json', metadata)
    old_module_filename = 'FixtureMod-0.9.jar'
    old_module_buffer = io.BytesIO()
    with zipfile.ZipFile(old_module_buffer, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'fixturemod', 'version': '0.9'}))
    old_module_bytes = old_module_buffer.getvalue()
    (malformed_mods / old_module_filename).write_bytes(old_module_bytes)

    receipt_alias = tmp_path / 'receipt-hardlink-sentinel.json'
    receipt_alias.write_text('preserve linked file contents', encoding='utf-8')
    os.link(receipt_alias, profile / 'copimine-migration-receipt.json')

    original_client_jar = client_jar.read_bytes()
    client_jar.write_bytes(original_client_jar + b'tampered')
    rejected_client = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)
    assert rejected_client.returncode != 0, rejected_client.stdout + rejected_client.stderr
    assert 'pinned build artifact' in rejected_client.stdout + rejected_client.stderr
    assert (profile / 'options.txt').read_text(encoding='utf-8').splitlines() == original_options
    assert receipt_alias.read_text(encoding='utf-8') == 'preserve linked file contents'
    assert not (profile / 'mods' / module_filename).exists()
    assert not (profile / 'resourcepacks').exists()
    client_jar.write_bytes(original_client_jar)

    installer_source = SCRIPT.read_text(encoding='utf-8')
    receipt_start = installer_source.index("Save-TextFileAtomically -Path $migrationReceiptPath")
    receipt_end = installer_source.index(" -StopAt $target", receipt_start) + len(" -StopAt $target")
    failed_installer = (installer_source[:receipt_start] +
                        "throw 'simulated migration receipt publication failure'" +
                        installer_source[receipt_end:])
    script.write_text(failed_installer, encoding='utf-8')
    original_options_bytes = (profile / 'options.txt').read_bytes()
    failed_commit = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)
    assert failed_commit.returncode != 0, failed_commit.stdout + failed_commit.stderr
    assert 'simulated migration receipt publication failure' in failed_commit.stdout + failed_commit.stderr
    assert (profile / 'options.txt').read_bytes() == original_options_bytes
    assert (profile / 'copimine-migration-receipt.json').read_text(encoding='utf-8') == 'preserve linked file contents'
    assert (profile / 'mods' / old_module_filename).read_bytes() == old_module_bytes
    assert not (profile / 'mods' / module_filename).exists()
    assert not (profile / 'mods' / 'CopiMineClient-0.1.1+26.3.jar').exists()
    assert not (profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip').exists()
    assert not (profile / 'migration-backups').exists()

    script.write_bytes(SCRIPT.read_bytes())

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode == 0, result.stdout + result.stderr
    options = (profile / 'options.txt').read_text(encoding='utf-8').splitlines()
    active_packs = json.loads(next(line.split(':', 1)[1] for line in options if line.startswith('resourcePacks:')))
    incompatible_packs = json.loads(next(
        line.split(':', 1)[1] for line in options if line.startswith('incompatibleResourcePacks:')
    ))
    expected_base_packs = initial_resource_packs or ['vanilla']
    assert active_packs == expected_base_packs + ['file/CopiMineResourcePack-26.3.zip']
    assert incompatible_packs == ['file/OtherOldPack.zip']
    assert 'soundCategory_music:0.25' in options
    assert (profile / 'mods' / module_filename).read_bytes() == module_bytes
    assert not (profile / 'mods' / old_module_filename).exists()
    backups = list((profile / 'migration-backups' / 'client-mods').glob('*/' + old_module_filename))
    assert len(backups) == 1 and backups[0].read_bytes() == old_module_bytes
    assert receipt_alias.read_text(encoding='utf-8') == 'preserve linked file contents'
    assert json.loads((profile / 'copimine-migration-receipt.json').read_text(encoding='utf-8-sig'))['minecraftVersion'] == '26.3'

    installed_pack = profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip'
    installed_pack.write_bytes(b'previous candidate pack')

    repeated = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)
    assert repeated.returncode == 0, repeated.stdout + repeated.stderr
    assert hashlib.sha256(installed_pack.read_bytes()).hexdigest() == candidate_sha256
    assert list((profile / 'resourcepacks').glob('CopiMineResourcePack-26.3.zip.backup-*'))
    assert receipt_alias.read_text(encoding='utf-8') == 'preserve linked file contents'

    validation_command = [
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-ValidateOnly',
    ]
    installed_state = {
        path: path.read_bytes()
        for path in (
            profile / 'mods' / module_filename,
            profile / 'mods' / 'CopiMineClient-0.1.1+26.3.jar',
            profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip',
            profile / 'options.txt',
            profile / 'copimine-migration-receipt.json',
        )
    }
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode == 0, validation.stdout + validation.stderr
    assert {path: path.read_bytes() for path in installed_state} == installed_state

    module_path = profile / 'mods' / module_filename
    module_path.unlink()
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0 and 'missing locked migration mod' in validation.stdout + validation.stderr
    module_path.write_bytes(module_bytes)

    client_path = profile / 'mods' / 'CopiMineClient-0.1.1+26.3.jar'
    client_path.write_bytes(b'corrupt client')
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0 and 'CopiMineClient differs from its pinned artifact' in validation.stdout + validation.stderr
    client_path.write_bytes(installed_state[client_path])

    installed_pack.write_bytes(b'corrupt pack')
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0 and 'resource pack differs from its pinned candidate' in validation.stdout + validation.stderr
    installed_pack.write_bytes(installed_state[installed_pack])

    (profile / 'options.txt').write_text('resourcePacks:["vanilla"]\n', encoding='utf-8')
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0 and 'is not enabled in options.txt' in validation.stdout + validation.stderr
    (profile / 'options.txt').write_bytes(installed_state[profile / 'options.txt'])

    receipt_path = profile / 'copimine-migration-receipt.json'
    receipt_path.write_text('{"minecraftVersion":"26.2"}', encoding='utf-8')
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0 and 'receipt does not match the installed profile' in validation.stdout + validation.stderr


@pytest.mark.parametrize('unsafe_target', [
    'receipt-directory', 'options-directory', 'mods-junction', 'resourcepacks-junction',
    'migration-backups-junction', 'receipt-file-symlink', 'late-mod-target-directory',
    'pack-target-directory', 'client-jar-junction',
])
def test_profile_install_rejects_unsafe_targets_before_mutating_profile(tmp_path, unsafe_target):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')
    if unsafe_target.endswith(('-junction', '-symlink')) and os.name != 'nt':
        pytest.skip('Creating Windows junctions and symlinks requires a Windows host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    _, candidate_sha256 = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
            'fabric': {'loader': '0.19.5', 'clientPortComplete': unsafe_target == 'client-jar-junction'},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': candidate_sha256,
        },
    }), encoding='utf-8')
    modules = []
    module_filename = 'FixtureMod-1.0.jar'
    if unsafe_target in ('late-mod-target-directory', 'pack-target-directory'):
        module_bytes = b'verified fixture mod bytes'
        modules.append({
            'filename': module_filename,
            'projectId': 'A1b2C3d4',
            'versionId': 'Z9y8X7w6',
            'url': 'https://cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar',
            'sha512': hashlib.sha512(module_bytes).hexdigest(),
            'size': len(module_bytes),
            'version': '1.0',
        })
        module_stage = root / 'build/minecraft-26.3/client-mods'
        module_stage.mkdir(parents=True)
        (module_stage / module_filename).write_bytes(module_bytes)
    (migration / 'client-mods.lock.json').write_text(json.dumps({'modules': modules}), encoding='utf-8')

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    (profile / 'Copimine.json').write_text(json.dumps({
        'downloads': {'client': {'sha1': CLIENT_SHA1}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    options = profile / 'options.txt'
    if unsafe_target == 'options-directory':
        options.mkdir()
    else:
        options.write_text('resourcePacks:["vanilla"]\n', encoding='utf-8')
    original_options = None if options.is_dir() else options.read_bytes()

    unsafe_path = None
    linked_target = None
    linked_file = None
    client_artifact = None
    if unsafe_target == 'receipt-file-symlink':
        unsafe_path = profile / 'copimine-migration-receipt.json'
        linked_file = tmp_path / 'receipt-external.json'
        linked_file.write_text('preserve linked receipt target', encoding='utf-8')
        symlink = subprocess.run(
            ['cmd', '/c', 'mklink', str(unsafe_path), str(linked_file)],
            capture_output=True, text=True, timeout=10,
        )
        if symlink.returncode != 0:
            pytest.skip(f'Creating file symlinks is unavailable on this Windows host: {symlink.stderr}')
    elif unsafe_target == 'client-jar-junction':
        external_client_libs = tmp_path / 'external-client-libs'
        external_client_libs.mkdir()
        client_artifact = external_client_libs / 'CopiMineClient-0.1.1+26.3.jar'
        with zipfile.ZipFile(client_artifact, 'w') as archive:
            archive.writestr('fabric.mod.json', json.dumps({
                'id': 'copimineclient', 'version': '0.1.1+26.3',
                'depends': {'minecraft': '26.3'},
                'entrypoints': {'client': ['me.copimine.client.CopiMineClient']},
                'mixins': ['copimineclient-26.3.mixins.json'],
            }))
            archive.writestr('copimineclient-26.3.mixins.json', json.dumps({
                'package': 'me.copimine.client.mixin', 'client': [],
            }))
            archive.writestr('me/copimine/client/CopiMineClient.class', b'fixture entrypoint')
            archive.writestr('me/copimine/client/ClientBridgeProtocol.class', b'fixture bridge')
            archive.writestr('me/copimine/client/BridgePayload.class', b'fixture payload')
        client_libs = root / 'build/minecraft-26.3/client/libs'
        client_libs.parent.mkdir(parents=True)
        junction = subprocess.run(
            ['cmd', '/c', 'mklink', '/J', str(client_libs), str(external_client_libs)],
            capture_output=True, text=True, timeout=10,
        )
        if junction.returncode != 0:
            pytest.skip(f'Creating directory junctions is unavailable on this Windows host: {junction.stderr}')

    if unsafe_target == 'late-mod-target-directory':
        (profile / 'mods' / module_filename).mkdir(parents=True)
    elif unsafe_target == 'pack-target-directory':
        (profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip').mkdir(parents=True)

    if unsafe_target == 'receipt-directory':
        unsafe_path = profile / 'copimine-migration-receipt.json'
        unsafe_path.mkdir()
    elif unsafe_target.endswith('-junction') and unsafe_target != 'client-jar-junction':
        child_name = unsafe_target.removesuffix('-junction')
        unsafe_path = profile / ('migration-backups' if child_name == 'migration-backups' else child_name)
        linked_target = tmp_path / (child_name + '-external-target')
        linked_target.mkdir()
        sentinel = linked_target / 'sentinel.txt'
        sentinel.write_text('preserve target contents', encoding='utf-8')
        junction = subprocess.run(
            ['cmd', '/c', 'mklink', '/J', str(unsafe_path), str(linked_target)],
            capture_output=True, text=True, timeout=10,
        )
        if junction.returncode != 0:
            pytest.skip(f'Creating directory junctions is unavailable on this Windows host: {junction.stderr}')

    command = [
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ]
    if unsafe_target != 'client-jar-junction':
        command.append('-DependenciesOnly')
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    diagnostic = (result.stdout + result.stderr).lower()
    expected_message = {
        'receipt-directory': 'migration receipt destination',
        'options-directory': 'text file destination',
        'mods-junction': 'profile directory destination',
        'resourcepacks-junction': 'profile directory destination',
        'migration-backups-junction': 'profile directory destination',
        'receipt-file-symlink': 'migration receipt destination',
        'late-mod-target-directory': 'profile artifact destination',
        'pack-target-directory': 'profile artifact destination',
        'client-jar-junction': 'unsafe directory path',
    }[unsafe_target]
    assert expected_message in diagnostic
    if original_options is not None:
        assert options.read_bytes() == original_options
    else:
        assert list(options.iterdir()) == []
    expected_names = {'Copimine.json', 'options.txt'}
    if unsafe_target in ('receipt-directory', 'receipt-file-symlink'):
        expected_names.add('copimine-migration-receipt.json')
    if unsafe_target.endswith('-junction') and unsafe_target != 'client-jar-junction':
        expected_names.add(unsafe_path.name)
    if unsafe_target == 'late-mod-target-directory':
        expected_names.add('mods')
    if unsafe_target == 'pack-target-directory':
        expected_names.add('resourcepacks')
    assert {path.name for path in profile.iterdir()} == expected_names
    assert (profile / 'mods').exists() == (unsafe_target in ('mods-junction', 'late-mod-target-directory'))
    assert (profile / 'resourcepacks').exists() == (unsafe_target in ('resourcepacks-junction', 'pack-target-directory'))
    if unsafe_target == 'late-mod-target-directory':
        assert list((profile / 'mods' / module_filename).iterdir()) == []
    if unsafe_target == 'pack-target-directory':
        assert list((profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip').iterdir()) == []
    if unsafe_target != 'migration-backups-junction':
        assert not (profile / 'migration-backups').exists()
    if linked_target:
        assert {path.name for path in linked_target.iterdir()} == {'sentinel.txt'}
        assert (linked_target / 'sentinel.txt').read_text(encoding='utf-8') == 'preserve target contents'
    if linked_file:
        assert linked_file.read_text(encoding='utf-8') == 'preserve linked receipt target'
        assert (profile / 'copimine-migration-receipt.json').is_symlink()
    if client_artifact:
        assert client_artifact.is_file()
        assert not (profile / 'mods').exists()
        assert not (profile / 'resourcepacks').exists()


@pytest.mark.parametrize('invalid_option', [
    'resourcePacks:[1]',
    'incompatibleResourcePacks:{"unexpected":"object"}',
])
def test_malformed_resource_pack_options_fail_before_profile_mutation(tmp_path, invalid_option):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    _, candidate_sha256 = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': True},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': candidate_sha256,
        },
    }), encoding='utf-8')
    (migration / 'client-mods.lock.json').write_text(json.dumps({'modules': []}), encoding='utf-8')

    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.1+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient', 'version': '0.1.1+26.3',
            'depends': {'minecraft': '26.3'},
            'entrypoints': {'client': ['me.copimine.client.CopiMineClient']},
            'mixins': ['copimineclient-26.3.mixins.json'],
        }))
        archive.writestr('copimineclient-26.3.mixins.json', json.dumps({'package': 'me.copimine.client.mixin', 'client': []}))
        archive.writestr('me/copimine/client/CopiMineClient.class', b'fixture entrypoint')
        archive.writestr('me/copimine/client/ClientBridgeProtocol.class', b'fixture bridge')
        archive.writestr('me/copimine/client/BridgePayload.class', b'fixture payload')
    pin_test_client_artifact(migration, client_jar)

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    profile_json = profile / 'Copimine.json'
    profile_json.write_text(json.dumps({
        'downloads': {'client': {'sha1': CLIENT_SHA1}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    options = profile / 'options.txt'
    option_lines = ['soundCategory_music:0.25']
    if not invalid_option.startswith('resourcePacks:'):
        option_lines.append('resourcePacks:["vanilla"]')
    option_lines.append(invalid_option)
    original_options = ('\n'.join(option_lines) + '\n').encode()
    options.write_bytes(original_options)

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    assert options.read_bytes() == original_options
    assert sorted(path.name for path in profile.iterdir()) == ['Copimine.json', 'options.txt']
    assert not (profile / 'mods').exists()
    assert not (profile / 'resourcepacks').exists()
    assert not (profile / 'migration-backups').exists()
    assert not (profile / 'copimine-migration-receipt.json').exists()


def test_existing_copimineclient_mod_is_rejected_before_profile_mutation(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    _, candidate_sha256 = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': True},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': candidate_sha256,
        },
    }), encoding='utf-8')
    (migration / 'client-mods.lock.json').write_text(json.dumps({'modules': []}), encoding='utf-8')

    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.1+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient', 'version': '0.1.1+26.3',
            'depends': {'minecraft': '26.3'},
            'entrypoints': {'client': ['me.copimine.client.CopiMineClient']},
            'mixins': ['copimineclient-26.3.mixins.json'],
        }))
        archive.writestr('copimineclient-26.3.mixins.json', json.dumps({'package': 'me.copimine.client.mixin', 'client': []}))
        archive.writestr('me/copimine/client/CopiMineClient.class', b'fixture entrypoint')
        archive.writestr('me/copimine/client/ClientBridgeProtocol.class', b'fixture bridge')
        archive.writestr('me/copimine/client/BridgePayload.class', b'fixture payload')
    pin_test_client_artifact(migration, client_jar)

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    (profile / 'Copimine.json').write_text(json.dumps({
        'downloads': {'client': {'sha1': CLIENT_SHA1}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    (profile / 'options.txt').write_text('resourcePacks:["vanilla"]\n', encoding='utf-8')
    mods = profile / 'mods'
    mods.mkdir()
    existing_client = mods / 'existing-client.jar'
    with zipfile.ZipFile(existing_client, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'copimineclient', 'version': '0.1.0'}))
    original_options = (profile / 'options.txt').read_bytes()

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    assert 'Another CopiMineClient mod is already present' in result.stdout + result.stderr
    assert (profile / 'options.txt').read_bytes() == original_options
    assert existing_client.is_file()
    assert not (profile / 'resourcepacks').exists()
    assert not (profile / 'copimine-migration-receipt.json').exists()


def test_stale_locked_fabric_mod_id_is_rejected_before_profile_mutation(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    pack, candidate_sha256 = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': False},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': candidate_sha256,
        },
    }), encoding='utf-8')
    locked_mod = {
        'project': 'fabric-api',
        'modId': 'fabric-api',
        'projectId': 'P7dR8mSH',
        'versionId': 'v2j28coa',
        'version': '0.162.0+26.3',
        'filename': 'fabric-api-0.162.0+26.3.jar',
        'url': 'https://cdn.modrinth.com/data/P7dR8mSH/versions/v2j28coa/fabric-api.jar',
        'sha512': 'b' * 128,
        'size': 1,
    }
    (migration / 'client-mods.lock.json').write_text(json.dumps({'modules': [locked_mod]}), encoding='utf-8')

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    (profile / 'Copimine.json').write_text(json.dumps({
        'downloads': {'client': {'sha1': CLIENT_SHA1}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    options = profile / 'options.txt'
    options.write_text('resourcePacks:["vanilla"]\n', encoding='utf-8')
    original_options = options.read_bytes()
    mods = profile / 'mods'
    mods.mkdir()
    stale_mod = mods / 'fabric-api-0.161.0+26.3.jar'
    stale_payload = io.BytesIO()
    with zipfile.ZipFile(stale_payload, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'fabric-api', 'version': '0.161.0+26.3'}))
    stale_mod.write_bytes(stale_payload.getvalue())
    original_stale_mod = stale_mod.read_bytes()

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-DependenciesOnly', '-ValidateOnly',
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    assert 'conflicts with locked migration mod' in result.stdout + result.stderr
    assert locked_mod['filename'] in result.stdout + result.stderr
    assert options.read_bytes() == original_options
    assert stale_mod.read_bytes() == original_stale_mod
    assert not (mods / locked_mod['filename']).exists()
    assert not (profile / 'resourcepacks').exists()
    assert not (profile / 'copimine-migration-receipt.json').exists()


def test_profile_install_rejects_unpinned_pack_before_modifying_profile(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes((ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    pack, _ = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': False},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': '0' * 64,
        },
    }), encoding='utf-8')
    (migration / 'client-mods.lock.json').write_text(json.dumps({'modules': []}), encoding='utf-8')

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    (profile / 'Copimine.json').write_text(json.dumps({
        'downloads': {'client': {'sha1': CLIENT_SHA1}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    options = profile / 'options.txt'
    options.write_text('resourcePacks:["vanilla"]\n', encoding='utf-8')
    original_options = options.read_bytes()

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-DependenciesOnly',
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    assert 'Resource-pack SHA-256 differs' in result.stdout + result.stderr
    assert options.read_bytes() == original_options
    assert not (profile / 'mods').exists()
    assert not (profile / 'resourcepacks').exists()
    assert not (root / 'build/minecraft-26.3/client-mods').exists()


def test_partial_client_mod_copy_does_not_install_corrupt_jar_or_replace_existing_mod(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell profile installation requires a Windows verification host')

    root = tmp_path / 'repo'
    script = root / 'scripts/minecraft/InstallMigrationProfile.ps1'
    script.parent.mkdir(parents=True)
    script.write_bytes(SCRIPT.read_bytes())
    (script.parent / 'DownloadPinnedArtifact.ps1').write_bytes(
        (ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1').read_bytes())
    migration = root / 'tools/minecraft-26.3'
    migration.mkdir(parents=True)
    _, pack_hash = write_migration_pack(root)
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3',
        'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
        'fabric': {'loader': '0.19.5', 'clientPortComplete': False},
        'resourcePackMigration': {
            'legacyPackSha256': LEGACY_PACK_SHA256,
            'candidateSha256': pack_hash,
        },
    }), encoding='utf-8')

    candidate = io.BytesIO()
    with zipfile.ZipFile(candidate, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'externalmod'}))
    expected = candidate.getvalue()
    module = {
        'filename': 'ExternalMod.jar',
        'projectId': 'A1b2C3d4',
        'versionId': 'Z9y8X7w6',
        'url': 'https://cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/ExternalMod.jar',
        'sha512': hashlib.sha512(expected).hexdigest(),
        'size': len(expected),
        'version': '1.0.0',
    }
    (migration / 'client-mods.lock.json').write_text(json.dumps({'modules': [module]}), encoding='utf-8')
    stage = root / 'build/minecraft-26.3/client-mods'
    stage.mkdir(parents=True)
    (stage / module['filename']).write_bytes(expected)

    profile = tmp_path / 'Copimine'
    profile.mkdir()
    (profile / 'Copimine.json').write_text(json.dumps({
        'downloads': {'client': {'sha1': CLIENT_SHA1}},
        'mainClass': 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        'javaVersion': {'majorVersion': 25},
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}],
    }), encoding='utf-8')
    (profile / 'options.txt').write_text('resourcePacks:["vanilla"]\n', encoding='utf-8')
    mods = profile / 'mods'
    mods.mkdir()
    destination = mods / module['filename']

    wrapper = tmp_path / 'fail_during_mod_copy.ps1'
    wrapper.write_text(r'''param([string]$Installer, [string]$Profile)
function Copy-Item {
    [CmdletBinding()]
    param([string]$LiteralPath, [string]$Destination, [switch]$Force)
    if ($Destination -match '[\\/]mods[\\/]ExternalMod\.jar(?:\.candidate[^\\/]*)?$') {
        [IO.File]::WriteAllBytes($Destination, [byte[]](0x13, 0x37, 0x42))
        throw 'simulated partial client-mod copy failure'
    }
    Microsoft.PowerShell.Management\Copy-Item -LiteralPath $LiteralPath -Destination $Destination -Force:$Force
}
& $Installer -ProfileDirectory $Profile -DependenciesOnly
''', encoding='utf-8')

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
        '-Installer', str(script), '-Profile', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    assert 'simulated partial client-mod copy failure' in result.stdout + result.stderr
    assert not destination.exists()
    assert not list(mods.glob('*.candidate*'))

    original_jar = b'previous profile jar'
    destination.write_bytes(original_jar)
    conflict = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-DependenciesOnly',
    ], capture_output=True, text=True, timeout=30)
    assert conflict.returncode != 0, conflict.stdout + conflict.stderr
    assert 'Refusing to replace a different existing mod' in conflict.stdout + conflict.stderr
    assert destination.read_bytes() == original_jar
