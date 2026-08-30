[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$AdminNickname = 'SudoKillDash9',
  [switch]$LaunchClient
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$worktreeRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $worktreeRoot 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $localRuntimeRoot 'end-rift-server')).Path
$sourcePluginDir = (Resolve-Path (Join-Path $worktreeRoot 'minecraft\server\plugins')).Path
$sourceEventConfig = Join-Path $worktreeRoot 'copimine-end-event\config.yml'
$targetPluginDir = (Resolve-Path (Join-Path $serverDir 'plugins')).Path
$sourcePackDir = (Resolve-Path (Join-Path $worktreeRoot 'resourcepacks\build')).Path
$targetPackDir = Join-Path $serverDir 'resourcepacks\build'
$logsDir = Join-Path $localRuntimeRoot 'user-session-logs'
$packPidFile = Join-Path $localRuntimeRoot 'end-rift-pack-http.pid'
$sharedRuntimeRoot = (Resolve-Path (Join-Path $worktreeRoot '..\..\local-runtime')).Path
$pgCtl = Join-Path $sharedRuntimeRoot 'postgresql\pgsql\bin\pg_ctl.exe'
$pgDataDir = (Resolve-Path (Join-Path $localRuntimeRoot 'end-rift-pgdata')).Path
$pack = Join-Path $sourcePackDir 'CopiMineResourcePack.zip'
$targetPack = Join-Path $targetPackDir 'CopiMineResourcePack.zip'
$serverProperties = Join-Path $serverDir 'server.properties'
$serverPropertiesBaseline = Join-Path $serverDir 'server.properties.pre-local-resourcepack'
$purpurConfig = Join-Path $serverDir 'purpur.yml'
$essentialsConfig = Join-Path $serverDir 'plugins\Essentials\config.yml'
$eventConfig = Join-Path $serverDir 'plugins\CopiMineEndEvent\config.yml'
$whitelistPath = Join-Path $serverDir 'whitelist.json'
$opsPath = Join-Path $serverDir 'ops.json'
$authmeDb = Join-Path $serverDir 'plugins\AuthMe\authme.db'
$eventStatePath = Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml'
$adminWebDir = Join-Path $worktreeRoot 'admin-web'
$websitePort = 8093
$websitePidFile = Join-Path $localRuntimeRoot 'copimine-local-web.pid'
$websiteEnvFile = Join-Path $localRuntimeRoot 'copimine-local-web.env'
$websiteDataDir = Join-Path $localRuntimeRoot 'copimine-local-web-data'
$websiteBackupsDir = Join-Path $localRuntimeRoot 'copimine-local-web-backups'
$websiteOutLog = Join-Path $logsDir 'copimine-local-web.out.log'
$websiteErrLog = Join-Path $logsDir 'copimine-local-web.err.log'
$websitePython = Join-Path $sharedRuntimeRoot 'venv\Scripts\python.exe'
$endRiftEnvFile = Join-Path $localRuntimeRoot 'end-rift.env'
$pluginManifestPath = Join-Path $worktreeRoot 'deploy\plugin_versions.json'
$serverPort = 25566
$rconPort = 25576
$postgresPort = 55433
$packPort = 8092
$radminVpnAddress = ''
$resourcePackBindAddress = '127.0.0.1'
$resourcePackUrlHost = '127.0.0.1'

New-Item -ItemType Directory -Path $logsDir, $targetPackDir -Force | Out-Null

function Assert-UnderRoot {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$Label
  )
  $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $fullRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
  if (-not $fullPath.StartsWith($fullRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "$Label is outside the approved local root: $Path"
  }
}

Assert-UnderRoot -Path $serverDir -Root $localRuntimeRoot -Label 'Local server'
Assert-UnderRoot -Path $sourcePluginDir -Root $worktreeRoot -Label 'Current worktree plugin source'
Assert-UnderRoot -Path $sourceEventConfig -Root $worktreeRoot -Label 'Current worktree End Rift config source'
Assert-UnderRoot -Path $targetPluginDir -Root $localRuntimeRoot -Label 'Local plugin directory'
Assert-UnderRoot -Path $targetPackDir -Root $localRuntimeRoot -Label 'Local resource-pack directory'
Assert-UnderRoot -Path $pgDataDir -Root $localRuntimeRoot -Label 'Local PostgreSQL data directory'
Assert-UnderRoot -Path $serverPropertiesBaseline -Root $localRuntimeRoot -Label 'Local server.properties baseline'
Assert-UnderRoot -Path $purpurConfig -Root $localRuntimeRoot -Label 'Local Purpur configuration'
Assert-UnderRoot -Path $essentialsConfig -Root $localRuntimeRoot -Label 'Local Essentials configuration'
Assert-UnderRoot -Path $whitelistPath -Root $localRuntimeRoot -Label 'Local whitelist'
Assert-UnderRoot -Path $opsPath -Root $localRuntimeRoot -Label 'Local operators'
Assert-UnderRoot -Path $authmeDb -Root $localRuntimeRoot -Label 'Local AuthMe database'
Assert-UnderRoot -Path $eventStatePath -Root $localRuntimeRoot -Label 'Local End Rift state'
Assert-UnderRoot -Path $websiteEnvFile -Root $localRuntimeRoot -Label 'Local website environment'
Assert-UnderRoot -Path $websiteDataDir -Root $localRuntimeRoot -Label 'Local website data'
Assert-UnderRoot -Path $websiteBackupsDir -Root $localRuntimeRoot -Label 'Local website backups'

if (-not (Test-Path -LiteralPath $serverProperties -PathType Leaf)) { throw "Local server.properties is missing: $serverProperties" }
if (-not (Test-Path -LiteralPath $purpurConfig -PathType Leaf)) { throw "Local purpur.yml is missing: $purpurConfig" }
if (-not (Test-Path -LiteralPath $eventConfig -PathType Leaf)) { throw "Local End Rift config is missing: $eventConfig" }
if (-not (Test-Path -LiteralPath $sourceEventConfig -PathType Leaf)) { throw "Current worktree End Rift config source is missing: $sourceEventConfig" }
if (-not (Test-Path -LiteralPath $adminWebDir -PathType Container)) { throw "Local admin-web source is missing: $adminWebDir" }
if ((Get-Content -LiteralPath $eventConfig -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused to start a non-local End Rift configuration.'
}

$branch = (& git -C $worktreeRoot branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused to start unexpected Git branch '$branch'; expected codex/end-rift-event."
}
$head = (& git -C $worktreeRoot rev-parse --short=12 HEAD 2>$null).Trim()
Write-Host "Using local worktree branch=$branch HEAD=$head"

function Test-TcpPort {
  param([string]$HostName, [int]$Port, [int]$TimeoutMs = 700)
  $client = [Net.Sockets.TcpClient]::new()
  try {
    $async = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
    $client.EndConnect($async)
    return $true
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Wait-TcpPort {
  param([string]$HostName, [int]$Port, [bool]$Expected, [int]$TimeoutSeconds = 30)
  $attempts = [Math]::Max(1, $TimeoutSeconds * 2)
  for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
    if ((Test-TcpPort -HostName $HostName -Port $Port) -eq $Expected) { return $true }
    Start-Sleep -Milliseconds 500
  }
  return $false
}

function Get-RadminVpnAddress {
  $addresses = @(
    Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Radmin VPN' -ErrorAction SilentlyContinue |
      Where-Object {
        $_.IPAddress -and
        $_.IPAddress -notlike '127.*' -and
        $_.AddressState -notin @('Tentative', 'Duplicate')
      } |
      Select-Object -ExpandProperty IPAddress
  )
  if ($addresses.Count -eq 0) {
    $addresses = @(
      Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
          $_.InterfaceAlias -match '(?i)radmin' -and
          $_.IPAddress -and
          $_.IPAddress -notlike '127.*'
        } |
        Select-Object -ExpandProperty IPAddress
    )
  }
  return [string](@($addresses | Select-Object -First 1)[0])
}

function Get-FileDigest {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Algorithm
  )
  $hashAlgorithm = [Security.Cryptography.HashAlgorithm]::Create($Algorithm)
  if ($null -eq $hashAlgorithm) { throw "Unsupported local hash algorithm: $Algorithm" }
  try {
    $stream = [IO.File]::OpenRead($Path)
    try {
      return ([BitConverter]::ToString($hashAlgorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    } finally {
      $stream.Dispose()
    }
  } finally {
    $hashAlgorithm.Dispose()
  }
}

function Get-FileSha1 {
  param([Parameter(Mandatory = $true)][string]$Path)
  return Get-FileDigest -Path $Path -Algorithm 'SHA1'
}

function Get-FileSha256 {
  param([Parameter(Mandatory = $true)][string]$Path)
  return Get-FileDigest -Path $Path -Algorithm 'SHA256'
}

function Assert-TrackedProductionPropertiesClean {
  $relativePath = 'minecraft/server/server.properties'
  $status = @(& git -C $worktreeRoot status --porcelain=v1 --untracked-files=no -- $relativePath)
  $statusExit = $LASTEXITCODE
  if ($statusExit -ne 0) {
    throw 'Unable to verify the tracked production server.properties state with Git.'
  }
  if (@($status | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -gt 0) {
    throw "Refused local start because tracked production $relativePath is dirty. Restore or commit it before using the local launcher; the launcher will never overwrite it."
  }
  return Get-FileSha256 -Path (Join-Path $worktreeRoot $relativePath)
}

function New-LocalSecret {
  param([int]$Bytes = 32)
  $buffer = New-Object byte[] $Bytes
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
  return ([BitConverter]::ToString($buffer) -replace '-', '').ToLowerInvariant()
}

function Read-LocalKeyValueFile {
  param([Parameter(Mandatory = $true)][string]$Path)
  $values = @{}
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $values }
  foreach ($line in Get-Content -LiteralPath $Path) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      $key = [string]$matches[1]
      $value = [string]$matches[2]
      $values[$key] = $value.Trim().Trim('"').Trim("'")
    }
  }
  return $values
}

