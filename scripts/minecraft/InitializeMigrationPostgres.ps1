[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RuntimeDirectory,
    [int]$Port = 55434,
    [string]$PostgresBin
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
Import-Module -Name (Join-Path $PSScriptRoot 'MigrationDatabaseEnvironment.psm1') -Force
$runtime = (Resolve-Path -LiteralPath $RuntimeDirectory -ErrorAction Stop).Path
$expectedRuntime = [IO.Path]::GetFullPath((Join-Path $workspaceRoot 'local-runtime/end-rift-server-26.3'))
if (-not [string]::Equals($runtime.TrimEnd('\'), $expectedRuntime.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The migration PostgreSQL runtime must remain inside local-runtime/end-rift-server-26.3.'
}
foreach ($path in @($workspaceRoot, $PSScriptRoot, $runtime)) {
    Assert-MigrationPathNoReparseAncestors -Path $path
}
if ($Port -lt 1024 -or $Port -gt 65535 -or $Port -eq 55433) {
    throw 'The migration PostgreSQL port must be valid and separate from the shared End Rift database.'
}

$data = Join-Path $runtime 'postgres-data'
$logDirectory = Join-Path $runtime 'logs'
$log = Join-Path $logDirectory 'postgres-migration.log'
$adminEnv = Join-Path $runtime 'migration-postgres-admin.env'
$pluginEnv = Join-Path $runtime 'migration-isolated-postgres.env'
$passwordFile = Join-Path $runtime 'postgres-initdb-password.tmp'
$database = 'copimine_migration_test'
$role = 'copimine_migration_test'
$schema = 'copimine_migration_26_3_candidate'

function Invoke-Psql([string]$PsqlPath, [string]$User, [string]$Password, [string]$Db, [string]$Sql) {
    $previous = [Environment]::GetEnvironmentVariable('PGPASSWORD', [EnvironmentVariableTarget]::Process)
    $previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
    try {
        $env:PGPASSWORD = $Password
        # psql uses stderr for successful NOTICE output, so let the explicit
        # exit-code check below decide whether the command failed.
        $PSNativeCommandUseErrorActionPreference = $false
        $output = $Sql | & $PsqlPath -q -X -w -h '127.0.0.1' -p $Port -U $User -d $Db -v 'ON_ERROR_STOP=1' -A -t 2>&1
        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL rejected a migration setup query (exit code $LASTEXITCODE)." }
        return (@($output | ForEach-Object { [string]$_ }) -join "`n")
    } finally {
        $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
        if ($null -eq $previous) { Remove-Item -LiteralPath 'Env:PGPASSWORD' -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable('PGPASSWORD', $previous, [EnvironmentVariableTarget]::Process) }
    }
}

foreach ($path in @($runtime, $data, $logDirectory, $log, $adminEnv, $pluginEnv, $passwordFile)) {
    Assert-MigrationPathNoReparseAncestors -Path $path
}
New-Item -ItemType Directory -Path $runtime, $data, $logDirectory -Force | Out-Null
foreach ($path in @($runtime, $data, $logDirectory)) {
    if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Migration PostgreSQL directories cannot be reparse points: $path"
    }
}

if ([string]::IsNullOrWhiteSpace($PostgresBin)) {
    $candidateBin = [IO.Path]::GetFullPath((Join-Path $workspaceRoot '../../local-runtime/postgresql/pgsql/bin'))
    if (Test-Path -LiteralPath (Join-Path $candidateBin 'pg_ctl.exe') -PathType Leaf) {
        $PostgresBin = $candidateBin
    } else {
        $pgCommand = Get-Command 'pg_ctl.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -eq $pgCommand) { throw 'PostgreSQL binaries are unavailable. Install PostgreSQL 16+ or pass -PostgresBin.' }
        $PostgresBin = Split-Path -Parent $pgCommand.Source
    }
}
$PostgresBin = (Resolve-Path -LiteralPath $PostgresBin -ErrorAction Stop).Path
$pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
$initDb = Join-Path $PostgresBin 'initdb.exe'
$psql = Join-Path $PostgresBin 'psql.exe'
$createDb = Join-Path $PostgresBin 'createdb.exe'
$ready = Join-Path $PostgresBin 'pg_isready.exe'
$postgresVersion = (& (Join-Path $PostgresBin 'postgres.exe') '--version' 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $postgresVersion -notmatch '(?<major>\d+)\.' -or [int]$Matches.major -lt 16) {
    throw 'PostgreSQL 16 or newer is required for the isolated migration database.'
}

foreach ($path in @($runtime, $data, $logDirectory, $log, $adminEnv, $pluginEnv, $passwordFile)) {
    Assert-MigrationPathNoReparseAncestors -Path $path
}
$credentials = Initialize-MigrationPostgresCredentialFiles `
    -AdminPath $adminEnv `
    -PluginPath $pluginEnv `
    -DatabaseInitialized (Test-Path -LiteralPath (Join-Path $data 'PG_VERSION') -PathType Leaf) `
    -Port $Port `
    -Database $database `
    -Schema $schema `
    -Role $role
$adminPassword = $credentials.AdminPassword
$pluginPassword = $credentials.PluginPassword

$versionPath = Join-Path $data 'PG_VERSION'
if (-not (Test-Path -LiteralPath $versionPath -PathType Leaf)) {
    if (@(Get-ChildItem -LiteralPath $data -Force).Count -gt 0) {
        throw 'The isolated PostgreSQL data directory is non-empty but has no PG_VERSION; refusing to initialize over it.'
    }
    $passwordFileCreatedByThisCall = $false
    try {
        Write-MigrationPrivateEnvFile -Path $passwordFile -Contents ($adminPassword + "`n")
        $passwordFileCreatedByThisCall = $true
        & $initDb -D $data -U postgres --auth-local=scram-sha-256 --auth-host=scram-sha-256 ('--pwfile=' + $passwordFile) --encoding=UTF8
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initdb failed for the isolated migration cluster.' }
    } finally {
        Remove-MigrationPrivateFileIfOwned -Path $passwordFile -CreatedByThisCall:$passwordFileCreatedByThisCall
    }
}
if ([int]([IO.File]::ReadAllText($versionPath).Trim()) -lt 16) { throw 'The isolated PostgreSQL cluster must use PostgreSQL 16 or newer.' }
$hostRules = @(Get-Content -LiteralPath (Join-Path $data 'pg_hba.conf') -Encoding UTF8 | Where-Object { $_ -match '^\s*host\s+' -and $_ -notmatch '^\s*#' })
if ($hostRules.Count -eq 0) {
    throw 'The isolated PostgreSQL cluster has no host authentication rules.'
}

