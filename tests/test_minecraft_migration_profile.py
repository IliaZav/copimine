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
    assert "21.0.12.1" in gitlab and "21.0.12.1" in release_gate_source
    assert github.count("java-version: '21.0.12+101.0.LTS'") == 3
    assert "distribution: jdkfile" in github
    assert "java-version: '25.0.4.1'" in github
    assert 'jdk-file: ${{ runner.temp }}/temurin-25.0.4.1+1.zip' in github
    assert '00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283' in github
    assert 'ExpectedSize 141167264' in github
    assert "java-version: '25.0.4+101.0.LTS'" not in github
    assert "java-version: '25.0.4+1'" not in github
    assert "25.0.4.1" in gitlab
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
    assert {
        module['modId']: module['tlauncherArtifactPrefixes']
        for module in lock['modules'] if module.get('tlauncherArtifactPrefixes')
    } == {
        'customskinloader-bootstrap': ['CustomSkinLoader_Universal'],
        'emotecraft': ['emotecraft-for-MC26.3'],
        'iris': ['iris-fabric'],
        'player_animation_library': ['PlayerAnimationLibMerged'],
        'sodium': ['sodium-fabric'],
        'voicechat': ['voicechat-fabric'],
    }
    assert 'Test-TLauncherVersionedIdentity' in profile_installer
    assert 'Client mod lock has an unsafe TLauncher artifact prefix' in profile_installer
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
    fabric_api = next(module for module in lock['modules'] if module['modId'] == 'fabric-api')
    assert fabric_api['bundled'] is True
    assert 'include "net.fabricmc.fabric-api:fabric-api:${profile.fabric.api}"' in (
        ROOT / 'tools/minecraft-26.3/client/build.gradle').read_text(encoding='utf-8')
    assert "Where-Object { $_.bundled -ne $true }" in profile_installer


def test_fabric_api_lock_matches_the_current_pinned_26_3_release():
    profile = json.loads((ROOT / 'tools/minecraft-26.3/profile.lock.json').read_text(encoding='utf-8'))
    lock = json.loads((ROOT / 'tools/minecraft-26.3/client-mods.lock.json').read_text(encoding='utf-8'))
    readme = (ROOT / 'docs/minecraft-26.3/README.md').read_text(encoding='utf-8')
    fabric_api = next(module for module in lock['modules'] if module['project'] == 'fabric-api')

    assert profile['fabric']['api'] == '0.164.0+26.3'
    assert profile['fabric']['loom'] == '1.19.1'
    assert lock['verifiedAt'] == '2026-10-19'
    assert fabric_api['version'] == profile['fabric']['api']
    assert f"| Fabric API | {profile['fabric']['api']} |" in readme
    assert fabric_api['versionId'] == 'LEklt8GT'
    assert fabric_api['projectId'] == 'P7dR8mSH'
    assert fabric_api['filename'] == 'fabric-api-0.164.0+26.3.jar'
    assert fabric_api['fabricMetadataVersion'] == profile['fabric']['api']
    assert fabric_api['url'] == (
        'https://cdn.modrinth.com/data/P7dR8mSH/versions/LEklt8GT/'
        'fabric-api-0.164.0%2B26.3.jar'
    )
    assert fabric_api['sha1'] == '7ec45ba92a286ed7d0c57bcd74cf9182789741ec'
    assert fabric_api['sha512'] == (
        '2ce7b0826f75801a56e8150eb5ceca467cdf8b4b44e160b5a5381e5412567d0'
        '24f46a16c679f29c4a9346b2687fa1740f91ed6026a31b2ed1f3d4b5035968d69'
    )
    assert fabric_api['size'] == 1_775_592