function Get-LocalServerProperty {
  param([Parameter(Mandatory = $true)][string]$Key)
  if ((Get-Item -LiteralPath $serverProperties).Length -gt 1048576) {
    throw "Refused to parse an unexpectedly large local server.properties file: $serverProperties"
  }
  $pattern = '^' + [Regex]::Escape($Key) + '=(.*)$'
  foreach ($line in Get-Content -LiteralPath $serverProperties -Encoding UTF8) {
    if ($line -match $pattern) { return [string]$matches[1] }
  }
  return ''
}

function Ensure-LocalWebsiteEnvironment {
  if (-not (Test-Path -LiteralPath $endRiftEnvFile -PathType Leaf)) {
    throw "The isolated End Rift environment file is missing: $endRiftEnvFile"
  }
  $base = Read-LocalKeyValueFile -Path $endRiftEnvFile
  foreach ($key in @('POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_SCHEMA', 'POSTGRES_USER', 'POSTGRES_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([string]$base[$key])) {
      throw "The isolated End Rift environment is missing $key."
    }
  }
  if ([string]$base['POSTGRES_HOST'] -notin @('127.0.0.1', 'localhost', '::1') -or
      [string]$base['POSTGRES_PORT'] -ne [string]$postgresPort -or
      [string]$base['POSTGRES_DB'] -ne 'copimine' -or
      [string]$base['POSTGRES_SCHEMA'] -ne 'copimine' -or
      [string]$base['POSTGRES_USER'] -ne 'copimine') {
    throw 'Refused to bind the local website to a non-isolated PostgreSQL endpoint.'
  }

  $rconPassword = Get-LocalServerProperty -Key 'rcon.password'
  if ([string]::IsNullOrWhiteSpace($rconPassword)) {
    throw 'The isolated local server has no RCON password for the website smoke checks.'
  }
  $existing = Read-LocalKeyValueFile -Path $websiteEnvFile
  $secretKey = [string]$existing['SECRET_KEY']
  if ($secretKey.Length -lt 32) { $secretKey = New-LocalSecret }
  $pluginApiKey = [string]$existing['PLUGIN_API_KEY']
  if ($pluginApiKey.Length -lt 32) { $pluginApiKey = New-LocalSecret }
  New-Item -ItemType Directory -Path $websiteDataDir, $websiteBackupsDir -Force | Out-Null

  $adminUsersJson = '{"localadmin":{"password_hash":"plain:localadmin123","role":"owner","enabled":true}}'
  $lines = @(
    '# Isolated local CopiMine website environment. Never use on production.',
    "COPIMINE_ENV_FILE=$websiteEnvFile",
    'COPIMINE_AUTH_STORAGE=postgresql',
    "POSTGRES_HOST=$($base['POSTGRES_HOST'])",
    "POSTGRES_PORT=$postgresPort",
    "POSTGRES_DB=$($base['POSTGRES_DB'])",
    "POSTGRES_USER=$($base['POSTGRES_USER'])",
    "POSTGRES_PASSWORD=$($base['POSTGRES_PASSWORD'])",
    "POSTGRES_SCHEMA=$($base['POSTGRES_SCHEMA'])",
    "DATABASE_URL=postgresql://$($base['POSTGRES_USER']):$($base['POSTGRES_PASSWORD'])@127.0.0.1:$postgresPort/$($base['POSTGRES_DB'])?schema=$($base['POSTGRES_SCHEMA'])",
    "MC_SERVER_DIR=$serverDir",
    "MC_WORLD_DIR=$(Join-Path $serverDir 'CopiMine')",
    'MC_HOST=127.0.0.1',
    "MC_PORT=$serverPort",
    "MC_PUBLIC_ADDRESS=127.0.0.1:$serverPort",
    'MC_PUBLIC_VERSION=1.21.1',
    'RCON_HOST=127.0.0.1',
    "RCON_PORT=$rconPort",
    "RCON_PASSWORD=$rconPassword",
    "MC_LOG_FILE=$(Join-Path $serverDir 'logs\latest.log')",
    "COREPROTECT_DB=$(Join-Path $serverDir 'plugins\CoreProtect\database.db')",
    "ADMIN_PLUGIN_DB=$(Join-Path $serverDir 'plugins\CopiMineUltimateAdminPlus\copimine_ultimate.db')",
    "COPIMINE_ADMIN_DATA=$websiteDataDir",
    "COPIMINE_AUTH_DB=$(Join-Path $websiteDataDir 'admin-auth.sqlite3')",
    "COPIMINE_BACKUPS_DIR=$websiteBackupsDir",
    "SECRET_KEY=$secretKey",
    "PLUGIN_API_KEY=$pluginApiKey",
    "ADMIN_PUBLIC_BASE_URL=http://127.0.0.1:$websitePort",
    "BACKEND_INTERNAL_BASE_URL=http://127.0.0.1:$websitePort",
    'ALLOW_INSECURE_HTTP_AUTH=1',
    'AUTH_COOKIE_SECURE=0',
    'REQUIRE_OP_FOR_LOGIN=0',
    'REQUIRE_WHITELIST_FOR_LOGIN=0',
    'COPIMINE_STARTUP_STRICT=1',
    'DB_WRITE_ENABLED=1',
    'ADMIN_DB_WRITE_ALLOWLIST=localadmin',
    'ALLOW_PLAIN_ADMIN_PASSWORDS=1',
    "ADMIN_USERS_JSON=$adminUsersJson",
    'MINECRAFT_SERVICE=copimine-end-rift-local',
    'COPIMINE_SYSTEMD_SERVICES=',
    'DISCORD_BOT_TOKEN=',
    'DISCORD_GUILD_ID=',
    'DISCORD_APPLICATIONS_CHANNEL_ID=',
    'DISCORD_REPORTS_CHANNEL_ID=',
    'DISCORD_ADMIN_ROLE_ID=',
    'DISCORD_ADMIN_ALLOWLIST=',
    "COPIMINE_RESOURCEPACK_URL=http://127.0.0.1:$packPort/CopiMineResourcePack.zip",
    'RCON_WEB_COMMAND_ALLOWLIST=list,tps,mspt,say,save-all,time query,weather query'
  )
  $utf8 = [Text.UTF8Encoding]::new($false)
  [IO.File]::WriteAllText($websiteEnvFile, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
  $script:LocalWebsiteEnv = Read-LocalKeyValueFile -Path $websiteEnvFile
  Write-Host "Verified isolated website environment at $websiteEnvFile (PostgreSQL 127.0.0.1:$postgresPort, data under local-runtime)."
}

function Get-LocalPaperProcesses {
  $localServerPids = @(
    Get-NetTCPConnection -State Listen -LocalPort $serverPort -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty OwningProcess
  )
  @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
      Where-Object {
        $_.CommandLine -and
        $_.CommandLine -match '(?i)purpur\.jar' -and
        (
          $_.CommandLine.IndexOf($serverDir, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
          $localServerPids -contains [int]$_.ProcessId
        )
      })
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $helper = Join-Path $scriptRoot 'InvokeEndRiftLocalRcon.ps1'
  $result = & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $helper `
    -ServerDir $serverDir -RconPort $rconPort -CommandText $CommandText
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON failed for '$CommandText'. Output: $($result -join [Environment]::NewLine)"
  }
  return ($result -join [Environment]::NewLine)
}

function Stop-ExistingLocalPaper {
  $processes = @(Get-LocalPaperProcesses)
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $serverPort -ErrorAction SilentlyContinue)
  if ($listeners.Count -gt 0 -and $processes.Count -eq 0) {
    throw "Port $serverPort is occupied by a process outside the isolated local Paper server."
  }
  if ($processes.Count -gt 1) {
    throw "Found multiple isolated Paper processes for $serverDir; refusing to guess which one to stop."
  }
  if ($processes.Count -eq 0) { return }

  $rconListeners = @(
    Get-NetTCPConnection -State Listen -LocalPort $rconPort -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty OwningProcess
  )
  if ($rconListeners -notcontains [int]$processes[0].ProcessId) {
    throw "Paper PID $($processes[0].ProcessId) owns $serverPort but not isolated RCON $rconPort; refusing to stop an unverified process."
  }
  if ($processes[0].CommandLine.IndexOf($serverDir, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    try {
      $identityResponse = Invoke-LocalRcon -CommandText 'plugins'
    } catch {
      throw "Paper PID $($processes[0].ProcessId) is not RCON-verifiable as the local End Rift server: $($_.Exception.Message)"
    }
    if ($identityResponse -notmatch 'CopiMineEndEvent') {
      throw "Paper PID $($processes[0].ProcessId) answered RCON but is not the local End Rift server."
    }
  }

  $processId = [int]$processes[0].ProcessId
  Write-Host "Stopping existing isolated Paper PID=$processId before synchronizing current plugins."
  Save-LocalPaperStateBeforeStop
  Invoke-LocalRcon -CommandText 'stop' | Out-Host
  $portClosed = Wait-TcpPort -HostName '127.0.0.1' -Port $serverPort -Expected $false -TimeoutSeconds 35
  $stillRunning = Get-Process -Id $processId -ErrorAction SilentlyContinue
  if ($stillRunning) {
    try { Wait-Process -Id $processId -Timeout 30 -ErrorAction Stop } catch { Write-Warning "Graceful local Paper process stop timed out; stopping only the verified local PID $processId." }
  }
  $stillRunning = Get-Process -Id $processId -ErrorAction SilentlyContinue
  if ($stillRunning) {
    Stop-Process -Id $processId -Force -ErrorAction Stop
  }
  if (-not $portClosed -and -not (Wait-TcpPort -HostName '127.0.0.1' -Port $serverPort -Expected $false -TimeoutSeconds 15)) {
    throw "Local Paper port $serverPort is still occupied after stopping PID $processId."
  }
  Assert-LocalPaperStateAfterStop
}

function Save-LocalPaperStateBeforeStop {
  # Paper normally flushes the world during /stop, but the launcher may be
  # restarted from either a batch file or an agent while players are still
  # online.  Flush explicitly first, then allow the normal plugin shutdown to
  # persist event-state.yml, whitelist.json, ops.json and AuthMe's database.
  try {
    Invoke-LocalRcon -CommandText 'minecraft:save-all flush' | Out-Host
  } catch {
    throw "Refused to stop local Paper because minecraft:save-all flush failed: $($_.Exception.Message)"
  }
  Start-Sleep -Milliseconds 750
  foreach ($path in @($whitelistPath, $opsPath, $authmeDb, $eventStatePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      throw "Refused to stop local Paper because persistent file is missing: $path"
    }
  }
  Write-Host 'Local persistence checkpoint before stop: save-all flush completed; whitelist.json, ops.json, authme.db and event-state.yml are present.'
}

function Assert-LocalPaperStateAfterStop {
  foreach ($path in @($whitelistPath, $opsPath, $authmeDb, $eventStatePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      throw "Local Paper stopped without a persistent file: $path"
    }
  }
  $checkpoint = @(
    "whitelist=$(Get-FileSha256 -Path $whitelistPath)",
    "ops=$(Get-FileSha256 -Path $opsPath)",
    "authme=$(Get-FileSha256 -Path $authmeDb)",
    "eventState=$(Get-FileSha256 -Path $eventStatePath)"
  ) -join ' '
  Write-Host "Local persistence checkpoint after stop: $checkpoint"
}

function Normalize-LocalServerProperties {
  if (-not (Test-Path -LiteralPath $serverPropertiesBaseline -PathType Leaf)) {
    throw "Local server.properties baseline is missing: $serverPropertiesBaseline"
  }
  if ((Get-Item -LiteralPath $serverPropertiesBaseline).Length -gt 1048576) {
    throw "Refused an unexpectedly large local server.properties baseline: $serverPropertiesBaseline"
  }
  if ((Get-Item -LiteralPath $serverProperties).Length -gt 1048576) {
    Copy-Item -LiteralPath $serverPropertiesBaseline -Destination $serverProperties -Force
    Write-Host "Restored oversized local server.properties from the isolated baseline."
  }
  $lines = @(Get-Content -LiteralPath $serverProperties -Encoding UTF8)
  $safePrompt = 'resource-pack-prompt={"text"\:"Для игры на CopiMine нужен ресурспак сервера.","color"\:"gray"}'
  $changed = $false
  for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match '^resource-pack-prompt=' -and $lines[$index].Length -gt 1024) {
      $lines[$index] = $safePrompt
      $changed = $true
    }
  }
  if ($changed) {
    $utf8 = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText($serverProperties, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
    Write-Host 'Normalized the local Paper resource-pack prompt.'
  }
  if ((Get-Item -LiteralPath $serverProperties).Length -gt 1048576) {
    throw "Local server.properties remains unexpectedly large after normalization: $serverProperties"
  }
}

function Normalize-LocalRadminJoinNetworkProperties {
  # Radmin adds a real WAN-sized RTT to the local test path.  Keep the event
  # arena intact while reducing the join burst and preventing a delayed
  # movement packet from becoming a false floating kick.  These keys are
  # written only to the isolated runtime server.properties by design.
  Set-LocalServerProperty -Key 'view-distance' -Value '6'
  Set-LocalServerProperty -Key 'simulation-distance' -Value '4'
  Set-LocalServerProperty -Key 'entity-broadcast-range-percentage' -Value '75'
  Set-LocalServerProperty -Key 'network-compression-threshold' -Value '256'
  Set-LocalServerProperty -Key 'allow-flight' -Value 'true'

  $expected = @{
    'view-distance' = '6'
    'simulation-distance' = '4'
    'entity-broadcast-range-percentage' = '75'
    'network-compression-threshold' = '256'
    'allow-flight' = 'true'
  }
  $actual = @{}
  foreach ($line in Get-Content -LiteralPath $serverProperties -Encoding UTF8) {
    if ($line -match '^([^=]+)=(.*)$' -and $expected.ContainsKey($matches[1])) {
      $actual[$matches[1]] = $matches[2]
    }
  }
  foreach ($key in $expected.Keys) {
    if ($actual[$key] -ne $expected[$key]) {
      throw "Local Radmin join property mismatch for ${key}: expected=$($expected[$key]) actual=$($actual[$key])"
    }
  }
  Write-Host 'Applied the local Radmin join profile: view=6 simulation=4 broadcast=75 compression=256 allow-flight=true.'
}

function Set-LocalPurpurProperty {
  param(
    [Parameter(Mandatory = $true)][string]$Key,
    [Parameter(Mandatory = $true)][string]$Value
  )
  if ((Get-Item -LiteralPath $purpurConfig).Length -gt 1048576) {
    throw "Refused to rewrite an unexpectedly large local purpur.yml: $purpurConfig"
  }
  $lines = @(Get-Content -LiteralPath $purpurConfig -Encoding UTF8)
  $pattern = '^(\s*)' + [Regex]::Escape($Key) + '\s*:'
  $replaced = $false
  for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match $pattern) {
      $lines[$index] = $matches[1] + $Key + ': ' + $Value
      $replaced = $true
    }
  }
  if (-not $replaced) {
    throw "Local purpur.yml does not contain the expected property: $Key"
  }
  $utf8 = [Text.UTF8Encoding]::new($false)
  [IO.File]::WriteAllText($purpurConfig, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
}

function Normalize-LocalPurpurProperties {
  Set-LocalPurpurProperty -Key 'use-alternate-keepalive' -Value 'true'
  $configuredKeepalive = ''
  foreach ($line in Get-Content -LiteralPath $purpurConfig -Encoding UTF8) {
    if ($line -match '^\s*use-alternate-keepalive\s*:\s*(true|false)\s*$') {
      $configuredKeepalive = $matches[1].ToLowerInvariant()
      break
    }
  }
  if ($configuredKeepalive -ne 'true') {
    throw "Local Purpur alternate keep-alive is not enabled: $configuredKeepalive"
  }
  Write-Host 'Enabled Purpur alternate keep-alive for the local Radmin test server.'
}

function Set-LocalEssentialsProperty {
  param(
    [Parameter(Mandatory = $true)][string]$Key,
    [Parameter(Mandatory = $true)][string]$Value
  )
  if (-not (Test-Path -LiteralPath $essentialsConfig -PathType Leaf)) {
    throw "Local Essentials configuration is missing: $essentialsConfig"
  }
  if ((Get-Item -LiteralPath $essentialsConfig).Length -gt 1048576) {
    throw "Refused to edit an unexpectedly large local Essentials configuration: $essentialsConfig"
  }
  $pattern = '^\s*' + [Regex]::Escape($Key) + '\s*:'
  $lines = @(Get-Content -LiteralPath $essentialsConfig -Encoding UTF8)
  $replaced = $false
  for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match $pattern) {
      $indent = $lines[$index].Substring(0, $lines[$index].Length - $lines[$index].TrimStart().Length)
      $lines[$index] = $indent + $Key + ': ' + $Value
      $replaced = $true
    }
  }
  if (-not $replaced) {
    $lines += ($Key + ': ' + $Value)
  }
  $utf8 = [Text.UTF8Encoding]::new($false)
  [IO.File]::WriteAllLines($essentialsConfig, [string[]]$lines, $utf8)
}

function Ensure-LocalVanillaBedRespawn {
  Set-LocalEssentialsProperty -Key 'respawn-listener-priority' -Value 'none'
  $configuredPriority = ''
  foreach ($line in Get-Content -LiteralPath $essentialsConfig -Encoding UTF8) {
    if ($line -match '^\s*respawn-listener-priority\s*:\s*(\S+)') {
      $configuredPriority = [string]$matches[1].Trim('"', "'")
      break
    }
  }
  if ($configuredPriority -ne 'none') {
    throw "Essentials respawn listener is not disabled in the local configuration: $configuredPriority"
  }
  Write-Host 'Configured EssentialsXSpawn to delegate player bed respawn to vanilla.'
}

function Set-LocalServerProperty {
  param(
    [Parameter(Mandatory = $true)][string]$Key,
    [Parameter(Mandatory = $true)][string]$Value
  )
  if ((Get-Item -LiteralPath $serverProperties).Length -gt 1048576) {
    throw "Refused to rewrite an unexpectedly large local server.properties file: $serverProperties"
  }
  $lines = @(Get-Content -LiteralPath $serverProperties -Encoding UTF8)
  $pattern = '^' + [Regex]::Escape($Key) + '='
  $found = $false
  for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match $pattern) {
      $lines[$index] = "$Key=$Value"
      $found = $true
    }
  }
  if (-not $found) { $lines += "$Key=$Value" }
  $utf8 = [Text.UTF8Encoding]::new($false)
  [IO.File]::WriteAllText($serverProperties, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
}

function Build-And-Sync-ResourcePack {
  $python = $websitePython
  $builder = Join-Path $worktreeRoot 'resourcepacks\build-resourcepack.py'
  if (-not (Test-Path -LiteralPath $builder -PathType Leaf)) { throw "Resource-pack builder is missing: $builder" }
  Write-Host 'Building the current resource pack from the checked-out source.'
  Push-Location $worktreeRoot
  try { & $python $builder '--skip-server-properties' } finally { Pop-Location }
  if ($LASTEXITCODE -ne 0) { throw "Resource-pack build failed with exit code $LASTEXITCODE." }
  if (-not (Test-Path -LiteralPath $pack -PathType Leaf)) { throw "Built resource pack is missing: $pack" }

  $sourceHash = Get-FileSha1 -Path $pack
  $sourceSha256 = Get-FileSha256 -Path $pack
  Copy-Item -LiteralPath $pack -Destination $targetPack -Force
  foreach ($sidecar in @('CopiMineResourcePack.sha1', 'CopiMineResourcePack.sha256')) {
    $sidecarPath = Join-Path $sourcePackDir $sidecar
    if (Test-Path -LiteralPath $sidecarPath -PathType Leaf) {
      Copy-Item -LiteralPath $sidecarPath -Destination (Join-Path $targetPackDir $sidecar) -Force
    }
  }
  $targetHash = Get-FileSha1 -Path $targetPack
  if ($targetHash -ne $sourceHash) { throw 'Local resource-pack copy failed SHA-1 verification.' }
  Set-LocalServerProperty -Key 'require-resource-pack' -Value 'true'
  Set-LocalServerProperty -Key 'resource-pack' -Value ("http\://${resourcePackUrlHost}\:${packPort}/CopiMineResourcePack.zip")
  Set-LocalServerProperty -Key 'resource-pack-sha1' -Value $sourceHash
  Write-Host "Resource pack ready SHA1=$sourceHash SHA256=$sourceSha256 bytes=$((Get-Item -LiteralPath $targetPack).Length)"
}

function Get-YamlScalar {
  param(
    [Parameter(Mandatory = $true)][string]$Text,
    [Parameter(Mandatory = $true)][string]$Key
  )
  $match = [Regex]::Match($Text, '(?m)^\s*' + [Regex]::Escape($Key) + '\s*:\s*(.+?)\s*$')
  if (-not $match.Success) { return '' }
  $value = $match.Groups[1].Value.Trim()
  if ($value.Length -ge 2) {
    $first = $value[0]
    $last = $value[$value.Length - 1]
    if (($first -eq [char]39 -and $last -eq [char]39) -or ($first -eq [char]34 -and $last -eq [char]34)) {
      $value = $value.Substring(1, $value.Length - 2)
    }
  }
  return $value
}

function Read-PluginDescriptor {
  param([Parameter(Mandatory = $true)][string]$JarPath)
  Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
  $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
  try {
    $entry = @($archive.Entries | Where-Object {
        $_.FullName -in @('plugin.yml', 'paper-plugin.yml', 'META-INF/plugin.yml', 'META-INF/paper-plugin.yml')
      } | Select-Object -First 1)
    if (-not $entry) { throw "Plugin metadata is missing in $JarPath" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
    $name = Get-YamlScalar -Text $metadata -Key 'name'
    $version = Get-YamlScalar -Text $metadata -Key 'version'
    if ([string]::IsNullOrWhiteSpace($name) -or [string]::IsNullOrWhiteSpace($version)) {
      throw "Plugin metadata in $JarPath has no usable name/version"
    }
    return [pscustomobject]@{
      MetadataPath = $entry.FullName
      PluginName = $name
      PluginVersion = $version
    }
  } finally {
    $archive.Dispose()
  }
}

function Get-CurrentPluginSources {
  if (-not (Test-Path -LiteralPath $pluginManifestPath -PathType Leaf)) {
    throw "Plugin version manifest is missing: $pluginManifestPath"
  }
  $manifest = Get-Content -LiteralPath $pluginManifestPath -Raw | ConvertFrom-Json
  $manifestEntries = @($manifest.plugins.PSObject.Properties)
  if ($manifestEntries.Count -ne 30) {
    throw "Expected 30 current server plugins in $pluginManifestPath, found $($manifestEntries.Count)."
  }
  $sources = @()
  foreach ($entry in $manifestEntries) {
    $fileName = $entry.Name
    $expectedVersion = [string]$entry.Value
    $sourcePath = Join-Path $sourcePluginDir $fileName
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
      throw "Current worktree plugin source is missing: $fileName at $sourcePath"
    }
    $descriptor = Read-PluginDescriptor -JarPath $sourcePath
    if ($descriptor.PluginVersion -ne $expectedVersion) {
      throw "Version mismatch for ${fileName}: manifest=$expectedVersion metadata=$($descriptor.PluginVersion) source=$sourcePath"
    }
    $sources += [pscustomobject]@{
      FileName = $fileName
      ExpectedVersion = $expectedVersion
      SourcePath = $sourcePath
      PluginName = $descriptor.PluginName
      PluginVersion = $descriptor.PluginVersion
      MetadataPath = $descriptor.MetadataPath
    }
  }
  return $sources
}

function Assert-LocalStartupPrerequisites {
  param([Parameter(Mandatory = $true)][object[]]$PluginSources)
  $builder = Join-Path $worktreeRoot 'resourcepacks\build-resourcepack.py'
  $paper = Join-Path $serverDir 'purpur.jar'
  foreach ($required in @(
      $builder,
      $paper,
      $pgCtl,
      $websitePython,
      $serverPropertiesBaseline,
      (Join-Path $scriptRoot 'InvokeEndRiftLocalRcon.ps1'),
      (Join-Path $scriptRoot 'StartEndRiftLocal.ps1'),
      (Join-Path $scriptRoot 'StartEndRiftLocalUserSession.ps1')
    )) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
      throw "Required local startup file is missing: $required"
    }
  }
  $java = Get-Command java.exe -ErrorAction SilentlyContinue
  if ($null -eq $java) { throw 'Java is not available on PATH for the local Paper server.' }
  if ($PluginSources.Count -ne 30) { throw "Expected 30 current plugin sources, found $($PluginSources.Count)." }
}

function Sync-CurrentPlugins {
  $sourceEntries = @(Get-CurrentPluginSources)
  $sourceNames = @($sourceEntries | ForEach-Object FileName)
  $targetJars = @(Get-ChildItem -LiteralPath $targetPluginDir -Filter '*.jar' -File)
  $unexpected = @($targetJars | Where-Object { $sourceNames -notcontains $_.Name })
  if ($unexpected.Count -gt 0) {
    throw "Local plugin directory contains JARs absent from the current manifest: $($unexpected.Name -join ', ')"
  }
  foreach ($sourceEntry in $sourceEntries) {
    $targetJar = Join-Path $targetPluginDir $sourceEntry.FileName
    $needsCopy = -not (Test-Path -LiteralPath $targetJar -PathType Leaf)
    if (-not $needsCopy) {
      $needsCopy = (Get-FileSha256 -Path $sourceEntry.SourcePath) -ne
        (Get-FileSha256 -Path $targetJar)
    }
    if ($needsCopy) {
      Copy-Item -LiteralPath $sourceEntry.SourcePath -Destination $targetJar -Force
      Write-Host "Synchronized plugin $($sourceEntry.FileName) [$($sourceEntry.PluginName) $($sourceEntry.PluginVersion)]."
    }
    $sourceHash = Get-FileSha256 -Path $sourceEntry.SourcePath
    $targetHash = Get-FileSha256 -Path $targetJar
    if ($sourceHash -ne $targetHash) { throw "Plugin hash mismatch after sync: $($sourceEntry.FileName)" }
  }
  Write-Host "Verified current plugin set: $($sourceEntries.Count) JARs, versions from $pluginManifestPath"
  return $sourceEntries
}

function Sync-CurrentEventConfig {
  $sourceText = Get-Content -LiteralPath $sourceEventConfig -Raw -Encoding UTF8
  if ($sourceText -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'Refused to sync a non-local End Rift configuration into the isolated server.'
  }
  $sourceHash = Get-FileSha256 -Path $sourceEventConfig
  $targetHash = if (Test-Path -LiteralPath $eventConfig -PathType Leaf) {
    Get-FileSha256 -Path $eventConfig
  } else {
    ''
  }
  if ($sourceHash -ne $targetHash) {
    Copy-Item -LiteralPath $sourceEventConfig -Destination $eventConfig -Force
    Write-Host 'Synchronized current End Rift config into isolated local Paper.'
  }
  if ((Get-FileSha256 -Path $eventConfig) -ne $sourceHash) {
    throw 'Local End Rift config hash mismatch after sync.'
  }
}

function Ensure-LocalPostgres {
  if (-not (Test-Path -LiteralPath $pgCtl -PathType Leaf)) { throw "Local PostgreSQL pg_ctl is missing: $pgCtl" }
  & $pgCtl -D $pgDataDir status 2>&1 | Out-Host
  if ($LASTEXITCODE -eq 0) { return }

  $postmasterPidPath = Join-Path $pgDataDir 'postmaster.pid'
  if (Test-Path -LiteralPath $postmasterPidPath -PathType Leaf) {
    $stalePid = 0
    $pidLine = Get-Content -LiteralPath $postmasterPidPath -TotalCount 1 -ErrorAction Stop
    $pidLine = ([string]$pidLine).Trim()
    [int]::TryParse($pidLine, [ref]$stalePid) | Out-Null
    $staleProcess = if ($stalePid -gt 0) { Get-Process -Id $stalePid -ErrorAction SilentlyContinue } else { $null }
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $postgresPort -ErrorAction SilentlyContinue)
    if ($null -ne $staleProcess -or $listeners.Count -gt 0 -or (Test-TcpPort -HostName '127.0.0.1' -Port $postgresPort)) {
      throw "Local PostgreSQL has a live process or listener while pg_ctl reports it stopped; refusing to move $postmasterPidPath."
    }
    $staleBackup = "$postmasterPidPath.stale-$((Get-Date).ToString('yyyyMMdd-HHmmss'))"
    Move-Item -LiteralPath $postmasterPidPath -Destination $staleBackup -Force
    Write-Host "Moved confirmed stale PostgreSQL pid file to $staleBackup."
  }

  if (Test-TcpPort -HostName '127.0.0.1' -Port $postgresPort) {
    throw "Port $postgresPort is occupied but is not owned by the approved local PostgreSQL cluster."
  }
  Write-Host "Starting isolated PostgreSQL on 127.0.0.1:$postgresPort."
  $pgCtlOut = Join-Path $logsDir 'postgres-ctl.start.stdout.log'
  $pgCtlErr = Join-Path $logsDir 'postgres-ctl.start.stderr.log'
  $postgresLog = Join-Path $logsDir 'postgres.log'
  Remove-Item -LiteralPath $pgCtlOut, $pgCtlErr, $postgresLog -Force -ErrorAction SilentlyContinue
  $pgCtlArgs = "-D `"$pgDataDir`" -l `"$postgresLog`" -o `"-p $postgresPort -h 127.0.0.1`" -w start"
  $pgCtlProcess = Start-Process -FilePath $pgCtl `
    -ArgumentList $pgCtlArgs `
    -RedirectStandardOutput $pgCtlOut `
    -RedirectStandardError $pgCtlErr `
    -WindowStyle Hidden `
    -PassThru
  $ready = Wait-TcpPort -HostName '127.0.0.1' -Port $postgresPort -Expected $true -TimeoutSeconds 60
  if (-not $ready) {
    $ctlError = if (Test-Path -LiteralPath $pgCtlErr) { (Get-Content -LiteralPath $pgCtlErr -Raw).Trim() } else { '' }
    $serverError = if (Test-Path -LiteralPath $postgresLog) { (Get-Content -LiteralPath $postgresLog -Tail 20 -ErrorAction SilentlyContinue) -join "`n" } else { '' }
    throw "Isolated PostgreSQL did not become ready. pg_ctl stderr=$ctlError postgres log=$serverError"
  }
  if (-not $pgCtlProcess.HasExited) {
    $pgCtlProcess.WaitForExit(5000) | Out-Null
  }
  $statusOutput = @(& $pgCtl -D $pgDataDir status 2>&1)
  $statusExit = $LASTEXITCODE
  if ($statusExit -ne 0) {
    $ctlError = if (Test-Path -LiteralPath $pgCtlErr) { ([string](Get-Content -LiteralPath $pgCtlErr -Raw -ErrorAction SilentlyContinue)).Trim() } else { '' }
    $statusText = ($statusOutput -join "`n").Trim()
    throw "Isolated PostgreSQL did not remain running after startup. pg_ctl status=$statusText stderr=$ctlError"
  }
}

function Ensure-ResourcePackHttpServer {
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $packPort -ErrorAction SilentlyContinue)
  $existingPid = 0
  if (Test-Path -LiteralPath $packPidFile -PathType Leaf) {
    [int]::TryParse((Get-Content -LiteralPath $packPidFile -Raw).Trim(), [ref]$existingPid) | Out-Null
  }
  $existing = if ($existingPid -gt 0) { Get-CimInstance Win32_Process -Filter "ProcessId=$existingPid" -ErrorAction SilentlyContinue } else { $null }
  $httpServerPattern = '(?i)-m\s+http\.server\s+' + [Regex]::Escape([string]$packPort)
  $bindPattern = '(?i)--bind\s+' + [Regex]::Escape($resourcePackBindAddress)
  $expectedListener = @(
    $listeners | Where-Object {
      [string]$_.LocalAddress -eq $resourcePackBindAddress -or
      [string]$_.LocalAddress -in @('0.0.0.0', '::')
    }
  )
  if ($listeners.Count -gt 0) {
    if (-not $existing -or $existing.CommandLine -notmatch $httpServerPattern) {
      throw "Port $packPort is occupied by an unknown HTTP process; refusing to reuse it for the resource pack."
    }
    if ($expectedListener.Count -eq 0 -or $existing.CommandLine -notmatch $bindPattern) {
      Write-Host "Restarting local resource-pack HTTP server PID=$existingPid for Radmin binding $resourcePackBindAddress."
      Stop-Process -Id $existingPid -Force -ErrorAction Stop
      if (-not (Wait-TcpPort -HostName '127.0.0.1' -Port $packPort -Expected $false -TimeoutSeconds 15)) {
        throw "Resource-pack HTTP port $packPort is still occupied after stopping verified PID $existingPid."
      }
      $listeners = @()
      Remove-Item -LiteralPath $packPidFile -Force -ErrorAction SilentlyContinue
    } else {
      Write-Host "Reusing local resource-pack HTTP server PID=$existingPid bound to $resourcePackBindAddress."
    }
  } else {
    if ($existing) { Remove-Item -LiteralPath $packPidFile -Force -ErrorAction SilentlyContinue }
  }
  if ($listeners.Count -eq 0) {
    $python = (Get-Command python.exe -ErrorAction Stop).Source
    $httpOut = Join-Path $logsDir 'resource-pack-http.out.log'
    $httpErr = Join-Path $logsDir 'resource-pack-http.err.log'
    $process = Start-Process -FilePath $python `
      -ArgumentList @('-m', 'http.server', [string]$packPort, '--bind', $resourcePackBindAddress) `
      -WorkingDirectory $targetPackDir `
      -RedirectStandardOutput $httpOut `
      -RedirectStandardError $httpErr `
      -WindowStyle Hidden `
      -PassThru
    Set-Content -LiteralPath $packPidFile -Value $process.Id -Encoding ASCII
    if (-not (Wait-TcpPort -HostName $resourcePackBindAddress -Port $packPort -Expected $true -TimeoutSeconds 15)) {
      throw "Resource-pack HTTP server PID $($process.Id) did not open port $packPort."
    }
    Write-Host "Started local resource-pack HTTP server PID=$($process.Id) at http://${resourcePackUrlHost}:$packPort/"
  }

  $probe = Join-Path $localRuntimeRoot 'resource-pack-http-probe.zip'
  try {
    Invoke-WebRequest -UseBasicParsing -Uri "http://${resourcePackUrlHost}:$packPort/CopiMineResourcePack.zip" -OutFile $probe -TimeoutSec 15
    $servedHash = Get-FileSha1 -Path $probe
    $expectedHash = Get-FileSha1 -Path $targetPack
    if ($servedHash -ne $expectedHash) { throw "HTTP resource-pack hash mismatch: served=$servedHash expected=$expectedHash" }
    Write-Host "Verified resource-pack HTTP response SHA1=$servedHash."
  } finally {
    Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
  }
}

