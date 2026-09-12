[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftOfficialA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftOfficialB',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string[]]$AdditionalBotNames = @(),
  [ValidateRange(30, 3600)]
  [int]$BotDurationSeconds = 900,
  [ValidateRange(30, 3600)]
  [int]$TimeoutSeconds = 900,
  [ValidateRange(0, 7)]
  [int]$StopAfterWave = 0,
  [switch]$StopAfterWave3,
  [switch]$StopAfterWave4
)

# Current official-flow probe. It drives the real roster, rune hold,
# objectives, combat, cleanup and boss paths on the isolated Paper instance.
# Only local event state is prepared; no world reset or production endpoint is
# allowed.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$statePath = Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$evidencePath = Join-Path $runtimeRoot 'official-current-live.log'
$botLogDirectory = Join-Path $runtimeRoot 'official-current-bots'
$playerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames)
$processes = @()
  $controlDirectory = Join-Path $botLogDirectory 'control'
$oldReflectEnabled = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_ENABLED', 'Process')
$oldReflectStart = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_START_MS', 'Process')
$oldObeliskTargets = [Environment]::GetEnvironmentVariable('END_RIFT_OBELISK_TARGETS', 'Process')
$oldControlDirectory = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_CONTROL_DIRECTORY', 'Process')
$oldWave7Chambers = [Environment]::GetEnvironmentVariable('END_RIFT_WAVE7_CHAMBERS', 'Process')
$oldGuardianProbeNames = [Environment]::GetEnvironmentVariable('END_RIFT_GUARDIAN_PROBE_NAMES', 'Process')
$oldAttackInterval = [Environment]::GetEnvironmentVariable('END_RIFT_ATTACK_INTERVAL_MS', 'Process')
  $originalRequiredPlayers = 0
  $originalCore = $null

if ($playerNames.Count -lt 2 -or $playerNames.Count -gt 20) {
  throw "Current official probe supports two to twenty players; received $($playerNames.Count)."
}
if (@($playerNames | Select-Object -Unique).Count -ne $playerNames.Count) {
  throw 'Current official probe requires unique player names.'
}
if ($StopAfterWave3) { $StopAfterWave = 3 }
if ($StopAfterWave4) { $StopAfterWave = 4 }
$configText = Get-Content -LiteralPath $configPath -Raw
if ($configText -notmatch '(?m)^\s*schema-version:\s*4\s*$') {
  throw 'Refused: the probe requires the current schema version.'
}
if ($configText -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: the official probe requires environment: local.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused Git branch '$branch'."
}
foreach ($path in @($serverDir, $rconScript, $botScript, $paperLog, $statePath)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf) -and
      -not (Test-Path -LiteralPath $path -PathType Container)) {
    throw "Required local probe input is missing: $path"
  }
}
$properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Refused: the probe is not pointed at isolated local ports.'
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON command failed: $CommandText $result"
  }
  return $result.Trim()
}

function Read-PaperLog {
  return [string](Get-Content -LiteralPath $paperLog -Raw)
}

function Get-LogLength {
  return [int64](Read-PaperLog).Length
}

function Get-LogTail {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $text = Read-PaperLog
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

function Wait-Log {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [int]$WaitSeconds = $TimeoutSeconds,
    [scriptblock]$Action
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Get-LogTail -Offset $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    if ($null -ne $Action) { & $Action }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for '$Pattern'. See $paperLog"
}

function Wait-LogCount {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int]$Minimum,
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [int]$WaitSeconds = $TimeoutSeconds,
    [scriptblock]$Action
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Get-LogTail -Offset $AfterOffset
    if (([Regex]::Matches($tail, $Pattern)).Count -ge $Minimum) { return $tail }
    if ($null -ne $Action) { & $Action }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for $Minimum matches of '$Pattern'. See $paperLog"
}

function Get-Status {
  return (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
}

function Get-Core {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Status did not expose Core coordinates: $Status" }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Get-RequiredPlayers {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'requiredPlayers=(\d+)')
  if (-not $match.Success) { return 2 }
  return [int]$match.Groups[1].Value
}

function Get-EventId {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'event=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) { throw "Status did not expose event id: $Status" }
  return $match.Groups[1].Value
}

function Get-Pads {
  $need = $playerNames.Count
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $yaml = Get-Content -LiteralPath $statePath -Raw
    $matches = [Regex]::Matches($yaml, '(?m)^-\s*x:\s*(-?\d+)\s*\r?\n\s*y:\s*(-?\d+)\s*\r?\n\s*z:\s*(-?\d+)')
    if ($matches.Count -ge $need) {
      $pads = @()
      for ($index = 0; $index -lt $need; $index++) {
        $pads += ,@([int]$matches[$index].Groups[1].Value, [int]$matches[$index].Groups[2].Value, [int]$matches[$index].Groups[3].Value)
      }
      return ,$pads
    }
    Start-Sleep -Milliseconds 250
  }
  throw "State did not persist $need rune locations."
}