def test_sodium_lock_respects_iris_exact_26_3_dependency():
    lock = json.loads((ROOT / 'tools/minecraft-26.3/client-mods.lock.json').read_text(encoding='utf-8'))
    sodium = next(module for module in lock['modules'] if module['modId'] == 'sodium')
    iris = next(module for module in lock['modules'] if module['modId'] == 'iris')
    iris_sodium = next(dependency for dependency in iris['dependencies'] if dependency['project_id'] == sodium['projectId'])

    assert sodium['version'] == 'mc26.3-0.9.2-fabric'
    assert sodium['channel'] == 'release'
    assert sodium['versionId'] == 'bAZQdGpg'
    assert sodium['filename'] == 'sodium-fabric-0.9.2+mc26.3.jar'
    assert sodium['url'] == (
        'https://cdn.modrinth.com/data/AANobbMI/versions/bAZQdGpg/'
        'sodium-fabric-0.9.2%2Bmc26.3.jar'
    )
    assert sodium['sha1'] == '9acfe851e36f4fb27d7c5baa224e8bd4e0334283'
    assert sodium['sha512'] == (
        'f3f260f204b8ce5e8777c2c73f537956755ec61f3817b0b78b5e5568e8aa40f'
        '14891f146475075109747665c7c88a45ca0ca9a8df3c68d79c58ecdaa8d8c4b93'
    )
    assert sodium['size'] == 1_908_068
    assert iris_sodium['version_id'] == sodium['versionId']


def test_profile_lock_matches_the_repeatable_current_client_build():
    profile = json.loads((ROOT / 'tools/minecraft-26.3/profile.lock.json').read_text(encoding='utf-8'))
    artifact = profile['clientArtifact']

    assert artifact['filename'] == 'CopiMineClient-0.1.3+26.3.jar'
    assert artifact['size'] == 15_025_264
    assert artifact['sha512'] == (
        '67665f71488f9a563a9cc3a5c33fc7fd0ff80a11ea9753147f21b03031d7fcc9'
        '3fb48830bd505d3d5d73e067587f0f05a87b384e386a78665bddbf45d117f7c4'
    )


def test_profile_validator_hashes_nested_dependencies_on_windows_powershell_51():
    profile_installer = SCRIPT.read_text(encoding='utf-8')

    assert '[Security.Cryptography.SHA512]::Create()' in profile_installer
    assert '$nestedHasher.ComputeHash($nestedBytes.ToArray())' in profile_installer
    assert '[Security.Cryptography.SHA512]::HashData(' not in profile_installer
    assert '[Convert]::ToHexString(' not in profile_installer


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


def test_paper_26_3_compile_lock_uses_one_verified_version_per_artifact():
    from xml.etree import ElementTree

    lock = json.loads((ROOT/'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json').read_text(encoding='utf-8'))
    profile = json.loads((ROOT/'tools/minecraft-26.3/profile.lock.json').read_text(encoding='utf-8'))
    assert profile['paper']['build'] == 205
    assert profile['paper']['channel'] == 'STABLE'
    assert profile['paper']['api'] == '26.3.build.205-stable'
    assert profile['paper']['apiSize'] == 2_943_078
    assert lock['paper26_3Api'] == profile['paper']['api']
    dependencies = lock['paper26_3Dependencies']
    assert len(dependencies) == 17
    coordinates = [entry['path'].rsplit('/', 2)[0] for entry in dependencies]
    assert len(coordinates) == len(set(coordinates))
    assert dependencies[0]['path'] == 'net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar'
    assert dependencies[1]['path'] == 'net/kyori/adventure-key/5.2.0/adventure-key-5.2.0.jar'
    assert all('/5.2.0/' in entry['path'] for entry in dependencies if '/adventure-' in entry['path'])
    metadata = ElementTree.parse(ROOT/'tools/minecraft-26.3/gradle/verification-metadata.xml')
    namespace = {'v': 'https://schema.gradle.org/dependency-verification'}
    verified = {}
    for component in metadata.findall('.//v:component', namespace):
        for artifact in component.findall('v:artifact', namespace):
            sha = artifact.find('v:sha256', namespace)
            if sha is not None:
                path = '/'.join((component.attrib['group'].replace('.', '/'), component.attrib['name'], component.attrib['version'], artifact.attrib['name']))
                verified[path] = sha.attrib['value']
    for entry in dependencies:
        assert entry['sha256'] == verified[entry['path']]
        assert entry['size'] > 0
    # The existing shared Java gate still consumes exactly the legacy graph.
    assert len(lock['dependencies']) == 18
    assert lock['dependencies'][0]['path'] == 'net/kyori/adventure-api/4.17.0/adventure-api-4.17.0.jar'


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
        if '$cp += $env:PAPER_COMPILE_DEPS' in source:
            assert source.index('$cp += $env:PAPER_COMPILE_DEPS', locked_branch) > locked_branch, relative
        else:
            assert '$cp = @($paperApi) + $compileDependencies' in source, relative
            assert 'Assert-LockedCompileFile -Path $compileDependencies[$index]' in source, relative
        fallback_branch=source.index('} else {', locked_branch)
        for ambient_marker in ("Get-ChildItem -Path $groupPath", "Get-ChildItem -Path (Join-Path $serverDir 'libraries')", "Get-ChildItem -Path $serverLibraries"):
            ambient_index=source.find(ambient_marker)
            if ambient_index >= 0:
                assert ambient_index > fallback_branch, (relative, ambient_marker)