function Assert-LocalResourcePackPortSafe {
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $packPort -ErrorAction SilentlyContinue)
  if ($listeners.Count -eq 0) { return }
  $existingPid = 0
  if (Test-Path -LiteralPath $packPidFile -PathType Leaf) {
    [int]::TryParse((Get-Content -LiteralPath $packPidFile -Raw).Trim(), [ref]$existingPid) | Out-Null
  }
  if ($existingPid -le 0) {
    throw "Resource-pack HTTP port $packPort is occupied without a verifiable local PID file."
  }
  $process = Get-CimInstance Win32_Process -Filter "ProcessId=$existingPid" -ErrorAction SilentlyContinue
  $commandLine = if ($process) { [string]$process.CommandLine } else { '' }
  if (-not $process -or $commandLine -notmatch '(?i)-m\s+http\.server\s+' + [Regex]::Escape([string]$packPort)) {
    throw "Resource-pack HTTP port $packPort is occupied by an unverified process; refusing to stop it."
  }
  $owners = @($listeners | Select-Object -ExpandProperty OwningProcess | Sort-Object -Unique)
  if ($owners.Count -ne 1 -or [int]$owners[0] -ne $existingPid) {
    throw "Resource-pack HTTP port $packPort is not exclusively owned by the verified local process PID=$existingPid."
  }
}

