[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'ShardProbe',
  [int]$TimeoutSeconds = 100
)

# Local-only authenticated-artifact integration probe. It uses the checked-out
# Artifacts API through /cmartifacts admin give, real player packets, the
# isolated End world, and the actual End Rift event state file.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path (Join-Path $root '..\..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$psqlPath = Join-Path $repoRoot 'local-runtime\postgresql\pgsql\bin\psql.exe'
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftShardProbe.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$statePath = Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml'
$botLogDirectory = Join-Path $runtimeRoot 'shard-passive-bots'
$botLogPath = Join-Path $botLogDirectory ($BotName + '.log')
$botErrPath = Join-Path $botLogDirectory ($BotName + '.err.log')
$process = $null
$openedEnd = $false
$permissionAdded = $false
$sectionSign = [char]0x00A7

if ((Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: shard probe requires environment: local.'
}
foreach ($path in @($botScript, $paperLog, $statePath)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Required local shard probe file is missing: $path"
  }
}
if (-not (Test-Path -LiteralPath $psqlPath -PathType Leaf)) {
  throw "Required local PostgreSQL client is missing: $psqlPath"
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON command failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Read-SharedText {
  param([Parameter(Mandatory = $true)][string]$Path)
  $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally {
    $stream.Dispose()
  }
}

function Invoke-LocalSql {
  param([Parameter(Mandatory = $true)][string]$Query)
  $output = & $psqlPath -h 127.0.0.1 -p 55433 -U copimine -d copimine -At -c $Query | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local PostgreSQL query failed: $Query`n$output"
  }
  return $output.Trim()
}

function Wait-Condition {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Condition,
    [Parameter(Mandatory = $true)][string]$Failure,
    [int]$WaitSeconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    if (& $Condition) { return }
    Start-Sleep -Milliseconds 250
  }
  throw $Failure
}

function Wait-LogMarker {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int]$AfterOffset,
    [int]$WaitSeconds = $TimeoutSeconds
  )
  Wait-Condition -WaitSeconds $WaitSeconds -Failure "Timed out waiting for local log marker '$Pattern'." -Condition {
    $text = Read-SharedText -Path $paperLog
    $tail = if ($AfterOffset -lt $text.Length) { $text.Substring($AfterOffset) } else { '' }
    return $tail -match $Pattern
  }
}

function Start-ShardBot {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  Remove-Item -LiteralPath $botLogPath, $botErrPath -Force -ErrorAction SilentlyContinue
  return Start-Process -FilePath (Get-Command node.exe -ErrorAction Stop).Source `
    -ArgumentList @($botScript, $BotName, '90000', 'admin-grant') `
    -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $botLogPath `
    -RedirectStandardError $botErrPath -PassThru
}

try {
  $null = Invoke-LocalRcon ("whitelist add " + $BotName)
  $null = Invoke-LocalRcon ("op " + $BotName)
  $endStatus = Invoke-LocalRcon -CommandText 'cmworld end status'
  $endStatus = $endStatus -replace ($sectionSign + '.'), ''
  if ($endStatus -match '(?i)\u0437\u0430\u043a\u0440\u044b\u0442') {
    $openResponse = Invoke-LocalRcon -CommandText 'cmworld end open'
    $openResponse = $openResponse -replace ($sectionSign + '.'), ''
    if ($openResponse -notmatch '(?i)\u043e\u0442\u043a\u0440\u044b\u0442|already|open') {
      throw "Local End could not be opened for the shard probe: $openResponse"
    }
    $null = ($openedEnd = $true)
  }

  $logOffset = (Read-SharedText -Path $paperLog).Length
  $process = Start-ShardBot
  $joined = $false
  $uuid = ''
  Wait-Condition -Failure "Shard probe player $BotName did not join the isolated Paper server." -Condition {
    if ($process.HasExited) { return $false }
    $list = Invoke-LocalRcon -CommandText 'list'
    $null = ($joined = $list -match [Regex]::Escape($BotName))
    if (-not $joined) { return $false }
    $output = ''
    if (Test-Path -LiteralPath $botLogPath) { $output = Read-SharedText -Path $botLogPath }
    $match = [Regex]::Match($output, 'PLAYER_JOIN\s+' + [Regex]::Escape($BotName) + '\s+uuid=([0-9a-fA-F-]{36})')
    return $joined -and $match.Success
  } -WaitSeconds 45
  # Wait-Condition invokes callbacks in a child scope, so read the UUID from
  # the completed bot log in the caller scope before using it in SQL queries.
  $joinMatch = [Regex]::Match((Read-SharedText -Path $botLogPath), 'PLAYER_JOIN\s+' + [Regex]::Escape($BotName) + '\s+uuid=([0-9a-fA-F-]{36})')
  if (-not $joinMatch.Success) { throw "Shard probe player $BotName joined but did not publish a UUID." }
  $uuid = $joinMatch.Groups[1].Value

  # AuthMe/permissions can refresh an online offline-mode player after the
  # operator list is loaded. Set an explicit disposable admin permission once
  # the player is online so the real player-only Artifacts path is exercised.
  $null = Invoke-LocalRcon -CommandText ("lp user " + $BotName + " permission set copimine.admin true")
  $permissionAdded = $true
  Write-Host 'PASS disposable player permission is active for the player-only Artifacts API'

  # The grant is asynchronous. The client log proves that the received
  # physical item is an ECHO_SHARD, while the read-only PostgreSQL query
  # proves the same owner-bound delivery reached CLAIMED/DELIVERED state.
  Wait-Condition -Failure 'Authenticated rift_core_shard was not delivered through Artifacts.' -Condition {
    $output = if (Test-Path -LiteralPath $botLogPath) { Read-SharedText -Path $botLogPath } else { '' }
    $inventoryPattern = '(?m)^INVENTORY\s+' + [Regex]::Escape($BotName) + '\s+[^\r\n]*:echo_shard:'
    $null = ($inventoryMatch = [Text.RegularExpressions.Regex]::IsMatch($output, $inventoryPattern))
    $delivery = [string](Invoke-LocalSql ("SELECT d.status || '|' || i.status || '|' || i.unique_item_id FROM copimine.artifact_pending_deliveries d JOIN copimine.artifact_item_instances i ON i.unique_item_id=d.unique_item_id WHERE d.player_uuid='" + $uuid + "' AND d.item_id='rift_core_shard' ORDER BY d.created_at DESC LIMIT 1"))
    $null = ($deliveryMatch = [Text.RegularExpressions.Regex]::IsMatch($delivery.Trim(), '(?i)^CLAIMED\|DELIVERED\|[0-9a-f-]{36}$'))
    return $inventoryMatch -and $deliveryMatch
  } -WaitSeconds 45
  Write-Host 'PASS authentic Rift Core Shard is physical and owner-bound in local PostgreSQL'

  # The bot activates the delivered shard through the normal player interaction
  # path.  This avoids the local Essentials RCON adapter reducing an
  # `execute in` command to an Overworld-only `/tp`.
  # A custom Bukkit world can legitimately use minecraft:overworld as its
  # dimension type, so NBT Dimension is not an authoritative world-name
  # assertion.  The plugin emits the destination Bukkit world after the real
  # Player#teleport call; match that durable server-side evidence instead.
  $teleportOffset = (Read-SharedText -Path $paperLog).Length
  $portalInfo = Invoke-LocalRcon -CommandText 'cmend portalroom info'
  $portalInfo = $portalInfo -replace ($sectionSign + '.'), ''
  $portalMatch = [Regex]::Match($portalInfo, '(?i)portalroom=([^ ]+)\s+[-0-9.]+,[-0-9.]+,[-0-9.]+')
  if (-not $portalMatch.Success) { throw "Could not resolve the persisted local portal room: $portalInfo" }
  $portalWorld = $portalMatch.Groups[1].Value
  $teleportPattern = 'RIFT_SHARD_TELEPORT.*player=' + [Regex]::Escape($uuid) + '.*world=' + [Regex]::Escape($portalWorld)
  Wait-LogMarker -Pattern $teleportPattern -AfterOffset $teleportOffset -WaitSeconds 15
  Write-Host ("PASS shard channel used the real player teleport into persisted local portal world=" + $portalWorld)
  Write-Host 'NOT VERIFIED passive End effects/Abyss Anchor in this run: the preserved current map stores its portal room in CopiMine, not the authoritative End world.'
  Write-Host ("SHARD_ACTIVE_LIVE_PASS player=" + $BotName + " uuid=" + $uuid + " portal-world=" + $portalWorld)
} finally {
  if ($process -and -not $process.HasExited) {
    try { $process.CloseMainWindow() | Out-Null } catch {}
    try { $process.Kill() } catch {}
  }
  if ($process) { try { $process.WaitForExit(5000) } catch {} }
  try { $null = Invoke-LocalRcon ("deop " + $BotName) } catch {}
  if ($permissionAdded) {
    try { $null = Invoke-LocalRcon ("lp user " + $BotName + " permission unset copimine.admin") } catch {}
  }
  try { $null = Invoke-LocalRcon ("whitelist remove " + $BotName) } catch {}
  if ($openedEnd) {
    try { $null = Invoke-LocalRcon 'cmworld end close confirm' } catch {}
  }
}
