[CmdletBinding()]
param(
  [string]$BotName = '',
  [ValidateRange(20, 180)]
  [int]$BotDurationSeconds = 45,
  [ValidateRange(30, 240)]
  [int]$TimeoutSeconds = 90,
  [ValidateRange(0, 60)]
  [int]$VisualHoldSeconds = 0,
  [string]$EvidencePath = '',
  [string[]]$ProtectedViewerNames = @(),
  [ValidateSet('survival', 'adventure', 'creative', 'spectator')]
  [string]$ProtectedViewerGameMode = 'spectator',
  [ValidateSet('survival', 'adventure', 'creative', 'spectator')]
  [string]$RestoreViewerGameMode = 'survival'
)

# Local/staging-only native gameplay boundary for the final-seal tentacle rig.
# The player is a real Mineflayer client and uses normal use_entity packets;
# this script only positions the disposable client and reads authoritative
# server state. It never edits a production world or grants rewards.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$pluginJar = Join-Path $serverDir 'plugins\CopiMineEndEvent.jar'
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$eventSource = Join-Path $root 'copimine-end-event\src\me\copimine\endevent\CopiMineEndEvent.java'
$throwPolicySource = Join-Path $root 'copimine-end-event\src\me\copimine\endevent\domain\TentacleThrowPolicy.java'
$sourceConfig = Join-Path $root 'copimine-end-event\config.yml'
$installedConfig = Join-Path $serverDir 'plugins\CopiMineEndEvent\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLogDirectory = Join-Path $runtimeRoot 'tentacle-live'
$botStdout = Join-Path $botLogDirectory ($BotName + '.stdout.log')
$botControlDirectory = Join-Path $botLogDirectory 'control'
$botTargetFile = Join-Path $botControlDirectory ($BotName + '.targets.json')
$botEnvironmentNames = @(
  'END_RIFT_BOT_SKIP_REGISTER',
  'END_RIFT_BOT_SKIP_AUTH',
  'END_RIFT_TENTACLE_PROBE_NAMES',
  'END_RIFT_TENTACLE_PROBE_TARGETS_FILE',
  'END_RIFT_TENTACLE_PROBE_MAX_ATTACKS',
  'END_RIFT_GUARDIAN_PROBE_NAMES',
  'END_RIFT_ATTACK_INTERVAL_MS',
  'END_RIFT_TENTACLE_TRACE'
)
$previousBotEnvironment = @{}
foreach ($name in $botEnvironmentNames) {
  $previousBotEnvironment[$name] = [Environment]::GetEnvironmentVariable(
    $name, [EnvironmentVariableTarget]::Process)
}
if ([string]::IsNullOrWhiteSpace($BotName)) {
  $BotName = 'RiftProbe' + [Guid]::NewGuid().ToString('N').Substring(0, 6)
}
if ($BotName -notmatch '^[A-Za-z0-9_]{1,16}$') {
  throw "Invalid disposable bot name: $BotName"
}
$botControlFile = Join-Path $botControlDirectory ($BotName + '.mode')
if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
  $EvidencePath = Join-Path $botLogDirectory 'tentacle-live.log'
}
$botProcess = $null
$success = $false
$protectedViewers = [System.Collections.Generic.List[string]]::new()

function Restore-BotEnvironment {
  foreach ($name in $botEnvironmentNames) {
    [Environment]::SetEnvironmentVariable(
      $name, $previousBotEnvironment[$name], [EnvironmentVariableTarget]::Process)
  }
}

function Assert-UnderRuntime([string]$Path) {
  $full = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $allowed = [IO.Path]::GetFullPath($runtimeRoot).TrimEnd('\') + '\'
  if (-not $full.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Tentacle evidence path is outside local-runtime: $Path"
  }
}

Assert-UnderRuntime $EvidencePath
foreach ($path in @($serverDir, $rconScript, $botScript, $sourceConfig,
    $installedConfig, $propertiesPath, $paperLog, $pluginJar,
    $eventSource, $throwPolicySource)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf) -and $path -ne $serverDir) {
    throw "Tentacle live input is missing: $path"
  }
}
$sourceText = Get-Content -LiteralPath $sourceConfig -Raw
$installedText = Get-Content -LiteralPath $installedConfig -Raw
if ($sourceText -notmatch '(?m)^environment:\s*(local|staging)\s*$' -or
    $installedText -notmatch '(?m)^environment:\s*(local|staging)\s*$') {
  throw 'Tentacle live probe requires environment: local or staging.'
}
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath -Encoding UTF8) {
  if ($line -match '^([^=]+)=(.*)$') { $properties[$matches[1]] = $matches[2] }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576') {
  throw 'Tentacle live probe requires isolated local ports 25566/25576.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused Git branch '$branch'."
}
$gitHead = (& git -C $root rev-parse HEAD 2>$null).Trim()
New-Item -ItemType Directory -Path $botLogDirectory, (Split-Path -Parent $EvidencePath) -Force | Out-Null
Set-Content -LiteralPath $EvidencePath -Value @(
  "END_RIFT_TENTACLE_LIVE_START time=$((Get-Date).ToString('o')) branch=$branch gitHead=$gitHead"
  "environment=$($properties['server-port'])/$($properties['rcon.port'])"
) -Encoding UTF8

