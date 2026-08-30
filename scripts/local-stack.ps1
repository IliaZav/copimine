[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Runtime = Join-Path $Root 'local-runtime'
$Logs = Join-Path $Runtime 'logs'
$Pids = Join-Path $Runtime 'pids'
$Downloads = Join-Path $Runtime 'downloads'
$PostgresRoot = Join-Path $Runtime 'postgresql'
$PostgresData = Join-Path $Runtime 'postgres-data'
$LocalEnvFile = Join-Path $Runtime 'local.env'
$PropertiesBackup = Join-Path $Runtime 'server.properties.before-local-stack'
$VoiceChatPort = 24455
$VoiceChatPropertiesBackup = Join-Path $Runtime 'voicechat-server.properties.before-local-stack'
$ServerDir = Join-Path $Root 'minecraft\server'
$AdminWebDir = Join-Path $Root 'admin-web'
$WebsitePort = 8090
$MinecraftPort = 25565
$RconPort = 25575
$PostgresPort = 55432
$MinecraftJvmXms = if ($env:MC_JVM_XMS) { $env:MC_JVM_XMS.Trim() } else { '512M' }
$MinecraftJvmXmx = if ($env:MC_JVM_XMX) { $env:MC_JVM_XMX.Trim() } else { '1536M' }
$PostgresDownloadUrl = 'https://get.enterprisedb.com/postgresql/postgresql-16.8-1-windows-x64-binaries.zip'
$PostgresZip = Join-Path $Downloads 'postgresql-16.8-1-windows-x64-binaries.zip'
$PostgresLog = Join-Path $Logs 'postgresql.log'
$WebsiteOutLog = Join-Path $Logs 'website.stdout.log'
$WebsiteErrLog = Join-Path $Logs 'website.stderr.log'
$MinecraftOutLog = Join-Path $Logs 'minecraft.stdout.log'
$MinecraftErrLog = Join-Path $Logs 'minecraft.stderr.log'
$StackLog = Join-Path $Logs 'stack.log'

foreach ($directory in @($Runtime, $Logs, $Pids, $Downloads)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

function Get-Sha1Hex {
    param([Parameter(Mandatory = $true)][string]$Path)
    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        return ([BitConverter]::ToString($sha1.ComputeHash($stream)) -replace '-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
        $sha1.Dispose()
    }
}

function Write-StackLog {
    param([string]$Message)
    $line = "{0} {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Add-Content -LiteralPath $StackLog -Value $line -Encoding UTF8
    Write-Host $line
}

function Read-KeyValueFile {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }
    foreach ($line in (Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue)) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $values[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
    return $values
}

function New-RandomHex {
    param([int]$Bytes = 32)
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($buffer) -replace '-', '').ToLowerInvariant()
}

function Write-LocalEnvironment {
    $values = Read-KeyValueFile -Path $LocalEnvFile
    if (-not $values['POSTGRES_PASSWORD']) { $values['POSTGRES_PASSWORD'] = New-RandomHex }
    if (-not $values['SECRET_KEY']) { $values['SECRET_KEY'] = New-RandomHex }
    if (-not $values['PLUGIN_API_KEY']) { $values['PLUGIN_API_KEY'] = New-RandomHex }
    if (-not $values['RCON_PASSWORD']) { $values['RCON_PASSWORD'] = New-RandomHex }

    $adminUsersJson = '{"localadmin":{"password_hash":"plain:localadmin123","role":"owner","enabled":true}}'
    $serverLog = (Join-Path $ServerDir 'logs\latest.log')
    $lines = @(
        'POSTGRES_HOST=127.0.0.1',
        "POSTGRES_PORT=$PostgresPort",
        'POSTGRES_DB=copimine',
        'POSTGRES_USER=copimine',
        "POSTGRES_PASSWORD=$($values['POSTGRES_PASSWORD'])",
        'POSTGRES_SCHEMA=copimine',
        "DATABASE_URL=postgresql://copimine:$($values['POSTGRES_PASSWORD'])@127.0.0.1:$PostgresPort/copimine?schema=copimine",
        'COPIMINE_AUTH_STORAGE=postgresql',
        "COPIMINE_ENV_FILE=$LocalEnvFile",
        "MC_SERVER_DIR=$ServerDir",
        "MC_WORLD_DIR=$(Join-Path $ServerDir 'CopiMine')",
        'MC_HOST=127.0.0.1',
        "MC_PORT=$MinecraftPort",
        'MC_PUBLIC_ADDRESS=127.0.0.1:25565',
        'RCON_HOST=127.0.0.1',
        "RCON_PORT=$RconPort",
        "RCON_PASSWORD=$($values['RCON_PASSWORD'])",
        "MC_LOG_FILE=$serverLog",
        "COREPROTECT_DB=$(Join-Path $ServerDir 'plugins\CoreProtect\database.db')",
        "ADMIN_PLUGIN_DB=$(Join-Path $ServerDir 'plugins\CopiMineUltimateAdminPlus\copimine_ultimate.db')",
        "COPIMINE_ADMIN_DATA=$(Join-Path $Runtime 'admin-data')",
        "COPIMINE_AUTH_DB=$(Join-Path $Runtime 'admin-auth.sqlite3')",
        "COPIMINE_BACKUPS_DIR=$(Join-Path $Runtime 'backups')",
        "SECRET_KEY=$($values['SECRET_KEY'])",
        "PLUGIN_API_KEY=$($values['PLUGIN_API_KEY'])",
        'ADMIN_PUBLIC_BASE_URL=http://127.0.0.1:8090',
        'BACKEND_INTERNAL_BASE_URL=http://127.0.0.1:8090',
        'ALLOW_INSECURE_HTTP_AUTH=1',
        'REQUIRE_OP_FOR_LOGIN=0',
        'REQUIRE_WHITELIST_FOR_LOGIN=0',
        'COPIMINE_STARTUP_STRICT=0',
        'DB_WRITE_ENABLED=1',
        'ADMIN_DB_WRITE_ALLOWLIST=localadmin',
        'ALLOW_PLAIN_ADMIN_PASSWORDS=1',
        "ADMIN_USERS_JSON=$adminUsersJson",
        'RCON_WEB_COMMAND_ALLOWLIST=list,tps,mspt,say,save-all,time query,weather query',
        'COPIMINE_SYSTEMD_SERVICES=',
        'DISCORD_BOT_TOKEN=',
        'DISCORD_GUILD_ID=',
        'DISCORD_APPLICATIONS_CHANNEL_ID=',
        'DISCORD_REPORTS_CHANNEL_ID='
    )
    $content = ($lines -join [Environment]::NewLine) + [Environment]::NewLine
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($LocalEnvFile, $content, $utf8)
    return (Read-KeyValueFile -Path $LocalEnvFile)
}

function Test-TcpPort {
    param([string]$TargetHost, [int]$Port, [int]$TimeoutMs = 700)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch { return $false } finally { $client.Close() }
}

function Wait-TcpPort {
    param([string]$TargetHost, [int]$Port, [bool]$Expected = $true, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if ((Test-TcpPort -TargetHost $TargetHost -Port $Port) -eq $Expected) { return $true }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Get-PidFilePath {
    param([string]$Name)
    return (Join-Path $Pids "$Name.pid")
}

function Read-PidFile {
    param([string]$Name)
    $path = Get-PidFilePath -Name $Name
    if (-not (Test-Path -LiteralPath $path)) { return 0 }
    $processId = 0
    [int]::TryParse((Get-Content -LiteralPath $path -Raw).Trim(), [ref]$processId) | Out-Null
    return $processId
}

function Write-PidFile {
    param([string]$Name, [int]$ProcessId)
    Set-Content -LiteralPath (Get-PidFilePath -Name $Name) -Value $ProcessId -Encoding ASCII
}

function Remove-PidFile {
    param([string]$Name)
    Remove-Item -LiteralPath (Get-PidFilePath -Name $Name) -Force -ErrorAction SilentlyContinue
}

function Get-ProcessIfAlive {
    param([int]$ProcessId)
    if ($ProcessId -le 0) { return $null }
    return Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
}

function Stop-ProcessTree {
    param([int]$ProcessId)
    if ($ProcessId -le 0) { return }
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) { Stop-ProcessTree -ProcessId ([int]$child.ProcessId) }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Read-ServerProperty {
    param([string]$Key)
    if (-not (Test-Path -LiteralPath (Join-Path $ServerDir 'server.properties'))) { return '' }
    foreach ($line in Get-Content -LiteralPath (Join-Path $ServerDir 'server.properties')) {
        if ($line -match ('^\s*' + [regex]::Escape($Key) + '=(.*)$')) { return $matches[1] }
    }
    return ''
}

function Set-ServerProperty {
    param([string]$Key, [string]$Value)
    $path = Join-Path $ServerDir 'server.properties'
    $pattern = '^\s*' + [regex]::Escape($Key) + '='
    $found = $false
    $lines = @()
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match $pattern) {
            $lines += "$Key=$Value"
            $found = $true
        } else {
            $lines += $line
        }
    }
    if (-not $found) { $lines += "$Key=$Value" }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
}

function Set-VoiceChatProperty {
    param([string]$Key, [string]$Value)
    $path = Join-Path $ServerDir 'plugins\voicechat\voicechat-server.properties'
    if (-not (Test-Path -LiteralPath $path)) { throw "Simple Voice Chat properties are missing: $path" }
    $pattern = '^\s*' + [regex]::Escape($Key) + '='
    $found = $false
    $lines = @()
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match $pattern) {
            $lines += "$Key=$Value"
            $found = $true
        } else {
            $lines += $line
        }
    }
    if (-not $found) { $lines += "$Key=$Value" }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
}