function Get-LocalWebsiteProcessFromPidFile {
  $processId = 0
  if (Test-Path -LiteralPath $websitePidFile -PathType Leaf) {
    [int]::TryParse((Get-Content -LiteralPath $websitePidFile -Raw).Trim(), [ref]$processId) | Out-Null
  }
  if ($processId -le 0) { return $null }
  return Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
}

function Assert-LocalWebsiteProcessIdentity {
  param([Parameter(Mandatory = $true)]$Process)
  $commandLine = [string]$Process.CommandLine
  if ([string]::IsNullOrWhiteSpace($commandLine) -or
      $commandLine -notmatch '(?i)backend\.main:app' -or
      $commandLine -notmatch '(?i)--port\s+' + [Regex]::Escape([string]$websitePort) -or
      $commandLine.IndexOf($adminWebDir, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw "Refused to stop an unverified process on local website port $websitePort."
  }
}

function Assert-LocalWebsitePortSafe {
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $websitePort -ErrorAction SilentlyContinue)
  $process = Get-LocalWebsiteProcessFromPidFile
  if ($process) { Assert-LocalWebsiteProcessIdentity -Process $process }
  $verifiedIds = @()
  if ($process) { $verifiedIds += [int]$process.ProcessId }
  foreach ($ownerId in @($listeners | ForEach-Object { [int]$_.OwningProcess } | Sort-Object -Unique)) {
    $listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerId" -ErrorAction SilentlyContinue
    if (-not $listenerProcess) {
      throw "Local website port $websitePort is owned by PID $ownerId, but its process could not be inspected."
    }
    Assert-LocalWebsiteProcessIdentity -Process $listenerProcess
    $verifiedIds += [int]$listenerProcess.ProcessId
  }
  if ($listeners.Count -gt 0 -and @($verifiedIds | Sort-Object -Unique).Count -eq 0) {
    throw "Port $websitePort is occupied but no verified local website process was found."
  }
}

