[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftOfficialA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftOfficialB',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string[]]$AdditionalBotNames = @(),
  [int]$BotDurationSeconds = 1200,
  [int]$TimeoutSeconds = 1100,
  [switch]$TowerFailureProbe,
  [switch]$RewardPickupProbe,
  [switch]$StopAfterRewardProbe,
  [switch]$StopAfterWave3,
  [switch]$StopAfterWave4
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
$PlayerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames)
if ($PlayerNames.Count -lt 2 -or $PlayerNames.Count -gt 20) {
  throw "Official End Rift driver supports two to twenty local players; received $($PlayerNames.Count)."
}
if (@($PlayerNames | Select-Object -Unique).Count -ne $PlayerNames.Count) {
  throw 'Official End Rift driver requires unique local player names.'
}
$runLabel = switch ($PlayerNames.Count) {
  3 { 'OFFICIAL_THREE_PLAYER_START'; break }
  5 { 'OFFICIAL_FIVE_PLAYER_START'; break }
  10 { 'OFFICIAL_TEN_PLAYER_START'; break }
  default { 'OFFICIAL_TWO_PLAYER_START' }
}
$passLabel = switch ($PlayerNames.Count) {
  3 { 'OFFICIAL_THREE_PLAYER_PASS'; break }
  5 { 'OFFICIAL_FIVE_PLAYER_PASS'; break }
  10 { 'OFFICIAL_TEN_PLAYER_PASS'; break }
  default { 'OFFICIAL_TWO_PLAYER_PASS' }
}
if ($PlayerNames.Count -eq 5) {
  $evidencePath = Join-Path $runtimeRoot 'official-five-player-live.log'
  $botLogDirectory = Join-Path $runtimeRoot 'official-five-player-bots'
} elseif ($PlayerNames.Count -eq 10) {
  $evidencePath = Join-Path $runtimeRoot 'official-ten-player-live.log'
  $botLogDirectory = Join-Path $runtimeRoot 'official-ten-player-bots'
} elseif ($PlayerNames.Count -eq 3) {
  $evidencePath = Join-Path $runtimeRoot 'official-three-player-live.log'
  $botLogDirectory = Join-Path $runtimeRoot 'official-three-player-bots'
}
$botControlDirectory = Join-Path $botLogDirectory 'control'

$configPath = Join-Path $root 'copimine-end-event\config.yml'
$configText = Get-Content -LiteralPath $configPath -Raw
$isV3Flow = $configText -match '(?m)^\s*schema-version:\s*3\s*$'
if ($configText -notmatch '(?m)^environment:\s*local\s*$') {
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
  $expectedRuneCount = $PlayerNames.Count
  for ($attempt = 0; $attempt -lt 20; $attempt++) {
    $yaml = Get-Content -LiteralPath $statePath -Raw
    $matches = [Regex]::Matches($yaml,
      '(?m)^-\s*x:\s*(-?\d+)\s*\r?\n\s*y:\s*(-?\d+)\s*\r?\n\s*z:\s*(-?\d+)')
    if ($matches.Count -ge $expectedRuneCount) {
      $coordinates = @()
      for ($index = 0; $index -lt $expectedRuneCount; $index++) {
        $coordinates += ,@(
          [int]$matches[$index].Groups[1].Value,
          [int]$matches[$index].Groups[2].Value,
          [int]$matches[$index].Groups[3].Value
        )
      }
      return ,$coordinates
    }
    Start-Sleep -Milliseconds 250
  }
  throw "The isolated event state did not persist $expectedRuneCount rune coordinates."
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
  if ($Pads.Count -lt $PlayerNames.Count) {
    throw "Expected one persisted rune per local player; pads=$($Pads.Count) players=$($PlayerNames.Count)."
  }
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    Teleport-Player -Name $PlayerNames[$index] -X ($Pads[$index][0] + 0.5D) `
      -Y $Pads[$index][1] -Z ($Pads[$index][2] + 0.5D)
  }
}

function Keep-PlayersAtCoreRing {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  $positions = @(
    [pscustomobject]@{ X = $Core[0] + 6.0D; Y = [double]$Core[1]; Z = $Core[2] + 0.5D },
    [pscustomobject]@{ X = $Core[0] - 6.0D; Y = [double]$Core[1]; Z = $Core[2] + 0.5D },
    [pscustomobject]@{ X = $Core[0] + 0.5D; Y = [double]$Core[1]; Z = $Core[2] + 6.0D },
    [pscustomobject]@{ X = $Core[0] + 0.5D; Y = [double]$Core[1]; Z = $Core[2] - 6.0D },
    [pscustomobject]@{ X = $Core[0] - 5.0D; Y = [double]$Core[1]; Z = $Core[2] + 5.0D }
  )
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $position = $positions[$index % $positions.Count]
    Teleport-Player -Name $PlayerNames[$index] -X $position.X -Y $position.Y -Z $position.Z
  }
}

function Keep-PlayersAtCore {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  # The carrier charge is delivered only while the holder is within the
  # server-authoritative 2.5 block radius of the Core.  Keep the disposable
  # clients on the Core top face during W1 so the live probe exercises pickup
  # and delivery instead of parking them outside the delivery radius.
  $point = [pscustomobject]@{
    X = [double]$Core[0] + 0.5D
    Y = [double]$Core[1] + 1.0D
    Z = [double]$Core[2] + 0.5D
  }
  Keep-PlayersAtPoint -Point $point
}

function Keep-PlayersAtWave1Carrier {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  # W1 is a real objective, not just a mob-clear probe.  The charge is
  # intentionally dropped at the defeated carrier's location, so the
  # disposable clients must first visit that display and then return to the
  # Core after pickup.  Read the authoritative display position through RCON;
  # never move the server-side objective or widen its delivery radius for a
  # test.
  $evidence = $script:LogEvidence.ToString()
  $created = [Regex]::Matches($evidence,
    'V2_CARRIER_CHARGE_CREATED event=' + [Regex]::Escape($eventId) +
    ' charge=([0-9a-fA-F-]{36})')
  $picked = [Regex]::Matches($evidence,
    'V2_CARRIER_PICKED_UP event=' + [Regex]::Escape($eventId) + '\b')
  $delivered = [Regex]::Matches($evidence,
    'V2_CARRIER_DELIVERED event=' + [Regex]::Escape($eventId) +
    ' charge=(\d+)/3')
  $createdIndex = if ($created.Count -eq 0) { -1 } else { $created[$created.Count - 1].Index }
  $pickedIndex = if ($picked.Count -eq 0) { -1 } else { $picked[$picked.Count - 1].Index }
  $deliveredIndex = if ($delivered.Count -eq 0) { -1 } else { $delivered[$delivered.Count - 1].Index }
  if ($createdIndex -gt $pickedIndex) {
    $chargeUuid = $created[$created.Count - 1].Groups[1].Value
    # Query the bounded Pos path directly.  Full entity NBT is truncated by
    # RCON before Pos on Paper builds with a large PDC payload, which made the
    # previous parser silently fall back to the combat sweep and miss the
    # dropped charge in larger rosters.
    $probe = Invoke-LocalRcon -CommandText ("data get entity $chargeUuid Pos")
    $positionMatch = [Regex]::Match($probe,
      '\[(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\]')
    if ($positionMatch.Success) {
      Keep-PlayersAtPoint -Point ([pscustomobject]@{
          X = [double]$positionMatch.Groups[1].Value
          Y = [double]$positionMatch.Groups[2].Value
          Z = [double]$positionMatch.Groups[3].Value
        })
      return
    }
  }
  if ($pickedIndex -gt $deliveredIndex) {
    Keep-PlayersAtCore -Core $Core
    return
  }
  Keep-PlayersAtCombatSweep -Core $Core
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
    # skeleton at about ten blocks from the core; visiting only the inner ring
    # makes the protocol clients miss a valid target and leaves the official
    # run waiting forever even though the server AI is active.
    [pscustomobject]@{ X = $Core[0] + 1.5D; Y = [double]$Core[1]; Z = $Core[2] - 10.5D },
    [pscustomobject]@{ X = $Core[0] - 10.5D; Y = [double]$Core[1]; Z = $Core[2] + 1.5D },
    [pscustomobject]@{ X = $Core[0] + 1.5D; Y = [double]$Core[1]; Z = $Core[2] + 10.5D },
    [pscustomobject]@{ X = $Core[0] + 10.5D; Y = [double]$Core[1]; Z = $Core[2] + 1.5D }
  )
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $position = $sweep[($script:CombatSweepIndex + ($index * 2)) % $sweep.Count]
    Teleport-Player -Name $PlayerNames[$index] -X $position.X -Y $position.Y -Z $position.Z
  }
  $script:CombatSweepIndex++
}

function Get-CombatMobPositions {
  $debug = Invoke-LocalRcon -CommandText 'cmend debug ai'
  $targetsLine = (($debug -split "`r?`n") | Where-Object { $_ -match 'AI_TARGETS' }) -join ' '
  $matches = [Regex]::Matches($targetsLine,
    '(?:WAVE_MOB|ELITE|FINAL_WAVE):[0-9a-fA-F-]+=[^;]*?\s(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)')
  $positions = @()
  foreach ($match in $matches) {
    $positions += [pscustomobject]@{
      X = [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
      Y = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
      Z = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
    }
  }
  # Return the individual position records.  Wrapping the collection in a
  # unary comma makes the caller receive one Object[] whose X/Z properties
  # are arrays; the subsequent teleport then fails after the objective has
  # already completed.  The live driver needs scalar coordinates per mob.
  return $positions
}

function Keep-PlayersAtCombatMobs {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  $mobs = @(Get-CombatMobPositions)
  if ($mobs.Count -eq 0) {
    Keep-PlayersAtCombatSweep -Core $Core
    return
  }
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $mob = $mobs[$index % $mobs.Count]
    $side = if (($index % 2) -eq 0) { 1.8D } else { -1.8D }
    Teleport-Player -Name $PlayerNames[$index] -X ($mob.X + $side) -Y $mob.Y -Z $mob.Z
  }
}

function Get-OfflineUuidText {
  param([Parameter(Mandatory = $true)][string]$Name)
  $md5 = [Security.Cryptography.MD5]::Create()
  try {
    $digest = $md5.ComputeHash([Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$Name"))
  } finally {
    $md5.Dispose()
  }
  $digest[6] = [byte](($digest[6] -band 0x0f) -bor 0x30)
  $digest[8] = [byte](($digest[8] -band 0x3f) -bor 0x80)
  $hex = -join ($digest | ForEach-Object { $_.ToString('x2') })
  return $hex.Substring(0, 8) + '-' + $hex.Substring(8, 4) + '-' +
    $hex.Substring(12, 4) + '-' + $hex.Substring(16, 4) + '-' + $hex.Substring(20, 12)
}

function Get-WaveSixChamberCount {
  if ($PlayerNames.Count -le 3) {
    return $PlayerNames.Count
  }
  return 4
}

function Get-WaveSixPlayerChamber {
  param([Parameter(Mandatory = $true)][string]$Name)
  $chamberCount = Get-WaveSixChamberCount
  $rankedPlayers = @(
    foreach ($playerName in $PlayerNames) {
      [pscustomobject]@{
        Name = $playerName
        OfflineUuid = Get-OfflineUuidText -Name $playerName
      }
    }
  ) | Sort-Object OfflineUuid
  for ($rank = 0; $rank -lt $rankedPlayers.Count; $rank++) {
    if ($rankedPlayers[$rank].Name -eq $Name) {
      return $rank % $chamberCount
    }
  }
  throw "Wave 6 probe could not assign local player to a chamber: $Name"
}

function Get-WaveSixChamberMob {
  param(
    [Parameter(Mandatory = $true)][int[]]$Core,
    [Parameter(Mandatory = $true)][int]$Chamber,
    [Parameter(Mandatory = $true)][int]$ChamberCount,
    [object[]]$Mobs = @()
  )
  $best = $null
  $centerAngle = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $Chamber / $ChamberCount)
  $halfSector = [Math]::PI / $ChamberCount - (8.0D * [Math]::PI / 180.0D)
  foreach ($mob in $Mobs) {
    $dx = [double]$mob.X - [double]$Core[0]
    $dz = [double]$mob.Z - [double]$Core[2]
    $radius = [Math]::Sqrt($dx * $dx + $dz * $dz)
    if ($radius -lt 3.5D -or $radius -gt 18.0D) {
      continue
    }
    $angle = [Math]::Atan2($dz, $dx)
    $delta = [Math]::Atan2([Math]::Sin($angle - $centerAngle), [Math]::Cos($angle - $centerAngle))
    $distanceFromCenter = [Math]::Abs($delta)
    if ($distanceFromCenter -gt [Math]::Max(0.05D, $halfSector)) {
      continue
    }
    if ($null -eq $best -or $distanceFromCenter -lt $best.AngularDistance) {
      $best = [pscustomobject]@{
        Mob = $mob
        AngularDistance = $distanceFromCenter
      }
    }
  }
  if ($null -ne $best) {
    return $best.Mob
  }
  return [pscustomobject]@{
    X = [double]$Core[0] + [Math]::Cos($centerAngle) * 10.0D
    Y = [double]$Core[1]
    Z = [double]$Core[2] + [Math]::Sin($centerAngle) * 10.0D
  }
}

function Keep-PlayersAtWaveSixMobs {
  param([Parameter(Mandatory = $true)][int[]]$Core)
  $mobs = @(Get-CombatMobPositions)
  $chamberCount = Get-WaveSixChamberCount
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $name = $PlayerNames[$index]
    $chamber = Get-WaveSixPlayerChamber -Name $name
    $mob = Get-WaveSixChamberMob -Core $Core -Chamber $chamber `
      -ChamberCount $chamberCount -Mobs $mobs
    # Keep the probe on the mob's exact chamber ray.  A generic nearest-mob
    # sweep can put player A in B's room, which would correctly be rejected by
    # the server's closed-chamber target policy and make this live test hang.
    Teleport-Player -Name $name -X ([double]$mob.X) -Y ([double]$mob.Y) -Z ([double]$mob.Z)
  }
}