function Backup-AndPatchServerProperties {
    $path = Join-Path $ServerDir 'server.properties'
    if (-not (Test-Path -LiteralPath $path)) { throw "Minecraft server.properties is missing: $path" }
    if (Test-Path -LiteralPath $PropertiesBackup) {
        Copy-Item -LiteralPath $PropertiesBackup -Destination $path -Force
        Remove-Item -LiteralPath $PropertiesBackup -Force
    }
    Copy-Item -LiteralPath $path -Destination $PropertiesBackup -Force
    Set-ServerProperty -Key 'enable-rcon' -Value 'true'
    Set-ServerProperty -Key 'rcon.port' -Value ([string]$RconPort)
    Set-ServerProperty -Key 'rcon.password' -Value $script:LocalValues['RCON_PASSWORD']
    Set-ServerProperty -Key 'require-resource-pack' -Value 'false'
    Set-ServerProperty -Key 'resource-pack' -Value ''
    Set-ServerProperty -Key 'resource-pack-sha1' -Value ''
}

function Backup-AndPatchVoiceChatProperties {
    $path = Join-Path $ServerDir 'plugins\voicechat\voicechat-server.properties'
    if (-not (Test-Path -LiteralPath $path)) { throw "Simple Voice Chat properties are missing: $path" }
    if (Test-Path -LiteralPath $VoiceChatPropertiesBackup) {
        Copy-Item -LiteralPath $VoiceChatPropertiesBackup -Destination $path -Force
        Remove-Item -LiteralPath $VoiceChatPropertiesBackup -Force
    }
    Copy-Item -LiteralPath $path -Destination $VoiceChatPropertiesBackup -Force
    Set-VoiceChatProperty -Key 'port' -Value ([string]$VoiceChatPort)
}

function Restore-ServerProperties {
    if (Test-Path -LiteralPath $PropertiesBackup) {
        Copy-Item -LiteralPath $PropertiesBackup -Destination (Join-Path $ServerDir 'server.properties') -Force
        Remove-Item -LiteralPath $PropertiesBackup -Force
        Write-StackLog 'Restored minecraft/server/server.properties.'
    }
}

