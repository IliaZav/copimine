[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftOfficialA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftOfficialB',
  [int]$BotDurationSeconds = 1200,
  [int]$TimeoutSeconds = 1100
)

# Local-only official two-player run.  This driver prepares the isolated event
# state and then lets the real countdown, wave objectives, boss controller and
# victory transaction progress without test-wave, artificial-damage or boss
# spawn shortcuts.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$statePath = Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml'
$evidencePath = Join-Path $runtimeRoot 'official-two-player-live.log'
$botLogDirectory = Join-Path $runtimeRoot 'official-two-player-bots'

$configPath = Join-Path $root 'copimine-end-event\config.yml'
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: official End Rift run requires environment: local.'
}
if (-not (Test-Path -LiteralPath $serverDir -PathType Container)) {
  throw "Isolated Paper directory is missing: $serverDir"
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local player bot is missing: $botScript"
}
if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
  throw "Local Paper log is missing: $paperLog"
}
$properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Refused: the official run is not pointed at the isolated local ports.'
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

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

function Get-CoreCoordinates {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'core=.*?(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) {
    throw "Core coordinates were not exposed by local status:`n$Status"
  }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Get-EventId {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'event=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) {
    throw "Event id was not exposed by local status:`n$Status"
  }
  return $match.Groups[1].Value
}

function Get-PadCoordinates {
  for ($attempt = 0; $attempt -lt 20; $attempt++) {
    $yaml = Get-Content -LiteralPath $statePath -Raw
    $matches = [Regex]::Matches($yaml,
      '(?m)^-\s*x:\s*(-?\d+)\s*\r?\n\s*y:\s*(-?\d+)\s*\r?\n\s*z:\s*(-?\d+)')
    if ($matches.Count -ge 2) {
      return @(
        @([int]$matches[0].Groups[1].Value, [int]$matches[0].Groups[2].Value, [int]$matches[0].Groups[3].Value),
        @([int]$matches[1].Groups[1].Value, [int]$matches[1].Groups[2].Value, [int]$matches[1].Groups[3].Value)
      )
    }
    Start-Sleep -Milliseconds 250
  }
  throw 'The isolated event state did not persist two rune coordinates.'
}

function Format-Coordinate {
  param([double]$Value)
  return $Value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Teleport-Player {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][double]$X,
    [Parameter(Mandatory = $true)][double]$Y,
    [Parameter(Mandatory = $true)][double]$Z
  )
  $null = Invoke-LocalRcon ("tp $Name $(Format-Coordinate $X) $(Format-Coordinate $Y) " +
      "$(Format-Coordinate $Z) 180 0")
}

function Keep-PlayersAtPads {
  param([Parameter(Mandatory = $true)][object[]]$Pads)
  Teleport-Player -Name $FirstBotName -X ($Pads[0][0] + 0.5D) -Y $Pads[0][1] -Z ($Pads[0][2] + 0.5D)
  Teleport-Player -Name $SecondBotName -X ($Pads[1][0] + 0.5D) -Y $Pads[1][1] -Z ($Pads[1][2] + 0.5D)
}

function Keep-PlayersAtCoreRing {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  Teleport-Player -Name $FirstBotName -X ($Core[0] + 6.0D) -Y $Core[1] -Z ($Core[2] + 0.5D)
  Teleport-Player -Name $SecondBotName -X ($Core[0] - 6.0D) -Y $Core[1] -Z ($Core[2] + 0.5D)
}

