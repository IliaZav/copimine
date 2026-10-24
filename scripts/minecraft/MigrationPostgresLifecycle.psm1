$modulePath = Join-Path $PSScriptRoot 'MigrationDatabaseEnvironment.psm1'
Import-Module -Name $modulePath

function Assert-MigrationPostgresNewClusterDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RuntimeDirectory,
        [Parameter(Mandatory)][string]$DataDirectory
    )

    $runtime = [IO.Path]::GetFullPath($RuntimeDirectory).TrimEnd('\')
    $expectedData = [IO.Path]::GetFullPath((Join-Path $runtime 'postgres-data')).TrimEnd('\')
    $data = [IO.Path]::GetFullPath($DataDirectory).TrimEnd('\')
    if (-not [string]::Equals($data, $expectedData, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The new PostgreSQL data directory must be the postgres-data child of its owned runtime.'
    }
    foreach ($path in @($runtime, $data)) { Assert-MigrationPathNoReparseAncestors -Path $path }
    if (-not (Test-Path -LiteralPath $runtime -PathType Container) -or
        -not (Test-Path -LiteralPath $data -PathType Container)) {
        throw 'The owned PostgreSQL runtime and data directories must already exist before provisioning.'
    }
    foreach ($path in @($runtime, $data)) {
        $item = Get-Item -LiteralPath $path -Force -ErrorAction Stop
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "PostgreSQL provisioning directories cannot be reparse points: $path"
        }
    }
    if (Test-Path -LiteralPath (Join-Path $data 'PG_VERSION') -PathType Leaf) {
        throw 'An initialized PostgreSQL cluster already exists; use the non-provisioning reuse path.'
    }
    if (@(Get-ChildItem -LiteralPath $data -Force -ErrorAction Stop).Count -ne 0) {
        throw 'The PostgreSQL data directory is non-empty without PG_VERSION; refusing to provision over partial data.'
    }
    return $true
}

function Resolve-MigrationPostgresBinaryDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$WorkspaceRoot,
        [Parameter(Mandatory)][string]$PostgresBin,
        [hashtable]$Operations
    )

    $root = (Resolve-Path -LiteralPath $WorkspaceRoot -ErrorAction Stop).Path
    $requested = [IO.Path]::GetFullPath($PostgresBin).TrimEnd('\')
    if ($null -eq $Operations) {
        $expected = [IO.Path]::GetFullPath((Join-Path $root 'local-runtime/toolchains/postgresql-16.15/pgsql/bin')).TrimEnd('\')
        if (-not [string]::Equals($requested, $expected, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'The live migration database must use the existing workspace PostgreSQL 16.15 toolchain path.'
        }

        $toolchains = Join-Path $root 'local-runtime/toolchains'
        $toolchainsItem = Get-Item -LiteralPath $toolchains -Force -ErrorAction Stop
        $reparse = [bool]($toolchainsItem.Attributes -band [IO.FileAttributes]::ReparsePoint)
        if ($reparse) {
            # The source-of-truth checkout intentionally exposes the pinned
            # toolchain directory through one junction. Resolve that one
            # approved boundary explicitly, then resume strict checks below.
            if ($toolchainsItem.LinkType -cne 'Junction' -or [string]::IsNullOrWhiteSpace([string]$toolchainsItem.Target)) {
                throw 'The pinned PostgreSQL toolchain path may contain only its expected local-runtime/toolchains junction.'
            }
            $targets = @($toolchainsItem.Target)
            if ($targets.Count -ne 1) { throw 'The pinned PostgreSQL toolchain junction must have exactly one target.' }
            $target = [string]$targets[0]
            if (-not [IO.Path]::IsPathRooted($target)) {
                $target = Join-Path (Split-Path -Parent $toolchains) $target
            }
            $toolchainRoot = [IO.Path]::GetFullPath($target).TrimEnd('\')
            if (-not (Test-Path -LiteralPath $toolchainRoot -PathType Container)) {
                throw 'The pinned PostgreSQL toolchain junction target is missing.'
            }
            $targetItem = Get-Item -LiteralPath $toolchainRoot -Force -ErrorAction Stop
            if ($targetItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw 'The pinned PostgreSQL toolchain junction cannot target another reparse point.'
            }
            $resolved = [IO.Path]::GetFullPath((Join-Path $toolchainRoot 'postgresql-16.15/pgsql/bin'))
        }
        else {
            $resolved = $expected
        }
    }
    else {
        # Process/SQL spies may use a disposable fixture outside the canonical
        # checkout. They still receive the full path and leaf checks below.
        $resolved = $requested
    }

    Assert-MigrationPathNoReparseAncestors -Path $resolved
    $resolved = (Resolve-Path -LiteralPath $resolved -ErrorAction Stop).Path
    foreach ($name in @('postgres.exe', 'pg_ctl.exe', 'pg_isready.exe', 'psql.exe')) {
        $binary = Join-Path $resolved $name
        if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw "Required PostgreSQL binary is missing: $name" }
        $item = Get-Item -LiteralPath $binary -Force -ErrorAction Stop
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint -or $item.PSIsContainer) {
            throw "Required PostgreSQL binary must be a regular file: $name"
        }
    }
    # Keep the caller's approved logical path for pg_ctl ownership comparison:
    # Windows reports the path used to start a junction-backed executable,
    # while the physical target above is used only to validate its contents.
    return $requested
}

function Get-MigrationPostgresListenerInfo {
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$Port)

    $connections = @()
    if (Get-Command 'Get-NetTCPConnection' -ErrorAction SilentlyContinue) {
        $connections = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    }
    if ($connections.Count -gt 0) {
        $owners = @($connections | ForEach-Object { [int]$_.OwningProcess } | Sort-Object -Unique)
        return [pscustomobject]@{
            Occupied = $true
            ProcessIds = $owners
            LocalAddresses = @($connections | ForEach-Object { [string]$_.LocalAddress } | Sort-Object -Unique)
        }
    }

    $probe = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $probe.BeginConnect('127.0.0.1', $Port, $null, $null)
        if ($pending.AsyncWaitHandle.WaitOne(300) -and $probe.Connected) {
            return [pscustomobject]@{ Occupied = $true; ProcessIds = @(); LocalAddresses = @('127.0.0.1') }
        }
    } finally { $probe.Dispose() }
    return [pscustomobject]@{ Occupied = $false; ProcessIds = @(); LocalAddresses = @() }
}

function Get-MigrationPostgresStalePidRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$DataDirectory,
        [Parameter(Mandatory)][int]$Port
    )

    $pidPath = Join-Path $DataDirectory 'postmaster.pid'
    if (-not (Test-Path -LiteralPath $pidPath -PathType Leaf)) { return $null }
    Assert-MigrationPathNoReparseAncestors -Path $pidPath
    $item = Get-Item -LiteralPath $pidPath -Force -ErrorAction Stop
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The PostgreSQL PID file must be a regular file before stale-file recovery.'
    }
    if ($item.Length -le 0 -or $item.Length -gt 4096) {
        throw 'The PostgreSQL PID file has an invalid size; preserving it and refusing automatic recovery.'
    }

    $bytes = [IO.File]::ReadAllBytes($pidPath)
    try {
        $text = [Text.UTF8Encoding]::new($false, $true).GetString($bytes)
    } catch {
        throw 'The PostgreSQL PID file is not valid UTF-8; preserving it and refusing automatic recovery.'
    }
    $lines = @($text -split "`r?`n")
    if ($lines.Count -lt 4 -or $lines[0] -notmatch '^\d+$' -or
        $lines[1] -notmatch '\S' -or $lines[2] -notmatch '^\d+$' -or $lines[3] -notmatch '^\d+$') {
        throw 'The PostgreSQL PID file is incomplete or malformed; preserving it and refusing automatic recovery.'
    }
    $recordedPid = 0
    $recordedPort = 0
    if (-not [int]::TryParse($lines[0], [ref]$recordedPid) -or $recordedPid -le 0 -or
        -not [int]::TryParse($lines[3], [ref]$recordedPort) -or $recordedPort -ne $Port) {
        throw 'The PostgreSQL PID file does not identify the expected isolated port; preserving it.'
    }
    try {
        $recordedDataDirectory = [IO.Path]::GetFullPath($lines[1].Replace('/', '\')).TrimEnd('\')
        $expectedDataDirectory = [IO.Path]::GetFullPath($DataDirectory).TrimEnd('\')
    } catch {
        throw 'The PostgreSQL PID file contains an invalid data-directory identity; preserving it.'
    }
    if (-not [string]::Equals($recordedDataDirectory, $expectedDataDirectory, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The PostgreSQL PID file belongs to a different data directory; preserving it.'
    }

    $recordedProcess = $null
    try {
        $recordedProcess = [Diagnostics.Process]::GetProcessById($recordedPid)
        if (-not $recordedProcess.HasExited) {
            throw 'The PostgreSQL PID is still present in the process table; preserving the PID file.'
        }
    } catch [ArgumentException] {
        # GetProcessById uses ArgumentException only when the PID is absent.
    } finally {
        if ($null -ne $recordedProcess) { $recordedProcess.Dispose() }
    }

    return [pscustomobject]@{
        ProcessId = $recordedPid
        Snapshot = [Convert]::ToBase64String($bytes)
    }
}

function Get-MigrationPostgresClusterState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$DataDirectory,
        [Parameter(Mandatory)][string]$PostgresBin
    )

    $pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
    $output = @(& $pgCtl -D $DataDirectory status 2>&1 | ForEach-Object { [string]$_ })
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        # pg_ctl status exit code 3 means "no server running"; its text is localized.
        if ($exitCode -ne 3 -and (($output -join ' ') -notmatch '(?i)no server running|PID file .* does not exist')) {
            throw 'pg_ctl could not safely determine whether the owned PostgreSQL cluster is running.'
        }
        $stalePid = Get-MigrationPostgresStalePidRecord -DataDirectory $DataDirectory -Port 55434
        return [pscustomobject]@{
            Running = $false
            Owned = $false
            ProcessId = $null
            StalePidFile = ($null -ne $stalePid)
            StalePidProcessId = $(if ($null -ne $stalePid) { $stalePid.ProcessId } else { 0 })
            StalePidHash = $(if ($null -ne $stalePid) { $stalePid.Snapshot } else { '' })
        }
    }

    $match = [regex]::Match(($output -join ' '), '(?i)PID\s*:\s*(?<pid>\d+)')
    if (-not $match.Success) { throw 'pg_ctl reported a running PostgreSQL cluster without a verifiable process ID.' }
    $processId = [int]$match.Groups['pid'].Value
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    $expectedExecutable = [IO.Path]::GetFullPath((Join-Path $PostgresBin 'postgres.exe'))
    $owned = $false
    if ($null -ne $process -and -not [string]::IsNullOrWhiteSpace($process.Path)) {
        $actualExecutable = [IO.Path]::GetFullPath($process.Path)
        $owned = [string]::Equals($actualExecutable, $expectedExecutable, [StringComparison]::OrdinalIgnoreCase)
    }
    return [pscustomobject]@{
        Running = $true
        Owned = $owned
        ProcessId = $processId
        StalePidFile = $false
        StalePidProcessId = 0
        StalePidHash = ''
    }
}