function Resolve-PostgresBinaries {
    $candidate = Join-Path $PostgresRoot 'pgsql\bin\postgres.exe'
    if (Test-Path -LiteralPath $candidate) { return (Split-Path $candidate -Parent) }
    $found = Get-ChildItem -LiteralPath $PostgresRoot -Filter 'postgres.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { return $found.Directory.FullName }

    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) { throw 'curl.exe is required to download portable PostgreSQL, but it was not found.' }
    Write-StackLog "Downloading portable PostgreSQL 16.8 to $PostgresZip (first start only)."
    & $curl.Source --silent --show-error -L --fail --retry 3 --retry-delay 2 --output $PostgresZip $PostgresDownloadUrl 2>&1 | Tee-Object -FilePath $StackLog -Append | Out-Host
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $PostgresZip)) { throw 'Portable PostgreSQL download failed. See local-runtime/logs/stack.log.' }
    Write-StackLog 'Extracting portable PostgreSQL.'
    Expand-Archive -LiteralPath $PostgresZip -DestinationPath $PostgresRoot -Force
    $found = Get-ChildItem -LiteralPath $PostgresRoot -Filter 'postgres.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $found) { throw 'Downloaded PostgreSQL archive did not contain postgres.exe.' }
    return $found.Directory.FullName
}

function Invoke-Psql {
    param(
        [string]$BinDir,
        [string]$Database,
        [string]$User,
        [string]$Sql,
        [switch]$AllowFailure
    )
    $psql = Join-Path $BinDir 'psql.exe'
    $previousPassword = $env:PGPASSWORD
    if ($User -eq 'postgres') { $env:PGPASSWORD = '' } else { $env:PGPASSWORD = $script:LocalValues['POSTGRES_PASSWORD'] }
    $token = [Guid]::NewGuid().ToString('N')
    $sqlFile = Join-Path $Runtime ("sql-$token.sql")
    $outFile = Join-Path $Runtime ("sql-$token.out")
    $errFile = Join-Path $Runtime ("sql-$token.err")
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($sqlFile, $Sql, $utf8)
    try {
        # Use redirected files instead of a PowerShell native stderr pipeline.
        # Windows PowerShell promotes psql NOTICE/WARNING output to terminating
        # ErrorRecords when ErrorActionPreference is Stop, and psql's log file
        # is held open by postgres itself.
        $psqlProcess = Start-Process -FilePath $psql -ArgumentList @('-h', '127.0.0.1', '-p', [string]$PostgresPort, '-U', $User, '-d', $Database, '-v', 'ON_ERROR_STOP=1', '-f', $sqlFile) -RedirectStandardOutput $outFile -RedirectStandardError $errFile -WindowStyle Hidden -Wait -PassThru
        $output = @()
        if (Test-Path -LiteralPath $outFile) { $output += @(Get-Content -LiteralPath $outFile -ErrorAction SilentlyContinue) }
        if (Test-Path -LiteralPath $errFile) { $output += @(Get-Content -LiteralPath $errFile -ErrorAction SilentlyContinue) }
        foreach ($line in $output) { Add-Content -LiteralPath $StackLog -Value ("psql: " + [string]$line) -Encoding UTF8 }
        $code = $psqlProcess.ExitCode
        if ($code -ne 0 -and -not $AllowFailure) { throw "psql failed with exit code $code." }
        return [pscustomobject]@{ ExitCode = $code; Output = ($output -join [Environment]::NewLine) }
    } finally {
        $env:PGPASSWORD = $previousPassword
        Remove-Item -LiteralPath $sqlFile, $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

function Wait-PostgresQuery {
    param(
        [Parameter(Mandatory = $true)][string]$BinDir,
        [string]$Database = 'postgres',
        [string]$User = 'postgres',
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $probe = Invoke-Psql -BinDir $BinDir -Database $Database -User $User -Sql 'SELECT 1;'
            if ($probe.ExitCode -eq 0 -and $probe.Output -match '(?m)^\s*1\s*$') {
                return $true
            }
        } catch {
            # PostgreSQL can accept TCP connections before it is ready to run a
            # query. Keep polling until the bounded timeout, then fail clearly.
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    throw "PostgreSQL accepted TCP connections but did not answer a query within $TimeoutSeconds seconds. See local-runtime/logs/postgresql.log."
}

function Ensure-Postgres {
    $bin = Resolve-PostgresBinaries
    $initdb = Join-Path $bin 'initdb.exe'
    $pgctl = Join-Path $bin 'pg_ctl.exe'
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresData 'PG_VERSION'))) {
        if (Test-Path -LiteralPath $PostgresData) {
            $existing = Get-ChildItem -LiteralPath $PostgresData -Force | Select-Object -First 1
            if ($existing) { throw "PostgreSQL data directory is incomplete: $PostgresData" }
        } else {
            New-Item -ItemType Directory -Path $PostgresData -Force | Out-Null
        }
        Write-StackLog 'Initializing the local PostgreSQL cluster.'
        & $initdb -D $PostgresData -U postgres --auth=trust --encoding=UTF8 --locale=C 2>&1 | Tee-Object -FilePath $PostgresLog -Append | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initdb failed. See local-runtime/logs/postgresql.log.' }
    }

    $running = Test-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort
    if (-not $running) {
        Write-StackLog "Starting local PostgreSQL on 127.0.0.1:$PostgresPort."
        # pg_ctl starts postgres through a detached cmd.exe on Windows.  Calling
        # it through a PowerShell pipeline can inherit the child handles and
        # block forever even after the server is accepting connections.  Use
        # Start-Process with isolated pipes and rely on the TCP readiness check.
        $pgctlOut = Join-Path $Logs 'pgctl.start.stdout.log'
        $pgctlErr = Join-Path $Logs 'pgctl.start.stderr.log'
        Remove-Item -LiteralPath $pgctlOut, $pgctlErr -Force -ErrorAction SilentlyContinue
        $pgctlArgs = "-D `"$PostgresData`" -l `"$PostgresLog`" -o `"-p $PostgresPort -h 127.0.0.1`" -w start"
        $pgctlProcess = Start-Process -FilePath $pgctl -ArgumentList $pgctlArgs -RedirectStandardOutput $pgctlOut -RedirectStandardError $pgctlErr -WindowStyle Hidden -PassThru
        $deadline = (Get-Date).AddSeconds(45)
        while (-not (Test-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort) -and (Get-Date) -lt $deadline) {
            if ($pgctlProcess.HasExited -and $pgctlProcess.ExitCode -ne 0) { break }
            Start-Sleep -Milliseconds 500
        }
        if (-not (Test-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort)) {
            if (-not $pgctlProcess.HasExited) { Stop-Process -Id $pgctlProcess.Id -Force -ErrorAction SilentlyContinue }
            $pgctlDetails = @(
                (Get-Content -LiteralPath $pgctlOut -Raw -ErrorAction SilentlyContinue),
                (Get-Content -LiteralPath $pgctlErr -Raw -ErrorAction SilentlyContinue)
            ) -join ' '
            throw "PostgreSQL did not become ready. $pgctlDetails See local-runtime/logs/postgresql.log."
        }
        # Do not wait for pg_ctl after the port is ready: on Windows its
        # detached wrapper may remain attached to the caller's console.
        if (-not $pgctlProcess.HasExited) {
            Start-Sleep -Milliseconds 200
        }
        if (-not (Wait-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort -Expected $true -TimeoutSeconds 5)) {
            throw 'PostgreSQL did not become ready. See local-runtime/logs/postgresql.log.'
        }
    }

    Wait-PostgresQuery -BinDir $bin | Out-Null
    $roleSql = @'
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'copimine') THEN
    CREATE ROLE copimine LOGIN PASSWORD '__PASSWORD__';
  ELSE
    ALTER ROLE copimine WITH LOGIN PASSWORD '__PASSWORD__';
  END IF;