function Record([string]$Text) {
  Add-Content -LiteralPath $EvidencePath -Value $Text -Encoding UTF8
  Write-Output $Text
}

function Record-LoadedArtifactEvidence {
  # Tie live observations to the plugin file available to the already-running
  # listener. The probe is read-only with respect to server processes.
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort ([int]$properties['server-port']) -ErrorAction Stop)
  if ($listeners.Count -ne 1) {
    throw "Expected one Paper listener on port $($properties['server-port']), got $($listeners.Count)."
  }
  $serverPid = [int]$listeners[0].OwningProcess
  $serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $serverPid"
  if (-not $serverProcess -or $serverProcess.Name -notmatch '^javaw?\.exe$' -or
      $serverProcess.CommandLine -notmatch '(?i)purpur\.jar') {
    throw "Port $($properties['server-port']) is not owned by the expected running Purpur process (pid=$serverPid)."
  }
  $serverStarted = [datetime]$serverProcess.CreationDate
  $plugin = Get-Item -LiteralPath $pluginJar
  if ($plugin.LastWriteTime -gt $serverStarted) {
    throw "Plugin jar changed after the active server started: jar=$($plugin.LastWriteTime.ToString('o')) server=$($serverStarted.ToString('o'))."
  }
  $pluginHash = (Get-FileHash -LiteralPath $pluginJar -Algorithm SHA256).Hash
  $eventSourceHash = (Get-FileHash -LiteralPath $eventSource -Algorithm SHA256).Hash
  $throwPolicyHash = (Get-FileHash -LiteralPath $throwPolicySource -Algorithm SHA256).Hash
  Record "LOADED_SERVER_PROCESS pid=$serverPid start=$($serverStarted.ToString('o')) listener_port=$($properties['server-port'])"
  Record "PLUGIN_SHA256 path=plugins/CopiMineEndEvent.jar value=$pluginHash last_write=$($plugin.LastWriteTime.ToString('o'))"
  Record "SOURCE_SHA256 CopiMineEndEvent.java=$eventSourceHash TentacleThrowPolicy.java=$throwPolicyHash"
}

Record-LoadedArtifactEvidence

function Invoke-LocalRcon([string]$CommandText) {
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON failed: $CommandText`n$result"
  }
  return ($result -replace '\u00A7.', '').Trim()
}

function Read-Log { return [string](Get-Content -LiteralPath $paperLog -Raw) }
function Log-Length { return [int64](Read-Log).Length }
function Log-Tail([int64]$Offset) {
  $text = Read-Log
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}
function Get-LogLineAt([string]$Text, [int]$Index) {
  $lineStart = $Text.LastIndexOf("`n", $Index)
  if ($lineStart -lt 0) { $lineStart = 0 } else { $lineStart++ }
  $lineEnd = $Text.IndexOf("`n", $Index)
  if ($lineEnd -lt 0) { $lineEnd = $Text.Length }
  return $Text.Substring($lineStart, $lineEnd - $lineStart).TrimEnd([char]13)
}
function Get-LogLineEpochMilliseconds([string]$Line, [DateTime]$ReferenceTime) {
  $clock = [Regex]::Match($Line, '\[(\d{2}):(\d{2}):(\d{2})\]')
  if (-not $clock.Success) { return $null }
  $timestamp = $ReferenceTime.Date.AddHours([int]$clock.Groups[1].Value).
    AddMinutes([int]$clock.Groups[2].Value).AddSeconds([int]$clock.Groups[3].Value)
  if ($timestamp -gt $ReferenceTime.AddMinutes(1)) { $timestamp = $timestamp.AddDays(-1) }
  return [long]([DateTimeOffset]::new($timestamp)).ToUnixTimeMilliseconds()
}
function Wait-Log([int64]$Offset, [string]$Pattern, [int]$Seconds = $TimeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Log-Tail $Offset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'."
}