function Format-Coordinate {
  param([double]$Value)
  return $Value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Teleport-Player {
  param([string]$Name, [double]$X, [double]$Y, [double]$Z)
  $null = Invoke-LocalRcon ("tp $Name $(Format-Coordinate $X) $(Format-Coordinate $Y) $(Format-Coordinate $Z) 180 0")
}

function Teleport-PlayersToPads {
  param([Parameter(Mandatory = $true)][object[]]$Pads)
  if ($Pads.Count -lt $playerNames.Count) { throw 'Not enough persisted runes for the local roster.' }
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    Teleport-Player $playerNames[$index] ($Pads[$index][0] + 0.5D) $Pads[$index][1] ($Pads[$index][2] + 0.5D)
  }
}

function Teleport-PlayersToCombatRing {
  param([Parameter(Mandatory = $true)][object]$Core)
  # Scriptblock actions are invoked from inside Wait-Log.  PowerShell can
  # preserve a returned coordinate array as one nested object in that scope;
  # normalize it here so the probe never turns a coordinate into an array and
  # accidentally fails while trying to add the ring offset.
  $pending = [System.Collections.Generic.Stack[object]]::new()
  $pending.Push($Core)
  $coordinates = [System.Collections.Generic.List[object]]::new()
  while ($pending.Count -gt 0) {
    $value = $pending.Pop()
    if ($value -is [Array]) {
      for ($nested = $value.Length - 1; $nested -ge 0; $nested--) {
        $pending.Push($value[$nested])
      }
    } else {
      $coordinates.Add($value)
    }
  }
  if ($coordinates.Count -lt 3) { throw "Core coordinates were not a flat XYZ tuple: $($coordinates -join ',')" }
  $x = [double]$coordinates[0]
  $y = [double]$coordinates[1]
  $z = [double]$coordinates[2]
  $points = @(
    [pscustomobject]@{ X = $x + 6.0D; Y = $y; Z = $z + 0.5D },
    [pscustomobject]@{ X = $x - 6.0D; Y = $y; Z = $z + 0.5D },
    [pscustomobject]@{ X = $x + 0.5D; Y = $y; Z = $z + 6.0D },
    [pscustomobject]@{ X = $x + 0.5D; Y = $y; Z = $z - 6.0D },
    [pscustomobject]@{ X = $x - 5.0D; Y = $y; Z = $z + 5.0D },
    [pscustomobject]@{ X = $x + 5.0D; Y = $y; Z = $z - 5.0D }
  )
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $point = $points[$index % $points.Count]
    Teleport-Player $playerNames[$index] $point.X $point.Y $point.Z
  }
}

function Get-OfflinePlayerUuid {
  param([Parameter(Mandatory = $true)][string]$Name)
  $md5 = [Security.Cryptography.MD5]::Create()
  try {
    $bytes = [Text.Encoding]::UTF8.GetBytes('OfflinePlayer:' + $Name)
    $hash = $md5.ComputeHash($bytes)
  } finally {
    $md5.Dispose()
  }
  # Paper's offline profile UUID is the raw name-MD5 form.  Do not apply the
  # RFC version bits here: the server's UUIDs in offline mode use this exact
  # byte sequence for chamber assignment.
  return (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Teleport-PlayersToChambers {
  $ordered = @($playerNames | ForEach-Object {
      [pscustomobject]@{ Name = $_; Uuid = Get-OfflinePlayerUuid $_ }
    } | Sort-Object Uuid)
  for ($index = 0; $index -lt $ordered.Count; $index++) {
    $chamber = $index % $wave7ChamberCount
    $angle = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $chamber / $wave7ChamberCount)
    $x = $core[0] + 0.5D + [Math]::Cos($angle) * 8.0D
    $z = $core[2] + 0.5D + [Math]::Sin($angle) * 8.0D
    Teleport-Player $ordered[$index].Name $x $core[1] $z
  }
}

function Teleport-PlayersToPoint {
  param([double]$X, [double]$Y, [double]$Z)
  foreach ($name in $playerNames) { Teleport-Player $name $X $Y $Z }
}

function Get-ObeliskCount {
  param([int]$Players)
  if ($Players -le 7) { return 4 }
  if ($Players -le 15) { return 5 }
  return 6
}

function Get-ObeliskTargets {
  param([int[]]$Core, [int]$Count)
  $targets = @()
  for ($index = 0; $index -lt $Count; $index++) {
    $angle = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $index / $Count)
    $targets += [pscustomobject]@{
      x = $Core[0] + [Math]::Round([Math]::Cos($angle) * 9.0D) + 0.5D
      y = $Core[1] + 4.0D
      z = $Core[2] + [Math]::Round([Math]::Sin($angle) * 9.0D) + 0.5D
    }
  }
  return ,$targets
}