def test_new_setup_java_action_reference_is_immutable():
    github=(ROOT/'.github/workflows/ci.yml').read_text(encoding='utf-8')

    assert github.count('actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961') == 3
    assert github.count('actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6') == 2
    assert 'actions/setup-java@v4' not in github


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
    with zipfile.ZipFile(client_jar) as archive:
        client_version = json.loads(archive.read('fabric.mod.json'))['version']
    lock['clientArtifact'] = {
        'filename': client_jar.name,
        'version': client_version,
        'size': len(payload),
        'sha512': hashlib.sha512(payload).hexdigest(),
    }
    lock_path.write_text(json.dumps(lock), encoding='utf-8')


def test_bundled_fabric_api_is_not_installed_as_a_separate_required_mod(tmp_path):
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

    api_version = '0.162.0+26.3'
    api_filename = f'fabric-api-{api_version}.jar'
    api_payload = io.BytesIO()
    with zipfile.ZipFile(api_payload, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'fabric-api', 'version': api_version}))
    api_bytes = api_payload.getvalue()
    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.3+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient', 'version': '0.1.3+26.3',
            'depends': {'minecraft': '26.3', 'fabric-api': '>=' + api_version},
            'entrypoints': {'client': ['me.copimine.client.CopiMineClient']},
            'mixins': ['copimineclient-26.3.mixins.json'],
            'jars': [{'file': f'META-INF/jars/{api_filename}'}],
        }))
        archive.writestr('copimineclient-26.3.mixins.json', json.dumps({'package': 'me.copimine.client.mixin', 'client': []}))
        archive.writestr('META-INF/jars/' + api_filename, api_bytes)
        archive.writestr('me/copimine/client/CopiMineClient.class', b'fixture entrypoint')
        archive.writestr('me/copimine/client/ClientBridgeProtocol.class', b'fixture bridge')
        archive.writestr('me/copimine/client/BridgePayload.class', b'fixture payload')
    (migration / 'profile.lock.json').write_text(json.dumps({
        'minecraftVersion': '26.3', 'minimumJava': 25,
        'minecraftClient': {'sha1': CLIENT_SHA1},
        'clientArtifact': {
            'filename': client_jar.name, 'version': '0.1.3+26.3',
            'size': client_jar.stat().st_size,
            'sha512': hashlib.sha512(client_jar.read_bytes()).hexdigest(),
        },
        'fabric': {'loader': '0.19.5', 'clientPortComplete': True},
        'resourcePackMigration': {'legacyPackSha256': LEGACY_PACK_SHA256, 'candidateSha256': pack_hash},
    }), encoding='utf-8')
    (migration / 'client-mods.lock.json').write_text(json.dumps({
        'schemaVersion': 1,
        'modules': [{
            'project': 'fabric-api', 'modId': 'fabric-api', 'fabricMetadataVersion': api_version,
            'version': api_version, 'filename': api_filename, 'bundled': True,
            'projectId': 'P7dR8mSH', 'versionId': 'v2j28coa',
            'url': 'https://cdn.modrinth.com/data/P7dR8mSH/versions/v2j28coa/' + api_filename,
            'sha512': hashlib.sha512(api_bytes).hexdigest(), 'size': len(api_bytes),
        }],
    }), encoding='utf-8')
    stage = root / 'build/minecraft-26.3/client-mods'
    stage.mkdir(parents=True)
    (stage / api_filename).write_bytes(api_bytes)

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
    old_api = mods / 'fabric-api-0.161.0+26.3.jar'
    with zipfile.ZipFile(old_api, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'fabric-api', 'version': '0.161.0+26.3'}))
    old_api_bytes = old_api.read_bytes()

    dependency_setup = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-DependenciesOnly',
    ], capture_output=True, text=True, timeout=30)
    assert dependency_setup.returncode == 0, dependency_setup.stdout + dependency_setup.stderr
    assert (mods / api_filename).read_bytes() == api_bytes

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode == 0, result.stdout + result.stderr
    assert (mods / client_jar.name).read_bytes() == client_jar.read_bytes()
    assert not any(
        json.loads(zipfile.ZipFile(path).read('fabric.mod.json')).get('id') == 'fabric-api'
        for path in mods.glob('*.jar')
    )
    receipt_path = profile / 'copimine-migration-receipt.json'
    complete_receipt = receipt_path.read_bytes()
    complete_options = (profile / 'options.txt').read_bytes()
    repeated_dependency_setup = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-DependenciesOnly',
    ], capture_output=True, text=True, timeout=30)
    assert repeated_dependency_setup.returncode != 0, repeated_dependency_setup.stdout + repeated_dependency_setup.stderr
    assert 'DependenciesOnly cannot run when CopiMineClient is installed' in (
        repeated_dependency_setup.stdout + repeated_dependency_setup.stderr)
    assert receipt_path.read_bytes() == complete_receipt
    assert (profile / 'options.txt').read_bytes() == complete_options
    assert (mods / client_jar.name).read_bytes() == client_jar.read_bytes()
    assert not any(
        json.loads(zipfile.ZipFile(path).read('fabric.mod.json')).get('id') == 'fabric-api'
        for path in mods.glob('*.jar')
    )
    backups = list((profile / 'migration-backups' / 'client-mods').glob('*/' + old_api.name))
    assert len(backups) == 1 and backups[0].read_bytes() == old_api_bytes
    bundled_api_backups = list((profile / 'migration-backups' / 'client-mods').glob('*/' + api_filename))
    assert len(bundled_api_backups) == 1 and bundled_api_backups[0].read_bytes() == api_bytes
    receipt = json.loads((profile / 'copimine-migration-receipt.json').read_text(encoding='utf-8-sig'))
    assert receipt['mods'] == []

    old_api.write_bytes(old_api_bytes)
    validation = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-ValidateOnly',
    ], capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0
    assert 'bundled Fabric API' in validation.stdout + validation.stderr

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
    api_version = '0.162.0+26.3'
    api_filename = 'fabric-api-0.162.0+26.3.jar'
    api_buffer = io.BytesIO()
    with zipfile.ZipFile(api_buffer, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': 'fabric-api', 'version': api_version}))
    api_bytes = api_buffer.getvalue()
    (migration / 'client-mods.lock.json').write_text(json.dumps({'schemaVersion': 1, 'modules': [{
        'filename': module_filename,
        'modId': 'fixturemod',
        'tlauncherArtifactPrefixes': ['FixtureMod-Official'],
        'version': '1.0',
        'fabricMetadataVersion': '1.0',
        'projectId': 'A1b2C3d4',
        'versionId': 'Z9y8X7w6',
        'url': 'https://cdn.modrinth.com/data/A1b2C3d4/versions/Z9y8X7w6/FixtureMod-1.0.jar',
        'sha1': hashlib.sha1(module_bytes).hexdigest(),
        'sha512': module_sha512,
        'size': len(module_bytes),
    }, {
        'project': 'fabric-api',
        'version': api_version,
        'fabricMetadataVersion': api_version,
        'filename': api_filename,
        'modId': 'fabric-api',
        'bundled': True,
        'projectId': 'P7dR8mSH',
        'versionId': 'v2j28coa',
        'url': 'https://cdn.modrinth.com/data/P7dR8mSH/versions/v2j28coa/fabric-api-0.162.0%2B26.3.jar',
        'sha512': hashlib.sha512(api_bytes).hexdigest(),
        'size': len(api_bytes),
    }]}), encoding='utf-8')
    module_stage = root / 'build/minecraft-26.3/client-mods'
    module_stage.mkdir(parents=True)
    (module_stage / module_filename).write_bytes(module_bytes)

    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.3+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient',
            'version': '0.1.3+26.3',
            'depends': {'minecraft': '26.3', 'fabric-api': '>=' + api_version},
            'entrypoints': {'client': ['me.copimine.client.CopiMineClient']},
            'mixins': ['copimineclient-26.3.mixins.json'],
            'jars': [{'file': 'META-INF/jars/' + api_filename}],
        }))
        archive.writestr('META-INF/jars/' + api_filename, api_bytes)
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
        'id': 'Copimine',
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

    old_client_bytes = b'previous CopiMine client artifact'
    old_api_bytes = b'previous standalone Fabric API artifact'
    api_base_bytes = b'Fabric API Base must stay independent'
    fixture_mod_extra_bytes = b'FixtureMod Extra must stay independent'
    tlauncher_additional = profile / 'TLauncherAdditional.json'
    tlauncher_additional.write_text(json.dumps({
        'modpack': {'version': {'mods': [
            {
                'stateGameElement': 'active', 'name': 'Fabric API Base', 'lanName': 'fabric-api-base',
                'version': {'name': '1.0', 'metadata': {
                    'sha1': hashlib.sha1(api_base_bytes).hexdigest(), 'size': len(api_base_bytes),
                    'path': 'mods/fabric-api-base-1.0.jar', 'url': 'mods/fabric-api-base-1.0.jar',
                }},
            },
            {
                'stateGameElement': 'active', 'name': 'Fabric API', 'lanName': 'fabric-api',
                'version': {'name': 'fabric-api-0.161.0+26.3', 'metadata': {
                    'sha1': hashlib.sha1(old_api_bytes).hexdigest(), 'size': len(old_api_bytes),
                    'path': 'mods/fabric-api-0.161.0%2B26.3.jar',
                    'url': '/mods/306612/8913512/fabric-api-0.161.0%252B26.3.jar',
                }},
            },
            {
                'stateGameElement': 'active', 'name': 'fabric-api-0.162.0+26.3',
                'userInstall': True,
                'version': {'name': '1.0', 'metadata': {
                    'sha1': hashlib.sha1(b'new standalone API').hexdigest(), 'size': 17,
                    'path': 'mods/fabric-api-0.162.0+26.3.jar',
                    'url': 'mods/fabric-api-0.162.0+26.3.jar',
                }},
            },
            {
                'stateGameElement': 'active', 'name': 'CopiMineClient-0.1.1+26.3',
                'userInstall': True,
                'version': {'name': '1.0', 'metadata': {
                    'sha1': hashlib.sha1(old_client_bytes).hexdigest(), 'size': len(old_client_bytes),
                    'path': 'mods/CopiMineClient-0.1.1+26.3.jar',
                    'url': 'mods/CopiMineClient-0.1.1+26.3.jar',
                }},
            },
            {
                'stateGameElement': 'active', 'name': 'FixtureMod Extra', 'lanName': 'fixturemod-extra',
                'version': {'name': '1.0', 'metadata': {
                    'sha1': hashlib.sha1(fixture_mod_extra_bytes).hexdigest(),
                    'size': len(fixture_mod_extra_bytes),
                    'path': 'mods/FixtureMod-Extra-1.0.jar',
                    'url': 'mods/FixtureMod-Extra-1.0.jar',
                }},
            },
            {
                'stateGameElement': 'active', 'name': 'FixtureMod-Official-0.9',
                'version': {'name': '0.9', 'metadata': {
                    'sha1': hashlib.sha1(old_module_bytes).hexdigest(), 'size': len(old_module_bytes),
                    'path': 'mods/FixtureMod-Official-0.9.jar', 'url': 'mods/FixtureMod-Official-0.9.jar',
                }},
            },
        ]}}}), encoding='utf-8')
    original_tlauncher_additional = tlauncher_additional.read_bytes()

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
    assert tlauncher_additional.read_bytes() == original_tlauncher_additional
    assert (profile / 'copimine-migration-receipt.json').read_text(encoding='utf-8') == 'preserve linked file contents'
    assert (profile / 'mods' / old_module_filename).read_bytes() == old_module_bytes
    assert not (profile / 'mods' / module_filename).exists()
    assert not (profile / 'mods' / 'CopiMineClient-0.1.3+26.3.jar').exists()
    assert not (profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip').exists()
    assert not (profile / 'migration-backups').exists()

    script.write_bytes(SCRIPT.read_bytes())

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode == 0, result.stdout + result.stderr
    tlauncher_manifest = json.loads(tlauncher_additional.read_text(encoding='utf-8'))
    launcher_mods = tlauncher_manifest['modpack']['version']['mods']
    assert not any(
        mod.get('lanName', '').casefold() == 'fabric-api'
        or mod.get('name', '').casefold() == 'fabric-api-0.162.0+26.3'
        for mod in launcher_mods
    )
    launcher_api_base = next(mod for mod in launcher_mods if mod.get('lanName') == 'fabric-api-base')
    assert launcher_api_base['version']['metadata']['path'] == 'mods/fabric-api-base-1.0.jar'
    assert launcher_api_base['version']['metadata']['sha1'] == hashlib.sha1(api_base_bytes).hexdigest()
    launcher_client = next(mod for mod in launcher_mods if mod.get('name', '').startswith('CopiMineClient-'))
    client_metadata = launcher_client['version']['metadata']
    assert launcher_client['name'] == 'CopiMineClient-0.1.3+26.3'
    assert client_metadata['path'] == 'mods/CopiMineClient-0.1.3+26.3.jar'
    assert client_metadata['sha1'] == hashlib.sha1(client_jar.read_bytes()).hexdigest()
    assert client_metadata['size'] == client_jar.stat().st_size
    launcher_module = next(
        mod for mod in launcher_mods
        if mod.get('version', {}).get('metadata', {}).get('path') == 'mods/FixtureMod-1.0.jar'
    )
    module_metadata = launcher_module['version']['metadata']
    assert module_metadata['path'] == 'mods/FixtureMod-1.0.jar'
    assert module_metadata['sha1'] == hashlib.sha1(module_bytes).hexdigest()
    assert module_metadata['size'] == len(module_bytes)
    assert launcher_module['userInstall'] is True
    launcher_fixture_mod_extra = next(
        mod for mod in launcher_mods if mod.get('lanName') == 'fixturemod-extra'
    )
    assert launcher_fixture_mod_extra['name'] == 'FixtureMod Extra'
    assert launcher_fixture_mod_extra['version']['metadata']['path'] == 'mods/FixtureMod-Extra-1.0.jar'
    assert launcher_fixture_mod_extra['version']['metadata']['sha1'] == hashlib.sha1(
        fixture_mod_extra_bytes
    ).hexdigest()
    manifest_after_install = tlauncher_additional.read_bytes()
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
    assert tlauncher_additional.read_bytes() == manifest_after_install
    assert hashlib.sha256(installed_pack.read_bytes()).hexdigest() == candidate_sha256
    assert list((profile / 'resourcepacks').glob('CopiMineResourcePack-26.3.zip.backup-*'))
    assert receipt_alias.read_text(encoding='utf-8') == 'preserve linked file contents'

    validation_command = [
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile), '-ValidateOnly',
    ]
    for malformed_name in ('InvalidUtf8.jar', 'OversizedMetadata.jar', 'MalformedJson.jar'):
        malformed_path = malformed_mods / malformed_name
        assert malformed_path.is_file()
        malformed_path.unlink()
    installed_state = {
        path: path.read_bytes()
        for path in (
            profile / 'mods' / module_filename,
            profile / 'mods' / 'CopiMineClient-0.1.3+26.3.jar',
            profile / 'resourcepacks' / 'CopiMineResourcePack-26.3.zip',
            profile / 'options.txt',
            profile / 'copimine-migration-receipt.json',
            tlauncher_additional,
        )
    }
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode == 0, validation.stdout + validation.stderr
    assert {path: path.read_bytes() for path in installed_state} == installed_state

    stale_manifest = json.loads(installed_state[tlauncher_additional].decode('utf-8-sig'))
    stale_client = next(
        mod for mod in stale_manifest['modpack']['version']['mods']
        if mod.get('name', '').startswith('CopiMineClient-')
    )
    stale_client['version']['metadata']['path'] = 'mods/CopiMineClient-0.1.1+26.3.jar'
    tlauncher_additional.write_text(json.dumps(stale_manifest), encoding='utf-8')
    stale_manifest_bytes = tlauncher_additional.read_bytes()
    stale_validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert stale_validation.returncode != 0
    assert 'TLauncherAdditional.json contains stale migration mod entries' in (
        stale_validation.stdout + stale_validation.stderr)
    assert tlauncher_additional.read_bytes() == stale_manifest_bytes
    tlauncher_additional.write_bytes(installed_state[tlauncher_additional])

    module_path = profile / 'mods' / module_filename
    module_path.unlink()
    validation = subprocess.run(validation_command, capture_output=True, text=True, timeout=30)
    assert validation.returncode != 0 and 'missing locked migration mod' in validation.stdout + validation.stderr
    module_path.write_bytes(module_bytes)

    client_path = profile / 'mods' / 'CopiMineClient-0.1.3+26.3.jar'
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

    if os.name == 'nt':
        receipt_path.write_bytes(installed_state[receipt_path])
        intermediate_profile = tmp_path / 'profile-case-rename'
        profile.rename(intermediate_profile)
        renamed_profile = tmp_path / 'CopiMine'
        intermediate_profile.rename(renamed_profile)
        renamed_validation = subprocess.run([
            shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
            '-ProfileDirectory', str(renamed_profile), '-ValidateOnly',
        ], capture_output=True, text=True, timeout=30)
        assert renamed_validation.returncode == 0, renamed_validation.stdout + renamed_validation.stderr


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
        client_artifact = external_client_libs / 'CopiMineClient-0.1.3+26.3.jar'
        with zipfile.ZipFile(client_artifact, 'w') as archive:
            archive.writestr('fabric.mod.json', json.dumps({
                'id': 'copimineclient', 'version': '0.1.3+26.3',
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

    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.3+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient', 'version': '0.1.3+26.3',
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


def test_existing_copimineclient_mod_is_rejected_or_upgraded_transactionally(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if os.name != 'nt' or not shell:
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

    client_jar = root / 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.3+26.3.jar'
    client_jar.parent.mkdir(parents=True)
    with zipfile.ZipFile(client_jar, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient', 'version': '0.1.3+26.3',
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

    existing_client.unlink()
    previous_client = mods / 'CopiMineClient-0.1.2+26.3.jar'
    with zipfile.ZipFile(previous_client, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'id': 'copimineclient',
            'version': '0.1.2+26.3',
            'depends': {'minecraft': '26.3'},
        }))
    profile_before_locked_install = {
        path.relative_to(profile).as_posix(): path.read_bytes() if path.is_file() else None
        for path in profile.rglob('*')
    }
    held_path = str(previous_client).replace("'", "''")
    installer_path = str(script).replace("'", "''")
    profile_path = str(profile).replace("'", "''")
    shell_path = str(shell).replace("'", "''")
    lock_wrapper = f"""
$held = [IO.File]::Open('{held_path}', [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
try {{
    & '{shell_path}' -NoProfile -ExecutionPolicy Bypass -File '{installer_path}' -ProfileDirectory '{profile_path}'
    exit $LASTEXITCODE
}} finally {{ $held.Dispose() }}
"""
    locked_result = subprocess.run([
        shell, '-NoProfile', '-Command', lock_wrapper,
    ], capture_output=True, text=True, timeout=30)

    assert locked_result.returncode != 0, locked_result.stdout + locked_result.stderr
    assert {
        path.relative_to(profile).as_posix(): path.read_bytes() if path.is_file() else None
        for path in profile.rglob('*')
    } == profile_before_locked_install
    assert previous_client.is_file()
    assert not (mods / client_jar.name).exists()
    assert not list(profile.glob('.copimine-migration-transaction-*'))

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script),
        '-ProfileDirectory', str(profile),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode == 0, result.stdout + result.stderr
    assert (mods / client_jar.name).read_bytes() == client_jar.read_bytes()
    assert not previous_client.exists()
    backups = list((profile / 'migration-backups' / 'client-mods').glob('*/' + previous_client.name))
    assert len(backups) == 1 and backups[0].read_bytes() != b''
    assert json.loads((profile / 'copimine-migration-receipt.json').read_text(encoding='utf-8-sig'))[
        'copimineClient']['version'] == '0.1.3+26.3'


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


def test_required_client_validator_scopes_enabled_check_to_client_bridge(tmp_path):
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        pytest.skip('PowerShell migration validators require a Windows verification host')

    copied_paths = [
        'CopiMineClient/gradle.properties',
        'CopiMineClient/src/main/resources/fabric.mod.json',
        'copimine-narcotics/config.yml',
        'copimine-narcotics/src/me/copimine/clientbridge/ClientBridgePayloads.java',
        'copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java',
        'copimine-narcotics/src/me/copimine/clientbridge/ClientModSessionRegistryService.java',
        'copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java',
        'tests/ValidateCopiMineClientModRequired.ps1',
        'tools/minecraft-26.3/client/build.gradle',
        'tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocol.java',
        'tools/minecraft-26.3/client/src/main/java/me/copimine/client/CopiMineClient.java',
        'tools/minecraft-26.3/client-mods.lock.json',
        'tools/minecraft-26.3/profile.lock.json',
        'tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/ClientModHandshakePolicy.java',
    ]
    temp_repo = tmp_path / 'repo'
    for relative in copied_paths:
        source = ROOT / relative
        destination = temp_repo / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)

    config = temp_repo / 'copimine-narcotics/config.yml'
    text = config.read_text(encoding='utf-8')
    assert 'client_bridge:\n  enabled: true' in text
    text = text.replace('client_bridge:\n  enabled: true', 'client_bridge:\n  enabled: false', 1)
    config.write_text(text + '\nother_plugin:\n  enabled: true\n', encoding='utf-8')

    result = subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        str(temp_repo / 'tests/ValidateCopiMineClientModRequired.ps1'),
    ], capture_output=True, text=True, timeout=30)

    assert result.returncode != 0, result.stdout + result.stderr
    assert 'client bridge must be enabled' in result.stdout + result.stderr


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
