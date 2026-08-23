[CmdletBinding()]
param(
    [string] $DataRoot = "",
    [string] $PostgresBin = "",
    [int] $Port = 55434
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$validationRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'artifacts\local-validation'))
if ([string]::IsNullOrWhiteSpace($DataRoot)) {
    $DataRoot = Join-Path $validationRoot 'postgres-full-plugins-v2'
}
if ([string]::IsNullOrWhiteSpace($PostgresBin)) {
    $PostgresBin = [IO.Path]::GetFullPath((Join-Path $repoRoot '..\..\..\copimine-local-bow-fix\local-runtime\postgresql\pgsql\bin'))
}

function Resolve-FullPath([string] $Path) {
    return [IO.Path]::GetFullPath($Path)
}

function Assert-UnderRoot([string] $Path, [string] $Root, [string] $Label) {
    $candidate = (Resolve-FullPath $Path).TrimEnd('\') + '\'
    $allowed = (Resolve-FullPath $Root).TrimEnd('\') + '\'
    if (-not $candidate.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay below $Root. Actual path: $Path"
    }
}

if ($Port -lt 1024 -or $Port -gt 65535) {
    throw 'The disposable PostgreSQL port must be between 1024 and 65535.'
}
$resolvedDataRoot = Resolve-FullPath $DataRoot
$resolvedBin = Resolve-FullPath $PostgresBin
$normalizedDataRoot = $resolvedDataRoot.Replace('\', '/')
Assert-UnderRoot $resolvedDataRoot $validationRoot 'Disposable PostgreSQL data root'
if ($resolvedDataRoot -match '(?i)(world|playerdata|production|authme|backup)') {
    throw 'The disposable PostgreSQL data root has a protected-data name.'
}

$initdb = Join-Path $resolvedBin 'initdb.exe'
$pgCtl = Join-Path $resolvedBin 'pg_ctl.exe'
$psql = Join-Path $resolvedBin 'psql.exe'
$createdb = Join-Path $resolvedBin 'createdb.exe'
foreach ($required in @($initdb, $pgCtl, $psql, $createdb)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "PostgreSQL runtime tool is missing: $required"
    }
}

$listener = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
Write-Output "LOCAL_POSTGRES_PORT_INPUT=$Port"
Write-Output "LOCAL_POSTGRES_LISTENER_COUNT=$($listener.Count)"
if ($listener.Count -gt 0) {
    $owner = @($listener | Select-Object -ExpandProperty OwningProcess)
    $ownedByThisRoot = @(Get-CimInstance Win32_Process -Filter "Name = 'postgres.exe'" -ErrorAction SilentlyContinue | Where-Object {
        ($_.CommandLine -replace '\\', '/') -match [regex]::Escape($normalizedDataRoot)
    })
    if ($ownedByThisRoot.Count -eq 0) {
        throw "Port $Port is already used by another process; the disposable database will not reuse it. PIDs: $($owner -join ', ')"
    }
}

if (-not (Test-Path -LiteralPath $resolvedDataRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $resolvedDataRoot -Force | Out-Null
}
$configPath = Join-Path $resolvedDataRoot 'postgresql.conf'
$ready = Test-Path -LiteralPath (Join-Path $resolvedDataRoot 'PG_VERSION') -PathType Leaf
if (-not $ready) {
    & $initdb -D $resolvedDataRoot -U postgres '--auth=trust' '--encoding=UTF8' '--no-locale' | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "initdb failed with exit code $LASTEXITCODE."
    }
}

if ($listener.Count -eq 0) {
    $logPath = Join-Path $resolvedDataRoot 'server.log'
    & $pgCtl -D $resolvedDataRoot -l $logPath -o "-p $Port -h 127.0.0.1" -w start | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "pg_ctl start failed with exit code $LASTEXITCODE."
    }
}

$deadline = [DateTimeOffset]::UtcNow.AddSeconds(30)
while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $probe = & $psql -w -h 127.0.0.1 -p $Port -U postgres -d postgres -Atqc 'SELECT 1' 2>$null
    if ($LASTEXITCODE -eq 0 -and (($probe -join '').Trim() -eq '1')) {
        break
    }
    Start-Sleep -Milliseconds 500
}
$probe = & $psql -w -h 127.0.0.1 -p $Port -U postgres -d postgres -Atqc 'SELECT 1' 2>$null
if ($LASTEXITCODE -ne 0 -or (($probe -join '').Trim() -ne '1')) {
    throw 'The disposable PostgreSQL instance did not become ready.'
}