function Stop-ExistingLocalWebsite {
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $websitePort -ErrorAction SilentlyContinue)
  $process = Get-LocalWebsiteProcessFromPidFile
  $verifiedWebsiteProcessIds = @()
  if ($process) {
    Assert-LocalWebsiteProcessIdentity -Process $process
    $verifiedWebsiteProcessIds += [int]$process.ProcessId
  }
  foreach ($ownerId in @($listeners | ForEach-Object { [int]$_.OwningProcess } | Sort-Object -Unique)) {
    $listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerId" -ErrorAction SilentlyContinue
    if (-not $listenerProcess) {
      throw "Local website port $websitePort is owned by PID $ownerId, but its process could not be inspected."
    }
    Assert-LocalWebsiteProcessIdentity -Process $listenerProcess
    $verifiedWebsiteProcessIds += [int]$listenerProcess.ProcessId
  }
  $verifiedWebsiteProcessIds = @($verifiedWebsiteProcessIds | Sort-Object -Unique)
  if ($listeners.Count -eq 0) {
    foreach ($processId in $verifiedWebsiteProcessIds) {
      if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        Write-Host "Stopping orphaned isolated local website PID=$processId."
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
      }
    }
    Remove-Item -LiteralPath $websitePidFile -Force -ErrorAction SilentlyContinue
    return
  }
  if ($verifiedWebsiteProcessIds.Count -eq 0) {
    throw "Port $websitePort is occupied but no verified local website process was found."
  }
  Write-Host "Stopping existing isolated local website process tree: $($verifiedWebsiteProcessIds -join ', ')."
  foreach ($processId in $verifiedWebsiteProcessIds) {
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
      Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
  }
  if (-not (Wait-TcpPort -HostName '127.0.0.1' -Port $websitePort -Expected $false -TimeoutSeconds 15)) {
    throw "Local website port $websitePort is still occupied after stopping verified process tree $($verifiedWebsiteProcessIds -join ', ')."
  }
  Remove-Item -LiteralPath $websitePidFile -Force -ErrorAction SilentlyContinue
}