& $pgCtl -D $data status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    $probe = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $probe.BeginConnect('127.0.0.1', $Port, $null, $null)
        if ($pending.AsyncWaitHandle.WaitOne(300) -and $probe.Connected) { throw "PostgreSQL port $Port is occupied by another process." }
    } finally { $probe.Dispose() }
    $options = "-c listen_addresses=127.0.0.1 -c port=$Port -c password_encryption=scram-sha-256"
    & $pgCtl -D $data -l $log -o $options -w start
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated loopback PostgreSQL cluster.' }
}

$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    & $ready -q -h '127.0.0.1' -p $Port -U postgres -d postgres
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Milliseconds 250
} while ([DateTime]::UtcNow -lt $deadline)
if ($LASTEXITCODE -ne 0) { throw 'Isolated migration PostgreSQL did not become ready on loopback.' }

$listenAddresses = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' 'SHOW listen_addresses;'
if ($listenAddresses.Trim() -cne '127.0.0.1') {
    throw 'The already-running isolated PostgreSQL cluster is not bound exclusively to 127.0.0.1.'
}
$activePort = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' 'SHOW port;'
if ($activePort.Trim() -cne [string]$Port) {
    throw 'The already-running isolated PostgreSQL cluster is listening on an unexpected port.'
}
$passwordEncryption = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' 'SHOW password_encryption;'
if ($passwordEncryption.Trim() -cne 'scram-sha-256') {
    throw 'The isolated PostgreSQL cluster must enforce SCRAM-SHA-256 password encryption.'
}
$effectiveHostRuleCount = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' "SELECT COUNT(*) FROM pg_hba_file_rules WHERE type LIKE 'host%';"
if ([int]$effectiveHostRuleCount.Trim() -lt 1) {
    throw 'The effective isolated PostgreSQL configuration has no host authentication rules.'
}
$invalidHostRules = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' "SELECT COUNT(*) FROM pg_hba_file_rules WHERE error IS NOT NULL OR (type LIKE 'host%' AND (auth_method<>'scram-sha-256' OR COALESCE(NOT ((address='127.0.0.1' AND netmask='255.255.255.255') OR (address='::1' AND netmask='ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff')),TRUE)));"
if ([int]$invalidHostRules.Trim() -ne 0) {
    throw 'The effective PostgreSQL host rules must use SCRAM-SHA-256 and allow only IPv4/IPv6 loopback clients.'
}