END $$;
'@.Replace('__PASSWORD__', $script:LocalValues['POSTGRES_PASSWORD'])
    Invoke-Psql -BinDir $bin -Database 'postgres' -User 'postgres' -Sql $roleSql | Out-Null
    $dbCheck = Invoke-Psql -BinDir $bin -Database 'postgres' -User 'postgres' -Sql "SELECT 1 FROM pg_database WHERE datname='copimine';"
    if ($dbCheck.Output -notmatch '(?m)^\s*1\s*$') {
        Invoke-Psql -BinDir $bin -Database 'postgres' -User 'postgres' -Sql 'CREATE DATABASE copimine OWNER copimine;' | Out-Null
    }
    Invoke-Psql -BinDir $bin -Database 'copimine' -User 'postgres' -Sql 'CREATE SCHEMA IF NOT EXISTS copimine AUTHORIZATION copimine; GRANT ALL ON SCHEMA copimine TO copimine;' | Out-Null
    Invoke-Psql -BinDir $bin -Database 'copimine' -User 'copimine' -Sql 'SELECT current_database(), current_user;' | Out-Null
    $postmaster = Join-Path $PostgresData 'postmaster.pid'
    if (Test-Path -LiteralPath $postmaster) {
        Write-PidFile -Name 'postgres' -ProcessId ([int](Get-Content -LiteralPath $postmaster | Select-Object -First 1))
    }
    Write-StackLog 'Local PostgreSQL is ready.'
    return $bin
}

function Ensure-PluginCompatibilitySchema {
    if (-not $script:PostgresBin) { return }
    # The current election plugin names candidate identity player_uuid, while
    # the admin GUI still reads the legacy uuid/name columns.  Keep both views
    # live in the local database so either plugin can be enabled together.
    $sql = @'
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS uuid TEXT NOT NULL DEFAULT '';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT '';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS raw_votes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS admin_adjustment INTEGER NOT NULL DEFAULT 0;
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS removed INTEGER NOT NULL DEFAULT 0;
UPDATE candidates
   SET uuid=CASE WHEN COALESCE(uuid,'')='' THEN COALESCE(player_uuid,'') ELSE uuid END,
       name=CASE WHEN COALESCE(name,'')='' THEN COALESCE(player_name,'') ELSE name END,
       display_name=CASE WHEN COALESCE(display_name,'')='' THEN COALESCE(player_name,name,'') ELSE display_name END;
CREATE OR REPLACE FUNCTION copimine_sync_candidate_compat()
RETURNS trigger
LANGUAGE plpgsql
AS $func$
BEGIN
  IF NEW.id IS NULL OR btrim(NEW.id)='' THEN
    NEW.id='candidate_'||COALESCE(NEW.election_id,'')||'_'||COALESCE(NULLIF(NEW.player_uuid,''),NULLIF(NEW.uuid,''),'');
  END IF;
  IF TG_OP='UPDATE' AND NEW.uuid IS DISTINCT FROM OLD.uuid AND NEW.player_uuid IS NOT DISTINCT FROM OLD.player_uuid THEN
    NEW.player_uuid=COALESCE(NEW.uuid,'');
  ELSE
    NEW.uuid=COALESCE(NULLIF(NEW.player_uuid,''),NEW.uuid,'');
  END IF;
  IF TG_OP='UPDATE' AND NEW.name IS DISTINCT FROM OLD.name AND NEW.player_name IS NOT DISTINCT FROM OLD.player_name THEN
    NEW.player_name=COALESCE(NEW.name,'');
  ELSE
    NEW.name=COALESCE(NULLIF(NEW.player_name,''),NEW.name,'');
  END IF;
  IF COALESCE(NEW.display_name,'')='' THEN NEW.display_name=COALESCE(NEW.name,NEW.player_name,''); END IF;
  RETURN NEW;
END;
$func$;
DROP TRIGGER IF EXISTS copimine_sync_candidate_compat_trigger ON candidates;
CREATE TRIGGER copimine_sync_candidate_compat_trigger
BEFORE INSERT OR UPDATE ON candidates
FOR EACH ROW EXECUTE FUNCTION copimine_sync_candidate_compat();

DO $$
DECLARE enabled_type TEXT;
BEGIN
  SELECT data_type INTO enabled_type
    FROM information_schema.columns
   WHERE table_schema=current_schema() AND table_name='artifact_items_catalog' AND column_name='enabled';
  IF enabled_type='integer' THEN
    ALTER TABLE artifact_items_catalog ALTER COLUMN enabled DROP DEFAULT;
    ALTER TABLE artifact_items_catalog ALTER COLUMN enabled TYPE BOOLEAN USING (enabled<>0);
    ALTER TABLE artifact_items_catalog ALTER COLUMN enabled SET DEFAULT TRUE;
  END IF;
  SELECT data_type INTO enabled_type
    FROM information_schema.columns
   WHERE table_schema=current_schema() AND table_name='artifact_shops' AND column_name='enabled';
  IF enabled_type='integer' THEN
    ALTER TABLE artifact_shops ALTER COLUMN enabled DROP DEFAULT;
    ALTER TABLE artifact_shops ALTER COLUMN enabled TYPE BOOLEAN USING (enabled<>0);
    ALTER TABLE artifact_shops ALTER COLUMN enabled SET DEFAULT TRUE;
  END IF;
END $$;

DO $$
DECLARE c RECORD;
BEGIN
  -- The plugins store epoch milliseconds.  Upgrade legacy INTEGER timestamp
  -- columns before Java plugins start writing audit/self-check rows.
  FOR c IN
    SELECT table_name,column_name
      FROM information_schema.columns
     WHERE table_schema=current_schema()
       AND data_type='integer'
       AND (column_name='time' OR column_name LIKE '%_at' OR column_name LIKE '%_until' OR column_name LIKE '%_deadline')
  LOOP
    EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I DROP DEFAULT', current_schema(), c.table_name, c.column_name);
    EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE BIGINT USING %I::bigint', current_schema(), c.table_name, c.column_name, c.column_name);
    EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I SET DEFAULT 0', current_schema(), c.table_name, c.column_name);
  END LOOP;
END $$;
'@
    Invoke-Psql -BinDir $script:PostgresBin -Database 'copimine' -User 'copimine' -Sql $sql | Out-Null
    Write-StackLog 'Applied local compatibility schema for election/admin/artifacts plugins.'
}

