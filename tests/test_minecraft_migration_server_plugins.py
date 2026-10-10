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
    assert 'if (!existing.equals(serverside))' in reconciliation
    assert reconciliation.index('if (resyncClient)') < reconciliation.index('player.platformPlayer.updateInventory()')
    assert reconciliation.index('player.platformPlayer.updateInventory()') < reconciliation.index('setItem(slot, serverside);')
    queued_resync = reconciliation.split('if (resyncClient)', 1)[1].split('setItem(slot, serverside);', 1)[0]
    assert 'player.platformPlayer.getGameMode() != GameMode.CREATIVE' in queued_resync
    assert queued_resync.index('GameMode.CREATIVE') < queued_resync.index('player.platformPlayer.updateInventory();')

def load():
    spec=importlib.util.spec_from_file_location('migration_plugins', ROOT/'scripts/minecraft/install_migration_server_plugins.py')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    return mod

def jar(name,version):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:z.writestr('plugin.yml',f'name: {name}\nversion: {version}\n')
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

    with pytest.raises(ValueError,match='temporary|symlink|link'):
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
        'candidate':{
            'filename':'CopiMineEndEvent-26.3.jar','runtimeFilename':'CopiMineEndEvent.jar',
            'sha256':hashlib.sha256(candidate).hexdigest(),
            'buildArtifact':'build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar',
        },
    }
    records=mod.load_install_records(tmp_path,{'modules':[],'unchangedBaseline':[baseline]})
    stage=tmp_path/'build/minecraft-26.3/server-plugins'
    stage.mkdir(parents=True)
    (stage/'CopiMineEndEvent-26.3.jar').write_bytes(b'partial cached candidate')
    mod.stage_records(records,stage,tmp_path)
    receipt=mod.install_records(tmp_path,server,records,stage,lambda port,host:False)

    assert len(records)==1
    assert (stage/'CopiMineEndEvent-26.3.jar').read_bytes()==candidate
    assert (server/'plugins/CopiMineEndEvent.jar').read_bytes()==candidate
    assert list((server/'migration-backups').rglob('CopiMineEndEvent.jar'))[0].read_bytes()==old
    assert receipt['installed'][0]['pluginName']=='CopiMineEndEvent'

def test_remote_unchanged_baseline_candidates_are_staged_once_with_module_overrides(tmp_path,monkeypatch):
    mod=load()
    example_old=jar('Example','1')
    example_new=jar('Example','2')
    farmcontrol=jar('FarmControl','1.3.0')
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
            },
        },
    ]
    records=mod.load_install_records(tmp_path,{'modules':[module],'unchangedBaseline':baselines})
    assert [record['pluginName'] for record in records]==['Example','FarmControl']
    assert records[1]['replaceSha256']==[hashlib.sha256(farmcontrol).hexdigest()]

    staged_bytes={'Example-2.jar':example_new,'FarmControl-1.3.0.jar':farmcontrol}
    def download(record,destination):
        destination.write_bytes(staged_bytes[record['filename']])
    monkeypatch.setattr(mod,'download_candidate',download)
    stage=tmp_path/'build/minecraft-26.3/server-plugins'
    mod.stage_records(records,stage,tmp_path)
    assert (stage/'Example-2.jar').read_bytes()==example_new
    assert (stage/'FarmControl-1.3.0.jar').read_bytes()==farmcontrol

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
        (item.get('candidate',{}).get('buildArtifact') or item.get('candidate',{}).get('url'))
    ]
    build_candidates=[item for item in baseline_candidates if item.get('candidate',{}).get('buildArtifact')]
    remote_candidates=[item for item in baseline_candidates if item.get('candidate',{}).get('url')]
    assert len(build_candidates)==8
    assert len(remote_candidates)==4
    missing=[item['candidate']['buildArtifact'] for item in build_candidates
             if not (ROOT/item['candidate']['buildArtifact']).is_file()]
    if missing:pytest.skip('the pinned 26.3 first-party plugin builds are required: '+', '.join(missing))

    records=load().load_install_records(ROOT,lock)

    assert len(records)==len(lock['modules'])+len(build_candidates)+len(remote_candidates)==31
    assert len({record['pluginName'] for record in records})==31
    expected_local={item['pluginName'] for item in build_candidates}
    expected_local.update(item['pluginName'] for item in lock['modules'] if item.get('buildArtifact'))
    assert {record['pluginName'] for record in records if record['sourceType']=='local'}==expected_local
    narcotics=next(record for record in records if record['pluginName']=='CopiMineNarcotics')
    assert 'ead5a29b561e96e0c46ab252743297e991f8fb1e21432ca47c1571c28a502928' in narcotics['replaceSha256']

