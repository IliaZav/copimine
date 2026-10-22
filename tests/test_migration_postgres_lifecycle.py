import json
import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
DUMMY_SENTINEL = "DUMMY_MIGRATION_PASSWORD_NEVER_EMIT_5f73"


def _powershell():
    return shutil.which("powershell.exe") or shutil.which("pwsh")


def test_postgres_lifecycle_operations_preserve_existing_cluster_and_credentials(tmp_path):
    powershell = _powershell()
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the migration PostgreSQL lifecycle module.")

    module_path = ROOT / "scripts/minecraft/MigrationPostgresLifecycle.psm1"
    escaped_module = str(module_path).replace("'", "''")
    escaped_root = str(tmp_path).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
$modulePath = '{escaped_module}'
if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf)) {{
  Write-Output 'LIFECYCLE_MODULE_MISSING'
  exit 0
}}
Import-Module -Name $modulePath -Force
if (-not (Get-Command Ensure-MigrationPostgresReady -ErrorAction SilentlyContinue)) {{
  Write-Output 'LIFECYCLE_API_MISSING'
  exit 0
}}
$workspace = '{escaped_root}'
New-Item -ItemType Directory -Path $workspace -Force | Out-Null
$pgBin = Join-Path $workspace 'toolchains/postgresql/pgsql/bin'
New-Item -ItemType Directory -Path $pgBin -Force | Out-Null
foreach ($name in @('postgres.exe', 'pg_ctl.exe', 'pg_isready.exe', 'psql.exe')) {{
  [IO.File]::WriteAllBytes((Join-Path $pgBin $name), [byte[]]@())
}}

function New-Fixture([string]$Name, [bool]$WithVersion = $true, [bool]$WithCredentials = $true) {{
  $runtime = Join-Path (Join-Path (Join-Path $workspace $Name) 'local-runtime') 'end-rift-server-26.3'
  $data = Join-Path $runtime 'postgres-data'
  $logs = Join-Path $runtime 'logs'
  New-Item -ItemType Directory -Path $data, $logs -Force | Out-Null
  if ($WithVersion) {{ [IO.File]::WriteAllText((Join-Path $data 'PG_VERSION'), '16' + [Environment]::NewLine) }}
  if ($WithCredentials) {{
    $enc = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText((Join-Path $runtime 'migration-postgres-admin.env'), 'POSTGRES_SUPERUSER_PASSWORD={DUMMY_SENTINEL}_ADMIN' + [Environment]::NewLine, $enc)
    $plugin = @(
      'POSTGRES_HOST=127.0.0.1',
      'POSTGRES_PORT=55434',
      'POSTGRES_DB=copimine_migration_test',
      'POSTGRES_SCHEMA=copimine_migration_26_3_candidate',
      'POSTGRES_USER=copimine_migration_test',
      'POSTGRES_PASSWORD={DUMMY_SENTINEL}',
      'DATABASE_URL=postgresql://copimine_migration_test:{DUMMY_SENTINEL}@127.0.0.1:55434/copimine_migration_test'
    ) -join "`n"
    [IO.File]::WriteAllText((Join-Path $runtime 'migration-isolated-postgres.env'), $plugin + "`n", $enc)
  }}
  return [pscustomobject]@{{ Runtime = $runtime; Data = $data; Admin = (Join-Path $runtime 'migration-postgres-admin.env'); Plugin = (Join-Path $runtime 'migration-isolated-postgres.env') }}
}}

function New-State([bool]$Running, [bool]$Owned, [bool]$Occupied, [int]$ProcessId = 42) {{
  return [pscustomobject]@{{ Running = $Running; Owned = $Owned; ProcessId = $ProcessId; Occupied = $Occupied; ListenerPid = $(if ($Occupied) {{ $ProcessId }} else {{ 0 }}); Starts = 0; Waits = 0; Validations = 0; StalePidFile = $false; StalePidProcessId = 0; StalePidHash = ''; StalePidQuarantineCount = 0; StalePidArchivePath = '' }}
}}

function Get-BytesSnapshot([string]$path) {{
  return [Convert]::ToBase64String([IO.File]::ReadAllBytes($path))
}}

