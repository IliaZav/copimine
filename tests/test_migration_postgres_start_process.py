"""A persistent child must not keep the pg_ctl launcher pipeline open on Windows."""
from pathlib import Path
import json
import shutil
import subprocess
import sys
import pytest
ROOT=Path(__file__).resolve().parents[1]
@pytest.mark.skipif(sys.platform!="win32",reason="Windows process-handle regression")
@pytest.mark.parametrize("mode",["success","failure"])
def test_pg_ctl_start_waits_for_launcher_not_persistent_child(tmp_path,mode):
    shell=shutil.which("powershell.exe")
    if not shell:pytest.skip("PowerShell unavailable")
    module=str(ROOT/"scripts/minecraft/MigrationPostgresLifecycle.psm1").replace("'","''")
    folder=str(tmp_path).replace("'","''")
    script=tmp_path/"probe.ps1"
    script.write_text(r'''
$ErrorActionPreference='Stop'
$root='FOLDER'
Import-Module 'MODULE' -Force
$source=@"
using System;
using System.Diagnostics;
using System.IO;
public class FakePgCtl {
 public static void Main(string[] args){
  if(File.Exists(Path.Combine(args[1],"fail-launch"))){Environment.Exit(7);}
  var start=new ProcessStartInfo("powershell.exe","-NoProfile -Command Start-Sleep -Seconds 12 # CM_OWNED_PG_START_PROBE");
  start.UseShellExecute=false;start.CreateNoWindow=true;
  var child=Process.Start(start);
  File.WriteAllText(Path.Combine(args[1],"owned-child.pid"),child.Id.ToString());
  Console.WriteLine("server started");
 }
}
"@
Add-Type -TypeDefinition $source -OutputAssembly (Join-Path $root 'pg_ctl.exe') -OutputType ConsoleApplication
$expectedFailure=EXPECTED_FAILURE
if($expectedFailure){[IO.File]::WriteAllText((Join-Path $root 'fail-launch'),'test')}
$watch=[Diagnostics.Stopwatch]::StartNew()
try {
 $failed=$false
 try { & (Get-Module MigrationPostgresLifecycle) {param($folder) Start-MigrationPostgresCluster -DataDirectory $folder -PostgresBin $folder -LogPath (Join-Path $folder 'postgres.log') -Port 55435} $root } catch {$failed=$true}
 if($failed -ne $expectedFailure){throw 'launcher exit status was not preserved'}
 $watch.Stop()
 [pscustomobject]@{Millis=$watch.ElapsedMilliseconds} | ConvertTo-Json -Compress
} finally {
 $pidFile=Join-Path $root 'owned-child.pid'
 if(Test-Path -LiteralPath $pidFile){
  $ownedId=[int]([IO.File]::ReadAllText($pidFile))
  $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$ownedId" -ErrorAction SilentlyContinue
  if($owned -and $owned.Name -eq 'powershell.exe' -and $owned.CommandLine.Contains('CM_OWNED_PG_START_PROBE')){
   Stop-Process -Id $ownedId -ErrorAction SilentlyContinue
  }
 }
}
'''.replace("FOLDER",folder).replace("MODULE",module).replace("EXPECTED_FAILURE","$true" if mode=="failure" else "$false"),encoding="utf-8")
    ran=subprocess.run([shell,"-NoProfile","-ExecutionPolicy","Bypass","-File",str(script)],capture_output=True,text=True,timeout=35)
    assert ran.returncode==0,ran.stdout+ran.stderr
    report=json.loads(ran.stdout.splitlines()[-1])
    assert report["Millis"]<5000,"pg_ctl exit waited for the persistent PostgreSQL child"