function Keep-PlayersAtPoint {
  param([Parameter(Mandatory = $true)][pscustomobject]$Point)
  $offsets = @(
    [pscustomobject]@{ X = 0.0D; Z = 0.0D },
    [pscustomobject]@{ X = 1.0D; Z = 0.0D },
    [pscustomobject]@{ X = -1.0D; Z = 0.0D },
    [pscustomobject]@{ X = 0.0D; Z = 1.0D },
    [pscustomobject]@{ X = 0.0D; Z = -1.0D }
  )
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $offset = $offsets[$index % $offsets.Count]
    Teleport-Player -Name $PlayerNames[$index] -X ($Point.X + $offset.X) -Y $Point.Y -Z ($Point.Z + $offset.Z)
  }
}

function Keep-PlayersAtV3ObeliskTargets {
  param([Parameter(Mandatory = $true)][object[]]$Targets)
  if ($Targets.Count -eq 0) {
    Keep-PlayersAtCombatSweep -Core $core
    return
  }
  # Keep the two disposable clients at one source at a time.  A reflected
  # projectile follows the player's real look vector, so standing near the
  # current source gives the live probe a short, deterministic return lane.
  # The dwell is long enough for three fire intervals; cycling the points is
  # still a real client interaction and does not alter server-side targeting
  # or reflection semantics.
  $now = Get-Date
  if ($null -eq $script:V3ObeliskProbeIndex) {
    $script:V3ObeliskProbeIndex = 0
    $script:V3ObeliskProbeNextSwitch = $now.AddSeconds(15)
  } elseif ($now -ge $script:V3ObeliskProbeNextSwitch) {
    $script:V3ObeliskProbeIndex = ($script:V3ObeliskProbeIndex + 1) % $Targets.Count
    $script:V3ObeliskProbeNextSwitch = $now.AddSeconds(15)
  }
  $target = $Targets[$script:V3ObeliskProbeIndex % $Targets.Count]
  $dx = [double]$target.X - ($coreX + 0.5D)
  $dz = [double]$target.Z - ($coreZ + 0.5D)
  $length = [Math]::Max(0.01D, [Math]::Sqrt($dx * $dx + $dz * $dz))
  # Stand just inside the obelisk's projectile approach path.  The player
  # must be close enough to send a real use_entity attack, but never inside
  # the journaled 3x3 base of the real block structure.
  $safeX = [double]$target.X - $dx / $length * 2.8D
  $safeZ = [double]$target.Z - $dz / $length * 2.8D
  foreach ($name in $PlayerNames) {
    Teleport-Player -Name $name -X $safeX -Y ([double]$target.Y) -Z $safeZ
  }
}