function Start-PlayerBot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][int[]]$Core)
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $out = Join-Path $botLogDirectory ($Name + '.log')
  $err = Join-Path $botLogDirectory ($Name + '.err.log')
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    (Format-Coordinate ($Core[0] + 0.5D)) + ' ' + (Format-Coordinate $Core[1]) + ' ' +
    (Format-Coordinate ($Core[2] + 0.5D)) + ' 20 900 "' + $controlDirectory + '"'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
  $script:processes += $process
}

function Wait-PlayersOnline {
  for ($attempt = 0; $attempt -lt 100; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Local players did not join: $($playerNames -join ', ')"
}

function Configure-Players {
  foreach ($name in $playerNames) {
    $null = Invoke-LocalRcon ("gamemode survival $name")
    $null = Invoke-LocalRcon ("clear $name")
    $null = Invoke-LocalRcon ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon ("give $name minecraft:netherite_sword 1")
    $null = Invoke-LocalRcon ("enchant $name minecraft:sharpness 5")
    $null = Invoke-LocalRcon ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon ("effect give $name minecraft:regeneration 1000 4 true")
    $null = Invoke-LocalRcon ("effect give $name minecraft:strength 1000 20 true")
    $null = Invoke-LocalRcon ("effect give $name minecraft:speed 1000 2 true")
  }
}

function Wait-Transition {
  param(
    [int]$CompletedWave,
    [object[]]$Pads,
    [int64]$AfterOffset = -1
  )
  $offset = if ($AfterOffset -ge 0) { $AfterOffset } else { Get-LogLength }
  Wait-Log -AfterOffset $offset -Pattern ('END_RIFT_TRANSITION_RUNES_READY.*completed_wave=' + $CompletedWave) -WaitSeconds 120 | Out-Null
  Teleport-PlayersToPads -Pads $Pads
  $next = $CompletedWave + 1
  Wait-Log -AfterOffset $offset -Pattern ('END_RIFT_WAVE_STARTED.*wave=' + $next + '\b') -WaitSeconds 120 -Action { Teleport-PlayersToPads -Pads $Pads } | Out-Null
  Write-Evidence "CURRENT_TRANSITION_PASS completed_wave=$CompletedWave next_wave=$next pads=$($Pads.Count) hold_ms=5000" | Out-Null
  # The next objective's first marker may share the exact tick with
  # END_RIFT_WAVE_STARTED. Preserve the cursor from before the transition.
  return [int64]$offset
}

function Wait-WaveComplete {
  param(
    [int]$Wave,
    [int[]]$Core,
    [int]$Seconds = $TimeoutSeconds,
    [int64]$AfterOffset = -1
  )
  $offset = if ($AfterOffset -ge 0) { $AfterOffset } else { Get-LogLength }
  Wait-Log -AfterOffset $offset -Pattern ('END_RIFT_WAVE_COMPLETED.*wave=' + $Wave + '\b') -WaitSeconds $Seconds -Action { Teleport-PlayersToCombatRing -Core $Core } | Out-Null
  # Completion and the intermission transition marker are committed by the
  # same server tick. Return the pre-completion cursor to the caller so the
  # following Wait-Transition cannot skip that marker.
  return $offset
}

function Wait-CoreRestoration {
  param([int64]$AfterOffset, [int[]]$Core)
  Wait-Log -AfterOffset $AfterOffset -Pattern 'END_RIFT_CORE_RESTORATION_STARTED.*next_wave=5' -WaitSeconds 60 | Out-Null
  Wait-Log -AfterOffset $AfterOffset -Pattern 'END_RIFT_CORE_RESTORATION_COMPLETED.*next_wave=5' -WaitSeconds 30 | Out-Null
  Wait-Log -AfterOffset $AfterOffset -Pattern 'END_RIFT_WAVE_STARTED.*wave=5\b' -WaitSeconds 120 -Action { Teleport-PlayersToCombatRing -Core $Core } | Out-Null
  Write-Evidence 'CURRENT_CORE_RESTORATION_PASS duration_ms=6000 next_wave=5' | Out-Null
  # Restoration and Wave 5 start can share the same server tick. Keep the
  # pre-restoration cursor so the first Black Fog marker cannot be skipped.
  return [int64]$AfterOffset
}

function Teleport-PlayersToNearestChamberMob {
  # Test-only positioning aid.  It selects only the current event's tagged
  # Endermen and performs no damage or objective mutation.  The server still
  # validates every real attack and room-local target decision.  Do not use an
  # entity-to-entity tp selector here: the isolated Paper console accepts the
  # player target reliably by name, so resolve the nearest target at the
  # player's coordinates and issue a normal scoped teleport.
  function Parse-Position {
    param([Parameter(Mandatory = $true)][string]$Text)
    $match = [Regex]::Match($Text, '\[(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\]')
    if (-not $match.Success) { return $null }
    return @([double]$match.Groups[1].Value, [double]$match.Groups[2].Value, [double]$match.Groups[3].Value)
  }
  function Get-ChamberIndex {
    param(
      [double]$X,
      [double]$Z,
      [int]$Chambers
    )
    $xOffset = $X - ($core[0] + 0.5D)
    $zOffset = $Z - ($core[2] + 0.5D)
    $radius = [Math]::Sqrt($xOffset * $xOffset + $zOffset * $zOffset)
    if ($radius -lt 3.5D -or $radius -gt 18.0D) { return -1 }
    $angle = [Math]::Atan2($zOffset, $xOffset)
    $halfSector = [Math]::Max(0.05D, [Math]::PI / $Chambers - (8.0D * [Math]::PI / 180.0D))
    $selected = -1
    $closest = [double]::PositiveInfinity
    for ($chamber = 0; $chamber -lt $Chambers; $chamber++) {
      $center = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $chamber / $Chambers)
      $delta = [Math]::Atan2([Math]::Sin($angle - $center), [Math]::Cos($angle - $center))
      $absolute = [Math]::Abs($delta)
      if ($absolute -le $halfSector -and $absolute -lt $closest) {
        $selected = $chamber
        $closest = $absolute
      }
    }
    return $selected
  }
  foreach ($name in $playerNames) {
    $playerPosition = Parse-Position (Invoke-LocalRcon ("data get entity $name Pos"))
    if ($null -eq $playerPosition) { continue }
    $playerChamber = Get-ChamberIndex $playerPosition[0] $playerPosition[2] $wave7ChamberCount
    if ($playerChamber -lt 0) { continue }
    $targetText = Invoke-LocalRcon ("execute positioned $(Format-Coordinate $playerPosition[0]) $(Format-Coordinate $playerPosition[1]) $(Format-Coordinate $playerPosition[2]) run data get entity @e[tag=copimine_end_event,type=enderman,sort=nearest,limit=1,distance=..24] Pos")
    $targetPosition = Parse-Position $targetText
    if ($null -eq $targetPosition) { continue }
    if ((Get-ChamberIndex $targetPosition[0] $targetPosition[2] $wave7ChamberCount) -ne $playerChamber) { continue }
    $dx = $targetPosition[0] - $playerPosition[0]
    $dy = $targetPosition[1] - $playerPosition[1]
    $dz = $targetPosition[2] - $playerPosition[2]
    if ($dx * $dx + $dy * $dy + $dz * $dz -gt 18.0D) {
      Teleport-Player $name $targetPosition[0] $targetPosition[1] $targetPosition[2]
    }
  }
}