$script:CombatSweepIndex = 0
function Keep-PlayersAtCombatSweep {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  $sweep = @(
    [pscustomobject]@{ X = $Core[0] + 6.0D; Y = [double]$Core[1]; Z = $Core[2] + 0.5D },
    [pscustomobject]@{ X = $Core[0] - 6.0D; Y = [double]$Core[1]; Z = $Core[2] + 0.5D },
    [pscustomobject]@{ X = $Core[0] + 0.5D; Y = [double]$Core[1]; Z = $Core[2] + 6.0D },
    [pscustomobject]@{ X = $Core[0] + 0.5D; Y = [double]$Core[1]; Z = $Core[2] - 6.0D },
    [pscustomobject]@{ X = $Core[0] - 5.5D; Y = [double]$Core[1]; Z = $Core[2] + 5.5D },
    [pscustomobject]@{ X = $Core[0] - 5.5D; Y = [double]$Core[1]; Z = $Core[2] - 5.5D },
    [pscustomobject]@{ X = $Core[0] + 6.5D; Y = [double]$Core[1]; Z = $Core[2] + 5.5D },
    [pscustomobject]@{ X = $Core[0] + 6.5D; Y = [double]$Core[1]; Z = $Core[2] - 5.5D },
    # Cover the outer spawn ring as well.  Wave 5 can legitimately place a
    # shulker at about ten blocks from the core; visiting only the inner ring
    # makes the protocol clients miss a valid target and leaves the official
    # run waiting forever even though the server AI is active.
    [pscustomobject]@{ X = $Core[0] + 1.5D; Y = [double]$Core[1]; Z = $Core[2] - 10.5D },
    [pscustomobject]@{ X = $Core[0] - 10.5D; Y = [double]$Core[1]; Z = $Core[2] + 1.5D },
    [pscustomobject]@{ X = $Core[0] + 1.5D; Y = [double]$Core[1]; Z = $Core[2] + 10.5D },
    [pscustomobject]@{ X = $Core[0] + 10.5D; Y = [double]$Core[1]; Z = $Core[2] + 1.5D }
  )
  $first = $sweep[$script:CombatSweepIndex % $sweep.Count]
  $second = $sweep[($script:CombatSweepIndex + 2) % $sweep.Count]
  $script:CombatSweepIndex++
  Teleport-Player -Name $FirstBotName -X $first.X -Y $first.Y -Z $first.Z
  Teleport-Player -Name $SecondBotName -X $second.X -Y $second.Y -Z $second.Z
}

function Keep-PlayersAtPoint {
  param([Parameter(Mandatory = $true)][pscustomobject]$Point)
  Teleport-Player -Name $FirstBotName -X $Point.X -Y $Point.Y -Z $Point.Z
  Teleport-Player -Name $SecondBotName -X ($Point.X + 1.0D) -Y $Point.Y -Z $Point.Z
}

function Get-BossUuid {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status,
    'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) {
    throw "Official boss UUID was not exposed by local status:`n$Status"
  }
  return $match.Groups[1].Value
}