function Move-MigrationPostgresStalePidToQuarantine {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$DataDirectory,
        [Parameter(Mandatory)][string]$PostgresBin,
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][int]$RecordedProcessId,
        [Parameter(Mandatory)][string]$ExpectedSnapshot
    )

    $state = Get-MigrationPostgresClusterState -DataDirectory $DataDirectory -PostgresBin $PostgresBin
    if ($state.Running -or -not $state.StalePidFile -or $state.StalePidProcessId -ne $RecordedProcessId -or
        $state.StalePidHash -cne $ExpectedSnapshot) {
        throw 'PostgreSQL state changed before stale PID quarantine; preserving all files.'
    }
    $listener = Get-MigrationPostgresListenerInfo -Port $Port
    if ($listener.Occupied) {
        throw "PostgreSQL port $Port became occupied before stale PID quarantine; preserving the PID file."
    }

    $record = Get-MigrationPostgresStalePidRecord -DataDirectory $DataDirectory -Port $Port
    if ($null -eq $record -or $record.ProcessId -ne $RecordedProcessId -or $record.Snapshot -cne $ExpectedSnapshot) {
        throw 'The PostgreSQL PID file changed before quarantine; preserving it.'
    }
    $pidPath = Join-Path $DataDirectory 'postmaster.pid'
    $timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ', [Globalization.CultureInfo]::InvariantCulture)
    $archivePath = "$pidPath.stale-$RecordedProcessId-$timestamp-$([Guid]::NewGuid().ToString('N'))"
    [IO.File]::Move($pidPath, $archivePath)
    $archivedBytes = [IO.File]::ReadAllBytes($archivePath)
    if ([Convert]::ToBase64String($archivedBytes) -cne $ExpectedSnapshot) {
        throw 'The quarantined PostgreSQL PID evidence did not match the validated original bytes.'
    }
    return $archivePath
}