function Resolve-WebsitePython {
    $sibling = Join-Path (Split-Path $Root -Parent) 'opt\copimine\admin-web\.codex-venv\Scripts\python.exe'
    if (Test-Path -LiteralPath $sibling) { return $sibling }
    $local = Join-Path $Runtime 'venv\Scripts\python.exe'
    $localVenvRoot = Join-Path $Runtime 'venv'
    $requirementsPath = Join-Path $AdminWebDir 'requirements.txt'

    if (Test-Path -LiteralPath $local -PathType Leaf) {
        $localVersion = (& $local -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')" 2>$null | Select-Object -Last 1)
        if ($localVersion -and $localVersion.Trim() -eq '3.13') {
            & $local -m pip install --disable-pip-version-check -r $requirementsPath 2>&1 | Tee-Object -FilePath $StackLog -Append | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'admin-web dependencies are not compatible with the local Python 3.13 environment. See local-runtime/logs/stack.log.' }
            return $local
        }
        Write-StackLog "Replacing an incompatible local Python environment ($($localVersion.Trim())). Python 3.13 is required by the pinned admin-web dependencies."
        Remove-Item -LiteralPath $localVenvRoot -Recurse -Force
    } elseif (Test-Path -LiteralPath $localVenvRoot -PathType Container) {
        Write-StackLog 'Removing an incomplete local Python environment before recreation.'
        Remove-Item -LiteralPath $localVenvRoot -Recurse -Force
    }

    $pythonCandidates = [System.Collections.Generic.List[string]]::new()
    $pyLauncher = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($pyLauncher) {
        $resolved313 = @(& $pyLauncher.Source '-3.13' '-c' 'import sys; print(sys.executable)' 2>$null) | Select-Object -Last 1
        if ($LASTEXITCODE -eq 0 -and $resolved313) {
            $pythonCandidates.Add($resolved313.ToString().Trim())
        }
    }
    foreach ($candidate in @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Python\Python313\python.exe'),
        (Join-Path $env:ProgramFiles 'Python313\python.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Python313\python.exe')
    )) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            $pythonCandidates.Add($candidate)
        }
    }
    $pythonCommand = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($pythonCommand) { $pythonCandidates.Add($pythonCommand.Source) }

    $basePython = $null
    foreach ($candidate in $pythonCandidates) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $candidateVersion = (& $candidate -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')" 2>$null | Select-Object -Last 1)
        if ($candidateVersion -and $candidateVersion.Trim() -eq '3.13') {
            $basePython = $candidate
            break
        }
    }
    if (-not $basePython) {
        throw 'Python 3.13 is required to start admin-web because the pinned psycopg[binary]==3.2.3 dependency has no Python 3.14 wheel.'
    }
    Write-StackLog 'Creating the local admin-web Python environment.'
    & $basePython -m venv $localVenvRoot 2>&1 | Tee-Object -FilePath $StackLog -Append | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Python virtual environment creation failed.' }
    $venvDeadline = (Get-Date).AddSeconds(30)
    while (-not (Test-Path -LiteralPath $local -PathType Leaf) -and (Get-Date) -lt $venvDeadline) {
        # Python/Defender can finish copying the interpreter just after the
        # venv process exits. Do not race the first pip invocation.
        Start-Sleep -Milliseconds 250
    }
    if (-not (Test-Path -LiteralPath $local -PathType Leaf)) {
        throw "Python venv did not produce the interpreter: $local"
    }
    & $local -m pip install --disable-pip-version-check -r $requirementsPath 2>&1 | Tee-Object -FilePath $StackLog -Append | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'admin-web dependency installation failed. See local-runtime/logs/stack.log.' }
    return $local
}