function Start-LocalWebsite {
  Ensure-LocalWebsiteEnvironment
  if (-not (Test-Path -LiteralPath $websitePython -PathType Leaf)) {
    throw "The prepared local website Python runtime is missing: $websitePython"
  }
  Stop-ExistingLocalWebsite
  $env:COPIMINE_ENV_FILE = $websiteEnvFile
  $env:PYTHONUNBUFFERED = '1'
  Remove-Item -LiteralPath $websiteOutLog, $websiteErrLog -Force -ErrorAction SilentlyContinue
  $process = Start-Process -FilePath $websitePython `
    -ArgumentList @('-m', 'uvicorn', 'backend.main:app', '--app-dir', $adminWebDir, '--host', '127.0.0.1', '--port', [string]$websitePort, '--log-level', 'info') `
    -WorkingDirectory $adminWebDir `
    -RedirectStandardOutput $websiteOutLog `
    -RedirectStandardError $websiteErrLog `
    -WindowStyle Hidden `
    -PassThru
  Set-Content -LiteralPath $websitePidFile -Value $process.Id -Encoding ASCII
  if (-not (Wait-TcpPort -HostName '127.0.0.1' -Port $websitePort -Expected $true -TimeoutSeconds 60)) {
    if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
      $details = (Get-Content -LiteralPath $websiteErrLog -Tail 80 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
      throw "Local website exited during startup. $details"
    }
    throw "Local website did not open port $websitePort. See $websiteErrLog"
  }

  $health = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/api/health" -TimeoutSec 20
  if ([int]$health.StatusCode -ne 200) { throw "Local website health returned HTTP $($health.StatusCode)." }
  $healthJson = $health.Content | ConvertFrom-Json
  if (-not [bool]$healthJson.ok) {
    throw "Local website health is not ready: $($health.Content)"
  }
  $index = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/" -TimeoutSec 20
  if ([int]$index.StatusCode -ne 200 -or $index.Content -notmatch 'CopiMine') {
    throw 'Local website frontend shell did not return a valid CopiMine page.'
  }

  $websitePackProbe = Join-Path $localRuntimeRoot 'copimine-local-web-resourcepack-probe.zip'
  try {
    Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/resourcepacks/CopiMineResourcePack.zip" -OutFile $websitePackProbe -TimeoutSec 30
    $servedPackHash = Get-FileSha1 -Path $websitePackProbe
    $expectedPackHash = Get-FileSha1 -Path $pack
    if ($servedPackHash -ne $expectedPackHash) {
      throw "Website resource-pack hash mismatch: served=$servedPackHash expected=$expectedPackHash"
    }
  } finally {
    Remove-Item -LiteralPath $websitePackProbe -Force -ErrorAction SilentlyContinue
  }

  $webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $loginBody = @{ username = 'localadmin'; password = 'localadmin123'; remember_me = $true } | ConvertTo-Json -Compress
  $login = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -WebSession $webSession -TimeoutSec 20
  $loginJson = $login.Content | ConvertFrom-Json
  if ([int]$login.StatusCode -ne 200 -or $loginJson.role -ne 'owner') {
    throw "Local website admin login did not return owner access: $($login.Content)"
  }
  $config = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/api/config" -WebSession $webSession -TimeoutSec 20
  $configJson = $config.Content | ConvertFrom-Json
  if (-not [bool]$configJson.features.postgresPool -or $configJson.features.authBackend -ne 'postgresql' -or -not [bool]$configJson.features.coreprotectDbExists) {
    throw "Local website database/runtime configuration is not ready: $($config.Content)"
  }
  $publicStatus = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/api/public/status" -TimeoutSec 30
  if ([int]$publicStatus.StatusCode -ne 200) { throw "Local website public status returned HTTP $($publicStatus.StatusCode)." }
  Write-Host "Local website is ready at http://127.0.0.1:$websitePort/; PostgreSQL, frontend, admin login and website-served resource pack verified."
}

