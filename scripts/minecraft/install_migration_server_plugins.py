"""Install pinned plugin candidates into the isolated 26.3 runtime while offline.

This receipt proves installed bytes, not gameplay compatibility. Old plugin JARs
are retained outside the active plugin directory; configurations are untouched.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import glob
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import subprocess
import tempfile
import urllib.request
import zipfile


def validate_record(record):
    name=record['filename']
    if not isinstance(name,str) or not name.endswith('.jar') or Path(name).name!=name or any(c in name for c in '/\\:'):
        raise ValueError('Unsafe plugin filename')
    runtime_name=record.get('runtimeFilename') or name
    if not isinstance(runtime_name,str) or not runtime_name.endswith('.jar') or Path(runtime_name).name!=runtime_name or any(c in runtime_name for c in '/\\:'):
        raise ValueError('Unsafe runtime plugin filename')
    if not re.fullmatch(r'[0-9a-f]{128}',record['sha512']):raise ValueError('Invalid plugin digest')
    if not isinstance(record['size'],int) or not 0<record['size']<100_000_000:raise ValueError('Invalid plugin size')
    if not re.fullmatch(r'[A-Za-z0-9_]+',record['pluginName']):raise ValueError('Invalid plugin identity')
    replacements=record.get('replaceSha256',[])
    if not isinstance(replacements,list) or any(not isinstance(value,str)
                                                or not re.fullmatch(r'[0-9a-f]{64}',value)
                                                for value in replacements):
        raise ValueError('Invalid plugin replacement SHA-256 allowlist')
    if record.get('sourceType')=='local':
        if not re.fullmatch(r'[0-9a-f]{64}',record.get('sha256','')):
            raise ValueError('Invalid local plugin SHA-256')
    elif not record.get('url','').startswith((
        'https://cdn.modrinth.com/data/',
        'https://github.com/dmulloy2/ProtocolLib/releases/download/',
        'https://github.com/froobynooby/FarmControl/releases/download/',
        'https://ci.ender.zone/job/EssentialsX/',
    )) and not re.fullmatch(
        r'https://hangarcdn\.papermc\.io/plugins/Gecolay/GSit/versions/\d+(?:\.\d+){2}/PAPER/GSit-\d+(?:\.\d+){2}\.jar',
        record.get('url','')
    ):
        raise ValueError('Unrecognized plugin source')


def identity(path):
    with zipfile.ZipFile(path) as jar:
        entry='paper-plugin.yml' if 'paper-plugin.yml' in jar.namelist() else 'plugin.yml'
        info=jar.getinfo(entry)
        if info.file_size>256_000:raise ValueError('Plugin descriptor exceeds limit')
        text=jar.read(entry).decode('utf-8-sig')
    pattern=(
        r"(?m)^name:[ \t]*(?:"
        r"'(?P<single>[^'\r\n]+)'|"
        r'"(?P<double>[^"\r\n]+)"|'
        r"(?P<plain>[A-Za-z0-9 _.-]+?))[ \t]*(?:#.*)?\r?$"
    )
    match=re.search(pattern,text)
    if not match:raise ValueError('Missing plugin identity')
    name=next(value for value in (match.group('single'),match.group('double'),match.group('plain')) if value is not None).strip()
    if not re.fullmatch(r'[A-Za-z0-9 _.-]+',name):raise ValueError('Invalid plugin identity')
    return name


def digest(path,algorithm):
    with path.open('rb') as stream:return hashlib.file_digest(stream,algorithm).hexdigest()


def capture_plugin_inventory(plugins):
    plugins=Path(plugins)
    if not plugins.is_dir() or plugins.resolve()!=plugins:
        raise ValueError('Plugin inventory requires the isolated regular plugin directory')
    rows=[];names=set();filenames=set()
    for path in sorted(plugins.glob('*.jar'),key=lambda item:item.name.casefold()):
        _ensure_regular_file(path,'installed plugin JAR')
        plugin_name=identity(path)
        if plugin_name in names or path.name in filenames:
            raise ValueError('Duplicate installed plugin identity or filename: '+plugin_name)
        names.add(plugin_name);filenames.add(path.name)
        rows.append({'pluginName':plugin_name,'filename':path.name,
                     'sha256':digest(path,'sha256'),'sha512':digest(path,'sha512')})
    return rows


def complete_receipt(server,receipt,records):
    plugins=Path(server)/'plugins'
    inventory=capture_plugin_inventory(plugins)
    by_name={row['pluginName']:row for row in inventory}
    installed=[]
    for record in records:
        name=record['pluginName']
        row=by_name.get(name)
        if row is None or row['filename']!=(record.get('runtimeFilename') or record['filename']):
            raise ValueError('Installed plugin inventory differs from the replacement plan: '+name)
        installed.append({**row,'version':record['version']})
    receipt['installed']=installed
    receipt['pluginInventory']=inventory
    return receipt


def validate_active_plugin_inventory(server,records,*,unauthenticated_test_mode=False):
    server=Path(server)
    selected=select_runtime_records(records,unauthenticated_test_mode=unauthenticated_test_mode)
    expected={}
    for record in selected:
        validate_record(record)
        name=record['pluginName']
        filename=record.get('runtimeFilename') or record['filename']
        sha256=record.get('sha256')
        sha512=record.get('sha512')
        if not re.fullmatch(r'[0-9a-f]{64}',sha256 or '') or not re.fullmatch(r'[0-9a-f]{128}',sha512 or ''):
            raise ValueError('Locked plugin inventory lacks complete digests: '+name)
        if name in expected or filename in {row['filename'] for row in expected.values()}:
            raise ValueError('Locked plugin inventory contains duplicate identities or filenames')
        expected[name]={'pluginName':name,'filename':filename,'sha256':sha256,'sha512':sha512}

    plugins=server/'plugins'
    try:
        info=plugins.lstat()
    except FileNotFoundError as error:
        raise ValueError('Active plugin inventory directory is missing') from error
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(info.st_mode) or
        (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(info.st_mode)):
        raise ValueError('Active plugin inventory directory is not a plain directory')
    actual=capture_plugin_inventory(plugins)
    actual_by_name={row['pluginName']:row for row in actual}
    if len(actual_by_name)!=len(actual) or actual_by_name!=expected:
        raise ValueError('Active plugin inventory differs from the locked 26.3 candidates')

    receipt_path=server/'migration-plugin-receipt.json'
    try:
        _ensure_regular_file(receipt_path,'migration plugin receipt')
        if receipt_path.stat().st_size>1_000_000:
            raise ValueError('Migration plugin receipt exceeds the safe size limit')
        receipt=json.loads(receipt_path.read_text(encoding='utf-8-sig'))
    except FileNotFoundError as error:
        raise ValueError('Active migration plugin receipt is missing') from error
    except (OSError,UnicodeError,json.JSONDecodeError) as error:
        raise ValueError('Active migration plugin receipt is invalid') from error
    expected_authentication='disabled-for-local-testing' if unauthenticated_test_mode else 'AuthMe-6.0.1'
    if (not isinstance(receipt,dict) or receipt.get('schemaVersion')!=1 or
        receipt.get('minecraftVersion')!='26.3' or receipt.get('nativeVerified') is not False or
        receipt.get('authenticationMode')!=expected_authentication):
        raise ValueError('Active migration plugin receipt does not match the requested runtime mode')

    receipt_inventory=receipt.get('pluginInventory')
    if not isinstance(receipt_inventory,list):
        raise ValueError('Active migration plugin receipt has no complete plugin inventory')
    inventory_by_name={}
    for row in receipt_inventory:
        if not isinstance(row,dict):raise ValueError('Active migration plugin receipt inventory is malformed')
        name=row.get('pluginName')
        if not isinstance(name,str) or name in inventory_by_name:
            raise ValueError('Active migration plugin receipt contains duplicate or invalid plugin rows')
        inventory_by_name[name]={key:row.get(key) for key in ('pluginName','filename','sha256','sha512')}
    if inventory_by_name!=expected:
        raise ValueError('Active migration plugin receipt inventory differs from the locked candidates')

    installed=receipt.get('installed')
    if not isinstance(installed,list):
        raise ValueError('Active migration plugin receipt has no installed plugin list')
    installed_by_name={}
    for row in installed:
        if not isinstance(row,dict):raise ValueError('Active migration installed plugin receipt is malformed')
        name=row.get('pluginName')
        if not isinstance(name,str) or name in installed_by_name:
            raise ValueError('Active migration receipt contains duplicate or invalid installed plugin rows')
        installed_by_name[name]={
            'pluginName':name,'filename':row.get('filename'),'sha256':row.get('sha256'),
            'version':row.get('version'),
        }
    expected_installed={name:{
        'pluginName':name,'filename':record.get('runtimeFilename') or record['filename'],
        'sha256':record['sha256'],'version':record.get('version'),
    } for name,record in ((record['pluginName'],record) for record in selected)}
    if installed_by_name!=expected_installed:
        raise ValueError('Active installed plugin receipt differs from the locked candidates')
    return actual


def _path_exists(path):
    try:
        Path(path).lstat()
        return True
    except FileNotFoundError:
        return False


def _ensure_regular_file(path,label):
    path=Path(path)
    info=path.lstat()
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if stat.S_ISLNK(info.st_mode) or (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag):
        raise ValueError('Symlink or reparse point refused: '+label)
    if not stat.S_ISREG(info.st_mode):raise ValueError('Non-regular file refused: '+label)


def _has_verified_plugin_backup(server,filename,expected_sha256):
    backup_root=Path(server)/'migration-backups/plugins'
    if not _path_exists(backup_root):return False
    root_info=backup_root.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(root_info.st_mode) or
        (reparse_flag and getattr(root_info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(root_info.st_mode)):
        raise ValueError('Symlink or non-directory refused: plugin backup root')
    for backup in backup_root.iterdir():
        backup_info=backup.lstat()
        if (stat.S_ISLNK(backup_info.st_mode) or
            (reparse_flag and getattr(backup_info,'st_file_attributes',0)&reparse_flag) or
            not stat.S_ISDIR(backup_info.st_mode)):
            raise ValueError('Symlink or non-directory refused: plugin backup entry')
        candidate=backup/filename
        if not _path_exists(candidate):continue
        _ensure_regular_file(candidate,'pinned authentication plugin backup')
        if digest(candidate,'sha256')==expected_sha256:return True
    return False


def _ensure_build_directory(root,path,create):
    root=Path(root).resolve()
    path=Path(os.path.abspath(path))
    build=root/'build'
    if path==build or not path.is_relative_to(build):
        raise ValueError('Plugin candidate staging must stay below the repository build directory')
    root_info=root.lstat()
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(root_info.st_mode) or
        (reparse_flag and getattr(root_info,'st_file_attributes',0)&reparse_flag)):
        raise ValueError('Symlink or reparse point refused: repository root')
    if not stat.S_ISDIR(root_info.st_mode):raise ValueError('Repository root is not a directory')

    current=root
    for part in path.relative_to(root).parts:
        current=current/part
        if not _path_exists(current):
            if not create:raise FileNotFoundError('Plugin candidate staging directory is missing')
            current.mkdir()
        info=current.lstat()
        if (stat.S_ISLNK(info.st_mode) or
            (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag)):
            raise ValueError('Symlink or reparse point refused: plugin candidate staging directory')
        if not stat.S_ISDIR(info.st_mode):raise ValueError('Plugin candidate staging path is not a directory')
    return path


def _ensure_runtime_parent(root,create):
    root=Path(root).resolve()
    root_info=root.lstat()
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(root_info.st_mode) or
        (reparse_flag and getattr(root_info,'st_file_attributes',0)&reparse_flag)):
        raise ValueError('Symlink or reparse point refused: repository root')
    if not stat.S_ISDIR(root_info.st_mode):raise ValueError('Repository root is not a directory')
    parent=root/'local-runtime'
    if not _path_exists(parent):
        if not create:raise FileNotFoundError('Isolated migration runtime directory is missing')
        parent.mkdir()
    info=parent.lstat()
    if (stat.S_ISLNK(info.st_mode) or
        (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag)):
        raise ValueError('Symlink or reparse point refused: isolated runtime directory')
    if not stat.S_ISDIR(info.st_mode):raise ValueError('Isolated runtime path is not a directory')
    return parent


def _locked_paper(root):
    profile_path=Path(root)/'tools/minecraft-26.3/profile.lock.json'
    try:profile=json.loads(profile_path.read_text(encoding='utf-8'))
    except (OSError,UnicodeError,json.JSONDecodeError) as error:
        raise ValueError('Minecraft 26.3 profile lock is invalid') from error
    if profile.get('minecraftVersion')!='26.3':
        raise ValueError('Migration runtime requires the locked Minecraft 26.3 profile')
    source_paper=profile.get('paper')
    if not isinstance(source_paper,dict):raise ValueError('Locked Paper server metadata is missing')
    build=source_paper.get('build')
    filename=f'paper-26.3-{build}.jar' if isinstance(build,int) and build>0 else ''
    sha256=source_paper.get('sha256')
    size=source_paper.get('size')
    expected_url=f'https://fill-data.papermc.io/v1/objects/{sha256}/{filename}'
    if (source_paper.get('channel') not in ('BETA','STABLE') or not filename or
        not isinstance(sha256,str) or not re.fullmatch(r'[0-9a-f]{64}',sha256) or
        not isinstance(size,int) or not 0<size<=500_000_000 or
        source_paper.get('url')!=expected_url):
        raise ValueError('Locked Paper server metadata is invalid')
    return {**source_paper,'filename':filename}


def _verify_prepared_runtime(runtime,paper,require_eula_false=False):
    runtime=Path(runtime)
    marker=runtime/'copimine-migration-runtime.json'
    if not _path_exists(marker):
        raise ValueError('Existing migration runtime is not owned by this pinned candidate')
    _ensure_regular_file(marker,'migration runtime marker')
    try:metadata=json.loads(marker.read_text(encoding='utf-8'))
    except (OSError,UnicodeError,json.JSONDecodeError) as error:
        raise ValueError('Migration runtime marker is invalid') from error
    expected={
        'schemaVersion':1,
        'minecraftVersion':'26.3',
        'paperFilename':paper['filename'],
        'paperSha256':paper['sha256'],
        'paperSize':paper['size'],
        'eulaAcceptedAtSetup':False,
    }
    if any(metadata.get(key)!=value for key,value in expected.items()):
        raise ValueError('Existing migration runtime is not owned by this pinned candidate')
    server_jar=runtime/paper['filename']
    _ensure_regular_file(server_jar,'prepared Paper server JAR')
    if server_jar.stat().st_size!=paper['size'] or digest(server_jar,'sha256')!=paper['sha256']:
        raise ValueError('Prepared Paper server JAR differs from the locked candidate')
    plugins=runtime/'plugins'
    if _path_exists(plugins):
        info=plugins.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
        if (stat.S_ISLNK(info.st_mode) or
            (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag)):
            raise ValueError('Symlink or reparse point refused: prepared plugins directory')
        if not stat.S_ISDIR(info.st_mode):raise ValueError('Prepared plugins path is not a directory')
    properties_path=runtime/'server.properties'
    _ensure_regular_file(properties_path,'prepared server.properties')
    _validate_local_runtime_properties(_java_property_entries(properties_path.read_text(encoding='utf-8')))
    eula_path=runtime/'eula.txt'
    _ensure_regular_file(eula_path,'prepared EULA file')
    try:eula_state=eula_path.read_text(encoding='ascii').strip().lower()
    except (OSError,UnicodeError) as error:
        raise ValueError('Prepared migration runtime EULA file is invalid') from error
    if eula_state not in {'eula=false','eula=true'}:
        raise ValueError('Prepared migration runtime EULA file must be eula=false or eula=true')
    if require_eula_false and eula_state!='eula=false':
        raise ValueError('Prepared migration runtime must retain eula=false')
    return runtime


def _assert_plain_runtime_tree(runtime):
    runtime=Path(runtime)
    pending=[runtime]
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    while pending:
        current=pending.pop()
        info=current.lstat()
        if stat.S_ISLNK(info.st_mode) or (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag):
            raise ValueError('Symlink or reparse point refused: isolated migration runtime tree')
        if stat.S_ISDIR(info.st_mode):
            pending.extend(current.iterdir())
        elif not stat.S_ISREG(info.st_mode):
            raise ValueError('Non-regular file refused: isolated migration runtime tree')


_PRIVATE_RUNTIME_FILES={
    'migration-postgres-admin.env',
    'migration-isolated-postgres.env',
    'postgres-initdb-password.tmp',
}


def _copy_windows_acl(source,destination):
    if os.name!='nt':return
    windows_root=Path(os.environ.get('SystemRoot') or os.environ.get('WINDIR') or r'C:\Windows')
    powershell=windows_root/'System32/WindowsPowerShell/v1.0/powershell.exe'
    if not powershell.is_file():
        raise ValueError('Windows PowerShell is required to preserve private migration credential ACLs.')
    module_path=windows_root/'System32/WindowsPowerShell/v1.0/Modules'
    environment=os.environ.copy()
    environment['PSModulePath']=str(module_path)
    environment['COPIMINE_MIGRATION_ACL_SOURCE']=str(Path(source).resolve())
    environment['COPIMINE_MIGRATION_ACL_TARGET']=str(Path(destination).resolve())
    script=(
        "$ErrorActionPreference='Stop'; "
        "$sourceAcl=Get-Acl -LiteralPath $env:COPIMINE_MIGRATION_ACL_SOURCE; "
        "Set-Acl -LiteralPath $env:COPIMINE_MIGRATION_ACL_TARGET -AclObject $sourceAcl; "
        "$targetAcl=Get-Acl -LiteralPath $env:COPIMINE_MIGRATION_ACL_TARGET; "
        "$sourceRules=@($sourceAcl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | "
        "ForEach-Object { '{0}|{1}|{2}|{3}|{4}|{5}' -f $_.IdentityReference.Value,[int]$_.FileSystemRights,"
        "$_.AccessControlType,$_.IsInherited,$_.InheritanceFlags,$_.PropagationFlags } | Sort-Object); "
        "$targetRules=@($targetAcl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]) | "
        "ForEach-Object { '{0}|{1}|{2}|{3}|{4}|{5}' -f $_.IdentityReference.Value,[int]$_.FileSystemRights,"
        "$_.AccessControlType,$_.IsInherited,$_.InheritanceFlags,$_.PropagationFlags } | Sort-Object); "
        "$rulesMatch=@(Compare-Object -ReferenceObject $sourceRules -DifferenceObject $targetRules).Count -eq 0; "
        "if ($sourceAcl.Owner -cne $targetAcl.Owner -or $sourceAcl.Group -cne $targetAcl.Group -or "
        "$sourceAcl.AreAccessRulesProtected -ne $targetAcl.AreAccessRulesProtected -or -not $rulesMatch) "
        "{ throw 'Migration credential ACL verification failed.' }"
    )
    try:
        result=subprocess.run(
            [str(powershell),'-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-Command',script],
            check=False,capture_output=True,text=True,timeout=30,env=environment)
    except (OSError,subprocess.TimeoutExpired) as error:
        raise ValueError('Could not preserve the protected ACL on a migration credential file.') from error
    if result.returncode!=0:
        raise ValueError('Could not preserve the protected ACL on a migration credential file.')


def _copy_private_runtime_file(source,destination):
    source=Path(source);destination=Path(destination)
    if os.name!='nt':
        shutil.copy2(source,destination)
        return
    try:
        # Create an empty file, apply the source DACL, then write secret bytes.
        # The credential content is never placed in a destination with inherited
        # default permissions that may be broader than the protected source ACL.
        with destination.open('xb'):
            pass
        _copy_windows_acl(source,destination)
        with source.open('rb') as input_file,destination.open('wb') as output_file:
            shutil.copyfileobj(input_file,output_file,64*1024)
        shutil.copystat(source,destination)
    except Exception:
        try:destination.unlink(missing_ok=True)
        except OSError:pass
        raise


def _copy_runtime_file(source,destination):
    if Path(source).name in _PRIVATE_RUNTIME_FILES:
        _copy_private_runtime_file(source,destination)
    else:
        shutil.copy2(source,destination)


def _copy_runtime_state(source,destination):
    source=Path(source);destination=Path(destination)
    skipped_root_files={'copimine-migration-runtime.json','migration-plugin-receipt.json'}
    for item in source.iterdir():
        if item.name in skipped_root_files or re.fullmatch(r'paper-26\.3-\d+\.jar',item.name):
            continue
        target=destination/item.name
        if item.name=='plugins' and item.is_dir():
            target.mkdir()
            for plugin_item in item.iterdir():
                if plugin_item.is_file() and plugin_item.suffix.casefold()=='.jar':
                    continue
                if plugin_item.is_dir():shutil.copytree(plugin_item,target/plugin_item.name,copy_function=_copy_runtime_file)
                else:_copy_runtime_file(plugin_item,target/plugin_item.name)
        elif item.is_dir():
            shutil.copytree(item,target,copy_function=_copy_runtime_file)
        else:_copy_runtime_file(item,target)


def _migration_postgres_runtime_state(runtime):
    runtime=Path(runtime)
    ports={55434}
    process_id=None
    environment_path=runtime/'migration-isolated-postgres.env'
    if _path_exists(environment_path):
        _ensure_regular_file(environment_path,'isolated PostgreSQL environment file')
        if environment_path.stat().st_size>65_536:
            raise ValueError('Isolated PostgreSQL environment file exceeds the safe size limit')
        try:lines=environment_path.read_text(encoding='utf-8').splitlines()
        except (OSError,UnicodeError) as error:
            raise ValueError('Isolated PostgreSQL environment file is invalid') from error
        settings={}
        for raw_line in lines:
            line=raw_line.strip()
            if not line or line.startswith('#'):continue
            separator=line.find('=')
            if separator<=0:
                raise ValueError('Isolated PostgreSQL environment file contains a malformed setting')
            name=line[:separator].strip();value=line[separator+1:].strip()
            if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',name):
                raise ValueError('Isolated PostgreSQL environment file contains an invalid key')
            if name in settings:raise ValueError('Isolated PostgreSQL environment file contains a duplicate key')
            settings[name]=value
        host=settings.get('POSTGRES_HOST')
        raw_port=settings.get('POSTGRES_PORT')
        if host!='127.0.0.1' or not isinstance(raw_port,str) or not re.fullmatch(r'[0-9]{1,5}',raw_port):
            raise ValueError('Isolated PostgreSQL environment must specify its loopback host and port')
        port=int(raw_port)
        if not 1024<=port<=65535 or port==55433:
            raise ValueError('Isolated PostgreSQL environment specifies an invalid port')
        ports.add(port)

    postmaster=runtime/'postgres-data'/'postmaster.pid'
    if _path_exists(postmaster):
        _ensure_regular_file(postmaster,'isolated PostgreSQL process record')
        if postmaster.stat().st_size>16_384:
            raise ValueError('Isolated PostgreSQL process record exceeds the safe size limit')
        try:lines=postmaster.read_text(encoding='utf-8').splitlines()
        except (OSError,UnicodeError) as error:
            raise ValueError('Isolated PostgreSQL process record is invalid') from error
        if (len(lines)<4 or not re.fullmatch(r'[1-9][0-9]*',lines[0].strip()) or
            len(lines[0].strip())>10):
            raise ValueError('Isolated PostgreSQL process record is incomplete')
        process_id=int(lines[0].strip())
        if process_id>0xffffffff:
            raise ValueError('Isolated PostgreSQL process record has an invalid process ID')
        recorded_data=os.path.normcase(os.path.abspath(lines[1].strip()))
        expected_data=os.path.normcase(os.path.abspath(runtime/'postgres-data'))
        if not lines[1].strip() or recorded_data!=expected_data:
            raise ValueError('Isolated PostgreSQL process record points at a different data directory')
        raw_port=lines[3].strip()
        if not re.fullmatch(r'[0-9]{1,5}',raw_port):
            raise ValueError('Isolated PostgreSQL process record has an invalid port')
        port=int(raw_port)
        if not 1024<=port<=65535 or port==55433:
            raise ValueError('Isolated PostgreSQL process record has an invalid port')
        ports.add(port)
    return sorted(ports),process_id


def _process_id_is_running(process_id):
    if not isinstance(process_id,int) or isinstance(process_id,bool) or not 1<=process_id<=0xffffffff:
        raise ValueError('Process ID must be a positive 32-bit integer')
    if os.name=='nt':
        import ctypes
        from ctypes import wintypes

        kernel32=ctypes.WinDLL('kernel32',use_last_error=True)
        open_process=kernel32.OpenProcess
        open_process.argtypes=(wintypes.DWORD,wintypes.BOOL,wintypes.DWORD)
        open_process.restype=wintypes.HANDLE
        handle=open_process(0x1000,False,process_id)  # PROCESS_QUERY_LIMITED_INFORMATION
        if not handle:
            error=ctypes.get_last_error()
            if error==87:return False  # ERROR_INVALID_PARAMETER: no process with this ID
            if error==5:return True  # ERROR_ACCESS_DENIED: fail closed
            raise ctypes.WinError(error)
        try:
            get_exit_code=kernel32.GetExitCodeProcess
            get_exit_code.argtypes=(wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD))
            get_exit_code.restype=wintypes.BOOL
            exit_code=wintypes.DWORD()
            if not get_exit_code(handle,ctypes.byref(exit_code)):
                raise ctypes.WinError(ctypes.get_last_error())
            return exit_code.value==259  # STILL_ACTIVE
        finally:
            kernel32.CloseHandle(handle)

    import errno
    try:
        os.kill(process_id,0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    except OSError as error:
        if error.errno==errno.ESRCH:return False
        if error.errno==errno.EPERM:return True
        raise


def _cleanup_orphaned_upgrade_scratch(parent):
    parent=Path(parent)
    parent_info=parent.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(parent_info.st_mode) or
        (reparse_flag and getattr(parent_info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(parent_info.st_mode)):
        raise ValueError('Symlink or non-directory refused: isolated runtime directory')
    pattern=re.compile(r'\.end-rift-server-26\.3-upgrade-[A-Za-z0-9_-]{8}')
    prefix='.end-rift-server-26.3-upgrade-'
    for scratch in parent.iterdir():
        if not scratch.name.startswith(prefix):continue
        if not pattern.fullmatch(scratch.name):
            raise ValueError('Unrecognized isolated runtime upgrade scratch directory name')
        if scratch.parent!=parent:
            raise ValueError('Runtime upgrade scratch path escaped the isolated runtime directory')
        info=scratch.lstat()
        if (stat.S_ISLNK(info.st_mode) or
            (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag) or
            not stat.S_ISDIR(info.st_mode)):
            raise ValueError('Symlink or non-directory refused: runtime upgrade scratch directory')
        _assert_plain_runtime_tree(scratch)
        shutil.rmtree(scratch)


def _upgrade_owned_runtime(runtime,parent,paper,candidate):
    runtime=Path(runtime);parent=Path(parent)
    _assert_plain_runtime_tree(runtime)
    marker=runtime/'copimine-migration-runtime.json'
    _ensure_regular_file(marker,'migration runtime marker')
    try:metadata=json.loads(marker.read_text(encoding='utf-8'))
    except (OSError,UnicodeError,json.JSONDecodeError) as error:
        raise ValueError('Migration runtime marker is invalid') from error
    old_filename=metadata.get('paperFilename')
    old_match=re.fullmatch(r'paper-26\.3-(\d+)\.jar',old_filename or '')
    old_sha=metadata.get('paperSha256')
    old_size=metadata.get('paperSize')
    if (metadata.get('schemaVersion')!=1 or metadata.get('minecraftVersion')!='26.3' or
        metadata.get('eulaAcceptedAtSetup') is not False or not old_match or
        not isinstance(old_sha,str) or not re.fullmatch(r'[0-9a-f]{64}',old_sha) or
        not isinstance(old_size,int) or isinstance(old_size,bool) or not 0<old_size<=500_000_000):
        raise ValueError('Existing migration runtime is not owned by a verifiable Paper 26.3 candidate')
    old_build=int(old_match.group(1))
    if old_build>=paper['build']:
        raise ValueError('Existing migration runtime is not an older Paper candidate')
    old_jar=runtime/old_filename
    _ensure_regular_file(old_jar,'previous Paper server JAR')
    if old_jar.stat().st_size!=old_size or digest(old_jar,'sha256')!=old_sha:
        raise ValueError('Previous Paper server JAR differs from its runtime marker')

    properties_path=runtime/'server.properties'
    _ensure_regular_file(properties_path,'prepared server.properties')
    if properties_path.stat().st_size>1_000_000:
        raise ValueError('Runtime server.properties exceeds the safe size limit')
    properties_text=properties_path.read_text(encoding='utf-8')
    properties=_java_property_entries(properties_text)
    _validate_local_runtime_properties(properties)
    settings=runtime_startup_settings(runtime)
    rcon_values=properties.get('rcon.port',[])
    if len(rcon_values)!=1 or not re.fullmatch(r'[0-9]{1,5}',rcon_values[0].strip()):
        raise ValueError('Runtime rcon.port must be a valid TCP port')
    rcon_port=int(rcon_values[0].strip())
    if not 1<=rcon_port<=65535:
        raise ValueError('Runtime rcon.port must be a valid TCP port')
    if port_open(settings['serverPort'],'127.0.0.1') or port_open(rcon_port,'127.0.0.1'):
        raise ValueError('Server is running; stop it before upgrading the Paper runtime')
    postgres_ports,postgres_pid=_migration_postgres_runtime_state(runtime)
    if postgres_pid is not None and _process_id_is_running(postgres_pid):
        raise ValueError(
            f'Isolated migration PostgreSQL may still be running: PID {postgres_pid} from postmaster.pid is active. '
            'Stop the database before upgrading. If the PID is stale or was reused, first verify that this runtime '
            'has no running PostgreSQL process; only then move the stale postmaster.pid aside and retry.'
        )
    for postgres_port in postgres_ports:
        if any(port_open(postgres_port,host) for host in ('127.0.0.1','::1')):
            raise ValueError('Isolated migration PostgreSQL is running; stop it before upgrading the Paper runtime')
    eula=runtime/'eula.txt'
    _ensure_regular_file(eula,'prepared EULA file')
    if eula.read_text(encoding='ascii').strip().lower() not in {'eula=false','eula=true'}:
        raise ValueError('Prepared migration runtime EULA file must be eula=false or eula=true')
    _validate_receipt_destination(runtime)

    backup_root=parent/'migration-backups'
    if not _path_exists(backup_root):backup_root.mkdir()
    backup_info=backup_root.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(backup_info.st_mode) or
        (reparse_flag and getattr(backup_info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(backup_info.st_mode)):
        raise ValueError('Symlink or non-directory refused: migration runtime backup directory')
    backup=Path(tempfile.mkdtemp(prefix=f'end-rift-server-26.3-build{old_build}-',dir=backup_root))
    backup_runtime=backup/'runtime'
    transaction_path=backup/'upgrade-in-progress.json'
    temporary=Path(tempfile.mkdtemp(prefix='.end-rift-server-26.3-upgrade-',dir=parent))
    moved=False
    try:
        _copy_runtime_state(runtime,temporary)
        shutil.copy2(candidate,temporary/paper['filename'])
        new_metadata={
            'schemaVersion':1,
            'minecraftVersion':'26.3',
            'paperFilename':paper['filename'],
            'paperSha256':paper['sha256'],
            'paperSize':paper['size'],
            'eulaAcceptedAtSetup':False,
        }
        marker_path=temporary/'copimine-migration-runtime.json'
        marker_path.write_text(json.dumps(new_metadata,indent=2)+'\n',encoding='utf-8',newline='\n')
        _verify_prepared_runtime(temporary,paper)
        transaction={
            'schemaVersion':1,
            'targetPaperFilename':paper['filename'],
            'targetPaperSha256':paper['sha256'],
            'targetPaperSize':paper['size'],
            'previousPaperFilename':old_filename,
            'previousPaperSha256':old_sha,
            'previousPaperSize':old_size,
        }
        with transaction_path.open('x',encoding='utf-8',newline='\n') as output:
            output.write(json.dumps(transaction,indent=2)+'\n')
            output.flush()
            os.fsync(output.fileno())
        os.rename(runtime,backup_runtime)
        moved=True
        try:
            os.rename(temporary,runtime)
        except Exception:
            os.rename(backup_runtime,runtime)
            moved=False
            raise
        try:transaction_path.unlink()
        except OSError:pass
    except Exception:
        if _path_exists(temporary):shutil.rmtree(temporary)
        if moved and _path_exists(backup_runtime) and not _path_exists(runtime):os.rename(backup_runtime,runtime)
        if _path_exists(backup) and not _path_exists(backup_runtime):
            transaction_path.unlink(missing_ok=True)
            if not any(backup.iterdir()):backup.rmdir()
        raise
    return runtime,backup_runtime


def _runtime_paper_from_marker(runtime):
    marker=Path(runtime)/'copimine-migration-runtime.json'
    _ensure_regular_file(marker,'migration runtime marker')
    try:metadata=json.loads(marker.read_text(encoding='utf-8'))
    except (OSError,UnicodeError,json.JSONDecodeError) as error:
        raise ValueError('Migration runtime marker is invalid during recovery') from error
    filename=metadata.get('paperFilename')
    match=re.fullmatch(r'paper-26\.3-(\d+)\.jar',filename or '')
    sha256=metadata.get('paperSha256');size=metadata.get('paperSize')
    if (metadata.get('schemaVersion')!=1 or metadata.get('minecraftVersion')!='26.3' or
        metadata.get('eulaAcceptedAtSetup') is not False or not match or
        not isinstance(sha256,str) or not re.fullmatch(r'[0-9a-f]{64}',sha256) or
        not isinstance(size,int) or isinstance(size,bool) or not 0<size<=500_000_000):
        raise ValueError('Migration runtime marker is not recoverable')
    return {'build':int(match.group(1)),'filename':filename,'sha256':sha256,'size':size}


def _recover_interrupted_runtime(parent,runtime,paper):
    if _path_exists(runtime):return False
    backup_root=Path(parent)/'migration-backups'
    if not _path_exists(backup_root):return False
    info=backup_root.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    if (stat.S_ISLNK(info.st_mode) or
        (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(info.st_mode)):
        raise ValueError('Symlink or non-directory refused: migration runtime backup directory')

    recoverable=[];archived=[];orphaned_transactions=[]
    for backup in sorted(backup_root.iterdir(),key=lambda item:item.name):
        backup_info=backup.lstat()
        if (stat.S_ISLNK(backup_info.st_mode) or
            (reparse_flag and getattr(backup_info,'st_file_attributes',0)&reparse_flag)):
            raise ValueError('Symlink or reparse point refused: migration runtime backup entry')
        if not stat.S_ISDIR(backup_info.st_mode):
            raise ValueError('Non-directory entry refused in migration runtime backups')
        snapshot=backup/'runtime'
        transaction_path=backup/'upgrade-in-progress.json'
        if _path_exists(transaction_path):
            _ensure_regular_file(transaction_path,'runtime upgrade recovery record')
            if transaction_path.stat().st_size>16_384:
                raise ValueError('Runtime upgrade recovery record exceeds the safe size limit')
            try:transaction=json.loads(transaction_path.read_text(encoding='utf-8'))
            except (OSError,UnicodeError,json.JSONDecodeError) as error:
                raise ValueError('Runtime upgrade recovery record is invalid') from error
            if (not isinstance(transaction,dict) or transaction.get('schemaVersion')!=1 or
                transaction.get('targetPaperFilename')!=paper['filename'] or
                transaction.get('targetPaperSha256')!=paper['sha256'] or
                transaction.get('targetPaperSize')!=paper['size']):
                orphaned_transactions.append(str(transaction_path))
                continue
            if not _path_exists(snapshot):
                orphaned_transactions.append(str(transaction_path))
                continue
            _assert_plain_runtime_tree(snapshot)
            previous=_runtime_paper_from_marker(snapshot)
            if (previous['build']>=paper['build'] or
                transaction.get('previousPaperFilename')!=previous['filename'] or
                transaction.get('previousPaperSha256')!=previous['sha256'] or
                transaction.get('previousPaperSize')!=previous['size']):
                raise ValueError('Interrupted runtime snapshot does not match its recovery record')
            _verify_prepared_runtime(snapshot,previous)
            recoverable.append((backup,snapshot,transaction_path))
        elif _path_exists(snapshot):
            archived.append(str(snapshot))

    if len(recoverable)>1:
        raise ValueError('Multiple interrupted runtime snapshots are recoverable; refusing ambiguous restore')
    if recoverable:
        backup,snapshot,transaction_path=recoverable[0]
        os.rename(snapshot,runtime)
        transaction_path.unlink()
        if not any(backup.iterdir()):backup.rmdir()
        return True
    if orphaned_transactions:
        raise ValueError('Migration runtime is missing and an incomplete upgrade record needs recovery review: '+
                         ', '.join(orphaned_transactions))
    if archived:
        raise ValueError('Migration runtime is missing but preserved runtime snapshots exist; refusing to create a blank world. '
                         'Recover a snapshot manually: '+', '.join(archived))
    return False


def _clear_resolved_upgrade_records(parent,runtime,paper):
    if not _path_exists(runtime):return
    current=_runtime_paper_from_marker(runtime)
    backup_root=Path(parent)/'migration-backups'
    if not _path_exists(backup_root):return
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    root_info=backup_root.lstat()
    if (stat.S_ISLNK(root_info.st_mode) or
        (reparse_flag and getattr(root_info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(root_info.st_mode)):
        raise ValueError('Symlink or non-directory refused: migration runtime backup directory')
    for backup in backup_root.iterdir():
        backup_info=backup.lstat()
        if (stat.S_ISLNK(backup_info.st_mode) or
            (reparse_flag and getattr(backup_info,'st_file_attributes',0)&reparse_flag) or
            not stat.S_ISDIR(backup_info.st_mode)):
            raise ValueError('Symlink or non-directory refused: migration runtime backup entry')
        transaction_path=backup/'upgrade-in-progress.json'
        if not _path_exists(transaction_path):continue
        _ensure_regular_file(transaction_path,'runtime upgrade recovery record')
        if transaction_path.stat().st_size>16_384:
            raise ValueError('Runtime upgrade recovery record exceeds the safe size limit')
        try:transaction=json.loads(transaction_path.read_text(encoding='utf-8'))
        except (OSError,UnicodeError,json.JSONDecodeError):continue
        targets_current=(transaction.get('targetPaperFilename')==current['filename'] and
                         transaction.get('targetPaperSha256')==current['sha256'] and
                         transaction.get('targetPaperSize')==current['size'])
        previous_is_current=(transaction.get('previousPaperFilename')==current['filename'] and
                             transaction.get('previousPaperSha256')==current['sha256'] and
                             transaction.get('previousPaperSize')==current['size'])
        if targets_current and current['filename']==paper['filename']:
            transaction_path.unlink()
        elif previous_is_current and not _path_exists(backup/'runtime'):
            transaction_path.unlink()
        if not _path_exists(backup/'runtime') and not any(backup.iterdir()):backup.rmdir()


def prepare_runtime(root=None):
    root=Path(root).resolve() if root is not None else Path(__file__).resolve().parents[2]
    paper=_locked_paper(root)
    filename=paper['filename']
    candidate=root/'build/minecraft-26.3/server'/filename
    _ensure_build_directory(root,candidate.parent,create=False)
    _ensure_regular_file(candidate,'staged Paper server candidate')
    if candidate.stat().st_size!=paper['size'] or digest(candidate,'sha256')!=paper['sha256']:
        raise ValueError('Staged Paper server candidate differs from its lock')

    parent=_ensure_runtime_parent(root,create=True)
    runtime=parent/'end-rift-server-26.3'
    with _runtime_lifecycle_lock(runtime):
        _cleanup_orphaned_upgrade_scratch(parent)
        if not _path_exists(runtime):
            _recover_interrupted_runtime(parent,runtime,paper)
        if _path_exists(runtime):
            info=runtime.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
            if (stat.S_ISLNK(info.st_mode) or
                (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag)):
                raise ValueError('Symlink or reparse point refused: isolated migration runtime')
            if not stat.S_ISDIR(info.st_mode):raise ValueError('Isolated migration runtime path is not a directory')
            if not _path_exists(runtime/'copimine-migration-runtime.json'):
                raise ValueError('Refusing to modify an unowned isolated migration runtime')
            try:
                prepared=_verify_prepared_runtime(runtime,paper)
                _clear_resolved_upgrade_records(parent,runtime,paper)
                return prepared
            except ValueError as current_error:
                if 'not owned by this pinned candidate' not in str(current_error):
                    raise
                _clear_resolved_upgrade_records(parent,runtime,paper)
                upgraded,backup=_upgrade_owned_runtime(runtime,parent,paper,candidate)
                print('Preserved previous Paper runtime at: '+str(backup))
                print('Archived plugin JARs were not carried into the active runtime; original JARs remain at: '+
                      str(backup/'plugins'))
                print('Install and verify the locked plugin candidates before starting Paper.')
                return _verify_prepared_runtime(upgraded,paper)

        temporary=Path(tempfile.mkdtemp(prefix='.end-rift-server-26.3.candidate-',dir=parent))
        try:
            (temporary/'plugins').mkdir()
            with (temporary/filename).open('xb') as output,candidate.open('rb') as source:
                shutil.copyfileobj(source,output,64*1024)
            properties=(
                'motd=CopiMine 26.3 migration test\n'
                'server-ip=127.0.0.1\n'
                'server-port=25566\n'
                'online-mode=false\n'
                'white-list=false\n'
                'max-players=1\n'
                'level-name=world\n'
                'view-distance=4\n'
                'simulation-distance=4\n'
                'spawn-protection=0\n'
                'enable-rcon=false\n'
                'rcon.port=25576\n'
            )
            (temporary/'server.properties').write_text(properties,encoding='utf-8',newline='\n')
            (temporary/'eula.txt').write_text('eula=false\n',encoding='ascii',newline='\n')
            metadata={
                'schemaVersion':1,
                'minecraftVersion':'26.3',
                'paperFilename':filename,
                'paperSha256':paper['sha256'],
                'paperSize':paper['size'],
                'eulaAcceptedAtSetup':False,
            }
            (temporary/'copimine-migration-runtime.json').write_text(
                json.dumps(metadata,indent=2)+'\n',encoding='utf-8',newline='\n')
            _verify_prepared_runtime(temporary,paper)
            if _path_exists(runtime):raise ValueError('Isolated migration runtime appeared during preparation')
            os.rename(temporary,runtime)
        except Exception:
            if _path_exists(temporary):
                info=temporary.lstat();reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
                if stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode) and not (
                    reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag
                ):
                    shutil.rmtree(temporary)
            raise
        return _verify_prepared_runtime(runtime,paper)


def _validate_receipt_destination(server):
    receipt=Path(server)/'migration-plugin-receipt.json'
    if _path_exists(receipt):_ensure_regular_file(receipt,'migration plugin receipt')
    return receipt


def write_receipt(server,receipt):
    destination=_validate_receipt_destination(server)
    descriptor,temporary_name=tempfile.mkstemp(
        prefix=destination.name+'.',suffix='.candidate',dir=destination.parent)
    temporary=Path(temporary_name)
    try:
        with os.fdopen(descriptor,'w',encoding='utf-8',newline='\n') as output:
            json.dump(receipt,output,indent=2)
            output.write('\n')
        _ensure_regular_file(temporary,'temporary migration receipt')
        # os.replace replaces the directory entry itself, so a link planted
        # after preflight is never followed when publishing the receipt.
        os.replace(temporary,destination)
        _ensure_regular_file(destination,'migration plugin receipt')
    except Exception:
        if _path_exists(temporary):temporary.unlink()
        raise


def verify_staged(record,path):
    validate_record(record)
    if path.stat().st_size!=record['size']:raise ValueError('Plugin size mismatch: '+record['filename'])
    if digest(path,'sha512')!=record['sha512']:raise ValueError('Plugin digest mismatch: '+record['filename'])
    if record.get('sha256') and digest(path,'sha256')!=record['sha256']:
        raise ValueError('Plugin SHA-256 mismatch: '+record['filename'])
    if identity(path)!=record['pluginName']:raise ValueError('Plugin identity mismatch: '+record['filename'])


def _java_unescape(value):
    result=[];index=0
    escapes={'t':'\t','n':'\n','r':'\r','f':'\f'}
    while index<len(value):
        char=value[index];index+=1
        if char!='\\':
            result.append(char);continue
        if index>=len(value):continue
        escaped=value[index];index+=1
        if escaped=='u':
            while index<len(value) and value[index]=='u':index+=1
            digits=value[index:index+4]
            if len(digits)!=4 or not re.fullmatch(r'[0-9A-Fa-f]{4}',digits):
                raise ValueError('Malformed Unicode escape in server.properties')
            result.append(chr(int(digits,16)));index+=4
        else:result.append(escapes.get(escaped,escaped))
    return ''.join(result)


def _java_property_entries(contents):
    # Java Properties treats CR, LF and CRLF as line endings, but form-feed
    # is whitespace inside a property. Python str.splitlines() would split on
    # form-feed and can hide the effective server-ip from this stop guard.
    physical=re.split(r'\r\n|\r|\n',contents);logical=[];buffer='';continued=False
    for line in physical:
        if not continued:
            stripped=line.lstrip(' \t\f')
            if stripped and stripped[0] in '#!':
                # Java Properties ignores comment lines before continuation
                # handling, even if a comment ends with a backslash.
                logical.append(line)
                continue
        if continued:line=line.lstrip(' \t\f')
        else:buffer=''
        buffer+=line
        trailing=len(buffer)-len(buffer.rstrip('\\'))
        if trailing%2:
            buffer=buffer[:-1];continued=True;continue
        logical.append(buffer);buffer='';continued=False
    if continued:logical.append(buffer)
    entries={}
    for line in logical:
        stripped=line.lstrip(' \t\f')
        if not stripped or stripped[0] in '#!':continue
        escaped=False;separator=len(stripped)
        for index,char in enumerate(stripped):
            if escaped:escaped=False;continue
            if char=='\\':escaped=True;continue
            if char in '=: \t\f':separator=index;break
        key=_java_unescape(stripped[:separator])
        index=separator
        if index<len(stripped):
            if stripped[index] in ' \t\f':
                while index<len(stripped) and stripped[index] in ' \t\f':index+=1
            if index<len(stripped) and stripped[index] in '=:':index+=1
            while index<len(stripped) and stripped[index] in ' \t\f':index+=1
        value=_java_unescape(stripped[index:])
        entries.setdefault(key,[]).append(value)
    return entries


def _validate_local_runtime_properties(properties):
    server_ips=properties.get('server-ip',[])
    if len(server_ips)>1:raise ValueError('Duplicate runtime server-ip')
    if len(server_ips)!=1 or server_ips[0].strip()!='127.0.0.1':
        raise ValueError('Migration runtime must bind to the 127.0.0.1 loopback address')
    online_modes=properties.get('online-mode',[])
    if len(online_modes)>1:raise ValueError('Duplicate runtime online-mode')
    if len(online_modes)!=1 or online_modes[0].strip().casefold()!='false':
        raise ValueError('Migration test runtime must use online-mode=false for licensed and offline client compatibility')
    rcon_values=properties.get('enable-rcon',[])
    if len(rcon_values)>1:raise ValueError('Duplicate runtime enable-rcon')
    if len(rcon_values)!=1 or rcon_values[0].strip().casefold()!='false':
        raise ValueError('Migration runtime requires enable-rcon=false')


def runtime_startup_settings(runtime):
    runtime=Path(runtime)
    properties_path=runtime/'server.properties'
    _ensure_regular_file(properties_path,'runtime server.properties')
    if properties_path.stat().st_size>1_000_000:
        raise ValueError('Runtime server.properties exceeds the safe size limit')
    properties=_java_property_entries(properties_path.read_text(encoding='utf-8'))
    _validate_local_runtime_properties(properties)
    ports=properties.get('server-port',[])
    if len(ports)>1:raise ValueError('Duplicate runtime server-port')
    if len(ports)!=1 or not re.fullmatch(r'[0-9]{1,5}',ports[0].strip()):
        raise ValueError('Runtime server-port must be a valid TCP port')
    port=int(ports[0].strip())
    if not 1<=port<=65535:raise ValueError('Runtime server-port must be a valid TCP port')
    return {'serverIp':'127.0.0.1','serverPort':port,'onlineMode':'false','enableRcon':'false'}


def configure_loopback_voicechat(runtime):
    runtime=Path(runtime)
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    for directory,label in ((runtime,'isolated runtime'),(runtime/'plugins','plugin directory')):
        info=directory.lstat()
        if (stat.S_ISLNK(info.st_mode) or
            (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag) or
            not stat.S_ISDIR(info.st_mode)):
            raise ValueError('Voice chat configuration requires a plain '+label)

    config_directory=runtime/'plugins/voicechat'
    if not _path_exists(config_directory):config_directory.mkdir()
    config_info=config_directory.lstat()
    if (stat.S_ISLNK(config_info.st_mode) or
        (reparse_flag and getattr(config_info,'st_file_attributes',0)&reparse_flag) or
        not stat.S_ISDIR(config_info.st_mode)):
        raise ValueError('Voice chat configuration directory is not a plain directory')

    config_path=config_directory/'voicechat-server.properties'
    if _path_exists(config_path):
        _ensure_regular_file(config_path,'local Voice Chat server configuration')
        if config_path.stat().st_size>1_000_000:
            raise ValueError('Local Voice Chat server configuration exceeds the safe size limit')
        contents=config_path.read_text(encoding='utf-8')
    else:
        contents=''
    properties=_java_property_entries(contents)
    if len(properties.get('bind_address',[]))>1:
        raise ValueError('Duplicate voice chat bind_address setting')

    had_final_newline=contents.endswith(('\n','\r'))
    lines=re.split(r'\r\n|\r|\n',contents) if contents else []
    if had_final_newline and lines and lines[-1]=='':lines.pop()
    binding_lines=[]
    for index,line in enumerate(lines):
        stripped=line.lstrip(' \t\f')
        if stripped and stripped[0] not in '#!' and re.match(
            r'^bind_address(?:[ \t\f]*[=:]|[ \t\f]+|$)',stripped
        ):
            binding_lines.append(index)
    if len(binding_lines)>1:
        raise ValueError('Duplicate voice chat bind_address setting')

    if binding_lines:
        index=binding_lines[0]
        leading=lines[index][:len(lines[index])-len(lines[index].lstrip(' \t\f'))]
        if properties.get('bind_address')==['127.0.0.1']:
            return {'voiceChatBindAddress':'127.0.0.1'}
        lines[index]=leading+'bind_address=127.0.0.1'
    else:
        lines.append('bind_address=127.0.0.1')
        had_final_newline=True

    updated='\n'.join(lines)+('\n' if had_final_newline else '')
    descriptor,temporary_name=tempfile.mkstemp(
        prefix=config_path.name+'.',suffix='.candidate',dir=config_directory)
    temporary=Path(temporary_name)
    try:
        with os.fdopen(descriptor,'w',encoding='utf-8',newline='\n') as output:
            output.write(updated)
            output.flush()
            os.fsync(output.fileno())
        _ensure_regular_file(temporary,'temporary local Voice Chat configuration')
        os.replace(temporary,config_path)
        _ensure_regular_file(config_path,'published local Voice Chat configuration')
    finally:
        temporary.unlink(missing_ok=True)
    return {'voiceChatBindAddress':'127.0.0.1'}


def download_candidate(record, destination):
    request=urllib.request.Request(record['url'],headers={'User-Agent':'CopiMine26Migration/1.0'})
    descriptor,temporary_name=tempfile.mkstemp(prefix=destination.stem+'.',suffix='.candidate',dir=destination.parent)
    temporary=Path(temporary_name)
    try:
        total=0
        with os.fdopen(descriptor,'wb') as output, urllib.request.urlopen(request,timeout=60) as response:
            while True:
                chunk=response.read(min(64*1024,record['size']+1-total))
                if not chunk:break
                total+=len(chunk)
                if total>record['size']:
                    raise ValueError('Downloaded plugin exceeds pinned size: '+record['filename'])
                output.write(chunk)
        verify_staged(record,temporary)
        os.replace(temporary,destination)
    finally:
        temporary.unlink(missing_ok=True)


def _local_artifact_path(root, relative):
    if not isinstance(relative,str) or not relative or '\\' in relative:
        raise ValueError('Invalid local plugin artifact path')
    relative_path=Path(relative)
    if relative_path.is_absolute() or any(part in ('','..','.') for part in relative_path.parts):
        raise ValueError('Unsafe local plugin artifact path')
    root=Path(root).resolve()
    artifact=root.joinpath(*relative_path.parts)
    current=root
    reparse_flag=getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',0)
    for part in relative_path.parts:
        current=current/part
        if _path_exists(current):
            info=current.lstat()
            if (stat.S_ISLNK(info.st_mode) or
                (reparse_flag and getattr(info,'st_file_attributes',0)&reparse_flag)):
                raise ValueError('Symlink or reparse point local plugin artifact refused')
    resolved=artifact.resolve(strict=True)
    if not resolved.is_relative_to(root) or not resolved.is_file():
        raise ValueError('Local plugin artifact is outside the repository or not a file')
    return resolved


def load_install_records(root, lock=None):
    """Merge pinned downloads with locally built first-party candidates."""
    root=Path(root).resolve()
    if lock is None:
        lock=json.loads((root/'tools/minecraft-26.3/server-plugins.lock.json').read_text(encoding='utf-8'))
    records=[]
    for original in lock['modules']:
        record={**original,'runtimeFilename':original.get('runtimeFilename') or original['filename'],'sourceType':'remote'}
        if original.get('buildScript'):
            relative_artifact=original.get('buildArtifact')
            relative_receipt=original.get('buildReceipt')
            build_sha256=original.get('buildSha256','')
            build_sha512=original.get('buildSha512','')
            build_size=original.get('buildSize')
            if (not re.fullmatch(r'[0-9a-f]{64}',build_sha256) or
                not re.fullmatch(r'[0-9a-f]{128}',build_sha512) or
                not isinstance(build_size,int) or not 0<build_size<100_000_000):
                raise ValueError('Invalid locked local build artifact for '+original['pluginName'])
            artifact=_local_artifact_path(root,relative_artifact)
            if artifact.name!=original['filename'] or artifact.stat().st_size!=build_size:
                raise ValueError('Local build artifact size or filename differs from its lock: '+original['pluginName'])
            if digest(artifact,'sha256')!=build_sha256 or digest(artifact,'sha512')!=build_sha512:
                raise ValueError('Local build artifact digest differs from its lock: '+original['pluginName'])
            if identity(artifact)!=original['pluginName']:
                raise ValueError('Local build artifact identity differs from its lock: '+original['pluginName'])
            receipt_path=_local_artifact_path(root,relative_receipt)
            _ensure_regular_file(receipt_path,'local plugin build receipt')
            if receipt_path.stat().st_size>1_000_000:
                raise ValueError('Local plugin build receipt exceeds the safe size limit')
            try:
                receipt=json.loads(receipt_path.read_text(encoding='utf-8-sig'))
            except (OSError,UnicodeError,ValueError) as error:
                raise ValueError('Invalid local plugin build receipt: '+original['pluginName']) from error
            if (receipt.get('sourceCommit')!=original.get('sourceCommit') or
                receipt.get('source')!=original.get('source') or
                receipt.get('sha512')!=build_sha512 or receipt.get('size')!=build_size or
                receipt.get('nativeVerified') is not False):
                raise ValueError('Local plugin build receipt differs from its lock: '+original['pluginName'])
            record.update({'sourceType':'local','artifactPath':str(artifact),
                           'sha256':build_sha256,'sha512':build_sha512,'size':build_size})
        records.append(record)
    module_names={record['pluginName'] for record in records}
    for baseline in lock.get('unchangedBaseline',[]):
        candidate=baseline.get('candidate') or {}
        plugin_name=baseline['pluginName']
        # A migration module candidate is authoritative when it replaces the
        # same plugin as an unchanged-baseline candidate.
        if plugin_name in module_names:
            continue
        old_sha256=baseline.get('sha256')
        if not re.fullmatch(r'[0-9a-f]{64}',old_sha256 or ''):
            raise ValueError('Invalid baseline plugin SHA-256: '+plugin_name)
        if candidate.get('buildArtifact'):
            artifact=_local_artifact_path(root,candidate['buildArtifact'])
            filename=candidate.get('filename')
            runtime_filename=candidate.get('runtimeFilename') or filename
            if artifact.name!=filename:
                raise ValueError('Local plugin artifact filename differs from its lock: '+str(filename))
            if digest(artifact,'sha256')!=candidate.get('sha256'):
                raise ValueError('Local plugin artifact SHA-256 differs from its lock: '+plugin_name)
            if identity(artifact)!=plugin_name:
                raise ValueError('Local plugin artifact identity differs from its lock: '+plugin_name)
            record={
                'pluginName':plugin_name,
                'version':baseline['version'],
                'filename':filename,
                'runtimeFilename':runtime_filename,
                'sourceType':'local',
                'artifactPath':str(artifact),
                'sha256':candidate['sha256'],
                'sha512':digest(artifact,'sha512'),
                'size':artifact.stat().st_size,
                'replaceSha256':list(dict.fromkeys([old_sha256,*baseline.get('replaceSha256',[])])),
            }
        elif candidate.get('url'):
            record={
                'pluginName':plugin_name,
                'version':candidate.get('version',baseline['version']),
                'filename':candidate.get('filename') or baseline['filename'],
                'runtimeFilename':candidate.get('runtimeFilename') or candidate.get('filename') or baseline['filename'],
                'sourceType':'remote',
                'url':candidate['url'],
                'sha256':candidate.get('sha256'),
                'sha512':candidate.get('sha512'),
                'size':candidate.get('size'),
                'replaceSha256':list(dict.fromkeys([old_sha256,*baseline.get('replaceSha256',[])])),
            }
        else:
            continue
        records.append(record)
    names=[record['pluginName'] for record in records]
    if len(names)!=len(set(names)):
        raise ValueError('Duplicate replacement plugin identity')
    for record in records:validate_record(record)
    return records


def _stage_local_candidate(record,destination):
    source=Path(record['artifactPath'])
    descriptor,temporary_name=tempfile.mkstemp(prefix=destination.stem+'.',suffix='.candidate',dir=destination.parent)
    temporary=Path(temporary_name)
    try:
        with os.fdopen(descriptor,'wb') as output,source.open('rb') as input_stream:
            shutil.copyfileobj(input_stream,output,64*1024)
        verify_staged(record,temporary)
        os.replace(temporary,destination)
    finally:
        temporary.unlink(missing_ok=True)


def stage_records(records,stage,root):
    stage=_ensure_build_directory(root,stage,create=True)
    for record in records:
        validate_record(record)
        destination=stage/record['filename']
        if _path_exists(destination):
            _ensure_regular_file(destination,'staged plugin candidate')
        try:
            verify_staged(record,destination)
            continue
        except (OSError,ValueError,KeyError,zipfile.BadZipFile):
            if record.get('patch') or record.get('buildScript'):
                raise ValueError('Rebuild the pinned local plugin artifact before staging: '+record['pluginName'])
        if record.get('sourceType')=='local':
            _stage_local_candidate(record,destination)
        else:
            download_candidate(record,destination)


def port_open(port, bind_address=''):
    host=(bind_address or '').strip()
    if not host or host=='0.0.0.0':host='127.0.0.1'
    elif host=='::':host='::1'
    try:
        with socket.create_connection((host,port),timeout=.3):return True
    except OSError:return False


def select_runtime_records(records,*,unauthenticated_test_mode=False):
    selected=list(records)
    if unauthenticated_test_mode:
        auth_plugins={'AuthMe','AuthEffects'}
        selected=[record for record in selected if record.get('pluginName') not in auth_plugins]
    return selected


MIGRATION_RUNTIME_MUTEX_PREFIX=r'Local\CopiMineMinecraft263RuntimeLifecycleLock-'


def runtime_lifecycle_mutex_name(server):
    # Match the PowerShell launcher’s lexical GetFullPath key; resolving junctions or aliases would split the lock.
    canonical=os.path.normcase(os.path.abspath(os.fspath(server))).replace('/','\\')
    suffix=hashlib.sha256(canonical.encode('utf-8')).hexdigest()
    return MIGRATION_RUNTIME_MUTEX_PREFIX+suffix


@contextmanager
def _runtime_lifecycle_lock(server):
    """Serialize plugin replacement against the supported local server launcher."""
    if os.name=='nt':
        import ctypes
        from ctypes import wintypes

        kernel32=ctypes.WinDLL('kernel32',use_last_error=True)
        create_mutex=kernel32.CreateMutexW
        create_mutex.argtypes=(wintypes.LPVOID,wintypes.BOOL,wintypes.LPCWSTR)
        create_mutex.restype=wintypes.HANDLE
        handle=create_mutex(None,False,runtime_lifecycle_mutex_name(server))
        if not handle:raise ctypes.WinError(ctypes.get_last_error())
        wait=kernel32.WaitForSingleObject
        wait.argtypes=(wintypes.HANDLE,wintypes.DWORD)
        wait.restype=wintypes.DWORD
        state=wait(handle,0)
        if state==0x102:
            kernel32.CloseHandle(handle)
            raise ValueError('Migration runtime lifecycle lock is held by server startup or another installer')
        if state not in (0,0x80):
            kernel32.CloseHandle(handle)
            raise ctypes.WinError(ctypes.get_last_error())
        release=kernel32.ReleaseMutex
        release.argtypes=(wintypes.HANDLE,)
        release.restype=wintypes.BOOL
        close=kernel32.CloseHandle
        close.argtypes=(wintypes.HANDLE,)
        close.restype=wintypes.BOOL
        try:
            yield
        finally:
            if not release(handle):
                close(handle)
                raise ctypes.WinError(ctypes.get_last_error())
            close(handle)
        return

    import fcntl
    lock_path=Path(server).parent/'.migration-lifecycle.lock'
    descriptor=os.open(lock_path,os.O_CREAT|os.O_RDWR,0o600)
    try:
        try:
            fcntl.flock(descriptor,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('Migration runtime lifecycle lock is held by server startup or another installer') from error
        try:
            yield
        finally:
            fcntl.flock(descriptor,fcntl.LOCK_UN)
    finally:
        os.close(descriptor)


def install_records(root,server,records,stage,port_checker=port_open,validate_only=False,receipt_writer=None,
                    unauthenticated_test_mode=False):
    records=select_runtime_records(records,unauthenticated_test_mode=unauthenticated_test_mode)
    with _runtime_lifecycle_lock(server):
        return _install_records_locked(root,server,records,stage,port_checker,validate_only,receipt_writer,
                                       unauthenticated_test_mode)


def _install_records_locked(root,server,records,stage,port_checker=port_open,validate_only=False,receipt_writer=None,
                            unauthenticated_test_mode=False):
    root=Path(root).resolve();server=Path(server).resolve()
    expected=root/'local-runtime/end-rift-server-26.3'
    if server!=expected or expected.resolve()!=expected:
        raise ValueError('Only the isolated 26.3 runtime is supported')
    paper=_locked_paper(root)
    _verify_prepared_runtime(server,paper,require_eula_false=False)
    stage=_ensure_build_directory(root,stage,create=False)
    _validate_receipt_destination(server)
    plugins=server/'plugins'
    if plugins.resolve()!=plugins:raise ValueError('Symlink plugin directory refused')
    names=[r['pluginName'] for r in records]
    if len(names)!=len(set(names)):raise ValueError('Duplicate replacement plugin identity')
    for record in records:
        candidate=stage/record['filename']
        _ensure_regular_file(candidate,'staged plugin candidate')
        verify_staged(record,candidate)
    properties_path=server/'server.properties'
    _ensure_regular_file(properties_path,'runtime server.properties')
    if properties_path.stat().st_size>1_000_000:raise ValueError('Runtime server.properties exceeds the safe size limit')
    properties=_java_property_entries(properties_path.read_text(encoding='utf-8'))
    _validate_local_runtime_properties(properties)
    ports=[]
    for key in ('server-port','rcon.port'):
        values=properties.get(key,[])
        if len(values)!=1 or not values[0].isdigit():raise ValueError('Missing or duplicate runtime port: '+key)
        port=int(values[0])
        if not 1<=port<=65535:raise ValueError('Runtime port must be between 1 and 65535: '+key)
        ports.append(port)
    server_bind=properties['server-ip'][0].strip()
    listeners=((ports[0],server_bind),(ports[1],''))
    if any(port_checker(port,host) for port,host in listeners):raise ValueError('Server is running; stop it before plugin replacement')
    existing={}
    for path in plugins.glob('*.jar'):
        _ensure_regular_file(path,'plugin JAR')
        existing.setdefault(identity(path),[]).append(path)
    plan=[]
    for record in records:
        destination=plugins/(record.get('runtimeFilename') or record['filename'])
        previous=existing.get(record['pluginName'],[])
        if len(previous)==1 and previous[0]==destination and digest(destination,'sha512')==record['sha512']:
            continue
        for path in previous:
            if digest(path,'sha256') not in record.get('replaceSha256',[]):
                raise ValueError('Refusing unrecognized existing '+record['pluginName']+' artifact')
        if _path_exists(destination):
            _ensure_regular_file(destination,'plugin destination')
            if destination not in previous:raise ValueError('Destination filename is already occupied')
        stale_temporaries=[destination.with_suffix('.candidate')]
        stale_temporaries.extend(destination.parent.glob(glob.escape(destination.name)+'.*.candidate'))
        for stale_temporary in stale_temporaries:
            if _path_exists(stale_temporary):
                raise ValueError('Unrecognized staging file in plugin directory: '+stale_temporary.name)
        plan.append((record,destination,previous))
    disabled_auth_plugins=[]
    disabled_auth_backup_sources=[]
    if unauthenticated_test_mode:
        lock_path=root/'tools/minecraft-26.3/server-plugins.lock.json'
        lock=json.loads(lock_path.read_text(encoding='utf-8'))
        locked_plugins=lock.get('modules',[])+lock.get('unchangedBaseline',[])
        for plugin_name in ('AuthMe','AuthEffects'):
            locked=next((row for row in locked_plugins if row.get('pluginName')==plugin_name),None)
            if locked is None:
                raise ValueError('Pinned '+plugin_name+' candidate is required to identify the local test-mode removal')
            candidate=locked.get('candidate') or {}
            accepted_hashes={value for value in (
                locked.get('sha256'),candidate.get('sha256'),locked.get('buildSha256'))
                if isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value)}
            accepted_hashes.update(value for value in locked.get('replaceSha256',[])
                                   if isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value))
            if not accepted_hashes:
                raise ValueError('Pinned '+plugin_name+' entry has no accepted artifact digests')
            for path in existing.get(plugin_name,[]):
                if digest(path,'sha256') not in accepted_hashes:
                    raise ValueError('Refusing to disable an unrecognized '+plugin_name+' artifact')
                disabled_auth_plugins.append(path)
            if plugin_name in ('AuthMe','AuthEffects') and not existing.get(plugin_name):
                source_filename=candidate.get('filename') or locked.get('filename')
                filename=source_filename
                expected_sha256=candidate.get('sha256') or locked.get('sha256')
                if (not isinstance(source_filename,str) or Path(source_filename).name!=source_filename or
                    not isinstance(filename,str) or Path(filename).name!=filename or
                    not isinstance(expected_sha256,str) or expected_sha256 not in accepted_hashes):
                    raise ValueError('Pinned '+plugin_name+' rollback artifact metadata is invalid')
                if not _has_verified_plugin_backup(server,filename,expected_sha256):
                    source=stage/source_filename
                    _ensure_regular_file(source,'pinned '+plugin_name+' rollback candidate')
                    if digest(source,'sha256')!=expected_sha256:
                        raise ValueError('Staged '+plugin_name+' rollback candidate differs from its pinned lock')
                    if identity(source)!=plugin_name:
                        raise ValueError('Staged '+plugin_name+' rollback candidate identity differs from its pinned lock')
                    disabled_auth_backup_sources.append((source,filename,expected_sha256))
    receipt={'schemaVersion':1,'minecraftVersion':'26.3','nativeVerified':False,
             'authenticationMode':'disabled-for-local-testing' if unauthenticated_test_mode else 'AuthMe-6.0.1',
             'installed':[],'pluginInventory':[]}
    if validate_only:
        active_auth_plugins=[name for name in ('AuthMe','AuthEffects') if existing.get(name)]
        if unauthenticated_test_mode and active_auth_plugins:
            raise ValueError('Authentication plugins are still active in the unauthenticated local test runtime: '+
                             ', '.join(active_auth_plugins))
        already_installed=True
        for record in records:
            path=plugins/(record.get('runtimeFilename') or record['filename'])
            if not _path_exists(path) or digest(path,'sha512')!=record['sha512']:
                already_installed=False
                break
        if already_installed:
            complete_receipt(server,receipt,records)
        return receipt
    if not plan and not disabled_auth_plugins and not disabled_auth_backup_sources:
        complete_receipt(server,receipt,records)
        if receipt_writer:receipt_writer(server,receipt)
        return receipt
    plugins.mkdir(exist_ok=True)
    if plugins.resolve()!=plugins or not plugins.is_dir():
        raise ValueError('Symlink plugin directory refused')
    # Resolve every source/destination before moving any file. Backups stay in
    # this new runtime and are never scanned as plugins by Paper.
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup=server/'migration-backups/plugins'/stamp
    if backup.resolve()!=backup or not backup.is_relative_to(server):raise ValueError('Unsafe backup destination')
    backup.mkdir(parents=True,exist_ok=False)
    moved=[];created=[];temporaries=[]
    try:
        for source in disabled_auth_plugins:
            saved=backup/source.name
            os.replace(source,saved);moved.append((source,saved))
        for source,filename,expected_sha256 in disabled_auth_backup_sources:
            saved=backup/filename
            if _path_exists(saved):
                raise ValueError('Pinned AuthMe backup destination is already occupied')
            shutil.copyfile(source,saved);created.append(saved)
            _ensure_regular_file(saved,'pinned AuthMe rollback backup')
            if digest(saved,'sha256')!=expected_sha256:
                raise ValueError('Pinned AuthMe rollback copy failed its SHA-256 check')
        for record,destination,previous in plan:
            for source in previous:
                saved=backup/source.name
                os.replace(source,saved);moved.append((source,saved))
            descriptor,temporary_name=tempfile.mkstemp(
                prefix=destination.name+'.',suffix='.candidate',dir=plugins)
            temporary=Path(temporary_name)
            temporaries.append(temporary)
            with os.fdopen(descriptor,'wb') as output,(stage/record['filename']).open('rb') as source:
                shutil.copyfileobj(source,output,64*1024)
            _ensure_regular_file(temporary,'temporary plugin candidate')
            verify_staged(record,temporary)
            os.replace(temporary,destination);created.append(destination)
            _ensure_regular_file(destination,'installed plugin JAR')
        for record in records:
            path=plugins/(record.get('runtimeFilename') or record['filename'])
            _ensure_regular_file(path,'installed plugin JAR')
            verify_staged(record,path)
        complete_receipt(server,receipt,records)
        receipt['backup']=str(backup)
        if receipt_writer:receipt_writer(server,receipt)
    except Exception:
        for path in created+temporaries:
            if _path_exists(path):path.unlink()
        for destination,saved in reversed(moved):os.replace(saved,destination)
        raise
    return receipt


def main(root=None,argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    operation=parser.add_mutually_exclusive_group()
    operation.add_argument('--stage-only',action='store_true')
    operation.add_argument('--validate-only',action='store_true')
    operation.add_argument('--prepare-runtime',action='store_true',
                           help='Create the isolated loopback-only Paper runtime without accepting the EULA or starting the server')
    operation.add_argument('--startup-settings-json',action='store_true',
                           help='Validate and print the effective loopback-only server settings for the local launcher')
    parser.add_argument('--unauthenticated-test-runtime',action='store_true',
                        help='Disable AuthMe and AuthEffects only in the loopback-only local test runtime; never use this option for a public server')
    args=parser.parse_args(argv)
    root=Path(root).resolve() if root is not None else Path(__file__).resolve().parents[2]
    if args.startup_settings_json:
        server=root/'local-runtime/end-rift-server-26.3'
        records=load_install_records(root)
        actual=validate_active_plugin_inventory(
            server,records,unauthenticated_test_mode=args.unauthenticated_test_runtime)
        settings=runtime_startup_settings(server)
        if any(row['pluginName'].casefold()=='voicechat' for row in actual):
            settings.update(configure_loopback_voicechat(server))
        print(json.dumps(settings,sort_keys=True))
        return
    if args.prepare_runtime:
        runtime=prepare_runtime(root)
        print('Prepared isolated Minecraft 26.3 Paper runtime; EULA setting preserved; server not started: '+str(runtime))
        return
    records=load_install_records(root)
    records=select_runtime_records(records,unauthenticated_test_mode=args.unauthenticated_test_runtime)
    stage=root/'build/minecraft-26.3/server-plugins'
    if args.stage_only:
        stage_records(records,stage,root)
        print('Verified '+str(len(records))+' pinned plugin candidates')
        return
    server=root/'local-runtime/end-rift-server-26.3'
    receipt=install_records(root,server,records,stage,validate_only=args.validate_only,
                            receipt_writer=None if args.validate_only else write_receipt,
                            unauthenticated_test_mode=args.unauthenticated_test_runtime)
    suffix=(' with AuthMe and AuthEffects disabled for loopback testing'
            if args.unauthenticated_test_runtime else '')
    if args.validate_only:
        print('Validated install plan for '+str(len(records))+' plugin candidates'+suffix+
              '; installed runtime inventory was not confirmed; native gameplay verification pending')
    else:
        print('Installed and verified '+str(len(records))+' plugins'+suffix+'; native gameplay verification pending')


if __name__=='__main__':main()