function Ensure-ServerRuntimeFiles {
    $jar = Join-Path $ServerDir 'purpur.jar'
    if (-not (Test-Path -LiteralPath $jar)) {
        $source = @(
            (Join-Path $Root 'artifacts\local-validation\paper\purpur.jar'),
            (Join-Path (Split-Path $Root -Parent) 'opt\copimine\minecraft\server\purpur.jar')
        ) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        if (-not $source) { throw "purpur.jar is missing from the disposable fixture and fallback: $jar" }
        Copy-Item -LiteralPath $source -Destination $jar -Force
        Write-StackLog "Copied Purpur from the local disposable fixture: $source"
    }
    $eula = Join-Path $ServerDir 'eula.txt'
    if (-not (Test-Path -LiteralPath $eula)) { Set-Content -LiteralPath $eula -Value 'eula=true' -Encoding ASCII }
    $voicechat = Join-Path $ServerDir 'plugins\voicechat-bukkit-2.6.16.jar'
    $legacyVoicechat = Join-Path $ServerDir 'plugins\voicechat-bukkit-2.6.11.jar'
    $legacyRemappedVoicechat = Join-Path $ServerDir 'plugins\.paper-remapped\voicechat-bukkit-2.6.11.jar'
    if (Test-Path -LiteralPath $legacyVoicechat -PathType Leaf) {
        Remove-Item -LiteralPath $legacyVoicechat -Force
        Write-StackLog 'Removed the stale local Simple Voice Chat 2.6.11 plugin before staging 2.6.16.'
    }
    if (Test-Path -LiteralPath $legacyRemappedVoicechat -PathType Leaf) {
        Remove-Item -LiteralPath $legacyRemappedVoicechat -Force
        Write-StackLog 'Removed the stale local remapped Simple Voice Chat 2.6.11 plugin.'
    }
    if (-not (Test-Path -LiteralPath $voicechat)) {
        $sourceVoicechat = @(
            (Join-Path $Root 'artifacts\local-validation\voicechat-bukkit-2.6.16.jar'),
            (Join-Path $Root 'artifacts\local-validation\full-plugin-source-20260824\voicechat-bukkit-2.6.16.jar'),
            (Join-Path $Root 'artifacts\local-validation\paper-full-plugins-clean-20260824\plugins\voicechat-bukkit-2.6.16.jar'),
            (Join-Path $Root 'minecraft\server\plugins\voicechat-bukkit-2.6.16.jar'),
            (Join-Path (Split-Path $Root -Parent) 'opt\copimine\minecraft\server\plugins\voicechat-bukkit-2.6.16.jar')
        ) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        if (-not $sourceVoicechat) {
            throw 'Simple Voice Chat 2.6.16 is required for the local plugin stack; stage the official JAR in artifacts/local-validation/voicechat-bukkit-2.6.16.jar.'
        }
        Copy-Item -LiteralPath $sourceVoicechat -Destination $voicechat -Force
        Write-StackLog "Copied the local Simple Voice Chat 2.6.16 API dependency: $sourceVoicechat"
    }
}

function Restore-VoiceChatProperties {
    if (Test-Path -LiteralPath $VoiceChatPropertiesBackup) {
        Copy-Item -LiteralPath $VoiceChatPropertiesBackup -Destination (Join-Path $ServerDir 'plugins\voicechat\voicechat-server.properties') -Force
        Remove-Item -LiteralPath $VoiceChatPropertiesBackup -Force
        Write-StackLog 'Restored minecraft/server/plugins/voicechat/voicechat-server.properties.'
    }
}

function Send-RconCommand {
    param([string]$Command)
    $password = $script:LocalValues['RCON_PASSWORD']
    if (-not $password) { return $false }
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', $RconPort)
        $stream = $client.GetStream()
        $encoding = New-Object System.Text.UTF8Encoding($false)
        function Send-RconPacket([int]$RequestId, [int]$Type, [string]$Payload) {
            $body = New-Object System.IO.MemoryStream
            $writer = New-Object System.IO.BinaryWriter($body)
            $writer.Write([int32]$RequestId)
            $writer.Write([int32]$Type)
            $writer.Write($encoding.GetBytes($Payload))
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Flush()
            $packet = New-Object System.IO.MemoryStream
            $packet.Write([BitConverter]::GetBytes([int32]$body.Length), 0, 4)
            $packet.Write($body.ToArray(), 0, [int]$body.Length)
            $stream.Write($packet.ToArray(), 0, [int]$packet.Length)
            $stream.Flush()
        }
        function Read-RconPacket {
            $lengthBytes = New-Object byte[] 4
            if ($stream.Read($lengthBytes, 0, 4) -ne 4) { return $null }
            $length = [BitConverter]::ToInt32($lengthBytes, 0)
            if ($length -lt 10 -or $length -gt 1048576) { return $null }
            $data = New-Object byte[] $length
            $offset = 0
            while ($offset -lt $length) {
                $read = $stream.Read($data, $offset, $length - $offset)
                if ($read -le 0) { return $null }
                $offset += $read
            }
            return [pscustomobject]@{ Id = [BitConverter]::ToInt32($data, 0); Type = [BitConverter]::ToInt32($data, 4) }
        }
        Send-RconPacket -RequestId 1 -Type 3 -Payload $password
        $auth = Read-RconPacket
        if (-not $auth -or $auth.Id -eq -1) { return $false }
        Send-RconPacket -RequestId 2 -Type 2 -Payload $Command
        [void](Read-RconPacket)
        return $true
    } catch { return $false } finally { $client.Close() }
}

function Find-MinecraftProcess {
    $trackedId = Read-PidFile -Name 'minecraft'
    if ($trackedId -gt 0) {
        $tracked = Get-CimInstance Win32_Process -Filter "ProcessId=$trackedId" -ErrorAction SilentlyContinue
        if ($tracked -and $tracked.CommandLine -match '(?i)-jar\s+purpur\.jar(?:\s|$)') {
            return @($tracked)
        }
    }

    # Start-Process records the exact Java PID above. The fallback is only for
    # recovery after a stale pid file and is narrowed to this runner's heap
    # profile so it cannot select the other local/event server.
    return @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -match '(?i)-Xms1G\s+-Xmx2G\s+-jar\s+purpur\.jar(?:\s|$)'
    })
}