def test_production_or_symlink_target_is_refused(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    with pytest.raises(ValueError,match='isolated'):mod.install_records(tmp_path,tmp_path/'minecraft/server',[r],stage,lambda port,host:False)

def test_unsafe_filename_and_wrong_plugin_identity_are_refused(tmp_path):
    mod=load();server,stage,r,old,new=fixture(tmp_path)
    with pytest.raises(ValueError,match='filename'):mod.validate_record({**r,'filename':'../escape.jar'})
    different=jar('Different','2');(stage/'new.jar').write_bytes(different)
    altered={**r,'sha512':hashlib.sha512(different).hexdigest(),'size':len(different)}
    with pytest.raises(ValueError,match='identity'):mod.install_records(tmp_path,server,[altered],stage,lambda port,host:False)

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

def test_grim263_patch_changes_only_inventory_class_when_built():
    original=ROOT/'build/minecraft-26.3/server-plugins/grimac-bukkit-2.3.74-f5bbe9c.jar'
    patched=original.with_name('GrimAC-2.3.74-f5bbe9c-creative-fix.jar')
    if not original.exists() or not patched.exists():pytest.skip('26.3 upstream plugin candidate and compatibility build required')
    with zipfile.ZipFile(original) as a,zipfile.ZipFile(patched) as b:
        assert set(a.namelist())==set(b.namelist())
        assert sorted(n for n in a.namelist() if a.read(n)!=b.read(n))==['ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.class']

def test_grim263_patch_build_is_reproducible():
    original=ROOT/'build/minecraft-26.3/server-plugins/grimac-bukkit-2.3.74-f5bbe9c.jar'
    patched=original.with_name('GrimAC-2.3.74-f5bbe9c-creative-fix.jar')
    powershell=shutil.which('pwsh') or shutil.which('powershell')
    major=json.loads((ROOT/'tools/minecraft-26.3/profile.lock.json').read_text())['grimPatchJavaMajor']
    java_home=os.environ.get(f'JAVA_HOME_{major}_X64') or os.environ.get('JAVA_HOME')
    compiler=Path(java_home)/'bin/javac.exe' if java_home else None
    if not original.is_file() or not patched.is_file() or not compiler or not compiler.is_file() or not powershell:
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
        ],capture_output=True,text=True,timeout=60)
        assert result.returncode==0, result.stdout+result.stderr
        hashes.append(hashlib.sha256(patched.read_bytes()).hexdigest())
    assert hashes[0]==hashes[1]
    record=next(x for x in json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text())['modules']
                if x['pluginName']=='GrimAC')
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
    runtime=ROOT/'local-runtime/end-rift-server-26.3/plugins'/candidate['runtimeFilename']
    assert built.is_file(), 'the pinned 26.3 AdminPlus candidate build is missing'
    artifacts=[built]+([runtime] if runtime.is_file() else [])
    for artifact in artifacts:
        assert hashlib.sha256(artifact.read_bytes()).hexdigest()==candidate['sha256']

def test_all_remaining_first_party_26_3_plugins_track_their_exact_build_and_runtime_jars():
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
        runtime=ROOT/'local-runtime/end-rift-server-26.3/plugins'/candidate['runtimeFilename']
        assert built.is_file(), f'missing 26.3 build artifact for {plugin_name}'
        artifacts=[built]+([runtime] if runtime.is_file() else [])
        for artifact in artifacts:
            assert hashlib.sha256(artifact.read_bytes()).hexdigest()==candidate['sha256']
            with zipfile.ZipFile(artifact) as jar_file:
                plugin_yml=jar_file.read('plugin.yml').decode('utf-8')
            assert f'name: {plugin_name}' in plugin_yml
            assert "api-version: '26.3'" in plugin_yml

