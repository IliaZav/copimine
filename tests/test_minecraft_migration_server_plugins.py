"""Offline installation must not partially replace a known local plugin set."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import stat
import sys
import time
from types import SimpleNamespace
import zipfile
import pytest

ROOT=Path(__file__).resolve().parents[1]
MIGRATION_RUNTIME=Path(os.environ.get(
    'COPIMINE_26_3_TEST_RUNTIME',
    str(ROOT/'local-runtime/end-rift-server-26.3'),
)).resolve()


def test_grim_creative_path_does_not_skip_pending_inventory_resync():
    source = (ROOT / 'thirdparty/grim-creative-patch/minecraft-26.3/src/ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.java').read_text(encoding='utf-8')
    method = source.split('public void tickWithBukkit()', 1)[1]
    creative_branch = method.split('if (creative) {', 1)[1].split('} else {', 1)[0]

    assert 'pendingFinalizedSlot.clear();' in creative_branch
    assert 'serverIsCurrentlyProcessingThesePredictions.clear();' in creative_branch
    assert 'return;' not in creative_branch
    assert 'if (creative) return;' not in method
    assert method.index('if (player.inventory.needResend)') < method.index('if (tickID % 5 == 0)')
    assert 'checkThatBukkitIsSynced(slotToCheck, !creative);' in method

    reconciliation = source.split('private void checkThatBukkitIsSynced(int slot, boolean resyncClient)', 1)[1]
    assert 'if (existing.getType() != serverside.getType() || existing.getAmount() != serverside.getAmount())' in reconciliation
    assert reconciliation.index('if (resyncClient)') < reconciliation.index('player.platformPlayer.updateInventory()')
    assert reconciliation.index('player.platformPlayer.updateInventory()') < reconciliation.index('setItem(slot, serverside);')
    queued_resync = reconciliation.split('if (resyncClient)', 1)[1].split('setItem(slot, serverside);', 1)[0]
    assert 'player.platformPlayer.getGameMode() != GameMode.CREATIVE' in queued_resync
    assert queued_resync.index('GameMode.CREATIVE') < queued_resync.index('player.platformPlayer.updateInventory();')

def test_grim263_lock_pins_latest_official_bukkit_artifact_without_claiming_native_support():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(module for module in lock['modules'] if module.get('pluginName')=='GrimAC')

    assert record['version']=='2.3.74-3bc8959'
    assert record['channel']=='alpha'
    assert record['versionId']=='aQAyYziH'
    assert record['filename']=='GrimAC-2.3.74-3bc8959-creative-fix.jar'
    assert record['url']=='https://cdn.modrinth.com/data/LJNGWSvH/versions/aQAyYziH/grimac-bukkit-2.3.74-3bc8959.jar'
    assert record['upstreamFilename']=='grimac-bukkit-2.3.74-3bc8959.jar'
    assert record['upstreamSize']==13139508
    assert record['upstreamSha1']=='47ddfbd92d3e63b8d83e9ec42b6df04122c7f172'
    assert record['upstreamSha256']=='698f9fe2e019d9ccd16388c0aed40f7dfd68cd52d60685025e65f0c5b03c71fa'
    assert record['upstreamSha512']=='fae6d12d553153fb20b5a4d639b6a0c2731fd02d07c152cd1da225eb70b409d73044025c97cc9304e99a65b5a1d435c726a98b674e60a895f81bb22f85e385e7'
    assert record['sourceCommit']=='3bc895910193212efd92d5eea9995334efe22388'
    assert record['upstreamPublishedAt']=='2026-10-19T03:16:55.871645Z'
    assert record['upstreamDeclares263'] is True
    assert '26.3' in record['gameVersions']
    assert record['compatibilityStatus']=='requires-runtime-validation'
    assert record['nativeVerified'] is False


def test_tab_lock_pins_latest_official_paper_26_3_release():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(module for module in lock['modules'] if module.get('project')=='tab-was-taken')

    assert record['version']=='6.2.1'
    assert record['channel']=='release'
    assert record['versionId']=='HlsRTW5F'
    assert record['filename']=='TAB v6.2.1.jar'
    assert record['url']=='https://cdn.modrinth.com/data/gG7VFbG0/versions/HlsRTW5F/TAB%20v6.2.1.jar'
    assert record['sha1']=='cb772026895bfa856c29a47b6951e1f4910a0aa0'
    assert record['sha256']=='0b6a21af6fd3b113c05821daaed00ff92234a84ca94b29b3aec0ef1bd313b8a9'
    assert record['sha512']=='146fff1d94506459b32532d707f11e544da448b7dbfcb16c7a9a4fb91db7be8be272b275f3315e4c2122529a3ac30ccc344654fc013303615679d6a90207b41c'
    assert record['size']==8483627
    assert record['compatibilityStatus']=='requires-runtime-validation'
    assert record['nativeVerified'] is False


def test_bluemap_lock_pins_26_3_paper_release():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(module for module in lock['modules'] if module.get('project')=='bluemap')

    assert record['version']=='5.29'
    assert record['channel']=='release'
    assert record['versionId']=='uHQIrUdf'
    assert record['filename']=='bluemap-5.29-paper.jar'
    assert record['url']=='https://cdn.modrinth.com/data/swbUV1cr/versions/uHQIrUdf/bluemap-5.29-paper.jar'
    assert record['size']==6336407
    assert record['sha256']=='7bf475936c26107d476c63151c76d5b49e865eebeada12c9e7d92155de877b9f'
    assert record['sha512']=='e936198eaf7ea83429e1d08b8e2a5447dd359c0252e3410cb3cc101860c933abcc055462a11b034a4dd77b699da54017412ac73728860fd8b68a672c32d9926c'
    assert '26.3' in record['gameVersions']
    assert record['compatibilityStatus']=='requires-runtime-validation'
    assert record['nativeVerified'] is False


def test_coreprotect_lock_does_not_claim_upstream_declares_minecraft_26_3():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(module for module in lock['modules'] if module.get('pluginName')=='CoreProtect')

    assert record['upstreamDeclares263'] is False
    assert record['compatibilityStatus']=='requires-runtime-validation'
    assert record['nativeVerified'] is False

def test_coreprotect_build_pins_latest_verified_upstream_master_commit():
    build_lock=json.loads((ROOT/'tools/minecraft-26.3/coreprotect-build.lock.json').read_text(encoding='utf-8'))
    plugin_lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(module for module in plugin_lock['modules'] if module.get('pluginName')=='CoreProtect')
    latest_verified_commit='bc46794bbae17e506dcb41ea7a80b099ca7fecb0'

    assert build_lock['commit']==latest_verified_commit
    assert build_lock['source']=='https://github.com/PlayPro/CoreProtect/tree/'+latest_verified_commit
    assert record['sourceCommit']==latest_verified_commit
    assert record['source']==build_lock['source']

def test_coreprotect_lock_separates_official_upstream_and_custom_build_integrity():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(module for module in lock['modules'] if module.get('pluginName')=='CoreProtect')

    assert record['url'].endswith('/CoreProtect-CE-24.1.jar')
    assert record['upstreamFilename']=='CoreProtect-CE-24.1.jar'
    assert record['size']==record['upstreamSize']==1_113_283
    assert record['sha1']==record['upstreamSha1']=='3e6ef12c6d4bd2c72f8dc32a35491fd414dc4d80'
    assert record['sha256']==record['upstreamSha256']=='a2acef7c06ef201355cef07d591a6055dfed358ad768faaaaaf4e276865ecc97'
    assert record['sha512']==record['upstreamSha512']=='76aad727528ddb990bbee5069f8ef3fcd018ae0bcdea89b965be2485e828633eb068672e841be64e550c8474f901a24d8c15c7bd44c2bd6c96244e1e58de0c95'
    assert record['filename']=='CoreProtect-CE-24.1-26.3-upstream.jar'
    assert record['buildArtifact'].endswith('/'+record['filename'])
    assert record['buildSize']==14_133_020
    assert record['buildSha256']=='48b9db1a9784190c7b5c295d5ebf1433cc4c8bbbd2c3a991aa54c975dde9a7d0'
    assert record['buildSha512']=='9e2604c9bf6f805d9958d856372a1134c8bda3c3632bf8f429b7acb03d8eb8256abf1e13e4725a49131a1468e2bdce600b22fbd6ad8cd4b289a2745b120cf271'

def load():
    spec=importlib.util.spec_from_file_location('migration_plugins', ROOT/'scripts/minecraft/install_migration_server_plugins.py')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    return mod

def jar(name,version):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:z.writestr('plugin.yml',f'name: {name}\nversion: {version}\n')
    return out.getvalue()

def artifacts_jar(version,items):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        z.writestr('plugin.yml',f'name: CopiMineArtifacts\nversion: {version}\n')
        z.writestr('items.yml',items)
    return out.getvalue()

def descriptor_jar(name_line):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:z.writestr('plugin.yml',name_line+'\nversion: 1\n')
    return out.getvalue()

def prepare_owned_runtime_fixture(root,server):
    paper=b'locked Paper 26.3 fixture'
    paper_sha=hashlib.sha256(paper).hexdigest();paper_filename='paper-26.3-161.jar'
    server.mkdir(parents=True,exist_ok=True)
    (server/paper_filename).write_bytes(paper)
    (server/'copimine-migration-runtime.json').write_text(json.dumps({
        'schemaVersion':1,'minecraftVersion':'26.3','paperFilename':paper_filename,
        'paperSha256':paper_sha,'paperSize':len(paper),'eulaAcceptedAtSetup':False,
    }),encoding='utf-8')
    (server/'eula.txt').write_text('eula=false\n',encoding='ascii')
    profile_dir=root/'tools/minecraft-26.3';profile_dir.mkdir(parents=True,exist_ok=True)
    (profile_dir/'profile.lock.json').write_text(json.dumps({
        'minecraftVersion':'26.3','paper':{
            'channel':'BETA','build':161,'sha256':paper_sha,'size':len(paper),
            'url':f'https://fill-data.papermc.io/v1/objects/{paper_sha}/{paper_filename}',
        },
    }),encoding='utf-8')

def fixture(tmp_path):
    old=jar('Example','1');new=jar('Example','2')
    server=tmp_path/'local-runtime/end-rift-server-26.3';(server/'plugins').mkdir(parents=True)
    (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n')
    prepare_owned_runtime_fixture(tmp_path,server)
    (server/'plugins/old.jar').write_bytes(old)
    stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True);(stage/'new.jar').write_bytes(new)
    record={'pluginName':'Example','version':'2','filename':'new.jar','url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),'replaceSha256':[hashlib.sha256(old).hexdigest()]}
    return server,stage,record,old,new


def test_install_refuses_unowned_runtime_before_plugin_mutation(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    (server/'copimine-migration-runtime.json').unlink()

    with pytest.raises(ValueError,match='not owned'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()
    assert not (server/'migration-backups').exists()


def test_install_refuses_runtime_with_paper_bytes_outside_the_lock(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    (server/'paper-26.3-161.jar').write_bytes(b'unlocked Paper bytes')

    with pytest.raises(ValueError,match='differs from the locked candidate'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()
    assert not (server/'migration-backups').exists()


def test_install_preserves_accepted_eula_while_the_server_is_stopped(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    (server/'eula.txt').write_text('eula=true\n',encoding='ascii')

    mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    assert (server/'eula.txt').read_text(encoding='ascii')=='eula=true\n'
    assert (server/'plugins/new.jar').read_bytes()==new
    assert not (server/'plugins/old.jar').exists()
    assert (server/'migration-backups').is_dir()

def artifacts_install_fixture(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    previous=server/'plugins/old.jar'
    previous.unlink()
    old=jar('CopiMineArtifacts','1')
    (server/'plugins/CopiMineArtifacts.jar').write_bytes(old)
    record.update(pluginName='CopiMineArtifacts',version='2',filename='CopiMineArtifacts-26.3.jar',
                  runtimeFilename='CopiMineArtifacts.jar',sha512='',size=0,
                  replaceSha256=[hashlib.sha256(old).hexdigest()])
    candidate=artifacts_jar('2','''items:\n  - id: current_item\n    price_ar: 150\n    name: Canonical name\n  - id: new_item\n    price_ar: 25\ndonation-catalog:\n  catalog-version: 1\n  website-base-url: https://copimine.ru\n  items:\n    - item-id: new_donation\n      price-donation: 50\n''')
    (stage/'new.jar').unlink()
    (stage/record['filename']).write_bytes(candidate)
    record['sha512']=hashlib.sha512(candidate).hexdigest()
    record['size']=len(candidate)
    data=server/'plugins/CopiMineArtifacts'
    data.mkdir()
    catalog=data/'items.yml'
    catalog.write_text('''items:\n  - id: current_item\n    price_ar: 500\n    admin_note: keep this field\n  - id: custom_item\n    price_ar: 75\ndonation-catalog:\n  catalog-version: 3\n  website-base-url: https://custom.example\n  items:\n    - item-id: custom_donation\n      price-donation: 100\n''',encoding='utf-8')
    config=data/'config.yml'
    config.write_bytes(b'debug_gui: false\nrepair:\n  fixed-price-ar: 7\n')
    return mod,server,stage,record,catalog,config,candidate

def test_artifacts_install_adds_missing_catalog_items_without_overwriting_custom_values(tmp_path):
    mod,server,stage,record,catalog,config,candidate=artifacts_install_fixture(tmp_path)
    original_config=config.read_bytes()

    mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    import yaml
    merged=yaml.safe_load(catalog.read_text(encoding='utf-8'))
    assert [(item['id'],item['price_ar']) for item in merged['items']]==[
        ('current_item',500),('custom_item',75),('new_item',25)]
    assert merged['items'][0]['admin_note']=='keep this field'
    assert merged['donation-catalog']['catalog-version']==3
    assert merged['donation-catalog']['website-base-url']=='https://custom.example'
    assert [item['item-id'] for item in merged['donation-catalog']['items']]==[
        'custom_donation','new_donation']
    assert config.read_bytes()==original_config
    assert (server/'plugins/CopiMineArtifacts.jar').read_bytes()==candidate

def test_artifacts_catalog_is_restored_when_plugin_receipt_publication_fails(tmp_path):
    mod,server,stage,record,catalog,config,candidate=artifacts_install_fixture(tmp_path)
    original_catalog=catalog.read_bytes()
    original_plugin=(server/'plugins/CopiMineArtifacts.jar').read_bytes()

    def fail_receipt(*args):
        assert b'new_item' in catalog.read_bytes()
        raise OSError('simulated receipt publication failure')
    with pytest.raises(OSError,match='simulated receipt publication failure'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,receipt_writer=fail_receipt)

    assert catalog.read_bytes()==original_catalog
    assert (server/'plugins/CopiMineArtifacts.jar').read_bytes()==original_plugin
    assert not (server/'migration-plugin-receipt.json').exists()

def test_artifacts_catalog_merge_completes_locked_source_and_is_idempotent():
    mod=load()
    canonical=(ROOT/'copimine-artifacts/items.yml').read_bytes()
    existing=b'''items:
  - id: zmei_gorynych
    price_ar: 500
    admin-note: preserve this value
'''

    merged=mod.merge_artifacts_catalog_bytes(existing,canonical)

    import yaml
    document=yaml.safe_load(merged)
    assert len(document['items'])==32
    assert len(document['donation-catalog']['items'])==12
    zmei=next(item for item in document['items'] if item['id']=='zmei_gorynych')
    assert zmei['price_ar']==500
    assert zmei['admin-note']=='preserve this value'
    assert mod.merge_artifacts_catalog_bytes(merged,canonical)==merged

def test_artifacts_catalog_merge_disables_retired_item_but_preserves_live_catalog_values():
    mod=load()
    canonical='''items: []
donation-catalog:
  catalog-version: 2
  items:
    - item-id: gde_moy_lut_blyat_compass
      display-name: "Retired compass"
      enabled: false
      price-donation: 100
      effect-profile-id: LOOT_COMPASS
'''.encode('utf-8')
    existing='''items: []
donation-catalog:
  catalog-version: 7
  website-base-url: https://runtime.example
  items:
    - item-id: gde_moy_lut_blyat_compass
      display-name: "Runtime compass name"
      enabled: true
      price-donation: 175
      effect-profile-id: LOOT_COMPASS
      operator-note: preserve-me
'''.encode('utf-8')

    merged=mod.merge_artifacts_catalog_bytes(existing,canonical)

    import yaml
    document=yaml.safe_load(merged)
    tombstone=document['donation-catalog']['items'][0]
    assert tombstone['enabled'] is False
    assert tombstone['display-name']=='Runtime compass name'
    assert tombstone['price-donation']==175
    assert tombstone['operator-note']=='preserve-me'
    assert document['donation-catalog']['catalog-version']==7
    assert document['donation-catalog']['website-base-url']=='https://runtime.example'
    assert mod.merge_artifacts_catalog_bytes(merged,canonical)==merged

def test_artifacts_catalog_merge_disables_retired_item_in_indentless_yaml_lists():
    mod=load()
    canonical='''items: []
donation-catalog:
  catalog-version: 2
  items:
    - item-id: retired_item
      display-name: "Canonical name"
      enabled: false
      price-donation: 100
      effect-profile-id: RETIRED
'''.encode('utf-8')
    existing='''items: []
donation-catalog:
  catalog-version: 7
  website-base-url: https://runtime.example
  items:
  - item-id: retired_item
    display-name: "Runtime name"
    enabled: true
    price-donation: 175
    effect-profile-id: RETIRED
    operator-note: preserve-me
'''.encode('utf-8')

    merged=mod.merge_artifacts_catalog_bytes(existing,canonical)

    import yaml
    document=yaml.safe_load(merged)
    retired=document['donation-catalog']['items'][0]
    assert retired['enabled'] is False
    assert retired['display-name']=='Runtime name'
    assert retired['price-donation']==175
    assert retired['operator-note']=='preserve-me'
    assert document['donation-catalog']['catalog-version']==7
    assert mod.merge_artifacts_catalog_bytes(merged,canonical)==merged

def test_artifacts_install_recovers_stale_catalog_even_when_plugin_jar_is_current(tmp_path):
    mod,server,stage,record,catalog,config,candidate=artifacts_install_fixture(tmp_path)
    stale_catalog=catalog.read_bytes()
    mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)
    catalog.write_bytes(stale_catalog)

    mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    import yaml
    merged=yaml.safe_load(catalog.read_text(encoding='utf-8'))
    assert [item['id'] for item in merged['items']]==['current_item','custom_item','new_item']
    assert [item['item-id'] for item in merged['donation-catalog']['items']]==[
        'custom_donation','new_donation']

def test_malformed_artifacts_catalog_refuses_plugin_install_before_mutation(tmp_path):
    mod,server,stage,record,catalog,config,candidate=artifacts_install_fixture(tmp_path)
    malformed=b'items: [broken\n'
    catalog.write_bytes(malformed)
    old_plugin=(server/'plugins/CopiMineArtifacts.jar').read_bytes()

    with pytest.raises(ValueError,match='Invalid or ambiguous existing Artifacts catalog YAML'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    assert catalog.read_bytes()==malformed
    assert (server/'plugins/CopiMineArtifacts.jar').read_bytes()==old_plugin
    assert not (server/'migration-backups/plugins').exists()

def test_tampered_stage_rejected_before_any_live_file_changes(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    (stage/'new.jar').write_bytes(new+b'tampered')
    with pytest.raises(ValueError,match='digest|size'):mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

def test_running_server_refuses_changes(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    with pytest.raises(ValueError,match='running'):mod.install_records(tmp_path,server,[r],stage,lambda port,host:True)
    assert (server/'plugins/old.jar').read_bytes()==old

def test_missing_replace_sha256_refuses_unknown_existing_plugin_without_key_error(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    record.pop('replaceSha256')

    with pytest.raises(ValueError,match='Refusing unrecognized existing Example artifact'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

@pytest.mark.parametrize(('key','value'),[
    ('server-port','0'),
    ('server-port','65536'),
    ('rcon.port','0'),
    ('rcon.port','65536'),
])
def test_invalid_runtime_ports_are_rejected_before_listener_checks_or_file_changes(tmp_path,key,value):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    properties=server/'server.properties'
    rows=[line for line in properties.read_text(encoding='utf-8').splitlines() if not line.startswith(key+'=')]
    rows.append(key+'='+value)
    properties.write_text('\n'.join(rows)+'\n',encoding='utf-8')
    calls=[]

    with pytest.raises(ValueError,match='between 1 and 65535'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:calls.append((port,host)) or False)

    assert calls==[]
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

def test_server_properties_reparse_point_is_rejected_before_listener_check(tmp_path,monkeypatch):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    properties=server/'server.properties'
    original_lstat=Path.lstat
    def lstat_with_reparse_properties(path):
        if path==properties:
            return SimpleNamespace(st_mode=stat.S_IFREG|0o600,st_file_attributes=0x400)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_reparse_properties)

    with pytest.raises(ValueError,match='Symlink or reparse point refused'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

def test_stage_reparse_parent_is_rejected_before_candidate_write(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build'/'server-plugins';parent=tmp_path/'build'
    record={'pluginName':'Example','version':'2','filename':'new.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':'0'*128,'size':10,'replaceSha256':[]}
    original_lstat=Path.lstat
    def lstat_with_reparse_parent(path):
        if path==parent:
            return SimpleNamespace(st_mode=stat.S_IFDIR|0o700,st_file_attributes=0x400)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_reparse_parent)

    with pytest.raises(ValueError,match='Symlink or reparse point refused'):
        mod.stage_records([record],stage,root=tmp_path)

    assert not (stage/'new.jar').exists()

def test_stage_reparse_candidate_is_rejected_even_when_its_bytes_match(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build'/'server-plugins';stage.mkdir(parents=True)
    new=jar('Example','2');destination=stage/'new.jar';destination.write_bytes(new)
    record={'pluginName':'Example','version':'2','filename':'new.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),'replaceSha256':[]}
    original_lstat=Path.lstat
    def lstat_with_reparse_candidate(path):
        if path==destination:
            return SimpleNamespace(st_mode=stat.S_IFREG|0o600,st_file_attributes=0x400)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_reparse_candidate)
    monkeypatch.setattr(mod,'download_candidate',lambda *args:pytest.fail('matching linked candidate must not download'))

    with pytest.raises(ValueError,match='Symlink or reparse point refused'):
        mod.stage_records([record],stage,root=tmp_path)

    assert destination.read_bytes()==new

def test_normal_cli_does_not_download_missing_remote_candidates(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build/minecraft-26.3/server-plugins';stage.mkdir(parents=True)
    write_bootstrap_fixture(tmp_path)
    mod.prepare_runtime(tmp_path)
    record={'pluginName':'Example','version':'2','filename':'new.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':'0'*128,'size':10,'replaceSha256':[]}
    monkeypatch.setattr(mod,'load_install_records',lambda root:[record])
    monkeypatch.setattr(mod,'stage_records',lambda *args:pytest.fail('normal install must be offline'))
    monkeypatch.setattr(mod.urllib.request,'urlopen',lambda *args,**kwargs:pytest.fail('network access is forbidden'))

    with pytest.raises(FileNotFoundError):
        mod.main(root=tmp_path,argv=[])

def test_receipt_publication_failure_rolls_back_installed_plugin_jars(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    def fail_receipt(*args):raise OSError('simulated receipt publication failure')

    with pytest.raises(OSError,match='receipt publication failure'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,receipt_writer=fail_receipt)

    assert sorted(path.name for path in (server/'plugins').glob('*.jar'))==['old.jar']
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()
    assert not list((server/'plugins').glob('*.candidate'))

def test_dangling_plugin_temp_symlink_is_rejected_before_backup_or_copy(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    plugins=server/'plugins'
    outside=tmp_path/'outside.jar'
    temporary=plugins/'new.candidate'
    try:
        temporary.symlink_to(outside)
    except (OSError,NotImplementedError) as error:
        pytest.skip('symlink creation is unavailable on this Windows host: '+str(error))

    with pytest.raises(ValueError,match='Unrecognized staging file|temporary|symlink|link'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)

    assert (plugins/'old.jar').read_bytes()==old
    assert not outside.exists()
    assert temporary.is_symlink()
    assert not (server/'migration-backups').exists()

def test_dangling_plugin_temp_link_is_detected_with_lstat(tmp_path,monkeypatch):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    temporary=server/'plugins/new.candidate'
    original_lstat=Path.lstat
    def lstat_with_dangling_link(path):
        if path==temporary:
            return SimpleNamespace(st_mode=stat.S_IFLNK|0o777,st_file_attributes=0)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_dangling_link)

    with pytest.raises(ValueError,match='Unrecognized staging file'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()
    assert not (server/'migration-backups').exists()

def test_mktemp_candidate_leftover_is_rejected_before_install(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    temporary=server/'plugins'/'new.jar.0f3a7c91.candidate'
    temporary.write_bytes(b'interrupted candidate write')

    with pytest.raises(ValueError,match='Unrecognized staging file'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert (server/'plugins/new.jar').exists() is False
    assert temporary.read_bytes()==b'interrupted candidate write'
    assert not (server/'migration-backups').exists()

def test_corrupt_downloaded_plugin_cache_is_replaced_only_after_verification(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True)
    new=jar('Example','2')
    record={'pluginName':'Example','version':'2','filename':'new.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),'replaceSha256':[]}
    destination=stage/'new.jar';destination.write_bytes(b'broken cached jar')
    monkeypatch.setattr(mod.urllib.request,'urlopen',lambda request,timeout:io.BytesIO(new))

    mod.stage_records([record],stage,tmp_path)

    assert destination.read_bytes()==new
    assert not list(stage.glob('*.candidate'))

def test_stage_records_prunes_unlocked_jars_but_keeps_pinned_upstream_source(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build/minecraft-26.3/server-plugins';stage.mkdir(parents=True)
    candidate=jar('Example','2');upstream=jar('Example','1')
    record={'pluginName':'Example','version':'2','filename':'Example-2.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/Example-2.jar',
            'sha512':hashlib.sha512(candidate).hexdigest(),'size':len(candidate),
            'upstreamFilename':'Example-1.jar','replaceSha256':[]}
    (stage/'Example-1.jar').write_bytes(upstream)
    stale=stage/'Example-1.9.jar';stale.write_bytes(jar('Example','1.9'))
    receipt=stage/'Example-2.jar.receipt.json';receipt.write_text('{"nativeVerified":false}',encoding='utf-8')
    monkeypatch.setattr(mod.urllib.request,'urlopen',lambda request,timeout:io.BytesIO(candidate))

    mod.stage_records([record],stage,tmp_path)

    assert (stage/'Example-2.jar').read_bytes()==candidate
    assert (stage/'Example-1.jar').read_bytes()==upstream
    assert receipt.read_text(encoding='utf-8')=='{"nativeVerified":false}'
    assert not stale.exists()

def test_stage_records_rejects_unexpected_reparse_jar_before_pruning_or_download(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build/minecraft-26.3/server-plugins';stage.mkdir(parents=True)
    outside=tmp_path/'outside.jar';outside.write_bytes(b'outside bytes')
    unexpected=stage/'unexpected.jar';unexpected.write_bytes(jar('Unexpected','1'))
    record={'pluginName':'Example','version':'2','filename':'Example-2.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/Example-2.jar',
            'sha512':hashlib.sha512(jar('Example','2')).hexdigest(),'size':len(jar('Example','2')),
            'replaceSha256':[]}
    original_lstat=Path.lstat
    def lstat_with_reparse_jar(path):
        if path==unexpected:
            return SimpleNamespace(st_mode=stat.S_IFREG|0o600,st_file_attributes=0x400)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_reparse_jar)
    monkeypatch.setattr(mod,'download_candidate',lambda *args:pytest.fail('unsafe stage must fail before downloading'))

    with pytest.raises(ValueError,match='Symlink or reparse point refused'):
        mod.stage_records([record],stage,tmp_path)

    assert outside.read_bytes()==b'outside bytes'
    assert unexpected.exists()
    assert not (stage/'Example-2.jar').exists()

def test_stage_records_refuses_unexpected_directory_outside_build_without_touching_it(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'protected-stage';stage.mkdir()
    stale=stage/'stale.jar';stale.write_bytes(b'keep outside generated stage')
    record={'pluginName':'Example','version':'2','filename':'Example-2.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/Example-2.jar',
            'sha512':'0'*128,'size':10,'replaceSha256':[]}

    with pytest.raises(ValueError,match='must stay below the repository build directory'):
        mod.stage_records([record],stage,tmp_path)

    assert stale.read_bytes()==b'keep outside generated stage'

def test_failed_plugin_cache_repair_preserves_previous_bytes(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True)
    new=jar('Example','2');bad=bytearray(new);bad[-1]^=1
    record={'pluginName':'Example','version':'2','filename':'new.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),'replaceSha256':[]}
    destination=stage/'new.jar';previous=b'broken cached jar';destination.write_bytes(previous)
    monkeypatch.setattr(mod.urllib.request,'urlopen',lambda request,timeout:io.BytesIO(bytes(bad)))

    with pytest.raises((ValueError,zipfile.BadZipFile)):
        mod.stage_records([record],stage,tmp_path)

    assert destination.read_bytes()==previous
    assert not list(stage.glob('*.candidate'))

def test_oversized_plugin_response_preserves_previous_cache(tmp_path,monkeypatch):
    mod=load();stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True)
    new=jar('Example','2')
    record={'pluginName':'Example','version':'2','filename':'new.jar',
            'url':'https://cdn.modrinth.com/data/test/versions/test/new.jar',
            'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),'replaceSha256':[]}
    destination=stage/'new.jar';previous=b'broken cached jar';destination.write_bytes(previous)
    monkeypatch.setattr(mod.urllib.request,'urlopen',lambda request,timeout:io.BytesIO(new+b'!'))

    with pytest.raises(ValueError,match='exceeds pinned size'):
        mod.stage_records([record],stage,tmp_path)

    assert destination.read_bytes()==previous
    assert not list(stage.glob('*.candidate'))

def test_server_bound_to_non_loopback_address_is_refused_before_listener_check(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    (server/'server.properties').write_text('server-ip=192.0.2.20\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n')
    calls=[]
    def check(port,host):
        calls.append((port,host))
        return False
    with pytest.raises(ValueError,match='loopback'):mod.install_records(tmp_path,server,[r],stage,check)
    assert calls==[]
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

def test_form_feed_whitespace_in_server_ip_matches_java_properties(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    (server/'server.properties').write_text(
        'server-ip=\f127.0.0.1\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n',encoding='utf-8')
    calls=[]
    def check(port,host):
        calls.append((port,host))
        return port==25566 and host=='127.0.0.1'
    with pytest.raises(ValueError,match='running'):
        mod.install_records(tmp_path,server,[r],stage,check)
    assert calls[0]==(25566,'127.0.0.1')
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

@pytest.mark.parametrize('duplicate',[
    'server-port=25567',
    ' server-port=25567',
    '# ignored comment\\\nserver-port=25567',
    r'server\u002dport=25567',
    'rcon.port=25577',
    'rcon.port : 25577',
    'server-ip=192.0.2.20',
    'server-ip =192.0.2.20',
])
def test_duplicate_runtime_properties_refuse_replacement(tmp_path,duplicate):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    with (server/'server.properties').open('a',encoding='utf-8') as output:output.write(duplicate+'\n')
    with pytest.raises(ValueError,match='[Dd]uplicate'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()

def test_foreign_same_name_plugin_refuses_entire_batch(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    (server/'plugins/old.jar').write_bytes(jar('Example','foreign'))
    with pytest.raises(ValueError,match='unrecognized'):mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)
    assert not (server/'plugins/new.jar').exists()

def test_offline_replace_backs_up_old_jar_and_is_idempotent(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    baseline=jar('Baseline','1')
    (server/'plugins/Baseline.jar').write_bytes(baseline)
    receipt=mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)
    assert (server/'plugins/new.jar').read_bytes()==new
    assert not (server/'plugins/old.jar').exists()
    backups=list((server/'migration-backups').rglob('old.jar'))
    assert len(backups)==1 and backups[0].read_bytes()==old
    assert receipt['installed'][0]['pluginName']=='Example'
    assert receipt['installed'][0]['sha256']==hashlib.sha256(new).hexdigest()
    assert receipt['installed'][0]['sha512']==hashlib.sha512(new).hexdigest()
    assert receipt['pluginInventory']==[
        {'pluginName':'Baseline','filename':'Baseline.jar',
         'sha256':hashlib.sha256(baseline).hexdigest(),'sha512':hashlib.sha512(baseline).hexdigest()},
        {'pluginName':'Example','filename':'new.jar',
         'sha256':hashlib.sha256(new).hexdigest(),'sha512':hashlib.sha512(new).hexdigest()},
    ]
    mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)
    assert len(list((server/'migration-backups').rglob('old.jar')))==1

def test_first_party_build_candidate_is_staged_and_installed_with_external_candidates(tmp_path):
    mod=load()
    old=jar('CopiMineEndEvent','0.1.0')
    candidate=jar('CopiMineEndEvent','0.1.0+26.3')
    baseline_replacement='a'*64
    candidate_replacement='b'*64
    artifact=tmp_path/'build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar'
    artifact.parent.mkdir(parents=True);artifact.write_bytes(candidate)
    server=tmp_path/'local-runtime/end-rift-server-26.3'
    (server/'plugins').mkdir(parents=True)
    (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n')
    prepare_owned_runtime_fixture(tmp_path,server)
    (server/'plugins/CopiMineEndEvent.jar').write_bytes(old)
    baseline={
        'pluginName':'CopiMineEndEvent','version':'0.1.0','filename':'CopiMineEndEvent.jar',
        'sha256':hashlib.sha256(old).hexdigest(),
        'replaceSha256':[baseline_replacement],
        'candidate':{
            'filename':'CopiMineEndEvent-26.3.jar','runtimeFilename':'CopiMineEndEvent.jar',
            'sha256':hashlib.sha256(candidate).hexdigest(),
            'runtimeEligible':True,'apiVersion':'26.3',
            'buildArtifact':'build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar',
            'replaceSha256':[candidate_replacement,baseline_replacement],
        },
    }
    records=mod.load_install_records(tmp_path,{'modules':[],'unchangedBaseline':[baseline]})
    stage=tmp_path/'build/minecraft-26.3/server-plugins'
    stage.mkdir(parents=True)
    (stage/'CopiMineEndEvent-26.3.jar').write_bytes(b'partial cached candidate')
    mod.stage_records(records,stage,tmp_path)
    receipt=mod.install_records(tmp_path,server,records,stage,lambda port,host:False)

    assert len(records)==1
    assert records[0]['replaceSha256']==[
        hashlib.sha256(old).hexdigest(),candidate_replacement,baseline_replacement,
    ]
    assert (stage/'CopiMineEndEvent-26.3.jar').read_bytes()==candidate
    assert (server/'plugins/CopiMineEndEvent.jar').read_bytes()==candidate
    assert list((server/'migration-backups').rglob('CopiMineEndEvent.jar'))[0].read_bytes()==old
    assert receipt['installed'][0]['pluginName']=='CopiMineEndEvent'

def test_remote_baseline_candidates_require_runtime_eligibility_and_module_overrides(tmp_path,monkeypatch):
    mod=load()
    example_old=jar('Example','1')
    example_new=jar('Example','2')
    farmcontrol=jar('FarmControl','1.3.0')
    eligible_old=jar('ExampleAddon','1')
    eligible_new=jar('ExampleAddon','1.26.3')
    module={
        'pluginName':'Example','version':'2','filename':'Example-2.jar',
        'url':'https://cdn.modrinth.com/data/test/versions/test/Example-2.jar',
        'sha512':hashlib.sha512(example_new).hexdigest(),'sha256':hashlib.sha256(example_new).hexdigest(),
        'size':len(example_new),'replaceSha256':[hashlib.sha256(example_old).hexdigest()],
    }
    baselines=[
        {
            'pluginName':'Example','version':'1','filename':'Example-1.jar',
            'sha256':hashlib.sha256(example_old).hexdigest(),
            'candidate':{
                'filename':'Example-1-candidate.jar','runtimeFilename':'Example-1.jar','version':'1',
                'url':'https://cdn.modrinth.com/data/test/versions/test/Example-1.jar',
                'sha256':hashlib.sha256(example_old).hexdigest(),
                'sha512':hashlib.sha512(example_old).hexdigest(),'size':len(example_old),
                'runtimeEligible':True,'gameVersions':['26.3'],
            },
        },
        {
            'pluginName':'FarmControl','version':'1.3.0','filename':'FarmControl-1.3.0.jar',
            'sha256':hashlib.sha256(farmcontrol).hexdigest(),
            'candidate':{
                'filename':'FarmControl-1.3.0.jar','runtimeFilename':'FarmControl-1.3.0.jar','version':'1.3.0',
                'url':'https://cdn.modrinth.com/data/test/versions/test/FarmControl-1.3.0.jar',
                'sha256':hashlib.sha256(farmcontrol).hexdigest(),
                'sha512':hashlib.sha512(farmcontrol).hexdigest(),'size':len(farmcontrol),
                'runtimeEligible':False,'gameVersions':['1.21'],
            },
        },
        {
            'pluginName':'ExampleAddon','version':'1','filename':'ExampleAddon-1.jar',
            'sha256':hashlib.sha256(eligible_old).hexdigest(),
            'candidate':{
                'filename':'ExampleAddon-1.26.3.jar','runtimeFilename':'ExampleAddon.jar','version':'1.26.3',
                'url':'https://cdn.modrinth.com/data/test/versions/test/ExampleAddon-1.26.3.jar',
                'sha256':hashlib.sha256(eligible_new).hexdigest(),
                'sha512':hashlib.sha512(eligible_new).hexdigest(),'size':len(eligible_new),
                'runtimeEligible':True,'gameVersions':['26.3'],
            },
        },
    ]
    records=mod.load_install_records(tmp_path,{'modules':[module],'unchangedBaseline':baselines})
    assert [record['pluginName'] for record in records]==['Example','ExampleAddon']
    assert records[1]['replaceSha256']==[hashlib.sha256(eligible_old).hexdigest()]

    staged_bytes={'Example-2.jar':example_new,'ExampleAddon-1.26.3.jar':eligible_new}
    def download(record,destination):
        destination.write_bytes(staged_bytes[record['filename']])
    monkeypatch.setattr(mod,'download_candidate',download)
    stage=tmp_path/'build/minecraft-26.3/server-plugins'
    mod.stage_records(records,stage,tmp_path)
    assert (stage/'Example-2.jar').read_bytes()==example_new
    assert (stage/'ExampleAddon-1.26.3.jar').read_bytes()==eligible_new
    assert not (stage/'FarmControl-1.3.0.jar').exists()


def test_baseline_candidate_without_explicit_runtime_eligibility_is_not_selected(tmp_path):
    mod=load();old=jar('ImplicitPlugin','1')
    baseline={
        'pluginName':'ImplicitPlugin','version':'1','filename':'ImplicitPlugin.jar',
        'sha256':hashlib.sha256(old).hexdigest(),
        'candidate':{
            'filename':'ImplicitPlugin-1.jar','url':'https://cdn.modrinth.com/data/test/versions/test/ImplicitPlugin-1.jar',
            'sha256':hashlib.sha256(old).hexdigest(),'sha512':hashlib.sha512(old).hexdigest(),
            'size':len(old),'gameVersions':['26.3'],
        },
    }

    records=mod.load_install_records(tmp_path,{'minecraftVersion':'26.3','modules':[],'unchangedBaseline':[baseline]})

    assert records==[]


def test_runtime_eligible_external_baseline_requires_26_3_compatibility_evidence(tmp_path):
    mod=load();old=jar('SeeMore','1.0.2')
    candidate={
        'pluginName':'SeeMore','version':'1.0.2','filename':'SeeMore-1.0.2.jar',
        'sha256':hashlib.sha256(old).hexdigest(),
        'candidate':{
            'filename':'SeeMore-1.0.2.jar','runtimeFilename':'SeeMore-1.0.2.jar','version':'1.0.2',
            'url':'https://cdn.modrinth.com/data/test/versions/test/SeeMore-1.0.2.jar',
            'sha256':hashlib.sha256(old).hexdigest(),
            'sha512':hashlib.sha512(old).hexdigest(),'size':len(old),
            'runtimeEligible':True,'gameVersions':['1.21.8'],
        },
    }

    with pytest.raises(ValueError,match='does not declare support for Minecraft 26.3'):
        mod.load_install_records(tmp_path,{'minecraftVersion':'26.3','modules':[],'unchangedBaseline':[candidate]})

def test_coreprotect_source_build_uses_lock_and_build_receipt_provenance(tmp_path):
    mod=load();built=jar('CoreProtect','24.1')
    filename='CoreProtect-CE-24.1-26.3-upstream.jar'
    artifact=tmp_path/'build/minecraft-26.3/server-plugins'/filename
    artifact.parent.mkdir(parents=True);artifact.write_bytes(built)
    source_commit='4b1e9ab33496d1672946c3ad497c119a0bb08df3'
    (artifact.parent/(filename+'.receipt.json')).write_text(json.dumps({
        'sourceCommit':source_commit,'source':'https://github.com/PlayPro/CoreProtect/tree/'+source_commit,
        'sha512':hashlib.sha512(built).hexdigest(),'size':len(built),'nativeVerified':False,
    }),encoding='utf-8-sig')
    upstream=b'upstream release bytes'
    candidate={
        'pluginName':'CoreProtect','version':'24.1','filename':filename,
        'url':'https://cdn.modrinth.com/data/Lu3KuzdV/versions/3sehX6Sg/CoreProtect-CE-24.1.jar',
        'sha512':hashlib.sha512(upstream).hexdigest(),'size':len(upstream),
        'sha256':hashlib.sha256(upstream).hexdigest(),'replaceSha256':[],
        'sourceCommit':source_commit,'source':'https://github.com/PlayPro/CoreProtect/tree/'+source_commit,
        'buildScript':'scripts/minecraft/BuildCoreProtectMigration.ps1',
        'buildArtifact':'build/minecraft-26.3/server-plugins/'+filename,
        'buildReceipt':'build/minecraft-26.3/server-plugins/'+filename+'.receipt.json',
        'buildSha256':hashlib.sha256(built).hexdigest(),
        'buildSha512':hashlib.sha512(built).hexdigest(),'buildSize':len(built),
    }

    records=mod.load_install_records(tmp_path,{'modules':[candidate],'unchangedBaseline':[]})

    assert len(records)==1
    assert records[0]['sourceType']=='local'
    assert records[0]['sha256']==candidate['buildSha256']
    assert records[0]['sha512']==candidate['buildSha512']
    assert records[0]['size']==candidate['buildSize']
    assert Path(records[0]['artifactPath'])==artifact.resolve()

def test_coreprotect_source_build_receipt_cannot_claim_another_commit(tmp_path):
    mod=load();built=jar('CoreProtect','24.1')
    filename='CoreProtect-CE-24.1-26.3-upstream.jar'
    artifact=tmp_path/'build/minecraft-26.3/server-plugins'/filename
    artifact.parent.mkdir(parents=True);artifact.write_bytes(built)
    (artifact.parent/(filename+'.receipt.json')).write_text(json.dumps({
        'sourceCommit':'0'*40,'source':'https://github.com/PlayPro/CoreProtect/tree/'+'0'*40,
        'sha512':hashlib.sha512(built).hexdigest(),'size':len(built),'nativeVerified':False,
    }),encoding='utf-8-sig')
    upstream=b'upstream release bytes';source_commit='4b1e9ab33496d1672946c3ad497c119a0bb08df3'
    candidate={
        'pluginName':'CoreProtect','version':'24.1','filename':filename,
        'url':'https://cdn.modrinth.com/data/Lu3KuzdV/versions/3sehX6Sg/CoreProtect-CE-24.1.jar',
        'sha512':hashlib.sha512(upstream).hexdigest(),'size':len(upstream),
        'sha256':hashlib.sha256(upstream).hexdigest(),'replaceSha256':[],
        'sourceCommit':source_commit,'source':'https://github.com/PlayPro/CoreProtect/tree/'+source_commit,
        'buildScript':'scripts/minecraft/BuildCoreProtectMigration.ps1',
        'buildArtifact':'build/minecraft-26.3/server-plugins/'+filename,
        'buildReceipt':'build/minecraft-26.3/server-plugins/'+filename+'.receipt.json',
        'buildSha256':hashlib.sha256(built).hexdigest(),
        'buildSha512':hashlib.sha512(built).hexdigest(),'buildSize':len(built),
    }

    with pytest.raises(ValueError,match='build receipt'):
        mod.load_install_records(tmp_path,{'modules':[candidate],'unchangedBaseline':[]})

def test_first_party_candidate_lock_rejects_escape_paths_and_unpinned_artifacts(tmp_path):
    mod=load()
    outside=tmp_path/'escape.jar'
    candidate=jar('OwnedPlugin','2')
    outside.write_bytes(candidate)
    baseline={
        'pluginName':'OwnedPlugin','version':'1','filename':'OwnedPlugin.jar',
        'sha256':hashlib.sha256(jar('OwnedPlugin','1')).hexdigest(),
        'candidate':{
            'filename':'escape.jar','runtimeFilename':'OwnedPlugin.jar',
            'sha256':hashlib.sha256(candidate).hexdigest(),
            'runtimeEligible':True,'apiVersion':'26.3',
            'buildArtifact':'../escape.jar',
        },
    }
    with pytest.raises(ValueError,match='Unsafe local plugin artifact path'):
        mod.load_install_records(tmp_path,{'modules':[],'unchangedBaseline':[baseline]})


@pytest.mark.parametrize('runtime_field',[{}, {'runtimeFilename':None}])
def test_local_candidate_defaults_missing_or_null_runtime_filename_to_build_filename(tmp_path,runtime_field):
    mod=load()
    artifact=jar('LocalPlugin','2')
    relative='build/plugins/LocalPlugin-26.3.jar'
    path=tmp_path/relative
    path.parent.mkdir(parents=True)
    path.write_bytes(artifact)
    baseline={
        'pluginName':'LocalPlugin','version':'1','filename':'LocalPlugin.jar',
        'sha256':hashlib.sha256(jar('LocalPlugin','1')).hexdigest(),
        'candidate':{
            'filename':path.name,'sha256':hashlib.sha256(artifact).hexdigest(),
            'runtimeEligible':True,'apiVersion':'26.3',
            'buildArtifact':relative,**runtime_field,
        },
    }

    records=mod.load_install_records(tmp_path,{'modules':[],'unchangedBaseline':[baseline]})

    assert records[0]['runtimeFilename']==path.name


def test_local_baseline_candidate_keeps_an_explicit_prior_candidate_replacement_hash(tmp_path):
    mod=load()
    baseline_bytes=jar('LocalPlugin','1')
    previous_candidate=jar('LocalPlugin','1.5')
    current_candidate=jar('LocalPlugin','2')
    relative='build/plugins/LocalPlugin-26.3.jar'
    artifact=tmp_path/relative
    artifact.parent.mkdir(parents=True)
    artifact.write_bytes(current_candidate)
    previous_sha256=hashlib.sha256(previous_candidate).hexdigest()
    baseline={
        'pluginName':'LocalPlugin','version':'1','filename':'LocalPlugin.jar',
        'sha256':hashlib.sha256(baseline_bytes).hexdigest(),
        'replaceSha256':[previous_sha256],
        'candidate':{
            'filename':artifact.name,'runtimeFilename':'LocalPlugin.jar',
            'sha256':hashlib.sha256(current_candidate).hexdigest(),
            'runtimeEligible':True,'apiVersion':'26.3',
            'buildArtifact':relative,
        },
    }

    records=mod.load_install_records(tmp_path,{'modules':[],'unchangedBaseline':[baseline]})

    assert records[0]['replaceSha256']==[baseline['sha256'],previous_sha256]

def test_migration_lock_loader_includes_each_built_first_party_plugin():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    module_names={item['pluginName'] for item in lock['modules']}
    baseline_candidates=[
        item for item in lock['unchangedBaseline']
        if item['pluginName'] not in module_names and
        item.get('candidate',{}).get('runtimeEligible') is True and
        (item.get('candidate',{}).get('buildArtifact') or item.get('candidate',{}).get('url'))
    ]
    build_candidates=[item for item in baseline_candidates if item.get('candidate',{}).get('buildArtifact')]
    remote_candidates=[item for item in baseline_candidates if item.get('candidate',{}).get('url')]
    assert len(build_candidates)==8
    assert len(remote_candidates)==2
    missing=[item['candidate']['buildArtifact'] for item in build_candidates
             if not (ROOT/item['candidate']['buildArtifact']).is_file()]
    if missing:pytest.skip('the pinned 26.3 first-party plugin builds are required: '+', '.join(missing))

    records=load().load_install_records(ROOT,lock)

    assert len(records)==len(lock['modules'])+len(build_candidates)+len(remote_candidates)==30
    assert len({record['pluginName'] for record in records})==30
    assert {'FarmControl','SeeMore'}.isdisjoint({record['pluginName'] for record in records})
    assert next(row for row in lock['unchangedBaseline'] if row['pluginName']=='FarmControl')['candidate']['runtimeEligible'] is False
    assert next(row for row in lock['unchangedBaseline'] if row['pluginName']=='SeeMore')['candidate']['runtimeEligible'] is False
    expected_local={item['pluginName'] for item in build_candidates}
    expected_local.update(item['pluginName'] for item in lock['modules'] if item.get('buildArtifact') or item.get('vendorArtifact'))
    assert {record['pluginName'] for record in records if record['sourceType']=='local'}==expected_local
    narcotics=next(record for record in records if record['pluginName']=='CopiMineNarcotics')
    assert '4a3e01050f8ab635f4760164fdb2723482b81923fafe75099506b6290b89b4d8' in narcotics['replaceSha256']
    assert 'ead5a29b561e96e0c46ab252743297e991f8fb1e21432ca47c1571c28a502928' in narcotics['replaceSha256']
    assert '6c1872fa0c5047a88fc90addc18d7c4537e3b70395636b8ee2980c82c22f3df9' in narcotics['replaceSha256']
    assert '60e2945678a7e2976ce7458531c173048de30787dbcd4d12fdb988af48cb127b' in narcotics['replaceSha256']
    assert '65a3821e5962f402a96fec1cfdab1d0a8a5c204d97dccb38a4b299c818802e3d' in narcotics['replaceSha256']
    assert 'ad9ec409e3bc82f3c1b40e57177f87a6ad5b06640a773f8479436d4a96adc0d5' in narcotics['replaceSha256']
    assert 'd53bafb4ebe9d65b8a0cfa67a82d8acf48ac0328639ceadfd9854dbad5f23a97' in narcotics['replaceSha256']
    assert '983fd1954eb85d8be8a5e18e0095200278933e930422854b4b19c19995fad116' in narcotics['replaceSha256']
    narcotics_candidate=next(item['candidate'] for item in lock['unchangedBaseline']
                             if item['pluginName']=='CopiMineNarcotics')
    assert narcotics_candidate['size'] == 370363
    assert narcotics_candidate['sha256'] == 'bea72432491f7afa0a0cbd23b608654b56899b8e463720f98dbfef0f39f2ef0d'
    assert narcotics_candidate['sha512'] == 'c2e3439ca69fd8ecc2b19aa654a38fb6bcd81e5be1da2da52934988edf1f75a5bb0170cbada994015500a6ccc6b2fcb6e8bc2bf4222a154c1b1fe6b5c14a586b'
    assert '835b8043781cc2bf580900c09653da7aa3436c8485266f7838b478ac5a047e70' in narcotics_candidate['replaceSha256']

def test_production_or_symlink_target_is_refused(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    with pytest.raises(ValueError,match='isolated'):mod.install_records(tmp_path,tmp_path/'minecraft/server',[r],stage,lambda port,host:False)

def test_unsafe_filename_and_wrong_plugin_identity_are_refused(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    with pytest.raises(ValueError,match='filename'):mod.validate_record({**r,'filename':'../escape.jar'})
    different=jar('Different','2');(stage/'new.jar').write_bytes(different)
    altered={**r,'sha512':hashlib.sha512(different).hexdigest(),'size':len(different)}
    with pytest.raises(ValueError,match='identity'):mod.install_records(tmp_path,server,[altered],stage,lambda port,host:False)

def test_luckperms_download_allows_only_its_official_release_url():
    mod=load()
    record={
        'pluginName':'LuckPerms',
        'filename':'LuckPerms-Bukkit-5.5.87.jar',
        'runtimeFilename':'LuckPerms-Bukkit-5.5.87.jar',
        'url':'https://download.luckperms.net/1674/bukkit/loader/LuckPerms-Bukkit-5.5.87.jar',
        'sha512':'0'*128,
        'sha256':'0'*64,
        'size':1024,
        'sourceType':'remote',
    }
    mod.validate_record(record)
    with pytest.raises(ValueError,match='source'):
        mod.validate_record({**record,'url':record['url'].replace('download.luckperms.net','download.luckperms.net.evil.example')})

def test_existing_plugin_identity_accepts_valid_bukkit_names_and_comments(tmp_path):
    mod=load()
    descriptors=(
        ('name: Multiverse-Core','Multiverse-Core'),
        ("name: 'My Plugin v2' # installed by an admin",'My Plugin v2'),
        ('name: "My.Plugin"','My.Plugin'),
        ('\ufeffname: CoreProtect','CoreProtect'),
        ('description:\n  name: NestedSpoof\nname: ActualPlugin','ActualPlugin'),
    )
    for index,(name_line,expected) in enumerate(descriptors):
        path=tmp_path/f'plugin-{index}.jar'
        path.write_bytes(descriptor_jar(name_line))
        assert mod.identity(path)==expected

def test_first_install_creates_missing_plugins_directory_after_validation(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    (server/'plugins/old.jar').unlink()
    (server/'plugins').rmdir()

    receipt=mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)

    assert (server/'plugins/new.jar').read_bytes()==new
    assert Path(receipt['backup']).is_dir()

def test_validate_only_does_not_create_missing_plugins_directory(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    (server/'plugins/old.jar').unlink()
    (server/'plugins').rmdir()

    mod.install_records(tmp_path,server,[r],stage,lambda port,host:False,validate_only=True)

    assert not (server/'plugins').exists()
    assert not (server/'migration-backups').exists()

def test_install_rejects_receipt_symlink_before_replacing_plugins(tmp_path,monkeypatch):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    receipt=server/'migration-plugin-receipt.json'
    original_lstat=Path.lstat
    def lstat_with_receipt_link(path):
        if path==receipt:
            return SimpleNamespace(st_mode=stat.S_IFLNK|0o777,st_file_attributes=0)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_receipt_link)

    with pytest.raises(ValueError,match='receipt|link|Symlink'):
        mod.install_records(tmp_path,server,[r],stage,lambda port,host:False)

    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()
    assert not (server/'migration-backups').exists()

def test_receipt_publication_uses_atomic_regular_file_replacement(tmp_path):
    mod=load();server=tmp_path/'local-runtime/end-rift-server-26.3';server.mkdir(parents=True)
    receipt=server/'migration-plugin-receipt.json'
    receipt.write_text('{"old": true}\n',encoding='utf-8')

    mod.write_receipt(server,{'nativeVerified':False,'installed':[]})

    assert json.loads(receipt.read_text(encoding='utf-8'))=={'nativeVerified':False,'installed':[]}
    assert not list(server.glob('migration-plugin-receipt.json.*.candidate'))

def test_receipt_publication_refuses_symlink_or_reparse_destination(tmp_path,monkeypatch):
    mod=load();server=tmp_path/'local-runtime/end-rift-server-26.3';server.mkdir(parents=True)
    receipt=server/'migration-plugin-receipt.json'
    original_lstat=Path.lstat
    def lstat_with_receipt_link(path):
        if path==receipt:
            return SimpleNamespace(st_mode=stat.S_IFLNK|0o777,st_file_attributes=0)
        return original_lstat(path)
    monkeypatch.setattr(Path,'lstat',lstat_with_receipt_link)

    with pytest.raises(ValueError,match='receipt|link|Symlink'):
        mod.write_receipt(server,{'installed':[]})

def test_second_replacement_failure_rolls_back_the_first(tmp_path,monkeypatch):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    other_old=jar('Other','1');other_new=jar('Other','2')
    (server/'plugins/other-old.jar').write_bytes(other_old)
    (stage/'other-new.jar').write_bytes(other_new)
    other={**r,'pluginName':'Other','filename':'other-new.jar','size':len(other_new),
           'sha512':hashlib.sha512(other_new).hexdigest(),'replaceSha256':[hashlib.sha256(other_old).hexdigest()]}
    original_copy=mod.shutil.copyfileobj
    def fail_second(source,destination,length=0):
        if Path(source.name).name=='other-new.jar':raise OSError('simulated installation failure')
        return original_copy(source,destination,length)
    monkeypatch.setattr(mod.shutil,'copyfileobj',fail_second)
    with pytest.raises(OSError,match='simulated'):mod.install_records(tmp_path,server,[r,other],stage,lambda port,host:False)
    assert (server/'plugins/old.jar').read_bytes()==old
    assert (server/'plugins/other-old.jar').read_bytes()==other_old
    assert not (server/'plugins/new.jar').exists() and not (server/'plugins/other-new.jar').exists()

def test_validate_only_does_not_move_or_create_anything(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    mod.install_records(tmp_path,server,[r],stage,lambda port,host:False,validate_only=True)
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()
    assert not (server/'migration-backups').exists()


def test_validate_only_cli_does_not_claim_installed_plugin_inventory(capsys, tmp_path, monkeypatch):
    mod=load()
    monkeypatch.setattr(mod, 'load_install_records', lambda root: [{'pluginName': 'Example'}])
    monkeypatch.setattr(mod, 'select_runtime_records', lambda records, unauthenticated_test_mode: records)
    monkeypatch.setattr(mod, 'install_records', lambda *args, **kwargs: {})

    mod.main(tmp_path, ['--validate-only'])

    output=capsys.readouterr().out
    assert 'Validated install plan for 1 plugin candidates' in output
    assert 'installed runtime inventory was not confirmed' in output
    assert 'Validated 1 plugins' not in output

def test_grim263_patch_changes_only_inventory_class_when_built():
    record=next(x for x in json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text())['modules']
                if x['pluginName']=='GrimAC')
    output_dir=Path(os.environ.get('COPIMINE_GRIM263_OUTPUT_DIR',str(ROOT/'build/minecraft-26.3/server-plugins')))
    original=Path(os.environ.get('COPIMINE_GRIM263_UPSTREAM_JAR',str(output_dir/record['upstreamFilename'])))
    patched=output_dir/record['filename']
    if not original.exists() or not patched.exists():pytest.skip('26.3 upstream plugin candidate and compatibility build required')
    with zipfile.ZipFile(original) as a,zipfile.ZipFile(patched) as b:
        assert set(a.namelist())==set(b.namelist())
        assert sorted(n for n in a.namelist() if a.read(n)!=b.read(n))==['ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.class']

def test_grim263_patch_build_is_reproducible():
    record=next(x for x in json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text())['modules']
                if x['pluginName']=='GrimAC')
    output_dir=Path(os.environ.get('COPIMINE_GRIM263_OUTPUT_DIR',str(ROOT/'build/minecraft-26.3/server-plugins')))
    original=Path(os.environ.get('COPIMINE_GRIM263_UPSTREAM_JAR',str(output_dir/record['upstreamFilename'])))
    patched=output_dir/record['filename']
    powershell=shutil.which('pwsh') or shutil.which('powershell')
    major=json.loads((ROOT/'tools/minecraft-26.3/profile.lock.json').read_text())['grimPatchJavaMajor']
    java_home=os.environ.get(f'JAVA_HOME_{major}_X64') or os.environ.get('JAVA_HOME')
    compiler=Path(java_home)/'bin/javac.exe' if java_home else None
    if not original.is_file() or not compiler or not compiler.is_file() or not powershell:
        pytest.skip('the pinned Grim patch JDK and inputs are required for reproducibility verification')
    java_home=Path(java_home)
    version=subprocess.run([str(java_home/'bin/java.exe'),'--version'],capture_output=True,text=True,timeout=15)
    detected=re.search(r'(?m)^(?:openjdk|java)(?: version)?[ " ]+(\d+)',version.stdout)
    if version.returncode or not detected or int(detected.group(1))!=major:
        pytest.skip('the pinned Java 21 Grim patch toolchain is not available on this test host')
    script=ROOT/'thirdparty/grim-creative-patch/minecraft-26.3/build.ps1'
    hashes=[]
    for _ in range(2):
        result=subprocess.run([
            powershell,'-NoProfile','-ExecutionPolicy','Bypass','-File',str(script),'-JavaHome',str(java_home),
            '-InputJarPath',str(original),'-OutputDirectory',str(output_dir),
        ],capture_output=True,text=True,timeout=60)
        assert result.returncode==0, result.stdout+result.stderr
        hashes.append(hashlib.sha256(patched.read_bytes()).hexdigest())
    assert hashes[0]==hashes[1]
    assert hashes[0]==record['sha256']
    patched_bytes=patched.read_bytes()
    assert len(patched_bytes)==record['size']
    assert hashlib.sha1(patched_bytes).hexdigest()==record['sha1']
    assert hashlib.sha512(patched_bytes).hexdigest()==record['sha512']

def test_admin_26_3_candidate_keeps_baseline_hash_and_tracks_runtime_build():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    record=next(p for p in lock['unchangedBaseline'] if p['pluginName']=='CopiMineUltimateAdminPlus')
    candidate=record['candidate']
    assert record['sha256']=='63c379a882600fd2e5c9ee227632c231d7657e8aefcf8c0fe142a7b6702796a6'
    assert candidate['apiVersion']=='26.3'
    assert candidate['runtimeFilename']==record['filename']
    assert len(candidate['sha256'])==64
    built=ROOT/candidate['buildArtifact']
    runtime=MIGRATION_RUNTIME/'plugins'/candidate['runtimeFilename']
    assert built.is_file(), 'the pinned 26.3 AdminPlus candidate build is missing'
    artifacts=[built]+([runtime] if runtime.is_file() else [])
    for artifact in artifacts:
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        # An installed jar may still be the explicitly replaceable predecessor until the
        # runtime is reinstalled; the built candidate itself must always match exactly.
        assert digest == candidate['sha256'] or (
            artifact is runtime and digest in candidate.get('replaceSha256', [])
        )
        with zipfile.ZipFile(artifact) as jar_file:
            assert 'messages_ru.yml' in jar_file.namelist()
            # Artifact YAML is canonical LF; a Windows checkout keeps CRLF, so compare content only.
            assert (jar_file.read('messages_ru.yml').replace(b'\r\n', b'\n')
                    == (ROOT/'copimine-admin-plugin/messages_ru.yml').read_bytes().replace(b'\r\n', b'\n'))

def test_first_party_replacements_accept_the_exact_installed_predecessor_jars():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baselines={row['pluginName']:row for row in lock['unchangedBaseline']}
    installed_predecessors={
        'CopiMineArtifacts':[
            '64340259981c4e4bb56e3e1168c8f901f2b658794338eeef458b2eb0617de5ef',
            '3202c55fb6b94423468f2eccf35b82d3e7a0cff39198aef2bf3777f0d2128ed8',
        ],
        'CopiMineUltimateAdminPlus':['6ef8296d397f4cfea46dd9fa137ae18cecdac71dd0c259a9e512ad86dbdcd175'],
        'CopiMineNarcotics':['983fd1954eb85d8be8a5e18e0095200278933e930422854b4b19c19995fad116'],
    }
    for plugin_name,digests in installed_predecessors.items():
        for digest in digests:
            assert digest in baselines[plugin_name].get('replaceSha256',[]), (
                f'{plugin_name} locked installer does not recognize its verified installed predecessor'
            )
    end_event_candidate=baselines['CopiMineEndEvent']['candidate']
    for digest in (
        '9274112cc63ed39e62b1a6e3f5d7284b9d92876137c248f7507d65b0b61b1118',
        'ede85b42fd29c8d917b552a5d4028fa9a9ed9a129f8db0b6982f2b93c19893bf',
    ):
        assert digest in end_event_candidate.get('replaceSha256',[]), (
            'CopiMineEndEvent candidate lock must recognize the exact previously installed test/runtime JAR'
        )

def test_all_remaining_first_party_26_3_plugins_track_their_build_and_runtime_upgrade_path():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baselines={p['pluginName']:p for p in lock['unchangedBaseline']}
    expected_builds={
        'AuthEffects':'AuthEffects-26.3.jar',
        'CopiMineArtifacts':'CopiMineArtifacts-26.3.jar',
        'CopiMineEconomyCore':'CopiMineEconomyCore-26.3.jar',
        'CopiMineElectionCore':'CopiMineElectionCore-26.3.jar',
        'CopiMineNarcotics':'CopiMineNarcotics-26.3.jar',
        'CopiMineWorldCore':'CopiMineWorldCore-26.3.jar',
    }
    for plugin_name,filename in expected_builds.items():
        baseline=baselines[plugin_name]
        candidate=baseline['candidate']
        assert candidate['filename']==filename
        assert candidate['runtimeFilename']==baseline['filename']
        assert candidate['apiVersion']=='26.3'
        assert candidate['compatibilityStatus']=='requires-runtime-validation'
        assert candidate['nativeVerified'] is lock['nativeVerified']

        built=ROOT/candidate['buildArtifact']
        runtime=MIGRATION_RUNTIME/'plugins'/candidate['runtimeFilename']
        assert built.is_file(), f'missing 26.3 build artifact for {plugin_name}'
        artifacts=[built]
        if runtime.is_file():
            runtime_sha256=hashlib.sha256(runtime.read_bytes()).hexdigest()
            assert runtime_sha256==candidate['sha256'] or runtime_sha256 in candidate.get('replaceSha256',[]), (
                f'{plugin_name} installed runtime JAR is neither the candidate nor an explicitly replaceable prior build'
            )
            artifacts.append(runtime)
        for artifact in artifacts:
            artifact_bytes=artifact.read_bytes()
            artifact_sha256=hashlib.sha256(artifact_bytes).hexdigest()
            if artifact==built:
                assert len(artifact_bytes)==candidate['size']
                assert artifact_sha256==candidate['sha256']
                assert hashlib.sha512(artifact_bytes).hexdigest()==candidate['sha512']
            else:
                assert artifact_sha256==candidate['sha256'] or artifact_sha256 in candidate.get('replaceSha256',[])
            with zipfile.ZipFile(artifact) as jar_file:
                plugin_yml=jar_file.read('plugin.yml').decode('utf-8')
                required_resource={
                    'CopiMineArtifacts':('items.yml', ROOT/'copimine-artifacts/items.yml'),
                }.get(plugin_name)
                if required_resource:
                    resource_name,source_path=required_resource
                    assert resource_name in jar_file.namelist(), (
                        f'{plugin_name} migration JAR is missing required runtime resource {resource_name}'
                    )
                    # Artifact YAML is canonical LF; a Windows checkout keeps CRLF, so compare content only.
                    assert (jar_file.read(resource_name).replace(b'\r\n', b'\n')
                            == source_path.read_bytes().replace(b'\r\n', b'\n')), (
                        f'{plugin_name} migration JAR resource {resource_name} differs from its source catalog'
                    )
            assert f'name: {plugin_name}' in plugin_yml
            assert "api-version: '26.3'" in plugin_yml

def test_end_event_26_3_candidate_tracks_and_installs_the_real_target_jar():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baseline=next(p for p in lock['unchangedBaseline'] if p['pluginName']=='CopiMineEndEvent')
    assert baseline['sha256']=='c32ff397bfa01a4279524ff9d9b31dcd8b18a0f7836a09cabecdbcd2e07f0cc3'
    candidate=baseline['candidate']
    assert candidate['filename']=='CopiMineEndEvent-26.3.jar'
    assert candidate['runtimeFilename']=='CopiMineEndEvent.jar'
    assert candidate['size']==1969295
    assert candidate['sha256']=='7f4c4cafa2a6aca85bea236337b6a200dec8761559a88ee4740c84db9a6204b0'
    assert candidate['sha512']=='885f1e620dce63206d3b135d73f3cc56022232609a9c1054707db1f15282c69cd4a424a92044c794ec40f797375d30d909043f0cc613dbd195589ac34379d5bb'
    assert candidate['apiVersion']=='26.3'
    assert candidate['buildArtifact']=='build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar'
    assert candidate['nativeVerified'] is lock['nativeVerified']
    assert baseline['replaceSha256']==[
        '5342d7793aa90bd36208f60f5ed44443a54ef8a44e62064112fcc3324db8b117',
        '86820193e67f166d79128d224e4ab9abf7dafce2c6efaad21b4dc6134bfaf4d7',
        'e2335f9462f61c0f255a87cc3609520099f3836843f46aee6dd0a910dad636bd',
        'd63ed7c3c3d810a5ecb8ea8f497ecb24004cf195381e410bfbb8d182094842c0',
        '93211233ec5649ed5e7e389ce8c557cb17b7ddee6aaabc93130cfcf84d1462ea',
        '825602f54b15615ecec9a3a6b10e3f4f45e41d8698d14712da33252659c5ae8d',
        'c07dd5b1a741bef33ff10f6a587f847782d21ea232c9ceb3c29af4882f44a038',
        'f6f09f56a663620b7a6f1e537071adee78b66f33137b20fad40ab24c20fe9f7d',
        '014b8ce72de3aaabfee26a62bc0341ec606d5683bc404f9edcb2496b1884865e',
        '10dd52eff90c85217caff45bfd08053eae8ee32d8349120d2f436b2f80ce6305',
        'a667e63d60d8441a24e80fa4131510bd30f6e58a88dd5f40a506a00bb697d8a0',
        'c50a03cd92eec82a97fb8acecf58301cc8da85488b04b20b0a4ca2458f2320be',
        'e2d0e255e67e9666cc5b82431461e52136667538b6ba81ebe619cb561a17dc9d',
        '43a06dd862a1cc8ee0961867de38cd2ef7b065cb40237457303c5999accc299c',
        'a00987c98b7cde88e8c0bb6518286511e114e443550c43952aacd9ddd0116f11',
        '985d42e6b4f6f44b034dd399bc12e5754be69e6121b8986156c087e18cfc7d22',
        'e3541f93572bd31f2f5c212ccd7d07c174806ddb3d2422f113052226e71bfd45',
        '73fcb907d24657169a388541457e02f492f9471207599226d56ecd51001eb18e',
        '31e2ba717b2ae8eeb6a602be1b519dcf979e0800393471b3ba6a532ca119133d',
        '6d1adbd3616d58168bb901391dacc24b3ab0afb3e9410d3a1649a2c38eeb50f9',
        'ae8cc781ae09287e50ec366fdb5860a11e2b5415659b3628299d0f3dae2920e4',
        'e446d6e9f7b03a01250985d049847d280ffdfcf24d0c9599f726b31aced50233',
        'de68867e66062de531399a9f38691057c12b0b448a100aa765587edfe9898d4d',
        'c9ca37ebab60b629be5e04e945524ecb5c3b8ec0bb61362e3deb10dbd94a14b5',
    ]
    assert candidate['replaceSha256']==[
        '5342d7793aa90bd36208f60f5ed44443a54ef8a44e62064112fcc3324db8b117',
        'fc81375344c583c979ee4aa03446da9295d02b9f09486bf374cdc4de9cce2e13',
        '8105e10a11a7f76e41f75333e3c71e8abffb96d7ae1e37759a03fdfa807837c0',
        '33301a85349352168ef6a9c0bb431d73c361cf1e7a71779e27b60104462270ab',
        '949fb0a9cdbffd62ad9c340b68612768bea1f1e10bb578af3a10dd2d55bf9fb9',
        '07a5b6ecd77077061b3cee57082e9d9cfe840418e50bd093e3573ff613ffbf9b',
        '12f032de165dad419f990c97adfce055916302c93659f9d422c4c861468d543c',
        '066624e84499bcfe9672471dcd9e03047b76397b7493657ae769cf4d64db3ddb',
        'c840119e531d827f883683ca729938a8b6aee6001eb219acc6ae5dca6fc56596',
        'd56258aeb5a488ef2c0c316c2c93589aaa7eb7324304ca6d54c4c279a74b2aee',
        '5212e427e7942729ecd85234aa4b4293a45fb30a00bb5f91f696914a48edfe64',
        'bd5572bcbcf206cdd2c68c1fdd993ebea9f1f5199d86cdf22ae31892b31081a2',
        '335e716a1f1e8a7bb7791ea7feca06d04aa7ae2ec8d9ffc162e68b7ae05fdbe7',
        'a350e1a4b06e0765560219ea8dcc732e3c44b76e5931ffb3318f6a93c15bda97',
        'ba2f880a2facca25bbc3c7f8a6e39939ecc3f9d7f71f32028f5b2da609a2c995',
        'c9eec1ad834f89acf0595db8ab316714add829bec8573f062ad8eb0d1b26f8e1',
        'e7ef03e68a608d3a6b8447dbb072d02c4427ef3d116263d285b23e1b39701742',
        '152704c4b62488f2bf71ed67395a2de8fd906175ea7479dd6544769434846340',
        'd0b61f8c8d9e073729fdb67c946314625c13d018ecec78d8a16659f80a578209',
        'aa8f9ea94ee471cd28efc4f574a16460412a2886e749ca4956bb337372561a42',
        'f5c3b01da174296256d21c69061c3b3d22f76bf3e5fbae267557d0b4ffdaf5cb',
        '7026719d47ec3c7acd66bf3b3e77357782f6f71d54fe5684279b86c58d5e8ea5',
        'ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c',
        '891da7cd4c450a32ffdb1c8c78724e8cbc410c2ab64afeac4209f9dc48e30d7e',
        '10dd52eff90c85217caff45bfd08053eae8ee32d8349120d2f436b2f80ce6305',
        'c50a03cd92eec82a97fb8acecf58301cc8da85488b04b20b0a4ca2458f2320be',
        'e2d0e255e67e9666cc5b82431461e52136667538b6ba81ebe619cb561a17dc9d',
        '9274112cc63ed39e62b1a6e3f5d7284b9d92876137c248f7507d65b0b61b1118',
        'ede85b42fd29c8d917b552a5d4028fa9a9ed9a129f8db0b6982f2b93c19893bf',
        'ec7b12e9f670c784f25df3a6ad2dc9bf9bc4399799b1b544b1574f6f21ba0b14',
        '43a06dd862a1cc8ee0961867de38cd2ef7b065cb40237457303c5999accc299c',
        'a00987c98b7cde88e8c0bb6518286511e114e443550c43952aacd9ddd0116f11',
        '985d42e6b4f6f44b034dd399bc12e5754be69e6121b8986156c087e18cfc7d22',
        '66aabae0c32a037281026b38f2b89e84e21b3a4062063c8275568d0459124853',
        '1f7af4f92349a0c7f3cca951728620ea1f2abab7f852a52822ac5f89b96e839f',
        'b8ef1e9c4e70b5c9bafc076e96fe637f162de79f388449ce1a5d369997bb5964',
        'e7f3c5bf2089e13ae2b44a971ea65e0349dacdffffbcdb4011c336f47d357662',
        'b843b3abeb90e81fb398f375e97b0b92f162cf2222d26ddb529130f528d834d9',
        'dbdeb9f7492e5d340d46201fccdd31c1ed19089da2803aed713432d9186aeca8',
        'a8a7a971d44fb330689e7659a184d968de712e797f8cb73f8029ad3b8c699599',
        '81aa434a619b644311147f38416982336d211c3530a364242e3dbae6335b223d',
        'a1dd3845cd443e69412cfae3ccd987edf14e5a537dbcce28d9bcaf3f5c9d003d',
        '903e20a69483411a425de3a61b33622f563e2e9aeda7d1b94617dabbb90155db',
        'e3541f93572bd31f2f5c212ccd7d07c174806ddb3d2422f113052226e71bfd45',
        '73fcb907d24657169a388541457e02f492f9471207599226d56ecd51001eb18e',
        '31e2ba717b2ae8eeb6a602be1b519dcf979e0800393471b3ba6a532ca119133d',
        '6d1adbd3616d58168bb901391dacc24b3ab0afb3e9410d3a1649a2c38eeb50f9',
        'ae8cc781ae09287e50ec366fdb5860a11e2b5415659b3628299d0f3dae2920e4',
        'e446d6e9f7b03a01250985d049847d280ffdfcf24d0c9599f726b31aced50233',
        'de68867e66062de531399a9f38691057c12b0b448a100aa765587edfe9898d4d',
        'c9ca37ebab60b629be5e04e945524ecb5c3b8ec0bb61362e3deb10dbd94a14b5',
    ]

    built=ROOT/candidate['buildArtifact']
    assert built.is_file(), 'the pinned first-party 26.3 candidate build is missing'
    built_bytes=built.read_bytes()
    assert len(built_bytes)==candidate['size']
    assert hashlib.sha256(built_bytes).hexdigest()==candidate['sha256']
    assert hashlib.sha512(built_bytes).hexdigest()==candidate['sha512']
    with zipfile.ZipFile(built) as jar_file:
        plugin_yml=jar_file.read('plugin.yml').decode('utf-8')
    assert 'api-version: \'26.3\'' in plugin_yml
    runtime=MIGRATION_RUNTIME/'plugins'/candidate['runtimeFilename']
    if runtime.is_file():
        runtime_sha256=hashlib.sha256(runtime.read_bytes()).hexdigest()
        assert runtime_sha256==candidate['sha256'] or runtime_sha256 in candidate['replaceSha256']

def test_active_26_3_plugin_directory_matches_the_unique_locked_inventory():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    assert len({p['pluginName'] for p in lock['modules']})==len(lock['modules']), 'module lock must not contain duplicate plugin names'
    assert len({p['pluginName'] for p in lock['unchangedBaseline']})==len(lock['unchangedBaseline']), 'baseline lock must not contain duplicate plugin names'
    expected={
        p['pluginName']:{
            'filename':p.get('runtimeFilename') or p['filename'],
            'sha256':p.get('buildSha256') if p.get('buildScript') else p['sha256'],
        }
        for p in lock['modules']
    }
    module_names=set(expected)
    for baseline in lock['unchangedBaseline']:
        if baseline['pluginName'] in module_names or baseline.get('candidate',{}).get('runtimeEligible') is not True:
            continue
        candidate=baseline.get('candidate')
        expected[baseline['pluginName']]={
            'filename':candidate['runtimeFilename'],
            'sha256':candidate['sha256'],
        }

    assert len({entry['filename'] for entry in expected.values()})==len(expected), 'plugin lock must not assign one runtime filename to multiple plugins'
    runtime=MIGRATION_RUNTIME/'plugins'
    if not runtime.is_dir():pytest.skip('isolated 26.3 runtime is not installed in this checkout')
    assert len(expected)==30, 'runtime-eligible candidates must collapse to one entry per installed plugin'
    server=runtime.parent
    receipt=json.loads((server/'migration-plugin-receipt.json').read_text(encoding='utf-8'))
    if receipt.get('authenticationMode')=='disabled-for-local-testing':
        disabled_authme=expected.pop('AuthMe')
        backups=list((server/'migration-backups/plugins').rglob(disabled_authme['filename']))
        assert len(backups)==1, 'test-only AuthMe disablement must preserve exactly one locked backup'
        assert backups[0].is_file() and not backups[0].is_symlink(), 'pinned AuthMe backup must be a regular file, not a symlink'
        assert hashlib.sha256(backups[0].read_bytes()).hexdigest()==disabled_authme['sha256']
        disabled_auth_effects=expected.pop('AuthEffects')
        auth_effects_lock=next(row for row in lock['unchangedBaseline'] if row['pluginName']=='AuthEffects')
        auth_effects_backup_filename=(auth_effects_lock['candidate'].get('runtimeFilename')
                                      or auth_effects_lock['candidate']['filename'])
        auth_effects_backups=list((server/'migration-backups/plugins').rglob(auth_effects_backup_filename))
        assert len(auth_effects_backups)==1, 'test-only AuthEffects disablement must preserve exactly one locked backup'
        assert hashlib.sha256(auth_effects_backups[0].read_bytes()).hexdigest()==disabled_auth_effects['sha256']
    else:
        assert receipt.get('authenticationMode')=='AuthMe-6.0.1', 'runtime receipt must declare a recognized authentication mode'
    actual_files={path.name for path in runtime.glob('*.jar')}
    expected_files={entry['filename'] for entry in expected.values()}
    assert actual_files==expected_files, f'unlocked or missing plugin jars: {actual_files ^ expected_files}'
    receipt_rows=receipt['pluginInventory']
    receipt_inventory={entry['pluginName']:{'filename':entry['filename'],'sha256':entry['sha256']} for entry in receipt_rows}
    assert len(receipt_inventory)==len(receipt_rows), 'runtime receipt must not contain duplicate plugin rows'
    assert receipt_inventory==expected, 'runtime receipt names, filenames, and digests must match the active locked plugin inventory'
    active_expected=dict(expected)
    local_override_path=server/'migration-local-end-event-build.json'
    if local_override_path.is_file():
        local_override=json.loads(local_override_path.read_text(encoding='utf-8'))
        event_entry=expected['CopiMineEndEvent']
        event_path=runtime/event_entry['filename']
        backup_path=Path(local_override['replacementBackup']).resolve()
        backup_root=(server/'migration-backups/plugins').resolve()
        assert local_override['scope']=='local-development-override'
        assert local_override['plugin']=='CopiMineEndEvent'
        assert Path(local_override['runtimePath']).resolve()==event_path.resolve()
        assert local_override['replacedJarSha256']==event_entry['sha256']
        assert backup_path.is_relative_to(backup_root)
        assert hashlib.sha256(backup_path.read_bytes()).hexdigest()==event_entry['sha256']
        active_expected['CopiMineEndEvent']={
            'filename':event_entry['filename'],'sha256':local_override['builtJarSha256'],
        }
    for plugin_name,entry in active_expected.items():
        artifact=runtime/entry['filename']
        assert hashlib.sha256(artifact.read_bytes()).hexdigest()==entry['sha256'], plugin_name

def test_authme_26_3_candidate_is_official_paper_release_and_replaces_known_baseline():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baseline=next(p for p in lock['unchangedBaseline'] if p['pluginName']=='AuthMe')
    candidate=next(p for p in lock['modules'] if p['pluginName']=='AuthMe')
    assert baseline['version']=='5.6.0-bCUSTOM'
    assert baseline['sha256']=='80371ba80087c14e49fad908c581ea7677943db4b968760c053d3bb0d26764d1'
    assert candidate['version']=='6.0.1'
    assert candidate['versionId']=='fPuH4uYs'
    assert candidate['projectId']=='9js4IEHC'
    assert candidate['url']=='https://cdn.modrinth.com/data/9js4IEHC/versions/fPuH4uYs/AuthMe-6.0.1-Paper.jar'
    assert candidate['filename']=='AuthMe-6.0.1-Paper.jar'
    assert candidate['sha512']=='832eb1570f1887493dc09bd183360c6c890aafe24fbc635d36f7c3162c339f064e3a0e082519defe7e67569c1d78072f582cb49c14a24d125d4ece463b765271'
    assert candidate['sha256']=='7704335e9e73a634d9d926344f77897f4c74f78f82453a59f5aa0f8d2722450a'
    assert candidate['size']==1_153_596
    assert candidate['replaceSha256']==[baseline['sha256']]
    assert candidate['platform']=='paper' and '26.3' in candidate['gameVersions']
    staged=ROOT/'build/minecraft-26.3/server-plugins'/candidate['filename']
    if staged.is_file():
        assert hashlib.sha256(staged.read_bytes()).hexdigest()==candidate['sha256']
        assert hashlib.sha512(staged.read_bytes()).hexdigest()==candidate['sha512']

def test_authme_replacement_preserves_auth_database_and_config(tmp_path):
    mod=load()
    old=jar('AuthMe','5.6.0-bCUSTOM');new=jar('AuthMe','6.0.1')
    server=tmp_path/'local-runtime/end-rift-server-26.3';plugins=server/'plugins';authdir=plugins/'AuthMe'
    authdir.mkdir(parents=True)
    (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n')
    prepare_owned_runtime_fixture(tmp_path,server)
    old_path=plugins/'AuthMe-5.6.0.jar';old_path.write_bytes(old)
    config=b'dataSource:\n  backend: POSTGRESQL\n'
    database=b'auth-record-fixture-not-a-live-database'
    (authdir/'config.yml').write_bytes(config);(authdir/'auths.db').write_bytes(database)
    stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True);(stage/'AuthMe-6.0.1-Paper.jar').write_bytes(new)
    record={'pluginName':'AuthMe','version':'6.0.1','filename':'AuthMe-6.0.1-Paper.jar',
            'url':'https://cdn.modrinth.com/data/9js4IEHC/versions/fPuH4uYs/AuthMe-6.0.1-Paper.jar',
            'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),
            'replaceSha256':[hashlib.sha256(old).hexdigest()]}
    mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)
    assert (plugins/'AuthMe-6.0.1-Paper.jar').read_bytes()==new
    assert (authdir/'config.yml').read_bytes()==config
    assert (authdir/'auths.db').read_bytes()==database
    backups=list((server/'migration-backups/plugins').rglob('AuthMe-5.6.0.jar'))
    assert len(backups)==1 and backups[0].read_bytes()==old


def test_clearlag_replacement_keeps_custom_settings_and_backs_up_old_jar(tmp_path):
    mod=load();old=jar('ClearLag','1.12.1');new=jar('ClearLag','1.14.4')
    server=tmp_path/'local-runtime/end-rift-server-26.3';plugins=server/'plugins';config_dir=plugins/'ClearLag'
    config_dir.mkdir(parents=True)
    (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n')
    prepare_owned_runtime_fixture(tmp_path,server)
    old_path=plugins/'ClearLag.jar';old_path.write_bytes(old)
    config=b'auto-updater:\n  enabled: false\nauto-clear:\n  clear-entities: false\n'
    (config_dir/'config.yml').write_bytes(config)
    candidate=next(p for p in json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))['modules'] if p['pluginName']=='ClearLag')
    stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True);(stage/candidate['filename']).write_bytes(new)
    record={'pluginName':'ClearLag','version':'1.14.4','filename':candidate['filename'],
            'url':candidate['url'],'sha512':hashlib.sha512(new).hexdigest(),'size':len(new),
            'replaceSha256':[hashlib.sha256(old).hexdigest()]}
    mod.install_records(tmp_path,server,[record],stage,lambda port,host:False)
    assert (plugins/candidate['filename']).read_bytes()==new
    assert (config_dir/'config.yml').read_bytes()==config
    backups=list((server/'migration-backups/plugins').rglob('ClearLag.jar'))
    assert len(backups)==1 and backups[0].read_bytes()==old


def test_packetevents_26_3_release_is_pinned_for_authme_packet_protections():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    candidate=next((p for p in lock['modules'] if p['pluginName']=='packetevents'),None)
    assert candidate is not None, 'AuthMe 6.0.1 packet protections need a pinned PacketEvents runtime'
    assert candidate['version']=='2.14.0'
    assert candidate['versionId']=='m78nFxYg'
    assert candidate['projectId']=='HYKaKraK'
    assert candidate['filename']=='packetevents-spigot-2.14.0.jar'
    assert candidate['url']=='https://cdn.modrinth.com/data/HYKaKraK/versions/m78nFxYg/packetevents-spigot-2.14.0.jar'
    assert candidate['sha256']=='060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7'
    assert candidate['sha512']=='18a942cb55f64783a63b28deea2cebbbe782e5d6a1089f6700430e183bfbce0a45398a640ed0dff4361b55190dcf47017f2ca7788f4a5c8c9efad1f549a63fe3'
    assert candidate['size']==5_723_413
    assert candidate['platform']=='paper' and '26.3' in candidate['gameVersions']
    assert candidate['compatibilityStatus']=='requires-runtime-validation'
    assert candidate['nativeVerified'] is lock['nativeVerified']
    assert candidate['replaceSha256']==[]
    staged=ROOT/'build/minecraft-26.3/server-plugins'/candidate['filename']
    if staged.is_file():
        assert hashlib.sha256(staged.read_bytes()).hexdigest()==candidate['sha256']
        assert hashlib.sha512(staged.read_bytes()).hexdigest()==candidate['sha512']


def test_clearlag_26_3_candidate_is_pinned_and_replaces_known_baseline():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baseline=next(p for p in lock['unchangedBaseline'] if p['pluginName']=='ClearLag')
    candidate=next(p for p in lock['modules'] if p['pluginName']=='ClearLag')
    assert baseline['version']=='1.12.1'
    assert baseline['sha256']=='aea791f8859f18e62f6b5b2d61e49a7b866c8a8a5878345f445b264e7853f44c'
    assert candidate['version']=='1.14.4'
    assert candidate['versionId']=='SqHwEbXk'
    assert candidate['projectId']=='7bhTp28M'
    assert candidate['channel']=='beta'
    assert candidate['filename']=='ClearLag 1.14.4 VER 1.18-26.3.jar'
    assert candidate['url']=='https://cdn.modrinth.com/data/7bhTp28M/versions/SqHwEbXk/ClearLag%201.14.4%20VER%201.18-26.3.jar'
    assert candidate['sha1']=='a73f88646f9e8edb8ab509deaa695c84aad5206d'
    assert candidate['sha512']=='ad43b046c7c0ee3f7adf61c2ec080cb20e71d68b803e9222bfb5861a8b061b65975601190387fda7a4aeeaf3985e8b69e6519c4fe8894cbfbe2694dbf16b8187'
    assert candidate['sha256']=='1f1e36e2328914254836c82f7e63194db7dcbfeacc1382f128936605ac350654'
    assert candidate['size']==304_296
    assert candidate['license']['id']=='LicenseRef-All-Rights-Reserved'
    assert candidate['replaceSha256']==[
        baseline['sha256'],
        '3b73f36478f7ccebb2c1fc482a2724c1b5e75dd0835c0ec35a765249639991ba',
    ]
    assert baseline['candidate']['version']=='1.14.4'
    assert baseline['candidate']['versionId']=='SqHwEbXk'
    assert baseline['candidate']['filename']==candidate['filename']
    assert baseline['candidate']['sha256']==candidate['sha256']
    assert baseline['candidate']['sha512']==candidate['sha512']
    assert baseline['candidate']['sha1']==candidate['sha1']
    assert baseline['candidate']['size']==candidate['size']
    assert '26.3' in candidate['gameVersions']
    assert candidate['compatibilityStatus']=='requires-runtime-validation'
    assert candidate['nativeVerified'] is lock['nativeVerified']

    staged=ROOT/'build/minecraft-26.3/server-plugins'/candidate['filename']
    assert staged.is_file(), 'the pinned ClearLag 26.3 candidate is missing'
    assert staged.stat().st_size==candidate['size']
    assert hashlib.sha512(staged.read_bytes()).hexdigest()==candidate['sha512']
    assert hashlib.sha256(staged.read_bytes()).hexdigest()==candidate['sha256']
    assert load().identity(staged)=='ClearLag'


def test_worldedit_26_3_candidate_uses_latest_stable_and_keeps_worldguard_pair():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    candidate=next(p for p in lock['modules'] if p['pluginName']=='WorldEdit')
    worldguard=next(p for p in lock['modules'] if p['pluginName']=='WorldGuard')

    assert candidate['version']=='7.4.6'
    assert candidate['channel']=='release'
    assert candidate['versionId']=='YFnqiUlL'
    assert candidate['projectId']=='1u6JkXh5'
    assert candidate['filename']=='worldedit-bukkit-7.4.6.jar'
    assert candidate['url']=='https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar'
    assert candidate['sha1']=='4de34cb32f338a7727f0c1cc4a0f7ebff3fcc3a3'
    assert candidate['sha256']=='e0ce67d870ad1fef8a40dd9d7926360caf3bb44722438545d6b20b55f82a0c91'
    assert candidate['sha512']=='1fe3975132857093f971fed9efc601299bfa76fa7885f52a542fee41e7a0165ca9cb3cbcf5ae244474887d774d650b0db6f8f79aa80f98b245dea44f1c1b4152'
    assert candidate['size']==7_870_216
    assert candidate['artifactVersion']=='7.4.6'
    assert candidate['platform']=='paper' and '26.3' in candidate['gameVersions']
    assert candidate['replaceSha256']==[
        '75df26f120ff424e62cba3a6e41b3b4e496251d5613b3c55c5f35b96bd8fd57c',
        '166365c44bad4cd916200b288cf391d3770e3197d1eca84ff74f3914d933e289',
    ]
    assert candidate['compatibilityStatus']=='requires-runtime-validation'
    assert candidate['nativeVerified'] is lock['nativeVerified']

    # WorldGuard requires Bukkit WorldEdit; keep its supported 26.3 pin paired.
    assert worldguard['version']=='7.0.20'
    assert worldguard['versionId']=='9YdeGXiK'
    assert worldguard['filename']=='worldguard-bukkit-7.0.20.jar'
    assert worldguard['sha1']=='dd5fa9be6f5faf8a053a597491d194f2de8963ec'
    assert worldguard['sha256']=='8768a49bcbecbb7a413ad134ee72d9871cdf967b7d938e6b96bb0ca72bb1467b'
    assert worldguard['sha512']=='d7ad3f8b14c57deceb409dc8997fe3d7f0a98dd4158c52648111bfbe6934ca9be53fa42ece4d60d9c2d9da33f7d6ecf597117991bd3b8e7afbf308cd925e409c'
    assert worldguard['size']==1_200_197
    assert worldguard['platform']=='paper' and worldguard['gameVersions']==['26.3']
    assert worldguard['compatibilityStatus']=='requires-runtime-validation'
    assert worldguard['nativeVerified'] is False
    assert worldguard['platform']=='paper' and '26.3' in worldguard['gameVersions']

def test_protocollib_26_3_packet_mapping_evidence_is_distinct_from_tested_maximum():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    candidate=next(p for p in lock['modules'] if p['pluginName']=='ProtocolLib')
    assert candidate['version']=='dev-d89a5de'
    assert candidate['upstreamPacketMappingVersion']=='26.3'
    assert candidate['upstreamMappingCommit']=='583353e9fa3dcec1f6da06d89c189d4ecda1afd4'
    assert candidate['upstreamMappingUrl']=='https://github.com/dmulloy2/ProtocolLib/commit/583353e9fa3dcec1f6da06d89c189d4ecda1afd4'
    assert candidate['upstreamMaximumTestedVersion']=='26.2'
    assert candidate['compatibilityStatus']=='requires-runtime-validation'
    assert candidate['nativeVerified'] is lock['nativeVerified']

    runtime=MIGRATION_RUNTIME/'plugins'/candidate['filename']
    if not runtime.is_file():
        pytest.skip('ProtocolLib runtime candidate is not installed in this checkout')
    assert hashlib.sha256(runtime.read_bytes()).hexdigest()==candidate['sha256']
    with zipfile.ZipFile(runtime) as archive:
        packets=archive.read('com/comphenix/protocol/PacketType$Play$Server.class')
    assert b'v26_3' in packets
    for packet in [b'ADD_TRANSIENT_BLOCK',b'HURT_ANIMATION',b'SHOW_DIALOG']:
        assert packet in packets


def test_protocollib_dev_build_is_vendored_so_the_mutable_upstream_tag_cannot_change_ci(tmp_path):
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    candidate=next(p for p in lock['modules'] if p['pluginName']=='ProtocolLib')
    assert candidate['vendorArtifact']=='thirdparty/server-plugins/'+candidate['filename']
    artifact=ROOT/candidate['vendorArtifact']
    assert artifact.is_file()
    assert artifact.stat().st_size==candidate['size']
    digests=hashlib.sha256(artifact.read_bytes()).hexdigest(),hashlib.sha512(artifact.read_bytes()).hexdigest()
    assert digests==(candidate['sha256'],candidate['sha512'])
    records=load().load_install_records(ROOT,lock)
    assert next(r for r in records if r['pluginName']=='ProtocolLib')['sourceType']=='local'

    only={'modules':[candidate]}
    assert len(load().load_install_records(ROOT,only))==1
    vendored=tmp_path/candidate['vendorArtifact']
    vendored.parent.mkdir(parents=True)
    vendored.write_bytes(artifact.read_bytes()[:-1])
    with pytest.raises(ValueError,match='Vendored plugin artifact size or filename differs from its lock'):
        load().load_install_records(tmp_path,only)


def test_remaining_official_server_plugins_have_verified_26_3_release_candidates():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baselines={p['pluginName']:p for p in lock['unchangedBaseline']}
    candidates={p['pluginName']:p for p in lock['modules']}
    expected={
        'Chunky':{
            'version':'1.5.3','versionId':'MdY6JATr','projectId':'fALzjamp',
            'filename':'Chunky-Bukkit-1.5.3.jar',
            'url':'https://cdn.modrinth.com/data/fALzjamp/versions/MdY6JATr/Chunky-Bukkit-1.5.3.jar',
            'sha512':'43ffecc6e6a734b752da41575bbb316526c124c3f878942437d5133c377bfbd9b78bda975520dc074d7158c15dade58a444ccd0fd8d8a25d165b6fc450140422',
            'sha256':'530d2c7430a96a39957391b7088be144daa3108f7665896d1c23aa8dd4af32f3','size':304_616,
        },
        'LuckPerms':{
            'version':'5.5.87',
            'filename':'LuckPerms-Bukkit-5.5.87.jar',
            'url':'https://download.luckperms.net/1674/bukkit/loader/LuckPerms-Bukkit-5.5.87.jar',
            'sha512':'e4b277a5c80e78c96a0e7530bd3c9839e2d2bf294f339e5ae094d3da9b2e84f201bfe1b9751276519c3d057104065055bd1fec7e2be803524407267e7153d57d',
            'sha256':'09d07b68965717976d2bab2f8d10436676f7c68d90bf6d24628cc0e5228bf406','size':1_509_479,
        },
        'GSit':{
            'version':'3.7.0',
            'filename':'GSit-3.7.0.jar',
            'url':'https://hangarcdn.papermc.io/plugins/Gecolay/GSit/versions/3.7.0/PAPER/GSit-3.7.0.jar',
            'sha512':'5a3941efcf28204c6f60fb90482ddfddfb14aa4d08dff03d5ba53258ba417b78942002b7fbb578ee15c562fcd7442a197629ab004ed78d64a6c432e4ca49bfad',
            'sha256':'57e119ca79d60fae98bffbb84961a78d70c2403698c4ebe208cae09d69302cea','size':811_798,
        },
        'Vault':{
            'version':'2.20.3','versionId':'qZgRzoYs','projectId':'ayRaM8J7',
            'filename':'VaultUnlocked-2.20.3.jar',
            'url':'https://cdn.modrinth.com/data/ayRaM8J7/versions/qZgRzoYs/VaultUnlocked-2.20.3.jar',
            'sha512':'0eedea1591459e7e327315b43afa834a173c8c2ae31b3b235586d31963df29a4a6c0ef4b8f3fdc746e15afd47ee50c1ff93584543b5c2ef7c2a80135dba1136a',
            'sha256':'fbc6651aca11e13376c115df66eeffa79592e79517be398299aa6beb49c22695','size':134_932,
        },
    }
    for plugin_name,metadata in expected.items():
        baseline=baselines[plugin_name]
        candidate=candidates[plugin_name]
        assert candidate['version']==metadata['version']
        if plugin_name == 'GSit':
            assert candidate['source']=='https://hangar.papermc.io/Gecolay/GSit/versions/3.7.0'
            assert 'versionId' not in candidate and 'projectId' not in candidate
            assert '143d38509f49c40b214887ad3c682c8db7fdd81c610787d0186d65f51256f4d2' in candidate['replaceSha256']
        elif plugin_name == 'LuckPerms':
            assert candidate['source']=='https://metadata.luckperms.net/data/all'
            assert 'versionId' not in candidate and 'projectId' not in candidate
        else:
            assert candidate['versionId']==metadata['versionId']
            assert candidate['projectId']==metadata['projectId']
        assert candidate['filename']==metadata['filename']
        assert candidate['url']==metadata['url']
        assert candidate['sha512']==metadata['sha512']
        assert candidate['sha256']==metadata['sha256']
        assert candidate['size']==metadata['size']
        assert baseline['sha256'] in candidate['replaceSha256']
        assert '26.3' in candidate['gameVersions']
        assert candidate['compatibilityStatus']=='requires-runtime-validation'
        assert candidate['nativeVerified'] is lock['nativeVerified']

        staged=ROOT/'build/minecraft-26.3/server-plugins'/candidate['filename']
        assert staged.is_file(), f'missing staged 26.3 candidate for {plugin_name}'
        assert staged.stat().st_size==candidate['size']
        assert hashlib.sha512(staged.read_bytes()).hexdigest()==candidate['sha512']
        assert hashlib.sha256(staged.read_bytes()).hexdigest()==candidate['sha256']
        assert load().identity(staged)==plugin_name

    for plugin_name,version_id,project_id,filename,digest in [
        ('PlaceholderAPI','pIvQcXW8','lKEzGugV','PlaceholderAPI-2.12.3.jar','fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437'),
        ('EntityClearer','G2mhZT01','SjDWdFjp','EntityClearer.jar','1918f1f611c04734b48435133cf74f806ab4154c5e6a1f556f2a45616573d330'),
    ]:
        baseline=baselines[plugin_name]
        candidate=baseline['candidate']
        assert candidate['versionId']==version_id
        assert candidate['projectId']==project_id
        assert candidate['runtimeFilename']==filename
        assert candidate['sha256']==digest==baseline['sha256']
        assert '26.3' in candidate['gameVersions']
        assert candidate['compatibilityStatus']=='requires-runtime-validation'
        assert candidate['nativeVerified'] is lock['nativeVerified']

    for plugin_name,filename,url,sha256,sha512,size,game_versions in [
        ('FarmControl','FarmControl-1.3.0.jar',
         'https://github.com/froobynooby/FarmControl/releases/download/v1.3.0/FarmControl-1.3.0.jar',
         '0cc753647b4f6bf55061aea05cdcb4dfbce6c922f912c817553dbfe14e088e85',
         '14d2c07d409e99c2068be9a20aecfb8ec00c98489e46b741c0dc103e52b6cdab1a9d3c955df91931962cf4e4768415e4f90aad1b3f3c4d794b9bb0240419c629',
         266_131,{'1.16','1.16.1','1.16.2','1.16.3','1.16.4','1.16.5','1.17','1.17.1','1.18','1.18.1','1.18.2','1.19','1.19.1','1.19.2','1.19.3','1.19.4','1.20','1.20.1','1.20.2','1.20.3','1.20.4','1.20.5','1.20.6','1.21'}),
        ('SeeMore','SeeMore-1.0.2.jar',
         'https://cdn.modrinth.com/data/IEt1Yy3F/versions/QXCh3qCi/SeeMore-1.0.2.jar',
         '8d06d342489947a296f07fbd012ea0e9842242f5af8d534f2dc91b9d2f0721e6',
         '5b6ad1edf059b8f78bb75b0cb7a47df39c05a1443d35e3b8a1f71ed78c4ccbc75d0422001442e6fc3bfdb9df26545600825af22bb00671135d5d206c20fc016b',
         107_284,{'1.19','1.19.1','1.19.2','1.19.3','1.19.4','1.20','1.20.1','1.20.2','1.20.3','1.20.4','1.20.5','1.20.6','1.21','1.21.1','1.21.2','1.21.3','1.21.4','1.21.5','1.21.6','1.21.7','1.21.8'}),
    ]:
        candidate=baselines[plugin_name]['candidate']
        assert candidate['filename']==filename
        assert candidate['url']==url
        assert candidate['sha256']==sha256==baselines[plugin_name]['sha256']
        assert candidate['sha512']==sha512
        assert candidate['size']==size
        assert set(candidate['gameVersions'])==game_versions
        assert '26.3' not in candidate['gameVersions']
        assert candidate['compatibilityStatus']=='requires-runtime-validation'
        assert candidate['nativeVerified'] is lock['nativeVerified']
        assert candidate['runtimeEligible'] is False


def write_bootstrap_fixture(root):
    content=b'pinned Paper 26.3 fixture'
    paper_sha=hashlib.sha256(content).hexdigest()
    filename='paper-26.3-161.jar'
    paper={
        'build':161,
        'channel':'BETA',
        'url':f'https://fill-data.papermc.io/v1/objects/{paper_sha}/{filename}',
        'size':len(content),
        'sha256':paper_sha,
    }
    profile=root/'tools/minecraft-26.3/profile.lock.json'
    profile.parent.mkdir(parents=True,exist_ok=True)
    profile.write_text(json.dumps({'minecraftVersion':'26.3','paper':paper}),encoding='utf-8')
    candidate=root/'build/minecraft-26.3/server'/filename
    candidate.parent.mkdir(parents=True,exist_ok=True)
    candidate.write_bytes(content)
    return content,filename


def invoke_runtime_prepare(mod,root):
    try:
        return mod.main(root=root,argv=['--prepare-runtime']),None
    except (SystemExit,ValueError) as error:
        return None,error


def test_prepare_runtime_stages_locked_paper_loopback_config_and_unaccepted_eula(tmp_path):
    mod=load();content,filename=write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'--prepare-runtime should complete successfully, got {error}'

    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    properties=(runtime/'server.properties').read_text(encoding='utf-8')
    assert (runtime/filename).read_bytes()==content
    assert 'server-ip=127.0.0.1' in properties
    assert 'server-port=25566' in properties
    assert 'online-mode=false' in properties
    assert 'enable-rcon=false' in properties
    assert 'max-players=20' in properties
    assert (runtime/'eula.txt').read_text(encoding='utf-8').strip()=='eula=false'
    assert (runtime/'plugins').is_dir()


def test_unauthenticated_local_test_mode_omits_authme_and_dependent_auth_effects():
    mod=load()
    records=[
        {'pluginName':'AuthMe','filename':'AuthMe-6.0.1-Paper.jar'},
        {'pluginName':'AuthEffects','filename':'AuthEffects.jar','depends':['AuthMe']},
        {'pluginName':'CoreProtect','filename':'CoreProtect-26.3.jar'},
    ]

    selected=mod.select_runtime_records(records,unauthenticated_test_mode=True)

    assert [record['pluginName'] for record in selected]==['CoreProtect']
    assert mod.select_runtime_records(records,unauthenticated_test_mode=False)==records


def test_unauthenticated_local_test_mode_moves_pinned_auth_plugins_to_backup(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    authme=jar('AuthMe','6.0.1')
    authme_path=server/'plugins/AuthMe-6.0.1-Paper.jar'
    authme_path.write_bytes(authme)
    auth_effects=jar('AuthEffects','1.0.0')
    auth_effects_path=server/'plugins/AuthEffects.jar'
    auth_effects_path.write_bytes(auth_effects)
    authme_lock=tmp_path/'tools/minecraft-26.3/server-plugins.lock.json'
    authme_lock.parent.mkdir(parents=True,exist_ok=True)
    authme_lock.write_text(json.dumps({'modules':[{
        'pluginName':'AuthMe','sha256':hashlib.sha256(authme).hexdigest(),'replaceSha256':[],
    }],'unchangedBaseline':[{
        'pluginName':'AuthEffects','sha256':'0'*64,
        'candidate':{'sha256':hashlib.sha256(auth_effects).hexdigest()},
    }]}),encoding='utf-8')

    receipt=mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,
                                unauthenticated_test_mode=True)

    assert not authme_path.exists()
    assert not auth_effects_path.exists()
    backup_files=list((server/'migration-backups/plugins').glob('*/AuthMe-6.0.1-Paper.jar'))
    assert len(backup_files)==1 and backup_files[0].read_bytes()==authme
    auth_effects_backups=list((server/'migration-backups/plugins').glob('*/AuthEffects.jar'))
    assert len(auth_effects_backups)==1 and auth_effects_backups[0].read_bytes()==auth_effects
    assert (server/'plugins/new.jar').read_bytes()==new
    assert receipt['authenticationMode']=='disabled-for-local-testing'

def test_unauthenticated_local_test_mode_preserves_locked_authme_candidate_when_not_installed(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    authme=jar('AuthMe','6.0.1')
    staged_authme=stage/'AuthMe-6.0.1-Paper.jar'
    staged_authme.write_bytes(authme)
    auth_effects=jar('AuthEffects','26.3')
    staged_auth_effects=stage/'AuthEffects-26.3.jar'
    staged_auth_effects.write_bytes(auth_effects)
    authme_lock=tmp_path/'tools/minecraft-26.3/server-plugins.lock.json'
    authme_lock.parent.mkdir(parents=True,exist_ok=True)
    authme_lock.write_text(json.dumps({'modules':[{
        'pluginName':'AuthMe','filename':staged_authme.name,
        'sha256':hashlib.sha256(authme).hexdigest(),'replaceSha256':[],
    }],'unchangedBaseline':[{
        'pluginName':'AuthEffects','filename':'AuthEffects.jar','sha256':'1'*64,
        'candidate':{'filename':staged_auth_effects.name,'runtimeFilename':'AuthEffects.jar',
                     'sha256':hashlib.sha256(auth_effects).hexdigest()},
    }]}),encoding='utf-8')

    receipt=mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,
                                unauthenticated_test_mode=True)

    assert not (server/'plugins'/staged_authme.name).exists()
    backups=list((server/'migration-backups/plugins').glob('*/AuthMe-6.0.1-Paper.jar'))
    assert len(backups)==1 and backups[0].read_bytes()==authme
    assert hashlib.sha256(backups[0].read_bytes()).hexdigest()==hashlib.sha256(authme).hexdigest()
    auth_effects_backups=list((server/'migration-backups/plugins').glob('*/AuthEffects-26.3.jar'))
    assert len(auth_effects_backups)==1 and auth_effects_backups[0].read_bytes()==auth_effects
    assert (server/'plugins/new.jar').read_bytes()==new
    assert receipt['authenticationMode']=='disabled-for-local-testing'

    second_receipt=mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,
                                       unauthenticated_test_mode=True)
    repeated_backups=list((server/'migration-backups/plugins').glob('*/AuthMe-6.0.1-Paper.jar'))
    assert len(repeated_backups)==1
    repeated_effects_backups=list((server/'migration-backups/plugins').glob('*/AuthEffects-26.3.jar'))
    assert len(repeated_effects_backups)==1
    assert second_receipt['authenticationMode']=='disabled-for-local-testing'


def test_unauthenticated_local_test_mode_refuses_unrecognized_auth_effects(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    auth_effects=jar('AuthEffects','1.0.0')
    auth_effects_path=server/'plugins/AuthEffects.jar'
    auth_effects_path.write_bytes(auth_effects)
    authme=jar('AuthMe','6.0.1')
    staged_authme=stage/'AuthMe-6.0.1-Paper.jar'
    staged_authme.write_bytes(authme)
    authme_lock=tmp_path/'tools/minecraft-26.3/server-plugins.lock.json'
    authme_lock.parent.mkdir(parents=True,exist_ok=True)
    authme_lock.write_text(json.dumps({'modules':[{
        'pluginName':'AuthMe','filename':staged_authme.name,
        'sha256':hashlib.sha256(authme).hexdigest(),'replaceSha256':[],
    }],'unchangedBaseline':[{'pluginName':'AuthEffects','sha256':'0'*64,
                              'candidate':{'sha256':'1'*64}}]}),encoding='utf-8')

    with pytest.raises(ValueError,match='unrecognized AuthEffects'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,
                            unauthenticated_test_mode=True)

    assert auth_effects_path.read_bytes()==auth_effects
    assert not (server/'plugins/new.jar').exists()
    assert not (server/'migration-backups/plugins').exists()


def test_prepare_runtime_never_replaces_unowned_directory_or_accepts_eula(tmp_path):
    mod=load();write_bootstrap_fixture(tmp_path)
    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    runtime.mkdir(parents=True)
    sentinel=runtime/'keep.txt';sentinel.write_text('existing user data',encoding='utf-8')

    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert isinstance(error,ValueError) and 'unowned' in str(error).lower(), error

    assert sentinel.read_text(encoding='utf-8')=='existing user data'
    assert not (runtime/'eula.txt').exists()


def test_prepare_runtime_is_idempotent_and_preserves_operator_eula_choice(tmp_path):
    mod=load();content,filename=write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'
    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    properties=runtime/'server.properties'
    properties.write_text(properties.read_text(encoding='utf-8')+'motd=operator note\n',encoding='utf-8')
    eula=runtime/'eula.txt';eula.write_text('eula=true\n',encoding='utf-8')

    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'idempotent --prepare-runtime should complete successfully, got {error}'

    assert (runtime/filename).read_bytes()==content
    assert 'motd=operator note' in properties.read_text(encoding='utf-8')
    assert eula.read_text(encoding='utf-8')=='eula=true\n'


def test_paper_upgrade_refuses_to_copy_a_live_migration_database(tmp_path,monkeypatch):
    mod=load();_,old_filename=write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'

    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    database_file=runtime/'postgres-data/base/1/1'
    database_file.parent.mkdir(parents=True)
    database_file.write_bytes(b'live PostgreSQL data must not be copied during a Paper upgrade')
    new_content=b'pinned Paper 26.3 build 169 fixture'
    new_filename='paper-26.3-169.jar'
    new_sha=hashlib.sha256(new_content).hexdigest()
    profile=tmp_path/'tools/minecraft-26.3/profile.lock.json'
    profile.write_text(json.dumps({'minecraftVersion':'26.3','paper':{
        'build':169,'channel':'BETA','url':f'https://fill-data.papermc.io/v1/objects/{new_sha}/{new_filename}',
        'size':len(new_content),'sha256':new_sha,
    }}),encoding='utf-8')
    candidate=tmp_path/'build/minecraft-26.3/server'/new_filename
    candidate.write_bytes(new_content)
    paper=mod._locked_paper(tmp_path)
    observed_ports=[]
    def database_is_listening(port,host):
        observed_ports.append((port,host))
        return port==55434 and host=='127.0.0.1'
    monkeypatch.setattr(mod,'port_open',database_is_listening)

    with pytest.raises(ValueError,match='PostgreSQL.*running'):
        mod._upgrade_owned_runtime(runtime,runtime.parent,paper,candidate)

    assert (55434,'127.0.0.1') in observed_ports
    assert (runtime/old_filename).is_file()
    assert database_file.read_bytes()==b'live PostgreSQL data must not be copied during a Paper upgrade'
    assert not (runtime.parent/'migration-backups').exists()


def test_paper_upgrade_refuses_to_copy_a_live_migration_database_on_its_configured_port(tmp_path,monkeypatch):
    mod=load();_,old_filename=write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'

    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    database_file=runtime/'postgres-data/base/1/1'
    database_file.parent.mkdir(parents=True)
    database_file.write_bytes(b'live PostgreSQL data must not be copied during a Paper upgrade')
    (runtime/'migration-isolated-postgres.env').write_text(
        'POSTGRES_HOST=127.0.0.1\nPOSTGRES_PORT=55435\n',encoding='utf-8')
    new_content=b'pinned Paper 26.3 build 169 fixture'
    new_filename='paper-26.3-169.jar'
    new_sha=hashlib.sha256(new_content).hexdigest()
    profile=tmp_path/'tools/minecraft-26.3/profile.lock.json'
    profile.write_text(json.dumps({'minecraftVersion':'26.3','paper':{
        'build':169,'channel':'BETA','url':f'https://fill-data.papermc.io/v1/objects/{new_sha}/{new_filename}',
        'size':len(new_content),'sha256':new_sha,
    }}),encoding='utf-8')
    candidate=tmp_path/'build/minecraft-26.3/server'/new_filename
    candidate.write_bytes(new_content)
    paper=mod._locked_paper(tmp_path)
    observed_ports=[]
    def database_is_listening(port,host):
        observed_ports.append((port,host))
        return port==55435 and host=='::1'
    monkeypatch.setattr(mod,'port_open',database_is_listening)

    with pytest.raises(ValueError,match='PostgreSQL.*running'):
        mod._upgrade_owned_runtime(runtime,runtime.parent,paper,candidate)

    assert (55435,'::1') in observed_ports
    assert (runtime/old_filename).is_file()
    assert database_file.read_bytes()==b'live PostgreSQL data must not be copied during a Paper upgrade'
    assert not (runtime.parent/'migration-backups').exists()


def test_paper_upgrade_refuses_to_copy_when_live_postgres_has_no_ipv4_listener(tmp_path,monkeypatch):
    mod=load();_,old_filename=write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'

    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    data=runtime/'postgres-data'
    database_file=data/'base/1/1'
    database_file.parent.mkdir(parents=True)
    database_file.write_bytes(b'live PostgreSQL data must not be copied during a Paper upgrade')
    pid=42424
    (data/'postmaster.pid').write_text(f'{pid}\n{data}\n1\n55436\n',encoding='utf-8')
    new_content=b'pinned Paper 26.3 build 169 fixture'
    new_filename='paper-26.3-169.jar'
    new_sha=hashlib.sha256(new_content).hexdigest()
    profile=tmp_path/'tools/minecraft-26.3/profile.lock.json'
    profile.write_text(json.dumps({'minecraftVersion':'26.3','paper':{
        'build':169,'channel':'BETA','url':f'https://fill-data.papermc.io/v1/objects/{new_sha}/{new_filename}',
        'size':len(new_content),'sha256':new_sha,
    }}),encoding='utf-8')
    candidate=tmp_path/'build/minecraft-26.3/server'/new_filename
    candidate.write_bytes(new_content)
    paper=mod._locked_paper(tmp_path)
    observed_pids=[]
    def postgres_process_is_running(observed_pid):
        observed_pids.append(observed_pid)
        return observed_pid==pid
    monkeypatch.setattr(mod,'_process_id_is_running',postgres_process_is_running,raising=False)
    monkeypatch.setattr(mod,'port_open',lambda _port,_host: False)

    with pytest.raises(ValueError,match='PostgreSQL.*running') as error:
        mod._upgrade_owned_runtime(runtime,runtime.parent,paper,candidate)

    assert observed_pids==[pid]
    assert 'postmaster.pid' in str(error.value)
    assert 'only then move the stale postmaster.pid aside' in str(error.value)
    assert (runtime/old_filename).is_file()
    assert database_file.read_bytes()==b'live PostgreSQL data must not be copied during a Paper upgrade'
    assert not (runtime.parent/'migration-backups').exists()


def test_process_id_liveness_probe_tracks_a_real_process(tmp_path):
    mod=load()
    process=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'])
    try:
        assert mod._process_id_is_running(process.pid)
    finally:
        process.terminate()
        process.wait(timeout=5)

    assert not mod._process_id_is_running(process.pid)


def test_runtime_upgrade_preserves_private_migration_credential_acl(tmp_path):
    if os.name!='nt':pytest.skip('Windows file ACL behavior is required for this regression.')
    windows_root=Path(os.environ.get('SystemRoot',r'C:\Windows'))
    powershell=windows_root/'System32/WindowsPowerShell/v1.0/powershell.exe'
    if not powershell.is_file():pytest.skip('Windows PowerShell is required to exercise migration credential ACLs.')
    mod=load()
    source=tmp_path/'runtime';source.mkdir()
    destination=tmp_path/'upgrade';destination.mkdir()
    credential=source/'migration-isolated-postgres.env'
    environment_module=ROOT/'scripts/minecraft/MigrationDatabaseEnvironment.psm1'
    environment=os.environ.copy()
    environment.update({
        'COPIMINE_MIGRATION_ENV_MODULE':str(environment_module),
        'COPIMINE_TEST_MIGRATION_ENV_PATH':str(credential),
        'COPIMINE_TEST_MIGRATION_ENV_CONTENTS':'POSTGRES_PASSWORD='+'a'*64+'\n',
        'PSModulePath':str(windows_root/'System32/WindowsPowerShell/v1.0/Modules'),
    })
    create_private_credential=(
        "$ErrorActionPreference='Stop'; "
        "Import-Module -Name $env:COPIMINE_MIGRATION_ENV_MODULE -Force; "
        "Write-MigrationPrivateEnvFile -Path $env:COPIMINE_TEST_MIGRATION_ENV_PATH "
        "-Contents $env:COPIMINE_TEST_MIGRATION_ENV_CONTENTS"
    )
    created=subprocess.run(
        [str(powershell),'-NoProfile','-ExecutionPolicy','Bypass','-Command',create_private_credential],
        check=False,capture_output=True,text=True,timeout=30,env=environment)
    assert created.returncode==0,created.stderr

    mod._copy_runtime_state(source,destination)

    compare_acl=(
        "$ErrorActionPreference='Stop'; "
        "$source=Get-Acl -LiteralPath $env:COPIMINE_TEST_MIGRATION_SOURCE; "
        "$copy=Get-Acl -LiteralPath $env:COPIMINE_TEST_MIGRATION_COPY; "
        "$sourceRules=@($source.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | "
        "ForEach-Object { '{0}|{1}|{2}|{3}|{4}|{5}' -f $_.IdentityReference.Value,[int]$_.FileSystemRights,"
        "$_.AccessControlType,$_.IsInherited,$_.InheritanceFlags,$_.PropagationFlags } | Sort-Object); "
        "$copyRules=@($copy.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | "
        "ForEach-Object { '{0}|{1}|{2}|{3}|{4}|{5}' -f $_.IdentityReference.Value,[int]$_.FileSystemRights,"
        "$_.AccessControlType,$_.IsInherited,$_.InheritanceFlags,$_.PropagationFlags } | Sort-Object); "
        "$rulesMatch=@(Compare-Object -ReferenceObject $sourceRules -DifferenceObject $copyRules).Count -eq 0; "
        "$same=$source.Owner -ceq $copy.Owner -and $source.Group -ceq $copy.Group -and "
        "$source.AreAccessRulesProtected -eq $copy.AreAccessRulesProtected -and $rulesMatch; "
        "[PSCustomObject]@{same=$same} | ConvertTo-Json -Compress"
    )
    environment['COPIMINE_TEST_MIGRATION_SOURCE']=str(credential)
    environment['COPIMINE_TEST_MIGRATION_COPY']=str(destination/credential.name)
    compared=subprocess.run(
        [str(powershell),'-NoProfile','-ExecutionPolicy','Bypass','-Command',compare_acl],
        check=False,capture_output=True,text=True,timeout=30,env=environment)
    assert compared.returncode==0,compared.stderr
    assert json.loads(compared.stdout)=={'same':True},'the copied migration credential must retain its protected source ACL'


def test_prepare_runtime_migrates_owned_older_paper_and_preserves_data_and_configs(tmp_path,capsys,monkeypatch):
    mod=load();old_content,old_filename=write_bootstrap_fixture(tmp_path)
    monkeypatch.setattr(mod,'port_open',lambda port,host:False)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'

    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    world_file=runtime/'world/region/r.0.0.mca'
    world_file.parent.mkdir(parents=True)
    world_file.write_bytes(b'world data must survive the Paper upgrade')
    plugin_config=runtime/'plugins/Example/config.yml'
    plugin_config.parent.mkdir(parents=True)
    plugin_config.write_text('existing: settings\n',encoding='utf-8')
    old_plugin=runtime/'plugins/Example.jar'
    old_plugin_bytes=jar('Example','1.0')
    old_plugin.write_bytes(old_plugin_bytes)
    receipt=runtime/'migration-plugin-receipt.json'
    receipt.write_text('{"old":true}\n',encoding='utf-8')
    properties=runtime/'server.properties'
    properties.write_text(properties.read_text(encoding='utf-8')+'motd=operator note\n',encoding='utf-8')
    eula=runtime/'eula.txt';eula.write_text('eula=true\n',encoding='ascii')

    new_content=b'pinned Paper 26.3 build 169 fixture'
    new_filename='paper-26.3-169.jar'
    new_sha=hashlib.sha256(new_content).hexdigest()
    profile=tmp_path/'tools/minecraft-26.3/profile.lock.json'
    profile.write_text(json.dumps({'minecraftVersion':'26.3','paper':{
        'build':169,'channel':'BETA','url':f'https://fill-data.papermc.io/v1/objects/{new_sha}/{new_filename}',
        'size':len(new_content),'sha256':new_sha,
    }}),encoding='utf-8')
    candidate=tmp_path/'build/minecraft-26.3/server'/new_filename
    candidate.write_bytes(new_content)

    _,error=invoke_runtime_prepare(mod,tmp_path)

    assert error is None, f'--prepare-runtime should migrate an owned older Paper runtime, got {error}'
    metadata=json.loads((runtime/'copimine-migration-runtime.json').read_text(encoding='utf-8'))
    assert metadata['paperFilename']==new_filename
    assert metadata['paperSha256']==new_sha
    assert (runtime/new_filename).read_bytes()==new_content
    assert (runtime/'server.properties').read_text(encoding='utf-8').endswith('motd=operator note\n')
    assert eula.read_text(encoding='ascii')=='eula=true\n'
    assert world_file.read_bytes()==b'world data must survive the Paper upgrade'
    assert plugin_config.read_text(encoding='utf-8')=='existing: settings\n'
    assert not old_plugin.exists(), 'older plugin JAR must not remain active during the Paper upgrade'
    assert not receipt.exists(), 'old plugin receipt must not describe the new runtime inventory'

    snapshots=list((tmp_path/'local-runtime/migration-backups').glob('end-rift-server-26.3-build161-*'))
    assert len(snapshots)==1, 'the complete prior runtime must be retained as a rollback snapshot'
    snapshot=snapshots[0]/'runtime'
    assert (snapshot/old_filename).read_bytes()==old_content
    assert (snapshot/'plugins/Example.jar').read_bytes()==old_plugin_bytes
    assert (snapshot/'world/region/r.0.0.mca').read_bytes()==b'world data must survive the Paper upgrade'
    assert (snapshot/'migration-plugin-receipt.json').read_text(encoding='utf-8')=='{"old":true}\n'
    output=capsys.readouterr().out
    assert 'Archived plugin JARs were not carried into the active runtime' in output
    assert str(snapshot/'plugins') in output
    assert 'Install and verify the locked plugin candidates before starting Paper.' in output


def test_prepare_runtime_recovers_interrupted_paper_swap_without_losing_world_data(tmp_path,monkeypatch):
    mod=load();write_bootstrap_fixture(tmp_path)
    monkeypatch.setattr(mod,'port_open',lambda port,host:False)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'

    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    world_file=runtime/'world/region/r.0.0.mca'
    world_file.parent.mkdir(parents=True)
    world_file.write_bytes(b'world data must survive an interrupted Paper swap')
    new_content=b'pinned Paper 26.3 build 169 fixture'
    new_filename='paper-26.3-169.jar'
    new_sha=hashlib.sha256(new_content).hexdigest()
    (tmp_path/'tools/minecraft-26.3/profile.lock.json').write_text(json.dumps({
        'minecraftVersion':'26.3','paper':{
            'build':169,'channel':'BETA','url':f'https://fill-data.papermc.io/v1/objects/{new_sha}/{new_filename}',
            'size':len(new_content),'sha256':new_sha,
        },
    }),encoding='utf-8')
    (tmp_path/'build/minecraft-26.3/server'/new_filename).write_bytes(new_content)

    real_rename=mod.os.rename
    moved_old_runtime=False
    def interrupt_between_directory_renames(source,destination):
        nonlocal moved_old_runtime
        source=Path(source);destination=Path(destination)
        if source==runtime and destination.parent.parent.name=='migration-backups':
            result=real_rename(source,destination)
            moved_old_runtime=True
            return result
        if moved_old_runtime and destination==runtime and source.name.startswith('.end-rift-server-26.3-upgrade-'):
            raise KeyboardInterrupt('simulated process interruption during Paper runtime swap')
        return real_rename(source,destination)
    monkeypatch.setattr(mod.os,'rename',interrupt_between_directory_renames)

    with pytest.raises(KeyboardInterrupt,match='simulated process interruption'):
        invoke_runtime_prepare(mod,tmp_path)

    assert not runtime.exists(), 'the simulated interruption should occur after the old runtime is archived'
    snapshots=list((tmp_path/'local-runtime/migration-backups').glob('end-rift-server-26.3-build161-*/runtime'))
    assert len(snapshots)==1
    assert (snapshots[0]/'world/region/r.0.0.mca').read_bytes()==b'world data must survive an interrupted Paper swap'
    assert (snapshots[0].parent/'upgrade-in-progress.json').is_file(), 'the swap must leave a recovery record before moving the runtime'
    orphaned_scratch=list((tmp_path/'local-runtime').glob('.end-rift-server-26.3-upgrade-*'))
    assert len(orphaned_scratch)==1 and orphaned_scratch[0].is_dir()

    monkeypatch.setattr(mod.os,'rename',real_rename)
    _,error=invoke_runtime_prepare(mod,tmp_path)

    assert error is None, f'prepare should recover the archived runtime and resume the upgrade, got {error}'
    assert (runtime/'world/region/r.0.0.mca').read_bytes()==b'world data must survive an interrupted Paper swap'
    metadata=json.loads((runtime/'copimine-migration-runtime.json').read_text(encoding='utf-8'))
    assert metadata['paperFilename']==new_filename
    assert not list((tmp_path/'local-runtime').glob('.end-rift-server-26.3-upgrade-*'))


def test_prepare_runtime_refuses_blank_recreation_when_only_an_archived_world_remains(tmp_path):
    mod=load();write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'
    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    world_file=runtime/'world/level.dat'
    world_file.parent.mkdir(parents=True)
    world_file.write_bytes(b'preserve the only remaining world copy')
    snapshot=tmp_path/'local-runtime/migration-backups/end-rift-server-26.3-build160-recovery/runtime'
    snapshot.parent.mkdir(parents=True)
    mod.os.rename(runtime,snapshot)

    _,error=invoke_runtime_prepare(mod,tmp_path)

    assert isinstance(error,ValueError) and 'refusing to create a blank world' in str(error).lower(), error
    assert not runtime.exists()
    assert (snapshot/'world/level.dat').read_bytes()==b'preserve the only remaining world copy'


def _write_startup_validation_fixture(root,*,full_plugin_set=False):
    mod=load()
    runtime=root/'local-runtime/end-rift-server-26.3'
    plugins=runtime/'plugins'
    plugins.mkdir(parents=True)
    voicechat_config=plugins/'voicechat/voicechat-server.properties'
    voicechat_config.parent.mkdir(parents=True)
    voicechat_config.write_text('bind_address=127.0.0.1\n',encoding='utf-8')
    (runtime/'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nenable-rcon=false\n',encoding='utf-8')
    plugin_rows=[]
    records=[]
    for name,version,filename in (
        ('CoreProtect','26.3','CoreProtect-26.3.jar'),
        ('CopiMineEndEvent','0.1.0','CopiMineEndEvent.jar'),
        ('AuthMe','6.0.1','AuthMe-6.0.1.jar'),
        ('voicechat','2.6.24','voicechat-bukkit-2.6.24.jar'),
    ):
        content=jar(name,version)
        records.append({
            'pluginName':name,'version':version,'filename':filename,'runtimeFilename':filename,
            'url':f'https://cdn.modrinth.com/data/test/versions/test/{filename}',
            'sha256':hashlib.sha256(content).hexdigest(),'sha512':hashlib.sha512(content).hexdigest(),
            'size':len(content),'replaceSha256':[],
        })
        if name!='AuthMe' or full_plugin_set:(plugins/filename).write_bytes(content)
        if name!='AuthMe' or full_plugin_set:
            plugin_rows.append({
                'pluginName':name,'filename':filename,'sha256':hashlib.sha256(content).hexdigest(),
                'sha512':hashlib.sha512(content).hexdigest(),
            })
    lock=root/'tools/minecraft-26.3/server-plugins.lock.json'
    lock.parent.mkdir(parents=True)
    lock.write_text(json.dumps({'modules':records,'unchangedBaseline':[]}),encoding='utf-8')
    receipt={
        'schemaVersion':1,'minecraftVersion':'26.3','nativeVerified':False,
        'authenticationMode':'AuthMe-6.0.1' if full_plugin_set else 'disabled-for-local-testing',
        'installed':[
            {**row,'version':next(record['version'] for record in records if record['pluginName']==row['pluginName'])}
            for row in plugin_rows
        ],
        'pluginInventory':plugin_rows,
    }
    (runtime/'migration-plugin-receipt.json').write_text(json.dumps(receipt),encoding='utf-8')
    return mod,runtime,plugins


def test_startup_settings_require_complete_locked_plugin_inventory_and_local_test_receipt(tmp_path,capsys):
    mod,_,_= _write_startup_validation_fixture(tmp_path)

    mod.main(root=tmp_path,argv=['--startup-settings-json','--unauthenticated-test-runtime'])

    assert json.loads(capsys.readouterr().out)=={
        'serverIp':'127.0.0.1','serverPort':25566,'onlineMode':'false','enableRcon':'false',
        'voiceChatBindAddress':'127.0.0.1'}
    assert (tmp_path/'local-runtime/end-rift-server-26.3/plugins/voicechat/voicechat-server.properties').read_text(
        encoding='utf-8')=='bind_address=127.0.0.1\n'


def test_startup_settings_refuse_voice_chat_drift_without_rewriting_runtime_files(tmp_path):
    mod,runtime,_=_write_startup_validation_fixture(tmp_path)
    config=runtime/'plugins/voicechat/voicechat-server.properties'
    original=b'# user setting\nbind_address=*\nport=24454\n'
    config.write_bytes(original)

    with pytest.raises(ValueError,match='voice chat.*loopback|bind_address.*127.0.0.1'):
        mod.main(root=tmp_path,argv=['--startup-settings-json','--unauthenticated-test-runtime'])

    assert config.read_bytes()==original


def test_startup_settings_do_not_create_missing_voice_chat_configuration(tmp_path):
    mod,runtime,_=_write_startup_validation_fixture(tmp_path)
    config=runtime/'plugins/voicechat/voicechat-server.properties'
    config.unlink()

    with pytest.raises(ValueError,match='Voice Chat.*missing|voice chat.*missing'):
        mod.main(root=tmp_path,argv=['--startup-settings-json','--unauthenticated-test-runtime'])

    assert not config.exists()


def _install_local_end_event_override_fixture(runtime,plugins):
    target=plugins/'CopiMineEndEvent.jar'
    previous=target.read_bytes()
    local_build=jar('CopiMineEndEvent','0.1.0-dev')
    target.write_bytes(local_build)
    backup=runtime/'migration-backups/plugins/test-session/CopiMineEndEvent.jar'
    backup.parent.mkdir(parents=True)
    backup.write_bytes(previous)
    manifest={
        'schemaVersion':1,
        'scope':'local-development-override',
        'plugin':'CopiMineEndEvent',
        'minecraftVersion':'26.3',
        'runtimePath':str(target.resolve()),
        'builtJarSize':len(local_build),
        'builtJarSha256':hashlib.sha256(local_build).hexdigest(),
        'builtJarSha512':hashlib.sha512(local_build).hexdigest(),
        'replacedJarSha256':hashlib.sha256(previous).hexdigest(),
        'replacementBackup':str(backup.resolve()),
        'sourceBranch':'codex/local-test',
        'sourceHead':'a'*40,
        'sourceTreeSha256':'b'*64,
        'sourceJavaFileCount':1,
        'sourceWorkingTreeDirty':True,
        'javaVersion':'Temurin 25.0.4.1',
    }
    (runtime/'migration-local-end-event-build.json').write_text(json.dumps(manifest),encoding='utf-8')
    return target,backup,manifest


def test_startup_settings_accept_only_receipted_local_end_event_override(tmp_path,capsys):
    mod,runtime,plugins=_write_startup_validation_fixture(tmp_path,full_plugin_set=True)
    _,_,manifest=_install_local_end_event_override_fixture(runtime,plugins)

    mod.main(root=tmp_path,argv=['--startup-settings-json','--local-end-event-build'])

    assert json.loads(capsys.readouterr().out)=={
        'serverIp':'127.0.0.1','serverPort':25566,'onlineMode':'false','enableRcon':'false',
        'voiceChatBindAddress':'127.0.0.1'}
    receipt=json.loads((runtime/'migration-plugin-receipt.json').read_text(encoding='utf-8'))
    locked_row=next(row for row in receipt['pluginInventory'] if row['pluginName']=='CopiMineEndEvent')
    assert manifest['replacedJarSha256']==locked_row['sha256']


def test_startup_settings_do_not_auto_accept_local_end_event_override(tmp_path):
    mod,runtime,plugins=_write_startup_validation_fixture(tmp_path,full_plugin_set=True)
    _install_local_end_event_override_fixture(runtime,plugins)

    with pytest.raises(ValueError,match='Active plugin inventory differs from the locked 26.3 candidates'):
        mod.main(root=tmp_path,argv=['--startup-settings-json'])


def test_local_end_event_override_keeps_other_locked_plugin_hashes_enforced(tmp_path):
    mod,runtime,plugins=_write_startup_validation_fixture(tmp_path,full_plugin_set=True)
    _install_local_end_event_override_fixture(runtime,plugins)
    (plugins/'CoreProtect-26.3.jar').write_bytes(jar('CoreProtect','26.3')+b'unreceipted change')

    with pytest.raises(ValueError,match='Active plugin inventory differs from the locked 26.3 candidates'):
        mod.main(root=tmp_path,argv=['--startup-settings-json','--local-end-event-build'])


def test_local_end_event_override_requires_the_pinned_jar_backup(tmp_path):
    mod,runtime,plugins=_write_startup_validation_fixture(tmp_path,full_plugin_set=True)
    _,backup,_=_install_local_end_event_override_fixture(runtime,plugins)
    backup.unlink()

    with pytest.raises(ValueError,match='Local End Event build backup is missing'):
        mod.main(root=tmp_path,argv=['--startup-settings-json','--local-end-event-build'])


@pytest.mark.parametrize('damage',['missing_receipt','missing_plugin','partial_receipt'])
def test_startup_settings_refuse_missing_or_partial_locked_plugin_installation(tmp_path,damage):
    mod,runtime,plugins=_write_startup_validation_fixture(tmp_path)
    if damage=='missing_receipt':
        (runtime/'migration-plugin-receipt.json').unlink()
    elif damage=='missing_plugin':
        (plugins/'CoreProtect-26.3.jar').unlink()
    else:
        receipt_path=runtime/'migration-plugin-receipt.json'
        receipt=json.loads(receipt_path.read_text(encoding='utf-8'))
        receipt['pluginInventory']=[]
        receipt_path.write_text(json.dumps(receipt),encoding='utf-8')

    with pytest.raises((ValueError,FileNotFoundError)):
        mod.main(root=tmp_path,argv=['--startup-settings-json','--unauthenticated-test-runtime'])


@pytest.mark.parametrize(('key','value'),[
    ('server-ip','0.0.0.0'),
    ('online-mode','true'),
    ('enable-rcon','true'),
])
def test_prepare_runtime_rejects_reused_public_bind_or_enabled_rcon(tmp_path,key,value):
    mod=load();write_bootstrap_fixture(tmp_path)
    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert error is None, f'initial --prepare-runtime should complete successfully, got {error}'
    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    properties=runtime/'server.properties'
    contents=properties.read_text(encoding='utf-8')
    expected='127.0.0.1' if key=='server-ip' else 'false'
    properties.write_text(contents.replace(f'{key}={expected}',f'{key}={value}'),encoding='utf-8')

    _,error=invoke_runtime_prepare(mod,tmp_path)
    assert isinstance(error,ValueError), f'reused runtime with {key}={value} must be rejected'
    assert 'loopback' in str(error).lower() or 'rcon' in str(error).lower() or 'online-mode' in str(error).lower()


@pytest.mark.parametrize(('server_ip','rcon_enabled'),[
    ('0.0.0.0','false'),
    ('127.0.0.1','true'),
])
def test_plugin_install_rejects_public_bind_or_enabled_rcon_before_listener_checks(tmp_path,server_ip,rcon_enabled):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    (server/'server.properties').write_text(
        f'server-ip={server_ip}\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon={rcon_enabled}\n',
        encoding='utf-8')
    checked=[]

    with pytest.raises(ValueError,match='loopback|rcon'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:checked.append((port,host)) or False)

    assert checked==[]
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()


def test_plugin_install_refuses_while_server_start_holds_runtime_lifecycle_lock(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    ready=tmp_path/'server-start-lock-ready.txt'
    lock_name=mod.runtime_lifecycle_mutex_name(server)
    if os.name=='nt':
        child_code='\n'.join((
            'import ctypes, pathlib, sys, time',
            'from ctypes import wintypes',
            'kernel32=ctypes.WinDLL("kernel32",use_last_error=True)',
            'kernel32.CreateMutexW.argtypes=(wintypes.LPVOID,wintypes.BOOL,wintypes.LPCWSTR)',
            'kernel32.CreateMutexW.restype=wintypes.HANDLE',
            'handle=kernel32.CreateMutexW(None,False,sys.argv[2])',
            'if not handle: raise ctypes.WinError(ctypes.get_last_error())',
            'state=kernel32.WaitForSingleObject(handle,0)',
            'if state not in (0,0x80): raise RuntimeError("failed to acquire lifecycle mutex: %s"%state)',
            'pathlib.Path(sys.argv[1]).write_text("locked",encoding="ascii")',
            'time.sleep(30)',
        ))
        lock_path=None
    else:
        lock_path=server.parent/'.migration-lifecycle.lock'
        child_code='\n'.join((
            'import fcntl, os, pathlib, sys, time',
            'descriptor=os.open(sys.argv[2],os.O_CREAT|os.O_RDWR,0o600)',
            'fcntl.flock(descriptor,fcntl.LOCK_EX|fcntl.LOCK_NB)',
            'pathlib.Path(sys.argv[1]).write_text("locked",encoding="ascii")',
            'time.sleep(30)',
        ))
    args=[sys.executable,'-c',child_code,str(ready),lock_name if os.name=='nt' else str(lock_path)]
    process=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        for _ in range(200):
            if ready.exists():break
            if process.poll() is not None:
                pytest.fail('server-start lock holder exited early: '+process.stderr.read())
            time.sleep(0.025)
        assert ready.exists(), 'server-start process did not acquire its lifecycle lock'
        checked=[]

        with pytest.raises(ValueError,match='lifecycle lock'):
            mod.install_records(tmp_path,server,[record],stage,
                                lambda port,host:checked.append((port,host)) or False)

        assert checked==[]
        assert (server/'plugins/old.jar').read_bytes()==old
        assert not (server/'plugins/new.jar').exists()
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill();process.wait(timeout=5)


def test_runtime_lifecycle_mutex_name_matches_powershell_full_path_without_resolving_aliases(tmp_path,monkeypatch):
    mod=load()
    server=tmp_path/'runtime'/'..'/'runtime'
    canonical=os.path.normcase(os.path.abspath(os.fspath(server))).replace('/','\\')
    expected=mod.MIGRATION_RUNTIME_MUTEX_PREFIX+hashlib.sha256(canonical.encode('utf-8')).hexdigest()
    path_module=SimpleNamespace(
        abspath=os.path.abspath,
        normcase=os.path.normcase,
        realpath=lambda *_:pytest.fail('mutex key must not resolve junctions or aliases'),
    )
    monkeypatch.setattr(mod,'os',SimpleNamespace(fspath=os.fspath,path=path_module))

    assert mod.runtime_lifecycle_mutex_name(server)==expected


def test_plugin_install_allows_an_independent_runtime_lifecycle_lock(tmp_path):
    mod=load()
    candidate_root=tmp_path/'candidate'
    other_root=tmp_path/'existing-worktree'
    server,stage,record,old,new=fixture(candidate_root)
    other_server=other_root/'local-runtime/end-rift-server-26.3'
    ready=tmp_path/'other-runtime-lock-ready.txt'
    if os.name=='nt':
        other_lock_name=mod.runtime_lifecycle_mutex_name(other_server)
        child_code='\n'.join((
            'import ctypes, pathlib, sys, time',
            'from ctypes import wintypes',
            'kernel32=ctypes.WinDLL("kernel32",use_last_error=True)',
            'kernel32.CreateMutexW.argtypes=(wintypes.LPVOID,wintypes.BOOL,wintypes.LPCWSTR)',
            'kernel32.CreateMutexW.restype=wintypes.HANDLE',
            'handle=kernel32.CreateMutexW(None,False,sys.argv[2])',
            'if not handle: raise ctypes.WinError(ctypes.get_last_error())',
            'state=kernel32.WaitForSingleObject(handle,0)',
            'if state not in (0,0x80): raise RuntimeError("failed to acquire other runtime mutex: %s"%state)',
            'pathlib.Path(sys.argv[1]).write_text("locked",encoding="ascii")',
            'time.sleep(30)',
        ))
        lock_argument=other_lock_name
    else:
        other_lock_path=other_server.parent/'.migration-lifecycle.lock'
        child_code='\n'.join((
            'import fcntl, os, pathlib, sys, time',
            'descriptor=os.open(sys.argv[2],os.O_CREAT|os.O_RDWR,0o600)',
            'fcntl.flock(descriptor,fcntl.LOCK_EX|fcntl.LOCK_NB)',
            'pathlib.Path(sys.argv[1]).write_text("locked",encoding="ascii")',
            'time.sleep(30)',
        ))
        lock_argument=str(other_lock_path)
    process=subprocess.Popen([sys.executable,'-c',child_code,str(ready),lock_argument],
                             stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    try:
        for _ in range(200):
            if ready.exists():break
            if process.poll() is not None:
                pytest.fail('independent runtime lock holder exited early: '+process.stderr.read())
            time.sleep(0.025)
        assert ready.exists(), 'independent runtime did not acquire its lifecycle lock'
        receipt=mod.install_records(candidate_root,server,[record],stage,lambda port,host:False)
        assert (server/'plugins/new.jar').read_bytes()==new
        assert receipt['installed'][0]['pluginName']=='Example'
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill();process.wait(timeout=5)


def test_migration_test_server_launcher_holds_runtime_scoped_lock_for_local_offline_runtime():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert 'Local\\CopiMineMinecraft263RuntimeLifecycleLock-' in source
    assert 'ComputeHash' in source
    assert 'GetFullPath($runtime)' in source
    assert '.WaitOne(0)' in source
    assert '--startup-settings-json' in source
    assert '--unauthenticated-test-runtime' in source
    assert 'ConvertFrom-Json' in source
    assert "eula=true" in source
    assert 'AuthMe' in source
    assert 'AuthEffects' in source
    assert '$authEffectsJars.Count -gt 0' in source
    assert '$javaArguments = @(' in source
    assert '& $java @javaArguments' in source
    assert "'127.0.0.1'" in source
    assert 'voiceChatBindAddress' in source


def test_migration_server_uses_jvm_heap_base_and_startup_reserve_safeguards():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert "'-XX:HeapBaseMinAddress=4g'" in source
    assert "'-Xms128M'" in source


def test_local_voice_chat_configuration_rebinds_wildcard_to_loopback(tmp_path):
    mod=load()
    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    config=runtime/'plugins/voicechat/voicechat-server.properties'
    config.parent.mkdir(parents=True)
    config.write_text('# retain local settings\nport=24454\nbind_address=*\nkeep_alive=1000\n',encoding='utf-8')

    result=mod.configure_loopback_voicechat(runtime)

    assert result=={'voiceChatBindAddress':'127.0.0.1'}
    assert config.read_text(encoding='utf-8')=='# retain local settings\nport=24454\nbind_address=127.0.0.1\nkeep_alive=1000\n'


def test_local_voice_chat_configuration_rejects_duplicate_bind_address(tmp_path):
    mod=load()
    runtime=tmp_path/'local-runtime/end-rift-server-26.3'
    config=runtime/'plugins/voicechat/voicechat-server.properties'
    config.parent.mkdir(parents=True)
    config.write_text('bind_address=*\nbind_address=127.0.0.1\n',encoding='utf-8')

    with pytest.raises(ValueError,match='Duplicate voice chat bind_address'):
        mod.configure_loopback_voicechat(runtime)


def test_migration_test_server_launcher_supports_explicit_full_plugin_loopback_mode():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert '[switch]$FullPluginSet' in source
    assert 'if ($FullPluginSet)' in source
    assert "'--unauthenticated-test-runtime'" in source
    assert 'AuthMe and AuthEffects are required for -FullPluginSet' in source
    assert '127.0.0.1' in source
    assert 'enableRcon' in source


def test_migration_launcher_requires_full_auth_mode_for_receipted_local_end_event_build():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert '[switch]$LocalEndEventBuild' in source
    assert 'if ($LocalEndEventBuild -and -not $FullPluginSet)' in source
    assert "if ($LocalEndEventBuild) { $startupSettingsArguments += '--local-end-event-build' }" in source
    assert 'receipted local CopiMineEndEvent build' in source


def test_migration_test_server_launcher_checks_java_version_process_exit_code_explicitly():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert re.search(
        r'\$javaVersionProcess\s*=\s*Start-Process\s+-FilePath \$java[\s\S]*?-Wait[\s\S]*?-PassThru',
        source,
    )
    assert '$javaVersionProcess.ExitCode' in source
    assert '$javaVersionExitCode -ne 0' in source
    java_version_gate = source[source.index("$java = Join-Path $JavaHome"):source.index("$probe = [System.Net.Sockets.TcpClient]::new()")]
    assert '$LASTEXITCODE -ne 0' not in java_version_gate


def test_migration_test_server_launcher_bounds_idle_g1_heap_retention():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')
    java_arguments = source[source.index('$javaArguments = @('):source.index('Push-Location -LiteralPath $runtime')]

    assert "'-Xmx1536M'" in java_arguments
    assert "'-XX:MinHeapFreeRatio=20'" in java_arguments
    assert "'-XX:MaxHeapFreeRatio=40'" in java_arguments
    assert 'SoftMaxHeapSize' not in java_arguments


def test_migration_plugin_builder_checks_java_version_process_exit_code_explicitly():
    source=(ROOT/'scripts/minecraft/BuildMigrationPlugins.ps1').read_text(encoding='utf-8')

    assert re.search(
        r'\$javaVersionProcess\s*=\s*Start-Process\s+-FilePath \$java[\s\S]*?-Wait[\s\S]*?-PassThru',
        source,
    )
    assert '$javaVersionProcess.ExitCode' in source
    assert '$javaVersionExitCode -ne 0' in source
    java_version_gate = source[source.index("$java = Join-Path $JavaHome"):source.index("$toolchain = Join-Path $root")]
    assert '$LASTEXITCODE -ne 0' not in java_version_gate


def test_migration_build_helpers_and_gradle_require_the_exact_profile_java_patch():
    plugin_builder=(ROOT/'scripts/minecraft/BuildMigrationPlugins.ps1').read_text(encoding='utf-8')
    coreprotect_builder=(ROOT/'scripts/minecraft/BuildCoreProtectMigration.ps1').read_text(encoding='utf-8')
    gradle_build=(ROOT/'tools/minecraft-26.3/build.gradle').read_text(encoding='utf-8')

    assert "$javaVersionValue -cne [string]$profile.buildJavaVersion" in plugin_builder
    assert "$javaVersion -cne [string]$profile.buildJavaVersion" in coreprotect_builder
    assert "System.getProperty('java.version')" in gradle_build
    assert 'currentJavaVersion != profile.buildJavaVersion' in gradle_build


def test_migration_test_server_launcher_uses_its_isolated_database_environment():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert "Ensure-MigrationPostgresReady -WorkspaceRoot $root" in source
    assert 'InitializeMigrationPostgres.ps1' not in source
    assert "'POSTGRES_HOST=127.0.0.1'" in source
    assert "'POSTGRES_PORT=55434'" in source
    assert "'POSTGRES_DB=copimine_migration_test'" in source
    assert "'POSTGRES_SCHEMA=copimine_migration_26_3_candidate'" in source
    assert "$null = Assert-MigrationDatabaseEnvironment -Path $databaseEnvironmentFile -RequiredSettings $requiredDatabaseSettings" in source
    assert "$env:COPIMINE_ENV_FILE = $databaseEnvironmentFile" in source
    assert "Import-Module -Name (Join-Path $PSScriptRoot 'MigrationDatabaseEnvironment.psm1')" in source
    assert "$previousDatabaseEnvironment = Clear-MigrationDatabaseOverrides" in source
    assert source.index('$previousDatabaseEnvironment = Clear-MigrationDatabaseOverrides') < source.index('& $java @javaArguments')
    assert "Remove-Item -LiteralPath 'Env:COPIMINE_ENV_FILE'" in source
    assert "'COPIMINE_ENV_FILE'" in source
    finally_block = source.rsplit('finally {', 1)[1]
    assert 'Restore-MigrationDatabaseOverrides -Values $previousDatabaseEnvironment' in finally_block


def test_migration_database_environment_clears_all_overrides_and_restores_them():
    module = ROOT/'scripts/minecraft/MigrationDatabaseEnvironment.psm1'
    powershell = shutil.which('pwsh')
    if powershell is None:
        pytest.skip('PowerShell Core is required for the migration environment integration check.')

    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{module}' -Force
[Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', '   ', [EnvironmentVariableTarget]::Process)
[Environment]::SetEnvironmentVariable('POSTGRES_HOST', ' ', [EnvironmentVariableTarget]::Process)
[Environment]::SetEnvironmentVariable('POSTGRES_USER', 'runtime-override', [EnvironmentVariableTarget]::Process)
[Environment]::SetEnvironmentVariable('PGHOST', 'remote-db-override', [EnvironmentVariableTarget]::Process)
[Environment]::SetEnvironmentVariable('PGPASSWORD', 'runtime-password-override', [EnvironmentVariableTarget]::Process)
$envFile = Join-Path ([IO.Path]::GetTempPath()) ('migration-environment-' + [guid]::NewGuid().ToString('N') + '.env')
[IO.File]::WriteAllText($envFile, "POSTGRES_PASSWORD=fixture-password-from-file`nPOSTGRES_HOST=127.0.0.1`nPOSTGRES_USER=file-user`nPGHOST=127.0.0.1`nPGPASSWORD=fixture-pg-password-from-file`n")
$saved = Clear-MigrationDatabaseOverrides
$passwordCleared = $null -eq [Environment]::GetEnvironmentVariable('POSTGRES_PASSWORD', [EnvironmentVariableTarget]::Process)
$hostCleared = $null -eq [Environment]::GetEnvironmentVariable('POSTGRES_HOST', [EnvironmentVariableTarget]::Process)
$postgresUserCleared = $null -eq [Environment]::GetEnvironmentVariable('POSTGRES_USER', [EnvironmentVariableTarget]::Process)
$pgHostCleared = $null -eq [Environment]::GetEnvironmentVariable('PGHOST', [EnvironmentVariableTarget]::Process)
$pgPasswordCleared = $null -eq [Environment]::GetEnvironmentVariable('PGPASSWORD', [EnvironmentVariableTarget]::Process)
$settings = @{{}}
foreach ($entry in [Environment]::GetEnvironmentVariables([EnvironmentVariableTarget]::Process).GetEnumerator()) {{
    $settings[[string]$entry.Key] = [string]$entry.Value
}}
foreach ($line in [IO.File]::ReadAllLines($envFile)) {{
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) {{ continue }}
    $separator = $trimmed.IndexOf('=')
    $key = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim().Replace('"', '')
    if (-not $settings.ContainsKey($key)) {{ $settings[$key] = $value }}
}}
$fileBackfill = $settings['POSTGRES_PASSWORD'] -ceq 'fixture-password-from-file' -and $settings['POSTGRES_HOST'] -ceq '127.0.0.1' -and $settings['POSTGRES_USER'] -ceq 'file-user' -and $settings['PGHOST'] -ceq '127.0.0.1' -and $settings['PGPASSWORD'] -ceq 'fixture-pg-password-from-file'
Restore-MigrationDatabaseOverrides -Values $saved
$restored = [Environment]::GetEnvironmentVariable('POSTGRES_PASSWORD', [EnvironmentVariableTarget]::Process) -ceq '   ' -and [Environment]::GetEnvironmentVariable('POSTGRES_HOST', [EnvironmentVariableTarget]::Process) -ceq ' ' -and [Environment]::GetEnvironmentVariable('POSTGRES_USER', [EnvironmentVariableTarget]::Process) -ceq 'runtime-override' -and [Environment]::GetEnvironmentVariable('PGHOST', [EnvironmentVariableTarget]::Process) -ceq 'remote-db-override' -and [Environment]::GetEnvironmentVariable('PGPASSWORD', [EnvironmentVariableTarget]::Process) -ceq 'runtime-password-override'
Remove-Item -LiteralPath $envFile -Force
[PSCustomObject]@{{ passwordCleared = $passwordCleared; hostCleared = $hostCleared; postgresUserCleared = $postgresUserCleared; pgHostCleared = $pgHostCleared; pgPasswordCleared = $pgPasswordCleared; fileBackfill = $fileBackfill; restored = $restored }} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, '-NoProfile', '-Command', command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 0, result.stderr
    assert json.loads(result.stdout) == {
        'passwordCleared': True,
        'hostCleared': True,
        'postgresUserCleared': True,
        'pgHostCleared': True,
        'pgPasswordCleared': True,
        'fileBackfill': True,
        'restored': True,
    }


def test_runtime_startup_settings_rejects_java_properties_duplicate_loopback_binding(tmp_path):
    mod=load();runtime=tmp_path/'local-runtime/end-rift-server-26.3';runtime.mkdir(parents=True)
    (runtime/'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-ip:0.0.0.0\nserver-port=25566\n'
        'online-mode=false\nenable-rcon=false\n',encoding='utf-8')

    with pytest.raises(ValueError,match='Duplicate runtime server-ip'):
        mod.runtime_startup_settings(runtime)


def test_runtime_startup_settings_accepts_java_colon_and_whitespace_separators(tmp_path):
    mod=load();runtime=tmp_path/'local-runtime/end-rift-server-26.3';runtime.mkdir(parents=True)
    (runtime/'server.properties').write_text(
        'server-ip : 127.0.0.1\nserver-port 25566\nonline-mode false\n'
        'enable-rcon : false\n',encoding='utf-8')

    assert mod.runtime_startup_settings(runtime)=={
        'serverIp':'127.0.0.1','serverPort':25566,'onlineMode':'false','enableRcon':'false'}


def test_plugin_install_rejects_online_mode_for_licensed_and_offline_local_test_clients(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    properties=server/'server.properties'
    properties.write_text(properties.read_text(encoding='utf-8').replace('online-mode=false','online-mode=true'),encoding='utf-8')
    checked=[]

    with pytest.raises(ValueError,match='online-mode=false'):
        mod.install_records(tmp_path,server,[record],stage,lambda port,host:checked.append((port,host)) or False)

    assert checked==[]
    assert (server/'plugins/old.jar').read_bytes()==old
    assert not (server/'plugins/new.jar').exists()


def test_upstream_baseline_plugins_have_locked_candidates_that_declare_minecraft_26_3():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baseline={row['pluginName']:row for row in lock['unchangedBaseline']}
    candidates={row['pluginName']:row for row in lock['modules']}
    expected={
        'Chunky':('1.5.3','Chunky-Bukkit-1.5.3.jar','https://cdn.modrinth.com/data/fALzjamp/versions/MdY6JATr/Chunky-Bukkit-1.5.3.jar'),
        'GSit':('3.7.0','GSit-3.7.0.jar','https://hangarcdn.papermc.io/plugins/Gecolay/GSit/versions/3.7.0/PAPER/GSit-3.7.0.jar'),
        'LuckPerms':('5.5.87','LuckPerms-Bukkit-5.5.87.jar','https://download.luckperms.net/1674/bukkit/loader/LuckPerms-Bukkit-5.5.87.jar'),
        'Vault':('2.20.3','VaultUnlocked-2.20.3.jar','https://cdn.modrinth.com/data/ayRaM8J7/versions/qZgRzoYs/VaultUnlocked-2.20.3.jar'),
    }
    for plugin_name,(version,filename,url) in expected.items():
        candidate=candidates[plugin_name]
        assert candidate['version']==version
        assert candidate['filename']==filename
        assert candidate['url']==url
        assert candidate['compatibilityStatus']=='requires-runtime-validation'
        assert candidate['nativeVerified'] is False
        assert re.fullmatch(r'[0-9a-f]{64}',candidate['sha256'])
        assert re.fullmatch(r'[0-9a-f]{128}',candidate['sha512'])
        assert candidate['size']>0
        assert baseline[plugin_name]['sha256'] in candidate['replaceSha256']
        assert '26.3' in candidate['gameVersions']

    assert candidates['GSit']['sha256']=='57e119ca79d60fae98bffbb84961a78d70c2403698c4ebe208cae09d69302cea'
    assert candidates['GSit']['sha512']=='5a3941efcf28204c6f60fb90482ddfddfb14aa4d08dff03d5ba53258ba417b78942002b7fbb578ee15c562fcd7442a197629ab004ed78d64a6c432e4ca49bfad'
    assert candidates['GSit']['sha1']=='4bba0e96d9011864aa642c7901e489dd843f357b'


def test_26_3_plugin_lock_pins_current_luckperms_and_reproducible_first_party_candidates():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    modules={row['pluginName']:row for row in lock['modules']}
    baseline={row['pluginName']:row for row in lock['unchangedBaseline']}

    luckperms=modules['LuckPerms']
    assert luckperms['version']=='5.5.87'
    assert luckperms['artifactVersion']=='5.5.87'
    assert luckperms['source']=='https://metadata.luckperms.net/data/all'
    assert luckperms['filename']=='LuckPerms-Bukkit-5.5.87.jar'
    assert luckperms['url']=='https://download.luckperms.net/1674/bukkit/loader/LuckPerms-Bukkit-5.5.87.jar'
    assert luckperms['sha1']=='072fbb8d2c70007c3170b12563af4911cee286da'
    assert luckperms['sha256']=='09d07b68965717976d2bab2f8d10436676f7c68d90bf6d24628cc0e5228bf406'
    assert luckperms['sha512']=='e4b277a5c80e78c96a0e7530bd3c9839e2d2bf294f339e5ae094d3da9b2e84f201bfe1b9751276519c3d057104065055bd1fec7e2be803524407267e7153d57d'
    assert luckperms['size']==1509479
    assert 'versionId' not in luckperms and 'projectId' not in luckperms
    assert luckperms['compatibilityStatus']=='requires-runtime-validation'
    assert luckperms['nativeVerified'] is False

    expected_candidates={
        'CopiMineArtifacts':('e82f5fa76818c0030cfaf43f2d40e0a6ac2e5726643c1c1e01ef1cc5d03054d2',
                             'dd1df56d8ec37d0db4aedb54a3d925e7b29af6035eb48d23ca01d7cb0a47754fdf36ded21c69ac04c5b46595272a38d05e4e8ea9df8bf0424e14962ed0ee503c',363351),
        'CopiMineEndEvent':('7f4c4cafa2a6aca85bea236337b6a200dec8761559a88ee4740c84db9a6204b0',
                            '885f1e620dce63206d3b135d73f3cc56022232609a9c1054707db1f15282c69cd4a424a92044c794ec40f797375d30d909043f0cc613dbd195589ac34379d5bb',1969295),
        'CopiMineNarcotics':('bea72432491f7afa0a0cbd23b608654b56899b8e463720f98dbfef0f39f2ef0d',
                             'c2e3439ca69fd8ecc2b19aa654a38fb6bcd81e5be1da2da52934988edf1f75a5bb0170cbada994015500a6ccc6b2fcb6e8bc2bf4222a154c1b1fe6b5c14a586b',370363),
    }
    for plugin_name,(sha256,sha512,size) in expected_candidates.items():
        candidate=baseline[plugin_name]['candidate']
        assert candidate['sha256']==sha256
        assert candidate['sha512']==sha512
        assert candidate['size']==size
        assert candidate['compatibilityStatus']=='requires-runtime-validation'
        assert candidate['nativeVerified'] is False


def test_artifacts_candidate_upgrade_accepts_approved_previous_candidates():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    artifacts=next(row for row in lock['unchangedBaseline'] if row['pluginName']=='CopiMineArtifacts')
    previous_candidates=(
        '3f859aac840fa60b1a76d4ee804c8b4cc506d1daafce1bbe883cb8fcaa0a3b86',
        'fb327767e393b7d3a3698d6d171223e64a9c7e049ebac986b74633acd198f264',
    )

    for previous in previous_candidates:
        assert previous in artifacts['replaceSha256'], (
            'the official installer must recognize the exact previous candidate before replacing it')
        assert previous in artifacts['candidate']['replaceSha256'], (
            'the local candidate lock must authorize replacing the exact previous candidate artifact')