function Teleport-GuardianProbeToNearest {
  # Test-only positioning aid for Last Seal. The bot still sends the real
  # USE_ENTITY attack packet and the server still validates the Interaction
  # hitbox, phase and participant. This only removes the non-determinism of a
  # tentacle throw carrying the probe away from its next target.
  $probe = $playerNames[0]
  if ([string]::IsNullOrWhiteSpace($probe)) { return }
  function Parse-Position {
    param([Parameter(Mandatory = $true)][string]$Text)
    $match = [Regex]::Match($Text, '\[(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\]')
    if (-not $match.Success) { return $null }
    return @([double]$match.Groups[1].Value, [double]$match.Groups[2].Value, [double]$match.Groups[3].Value)
  }
  $playerPosition = Parse-Position (Invoke-LocalRcon ("data get entity $probe Pos"))
  if ($null -eq $playerPosition) { return }
  $targetText = Invoke-LocalRcon ("execute positioned $(Format-Coordinate $playerPosition[0]) $(Format-Coordinate $playerPosition[1]) $(Format-Coordinate $playerPosition[2]) run data get entity @e[type=interaction,sort=nearest,limit=1,distance=..32] Pos")
  $targetPosition = Parse-Position $targetText
  if ($null -eq $targetPosition) { return }
  $dx = $targetPosition[0] - $playerPosition[0]
  $dy = $targetPosition[1] - $playerPosition[1]
  $dz = $targetPosition[2] - $playerPosition[2]
  if ($dx * $dx + $dy * $dy + $dz * $dz -gt 4.0D) {
    Teleport-Player $probe $targetPosition[0] $targetPosition[1] $targetPosition[2]
  }
}