function Assert-MinecraftStartupHealthy {
    param([int]$TimeoutSeconds = 30)
    $logPath = Join-Path $ServerDir 'logs\latest.log'
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $content = ''
    do {
        if (Test-Path -LiteralPath $logPath -PathType Leaf) {
            $content = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
            if ($content -match 'Done \(') { break }
        }
        if (@(Find-MinecraftProcess).Count -eq 0) {
            throw "Minecraft exited before startup completed. Inspect $logPath."
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    if ($content -notmatch 'Done \(') {
        throw "Minecraft did not report a completed startup within $TimeoutSeconds seconds. Inspect $logPath."
    }

    foreach ($pattern in @(
        '(?im)Could not load plugin',
        '(?im)InvalidPluginException:',
        '(?im)NoClassDefFoundError:',
        '(?im)ClassNotFoundException:'
    )) {
        $failure = [regex]::Match($content, $pattern)
        if ($failure.Success) {
            throw "Minecraft plugin startup failed ($pattern). Inspect $logPath."
        }
    }

    foreach ($pluginName in @(
        'AuthEffects',
        'AuthMe',
        'ClearLag',
        'CopiMineArtifacts',
        'CopiMineEconomyCore',
        'CopiMineElectionCore',
        'CopiMineNarcotics',
        'CopiMineUltimateAdminPlus',
        'CopiMineWorldCore',
        'GrimAC',
        'voicechat'
    )) {
        $marker = '\[' + [regex]::Escape($pluginName) + '\] Enabling '
        if ($content -notmatch $marker) {
            throw "Minecraft plugin '$pluginName' did not reach its enabling phase. Inspect $logPath."
        }
    }
    Write-StackLog 'Minecraft startup gate passed: completed startup with all required local plugins enabled.'
}

function Start-Website {
    if (Test-TcpPort -TargetHost '127.0.0.1' -Port $WebsitePort) { throw "Port $WebsitePort is already in use." }
    $python = Resolve-WebsitePython
    New-Item -ItemType Directory -Path (Join-Path $Runtime 'admin-data'), (Join-Path $Runtime 'backups') -Force | Out-Null
    $env:COPIMINE_ENV_FILE = $LocalEnvFile
    $env:PYTHONUNBUFFERED = '1'
    $proc = Start-Process -FilePath $python -ArgumentList @('-m', 'uvicorn', 'backend.main:app', '--host', '127.0.0.1', '--port', [string]$WebsitePort, '--log-level', 'info') -WorkingDirectory $AdminWebDir -RedirectStandardOutput $WebsiteOutLog -RedirectStandardError $WebsiteErrLog -WindowStyle Hidden -PassThru
    Write-PidFile -Name 'website' -ProcessId $proc.Id
    if (-not (Wait-TcpPort -TargetHost '127.0.0.1' -Port $WebsitePort -Expected $true -TimeoutSeconds 45)) {
        if (-not (Get-ProcessIfAlive -ProcessId $proc.Id)) { throw 'admin-web exited during startup. See local-runtime/logs/website.stderr.log.' }
        throw 'admin-web did not open port 8090. See local-runtime/logs/website.stderr.log.'
    }
    Write-StackLog "Website is ready at http://127.0.0.1:$WebsitePort/ (login: localadmin / localadmin123)."
}

function Start-Minecraft {
    Ensure-ServerRuntimeFiles
    if (Test-TcpPort -TargetHost '127.0.0.1' -Port $MinecraftPort) { throw "Port $MinecraftPort is already in use." }
    Backup-AndPatchServerProperties
    Backup-AndPatchVoiceChatProperties
    $resourcePackBuild = Join-Path $Root 'resourcepacks\build'
    $resourcePackZip = Join-Path $resourcePackBuild 'CopiMineResourcePack.zip'
    if (-not (Test-Path -LiteralPath $resourcePackZip -PathType Leaf)) {
        throw "Built resource pack is missing: $resourcePackZip. Run resourcepacks/build-resourcepack.py first."
    }
    $resourcePackSha1 = Get-Sha1Hex -Path $resourcePackZip
    Set-ServerProperty -Key 'resource-pack' -Value "http://127.0.0.1:$WebsitePort/resourcepacks/CopiMineResourcePack.zip"
    Set-ServerProperty -Key 'resource-pack-sha1' -Value $resourcePackSha1
    $java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
    if (-not $java) { throw 'Java 21 is required to start Purpur.' }
    $env:COPIMINE_ENV_FILE = $LocalEnvFile
    $env:POSTGRES_HOST = '127.0.0.1'
    $env:POSTGRES_PORT = [string]$PostgresPort
    $env:POSTGRES_DB = 'copimine'
    $env:POSTGRES_USER = 'copimine'
    $env:POSTGRES_PASSWORD = $script:LocalValues['POSTGRES_PASSWORD']
    $env:POSTGRES_SCHEMA = 'copimine'
    $env:RCON_PASSWORD = $script:LocalValues['RCON_PASSWORD']
    $proc = Start-Process -FilePath $java -ArgumentList @("-Xms$MinecraftJvmXms", "-Xmx$MinecraftJvmXmx", '-jar', 'purpur.jar', 'nogui') -WorkingDirectory $ServerDir -RedirectStandardOutput $MinecraftOutLog -RedirectStandardError $MinecraftErrLog -WindowStyle Hidden -PassThru
    Write-PidFile -Name 'minecraft' -ProcessId $proc.Id
    if (-not (Wait-TcpPort -TargetHost '127.0.0.1' -Port $MinecraftPort -Expected $true -TimeoutSeconds 120)) {
        if (-not (Get-ProcessIfAlive -ProcessId $proc.Id)) { throw 'Minecraft exited during startup. See local-runtime/logs/minecraft.stderr.log and minecraft/server/logs/latest.log.' }
        throw 'Minecraft did not open port 25565. See local-runtime/logs/minecraft.stdout.log and minecraft/server/logs/latest.log.'
    }
    if (-not (Wait-TcpPort -TargetHost '127.0.0.1' -Port $RconPort -Expected $true -TimeoutSeconds 180)) { throw 'Minecraft RCON did not become ready.' }
    Assert-MinecraftStartupHealthy
    [void](Send-RconCommand -Command 'save-all')
    $adminFile = Join-Path $Runtime 'local-admin.txt'
    if (Test-Path -LiteralPath $adminFile) {
        $adminNick = (Get-Content -LiteralPath $adminFile -Raw).Trim()
        if ($adminNick -and $adminNick -notmatch '^#') {
            [void](Send-RconCommand -Command ("op " + $adminNick))
            Write-StackLog "Granted local OP to '$adminNick' from local-runtime/local-admin.txt."
        }
    } else {
        Set-Content -LiteralPath $adminFile -Value '# Put your Minecraft nickname on the first line and restart the local stack to receive OP.' -Encoding UTF8
    }
    Write-StackLog 'Minecraft is ready at 127.0.0.1:25565.'
}

function Stop-Website {
    $processId = Read-PidFile -Name 'website'
    if ($processId -gt 0) { Stop-ProcessTree -ProcessId $processId }
    Remove-PidFile -Name 'website'
    if (Wait-TcpPort -TargetHost '127.0.0.1' -Port $WebsitePort -Expected $false -TimeoutSeconds 10) { Write-StackLog 'Website stopped.' } else { Write-StackLog 'Website port is still occupied; inspect website PID/logs.' }
}

function Stop-Minecraft {
    $processId = Read-PidFile -Name 'minecraft'
    if ($processId -le 0) {
        $found = Find-MinecraftProcess | Select-Object -First 1
        if ($found) { $processId = [int]$found.ProcessId }
    }
    if (Test-TcpPort -TargetHost '127.0.0.1' -Port $RconPort) { [void](Send-RconCommand -Command 'stop') }
    if ($processId -gt 0) {
        $deadline = (Get-Date).AddSeconds(45)
        while ((Get-ProcessIfAlive -ProcessId $processId) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
        if (Get-ProcessIfAlive -ProcessId $processId) { Stop-ProcessTree -ProcessId $processId }
    }
    Remove-PidFile -Name 'minecraft'
    if (Wait-TcpPort -TargetHost '127.0.0.1' -Port $MinecraftPort -Expected $false -TimeoutSeconds 20) { Write-StackLog 'Minecraft stopped.' } else { Write-StackLog 'Minecraft port is still occupied; inspect the process and logs.' }
    Restore-ServerProperties
    Restore-VoiceChatProperties
}

function Stop-Postgres {
    $bin = Join-Path $PostgresRoot 'pgsql\bin'
    $pgctl = Join-Path $bin 'pg_ctl.exe'
    if ((Test-Path -LiteralPath $pgctl) -and (Test-Path -LiteralPath (Join-Path $PostgresData 'PG_VERSION'))) {
        if (Test-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort) {
            $pgctlOut = Join-Path $Logs 'pgctl.stop.stdout.log'
            $pgctlErr = Join-Path $Logs 'pgctl.stop.stderr.log'
            Remove-Item -LiteralPath $pgctlOut, $pgctlErr -Force -ErrorAction SilentlyContinue
            $pgctlArgs = "-D `"$PostgresData`" -m fast -w -t 30 stop"
            $pgctlProcess = Start-Process -FilePath $pgctl -ArgumentList $pgctlArgs -RedirectStandardOutput $pgctlOut -RedirectStandardError $pgctlErr -WindowStyle Hidden -PassThru
            $pgctlProcess | Wait-Process -Timeout 40 -ErrorAction SilentlyContinue
            if (-not $pgctlProcess.HasExited) { Stop-Process -Id $pgctlProcess.Id -Force -ErrorAction SilentlyContinue }
            foreach ($detail in @((Get-Content -LiteralPath $pgctlOut -Raw -ErrorAction SilentlyContinue), (Get-Content -LiteralPath $pgctlErr -Raw -ErrorAction SilentlyContinue))) {
                if ($detail) { Add-Content -LiteralPath $StackLog -Value $detail.Trim() -Encoding UTF8 }
            }
        }
    }
    $processId = Read-PidFile -Name 'postgres'
    if ($processId -gt 0 -and (Get-ProcessIfAlive -ProcessId $processId) -and -not (Test-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort)) { Stop-ProcessTree -ProcessId $processId }
    Remove-PidFile -Name 'postgres'
    if (Wait-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort -Expected $false -TimeoutSeconds 20) { Write-StackLog 'PostgreSQL stopped.' } else { Write-StackLog 'PostgreSQL port is still occupied; inspect local-runtime/logs/postgresql.log.' }
}

function Stop-Stack {
    Stop-Minecraft
    Stop-Website
    Stop-Postgres
}

function Show-Status {
    $rows = @(
        [pscustomobject]@{ Service = 'PostgreSQL'; Address = "127.0.0.1:$PostgresPort"; Ready = (Test-TcpPort -TargetHost '127.0.0.1' -Port $PostgresPort); Log = $PostgresLog },
        [pscustomobject]@{ Service = 'Website'; Address = "http://127.0.0.1:$WebsitePort/"; Ready = (Test-TcpPort -TargetHost '127.0.0.1' -Port $WebsitePort); Log = $WebsiteErrLog },
        [pscustomobject]@{ Service = 'Minecraft'; Address = '127.0.0.1:25565'; Ready = (Test-TcpPort -TargetHost '127.0.0.1' -Port $MinecraftPort); Log = (Join-Path $ServerDir 'logs\latest.log') },
        [pscustomobject]@{ Service = 'RCON'; Address = "127.0.0.1:$RconPort"; Ready = (Test-TcpPort -TargetHost '127.0.0.1' -Port $RconPort); Log = $MinecraftOutLog }
    )
    $rows | Format-Table -AutoSize
    Write-Host "Logs: $Logs"
}

try {
    $script:LocalValues = Write-LocalEnvironment
    if ($Action -eq 'start') {
        $script:PostgresBin = Ensure-Postgres
        Start-Website
        Ensure-PluginCompatibilitySchema
        Start-Minecraft
        Write-StackLog 'Local CopiMine stack started. Use 127.0.0.1:25565 in Minecraft.'
        Write-Host "Website: http://127.0.0.1:$WebsitePort/"
        Write-Host "Minecraft: 127.0.0.1:$MinecraftPort"
        Write-Host "Logs: $Logs"
    } elseif ($Action -eq 'stop') {
        Stop-Stack
        Write-StackLog 'Local CopiMine stack stopped.'
    } else {
        Show-Status
    }
    exit 0
} catch {
    Write-StackLog ("ERROR: " + $_.Exception.Message)
    if ($Action -eq 'start') {
        Write-StackLog 'Startup failed; attempting safe cleanup.'
        try { Stop-Stack } catch { Write-StackLog ("Cleanup error: " + $_.Exception.Message) }
    }
    exit 1
}