function Get-V3ObeliskCount {
  param([Parameter(Mandatory = $true)][int]$PlayerCount)
  if ($PlayerCount -le 7) { return 4 }
  if ($PlayerCount -le 10) { return 5 }
  if ($PlayerCount -le 15) { return 5 }
  return 6
}

function Get-V3ObeliskTargets {
  param(
    [Parameter(Mandatory = $true)][double]$CoreX,
    [Parameter(Mandatory = $true)][double]$CoreY,
    [Parameter(Mandatory = $true)][double]$CoreZ,
    [Parameter(Mandatory = $true)][int]$Count
  )
  $targets = @()
  for ($index = 0; $index -lt $Count; $index++) {
    # Keep this calculation identical to V3ObeliskPlacementPolicy.candidates:
    # the policy rounds radial offsets first, then the adapter adds the block
    # center's .5 coordinate.  A four-player run happens to be a cross; for
    # five and six players the angles are not axis-aligned, so a hard-coded
    # four-point probe would check the wrong blocks and steer reflections away.
    $angle = -[Math]::PI / 2.0D +
      (2.0D * [Math]::PI * $index / [double]$Count)
    $offsetX = [int][Math]::Round([Math]::Cos($angle) * 9.0D)
    $offsetZ = [int][Math]::Round([Math]::Sin($angle) * 9.0D)
    $targets += [pscustomobject]@{
      X = $CoreX + $offsetX + 0.5D
      Y = $CoreY
      Z = $CoreZ + $offsetZ + 0.5D
    }
  }
  return $targets
}

function Assert-V3ObeliskBaseBlocks {
  param(
    [Parameter(Mandatory = $true)][object[]]$Targets,
    [Parameter(Mandatory = $true)][string]$Material,
    [Parameter(Mandatory = $true)][int]$Expected
  )
  $objective = 'endrift_v3_probe'
  $holder = '#endrift_v3_probe'
  $null = Invoke-LocalRcon -CommandText ("scoreboard objectives add $objective dummy")
  $matched = 0
  try {
    foreach ($target in $Targets) {
      $x = [int][Math]::Floor([double]$target.X)
      $y = [int][Math]::Floor([double]$target.Y)
      $z = [int][Math]::Floor([double]$target.Z)
      $null = Invoke-LocalRcon -CommandText ("scoreboard players set $holder $objective 0")
      $null = Invoke-LocalRcon -CommandText ("execute if block $x $y $z minecraft:$Material run scoreboard players set $holder $objective 1")
      $score = Invoke-LocalRcon -CommandText ("scoreboard players get $holder $objective")
      if ($score -match '(?<!\d)1(?!\d)') {
        $matched++
      }
    }
  } finally {
    $null = Invoke-LocalRcon -CommandText ("scoreboard objectives remove $objective")
  }
  Write-Evidence "OFFICIAL_V3_OBELISK_BLOCK_PROBE material=$Material matched=$matched expected=$Expected"
  if ($matched -ne $Expected) {
    throw "V3 obelisk block probe failed: material=$Material matched=$matched expected=$Expected"
  }
}

function Assert-WaveRewardPickup {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][int[]]$Core,
    [int]$Wave = 1
  )
  # Wave rewards are physical Item entities at the Core.  Visit one player at
  # a time so the ownership guard is exercised: the first player cannot take
  # the second player's bundle, and both players must still receive the
  # guaranteed wave-1 COOKED_BEEF stack.
  $probeTag = 'endrift_reward_probe_present'
  $null = Invoke-LocalRcon ("tag $Name remove $probeTag")
  # Rewards are spawned on the Core's top face.  Stand on that same face so
  # the player is within the vanilla pickup radius of every personal stack;
  # the core block is solid below the player and does not push the client out.
  $pickupX = $Core[0] + 0.5D
  $pickupY = $Core[1] + 1.0D
  $pickupZ = $Core[2] + 0.5D
  for ($attempt = 0; $attempt -lt 12; $attempt++) {
    # The protocol combat bot keeps sending its previous physics position
    # after an administrative teleport.  Re-assert the pickup position for
    # each bounded poll so the probe measures the item pickup path rather than
    # that client-side movement race.
    Teleport-Player -Name $Name -X $pickupX -Y $pickupY -Z $pickupZ
    Start-Sleep -Milliseconds 500
    # RCON truncates large Inventory NBT responses.  The item predicate is a
    # bounded server-side query and is therefore the authoritative check that
    # the guaranteed material actually entered this player's inventory.
    $null = Invoke-LocalRcon ("execute if items entity $Name container.* minecraft:cooked_beef run tag $Name add $probeTag")
    $tags = Invoke-LocalRcon ("data get entity $Name Tags")
    if ([Regex]::IsMatch($tags, [Regex]::Escape($probeTag))) {
      $null = Invoke-LocalRcon ("tag $Name remove $probeTag")
      Write-Evidence "OFFICIAL_WAVE_REWARD_PICKUP_PASS event=$eventId wave=$Wave player=$Name material=minecraft:cooked_beef inventory_verified=true"
      return
    }
  }
  $null = Invoke-LocalRcon ("tag $Name remove $probeTag")
  $position = Invoke-LocalRcon ("data get entity $Name Pos")
  $inventory = Invoke-LocalRcon ("data get entity $Name Inventory")
  $itemProbe = Invoke-LocalRcon 'execute as @e[type=item,x=7,y=68,z=-40,dx=3,dy=3,dz=3] run data get entity @s Item'
  $itemPositions = Invoke-LocalRcon 'execute as @e[type=item,x=7,y=68,z=-40,dx=3,dy=3,dz=3] run data get entity @s Pos'
  $itemDelays = Invoke-LocalRcon 'execute as @e[type=item,x=7,y=68,z=-40,dx=3,dy=3,dz=1] run data get entity @s PickupDelay'
  $position = ($position -replace '\r?\n', ' ').Trim()
  $inventory = ($inventory -replace '\r?\n', ' ').Trim()
  $itemProbe = ($itemProbe -replace '\r?\n', ' ').Trim()
  $itemPositions = ($itemPositions -replace '\r?\n', ' ').Trim()
  $itemDelays = ($itemDelays -replace '\r?\n', ' ').Trim()
  Write-Evidence "OFFICIAL_WAVE_REWARD_PICKUP_DIAGNOSTICS event=$eventId wave=$Wave player=$Name position=<$position> inventory=<$inventory> nearby_items=<$itemProbe> nearby_positions=<$itemPositions> nearby_pickup_delays=<$itemDelays>"
  throw "Player $Name did not pick up the guaranteed wave-$Wave reward at the Core."
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
  $offsets = @(
    [pscustomobject]@{ X = 1.8D; Z = 0.0D },
    [pscustomobject]@{ X = -1.8D; Z = 0.0D },
    [pscustomobject]@{ X = 0.0D; Z = 1.8D },
    [pscustomobject]@{ X = 0.0D; Z = -1.8D },
    [pscustomobject]@{ X = 1.4D; Z = 1.4D }
  )
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $offset = $offsets[$index % $offsets.Count]
    Teleport-Player -Name $PlayerNames[$index] -X ($position[0] + $offset.X) `
      -Y $position[1] -Z ($position[2] + $offset.Z)
  }
}

function Get-GuardianHitboxPositions {
  # Interaction companions are tagged by the plugin and are the only
  # damageable surface for permanent guardians. Query only the bounded arena
  # footprint; do not scan the world or depend on client-side display models.
  $probe = Invoke-LocalRcon 'execute as @e[type=minecraft:interaction,tag=copimine_end_event,x=-12,y=65,z=-59,dx=40,dy=8,dz=40] run data get entity @s Pos'
  $positions = @()
  foreach ($match in [Regex]::Matches($probe,
      '\[\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*\]')) {
    $positions += [pscustomobject]@{
      X = [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
      Y = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
      Z = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
    }
  }
  return $positions
}

function Keep-PlayersAtGuardiansAndBoss {
  param([Parameter(Mandatory = $true)][string]$Uuid)
  $bossPosition = Get-BossPosition $Uuid
  $guardians = @(Get-GuardianHitboxPositions)
  $offsets = @(
    [pscustomobject]@{ X = 1.8D; Z = 0.0D },
    [pscustomobject]@{ X = -1.8D; Z = 0.0D },
    [pscustomobject]@{ X = 0.0D; Z = 1.8D },
    [pscustomobject]@{ X = 0.0D; Z = -1.8D },
    [pscustomobject]@{ X = 1.4D; Z = 1.4D }
  )
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    if ($index -lt $guardians.Count) {
      # Put one independent client beside each real Interaction hitbox. This
      # keeps the two-player probe bounded while proving that all permanent
      # guardians can be damaged concurrently; remaining clients stay on the
      # boss and observe the shield.
      $guardian = $guardians[$index]
      Teleport-Player -Name $PlayerNames[$index] -X ($guardian.X + 1.25D) `
        -Y ([double]$coreY) -Z $guardian.Z
      continue
    }
    $offset = $offsets[$index % $offsets.Count]
    Teleport-Player -Name $PlayerNames[$index] -X ($bossPosition[0] + $offset.X) `
      -Y $bossPosition[1] -Z ($bossPosition[2] + $offset.Z)
  }
}

function Set-OfficialBotCombatMode {
  param(
    [ValidateSet('PASSIVE', 'ACTIVE')]
    [string]$Mode,
    [int]$ActiveFromIndex = 0
  )
  for ($index = 0; $index -lt $PlayerNames.Count; $index++) {
    $shouldBeActive = if ($Mode -eq 'ACTIVE') {
      $index -ge $ActiveFromIndex
    } else {
      $index -lt $ActiveFromIndex
    }
    $marker = if ($shouldBeActive) { 'END_RIFT_RESUME' } else { 'END_RIFT_PASSIVE' }
    $requestedMode = if ($shouldBeActive) { 'ACTIVE' } else { 'PASSIVE' }
    # Chat markers are retained as human-readable evidence, but a burst of
    # tellraw packets is not a reliable process-control channel on a busy
    # local server. Each bot also polls its own mode file.
    Set-Content -LiteralPath (Join-Path $botControlDirectory ($PlayerNames[$index] + '.mode')) `
      -Value $requestedMode -NoNewline -Encoding ASCII
    $null = Invoke-LocalRcon -CommandText ("tellraw " + $PlayerNames[$index] +
      ' {"text":"' + $marker + '"}')
  }
  Write-Evidence "OFFICIAL_BOT_COMBAT_MODE mode=$Mode active_from_index=$ActiveFromIndex players=$($PlayerNames.Count)"
}

