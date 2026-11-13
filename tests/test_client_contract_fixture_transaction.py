from pathlib import Path
import hashlib,json,os,shutil,subprocess
import pytest

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/minecraft/ClientContractFixtures.ps1'
@pytest.fixture(params=['pwsh','powershell.exe'])
def shell(request):
    executable=shutil.which(request.param)
    if not executable:pytest.skip(request.param+' is unavailable')
    return executable
NAMES=['CopiMineClient-0.1.1.jar','CustomSkinLoader_Fabric-14.26.1.jar','emotecraft-for-MC1.21.1-2.4.12-fabric.jar','fabric-api-0.116.12+1.21.1.jar','voicechat-fabric-1.21.1-2.6.16.jar','iris-fabric-1.8.8+mc1.21.1.jar','sodium-fabric-0.6.13+mc1.21.1.jar']

def command(root,*args):
    r=subprocess.run(['git','-C',str(root),*args],capture_output=True,text=True)
    assert r.returncode==0,r.stderr
    return r.stdout.strip()

def setup(root):
    root.mkdir()
    for rel in ['scripts/minecraft/MigrationOutputSnapshot.ps1','scripts/thirdparty/build_modpack.ps1']:
        p=root/rel;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/rel,p)
    command(root,'init','-q');(root/'README').write_text('fixture\n');command(root,'add','README')
    command(root,'-c','user.name=Fixture Probe','-c','user.email=fixture@example.invalid','commit','-qm','fixture')
    checks=[]
    for name in NAMES:
        p=root/'thirdparty/client-mods'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'old-'+name.encode());checks.append('SHA256  '+p.relative_to(root).as_posix()+'  '+hashlib.sha256(p.read_bytes()).hexdigest())
    p=root/'CopiMineClient/build/libs'/NAMES[0];p.parent.mkdir(parents=True);p.write_bytes(b'new-compiled-client')
    (root/'thirdparty/checksums.txt').write_text('\n'.join(checks)+'\n')
    (root/'thirdparty/thirdparty_manifest.json').write_text(json.dumps({'artifacts':{'clientMods':[{'path':'thirdparty/client-mods/'+NAMES[0],'sha1':'0'*40,'sha256':'0'*64}]},'clientArchive':{'path':'thirdparty/CopiMineMods.zip','sha1':'0'*40,'sha256':'0'*64}}))
    (root/'thirdparty/modpack_manifest.json').write_text('{"fixture":true}')
    (root/'thirdparty/_modpack_stage').mkdir();(root/'thirdparty/_modpack_stage/original-marker').write_bytes(b'preserve')
    (root/'unrelated-world.txt').write_bytes(b'untouched')

def state(root):
    return {p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob('*') if p.is_file() and '.git' not in p.relative_to(root).parts}

def call(root,backup,action,shell):
    env=dict(os.environ)
    if Path(shell).name.lower()=='powershell.exe':
        # A Python child does not get pwsh's automatic Windows PS module-path translation.
        env['PSMODULEPATH']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    return subprocess.run([shell,'-NoProfile','-ExecutionPolicy','Bypass','-File',str(HELPER),'-Action',action,'-WorkspaceRoot',str(root),'-BackupDirectory',str(backup)],capture_output=True,text=True,env=env)

@pytest.mark.parametrize('missing_dependency',[False,True])
def test_fixture_promotion_and_rollback_are_byte_exact(tmp_path,missing_dependency,shell):
    root=tmp_path/'workspace';backup=tmp_path/'backup';setup(root)
    if missing_dependency:(root/'thirdparty/client-mods'/NAMES[-1]).unlink()
    before=state(root);r=call(root,backup,'Prepare',shell)
    assert (r.returncode!=0)==missing_dependency,r.stdout+r.stderr
    assert (root/'thirdparty/client-mods'/NAMES[0]).read_bytes()==b'new-compiled-client'
    if not missing_dependency:
        import zipfile
        with zipfile.ZipFile(root/'thirdparty/CopiMineMods.zip') as z:
            entries=[name for name in z.namelist() if name.replace('\\','/')=='mods/'+NAMES[0]]
            assert len(entries)==1
            assert z.read(entries[0])==b'new-compiled-client'
        duplicate=call(root,backup,'Prepare',shell);assert duplicate.returncode!=0,'existing backup was overwritten'
    for _ in range(2):
        restored=call(root,backup,'Restore',shell);assert restored.returncode==0,restored.stdout+restored.stderr
        assert state(root)==before,'rollback lost or changed original fixture bytes'

def test_fixture_restore_refuses_another_source_commit(tmp_path,shell):
    root=tmp_path/'workspace';backup=tmp_path/'backup';setup(root);before=state(root)
    old=command(root,'rev-parse','HEAD');r=call(root,backup,'Prepare',shell);assert r.returncode==0,r.stdout+r.stderr
    command(root,'-c','user.name=Fixture Probe','-c','user.email=fixture@example.invalid','commit','--allow-empty','-qm','another source')
    r=call(root,backup,'Restore',shell);assert r.returncode!=0 and 'Source changed' in r.stderr
    command(root,'switch','--detach',old)
    r=call(root,backup,'Restore',shell);assert r.returncode==0,r.stdout+r.stderr
    assert state(root)==before