def test_end_event_26_3_candidate_tracks_and_installs_the_real_target_jar():
    lock=json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    baseline=next(p for p in lock['unchangedBaseline'] if p['pluginName']=='CopiMineEndEvent')
    assert baseline['sha256']=='c32ff397bfa01a4279524ff9d9b31dcd8b18a0f7836a09cabecdbcd2e07f0cc3'
    candidate=baseline['candidate']
    assert candidate['filename']=='CopiMineEndEvent-26.3.jar'
    assert candidate['runtimeFilename']=='CopiMineEndEvent.jar'
    assert candidate['apiVersion']=='26.3'
    assert candidate['buildArtifact']=='build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar'
    assert candidate['nativeVerified'] is lock['nativeVerified']
    assert baseline['replaceSha256']==[
        '86820193e67f166d79128d224e4ab9abf7dafce2c6efaad21b4dc6134bfaf4d7',
        'e2335f9462f61c0f255a87cc3609520099f3836843f46aee6dd0a910dad636bd',
        'd63ed7c3c3d810a5ecb8ea8f497ecb24004cf195381e410bfbb8d182094842c0',
    ]

    built=ROOT/candidate['buildArtifact']
    runtime=ROOT/'local-runtime/end-rift-server-26.3/plugins'/candidate['runtimeFilename']
    assert built.is_file(), 'the pinned first-party 26.3 candidate build is missing'
    artifacts=[built]+([runtime] if runtime.is_file() else [])
    for artifact in artifacts:
        assert hashlib.sha256(artifact.read_bytes()).hexdigest()==candidate['sha256']
        with zipfile.ZipFile(artifact) as jar_file:
            plugin_yml=jar_file.read('plugin.yml').decode('utf-8')
        assert 'api-version: \'26.3\'' in plugin_yml

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
        if baseline['pluginName'] in module_names:
            continue
        candidate=baseline.get('candidate')
        expected[baseline['pluginName']]={
            'filename':candidate['runtimeFilename'] if candidate else baseline['filename'],
            'sha256':candidate['sha256'] if candidate else baseline['sha256'],
        }

    assert len({entry['filename'] for entry in expected.values()})==len(expected), 'plugin lock must not assign one runtime filename to multiple plugins'
    runtime=ROOT/'local-runtime/end-rift-server-26.3/plugins'
    if not runtime.is_dir():pytest.skip('isolated 26.3 runtime is not installed in this checkout')
    assert len(expected)==31, 'replacement candidates must collapse to one entry per installed plugin'
    server=runtime.parent
    receipt=json.loads((server/'migration-plugin-receipt.json').read_text(encoding='utf-8'))
    if receipt.get('authenticationMode')=='disabled-for-local-testing':
        disabled_authme=expected.pop('AuthMe')
        backups=list((server/'migration-backups/plugins').rglob(disabled_authme['filename']))
        assert len(backups)==1, 'test-only AuthMe disablement must preserve exactly one locked backup'
        assert backups[0].is_file() and not backups[0].is_symlink(), 'pinned AuthMe backup must be a regular file, not a symlink'
        assert hashlib.sha256(backups[0].read_bytes()).hexdigest()==disabled_authme['sha256']
    else:
        assert receipt.get('authenticationMode')=='AuthMe-6.0.1', 'runtime receipt must declare a recognized authentication mode'
    actual_files={path.name for path in runtime.glob('*.jar')}
    expected_files={entry['filename'] for entry in expected.values()}
    assert actual_files==expected_files, f'unlocked or missing plugin jars: {actual_files ^ expected_files}'
    receipt_rows=receipt['pluginInventory']
    receipt_inventory={entry['pluginName']:{'filename':entry['filename'],'sha256':entry['sha256']} for entry in receipt_rows}
    assert len(receipt_inventory)==len(receipt_rows), 'runtime receipt must not contain duplicate plugin rows'
    assert receipt_inventory==expected, 'runtime receipt names, filenames, and digests must match the active locked plugin inventory'
    for plugin_name,entry in expected.items():
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
    mod=load();old=jar('ClearLag','1.12.1');new=jar('ClearLag','1.14.1')
    server=tmp_path/'local-runtime/end-rift-server-26.3';plugins=server/'plugins';config_dir=plugins/'ClearLag'
    config_dir.mkdir(parents=True)
    (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nrcon.port=25576\nenable-rcon=false\n')
    prepare_owned_runtime_fixture(tmp_path,server)
    old_path=plugins/'ClearLag.jar';old_path.write_bytes(old)
    config=b'auto-updater:\n  enabled: false\nauto-clear:\n  clear-entities: false\n'
    (config_dir/'config.yml').write_bytes(config)
    candidate=next(p for p in json.loads((ROOT/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))['modules'] if p['pluginName']=='ClearLag')
    stage=tmp_path/'build/server-plugins';stage.mkdir(parents=True);(stage/candidate['filename']).write_bytes(new)
    record={'pluginName':'ClearLag','version':'1.14.1','filename':candidate['filename'],
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
    assert candidate['version']=='1.14.1'
    assert candidate['versionId']=='raHM5t3P'
    assert candidate['projectId']=='7bhTp28M'
    assert candidate['channel']=='beta'
    assert candidate['filename']=='ClearLag 1.14.1 VER 1.18-26.3.jar'
    assert candidate['url']=='https://cdn.modrinth.com/data/7bhTp28M/versions/raHM5t3P/ClearLag%201.14.1%20VER%201.18-26.3.jar'
    assert candidate['sha512']=='b6111de4e5b1d201cae890eead341e53272edc24cc205dfad92c7558169a7730447a7c10cac123b8f1696937658a3849216f8e278ffa566832a47015fd002c3e'
    assert candidate['sha256']=='3b73f36478f7ccebb2c1fc482a2724c1b5e75dd0835c0ec35a765249639991ba'
    assert candidate['size']==303_709
    assert candidate['license']['id']=='LicenseRef-All-Rights-Reserved'
    assert candidate['replaceSha256']==[baseline['sha256']]
    assert '26.3' in candidate['gameVersions']
    assert candidate['compatibilityStatus']=='requires-runtime-validation'
    assert candidate['nativeVerified'] is lock['nativeVerified']

    staged=ROOT/'build/minecraft-26.3/server-plugins'/candidate['filename']
    assert staged.is_file(), 'the pinned ClearLag 26.3 candidate is missing'
    assert staged.stat().st_size==candidate['size']
    assert hashlib.sha512(staged.read_bytes()).hexdigest()==candidate['sha512']
    assert hashlib.sha256(staged.read_bytes()).hexdigest()==candidate['sha256']
    assert load().identity(staged)=='ClearLag'

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

    runtime=ROOT/'local-runtime/end-rift-server-26.3/plugins'/candidate['filename']
    if not runtime.is_file():
        pytest.skip('ProtocolLib runtime candidate is not installed in this checkout')
    assert hashlib.sha256(runtime.read_bytes()).hexdigest()==candidate['sha256']
    with zipfile.ZipFile(runtime) as archive:
        packets=archive.read('com/comphenix/protocol/PacketType$Play$Server.class')
    assert b'v26_3' in packets
    for packet in [b'ADD_TRANSIENT_BLOCK',b'HURT_ANIMATION',b'SHOW_DIALOG']:
        assert packet in packets


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
            'version':'v5.5.71-bukkit','versionId':'b0mk8uS6','projectId':'Vebnzrzj',
            'filename':'LuckPerms-Bukkit-5.5.71.jar',
            'url':'https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar',
            'sha512':'188a91f0a543d23bfda32385fca6db63d61e49c8a422bd452a260bd9cbc6a7d7fe45071199e9fca8f3ce43c2b41ee84fd315bd15464577028ff3951a7d4fab27',
            'sha256':'49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d','size':1_501_521,
        },
        'GSit':{
            'version':'3.7.0','versionId':'gnY5Flgo','projectId':'GOHbQGyX',
            'filename':'GSit-3.7.0.jar',
            'url':'https://cdn.modrinth.com/data/GOHbQGyX/versions/gnY5Flgo/GSit-3.7.0.jar',
            'sha512':'7813fe99d2fde47e3a40538c09b87b88b6101c27fb4f3305e0fd9bf806809c37e4e67c726f96596caca79785cdd7aa8d8ae724b211535775db21e602f57993d5',
            'sha256':'143d38509f49c40b214887ad3c682c8db7fdd81c610787d0186d65f51256f4d2','size':811_798,
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
        assert candidate['versionId']==metadata['versionId']
        assert candidate['projectId']==metadata['projectId']
        assert candidate['filename']==metadata['filename']
        assert candidate['url']==metadata['url']
        assert candidate['sha512']==metadata['sha512']
        assert candidate['sha256']==metadata['sha256']
        assert candidate['size']==metadata['size']
        assert candidate['replaceSha256']==[baseline['sha256']]
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
    assert (runtime/'eula.txt').read_text(encoding='utf-8').strip()=='eula=false'
    assert (runtime/'plugins').is_dir()


def test_unauthenticated_local_test_mode_omits_only_authme():
    mod=load()
    records=[
        {'pluginName':'AuthMe','filename':'AuthMe-6.0.1-Paper.jar'},
        {'pluginName':'CoreProtect','filename':'CoreProtect-26.3.jar'},
    ]

    selected=mod.select_runtime_records(records,unauthenticated_test_mode=True)

    assert [record['pluginName'] for record in selected]==['CoreProtect']
    assert mod.select_runtime_records(records,unauthenticated_test_mode=False)==records


def test_unauthenticated_local_test_mode_moves_only_pinned_authme_to_backup(tmp_path):
    mod=load();server,stage,record,old,new=fixture(tmp_path)
    authme=jar('AuthMe','6.0.1')
    authme_path=server/'plugins/AuthMe-6.0.1-Paper.jar'
    authme_path.write_bytes(authme)
    authme_lock=tmp_path/'tools/minecraft-26.3/server-plugins.lock.json'
    authme_lock.parent.mkdir(parents=True,exist_ok=True)
    authme_lock.write_text(json.dumps({'modules':[{
        'pluginName':'AuthMe','sha256':hashlib.sha256(authme).hexdigest(),'replaceSha256':[],
    }]}),encoding='utf-8')

    receipt=mod.install_records(tmp_path,server,[record],stage,lambda port,host:False,
                                unauthenticated_test_mode=True)

    assert not authme_path.exists()
    backup_files=list((server/'migration-backups/plugins').glob('*/AuthMe-6.0.1-Paper.jar'))
    assert len(backup_files)==1 and backup_files[0].read_bytes()==authme
    assert (server/'plugins/new.jar').read_bytes()==new
    assert receipt['authenticationMode']=='disabled-for-local-testing'


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


def test_prepare_runtime_migrates_owned_older_paper_and_preserves_data_and_configs(tmp_path,capsys):
    mod=load();old_content,old_filename=write_bootstrap_fixture(tmp_path)
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

    monkeypatch.setattr(mod.os,'rename',real_rename)
    _,error=invoke_runtime_prepare(mod,tmp_path)

    assert error is None, f'prepare should recover the archived runtime and resume the upgrade, got {error}'
    assert (runtime/'world/region/r.0.0.mca').read_bytes()==b'world data must survive an interrupted Paper swap'
    metadata=json.loads((runtime/'copimine-migration-runtime.json').read_text(encoding='utf-8'))
    assert metadata['paperFilename']==new_filename


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


def _write_startup_validation_fixture(root):
    mod=load()
    runtime=root/'local-runtime/end-rift-server-26.3'
    plugins=runtime/'plugins'
    plugins.mkdir(parents=True)
    (runtime/'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25566\nonline-mode=false\nenable-rcon=false\n',encoding='utf-8')
    plugin_rows=[]
    records=[]
    for name,version,filename in (
        ('CoreProtect','26.3','CoreProtect-26.3.jar'),
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
        if name!='AuthMe':(plugins/filename).write_bytes(content)
        if name!='AuthMe':
            plugin_rows.append({
                'pluginName':name,'filename':filename,'sha256':hashlib.sha256(content).hexdigest(),
                'sha512':hashlib.sha512(content).hexdigest(),
            })
    lock=root/'tools/minecraft-26.3/server-plugins.lock.json'
    lock.parent.mkdir(parents=True)
    lock.write_text(json.dumps({'modules':records,'unchangedBaseline':[]}),encoding='utf-8')
    receipt={
        'schemaVersion':1,'minecraftVersion':'26.3','nativeVerified':False,
        'authenticationMode':'disabled-for-local-testing',
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
    lock_name='Local\\CopiMineMinecraft263RuntimeLifecycleLock'
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


def test_migration_test_server_launcher_holds_shared_lock_for_local_offline_runtime():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert 'Local\\CopiMineMinecraft263RuntimeLifecycleLock' in source
    assert '.WaitOne(0)' in source
    assert '--startup-settings-json' in source
    assert '--unauthenticated-test-runtime' in source
    assert 'ConvertFrom-Json' in source
    assert "eula=true" in source
    assert 'AuthMe' in source
    assert '$javaArguments = @(' in source
    assert '& $java @javaArguments' in source
    assert "'127.0.0.1'" in source
    assert 'voiceChatBindAddress' in source


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
    assert 'AuthMe is required for -FullPluginSet' in source
    assert '127.0.0.1' in source
    assert 'enableRcon' in source


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


def test_migration_test_server_launcher_uses_its_isolated_database_environment():
    source=(ROOT/'scripts/minecraft/StartMigrationTestServer.ps1').read_text(encoding='utf-8')

    assert "$databaseEnvironmentFile = Join-Path $runtime 'migration-test.env'" in source
    assert "Test-Path -LiteralPath $databaseEnvironmentFile -PathType Leaf" in source
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
