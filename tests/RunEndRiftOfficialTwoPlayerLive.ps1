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
  [switch]$TowerFailureProbe
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
if ($PlayerNames.Count -lt 2 -or $PlayerNames.Count -gt 5) {
  throw "Official End Rift driver supports two to five local players; received $($PlayerNames.Count)."
}
if (@($PlayerNames | Select-Object -Unique).Count -ne $PlayerNames.Count) {
  throw 'Official End Rift driver requires unique local player names.'
}
$runLabel = if ($PlayerNames.Count -eq 5) { 'OFFICIAL_FIVE_PLAYER_START' } else { 'OFFICIAL_TWO_PLAYER_START' }
$passLabel = if ($PlayerNames.Count -eq 5) { 'OFFICIAL_FIVE_PLAYER_PASS' } else { 'OFFICIAL_TWO_PLAYER_PASS' }
if ($PlayerNames.Count -eq 5) {
  $evidencePath = Join-Path $runtimeRoot 'official-five-player-live.log'
  $botLogDirectory = Join-Path $runtimeRoot 'official-five-player-bots'
}

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
  return ,$positions
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
  $debugPlain = $debug -replace '\u00A7.', ''
  if ($debugPlain -notmatch ('stage=' + [Regex]::Escape($Stage))) {
    # A five-player party can cross two health thresholds between the log
    # poll and this RCON request.  The transition log is the authoritative
    # event assertion; retain the live AI snapshot as evidence of the newer
    # stage rather than failing a correct, fast transition.
    $logEvidence = $script:LogEvidence.ToString()
    $transitionPattern = 'BOSS_STAGE_TRANSITION.*to=' + [Regex]::Escape($Stage)
    $absorptionFastTransition = $false
    if ($Stage -eq 'ABSORPTION') {
      $absorptionFastTransition = $logEvidence -match 'BOSS_STAGE_TRANSITION.*crossed=.*ABSORPTION' `
          -and $logEvidence -match 'BOSS_ABSORPTION_BUFF.*damageable=true' `
          -and ($debugPlain -match 'absorptionCompleted=true')
    }
    if ($logEvidence -notmatch $transitionPattern -and -not $absorptionFastTransition) {
      throw "Official boss stage $Stage was not exposed by AI diagnostics or the transition log:`n$debug"
    }
    if ($absorptionFastTransition) {
      Write-Evidence "OFFICIAL_BOSS_STAGE_FAST_TRANSITION stage=$Stage boss=$BossUuid evidence=threshold-crossed-and-buff current=$debug"
    } else {
      Write-Evidence "OFFICIAL_BOSS_STAGE_FAST_TRANSITION stage=$Stage boss=$BossUuid current=$debug"
    }
    return
  }
  Write-Evidence "OFFICIAL_BOSS_STAGE stage=$Stage boss=$BossUuid $debug"
}