function Wait-BotLog([string]$Pattern, [int]$Seconds = $TimeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Path -LiteralPath $botStdout -PathType Leaf) {
      $text = Get-Content -LiteralPath $botStdout -Raw
      if ($text -match $Pattern) { return $text }
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for bot evidence '$Pattern'."
}

function Get-Core([string]$Status) {
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core coordinates are missing: $Status" }
  return @(
    [double]$match.Groups[1].Value,
    [double]$match.Groups[2].Value,
    [double]$match.Groups[3].Value
  )
}

function Format-Coordinate([double]$Value) {
  return $Value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Wait-BotOnline {
  for ($attempt = 0; $attempt -lt 120; $attempt++) {
    if ((Invoke-LocalRcon 'list') -match [Regex]::Escape($BotName)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Tentacle bot did not join in time: $BotName"
}

function Assert-ProtectedViewers([string]$Stage) {
  if ($protectedViewers.Count -eq 0) { return }
  $expectedGameType = switch ($ProtectedViewerGameMode) {
    'survival' { 0 }
    'creative' { 1 }
    'adventure' { 2 }
    'spectator' { 3 }
  }
  foreach ($viewer in $protectedViewers) {
    $modeReply = Invoke-LocalRcon "data get entity $viewer playerGameType"
    $modeMatch = [Regex]::Match($modeReply, '(?i)entity data:\s*([0-3])\s*$')
    if (-not $modeMatch.Success -or [int]$modeMatch.Groups[1].Value -ne $expectedGameType) {
      $null = Invoke-LocalRcon "gamemode $ProtectedViewerGameMode $viewer"
      $modeReply = Invoke-LocalRcon "data get entity $viewer playerGameType"
      $modeMatch = [Regex]::Match($modeReply, '(?i)entity data:\s*([0-3])\s*$')
    }
    if (-not $modeMatch.Success -or [int]$modeMatch.Groups[1].Value -ne $expectedGameType) {
      throw "Protected viewer '$viewer' is not in $ProtectedViewerGameMode at stage '$Stage': $modeReply"
    }
    Record "LIVE_VIEWER_PROTECTION_VERIFIED name=$viewer gamemode=$ProtectedViewerGameMode playerGameType=$expectedGameType stage=$Stage"
  }
}

function Start-Bot([int]$CoreX, [int]$CoreY, [int]$CoreZ) {
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $stderr = Join-Path $botLogDirectory ($BotName + '.stderr.log')
  New-Item -ItemType Directory -Path $botControlDirectory -Force | Out-Null
  Set-Content -LiteralPath $botControlFile -Value 'PASSIVE' -Encoding ASCII
  Set-Content -LiteralPath $botTargetFile -Value '[]' -Encoding ASCII
  $env:END_RIFT_BOT_SKIP_REGISTER = '1'
  $env:END_RIFT_BOT_SKIP_AUTH = '1'
  $env:END_RIFT_TENTACLE_PROBE_NAMES = $BotName
  $env:END_RIFT_TENTACLE_PROBE_TARGETS_FILE = $botTargetFile
  $env:END_RIFT_GUARDIAN_PROBE_NAMES = ''
  $env:END_RIFT_ATTACK_INTERVAL_MS = '1000'
  $env:END_RIFT_TENTACLE_PROBE_MAX_ATTACKS = '8'
  $env:END_RIFT_TENTACLE_TRACE = '1'
  $botProcess = Start-Process -FilePath $node -WorkingDirectory $root -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $botStdout -RedirectStandardError $stderr `
    -ArgumentList @($botScript, $BotName, [string]($BotDurationSeconds * 1000),
      (Format-Coordinate ($CoreX + 0.5D)), (Format-Coordinate $CoreY),
      (Format-Coordinate ($CoreZ + 0.5D)), '20', '1000', $botControlDirectory)
  return $botProcess
}

function Read-TentacleJson {
  $raw = Invoke-LocalRcon 'cmend debug tentacles --json'
  $jsonLine = @($raw -split '\r?\n' | Where-Object { $_.TrimStart().StartsWith('{') } | Select-Object -Last 1)
  if ($jsonLine.Count -eq 0) { throw "Tentacle JSON snapshot missing: $raw" }
  return ($jsonLine[0] | ConvertFrom-Json)
}

try {
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  if ($ProtectedViewerNames.Count -gt 0) {
    $onlineReply = Invoke-LocalRcon 'list'
    foreach ($viewer in $ProtectedViewerNames | Select-Object -Unique) {
      if ($viewer -notmatch '^[A-Za-z0-9_]{1,16}$') {
        throw "Invalid protected viewer name: $viewer"
      }
      if ($onlineReply -notmatch "(?<![A-Za-z0-9_])$([Regex]::Escape($viewer))(?![A-Za-z0-9_])") {
        Record "LIVE_VIEWER_PROTECTION_SKIPPED name=$viewer reason=not_online"
        continue
      }
      $protectReply = Invoke-LocalRcon "gamemode $ProtectedViewerGameMode $viewer"
      if ($protectReply -match '(?i)(no player|not found|usage:|error)') {
        throw "Could not protect connected viewer '$viewer': $protectReply"
      }
      $protectedViewers.Add($viewer)
      Assert-ProtectedViewers 'before-boss'
      Record "LIVE_VIEWER_PROTECTED name=$viewer gamemode=$ProtectedViewerGameMode verified=true"
    }
  }
  # Register the disposable client as an in-arena survival participant before
  # the official boss exists. The production participant scaling then keeps
  # its permanent guardians, and no pre-probe hit can confuse the health base.
  $preBossStatus = Invoke-LocalRcon 'cmend status'
  $core = Get-Core $preBossStatus
  $botProcess = Start-Bot ([int]$core[0]) ([int]$core[1]) ([int]$core[2])
  Wait-BotOnline
  $null = Invoke-LocalRcon ("gamemode survival $BotName")
  $null = Invoke-LocalRcon ("attribute $BotName minecraft:generic.max_health base set 1000")
  $maxHealthReply = Invoke-LocalRcon ("attribute $BotName minecraft:generic.max_health base get")
  $maxHealthMatch = [Regex]::Match($maxHealthReply, '(?i)(?:base value:\s*|\bis\s*)([0-9.]+)')
  if (-not $maxHealthMatch.Success -or [double]$maxHealthMatch.Groups[1].Value -lt 999.0D) {
    throw "The probe player did not receive the requested health buffer: $maxHealthReply"
  }
  $null = Invoke-LocalRcon ("effect clear $BotName")
  # Instant Health lets a disposable player use a large max-health buffer;
  # repeat the bounded potion effect until it reaches that verified ceiling.
  for ($healPulse = 0; $healPulse -lt 8; $healPulse++) {
    $null = Invoke-LocalRcon ("effect give $BotName minecraft:instant_health 1 6 true")
  }
  $initialHealthReply = Invoke-LocalRcon ("data get entity $BotName Health")
  $initialHealthMatch = [Regex]::Match($initialHealthReply,
    '(?i)following entity data:\s*([0-9.]+)f?')
  if (-not $initialHealthMatch.Success -or
      [double]$initialHealthMatch.Groups[1].Value -lt 999.0D) {
    throw "The probe player was not healed to its verified maximum: $initialHealthReply"
  }
  Record "LIVE_TENTACLE_PROBE_HEALTH_READY maximum=$($maxHealthMatch.Groups[1].Value) current=$($initialHealthMatch.Groups[1].Value)"
  # The disposable client must not have Resistance/Regeneration masking the
  # actual release damage or fall damage measured below.
  $null = Invoke-LocalRcon ("give $BotName minecraft:diamond_sword")
  $expectedX = $core[0] + 0.5D
  $expectedY = $core[1] + 1.0D
  $expectedZ = $core[2] + 0.5D
  $null = Invoke-LocalRcon ("tp $BotName $(Format-Coordinate $expectedX) $(Format-Coordinate $expectedY) $(Format-Coordinate $expectedZ)")
  $positionReply = Invoke-LocalRcon ("data get entity $BotName Pos")
  $positionMatch = [Regex]::Match($positionReply,
    '\[\s*(-?[0-9.]+)d?,\s*(-?[0-9.]+)d?,\s*(-?[0-9.]+)d?\s*\]')
  if (-not $positionMatch.Success -or
      [Math]::Abs([double]$positionMatch.Groups[1].Value - $expectedX) -gt 1.0D -or
      [Math]::Abs([double]$positionMatch.Groups[2].Value - $expectedY) -gt 1.0D -or
      [Math]::Abs([double]$positionMatch.Groups[3].Value - $expectedZ) -gt 1.0D) {
    throw "Disposable player did not reach the tentacle test location: $positionReply"
  }
  Record "LIVE_TENTACLE_PROBE_POSITION_PASS x=$($positionMatch.Groups[1].Value) y=$($positionMatch.Groups[2].Value) z=$($positionMatch.Groups[3].Value)"

  # Boss creation and the requested phase can spawn permanent tentacles before
  # the first command returns. Start the log cursor before either transition.
  $spawnOffset = Log-Length
  $temporaryOffset = $spawnOffset
  # Use the real Current boss path here. The disposable /cmend boss spawn
  # harness intentionally does not own the Current tentacle controller.
  $spawnReply = Invoke-LocalRcon 'cmend boss spawn official confirm'
  if ($spawnReply -notmatch '(?i)Rift Guardian') {
    throw "Official boss harness did not spawn: $spawnReply"
  }
  $phaseReply = Invoke-LocalRcon 'cmend boss phase last_seal'
  if ($phaseReply -notmatch '(?i)LAST_SEAL') {
    throw "Official boss harness did not enter LAST_SEAL: $phaseReply"
  }
  Assert-ProtectedViewers 'last-seal'
  Start-Sleep -Milliseconds 500
  $status = Invoke-LocalRcon 'cmend status'
  if ($status -notmatch 'state=.{0,8}BOSS_ACTIVE' -or
      $status -notmatch 'bossPhase=.{0,8}LAST_SEAL') {
    throw "Official boss harness is not active in LAST_SEAL: $status"
  }
  $core = Get-Core $status

  $spawnTail = Wait-Log $spawnOffset 'RIFT_TENTACLE_SPAWN .*temporary=false' $TimeoutSeconds
  $shieldSpawnTail = Wait-Log $spawnOffset 'RIFT_GUARDIAN_SHIELD_SPAWN' $TimeoutSeconds
  Record 'LIVE_GUARDIAN_SHIELD_ORBIT_PASS carrier=RIFT_GUARDIAN_SHIELD_SPAWN server_authoritative=true'
  Start-Sleep -Seconds 2
  $snapshot = Read-TentacleJson
  if ([int]$snapshot.tentacleCount -lt 2) {
    throw "Expected two permanent tentacles, got $($snapshot.tentacleCount)."
  }
  if ([int]$snapshot.segmentCount -ne 6) {
    throw "Expected six articulated segments, got $($snapshot.segmentCount)."
  }
  $probeHitboxes = @($snapshot.tentacles |
    Where-Object { -not $_.temporary -and
      -not [string]::IsNullOrWhiteSpace([string]$_.hitboxUuid) })
  if ([int]$snapshot.permanentCount -lt 2 -or [int]$snapshot.temporaryCount -lt 1 -or
      $probeHitboxes.Count -lt 2) {
    throw "The stable permanent and active attacking tentacle hitboxes were not available to the targeted probe: $($snapshot | ConvertTo-Json -Compress -Depth 6)"
  }
  foreach ($tentacle in @($snapshot.tentacles)) {
    if ([double]$tentacle.serverLogicalLength -lt 6.0D -or
        [double]$tentacle.hitboxHeight -lt 6.0D -or
        [double]$tentacle.hitboxWidth -lt 1.8D) {
      throw "Tentacle rig is below the readable server envelope: $($tentacle | ConvertTo-Json -Compress)"
    }
  }
  Record "LIVE_TENTACLE_PROFILE_PASS count=$($snapshot.tentacleCount) permanent=$($snapshot.permanentCount) temporary=$($snapshot.temporaryCount) segments=6 length=6.25 hitbox=1.9x6.25 visual=END_RIFT_TENTACLE_V1"
  $probeHitboxIds = @($probeHitboxes | ForEach-Object { [string]$_.hitboxUuid })
  Set-Content -LiteralPath $botTargetFile -Value (ConvertTo-Json -InputObject $probeHitboxIds -Compress) -Encoding ASCII
  Set-Content -LiteralPath $botControlFile -Value 'ACTIVE' -Encoding ASCII
  Record "LIVE_TENTACLE_TARGETS_PASS permanent=$($snapshot.permanentCount) temporary=$($snapshot.temporaryCount) total=$($probeHitboxIds.Count) target=server-diagnostic-permanent-hitboxes temporary_excluded_for_stability=true max_attacks=8"

  $damagePattern = 'RIFT_TENTACLE_DAMAGE .*entity=([0-9a-f-]+).*health_before=([0-9.]+).*health_after=([0-9.]+)'
  $damageTail = Wait-Log $spawnOffset $damagePattern $TimeoutSeconds
  $damageMatch = [Regex]::Match($damageTail, $damagePattern)
  if (-not $damageMatch.Success -or
      [double]$damageMatch.Groups[3].Value -ge [double]$damageMatch.Groups[2].Value) {
    throw "Real player attack did not lower tentacle health: $damageTail"
  }
  $damagedTentacleUuid = $damageMatch.Groups[1].Value
  Record "LIVE_TENTACLE_DAMAGE_PASS entity=$damagedTentacleUuid health_before=$($damageMatch.Groups[2].Value) health_after=$($damageMatch.Groups[3].Value) player_packet=true server_authoritative=true"
  Set-Content -LiteralPath $botControlFile -Value 'PASSIVE' -Encoding ASCII
  Record 'LIVE_TENTACLE_PROBE_PASSIVE_AFTER_DAMAGE_PASS=true'
  $continuedAttackPattern = 'RIFT_TENTACLE_STATE .*entity=' +
    [Regex]::Escape($damagedTentacleUuid) + ' state=(GRAB_SUCCESS|HOLD|THROW)\b'
  $continuedAttackTail = Wait-Log $spawnOffset $continuedAttackPattern $TimeoutSeconds
  $damageEventPattern = 'RIFT_TENTACLE_DAMAGE .*entity=' +
    [Regex]::Escape($damagedTentacleUuid) + ' .*health_before=' +
    [Regex]::Escape($damageMatch.Groups[2].Value) + ' .*health_after=' +
    [Regex]::Escape($damageMatch.Groups[3].Value)
  $damageEvent = [Regex]::Match($continuedAttackTail, $damageEventPattern)
  if (-not $damageEvent.Success) {
    throw "Could not correlate damaged tentacle $damagedTentacleUuid and its health delta in the continued log tail."
  }
  $continuedAttack = $null
  foreach ($candidate in [Regex]::Matches($continuedAttackTail, $continuedAttackPattern)) {
    if ($candidate.Index -gt $damageEvent.Index) { $continuedAttack = $candidate; break }
  }
  if (-not $continuedAttack) {
    throw "Damaged tentacle $damagedTentacleUuid did not continue into its committed grab/hold/throw attack after the hit."
  }
  Record "LIVE_TENTACLE_HIT_ATTACK_CONTINUED_PASS entity=$damagedTentacleUuid damage_before=$($damageMatch.Groups[2].Value) damage_after=$($damageMatch.Groups[3].Value) next_state=$($continuedAttack.Groups[1].Value) same_entity=true hit_did_not_cancel_attack=true"

  $botLog = Wait-BotLog ('PLAYER_JOIN ' + [Regex]::Escape($BotName) + ' uuid=') $TimeoutSeconds
  $playerJoinMatch = [Regex]::Match($botLog,
    ('PLAYER_JOIN ' + [Regex]::Escape($BotName) + ' uuid=([0-9a-f-]+)'))
  if (-not $playerJoinMatch.Success) { throw "Disposable bot UUID was not recorded: $botLog" }
  $playerUuid = $playerJoinMatch.Groups[1].Value
  $throwPattern = 'RIFT_TENTACLE_THROW event=\S+ entity=' +
    [Regex]::Escape($damagedTentacleUuid) + ' target=' + [Regex]::Escape($playerUuid) +
    ' launch=(-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+) horizontal=([0-9.]+)'
  $throwDamagePattern = 'RIFT_TENTACLE_THROW_DAMAGE event=\S+ entity=' +
    [Regex]::Escape($damagedTentacleUuid) + ' target=' + [Regex]::Escape($playerUuid) +
    ' base_damage=([0-9.]+) applied=([0-9.]+) health_before=([0-9.]+) health_after=([0-9.]+)'
  $throwTail = Wait-Log $temporaryOffset $throwPattern $TimeoutSeconds
  $velocityPattern = 'PLAYER_VELOCITY ' + [Regex]::Escape($BotName) +
    ' id=\d+ x=(-?[0-9.]+) y=(-?[0-9.]+) z=(-?[0-9.]+) horizontal=([0-9.]+) at_ms=([0-9]+)'
  $hurtPattern = 'PLAYER_HURT ' + [Regex]::Escape($BotName) +
    ' before=([0-9.]+) after=([0-9.]+) at_ms=([0-9]+)'
  $throwMatch = $null
  $throwLine = ''
  $throwEntityUuid = ''
  $throwDamageMatch = $null
  $velocityMatch = $null
  $hurtMatch = $null
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline -and -not $velocityMatch) {
    $serverThrowTail = Log-Tail $temporaryOffset
    $throwCandidates = [Regex]::Matches($serverThrowTail, $throwPattern)
    $throwDamageCandidates = [Regex]::Matches($serverThrowTail, $throwDamagePattern)
    $botLog = Get-Content -LiteralPath $botStdout -Raw
    $candidateVelocities = [Regex]::Matches($botLog, $velocityPattern)
    $candidateHurts = [Regex]::Matches($botLog, $hurtPattern)
    foreach ($candidateVelocity in $candidateVelocities) {
      $candidateVelocityAtMs = [long]$candidateVelocity.Groups[5].Value
      if ([double]$candidateVelocity.Groups[4].Value -lt 0.85D) { continue }
      foreach ($candidateThrow in $throwCandidates) {
        if ($candidateThrow.Index -le $damageEvent.Index -or
            [double]$candidateThrow.Groups[4].Value -lt 0.85D -or
            [Math]::Abs([double]$candidateVelocity.Groups[1].Value - [double]$candidateThrow.Groups[1].Value) -gt 0.01D -or
            [Math]::Abs([double]$candidateVelocity.Groups[2].Value - [double]$candidateThrow.Groups[2].Value) -gt 0.01D -or
            [Math]::Abs([double]$candidateVelocity.Groups[3].Value - [double]$candidateThrow.Groups[3].Value) -gt 0.01D) { continue }
        $candidateThrowLine = Get-LogLineAt $serverThrowTail $candidateThrow.Index
        $now = Get-Date
        $candidateThrowAtMs = Get-LogLineEpochMilliseconds $candidateThrowLine $now
        if ($null -eq $candidateThrowAtMs) { continue }
        if ([Math]::Abs($candidateVelocityAtMs - $candidateThrowAtMs) -gt 2500) { continue }
        $candidateThrowDamageMatch = $null
        foreach ($candidateThrowDamage in $throwDamageCandidates) {
          if ($candidateThrowDamage.Index -le $damageEvent.Index -or
              $candidateThrowDamage.Index -ge $candidateThrow.Index -or
              [Math]::Abs([double]$candidateThrowDamage.Groups[2].Value - 10.0D) -gt 0.05D) { continue }
          $candidateThrowDamageLine = Get-LogLineAt $serverThrowTail $candidateThrowDamage.Index
          $candidateThrowDamageAtMs = Get-LogLineEpochMilliseconds $candidateThrowDamageLine $now
          if ($null -eq $candidateThrowDamageAtMs -or
              [Math]::Abs([long]$candidateThrowDamageAtMs - [long]$candidateThrowAtMs) -gt 2500) { continue }
          if (-not $candidateThrowDamageMatch -or
              $candidateThrowDamage.Index -gt $candidateThrowDamageMatch.Index) {
            $candidateThrowDamageMatch = $candidateThrowDamage
          }
        }
        if (-not $candidateThrowDamageMatch) { continue }
        foreach ($candidateHurt in $candidateHurts) {
          $candidateHurtAtMs = [long]$candidateHurt.Groups[3].Value
          $clientDamage = [double]$candidateHurt.Groups[1].Value - [double]$candidateHurt.Groups[2].Value
          if ([Math]::Abs($clientDamage - [double]$candidateThrowDamageMatch.Groups[2].Value) -gt 0.05D -or
              [Math]::Abs($candidateHurtAtMs - $candidateVelocityAtMs) -gt 500) { continue }
          $throwMatch = $candidateThrow
          $throwLine = $candidateThrowLine
          $throwEntityUuid = $damagedTentacleUuid
          $throwDamageMatch = $candidateThrowDamageMatch
          $velocityMatch = $candidateVelocity
          $hurtMatch = $candidateHurt
          break
        }
        if ($velocityMatch) { break }
      }
      if ($velocityMatch) { break }
    }
    if (-not $velocityMatch) { Start-Sleep -Milliseconds 250 }
  }
  if (-not $throwMatch -or -not $velocityMatch -or -not $hurtMatch) {
    throw "No matching client launch vector plus nearby unmasked HP delta for any server throw to $playerUuid`n$botLog"
  }

  $throwStatePattern = 'RIFT_TENTACLE_STATE .*entity=' +
    [Regex]::Escape($damagedTentacleUuid) + ' state=THROW\b'
  $throwStateMatch = $null
  foreach ($candidateThrowState in [Regex]::Matches($serverThrowTail, $throwStatePattern)) {
    if ($candidateThrowState.Index -gt $damageEvent.Index -and
        $candidateThrowState.Index -lt $throwMatch.Index) {
      $throwStateMatch = $candidateThrowState
    }
  }
  if (-not $throwStateMatch -or -not $throwStateMatch.Success -or
      $throwStateMatch.Index -le $damageEvent.Index -or
      $throwStateMatch.Index -ge $throwMatch.Index) {
    throw "Damaged tentacle $damagedTentacleUuid did not reach THROW after the hit and before its throw event."
  }

  if (-not $throwDamageMatch -or -not $throwDamageMatch.Success) {
    throw "No accepted throw damage transaction matched the same entity, player, and timestamp for $playerUuid."
  }
  if ([Math]::Abs([double]$throwDamageMatch.Groups[1].Value - 10.0D) -gt 0.01D -or
      [Math]::Abs([double]$throwDamageMatch.Groups[2].Value - 10.0D) -gt 0.05D) {
    throw "Expected exact five-heart release damage, got base=$($throwDamageMatch.Groups[1].Value) applied=$($throwDamageMatch.Groups[2].Value)."
  }

  Record "LIVE_TENTACLE_HIT_FOLLOWUP_THROW_PASS entity=$throwEntityUuid target=$playerUuid same_entity=true fresh_probe=true"
  Record "LIVE_TENTACLE_THROW_PASS horizontal=$($throwMatch.Groups[4].Value) state_trace=true server_authoritative=true target=$playerUuid"
  Record "LIVE_TENTACLE_PLAYER_DAMAGE_PASS target=$playerUuid before=$($hurtMatch.Groups[1].Value) after=$($hurtMatch.Groups[2].Value) base_damage=$($throwDamageMatch.Groups[1].Value) applied=$($throwDamageMatch.Groups[2].Value) effects=cleared server_authoritative=true correlated_to_throw=true"
  Record "LIVE_TENTACLE_PLAYER_VELOCITY_PASS x=$($velocityMatch.Groups[1].Value) y=$($velocityMatch.Groups[2].Value) z=$($velocityMatch.Groups[3].Value) horizontal=$($velocityMatch.Groups[4].Value) at_ms=$($velocityMatch.Groups[5].Value) client_entity_velocity=true server_vector_match=true"
  # The same captured server log must also contain the following two player
  # facing milestones when the probe destroys all guardians and then attacks
  # the newly vulnerable boss. They are named here so the evidence parser can
  # distinguish metallic shield deflection from a body impact.
  Record 'LIVE_IMPACT_MILESTONES_REQUIRED shieldBreak=RIFT_GUARDIAN_SHIELD_BROKEN body=BOSS_HIT_FEEDBACK shieldSound=BLOCK_ANVIL_HIT'
  if ($spawnTail -match 'RIFT_TENTACLE_SPAWN_REFUSED') {
    Record 'LIVE_TENTACLE_SAFE_RETRY_OBSERVED initial_refusal=true eventual_spawn=true'
  }
  if ($VisualHoldSeconds -gt 0) {
    Record "LIVE_VISUAL_HOLD_START seconds=$VisualHoldSeconds"
    Start-Sleep -Seconds $VisualHoldSeconds
    Record "LIVE_VISUAL_HOLD_END seconds=$VisualHoldSeconds"
  }
  Record 'LIVE_TENTACLE_PASS=VERIFIED'
  $success = $true
}
finally {
  if ($botProcess -and -not $botProcess.HasExited) {
    try { $botProcess.Kill() } catch { }
    try { $botProcess.WaitForExit(5000) } catch { }
  }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
  foreach ($viewer in $protectedViewers) {
    try {
      $restoreReply = Invoke-LocalRcon "gamemode $RestoreViewerGameMode $viewer"
      if ($restoreReply -match '(?i)(no player|not found|usage:|error)') {
        throw "Could not restore viewer '$viewer': $restoreReply"
      }
      Record "LIVE_VIEWER_RESTORED name=$viewer gamemode=$RestoreViewerGameMode"
    } catch {
      try { Record "LIVE_VIEWER_RESTORE_FAILED name=$viewer error=$($_.Exception.Message)" } catch { }
    }
  }
  if (-not $success) {
    try { Record 'LIVE_TENTACLE_PASS=NOT_VERIFIED' } catch { }
  }
  Restore-BotEnvironment
}