function New-Operations($state) {{
  $operations = @{{}}
  $operations.GetBinaryVersion = {{ param($bin) 'postgres (PostgreSQL) 16.15' }}.GetNewClosure()
  $operations.GetClusterState = {{ param($data, $bin, $port) [pscustomobject]@{{ Running = $state.Running; Owned = $state.Owned; ProcessId = $(if ($state.Running) {{ $state.ProcessId }} else {{ $null }}); StalePidFile = $state.StalePidFile; StalePidProcessId = $state.StalePidProcessId; StalePidHash = $state.StalePidHash }} }}.GetNewClosure()
  $operations.GetListenerInfo = {{ param($port) $owners = @(); if ($state.Occupied) {{ $owners = @([int]$state.ListenerPid) }}; [pscustomobject]@{{ Occupied = $state.Occupied; ProcessIds = $owners; LocalAddresses = @('127.0.0.1') }} }}.GetNewClosure()
  $operations.QuarantineStalePid = {{ param($data, $port, $recordedPid, $expectedHash)
    if ($state.Occupied) {{ throw 'occupied port reached stale PID quarantine' }}
    if (-not $state.StalePidFile -or $recordedPid -ne $state.StalePidProcessId) {{ throw 'unexpected stale PID quarantine request' }}
    $pidPath = Join-Path $data 'postmaster.pid'
    if ((Get-BytesSnapshot $pidPath) -cne $expectedHash) {{ throw 'stale PID file changed before quarantine' }}
    $archivePath = $pidPath + '.stale-test-' + $recordedPid
    Move-Item -LiteralPath $pidPath -Destination $archivePath -ErrorAction Stop
    $state.StalePidQuarantineCount++
    $state.StalePidArchivePath = $archivePath
    $state.StalePidFile = $false
  }}.GetNewClosure()
  $operations.StartCluster = {{ param($data, $bin, $log, $port) $state.Starts++; $state.Running = $true; $state.Owned = $true; $state.ProcessId = 43; $state.Occupied = $true; $state.ListenerPid = 43 }}.GetNewClosure()
  $operations.WaitReady = {{ param($bin, $port, $timeout) $state.Waits++; return $true }}.GetNewClosure()
  $operations.ValidateIdentity = {{ param($context) $state.Validations++; return $true }}.GetNewClosure()
  return $operations
}}