$roleExists = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' "SELECT 1 FROM pg_roles WHERE rolname='$role';"
if ($roleExists.Trim() -ne '1') {
    Invoke-Psql $psql 'postgres' $adminPassword 'postgres' "CREATE ROLE $role LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;" | Out-Null
}
$pluginPasswordHex = ConvertTo-MigrationPostgresPasswordHex -Value $pluginPassword
$alterRoleSql = @'
DO $migration$
BEGIN
  EXECUTE format(
    'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L',
    '__ROLE__',
    convert_from(decode('__PLUGIN_PASSWORD_HEX__','hex'),'UTF8')
  );
END
$migration$;
'@
$alterRoleSql = $alterRoleSql.Replace('__ROLE__', $role).Replace('__PLUGIN_PASSWORD_HEX__', $pluginPasswordHex)
Invoke-Psql $psql 'postgres' $adminPassword 'postgres' $alterRoleSql | Out-Null
$databaseExists = Invoke-Psql $psql 'postgres' $adminPassword 'postgres' "SELECT 1 FROM pg_database WHERE datname='$database';"
if ($databaseExists.Trim() -ne '1') {
    $previous = [Environment]::GetEnvironmentVariable('PGPASSWORD', [EnvironmentVariableTarget]::Process)
    try {
        $env:PGPASSWORD = $adminPassword
        & $createDb -h '127.0.0.1' -p $Port -U postgres -O $role $database
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated migration database.' }
    } finally {
        if ($null -eq $previous) { Remove-Item -LiteralPath 'Env:PGPASSWORD' -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable('PGPASSWORD', $previous, [EnvironmentVariableTarget]::Process) }
    }
}

Invoke-Psql $psql 'postgres' $adminPassword 'postgres' "ALTER DATABASE $database OWNER TO $role; REVOKE ALL ON DATABASE $database FROM PUBLIC; GRANT CONNECT,TEMPORARY ON DATABASE $database TO $role;" | Out-Null
Invoke-Psql $psql 'postgres' $adminPassword $database "SET client_min_messages TO warning; CREATE SCHEMA IF NOT EXISTS $schema AUTHORIZATION $role; ALTER SCHEMA $schema OWNER TO $role; ALTER ROLE $role IN DATABASE $database SET search_path TO $schema,public;" | Out-Null
$identity = Invoke-Psql $psql $role $pluginPassword $database "SET search_path TO $schema,public; SELECT current_database() || '|' || current_user || '|' || current_schema();"
if ($identity.Trim() -ne "$database|$role|$schema") { throw 'Isolated PostgreSQL identity/schema preflight failed; plugin migrations were not started.' }

Write-Output "Isolated migration PostgreSQL ready: database=$database schema=$schema user=$role endpoint=127.0.0.1:$Port auth=scram-sha-256"
Write-Output $pluginEnv