$processes = @()
$pads = $null
$core = $null
$bossUuid = $null
$runeCount = $PlayerNames.Count
$setupCommand = if ($runeCount -eq 5) {
  'cmend core setat 8 68 -39 5'
} else {
  "cmend core setat 8 68 -39 $runeCount"
}
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
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

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  foreach ($name in $PlayerNames) {
    $botLog = Join-Path $botLogDirectory ($name + '.log')
    $botErr = Join-Path $botLogDirectory ($name + '.err.log')
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
      (Format-Coordinate ($coreX + 0.5D)) + ' ' + (Format-Coordinate $coreY) + ' ' +
      (Format-Coordinate ($coreZ + 0.5D)) + ' ' + (Format-Coordinate 20.0D)
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botLog -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
  }
  Wait-LocalPlayers
  foreach ($name in $PlayerNames) {
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

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=2' -WaitSeconds 60
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_MARK.*wave=2' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-LogRegex -Pattern 'WAVE_SKELETON_MARKED_TARGET.*wave=2' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=2' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=3' -WaitSeconds 60
  # Portal count is roster-scaled by the event policy: a five-player run has
  # four portals, while a two-player compatibility run has three.  Read the
  # authoritative count from Paper instead of hardcoding the two-player case.
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_STARTED.*wave=3.*portals=\d+' -WaitSeconds 20
  $portalMatch = [Regex]::Match($script:LogEvidence.ToString(),
    'WAVE_OBJECTIVE_STARTED.*wave=3.*portals=(\d+)')
  if (-not $portalMatch.Success) {
    throw 'Paper did not expose the authoritative wave 3 portal count.'
  }
  $portalCount = [int]$portalMatch.Groups[1].Value
  if ($portalCount -lt 3 -or $portalCount -gt 6) {
    throw "Wave 3 portal count is outside the bounded policy: $portalCount"
  }
  $portalY = $coreY
  $portals = @()
  for ($index = 0; $index -lt $portalCount; $index++) {
    $angle = (2.0D * [Math]::PI * $index) / $portalCount
    $portals += [pscustomobject]@{
      X = $coreX + 0.5D + 8.0D * [Math]::Cos($angle)
      Y = $portalY
      Z = $coreZ + 0.5D + 8.0D * [Math]::Sin($angle)
    }
  }
  foreach ($portal in $portals) {
    Wait-SecondsWithAction -Seconds 7 -Action { Keep-PlayersAtPoint -Point $portal }
  }
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=3' -WaitSeconds 30 -DuringWait { Keep-PlayersAtCoreRing -Core $core }
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=3' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=4' -WaitSeconds 60
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_STARTED.*wave=4.*TOWER_DEFENSE' -WaitSeconds 20
  # Wave IV is scaled by the official roster and split into bounded groups.
  # Read the total from the authoritative first spawn line so a five-player
  # run validates all six groups rather than inheriting the two-player 1/4
  # assumption.
  Wait-LogRegex -Pattern 'WAVE_TOWER_GROUP_SPAWN.*group=1/\d+.*spawned=\d+' -WaitSeconds 20
  $towerGroupMatch = [Regex]::Match($script:LogEvidence.ToString(),
    'WAVE_TOWER_GROUP_SPAWN.*group=1/(\d+).*spawned=\d+')
  if (-not $towerGroupMatch.Success) {
    throw 'Paper did not expose the authoritative Wave 4 group count.'
  }
  $towerGroupCount = [int]$towerGroupMatch.Groups[1].Value
  if ($towerGroupCount -lt 1 -or $towerGroupCount -gt 12) {
    throw "Wave 4 group count is outside the bounded policy: $towerGroupCount"
  }
  if ($TowerFailureProbe) {
    # This remains an official two-player session: only the bounded local
    # probe asks the plugin to apply a synthetic Core-destroying attack.  The
    # plugin then runs its real failure cleanup and owned retry scheduler.
    $null = Invoke-LocalRcon -CommandText 'cmend test tower fail'
    Wait-LogRegex -Pattern 'WAVE_TEST_FAILURE_INJECTED.*wave=4' -WaitSeconds 20
    Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_FAILED.*wave=4.*retry=true' -WaitSeconds 20
    $failedStatus = Invoke-LocalRcon -CommandText 'cmend status'
    if ($failedStatus -notmatch 'wave=.*4' -or $failedStatus -notmatch 'event-mobs=.*0') {
      throw "Wave 4 failure did not synchronously remove the old event mobs:`n$failedStatus"
    }
    Write-Evidence "OFFICIAL_W4_FAILURE_CLEANUP_PASS event=$eventId wave=4 event-mobs=0"
    Wait-LogRegex -Pattern 'WAVE_RETRY_OBJECTIVE_RESET.*wave=4' -WaitSeconds 30
    Wait-LogRegex -Pattern 'WAVE_RETRY_STARTED.*wave=4' -WaitSeconds 30
    $retryStatus = Invoke-LocalRcon -CommandText 'cmend debug objectives'
    if ($retryStatus -notmatch 'wave=4' -or $retryStatus -notmatch 'towerAttempt=2') {
      throw "Wave 4 retry did not start as attempt 2:`n$retryStatus"
    }
    Write-Evidence "OFFICIAL_W4_RETRY_PASS event=$eventId wave=4 attempt=2 old_mobs_cleared=true"
    Wait-LogRegex -Pattern ("WAVE_TOWER_GROUP_SPAWN.*group=2/" + $towerGroupCount + ".*spawned=\d+") `
      -WaitSeconds 45 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  } else {
    for ($group = 2; $group -le $towerGroupCount; $group++) {
      Wait-LogRegex -Pattern ("WAVE_TOWER_GROUP_SPAWN.*group=" + $group + "/" + $towerGroupCount + ".*spawned=\d+") `
        -WaitSeconds 45 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
    }
  }
  Write-Evidence "OFFICIAL_W4_GROUPS_PASS event=$eventId count=$towerGroupCount"
  Wait-LogRegex -Pattern 'WAVE_OBJECTIVE_COMPLETE.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatSweep -Core $core }
  Wait-LogRegex -Pattern 'WAVE_COMPLETED.*wave=4' -WaitSeconds 240 -DuringWait { Keep-PlayersAtCombatMobs -Core $core }
  if ($TowerFailureProbe) {
    Write-Evidence "OFFICIAL_W4_RETRY_COMPLETED_PASS event=$eventId wave=4 attempt=2 objective=success"
    return
  }

  Wait-LogRegex -Pattern 'WAVE_STARTED.*wave=5' -WaitSeconds 60
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
  Write-Evidence "$passLabel event=$eventId boss=$bossUuid players=$($PlayerNames.Count) waves=1,2,3,4,5,final stages=AWAKENING,HUNTER,DISTORTION,ABSORPTION,CATASTROPHE,JUDGMENT victory=true"
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
    Invoke-LocalRcon -CommandText $setupCommand | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add DIAMOND 100' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add ENDER_EYE 64' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add AMETHYST_SHARD 128' | Out-Null
    Invoke-LocalRcon -CommandText 'cmend resources add BLAZE_ROD 64' | Out-Null
  } catch {
    Write-Warning "Local scene restore failed: $($_.Exception.Message)"
  }
}