function Get-OfficialPermanentTentacleCount {
  param([Parameter(Mandatory = $true)][int]$PlayerCount)
  $safePlayers = [Math]::Max(0, [Math]::Min(20, $PlayerCount))
  if ($safePlayers -eq 0) { return 0 }
  if ($safePlayers -le 2) { return 2 }
  if ($safePlayers -le 4) { return 3 }
  if ($safePlayers -le 7) { return 4 }
  if ($safePlayers -le 10) { return 5 }
  if ($safePlayers -le 15) { return 6 }
  return 8
}

function Wait-LocalPlayers {
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if (@($PlayerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Official local player bots did not join: $($PlayerNames -join ', ')"
}

function Wait-V2TransitionToWave {
  param(
    [Parameter(Mandatory = $true)][int]$CompletedWave,
    [Parameter(Mandatory = $true)][int]$NextWave
  )
  Wait-LogRegex -Pattern ("V2_TRANSITION_RUNES_READY.*completed_wave=" + $CompletedWave) `
    -WaitSeconds 45
  # Get-PadCoordinates returns the coordinate arrays as its pipeline output.
  # Do not wrap that already-shaped result in @(...): PowerShell would treat
  # the whole nested array as one pad and report a false one-player layout.
  $nextPads = Get-PadCoordinates
  if ($nextPads.Count -ne $PlayerNames.Count) {
    throw "V2 transition did not persist one rune per official player: completed=$CompletedWave pads=$($nextPads.Count) players=$($PlayerNames.Count)"
  }
  Keep-PlayersAtPads -Pads $nextPads
  $waveStartPattern = if ($isV3Flow) {
    'V3_WAVE_STARTED.*wave=' + $NextWave
  } else {
    'V2_WAVE_STARTED.*wave=' + $NextWave
  }
  Wait-LogRegex -Pattern $waveStartPattern -WaitSeconds 120 `
    -DuringWait { Keep-PlayersAtPads -Pads $nextPads }
  Write-Evidence "OFFICIAL_V2_TRANSITION_PASS event=$eventId completed_wave=$CompletedWave next_wave=$NextWave pads=$($nextPads.Count) hold_ms=5000"
  return ,$nextPads
}

function Wait-EventWaveStarted {
  param([Parameter(Mandatory = $true)][int]$Wave, [int]$WaitSeconds = 120)
  $pattern = if ($isV3Flow) {
    'V3_WAVE_STARTED.*wave=' + $Wave
  } else {
    'V2_WAVE_STARTED.*wave=' + $Wave
  }
  Wait-LogRegex -Pattern $pattern -WaitSeconds $WaitSeconds
}

function Wait-EventWaveCompleted {
  param([Parameter(Mandatory = $true)][int]$Wave, [int]$WaitSeconds = 240,
        [scriptblock]$DuringWait = $null)
  $pattern = if ($isV3Flow) {
    'V3_WAVE_COMPLETED.*wave=' + $Wave
  } else {
    'V2_WAVE_COMPLETED.*wave=' + $Wave
  }
  Wait-LogRegex -Pattern $pattern -WaitSeconds $WaitSeconds -DuringWait $DuringWait
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
    $offset = 0
    while ($offset -lt $length) {
      $read = $stream.Read($buffer, $offset, $length - $offset)
      if ($read -le 0) {
        break
      }
      $offset += $read
    }
    $script:LogOffset = $stream.Position
    return [Text.Encoding]::UTF8.GetString($buffer, 0, $offset)
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
  while ($true) {
    $chunk = Read-NewPaperLog
    if (-not [string]::IsNullOrEmpty($chunk)) {
      [void]$script:LogEvidence.Append($chunk)
    }
    $evidence = $script:LogEvidence.ToString()
    if ([Regex]::IsMatch($evidence, $Pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
      Write-Evidence "OFFICIAL_LOG_PASS pattern=$Pattern"
      return
    }
    if ((Get-Date) -ge $deadline) {
      break
    }
    if ($null -ne $DuringWait -and (Get-Date) -ge $nextAction) {
      & $DuringWait
      # DuringWait can issue several sequential RCON commands.  The server
      # may write the awaited marker while that action is still running.  Read
      # and test once more before checking the deadline, otherwise a slow
      # multi-player maintenance action can turn a real pass into a timeout.
      $chunk = Read-NewPaperLog
      if (-not [string]::IsNullOrEmpty($chunk)) {
        [void]$script:LogEvidence.Append($chunk)
      }
      $evidence = $script:LogEvidence.ToString()
      if ([Regex]::IsMatch($evidence, $Pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
        Write-Evidence "OFFICIAL_LOG_PASS pattern=$Pattern"
        return
      }
      if ((Get-Date) -ge $deadline) {
        break
      }
      $nextAction = (Get-Date).AddSeconds(1.5D)
    }
    Start-Sleep -Milliseconds 500
  }

  # Make the final read explicit as well.  A log write can race the final
  # sleep even when no DuringWait action is configured.
  $chunk = Read-NewPaperLog
  if (-not [string]::IsNullOrEmpty($chunk)) {
    [void]$script:LogEvidence.Append($chunk)
  }
  $evidence = $script:LogEvidence.ToString()
  if ([Regex]::IsMatch($evidence, $Pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
    Write-Evidence "OFFICIAL_LOG_PASS pattern=$Pattern"
    return
  }
  throw "Timed out waiting for local Paper log pattern '$Pattern'."
}

function Wait-LocalPhase {
  param(
    [Parameter(Mandatory = $true)][string]$PhasePattern,
    [Parameter(Mandatory = $true)][int]$WaitSeconds
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $debug = Invoke-LocalRcon -CommandText 'cmend debug ai'
    if ([Regex]::IsMatch($debug, 'phase=(' + $PhasePattern + ')',
        [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
      Write-Evidence "OFFICIAL_PHASE_PASS phase_pattern=$PhasePattern"
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for local End Rift phase '$PhasePattern'."
}

function Prepare-OfficialAuthMeAccounts {
  foreach ($name in $PlayerNames) {
    # Register the disposable local accounts from the console before the
    # protocol clients connect.  AuthMe's async /register executor is not
    # safe when several fresh offline clients send that command in the same
    # tick; one of them can remain in the login screen and be kicked even
    # though the server-side player list already contains its name.
    $unregisterResponse = Invoke-LocalRcon -CommandText ("authme unregister $name")
    if ($unregisterResponse -notmatch "(?i)This user isn't registered!") {
      Wait-LogRegex -Pattern ("AuthMe\].*" + [Regex]::Escape($name) + " was unregistered by Rcon") -WaitSeconds 20
    }
    $null = Invoke-LocalRcon -CommandText ("authme register $name endrift-local")
    Wait-LogRegex -Pattern ("AuthMe\].*Rcon registered " + [Regex]::Escape($name)) -WaitSeconds 20
  }
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
  $debugPlain = $debug -replace '\u00A7.', ''
  if ($debugPlain -notmatch ('stage=' + [Regex]::Escape($Stage))) {
    # A five-player party can cross two health thresholds between the log
    # poll and this RCON request. The V2 transition log is authoritative;
    # retain the live AI snapshot as evidence of the newer stage rather than
    # failing a correct, fast transition.
    $logEvidence = $script:LogEvidence.ToString()
    $transitionPattern = 'BOSS_V2_STAGE_TRANSITION.*to=' + [Regex]::Escape($Stage)
    if ($logEvidence -notmatch $transitionPattern) {
      throw "Official boss stage $Stage was not exposed by AI diagnostics or the transition log:`n$debug"
    }
    Write-Evidence "OFFICIAL_BOSS_STAGE_FAST_TRANSITION stage=$Stage boss=$BossUuid current=$debug"
    return
  }
  Write-Evidence "OFFICIAL_BOSS_STAGE stage=$Stage boss=$BossUuid $debug"
}

$processes = @()
$pads = $null
$core = $null
$bossUuid = $null
$v3ObeliskTargets = @()
$reflectionEnvConfigured = $false
$oldReflectEnabled = $null
$oldReflectStartMs = $null
$oldObeliskTargets = $null
$oldBotControlDirectory = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_CONTROL_DIRECTORY', 'Process')
$oldGuardianProbeNames = [Environment]::GetEnvironmentVariable('END_RIFT_GUARDIAN_PROBE_NAMES', 'Process')
$guardianProbeEnvConfigured = $false
$runeCount = $PlayerNames.Count
$setupCommand = if ($runeCount -eq 5) {
  'cmend core setat 8 68 -39 5'
} else {
  "cmend core setat 8 68 -39 $runeCount"
}
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  New-Item -ItemType Directory -Path $botControlDirectory -Force | Out-Null
  Get-ChildItem -LiteralPath $botControlDirectory -Filter '*.mode' -File -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
  foreach ($name in $PlayerNames) {
    Set-Content -LiteralPath (Join-Path $botControlDirectory ($name + '.mode')) `
      -Value 'ACTIVE' -NoNewline -Encoding ASCII
  }
  $env:END_RIFT_BOT_CONTROL_DIRECTORY = $botControlDirectory
  # The first N clients are placed beside permanent guardians and attack only
  # the real Interaction hitboxes. Remaining clients stay eligible to attack
  # the boss, so large runs observe both the shield block and the guardian
  # damage window instead of pausing every client in LAST_SEAL.
  $guardianProbeNames = @($PlayerNames | Select-Object -First (Get-OfficialPermanentTentacleCount -PlayerCount $PlayerNames.Count))
  $env:END_RIFT_GUARDIAN_PROBE_NAMES = ($guardianProbeNames -join ',')
  $guardianProbeEnvConfigured = $true
  [IO.File]::WriteAllText($evidencePath, "$runLabel $(Get-Date -Format o) players=$($PlayerNames.Count)", [Text.UTF8Encoding]::new($false))

  # This is a local event-session reset only.  It restores the saved vanilla
  # block data and event-owned entities; it never wipes the world, players or
  # the isolated PostgreSQL data directory.
  $null = Invoke-LocalRcon -CommandText 'cmend core remove confirm'
  Start-Sleep -Milliseconds 500
  foreach ($mobType in @('spider', 'enderman', 'skeleton')) {
    # The local flat test scene may contain naturally spawned mobs from an
    # earlier manual session.  Remove only those three types inside the
    # bounded arena footprint after event-owned entities were cleaned; no
    # world, player data or PostgreSQL reset is involved.
    $null = Invoke-LocalRcon -CommandText ("kill @e[type=$mobType,x=-12,y=65,z=-59,dx=40,dy=6,dz=40]")
  }
  $null = Invoke-LocalRcon -CommandText $setupCommand
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
  if ($isV3Flow) {
    $v3ObeliskTargets = Get-V3ObeliskTargets -CoreX $coreX -CoreY $coreY -CoreZ $coreZ `
      -Count (Get-V3ObeliskCount -PlayerCount $PlayerNames.Count)
    # The optional reflection path is inherited only by these local probe
    # processes.  It is never written to server configuration or production
    # environment state.
    $oldReflectEnabled = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_ENABLED', 'Process')
    $oldReflectStartMs = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_START_MS', 'Process')
    $oldObeliskTargets = [Environment]::GetEnvironmentVariable('END_RIFT_OBELISK_TARGETS', 'Process')
    $env:END_RIFT_REFLECT_ENABLED = '1'
    $env:END_RIFT_REFLECT_START_MS = '0'
    $env:END_RIFT_OBELISK_TARGETS = ($v3ObeliskTargets | ConvertTo-Json -Compress)
    $reflectionEnvConfigured = $true
  }
  $pads = Get-PadCoordinates
  # RCON returns one string per status line.  Normalize the complete response
  # before matching.  Use Regex.IsMatch explicitly: PowerShell's -notmatch
  # can still become an array operation when a provider returns multiple
  # records, which would make unrelated lines look like a failed assertion.
  $statusText = ($status -join [Environment]::NewLine) -replace '\u00A7.', ''
  $hasEmptyPads = [Regex]::IsMatch($statusText, "pads=0/$runeCount")
  $padStatusLine = (($statusText -split "`r?`n") | Where-Object { $_ -match 'pads=' } | Select-Object -First 1)
  Write-Evidence "OFFICIAL_SETUP_STATUS_CHECK pads_type=$($pads.GetType().FullName) pads_count=$($pads.Count) rune_count=$runeCount has_empty_pads=$hasEmptyPads status_length=$($statusText.Length) pad_line=<$padStatusLine>"
  if (($pads.Count -ne $runeCount) -or (-not $hasEmptyPads)) {
    throw "Local Core did not expose one rune per player: expected=$runeCount pads=$($pads.Count)`n$status"
  }
  Write-Evidence "OFFICIAL_LOCAL_SETUP_PASS event=$eventId core=$($core -join ',') pads=$($pads.Count)"

  Prepare-OfficialAuthMeAccounts
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  foreach ($name in $PlayerNames) {
    $botLog = Join-Path $botLogDirectory ($name + '.log')
    $botErr = Join-Path $botLogDirectory ($name + '.err.log')
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
      (Format-Coordinate ($coreX + 0.5D)) + ' ' + (Format-Coordinate $coreY) + ' ' +
      (Format-Coordinate ($coreZ + 0.5D)) + ' ' + (Format-Coordinate 20.0D) + ' 900'
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botLog -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
  }
  Wait-LocalPlayers
  foreach ($name in $PlayerNames) {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("clear $name")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.max_health base set 1000")
    # Essentials/custom command handling defaults an omitted give amount to a
    # full stack.  A sword stack fills every inventory slot and makes the
    # later reward pickup probe report a false gameplay failure.
    $null = Invoke-LocalRcon -CommandText ("give $name minecraft:netherite_sword 1")
    $null = Invoke-LocalRcon -CommandText ("enchant $name minecraft:sharpness 5")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:regeneration 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:strength 1000 20 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:speed 1000 2 true")
  }

  Keep-PlayersAtPads -Pads $pads
  Wait-LogRegex -Pattern 'RITUAL_STARTED' -WaitSeconds 20 -DuringWait { Keep-PlayersAtPads -Pads $pads }
  Wait-LogRegex -Pattern 'RITUAL_COMPLETED' -WaitSeconds 80 -DuringWait { Keep-PlayersAtPads -Pads $pads }

  Wait-EventWaveStarted -Wave 1 -WaitSeconds 20
  Wait-LogRegex -Pattern 'V2_WAVE_GROUP_SPAWN.*wave=1.*group=1/\d+.*spawned=\d+' -WaitSeconds 30
  Keep-PlayersAtWave1Carrier -Core $core
  Wait-EventWaveCompleted -Wave 1 -WaitSeconds 240 -DuringWait { Keep-PlayersAtWave1Carrier -Core $core }
  if ($RewardPickupProbe) {
    Wait-LogRegex -Pattern 'WAVE_REWARD_SPAWNED.*wave=1' -WaitSeconds 20
    Write-Evidence "WAVE_REWARD_PICKUP_PROBE event=$eventId wave=1 recipients=$($PlayerNames.Count) mode=one-player-at-a-time"
    # Stop the survival clients' attack loop before measuring pickup.  The
    # reward is a physical Item entity; leaving wave combat active would let
    # mobs knock the probe away while the ownership check is being exercised.
    foreach ($name in $PlayerNames) {
      $null = Invoke-LocalRcon -CommandText ("tellraw $name " + '{"text":"END_RIFT_PASSIVE"}')
      $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.knockback_resistance base set 1")
    }
    foreach ($name in $PlayerNames) {
      Assert-WaveRewardPickup -Name $name -Core $core -Wave 1
    }
    if ($StopAfterRewardProbe) {
      Write-Evidence "WAVE_REWARD_PICKUP_PROBE_PASS event=$eventId wave=1 recipients=$($PlayerNames.Count) stop_after_probe=true"
      return
    }
    Keep-PlayersAtCoreRing -Core $core
  }

  $pads = Wait-V2TransitionToWave -CompletedWave 1 -NextWave 2
  Wait-LogRegex -Pattern 'V2_WAVE_OBJECTIVE_STARTED.*wave=2.*HUNT_MARK' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-LogRegex -Pattern 'V2_HUNT_CYCLE.*cycle=1' -WaitSeconds 45 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-EventWaveCompleted -Wave 2 -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  $pads = Wait-V2TransitionToWave -CompletedWave 2 -NextWave 3
  # V2 Wave 3 has exactly three sequential portals for every roster size.
  Wait-LogRegex -Pattern 'V2_PORTALS_READY.*count=3.*sequential=true' -WaitSeconds 20
  $portalCount = 3
  $portalY = $coreY
  $portals = @()
  for ($index = 0; $index -lt $portalCount; $index++) {
    # Keep this probe in lockstep with v2PrepareSequentialPortals: the first
    # portal is north of the Core (negative Z), not on the positive-X axis.
    $angle = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $index) / $portalCount
    $portals += [pscustomobject]@{
      X = $coreX + 0.5D + 8.0D * [Math]::Cos($angle)
      Y = $portalY
      Z = $coreZ + 0.5D + 8.0D * [Math]::Sin($angle)
    }
  }
  foreach ($portal in $portals) {
    # Capture a scalar record for the scriptblock instead of relying on the
    # foreach variable's late-bound value while the wait loop runs.
    $targetPortal = [pscustomobject]@{
      X = [double]$portal.X
      Y = [double]$portal.Y
      Z = [double]$portal.Z
    }
    Wait-SecondsWithAction -Seconds 7 -Action {
      Keep-PlayersAtPoint -Point $targetPortal
    }
  }
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=3' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCoreRing -Core $core }
  # After the portal objective completes, one defender can still be outside
  # the inner ring.  Keep the bots on the authoritative mob positions while
  # waiting for the real Wave 3 completion marker; this exercises the same
  # portal-mob damage path without making the test depend on a lucky spawn.
  Wait-EventWaveCompleted -Wave 3 -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatMobs -Core $core }
  if ($StopAfterWave3) {
    Write-Evidence "OFFICIAL_WAVE3_PASS event=$eventId portals=$portalCount stop_after_wave3=true"
    return
  }

  $pads = Wait-V2TransitionToWave -CompletedWave 3 -NextWave 4
  if ($isV3Flow) {
    $expectedV3Obelisks = Get-V3ObeliskCount -PlayerCount $PlayerNames.Count
    Wait-LogRegex -Pattern ('V3_WAVE_STARTED.*wave=4.*objective=OBELISK_ASSAULT') `
      -WaitSeconds 120 -DuringWait { Keep-PlayersAtV3ObeliskTargets -Targets $v3ObeliskTargets }
    Wait-LogRegex -Pattern ('V3_OBELISK_ASSAULT_READY.*obelisks=' + $expectedV3Obelisks + '.*real_blocks=true') `
      -WaitSeconds 30 -DuringWait { Keep-PlayersAtV3ObeliskTargets -Targets $v3ObeliskTargets }

    $activeDeadline = (Get-Date).AddSeconds(45)
    $activeCount = 0
    while ((Get-Date) -lt $activeDeadline) {
      $chunk = Read-NewPaperLog
      if (-not [string]::IsNullOrEmpty($chunk)) {
        [void]$script:LogEvidence.Append($chunk)
      }
      $activeCount = [Regex]::Matches(
        $script:LogEvidence.ToString(),
        'V3_OBELISK_ACTIVE event=' + [Regex]::Escape($eventId) + '\b').Count
      if ($activeCount -ge $expectedV3Obelisks) {
        break
      }
      Keep-PlayersAtV3ObeliskTargets -Targets $v3ObeliskTargets
      Start-Sleep -Milliseconds 250
    }
    if ($activeCount -ne $expectedV3Obelisks) {
      throw "V3 Wave 4 did not activate exactly $expectedV3Obelisks real obelisks: active=$activeCount"
    }
    Assert-V3ObeliskBaseBlocks -Targets $v3ObeliskTargets -Material 'polished_blackstone' -Expected $expectedV3Obelisks
    Write-Evidence "OFFICIAL_V3_OBELISK_ACTIVE_PASS event=$eventId obelisks=$activeCount real_blocks=true"

    Wait-LogRegex -Pattern ('V3_RIFT_FIREBALL_LAUNCH.*event=' + [Regex]::Escape($eventId)) `
      -WaitSeconds 180 -DuringWait { Keep-PlayersAtV3ObeliskTargets -Targets $v3ObeliskTargets }
    $hitPattern = 'V3_OBELISK_REFLECTED_HIT event=' + [Regex]::Escape($eventId) +
      ' obelisk=([0-9a-fA-F-]{36}) fireball=([0-9a-fA-F-]{36}).*consumed=true'
    $hitDeadline = (Get-Date).AddSeconds(420)
    $hitCount = 0
    $hitObeliskCount = 0
    $hitFireballCount = 0
    while ((Get-Date) -lt $hitDeadline) {
      $chunk = Read-NewPaperLog
      if (-not [string]::IsNullOrEmpty($chunk)) {
        [void]$script:LogEvidence.Append($chunk)
      }
      $hitMatches = [Regex]::Matches($script:LogEvidence.ToString(), $hitPattern)
      $hitCount = $hitMatches.Count
      $hitObeliskCount = @($hitMatches | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique).Count
      $hitFireballCount = @($hitMatches | ForEach-Object { $_.Groups[2].Value } | Sort-Object -Unique).Count
      if (($hitCount -ge ($expectedV3Obelisks * 3)) -and $hitObeliskCount -eq $expectedV3Obelisks -and $hitFireballCount -eq $hitCount) {
        break
      }
      Keep-PlayersAtV3ObeliskTargets -Targets $v3ObeliskTargets
      Start-Sleep -Milliseconds 250
    }
    $doubleReflectRejects = [Regex]::Matches(
      $script:LogEvidence.ToString(),
      'RIFT_FIREBALL_REFLECT_REJECTED event=' + [Regex]::Escape($eventId) +
      '.*reason=already-reflected').Count
    Write-Evidence "OFFICIAL_V3_OBELISK_REFLECTION_PASS event=$eventId hits=$hitCount expected=$($expectedV3Obelisks * 3) distinct_obelisks=$hitObeliskCount distinct_fireballs=$hitFireballCount duplicate_reflect_rejects=$doubleReflectRejects"
    if (($hitCount -lt ($expectedV3Obelisks * 3)) -or $hitObeliskCount -ne $expectedV3Obelisks -or $hitFireballCount -ne $hitCount) {
      throw "V3 reflected-fireball coverage failed: hits=$hitCount expected=$($expectedV3Obelisks * 3) obelisks=$hitObeliskCount fireballs=$hitFireballCount"
    }
    Wait-EventWaveCompleted -Wave 4 -WaitSeconds 300 -DuringWait { Keep-PlayersAtCombatMobs -Core $core }
    # The temporary structure is journaled one block above the solid floor;
    # the current arena map intentionally has air in that cell.  Verify the
    # exact restored base rather than assuming a vanilla floor material.
    Assert-V3ObeliskBaseBlocks -Targets $v3ObeliskTargets -Material 'air' -Expected $expectedV3Obelisks
    Write-Evidence "OFFICIAL_V3_WAVE4_PASS event=$eventId obelisks=$expectedV3Obelisks reflected_hits=$hitCount real_blocks_restored=true"
    if ($StopAfterWave4) {
      Write-Evidence "OFFICIAL_WAVE4_PASS event=$eventId stop_after_wave4=true"
      return
    }
    # Wave 4 is followed by the same persisted transition-rune handoff as the
    # earlier V3 waves.  Waiting directly for Wave 5 leaves the live driver
    # parked in INTERMISSION_4 with both clients off the runes.
    $pads = Wait-V2TransitionToWave -CompletedWave 4 -NextWave 5
  }

  if ($isV3Flow) {
    # V3 keeps the proven fog/ring/chamber adapters, but the public wave
    # numbers and transitions are different.  Keep this branch explicit so a
    # schema-3 run can never fall through into the retired V2 wave numbering.
    Wait-LogRegex -Pattern 'V3_WAVE_STARTED.*wave=5.*objective=BLACK_FOG' -WaitSeconds 120 `
      -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    Wait-LogRegex -Pattern 'V3_WAVE_OBJECTIVE_ADAPTER.*wave=5.*adapter_slot=4.*objective=BLACK_FOG' `
      -WaitSeconds 30
    for ($fogCycle = 1; $fogCycle -le 3; $fogCycle++) {
      Wait-LogRegex -Pattern ("V2_FOG_SAFE_START.*cycle={0}/3" -f $fogCycle) -WaitSeconds 120 `
        -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
      Wait-LogRegex -Pattern ("V2_FOG_START.*cycle={0}/3.*height=3" -f $fogCycle) -WaitSeconds 45 `
        -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    }
    Wait-LogRegex -Pattern 'V2_FOG_COMPLETE.*cycles=3' -WaitSeconds 180 `
      -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    Wait-EventWaveCompleted -Wave 5 -WaitSeconds 300 `
      -DuringWait { Keep-PlayersAtCombatMobs -Core $core }
    Write-Evidence "OFFICIAL_V3_WAVE5_PASS event=$eventId objective=BLACK_FOG cycles=3"

    # Wave 5 is followed by the explicit six-second Core restoration before
    # the outer-edge runes for Wave 6 are created.
    Wait-LocalPhase -PhasePattern 'CORE_RESTORATION|INTERMISSION_5' -WaitSeconds 30
    Wait-LogRegex -Pattern 'V2_TRANSITION_RUNES_READY.*completed_wave=5' -WaitSeconds 60
    $pads = Wait-V2TransitionToWave -CompletedWave 5 -NextWave 6
    Wait-LogRegex -Pattern 'V3_WAVE_STARTED.*wave=6.*objective=COLLAPSE_RINGS' -WaitSeconds 120 `
      -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    Wait-LogRegex -Pattern 'V3_WAVE_OBJECTIVE_ADAPTER.*wave=6.*adapter_slot=5.*objective=COLLAPSE_RINGS' `
      -WaitSeconds 30
    for ($ring = 1; $ring -le 3; $ring++) {
      Wait-LogRegex -Pattern ("V2_RING_COLLAPSED.*ring={0}/3" -f $ring) -WaitSeconds 150 `
        -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    }
    Wait-EventWaveCompleted -Wave 6 -WaitSeconds 360 `
      -DuringWait { Keep-PlayersAtCombatMobs -Core $core }
    Wait-LocalPhase -PhasePattern 'INTERMISSION_6|WAVE_7' -WaitSeconds 30
    Write-Evidence "OFFICIAL_V3_WAVE6_PASS event=$eventId objective=COLLAPSE_RINGS rings=3"

    $pads = Wait-V2TransitionToWave -CompletedWave 6 -NextWave 7
    Wait-LogRegex -Pattern 'V3_WAVE_STARTED.*wave=7.*objective=REALITY_SPLIT' -WaitSeconds 120 `
      -DuringWait { Keep-PlayersAtPads -Pads $pads }
    Wait-LogRegex -Pattern 'V3_WAVE_OBJECTIVE_ADAPTER.*wave=7.*adapter_slot=6.*objective=REALITY_SPLIT' `
      -WaitSeconds 30
    Wait-LogRegex -Pattern ("V2_CHAMBERS_ASSIGNED.*chambers={0}" -f (Get-WaveSixChamberCount)) `
      -WaitSeconds 60 -DuringWait { Keep-PlayersAtWaveSixMobs -Core $core }
    $expectedChambers = Get-WaveSixChamberCount
    Wait-LogRegex -Pattern ("V2_CHAMBERS_COMPLETE.*chambers={0}" -f $expectedChambers) -WaitSeconds 420 `
      -DuringWait { Keep-PlayersAtWaveSixMobs -Core $core }
    Wait-EventWaveCompleted -Wave 7 -WaitSeconds 360
    $wave7Status = Invoke-LocalRcon -CommandText 'cmend debug ai'
    if ($wave7Status -notmatch 'phase=.*PRE_BOSS_COOLDOWN') {
      throw "V3 Wave 7 did not enter PRE_BOSS_COOLDOWN after all chambers completed:`n$wave7Status"
    }
    Write-Evidence "OFFICIAL_V3_WAVE7_PASS event=$eventId objective=REALITY_SPLIT chambers=$expectedChambers passage_open=true"
  } else {
    Wait-LogRegex -Pattern 'V2_WAVE_OBJECTIVE_STARTED.*wave=4.*BLACK_FOG' -WaitSeconds 20
    Wait-LogRegex -Pattern 'V2_FOG_SAFE_START.*cycle=1/3' -WaitSeconds 45 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    Wait-LogRegex -Pattern 'V2_FOG_START.*cycle=1/3.*height=3' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    Wait-LogRegex -Pattern 'V2_FOG_COMPLETE.*cycles=3' -WaitSeconds 180 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatMobs -Core $core }

    $pads = Wait-V2TransitionToWave -CompletedWave 4 -NextWave 5
    Wait-LogRegex -Pattern 'V2_WAVE_OBJECTIVE_STARTED.*wave=5.*COLLAPSE_RINGS' -WaitSeconds 20
    Wait-LogRegex -Pattern 'V2_RING_COLLAPSED.*ring=1/3' -WaitSeconds 90 -DuringWait { Keep-PlayersAtCoreRing -Core $core }
    Wait-LogRegex -Pattern 'V2_RING_COLLAPSED.*ring=3/3' -WaitSeconds 90 -DuringWait { Keep-PlayersAtCoreRing -Core $core }
    Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=5' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

    $pads = Wait-V2TransitionToWave -CompletedWave 5 -NextWave 6
    # Wave 6 is chamber-isolated.  Keep each client near the authoritative mob
    # positions so its own chamber can clear without ever opening a cross-room
    # target path.  The controller itself remains responsible for assigning and
    # validating the chamber; the probe only supplies player movement.
    $expectedChambers = Get-WaveSixChamberCount
    Wait-LogRegex -Pattern ("V2_CHAMBERS_COMPLETE.*chambers={0}" -f $expectedChambers) -WaitSeconds 360 `
      -DuringWait { Keep-PlayersAtWaveSixMobs -Core $core }
    Wait-LogRegex -Pattern 'V2_WAVE_COMPLETED.*wave=6.*CHAMBERS' -WaitSeconds 30
    $waveSixStatus = Invoke-LocalRcon -CommandText 'cmend debug ai'
    if ($waveSixStatus -notmatch 'phase=.*PRE_BOSS_COOLDOWN|phase=.*WAVE_6') {
      throw "Wave 6 did not leave the isolated chamber flow in a V2 phase:`n$waveSixStatus"
    }
    Write-Evidence "OFFICIAL_WAVE6_CHAMBER_PASS event=$eventId isolated=true chambers=$expectedChambers passage_open=true"
  }

  Wait-LogRegex -Pattern 'BOSS_CINEMATIC_STARTED' -WaitSeconds 30
  Wait-LogRegex -Pattern 'BOSS_SPAWNED' -WaitSeconds 120
  $bossUuid = Get-BossUuid (Invoke-LocalRcon -CommandText 'cmend status')
  Assert-BossStage -Stage 'AWAKENING' -BossUuid $bossUuid

  Wait-LogRegex -Pattern 'BOSS_V2_STAGE_TRANSITION.*to=HUNT' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'HUNT' -BossUuid $bossUuid
  $preLastSealTentacleCount = [Regex]::Matches(
    $script:LogEvidence.ToString(), 'RIFT_TENTACLE_SPAWN .*temporary=false').Count
  if ($preLastSealTentacleCount -ne 0) {
    throw "Permanent guardian tentacles appeared before LAST_SEAL: count=$preLastSealTentacleCount"
  }
  Write-Evidence "OFFICIAL_TENTACLE_PRE_LAST_SEAL_PASS event=$eventId permanent_tentacles=0"
  Wait-LogRegex -Pattern 'BOSS_V2_STAGE_TRANSITION.*to=RIFT' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'RIFT' -BossUuid $bossUuid
  if ($isV3Flow) {
    Wait-LogRegex -Pattern 'V3_RIFT_FRACTURES_STARTED.*phase=RIFT' -WaitSeconds 30 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
    Wait-LogRegex -Pattern 'V3_RIFT_FRACTURE_ACTIVE.*phase=RIFT' -WaitSeconds 30 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
    if ($script:LogEvidence.ToString() -match 'RIFT_OBELISKS_SPAWNED.*stage=RIFT') {
      throw 'Official V3 RIFT stage unexpectedly started legacy boss obelisks.'
    }
    Write-Evidence "OFFICIAL_V3_RIFT_FRACTURE_PASS event=$eventId legacy_boss_obelisks=false"
  } else {
    Wait-LogRegex -Pattern 'RIFT_OBELISKS_SPAWNED.*stage=RIFT' -WaitSeconds 30 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  }
  Wait-LogRegex -Pattern 'BOSS_V2_STAGE_TRANSITION.*to=OVERLOAD' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'OVERLOAD' -BossUuid $bossUuid
  Wait-LogRegex -Pattern 'BOSS_V2_STAGE_TRANSITION.*to=RAGE' -WaitSeconds 180 -DuringWait { Keep-PlayersNearBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'RAGE' -BossUuid $bossUuid
  if ($PlayerNames.Count -ge 8) {
    # The local protocol clients use a high-damage test sword. Let one real
    # client cross the RAGE -> LAST_SEAL threshold while the others observe;
    # this preserves the final guardian window instead of killing the boss in
    # the same burst as the threshold transition.
    Set-OfficialBotCombatMode -Mode PASSIVE -ActiveFromIndex 1
  }
  Wait-LogRegex -Pattern 'BOSS_V2_STAGE_TRANSITION.*to=LAST_SEAL' -WaitSeconds 180 -DuringWait { Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid }
  Assert-BossStage -Stage 'LAST_SEAL' -BossUuid $bossUuid
  if ($PlayerNames.Count -ge 8) {
    # Once LAST_SEAL is active, guardian probes and the remaining boss probes
    # must run together. The guardian shield blocks the latter until every
    # permanent Interaction guardian is defeated, then the normal boss damage
    # path resumes.
    Set-OfficialBotCombatMode -Mode ACTIVE -ActiveFromIndex 0
  }
  Wait-LogRegex -Pattern 'BOSS_V2_LAST_SEAL_VISUALS_STARTED' -WaitSeconds 30 -DuringWait { Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid }
  $expectedPermanentTentacles = Get-OfficialPermanentTentacleCount -PlayerCount $PlayerNames.Count
  $tentacleDeadline = (Get-Date).AddSeconds(30)
  $tentacleCount = 0
  while ((Get-Date) -lt $tentacleDeadline) {
    $tentacleChunk = Read-NewPaperLog
    if (-not [string]::IsNullOrEmpty($tentacleChunk)) {
      [void]$script:LogEvidence.Append($tentacleChunk)
    }
    $tentacleCount = [Regex]::Matches(
      $script:LogEvidence.ToString(), 'RIFT_TENTACLE_SPAWN .*temporary=false').Count
    if ($tentacleCount -ge $expectedPermanentTentacles) {
      break
    }
    Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid
    Start-Sleep -Milliseconds 250
  }
  if ($tentacleCount -ne $expectedPermanentTentacles) {
    throw "LAST_SEAL did not materialize exactly $expectedPermanentTentacles permanent guardian tentacles: count=$tentacleCount"
  }
  Write-Evidence "OFFICIAL_TENTACLE_LAST_SEAL_PASS event=$eventId permanent_tentacles=$tentacleCount temporary_cap=6"
  Wait-LogRegex -Pattern 'BOSS_V2_DAMAGE_BLOCKED.*reason=permanent-guardian-shield' `
    -WaitSeconds 30 -DuringWait { Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid }
  Wait-LogRegex -Pattern 'RIFT_TENTACLE_DAMAGE.*health_after=' `
    -WaitSeconds 90 -DuringWait { Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid }
  Wait-LogRegex -Pattern 'RIFT_GUARDIAN_SHIELD_BROKEN' `
    -WaitSeconds 120 -DuringWait { Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid }
  Write-Evidence "OFFICIAL_TENTACLE_COMBAT_PASS event=$eventId hitbox_damage=true boss_shield_observed=true damage_window=true"
  Wait-LogRegex -Pattern 'BOSS_DEFEAT_COMMITTED' -WaitSeconds 240 -DuringWait { Keep-PlayersAtGuardiansAndBoss -Uuid $bossUuid }
  Wait-LogRegex -Pattern 'BOSS_DEFEATED' -WaitSeconds 30
  Wait-LogRegex -Pattern 'END_EVENT_WAVE_COMBAT_CLEANUP.*reason=official-boss-defeat' -WaitSeconds 30
  Wait-LogRegex -Pattern 'VICTORY' -WaitSeconds 60
  $finalStatus = Invoke-LocalRcon -CommandText 'cmend status'
  if ($finalStatus -notmatch 'boss=.*none' -or $finalStatus -notmatch 'event-mobs=.*0') {
    throw "Official victory left event entities behind:`n$finalStatus"
  }
  $waveList = if ($isV3Flow) { '1,2,3,4,5,6,7' } else { '1,2,3,4,5,6' }
  Write-Evidence "$passLabel event=$eventId boss=$bossUuid players=$($PlayerNames.Count) waves=$waveList stages=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL victory=true"
  Write-Evidence $finalStatus
} finally {
  if ($null -eq $oldBotControlDirectory) { Remove-Item Env:END_RIFT_BOT_CONTROL_DIRECTORY -ErrorAction SilentlyContinue }
  else { $env:END_RIFT_BOT_CONTROL_DIRECTORY = $oldBotControlDirectory }
  if ($guardianProbeEnvConfigured) {
    if ($null -eq $oldGuardianProbeNames) { Remove-Item Env:END_RIFT_GUARDIAN_PROBE_NAMES -ErrorAction SilentlyContinue }
    else { $env:END_RIFT_GUARDIAN_PROBE_NAMES = $oldGuardianProbeNames }
  }
  if ($reflectionEnvConfigured) {
    if ($null -eq $oldReflectEnabled) { Remove-Item Env:END_RIFT_REFLECT_ENABLED -ErrorAction SilentlyContinue }
    else { $env:END_RIFT_REFLECT_ENABLED = $oldReflectEnabled }
    if ($null -eq $oldReflectStartMs) { Remove-Item Env:END_RIFT_REFLECT_START_MS -ErrorAction SilentlyContinue }
    else { $env:END_RIFT_REFLECT_START_MS = $oldReflectStartMs }
    if ($null -eq $oldObeliskTargets) { Remove-Item Env:END_RIFT_OBELISK_TARGETS -ErrorAction SilentlyContinue }
    else { $env:END_RIFT_OBELISK_TARGETS = $oldObeliskTargets }
  }
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
  if ($RewardPickupProbe) {
    foreach ($name in $PlayerNames) {
      try { Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.knockback_resistance base set 0") | Out-Null } catch { }
    }
  }
  # Restore a clean, configured local scene for the next run.  The same
  # command removes old event mobs/bosses and restores the saved block data;
  # no world or player-data wipe is used.
  try { Invoke-LocalRcon -CommandText 'cmend core remove confirm' | Out-Null } catch { }
  try {
    Invoke-LocalRcon -CommandText $setupCommand | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add DIAMOND 100' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add ENDER_EYE 64' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add AMETHYST_SHARD 128' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add BLAZE_ROD 64' | Out-Null
  } catch {
    Write-Warning "Local scene restore failed: $($_.Exception.Message)"
  }
}