function Invoke-Ensure($fixture, $state, [bool]$StartIfStopped) {{
  $ops = New-Operations $state
  try {{
    $result = Ensure-MigrationPostgresReady -WorkspaceRoot (Split-Path -Parent (Split-Path -Parent $fixture.Runtime)) `
      -RuntimeDirectory $fixture.Runtime -PostgresBin $pgBin -Port 55434 -StartIfStopped:$StartIfStopped -Operations $ops
    return [pscustomobject]@{{ Success = $true; Started = $result.StartedByThisCall; Error = '' }}
  }} catch {{
    return [pscustomobject]@{{ Success = $false; Started = $false; Error = $_.Exception.Message }}
  }}
}}

function Set-StalePidFixture($fixture, $state, [int]$recordedPid) {{
  $pidPath = Join-Path $fixture.Data 'postmaster.pid'
  $pidContents = @([string]$recordedPid, $fixture.Data, '1782000000', '55434', (Join-Path $fixture.Runtime 'logs'), '127.0.0.1', '', 'ready') -join "`n"
  [IO.File]::WriteAllText($pidPath, $pidContents + "`n", [Text.UTF8Encoding]::new($false))
  $state.StalePidFile = $true
  $state.StalePidProcessId = $recordedPid
  $state.StalePidHash = Get-BytesSnapshot $pidPath
}}

$healthy = New-Fixture 'healthy'
$healthyState = New-State $true $true $true 42
$adminHashBefore = Get-BytesSnapshot $healthy.Admin
$pluginHashBefore = Get-BytesSnapshot $healthy.Plugin
$healthyFirst = Invoke-Ensure $healthy $healthyState $true
$healthySecond = Invoke-Ensure $healthy $healthyState $true
$healthyAdminUnchanged = $adminHashBefore -ceq (Get-BytesSnapshot $healthy.Admin)
$healthyPluginUnchanged = $pluginHashBefore -ceq (Get-BytesSnapshot $healthy.Plugin)

$stopped = New-Fixture 'stopped'
$stoppedState = New-State $false $false $false
$stoppedResult = Invoke-Ensure $stopped $stoppedState $true

$stalePid = New-Fixture 'stale-pid'
$stalePidState = New-State $false $false $false
Set-StalePidFixture $stalePid $stalePidState 987654
$stalePidResult = Invoke-Ensure $stalePid $stalePidState $true
$stalePidArchivePreserved = $stalePidState.StalePidArchivePath -and
  (Test-Path -LiteralPath $stalePidState.StalePidArchivePath -PathType Leaf) -and
  (Get-BytesSnapshot $stalePidState.StalePidArchivePath) -ceq $stalePidState.StalePidHash

$stalePidOccupied = New-Fixture 'stale-pid-occupied'
$stalePidOccupiedState = New-State $false $false $true 98
Set-StalePidFixture $stalePidOccupied $stalePidOccupiedState 987655
$stalePidOccupiedResult = Invoke-Ensure $stalePidOccupied $stalePidOccupiedState $true
$stalePidOccupiedUnchanged = (Test-Path -LiteralPath (Join-Path $stalePidOccupied.Data 'postmaster.pid') -PathType Leaf) -and
  $stalePidOccupiedState.StalePidQuarantineCount -eq 0

$stalePidValidateOnly = New-Fixture 'stale-pid-validate-only'
$stalePidValidateOnlyState = New-State $false $false $false
Set-StalePidFixture $stalePidValidateOnly $stalePidValidateOnlyState 987656
$stalePidValidateOnlyResult = Invoke-Ensure $stalePidValidateOnly $stalePidValidateOnlyState $false
$stalePidValidateOnlyUnchanged = (Test-Path -LiteralPath (Join-Path $stalePidValidateOnly.Data 'postmaster.pid') -PathType Leaf) -and
  $stalePidValidateOnlyState.StalePidQuarantineCount -eq 0

$wrongPort = New-Fixture 'wrong-port'
$wrongPortState = New-State $false $false $true 98
$wrongPortResult = Invoke-Ensure $wrongPort $wrongPortState $true

$missingCredential = New-Fixture 'missing-credential'
Remove-Item -LiteralPath $missingCredential.Plugin -Force
$missingCredentialState = New-State $false $false $false
$missingCredentialResult = Invoke-Ensure $missingCredential $missingCredentialState $true

$corruptCredential = New-Fixture 'corrupt-credential'
$corruptText = [IO.File]::ReadAllText($corruptCredential.Plugin).Replace('POSTGRES_HOST=127.0.0.1', 'POSTGRES_HOST=192.0.2.10')
[IO.File]::WriteAllText($corruptCredential.Plugin, $corruptText, [Text.UTF8Encoding]::new($false))
$corruptCredentialState = New-State $false $false $false
$corruptCredentialResult = Invoke-Ensure $corruptCredential $corruptCredentialState $true

$partial = New-Fixture 'partial' $false $false
[IO.File]::WriteAllText((Join-Path $partial.Data 'partial-init.marker'), 'preserve')
$partialState = New-State $false $false $false
$partialResult = Invoke-Ensure $partial $partialState $true
$guardMessage = ''
try {{ Assert-MigrationPostgresNewClusterDirectory -RuntimeDirectory $partial.Runtime -DataDirectory $partial.Data | Out-Null }} catch {{ $guardMessage = $_.Exception.Message }}
$partialCredentialFilesAbsent = -not (Test-Path -LiteralPath $partial.Admin) -and -not (Test-Path -LiteralPath $partial.Plugin)

$validateOnly = New-Fixture 'validate-only'
$validateOnlyState = New-State $false $false $false
$validateOnlyResult = Invoke-Ensure $validateOnly $validateOnlyState $false

[pscustomobject]@{{
  healthyRunsPass = ($healthyFirst.Success -and $healthySecond.Success)
  healthyStartCalls = $healthyState.Starts
  healthyValidationCalls = $healthyState.Validations
  healthyPrivateFilesUnchanged = ($healthyAdminUnchanged -and $healthyPluginUnchanged)
  stoppedRunPasses = $stoppedResult.Success
  stoppedStartCalls = $stoppedState.Starts
  stoppedValidationCalls = $stoppedState.Validations
  stalePidStartPasses = $stalePidResult.Success
  stalePidQuarantines = $stalePidState.StalePidQuarantineCount
  stalePidStartCalls = $stalePidState.Starts
  stalePidArchivePreservesOriginal = $stalePidArchivePreserved
  stalePidOccupiedRejectedWithoutQuarantine = (-not $stalePidOccupiedResult.Success -and $stalePidOccupiedState.Starts -eq 0 -and $stalePidOccupiedUnchanged)
  stalePidValidateOnlyRejectedWithoutMutation = (-not $stalePidValidateOnlyResult.Success -and $stalePidValidateOnlyState.Starts -eq 0 -and $stalePidValidateOnlyUnchanged)
  wrongPortRejected = (-not $wrongPortResult.Success -and $wrongPortState.Starts -eq 0)
  missingCredentialRejectedWithoutStart = (-not $missingCredentialResult.Success -and $missingCredentialState.Starts -eq 0)
  corruptCredentialRejectedWithoutStart = (-not $corruptCredentialResult.Success -and $corruptCredentialState.Starts -eq 0)
  partialDirectoryRejectedWithoutStart = (-not $partialResult.Success -and $partialState.Starts -eq 0)
  partialProvisionGuardRejects = (-not [string]::IsNullOrWhiteSpace($guardMessage))
  partialCredentialFilesRemainAbsent = $partialCredentialFilesAbsent
  validateOnlyStoppedRejectedWithoutStart = (-not $validateOnlyResult.Success -and $validateOnlyState.Starts -eq 0)
}} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=45,
    )
    combined = (result.stdout + result.stderr).replace(DUMMY_SENTINEL, "[dummy-redacted]")
    safe_error = result.stderr.replace(DUMMY_SENTINEL, "[dummy-redacted]")
    assert result.returncode == 0, f"PowerShell fixture probe failed: {safe_error}"
    assert "LIFECYCLE_MODULE_MISSING" not in combined, "the non-provisioning PostgreSQL lifecycle module is missing"
    assert "LIFECYCLE_API_MISSING" not in combined, "the non-provisioning PostgreSQL reuse function is missing"
    report = json.loads(result.stdout.splitlines()[-1])
    assert report["healthyRunsPass"] is True, "an owned running cluster should validate repeatedly"
    assert report["healthyStartCalls"] == 0, "an already-running cluster must not be restarted"
    assert report["healthyValidationCalls"] == 2, "each healthy reuse should perform identity validation"
    assert report["healthyPrivateFilesUnchanged"] is True, "reuse must preserve both private credential files byte-for-byte"
    assert report["stoppedRunPasses"] is True and report["stoppedStartCalls"] == 1
    assert report["stoppedValidationCalls"] == 1, "starting an existing cluster must still validate its identity"
    assert report["stalePidStartPasses"] is True, "a stale PID for the exact stopped owned cluster should not block normal startup"
    assert report["stalePidQuarantines"] == 1 and report["stalePidStartCalls"] == 1
    assert report["stalePidArchivePreservesOriginal"] is True, "startup must quarantine, not discard, the stale PID evidence"
    assert report["stalePidOccupiedRejectedWithoutQuarantine"] is True, "an occupied port must reject stale PID repair before any mutation"
    assert report["stalePidValidateOnlyRejectedWithoutMutation"] is True, "ValidateOnly must preserve a stale PID file and never start"
    assert report["wrongPortRejected"] is True, "an occupied PostgreSQL port must fail before starting the cluster"
    assert report["missingCredentialRejectedWithoutStart"] is True
    assert report["corruptCredentialRejectedWithoutStart"] is True
    assert report["partialDirectoryRejectedWithoutStart"] is True
    assert report["partialProvisionGuardRejects"] is True
    assert report["partialCredentialFilesRemainAbsent"] is True
    assert report["validateOnlyStoppedRejectedWithoutStart"] is True
    assert DUMMY_SENTINEL not in combined, "lifecycle diagnostics exposed a dummy credential sentinel"


def test_ensure_identity_sql_is_read_only_and_does_not_reprovision():
    module_path = ROOT / "scripts/minecraft/MigrationPostgresLifecycle.psm1"
    assert module_path.is_file(), "the non-provisioning lifecycle implementation is missing"
    source = module_path.read_text(encoding="utf-8")
    start = source.find("function Test-MigrationPostgresIdentity")
    end = source.find("function Invoke-MigrationPostgresLifecycleOperation", start)
    assert start >= 0 and end > start, "read-only identity validation function is missing"
    identity = source[start:end].upper()
    assert "BEGIN READ ONLY" in identity
    for forbidden in ("ALTER ROLE", "ALTER DATABASE", "CREATE ROLE", "CREATE SCHEMA", "DROP ", "TRUNCATE "):
        assert forbidden not in identity, f"reuse identity validation must not issue {forbidden.strip()}"