function Get-BossPosition {
  param([Parameter(Mandatory = $true)][string]$Uuid)
  $data = Invoke-LocalRcon ("data get entity $Uuid Pos")
  $match = [Regex]::Match($data, '\[\s*([-0-9.]+)d,\s*([-0-9.]+)d,\s*([-0-9.]+)d\s*\]')
  if (-not $match.Success) {
    throw "Unable to parse official boss position:`n$data"
  }
  return @(
    [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  )
}

function Keep-PlayersNearBoss {
  param([Parameter(Mandatory = $true)][string]$Uuid)
  $position = Get-BossPosition $Uuid
  Teleport-Player -Name $FirstBotName -X ($position[0] + 1.8D) -Y $position[1] -Z $position[2]
  Teleport-Player -Name $SecondBotName -X ($position[0] - 1.8D) -Y $position[1] -Z $position[2]
}

function Wait-LocalPlayers {
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if ($list -match [Regex]::Escape($FirstBotName) -and
        $list -match [Regex]::Escape($SecondBotName)) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Official two-player bots did not join: $FirstBotName, $SecondBotName"
}

$script:LogOffset = [long](Get-Item -LiteralPath $paperLog).Length
$script:LogEvidence = [Text.StringBuilder]::new()
function Read-NewPaperLog {
  $stream = [IO.File]::Open($paperLog, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    if ($script:LogOffset -gt $stream.Length) {
      $script:LogOffset = 0L
    }
    $stream.Seek($script:LogOffset, [IO.SeekOrigin]::Begin) | Out-Null
    $length = [int]($stream.Length - $script:LogOffset)
    if ($length -le 0) {
      return ''
    }
    $buffer = [byte[]]::new($length)
    $read = $stream.Read($buffer, 0, $length)
    $script:LogOffset = $stream.Length
    return [Text.Encoding]::UTF8.GetString($buffer, 0, $read)
  } finally {
    $stream.Dispose()
  }
}

function Wait-LogRegex {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int]$WaitSeconds,
    [scriptblock]$DuringWait = $null
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  $nextAction = Get-Date
  while ((Get-Date) -lt $deadline) {
    $chunk = Read-NewPaperLog
    if (-not [string]::IsNullOrEmpty($chunk)) {
      [void]$script:LogEvidence.Append($chunk)
    }
    $evidence = $script:LogEvidence.ToString()
    if ([Regex]::IsMatch($evidence, $Pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
      Write-Evidence "OFFICIAL_LOG_PASS pattern=$Pattern"
      return
    }
    if ($null -ne $DuringWait -and (Get-Date) -ge $nextAction) {
      & $DuringWait
      $nextAction = (Get-Date).AddSeconds(1.5D)
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for local Paper log pattern '$Pattern'."
}

function Wait-SecondsWithAction {
  param(
    [Parameter(Mandatory = $true)][int]$Seconds,
    [Parameter(Mandatory = $true)][scriptblock]$Action
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  $nextAction = Get-Date
  while ((Get-Date) -lt $deadline) {
    $chunk = Read-NewPaperLog
    if (-not [string]::IsNullOrEmpty($chunk)) {
      [void]$script:LogEvidence.Append($chunk)
    }
    if ((Get-Date) -ge $nextAction) {
      & $Action
      $nextAction = (Get-Date).AddSeconds(1.0D)
    }
    Start-Sleep -Milliseconds 250
  }
}

function Assert-BossStage {
  param(
    [Parameter(Mandatory = $true)][string]$Stage,
    [Parameter(Mandatory = $true)][string]$BossUuid
  )
  $debug = Invoke-LocalRcon -CommandText 'cmend debug ai'
  if ($debug -notmatch ('stage=.*' + [Regex]::Escape($Stage))) {
    throw "Official boss stage $Stage was not exposed by AI diagnostics:`n$debug"
  }
  Write-Evidence "OFFICIAL_BOSS_STAGE stage=$Stage boss=$BossUuid $debug"
}

$processes = @()
$pads = $null
$core = $null
$bossUuid = $null
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  [IO.File]::WriteAllText($evidencePath, "OFFICIAL_TWO_PLAYER_START $(Get-Date -Format o)", [Text.UTF8Encoding]::new($false))

  # This is a local event-session reset only.  It restores the saved vanilla
  # block data and event-owned entities; it never wipes the world, players or
  # the isolated PostgreSQL data directory.
  $null = Invoke-LocalRcon -CommandText 'cmend core remove confirm'
  Start-Sleep -Milliseconds 500
  foreach ($mobType in @('spider', 'enderman', 'shulker')) {
    # The local flat test scene may contain naturally spawned mobs from an
    # earlier manual session.  Remove only those three types inside the
    # bounded arena footprint after event-owned entities were cleaned; no
    # world, player data or PostgreSQL reset is involved.
    $null = Invoke-LocalRcon -CommandText ("kill @e[type=$mobType,x=-12,y=65,z=-59,dx=40,dy=6,dz=40]")
  }
  $null = Invoke-LocalRcon -CommandText 'cmend core setat 8 68 -39 2'
  Start-Sleep -Milliseconds 500
  $null = Invoke-LocalRcon -CommandText 'cmend resources reset confirm'
  foreach ($resource in @(
    'DIAMOND 100', 'ENDER_EYE 64', 'AMETHYST_SHARD 128', 'BLAZE_ROD 64'
  )) {
    $null = Invoke-LocalRcon -CommandText ("cmend resources add $resource")
  }
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  if ($status -notmatch 'state=.*READY_FOR_PLAYERS') {
    throw "Local Core did not become READY_FOR_PLAYERS after resource preparation:`n$status"
  }
  $core = Get-CoreCoordinates $status
  $coreX = [double]$core[0]
  $coreY = [double]$core[1]
  $coreZ = [double]$core[2]
  $eventId = Get-EventId $status
  $pads = Get-PadCoordinates
  Write-Evidence "OFFICIAL_LOCAL_SETUP_PASS event=$eventId core=$($core -join ',') pads=$($pads.Count)"

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  foreach ($name in @($FirstBotName, $SecondBotName)) {
    $botLog = Join-Path $botLogDirectory ($name + '.log')
    $botErr = Join-Path $botLogDirectory ($name + '.err.log')
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
      (Format-Coordinate ($coreX + 0.5D)) + ' ' + (Format-Coordinate $coreY) + ' ' +
      (Format-Coordinate ($coreZ + 0.5D)) + ' ' + (Format-Coordinate 20.0D)
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botLog -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
  }
  Wait-LocalPlayers
  foreach ($name in @($FirstBotName, $SecondBotName)) {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("clear $name")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon -CommandText ("give $name minecraft:netherite_sword")
    $null = Invoke-LocalRcon -CommandText ("enchant $name minecraft:sharpness 5")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:regeneration 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:strength 1000 20 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:speed 1000 2 true")
  }

  Keep-PlayersAtPads -Pads $pads
  Wait-LogRegex -Pattern 'RITUAL_STARTED' -WaitSeconds 20 -DuringWait { Keep-PlayersAtPads -Pads $pads }
  Wait-LogRegex -Pattern 'RITUAL_COMPLETED' -WaitSeconds 80 -DuringWait { Keep-PlayersAtPads -Pads $pads }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=1' -WaitSeconds 20
  Keep-PlayersAtCombatSweep -Core $core
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=1' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=2' -WaitSeconds 30
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=2' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=3' -WaitSeconds 30
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_STARTED.*wave=3.*portals=3' -WaitSeconds 20
  $portalY = $coreY
  $portals = @(
    [pscustomobject]@{ X = $coreX + 8.5D; Y = $portalY; Z = $coreZ + 0.5D },
    [pscustomobject]@{ X = $coreX - 3.5D; Y = $portalY; Z = $coreZ + 7.428D },
    [pscustomobject]@{ X = $coreX - 3.5D; Y = $portalY; Z = $coreZ - 6.428D }
  )
  foreach ($portal in $portals) {
    Wait-SecondsWithAction -Seconds 7 -Action { Keep-PlayersAtPoint -Point $portal }
  }
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=3' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCoreRing -Core $core }
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=3' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=4' -WaitSeconds 30
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_STARTED.*wave=4.*TOWER_DEFENSE' -WaitSeconds 20
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=5' -WaitSeconds 30
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_STARTED.*wave=5.*RIFT_STORM' -WaitSeconds 20
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=5' -WaitSeconds 90 -DuringWait { Keep-PlayersAtCoreRing -Core $core }
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=5' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'BOSS_CINEMATIC_STARTED' -WaitSeconds 30
  Wait-LogRegex -Pattern 'FINAL_WAVE_STARTED' -WaitSeconds 30
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=FINAL' -WaitSeconds 300 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-LogRegex -Pattern 'BOSS_SPAWNED' -WaitSeconds 120
  $bossUuid = Get-BossUuid (Invoke-LocalRcon -CommandText 'cmend status')
  Assert-BossStage -Stage 'AWAKENING' -BossUuid $bossUuid

  Wait-LogRegex -Pattern 'BOSS_STAGE_TRANSITION.*to=HUNTER' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'HUNTER' -BossUuid $bossUuid
  Wait-LogRegex -Pattern 'BOSS_STAGE_TRANSITION.*to=DISTORTION' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'DISTORTION' -BossUuid $bossUuid
  Wait-LogRegex -Pattern 'BOSS_CAST_STATE.*ABSORPTION_CHANNEL' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Wait-LogRegex -Pattern 'BOSS_ABSORPTION_BUFF' -WaitSeconds 30 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'ABSORPTION' -BossUuid $bossUuid
  Wait-LogRegex -Pattern 'BOSS_STAGE_TRANSITION.*to=CATASTROPHE' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'CATASTROPHE' -BossUuid $bossUuid
  Wait-LogRegex -Pattern 'BOSS_CAST_STATE.*JUDGMENT_CAST' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Wait-LogRegex -Pattern 'BOSS_JUDGMENT_SAFE_ZONE' -WaitSeconds 30 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'CATASTROPHE' -BossUuid $bossUuid

  Wait-LogRegex -Pattern 'BOSS_DEFEAT_COMMITTED' -WaitSeconds 240 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Wait-LogRegex -Pattern 'BOSS_DEFEATED' -WaitSeconds 30
  Wait-LogRegex -Pattern 'END_EVENT_WAVE_COMBAT_CLEANUP.*reason=official-boss-defeat' -WaitSeconds 30
  Wait-LogRegex -Pattern 'VICTORY' -WaitSeconds 60
  $finalStatus = Invoke-LocalRcon -CommandText 'cmend status'
  if ($finalStatus -notmatch 'boss=.*none' -or $finalStatus -notmatch 'event-mobs=.*0') {
    throw "Official victory left event entities behind:`n$finalStatus"
  }
  Write-Evidence "OFFICIAL_TWO_PLAYER_PASS event=$eventId boss=$bossUuid waves=1,2,3,4,5,final stages=AWAKENING,HUNTER,DISTORTION,ABSORPTION,CATASTROPHE,JUDGMENT victory=true"
  Write-Evidence $finalStatus
} finally {
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) {
      try { $process.Kill() } catch { }
    }
  }
  foreach ($process in $processes) {
    if ($process) {
      try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
  }
  # Restore a clean, configured local scene for the next run.  The same
  # command removes old event mobs/bosses and restores the saved block data;
  # no world or player-data wipe is used.
  try { Invoke-LocalRcon -CommandText 'cmend core remove confirm' | Out-Null } catch { }
  try {
    Invoke-LocalRcon -CommandText 'cmend core setat 8 68 -39 2' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add DIAMOND 100' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add ENDER_EYE 64' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add AMETHYST_SHARD 128' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add BLAZE_ROD 64' | Out-Null
  } catch {
    Write-Warning "Local scene restore failed: $($_.Exception.Message)"
  }
}