$roleResult = & $psql -w -h 127.0.0.1 -p $Port -U postgres -d postgres -Atqc "SELECT 1 FROM pg_roles WHERE rolname='copimine_test'"
$roleExists = if ($null -eq $roleResult) { '' } else { ([string]$roleResult).Trim() }
if ($roleExists -ne '1') {
    & $psql -w -h 127.0.0.1 -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE ROLE copimine_test LOGIN PASSWORD 'copimine_test'" | Out-Host
}
$databaseResult = & $psql -w -h 127.0.0.1 -p $Port -U postgres -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='copimine_test'"
$databaseExists = if ($null -eq $databaseResult) { '' } else { ([string]$databaseResult).Trim() }
if ($databaseExists -ne '1') {
    & $createdb -w -h 127.0.0.1 -p $Port -U postgres -O copimine_test copimine_test | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "The disposable copimine_test database could not be created. ExitCode=$LASTEXITCODE"
    }
}
& $psql -w -h 127.0.0.1 -p $Port -U postgres -d copimine_test -v ON_ERROR_STOP=1 -c 'CREATE SCHEMA IF NOT EXISTS copimine_test AUTHORIZATION copimine_test' | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "The disposable copimine_test schema could not be created. ExitCode=$LASTEXITCODE"
}

# The local checkout contains both the ElectionCore player_uuid/player_name
# schema and the legacy admin uuid/name readers. Keep both aliases in this
# synthetic database only so every local plugin can complete startup.
$candidateCompatibilitySql = @'
SET search_path TO copimine_test;
CREATE TABLE IF NOT EXISTS candidates (
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL DEFAULT '',
    player_uuid TEXT NOT NULL DEFAULT '',
    player_name TEXT NOT NULL DEFAULT '',
    application_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1,
    last_result INTEGER NOT NULL DEFAULT 0,
    uuid TEXT,
    name TEXT,
    display_name TEXT,
    raw_votes INTEGER DEFAULT 0,
    admin_adjustment INTEGER DEFAULT 0,
    removed INTEGER DEFAULT 0
);
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS player_uuid TEXT NOT NULL DEFAULT '';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS player_name TEXT NOT NULL DEFAULT '';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS uuid TEXT;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS display_name TEXT;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS raw_votes INTEGER DEFAULT 0;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS admin_adjustment INTEGER DEFAULT 0;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS removed INTEGER DEFAULT 0;
UPDATE candidates
   SET uuid = COALESCE(NULLIF(uuid, ''), player_uuid),
       name = COALESCE(NULLIF(name, ''), player_name)
 WHERE COALESCE(uuid, '') = '' OR COALESCE(name, '') = '';
'@
& $psql -w -h 127.0.0.1 -p $Port -U postgres -d copimine_test -v ON_ERROR_STOP=1 -c $candidateCompatibilitySql | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "The disposable candidate compatibility schema could not be prepared. ExitCode=$LASTEXITCODE"
}

Write-Output "LOCAL_POSTGRES_ROOT=$resolvedDataRoot"
Write-Output "LOCAL_POSTGRES_BIN=$resolvedBin"
Write-Output "LOCAL_POSTGRES_PORT=$Port"
Write-Output 'LOCAL_POSTGRES_DB=copimine_test'
Write-Output 'LOCAL_POSTGRES_DATA_COPIED=NO'
Write-Output 'LOCAL_STAGING_SCHEMA_COMPATIBILITY=ELECTION_CANDIDATE_ALIASES_ONLY'
Write-Output 'LOCAL_POSTGRES_READY=YES'