function Start-LocalPaper {
  $outLog = Join-Path $logsDir 'paper.out.log'
  $errLog = Join-Path $logsDir 'paper.err.log'
  $starter = Join-Path $scriptRoot 'StartEndRiftLocal.ps1'
  & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $starter `
    -ServerDir $serverDir -LogPath $outLog -ErrPath $errLog -ReadyTimeoutSeconds 180
  if ($LASTEXITCODE -ne 0) { throw "Local Paper startup failed with exit code $LASTEXITCODE." }
}

function Ensure-LocalCurrentMap {
  # The current local world is user-owned test data.  A normal restart must
  # never run the one-shot scene builder, fill/clear blocks, place command
  # blocks, or rebind the Gate.  The explicit SetupEndRiftLocalScene.ps1
  # script remains available only when the operator deliberately requests a
  # disposable scene rebuild.
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  if ([string]::IsNullOrWhiteSpace(($status -join [Environment]::NewLine))) {
    throw 'Local End Rift status was empty; refusing to claim the current map is ready.'
  }
  Write-Host 'Preserving current local map; no End Rift scene rebuild was requested.'
  Write-Host (($status -join [Environment]::NewLine).Trim())
}

function Grant-LocalWhitelistOperators {
  if (-not (Test-Path -LiteralPath $whitelistPath -PathType Leaf)) {
    throw "Local whitelist is missing: $whitelistPath"
  }
  if (-not (Test-Path -LiteralPath $authmeDb -PathType Leaf)) {
    throw "Local AuthMe database is missing: $authmeDb"
  }

  $python = (Get-Command python.exe -ErrorAction Stop).Source
  $queryScript = @'
import json
import re
import sqlite3
import sys

authme_path, whitelist_path = sys.argv[1], sys.argv[2]
with sqlite3.connect(authme_path) as connection:
    rows = connection.execute("SELECT username, realname FROM authme").fetchall()

accounts = {}
for username, realname in rows:
    for value in (username, realname):
        if value:
            accounts[str(value).casefold()] = str(realname or username)

with open(whitelist_path, encoding="utf-8") as stream:
    entries = json.load(stream)
    if not isinstance(entries, list):
        raise SystemExit("Local whitelist.json must contain a JSON array.")

for entry in entries:
    if not isinstance(entry, dict):
        continue
    name = str(entry.get("name") or "").strip()
    if re.fullmatch(r"[A-Za-z0-9_]{1,16}", name) and name.casefold() in accounts:
        print(accounts[name.casefold()])
'@
  $queryPath = Join-Path $logsDir 'local-whitelist-authme.py'
  $utf8 = [Text.UTF8Encoding]::new($false)
  try {
    [IO.File]::WriteAllText($queryPath, $queryScript, $utf8)
    $matchedNames = @(& $python $queryPath $authmeDb $whitelistPath)
    $pythonExit = $LASTEXITCODE
  } finally {
    Remove-Item -LiteralPath $queryPath -Force -ErrorAction SilentlyContinue
  }
  if ($pythonExit -ne 0) {
    throw "Could not read local whitelist/AuthMe intersection."
  }
  $opNames = @(
    $matchedNames |
      ForEach-Object { ([string]$_).Trim() } |
      Where-Object { $_ -match '^[A-Za-z0-9_]{1,16}$' } |
      Select-Object -Unique
  )
  foreach ($name in $opNames) {
    $command = "op $name"
    $opResponse = Invoke-LocalRcon -CommandText $command
    if ($opResponse) { Write-Host $opResponse }
  }
  if ($opNames.Count -eq 0) {
    Write-Host 'Local whitelist/AuthMe OP sync: 0 matching accounts.'
  } else {
    Write-Host "Local whitelist/AuthMe OP sync: granted OP to $($opNames.Count) account(s): $($opNames -join ', ')."
  }
}

function Get-MinecraftLauncherPath {
  $candidates = @()
  if (-not [string]::IsNullOrWhiteSpace($env:COPIMINE_LAUNCHER_PATH)) {
    $candidates += $env:COPIMINE_LAUNCHER_PATH
  }
  if (-not [string]::IsNullOrWhiteSpace($env:APPDATA)) {
    $candidates += Join-Path $env:APPDATA '.minecraft\TLauncher.exe'
  }
  if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
    $candidates += Join-Path $env:USERPROFILE '.minecraft\TLauncher.exe'
  }
  $candidates += @(
    'D:\.minecraft\TLauncher.exe',
    'C:\.minecraft\TLauncher.exe'
  )
  foreach ($candidate in @($candidates | Select-Object -Unique)) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
      return (Resolve-Path -LiteralPath $candidate).Path
    }
  }
  return ''
}

function Launch-MinecraftClientIfNeeded {
  if (-not $LaunchClient) { return }
  $client = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
      Where-Object {
        $_.Name -ieq 'javaw.exe' -and
        $_.CommandLine -and
        $_.CommandLine -match '(?i)net\.fabricmc\.loader\.impl\.launch\.knot\.KnotClient'
      })
  if ($client.Count -gt 0) {
    Write-Host "Minecraft client is already running (PID=$($client[0].ProcessId)); leaving it untouched."
    return
  }
  $runningLauncher = @(Get-Process -Name 'TLauncher' -ErrorAction SilentlyContinue)
  if ($runningLauncher.Count -gt 0) {
    Write-Host "TLauncher is already running (PID=$($runningLauncher[0].Id)); leaving it untouched."
    return
  }
  $launcher = Get-MinecraftLauncherPath
  if ([string]::IsNullOrWhiteSpace($launcher)) {
    throw 'Minecraft launcher was requested but no supported launcher was found.'
  }
  Start-Process -FilePath $launcher -WorkingDirectory (Split-Path -Parent $launcher) | Out-Null
Write-Host "TLauncher started from $launcher. Select the local 1.21.1 profile and connect to ${resourcePackUrlHost}:$serverPort."
}

$currentPluginSources = @(Get-CurrentPluginSources)
Assert-LocalStartupPrerequisites -PluginSources $currentPluginSources
$trackedProductionPropertiesSha256 = Assert-TrackedProductionPropertiesClean
$radminVpnAddress = Get-RadminVpnAddress
if (-not [string]::IsNullOrWhiteSpace($radminVpnAddress)) {
  $resourcePackBindAddress = $radminVpnAddress
  $resourcePackUrlHost = $radminVpnAddress
  Write-Host "Radmin VPN resource-pack endpoint: http://${resourcePackUrlHost}:$packPort/CopiMineResourcePack.zip"
} else {
  Write-Warning 'Radmin VPN IPv4 address was not found; resource pack will remain available only on 127.0.0.1.'
}
Ensure-LocalPostgres
Ensure-LocalWebsiteEnvironment
Assert-LocalResourcePackPortSafe
Assert-LocalWebsitePortSafe
Stop-ExistingLocalPaper
Normalize-LocalPurpurProperties
Ensure-LocalVanillaBedRespawn
Normalize-LocalServerProperties
Normalize-LocalRadminJoinNetworkProperties
Build-And-Sync-ResourcePack
if ((Get-FileSha256 -Path (Join-Path $worktreeRoot 'minecraft\server\server.properties')) -ne $trackedProductionPropertiesSha256) {
  throw 'Local resource-pack preparation changed tracked production minecraft/server/server.properties.'
}
$currentPluginSources = @(Sync-CurrentPlugins)
Sync-CurrentEventConfig
Set-LocalServerProperty -Key 'server-port' -Value ([string]$serverPort)
Set-LocalServerProperty -Key 'enable-rcon' -Value 'true'
Set-LocalServerProperty -Key 'rcon.port' -Value ([string]$rconPort)
Ensure-ResourcePackHttpServer
Start-LocalPaper
Ensure-LocalCurrentMap
Start-LocalWebsite

$pluginResponse = Invoke-LocalRcon -CommandText 'plugins'
$expectedPlugins = @($currentPluginSources | ForEach-Object PluginName | Sort-Object -Unique)
foreach ($plugin in $expectedPlugins) {
  if ($pluginResponse -notmatch [Regex]::Escape($plugin)) {
    throw "Active Paper plugin list does not contain $plugin. Actual response: $pluginResponse"
  }
}
Write-Host "Verified all expected plugins are loaded ($($expectedPlugins.Count)): $($expectedPlugins -join ', ')"

$status = Invoke-LocalRcon -CommandText 'cmend status'
if ([string]::IsNullOrWhiteSpace(($status -join [Environment]::NewLine))) {
  throw "Local End Rift status is empty after preserving the current map."
}
Write-Host $status

Grant-LocalWhitelistOperators
$opResponse = Invoke-LocalRcon -CommandText ("op " + $AdminNickname)
if ($opResponse) { Write-Host $opResponse }
$opsPath = Join-Path $serverDir 'ops.json'
if ((Get-Content -LiteralPath $opsPath -Raw) -notmatch ('"name"\s*:\s*"' + [Regex]::Escape($AdminNickname) + '"')) {
  throw "OP grant was not persisted for $AdminNickname in ops.json."
}
Write-Host "Local OP persisted for $AdminNickname."

Launch-MinecraftClientIfNeeded
Write-Host "LOCAL_USER_SESSION_READY server=${resourcePackUrlHost}:$serverPort website=http://127.0.0.1:$websitePort resource-pack=http://${resourcePackUrlHost}:$packPort/CopiMineResourcePack.zip radmin=$radminVpnAddress branch=$branch head=$head"