try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null
  Get-ChildItem -LiteralPath $controlDirectory -Filter '*.mode' -File -ErrorAction SilentlyContinue | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
  # Keep one cursor for the whole disposable run. The event id is generated
  # after core setup, so this cursor is guaranteed to precede every marker for
  # the new attempt, including markers emitted during setup/teleport.
  $runLogOffset = Get-LogLength
  $baselineStatus = Get-Status
  $originalCore = Get-Core $baselineStatus
  $originalRequiredPlayers = Get-RequiredPlayers $baselineStatus
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  $null = Invoke-LocalRcon 'cmend core remove confirm'
  $null = Invoke-LocalRcon ("cmend core setat $($originalCore[0]) $($originalCore[1]) $($originalCore[2]) $($playerNames.Count)")
  $null = Invoke-LocalRcon 'cmend resources reset confirm'
  foreach ($resource in @('DIAMOND 100', 'ENDER_EYE 64', 'AMETHYST_SHARD 128', 'BLAZE_ROD 64')) {
    $null = Invoke-LocalRcon ("cmend resources add $resource")
  }
  $status = Get-Status
  if ($status -notmatch 'state=READY_FOR_PLAYERS') { throw "Local event did not become ready: $status" }
  $core = Get-Core $status
  $eventId = Get-EventId $status
  $pads = Get-Pads
  Write-Evidence "CURRENT_SETUP_PASS event=$eventId players=$($playerNames.Count) core=$($core -join ',') pads=$($pads.Count)"

  $env:END_RIFT_BOT_CONTROL_DIRECTORY = $controlDirectory
  $env:END_RIFT_REFLECT_ENABLED = '1'
  $env:END_RIFT_REFLECT_START_MS = '0'
  $env:END_RIFT_OBELISK_TARGETS = (Get-ObeliskTargets -Core $core -Count (Get-ObeliskCount $playerNames.Count) | ConvertTo-Json -Compress)
  $wave7ChamberCount = if ($playerNames.Count -le 3) { $playerNames.Count } else { 4 }
  $env:END_RIFT_WAVE7_CHAMBERS = [string]$wave7ChamberCount
  # Last Seal is intentionally shielded while permanent guardian hitboxes are
  # alive. Designate the real bots to attack those hitboxes, then let the
  # normal boss path resume once the shield is legitimately removed.
  $env:END_RIFT_GUARDIAN_PROBE_NAMES = $playerNames -join ','
  $env:END_RIFT_ATTACK_INTERVAL_MS = '200'
  foreach ($name in $playerNames) {
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Value 'ACTIVE' -NoNewline -Encoding ASCII
    Start-PlayerBot -Name $name -Core $core
  }
  Wait-PlayersOnline
  Configure-Players
  # Rune occupancy can complete the ritual in the same command batch as the
  # pad teleports. Capture the cursor before that mutation, otherwise the
  # reader can start after RITUAL_STARTED and lose the whole transition.
  $ritualOffset = $runLogOffset
  Teleport-PlayersToPads -Pads $pads
  Wait-Log -AfterOffset $ritualOffset -Pattern ('RITUAL_STARTED.*event=' + $eventId) -WaitSeconds 30 -Action { Teleport-PlayersToPads -Pads $pads } | Out-Null
  Wait-Log -AfterOffset $ritualOffset -Pattern ('RITUAL_COMPLETED.*event=' + $eventId) -WaitSeconds 100 -Action { Teleport-PlayersToPads -Pads $pads } | Out-Null
  Write-Evidence "CURRENT_RITUAL_PASS event=$eventId players=$($playerNames.Count)"

  # RITUAL_COMPLETED, the state transition, and WAVE_STARTED are emitted by
  # the same server tick. Keep the pre-ritual cursor so a reader that observes
  # RITUAL_COMPLETED cannot skip the already-written W1 marker.
  $waveOffset = $ritualOffset
  Wait-Log -AfterOffset $waveOffset -Pattern ('END_RIFT_WAVE_STARTED.*event=' + $eventId + '.*wave=1\b') -WaitSeconds 30 | Out-Null
  $waveOneCompletionOffset = $null
  for ($delivery = 1; $delivery -le 3; $delivery++) {
    $carrierOffset = Get-LogLength
    $created = Wait-Log -AfterOffset $carrierOffset -Pattern 'END_RIFT_CARRIER_CHARGE_CREATED.*charge=[0-9a-fA-F-]{36}' -WaitSeconds 120 -Action { Teleport-PlayersToCombatRing -Core $core }
    $chargeMatch = [Regex]::Match($created, 'END_RIFT_CARRIER_CHARGE_CREATED.*charge=([0-9a-fA-F-]{36})')
    if (-not $chargeMatch.Success) { throw "Wave 1 charge id was not exposed: $created" }
    $chargeId = $chargeMatch.Groups[1].Value
    $chargePosition = $null
    for ($attempt = 0; $attempt -lt 20 -and $null -eq $chargePosition; $attempt++) {
      $positionText = Invoke-LocalRcon ("data get entity $chargeId Pos")
      $positionMatch = [Regex]::Match($positionText, '\[(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\]')
      if ($positionMatch.Success) {
        $chargePosition = @([double]$positionMatch.Groups[1].Value, [double]$positionMatch.Groups[2].Value, [double]$positionMatch.Groups[3].Value)
      } else {
        Start-Sleep -Milliseconds 250
      }
    }
    $pickupPattern = 'END_RIFT_CARRIER_PICKED_UP.*charge=' + [Regex]::Escape($chargeId)
    $alreadyPickedUp = $false
    if ($null -eq $chargePosition) {
      # A carrier can die next to a player.  The display is then picked up in
      # the same server tick as its creation and is already gone by the time
      # RCON asks for Pos.  That is valid gameplay, not a missing entity.
      $alreadyPickedUp = (Get-LogTail -Offset $carrierOffset) -match $pickupPattern
      if ($alreadyPickedUp) {
        $chargePosition = @($core[0] + 0.5D, $core[1] + 1.0D, $core[2] + 0.5D)
      }
    }
    if ($null -eq $chargePosition) { throw "Wave 1 charge location was not readable: $chargeId" }
    if (-not $alreadyPickedUp) {
      Teleport-PlayersToPoint $chargePosition[0] $chargePosition[1] $chargePosition[2]
      Wait-Log -AfterOffset $carrierOffset -Pattern $pickupPattern -WaitSeconds 60 -Action { Teleport-PlayersToPoint $chargePosition[0] $chargePosition[1] $chargePosition[2] } | Out-Null
    }
    Teleport-PlayersToPoint ($core[0] + 0.5D) ($core[1] + 1.0D) ($core[2] + 0.5D)
    if ($delivery -eq 3) {
      # The final delivery and W1 completion are committed synchronously by
      # the same server tick.  Capture the offset before that delivery so the
      # completion marker cannot be missed by a post-delivery reader.
      $waveOneCompletionOffset = $carrierOffset
    }
    Wait-Log -AfterOffset $carrierOffset -Pattern ('END_RIFT_CARRIER_DELIVERED.*charge=' + $delivery + '/3') -WaitSeconds 60 -Action { Teleport-PlayersToPoint ($core[0] + 0.5D) ($core[1] + 1.0D) ($core[2] + 0.5D) } | Out-Null
  }
  Wait-Log -AfterOffset $waveOneCompletionOffset -Pattern 'END_RIFT_WAVE_COMPLETED.*wave=1\b' -WaitSeconds 300 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  $waveOneTransitionOffset = $waveOneCompletionOffset
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=1 objective=RIFT_CARRIERS"
  if ($StopAfterWave -eq 1) { return }

  $pads = Get-Pads
  $waveTwoStartOffset = Wait-Transition -CompletedWave 1 -Pads $pads -AfterOffset $waveOneTransitionOffset
  $waveTwoTransitionOffset = Wait-WaveComplete -Wave 2 -Core $core -Seconds 300
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=2 objective=RIFT_HUNT"
  if ($StopAfterWave -eq 2) { return }

  $pads = Get-Pads
  $waveThreeStartOffset = Wait-Transition -CompletedWave 2 -Pads $pads -AfterOffset $waveTwoTransitionOffset
  $portalOffset = $waveThreeStartOffset
  Wait-Log -AfterOffset $portalOffset -Pattern 'END_RIFT_PORTALS_READY.*count=3.*sequential=true' -WaitSeconds 60 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  for ($portalIndex = 0; $portalIndex -lt 3; $portalIndex++) {
    $angle = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $portalIndex / 3.0D)
    $portalX = $core[0] + 0.5D + 8.0D * [Math]::Cos($angle)
    $portalZ = $core[2] + 0.5D + 8.0D * [Math]::Sin($angle)
    Teleport-PlayersToPoint $portalX $core[1] $portalZ
    $actionX = $portalX
    $actionZ = $portalZ
    Wait-Log -AfterOffset $portalOffset -Pattern ('PORTAL_CAPTURE_PROGRESS.*index=' + $portalIndex + '.*completed=true') -WaitSeconds 45 -Action { Teleport-PlayersToPoint $actionX $core[1] $actionZ } | Out-Null
  }
  $waveThreeTransitionOffset = Wait-WaveComplete -Wave 3 -Core $core -Seconds 300
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=3 objective=RIFT_GATES portals=3"
  if ($StopAfterWave -eq 3) { return }

  $pads = Get-Pads
  $waveFourStartOffset = Wait-Transition -CompletedWave 3 -Pads $pads -AfterOffset $waveThreeTransitionOffset
  $obeliskCount = Get-ObeliskCount $playerNames.Count
  $w4Offset = $waveFourStartOffset
  Wait-Log -AfterOffset $w4Offset -Pattern ('END_RIFT_OBELISK_ASSAULT_READY.*obelisks=' + $obeliskCount + '.*real_blocks=true') -WaitSeconds 150 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  Wait-LogCount -AfterOffset $w4Offset -Pattern 'END_RIFT_OBELISK_ACTIVE ' -Minimum $obeliskCount -WaitSeconds 90 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  Wait-Log -AfterOffset $w4Offset -Pattern 'RIFT_FIREBALL_LAUNCH ' -WaitSeconds 120 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  $waveFourTransitionOffset = Wait-WaveComplete -Wave 4 -Core $core -Seconds 600
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=4 objective=OBELISK_ASSAULT obelisks=$obeliskCount"
  if ($StopAfterWave -eq 4) { return }

  $waveFiveStartOffset = Wait-CoreRestoration -AfterOffset $waveFourTransitionOffset -Core $core
  $fogOffset = $waveFiveStartOffset
  for ($cycle = 1; $cycle -le 3; $cycle++) {
    Wait-Log -AfterOffset $fogOffset -Pattern ('END_RIFT_FOG_SAFE_START.*cycle=' + $cycle + '/3') -WaitSeconds 180 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
    Wait-Log -AfterOffset $fogOffset -Pattern ('END_RIFT_FOG_START.*cycle=' + $cycle + '/3') -WaitSeconds 90 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  }
  Wait-Log -AfterOffset $fogOffset -Pattern 'END_RIFT_FOG_COMPLETE.*cycles=3' -WaitSeconds 180 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  # FOG_COMPLETE and WAVE_COMPLETED are intentionally emitted by the same
  # server tick. Reuse the objective cursor or the completion marker can be
  # skipped before Wait-WaveComplete captures its default cursor.
  $waveFiveTransitionOffset = Wait-WaveComplete -Wave 5 -Core $core -Seconds 600 -AfterOffset $fogOffset
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=5 objective=BLACK_FOG cycles=3"
  if ($StopAfterWave -eq 5) { return }

  $pads = Get-Pads
  $waveSixStartOffset = Wait-Transition -CompletedWave 5 -Pads $pads -AfterOffset $waveFiveTransitionOffset
  $ringsOffset = $waveSixStartOffset
  for ($ring = 1; $ring -le 3; $ring++) {
    Wait-Log -AfterOffset $ringsOffset -Pattern ('WAVE_6_PAIR_SPAWNED.*ring=' + $ring + '\b') -WaitSeconds 180 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
    Wait-Log -AfterOffset $ringsOffset -Pattern ('WAVE_6_PAIR_DEFEATED.*completed=' + $ring + '/3') -WaitSeconds 300 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  }
  # The final ring pair and Wave 6 completion are emitted on the same tick.
  # Keep the ring cursor so the completion marker remains observable.
  $waveSixTransitionOffset = Wait-WaveComplete -Wave 6 -Core $core -Seconds 700 -AfterOffset $ringsOffset
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=6 objective=COLLAPSE_RINGS rings=3"
  if ($StopAfterWave -eq 6) { return }

  $pads = Get-Pads
  $waveSevenStartOffset = Wait-Transition -CompletedWave 6 -Pads $pads -AfterOffset $waveSixTransitionOffset
  $chamberOffset = $waveSevenStartOffset
  Wait-Log -AfterOffset $chamberOffset -Pattern 'END_RIFT_CHAMBERS_ASSIGNED' -WaitSeconds 180 | Out-Null
  Teleport-PlayersToChambers
  foreach ($name in $playerNames) {
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Value 'ACTIVE_WAVE7' -NoNewline -Encoding ASCII
  }
  Wait-Log -AfterOffset $chamberOffset -Pattern 'END_RIFT_CHAMBERS_COMPLETE' -WaitSeconds 900 -Action { Teleport-PlayersToNearestChamberMob } | Out-Null
  # Chamber completion and Wave 7 completion may share a tick as well.
  Wait-WaveComplete -Wave 7 -Core $core -Seconds 900 -AfterOffset $chamberOffset | Out-Null
  foreach ($name in $playerNames) {
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Value 'ACTIVE' -NoNewline -Encoding ASCII
  }
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=7 objective=REALITY_SPLIT chambers=local"
  if ($StopAfterWave -eq 7) { return }

  $bossOffset = Get-LogLength
  Wait-Log -AfterOffset $bossOffset -Pattern 'BOSS_CINEMATIC_STARTED' -WaitSeconds 180 | Out-Null
  Wait-Log -AfterOffset $bossOffset -Pattern 'BOSS_SPAWNED' -WaitSeconds 240 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  foreach ($stage in @('HUNT', 'RIFT', 'OVERLOAD', 'RAGE', 'LAST_SEAL')) {
    $stageAction = if ($stage -eq 'LAST_SEAL') {
      { Teleport-GuardianProbeToNearest }
    } else {
      { Teleport-PlayersToCombatRing -Core $core }
    }
    Wait-Log -AfterOffset $bossOffset -Pattern ('BOSS_STAGE_TRANSITION.*to=' + $stage + '\b') -WaitSeconds 600 -Action $stageAction | Out-Null
  }
  Wait-Log -AfterOffset $bossOffset -Pattern 'BOSS_DEFEAT_COMMITTED' -WaitSeconds 1200 -Action { Teleport-GuardianProbeToNearest } | Out-Null
  Wait-Log -AfterOffset $bossOffset -Pattern 'BOSS_DEFEATED' -WaitSeconds 90 | Out-Null
  Wait-Log -AfterOffset $bossOffset -Pattern 'VICTORY' -WaitSeconds 180 | Out-Null
  $finalStatus = Get-Status
  if ($finalStatus -notmatch 'boss=none' -or $finalStatus -notmatch 'event-mobs=\s*0') {
    throw "Current victory left transient combat entities: $finalStatus"
  }
  Write-Evidence "CURRENT_OFFICIAL_PASS event=$eventId players=$($playerNames.Count) waves=1,2,3,4,5,6,7 stages=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL victory=true"
  Write-Evidence $finalStatus
}
finally {
  if ($null -eq $oldReflectEnabled) { Remove-Item Env:END_RIFT_REFLECT_ENABLED -ErrorAction SilentlyContinue } else { $env:END_RIFT_REFLECT_ENABLED = $oldReflectEnabled }
  if ($null -eq $oldReflectStart) { Remove-Item Env:END_RIFT_REFLECT_START_MS -ErrorAction SilentlyContinue } else { $env:END_RIFT_REFLECT_START_MS = $oldReflectStart }
  if ($null -eq $oldObeliskTargets) { Remove-Item Env:END_RIFT_OBELISK_TARGETS -ErrorAction SilentlyContinue } else { $env:END_RIFT_OBELISK_TARGETS = $oldObeliskTargets }
  if ($null -eq $oldControlDirectory) { Remove-Item Env:END_RIFT_BOT_CONTROL_DIRECTORY -ErrorAction SilentlyContinue } else { $env:END_RIFT_BOT_CONTROL_DIRECTORY = $oldControlDirectory }
  if ($null -eq $oldWave7Chambers) { Remove-Item Env:END_RIFT_WAVE7_CHAMBERS -ErrorAction SilentlyContinue } else { $env:END_RIFT_WAVE7_CHAMBERS = $oldWave7Chambers }
  if ($null -eq $oldGuardianProbeNames) { Remove-Item Env:END_RIFT_GUARDIAN_PROBE_NAMES -ErrorAction SilentlyContinue } else { $env:END_RIFT_GUARDIAN_PROBE_NAMES = $oldGuardianProbeNames }
  if ($null -eq $oldAttackInterval) { Remove-Item Env:END_RIFT_ATTACK_INTERVAL_MS -ErrorAction SilentlyContinue } else { $env:END_RIFT_ATTACK_INTERVAL_MS = $oldAttackInterval }
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { } }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.WaitForExit(5000) | Out-Null } catch { } }
  }
  try { Invoke-LocalRcon 'cmend wave clear' | Out-Null } catch { }
  try { Invoke-LocalRcon 'cmend boss kill cleanup' | Out-Null } catch { }
  if ($null -ne $originalCore -and $originalRequiredPlayers -ge 2 -and $originalRequiredPlayers -le 20) {
    try {
      Invoke-LocalRcon 'cmend core remove confirm' | Out-Null
      Invoke-LocalRcon ("cmend core setat $($originalCore[0]) $($originalCore[1]) $($originalCore[2]) $originalRequiredPlayers") | Out-Null
    } catch { Write-Warning "Local event configuration restore failed: $($_.Exception.Message)" }
  }
}