function Start-MigrationPostgresCluster {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$DataDirectory,
        [Parameter(Mandatory)][string]$PostgresBin,
        [Parameter(Mandatory)][string]$LogPath,
        [Parameter(Mandatory)][int]$Port
    )

    $pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
    $options = "-c listen_addresses=127.0.0.1 -c port=$Port -c password_encryption=scram-sha-256"
    & $pgCtl -D $DataDirectory -l $LogPath -o $options -w start 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the already-initialized isolated PostgreSQL cluster; inspect its log.' }
}

function Wait-MigrationPostgresReady {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$PostgresBin,
        [Parameter(Mandatory)][int]$Port,
        [int]$TimeoutSeconds = 30
    )

    $ready = Join-Path $PostgresBin 'pg_isready.exe'
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        & $ready -q -h '127.0.0.1' -p $Port -U 'postgres' -d 'postgres' 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { return $true }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    return $false
}

function Invoke-MigrationPostgresReadOnlyQuery {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$PsqlPath,
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$User,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Sql
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', [EnvironmentVariableTarget]::Process)
    $previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
    try {
        $env:PGPASSWORD = $Password
        $PSNativeCommandUseErrorActionPreference = $false
        $output = $Sql | & $PsqlPath -q -X -w -h '127.0.0.1' -p $Port -U $User -d $Database `
            -v 'ON_ERROR_STOP=1' -A -t 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL rejected a read-only migration identity check.' }
        return (@($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
    } finally {
        $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
        if ($null -eq $previousPassword) {
            Remove-Item -LiteralPath 'Env:PGPASSWORD' -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, [EnvironmentVariableTarget]::Process)
        }
    }
}

function Test-MigrationPostgresIdentity {
    [CmdletBinding()]
    param([Parameter(Mandatory)][pscustomobject]$Context)

    $psql = Join-Path $Context.PostgresBin 'psql.exe'
    $adminPassword = Read-MigrationPrivateEnvValue -Path $Context.AdminEnvironmentFile -Name 'POSTGRES_SUPERUSER_PASSWORD'
    $pluginPassword = Read-MigrationPrivateEnvValue -Path $Context.PluginEnvironmentFile -Name 'POSTGRES_PASSWORD'
    if ([string]::IsNullOrWhiteSpace($adminPassword) -or [string]::IsNullOrWhiteSpace($pluginPassword)) {
        throw 'Private PostgreSQL credentials are missing or empty; no replacement credentials were created.'
    }

    $clusterQuery = @'
BEGIN READ ONLY;
SELECT current_setting('data_directory') || '|' || current_setting('port') || '|' ||
       current_setting('listen_addresses') || '|' || current_setting('password_encryption');
COMMIT;
'@
    $clusterIdentity = Invoke-MigrationPostgresReadOnlyQuery -PsqlPath $psql -Port $Context.Port `
        -User 'postgres' -Password $adminPassword -Database 'postgres' -Sql $clusterQuery
    $clusterParts = $clusterIdentity -split '\|', 4
    if ($clusterParts.Count -ne 4) { throw 'PostgreSQL returned an incomplete cluster identity.' }
    $actualData = [IO.Path]::GetFullPath($clusterParts[0].Replace('/', '\')).TrimEnd('\')
    $expectedData = [IO.Path]::GetFullPath($Context.DataDirectory).TrimEnd('\')
    if (-not [string]::Equals($actualData, $expectedData, [StringComparison]::OrdinalIgnoreCase) -or
        $clusterParts[1] -cne [string]$Context.Port -or
        $clusterParts[2] -cne '127.0.0.1' -or
        $clusterParts[3] -cne 'scram-sha-256') {
        throw 'The authenticated PostgreSQL cluster does not match the owned data path, loopback endpoint, port, and SCRAM configuration.'
    }

    $roleSql = "BEGIN READ ONLY; SELECT rolcanlogin::text || '|' || rolsuper::text || '|' || rolcreatedb::text || '|' || rolcreaterole::text FROM pg_roles WHERE rolname='$($Context.Role)'; COMMIT;"
    $roleState = Invoke-MigrationPostgresReadOnlyQuery -PsqlPath $psql -Port $Context.Port `
        -User 'postgres' -Password $adminPassword -Database 'postgres' -Sql $roleSql
    if ($roleState -cne 'true|false|false|false') { throw 'The isolated migration database role is absent or has unexpected privileges.' }

    $databaseSql = "BEGIN READ ONLY; SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='$($Context.Database)'; COMMIT;"
    $databaseOwner = Invoke-MigrationPostgresReadOnlyQuery -PsqlPath $psql -Port $Context.Port `
        -User 'postgres' -Password $adminPassword -Database 'postgres' -Sql $databaseSql
    if ($databaseOwner -cne $Context.Role) { throw 'The isolated migration database is not owned by the expected role.' }

    $schemaSql = "BEGIN READ ONLY; SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='$($Context.Schema)'; COMMIT;"
    $schemaOwner = Invoke-MigrationPostgresReadOnlyQuery -PsqlPath $psql -Port $Context.Port `
        -User 'postgres' -Password $adminPassword -Database $Context.Database -Sql $schemaSql
    if ($schemaOwner -cne $Context.Role) { throw 'The isolated migration schema is not owned by the expected role.' }

    $invalidRulesSql = @'
BEGIN READ ONLY;
SELECT COUNT(*)::text FROM pg_hba_file_rules
WHERE error IS NOT NULL OR
      (type LIKE 'host%' AND
       (auth_method <> 'scram-sha-256' OR
        COALESCE(NOT ((address = '127.0.0.1' AND netmask = '255.255.255.255') OR
                      (address = '::1' AND netmask = 'ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff')), TRUE)));
COMMIT;
'@
    $invalidRules = Invoke-MigrationPostgresReadOnlyQuery -PsqlPath $psql -Port $Context.Port `
        -User 'postgres' -Password $adminPassword -Database 'postgres' -Sql $invalidRulesSql
    if ($invalidRules -cne '0') { throw 'PostgreSQL host rules must be valid loopback-only SCRAM-SHA-256 rules.' }

    $applicationIdentitySql = 'BEGIN READ ONLY; SELECT current_database() || ''|'' || current_user || ''|'' || COALESCE(current_schema(), '''') || ''|'' || current_setting(''search_path''); COMMIT;'
    $applicationIdentity = Invoke-MigrationPostgresReadOnlyQuery -PsqlPath $psql -Port $Context.Port `
        -User $Context.Role -Password $pluginPassword -Database $Context.Database -Sql $applicationIdentitySql
    $expectedIdentity = "$($Context.Database)|$($Context.Role)|$($Context.Schema)|$($Context.Schema), public"
    if ($applicationIdentity -cne $expectedIdentity) {
        throw 'The private plugin credential does not authenticate to the isolated database, role, and schema.'
    }
    return $true
}

function Invoke-MigrationPostgresLifecycleOperation {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][object[]]$Arguments,
        [hashtable]$Operations
    )

    if ($null -ne $Operations) {
        $operation = $Operations[$Name]
        if ($null -eq $operation) { throw "The PostgreSQL lifecycle test operation '$Name' is missing." }
        return & $operation @Arguments
    }
    switch ($Name) {
        'GetBinaryVersion' {
            $text = @(& (Join-Path $Arguments[0] 'postgres.exe') '--version' 2>&1 | ForEach-Object { [string]$_ }) -join ' '
            if ($LASTEXITCODE -ne 0) { throw 'The configured PostgreSQL binary did not report its version.' }
            return $text
        }
        'GetClusterState' { return Get-MigrationPostgresClusterState -DataDirectory $Arguments[0] -PostgresBin $Arguments[1] }
        'GetListenerInfo' { return Get-MigrationPostgresListenerInfo -Port $Arguments[0] }
        'QuarantineStalePid' {
            return Move-MigrationPostgresStalePidToQuarantine -DataDirectory $Arguments[0] -PostgresBin $Arguments[4] `
                -Port $Arguments[1] -RecordedProcessId $Arguments[2] -ExpectedSnapshot $Arguments[3]
        }
        'StartCluster' {
            Start-MigrationPostgresCluster -DataDirectory $Arguments[0] -PostgresBin $Arguments[1] -LogPath $Arguments[2] -Port $Arguments[3]
            return
        }
        'WaitReady' { return Wait-MigrationPostgresReady -PostgresBin $Arguments[0] -Port $Arguments[1] -TimeoutSeconds $Arguments[2] }
        'ValidateIdentity' { return Test-MigrationPostgresIdentity -Context $Arguments[0] }
        default { throw "Unknown PostgreSQL lifecycle operation: $Name" }
    }
}

function Ensure-MigrationPostgresReady {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$WorkspaceRoot,
        [Parameter(Mandatory)][string]$RuntimeDirectory,
        [Parameter(Mandatory)][string]$PostgresBin,
        [Parameter(Mandatory)][switch]$StartIfStopped,
        [int]$Port = 55434,
        [string]$Database = 'copimine_migration_test',
        [string]$Schema = 'copimine_migration_26_3_candidate',
        [string]$Role = 'copimine_migration_test',
        [hashtable]$Operations
    )

    $root = (Resolve-Path -LiteralPath $WorkspaceRoot -ErrorAction Stop).Path
    $expectedRuntime = [IO.Path]::GetFullPath((Join-Path $root 'local-runtime/end-rift-server-26.3'))
    $runtime = (Resolve-Path -LiteralPath $RuntimeDirectory -ErrorAction Stop).Path
    if (-not [string]::Equals($runtime.TrimEnd('\'), $expectedRuntime.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The migration PostgreSQL runtime must remain inside local-runtime/end-rift-server-26.3.'
    }
    if ($Port -ne 55434 -or $Database -cne 'copimine_migration_test' -or
        $Schema -cne 'copimine_migration_26_3_candidate' -or $Role -cne 'copimine_migration_test') {
        throw 'The migration PostgreSQL identity must remain on the canonical isolated endpoint, database, schema, and role.'
    }
    foreach ($path in @($root, $runtime)) { Assert-MigrationPathNoReparseAncestors -Path $path }

    $data = Join-Path $runtime 'postgres-data'
    $versionPath = Join-Path $data 'PG_VERSION'
    $adminEnvironment = Join-Path $runtime 'migration-postgres-admin.env'
    $pluginEnvironment = Join-Path $runtime 'migration-isolated-postgres.env'
    $logPath = Join-Path (Join-Path $runtime 'logs') 'postgres-migration.log'
    foreach ($path in @($data, $versionPath, $adminEnvironment, $pluginEnvironment, (Split-Path -Parent $logPath))) {
        Assert-MigrationPathNoReparseAncestors -Path $path
    }
    if (-not (Test-Path -LiteralPath $data -PathType Container)) {
        throw 'The existing isolated PostgreSQL data directory is missing; automatic provisioning is disabled.'
    }
    foreach ($directory in @($runtime, $data)) {
        $item = Get-Item -LiteralPath $directory -Force -ErrorAction Stop
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Owned PostgreSQL directories cannot be reparse points.' }
    }
    if (-not (Test-Path -LiteralPath $versionPath -PathType Leaf)) {
        if (@(Get-ChildItem -LiteralPath $data -Force -ErrorAction Stop).Count -gt 0) {
            throw 'The PostgreSQL data directory is non-empty without PG_VERSION; preserving it and refusing initialization.'
        }
        throw 'The isolated PostgreSQL cluster has not been initialized; invoke the explicit initializer only for a new empty fixture.'
    }
    $versionText = [IO.File]::ReadAllText($versionPath).Trim()
    if ($versionText -notmatch '^\d+$' -or [int]$versionText -lt 16) {
        throw 'The owned PostgreSQL data directory does not contain a valid PostgreSQL 16+ PG_VERSION.'
    }

    $postgresBinResolved = Resolve-MigrationPostgresBinaryDirectory -WorkspaceRoot $root `
        -PostgresBin $PostgresBin -Operations $Operations
    $binaryVersion = Invoke-MigrationPostgresLifecycleOperation -Name 'GetBinaryVersion' `
        -Arguments @($postgresBinResolved) -Operations $Operations
    if ($binaryVersion -notmatch '(?<major>\d+)\.' -or [int]$Matches.major -lt 16) {
        throw 'PostgreSQL 16 or newer binaries are required for the isolated migration database.'
    }

    foreach ($file in @($adminEnvironment, $pluginEnvironment)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
            throw 'An existing isolated PostgreSQL credential file is missing; it will not be regenerated.'
        }
        $item = Get-Item -LiteralPath $file -Force -ErrorAction Stop
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint -or $item.PSIsContainer) {
            throw 'Existing migration PostgreSQL credentials must be regular private files.'
        }
    }
    $required = @(
        'POSTGRES_HOST=127.0.0.1', "POSTGRES_PORT=$Port", "POSTGRES_DB=$Database",
        "POSTGRES_SCHEMA=$Schema", "POSTGRES_USER=$Role"
    )
    $null = Assert-MigrationDatabaseEnvironment -Path $pluginEnvironment -RequiredSettings $required
    if ([string]::IsNullOrWhiteSpace((Read-MigrationPrivateEnvValue -Path $adminEnvironment -Name 'POSTGRES_SUPERUSER_PASSWORD'))) {
        throw 'The existing PostgreSQL administrator credential is missing or empty; it will not be regenerated.'
    }

    $state = Invoke-MigrationPostgresLifecycleOperation -Name 'GetClusterState' `
        -Arguments @($data, $postgresBinResolved) -Operations $Operations
    $startedByThisCall = $false
    if ($state.Running) {
        if (-not $state.Owned -or $null -eq $state.ProcessId) {
            throw 'A PostgreSQL process is running for this data path but its executable ownership cannot be verified.'
        }
    } else {
        $listener = Invoke-MigrationPostgresLifecycleOperation -Name 'GetListenerInfo' `
            -Arguments @($Port) -Operations $Operations
        if ($listener.Occupied) {
            throw "PostgreSQL port $Port is occupied by a process that does not own the configured cluster; refusing to start."
        }
        if (-not $StartIfStopped) {
            throw 'The owned PostgreSQL cluster is stopped; read-only validation does not start services.'
        }
        if (-not (Test-Path -LiteralPath (Split-Path -Parent $logPath) -PathType Container)) {
            throw 'The existing PostgreSQL log directory is missing; automatic directory creation is disabled.'
        }
        if ($state.StalePidFile) {
            Invoke-MigrationPostgresLifecycleOperation -Name 'QuarantineStalePid' `
                -Arguments @($data, $Port, $state.StalePidProcessId, $state.StalePidHash, $postgresBinResolved) -Operations $Operations
            $state = Invoke-MigrationPostgresLifecycleOperation -Name 'GetClusterState' `
                -Arguments @($data, $postgresBinResolved) -Operations $Operations
            if ($state.Running -or $state.StalePidFile) {
                throw 'PostgreSQL did not reach a stopped state without a stale PID file after quarantine; start was cancelled.'
            }
        }
        Invoke-MigrationPostgresLifecycleOperation -Name 'StartCluster' `
            -Arguments @($data, $postgresBinResolved, $logPath, $Port) -Operations $Operations
        $startedByThisCall = $true
        $state = Invoke-MigrationPostgresLifecycleOperation -Name 'GetClusterState' `
            -Arguments @($data, $postgresBinResolved) -Operations $Operations
        if (-not $state.Running -or -not $state.Owned -or $null -eq $state.ProcessId) {
            throw 'PostgreSQL start completed without a verifiable owner process for the existing data directory.'
        }
    }

    $listener = Invoke-MigrationPostgresLifecycleOperation -Name 'GetListenerInfo' `
        -Arguments @($Port) -Operations $Operations
    if (-not $listener.Occupied) { throw 'The owned PostgreSQL process has no listener on its required port.' }
    if (@($listener.ProcessIds).Count -gt 0 -and @($listener.ProcessIds | Where-Object { [int]$_ -ne [int]$state.ProcessId }).Count -gt 0) {
        throw 'The required PostgreSQL port is also owned by another process; refusing an ambiguous cluster identity.'
    }
    $ready = Invoke-MigrationPostgresLifecycleOperation -Name 'WaitReady' `
        -Arguments @($postgresBinResolved, $Port, 30) -Operations $Operations
    if (-not $ready) { throw 'The owned isolated PostgreSQL cluster is not accepting loopback connections.' }

    $context = [pscustomobject]@{
        DataDirectory = $data
        PostgresBin = $postgresBinResolved
        AdminEnvironmentFile = $adminEnvironment
        PluginEnvironmentFile = $pluginEnvironment
        Port = $Port
        Database = $Database
        Schema = $Schema
        Role = $Role
    }
    $identityAttempt = 0
    while ($true) {
        try {
            $identityValid = Invoke-MigrationPostgresLifecycleOperation -Name 'ValidateIdentity' `
                -Arguments @($context) -Operations $Operations
            break
        } catch {
            # pg_ctl -w returns before a just-started cluster accepts authenticated
            # queries on some Windows hosts; only retry an owned start we initiated.
            $identityAttempt++
            if (-not $startedByThisCall -or $identityAttempt -ge 20) { throw }
            Start-Sleep -Milliseconds 250
        }
    }
    if (-not $identityValid) { throw 'The existing PostgreSQL cluster failed read-only identity validation.' }

    return [pscustomobject]@{
        Ready = $true
        StartedByThisCall = $startedByThisCall
        Endpoint = "127.0.0.1:$Port"
        Database = $Database
        Schema = $Schema
        Role = $Role
        PostgreSqlVersion = $binaryVersion
    }
}

Export-ModuleMember -Function Assert-MigrationPostgresNewClusterDirectory, Ensure-MigrationPostgresReady
