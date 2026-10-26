[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftOfficialA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftOfficialB',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string[]]$AdditionalBotNames = @(),
  [ValidateRange(30, 3600)]
  [int]$BotDurationSeconds = 3600,
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
. (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$branchLines = @(& git -C $root branch --show-current 2>$null)
$branchExitCode = $LASTEXITCODE
$branch = if ($branchLines.Count -gt 0) { ([string]$branchLines[0]).Trim() } else { '' }
if ($branchExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($branch) -or
    $branch -notin @('codex/end-rift-event', 'codex/end-rift-26-3-integrated-final', 'codex/copimine-source-of-truth-20261020')) {
  throw "Refused Git branch '$branch'."
}
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$runtimeRoot = Assert-EndRiftLocalRuntimePath -Path $runtimeRoot -LocalRuntimeRoot $runtimeRoot -MustExist
$serverDir = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtimeRoot 'end-rift-server') -LocalRuntimeRoot $runtimeRoot -MustExist
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $serverDir 'logs\latest.log') -LocalRuntimeRoot $runtimeRoot -MustExist
$statePath = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml') `
  -LocalRuntimeRoot $runtimeRoot -MustExist
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$evidencePath = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtimeRoot 'official-current-live.log') -LocalRuntimeRoot $runtimeRoot
$botLogDirectory = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtimeRoot 'official-current-bots') -LocalRuntimeRoot $runtimeRoot
$playerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames)
$processes = @()
$script:temporaryProbeWhitelistNames = [System.Collections.Generic.List[string]]::new()
$script:botModeRequestIds = @{}
$controlDirectory = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $botLogDirectory 'control') -LocalRuntimeRoot $runtimeRoot
$oldReflectEnabled = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_ENABLED', 'Process')
$oldReflectStart = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_START_MS', 'Process')
$oldReflectDiagnostics = [Environment]::GetEnvironmentVariable('END_RIFT_REFLECT_DIAGNOSTICS', 'Process')
$oldObeliskTargets = [Environment]::GetEnvironmentVariable('END_RIFT_OBELISK_TARGETS', 'Process')
$oldControlDirectory = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_CONTROL_DIRECTORY', 'Process')
$oldWave7Chambers = [Environment]::GetEnvironmentVariable('END_RIFT_WAVE7_CHAMBERS', 'Process')
$oldGuardianProbeNames = [Environment]::GetEnvironmentVariable('END_RIFT_GUARDIAN_PROBE_NAMES', 'Process')
$oldAttackInterval = [Environment]::GetEnvironmentVariable('END_RIFT_ATTACK_INTERVAL_MS', 'Process')
  $originalRequiredPlayers = 0
  $originalCore = $null

function Assert-UnderRuntime {
  param([Parameter(Mandatory = $true)][string]$Path)
  return Assert-EndRiftLocalRuntimePath -Path $Path -LocalRuntimeRoot $runtimeRoot
}

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
Assert-EndRiftLocalServerBind -ServerDir $serverDir -ServerPort 25566 -RconPort 25576 | Out-Null

function Get-RitualScalingProfile {
  param([Parameter(Mandatory = $true)][int]$Participants)

  $count = [Math]::Max(2, [Math]::Min(20, $Participants))
  $guards = if ($count -le 4) { 5 } elseif ($count -le 8) { 10 } else { 15 }
  $projectiles = if ($count -le 2) { 3 } elseif ($count -eq 3) { 4 } `
    elseif ($count -le 5) { 5 } elseif ($count -eq 6) { 6 } else { 7 }
  $zones = if ($count -le 8) { 1 } elseif ($count -le 16) { 2 } else { 3 }
  $cooldown = 14
  return [pscustomobject]@{
    Casters = 5
    Guards = $guards
    Projectiles = $projectiles
    Zones = $zones
    MajorCooldownSeconds = $cooldown
  }
}
$ritualProfile = Get-RitualScalingProfile -Participants $playerNames.Count

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  try {
    $result = & $rconScript -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  } catch {
    throw "Local RCON command failed: $CommandText $($_.Exception.Message)"
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
  $safeEvidencePath = Assert-EndRiftLocalRuntimePath `
    -Path $evidencePath -LocalRuntimeRoot $runtimeRoot
  Add-Content -LiteralPath $safeEvidencePath -Value $Text -Encoding UTF8
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
    Assert-LocalBotsStillRunning
    $tail = Get-LogTail -Offset $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    if ($null -ne $Action) {
      & $Action
    }
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
    Assert-LocalBotsStillRunning
    $tail = Get-LogTail -Offset $AfterOffset
    if (([Regex]::Matches($tail, $Pattern)).Count -ge $Minimum) { return $tail }
    if ($null -ne $Action) { & $Action }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for $Minimum matches of '$Pattern'. See $paperLog"
}

function Wait-NewCarrierCharge {
  param(
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [Parameter(Mandatory = $true)][hashtable]$SeenCharges,
    [int]$WaitSeconds = $TimeoutSeconds,
    [scriptblock]$Action
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    Assert-LocalBotsStillRunning
    # Wave start, replacement spawn and charge creation can share one Paper
    # tick. Scan from the single pre-wave cursor and select the first UUID
    # that this probe has not consumed instead of advancing a byte cursor
    # after each marker and losing same-tick output.
    $tail = Get-LogTail -Offset $AfterOffset
    $matches = [Regex]::Matches($tail, 'END_RIFT_CARRIER_CHARGE_CREATED.*charge=([0-9a-fA-F-]{36})')
    foreach ($match in $matches) {
      $candidate = $match.Groups[1].Value
      if (-not $SeenCharges.ContainsKey($candidate)) {
        $SeenCharges[$candidate] = $true
        return $candidate
      }
    }
    if ($null -ne $Action) { & $Action | Out-Null }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for a new Wave 1 carrier charge. See $paperLog"
}

function Wait-CarrierDelivery {
  param(
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [Parameter(Mandatory = $true)][int]$DeliveryNumber,
    [Parameter(Mandatory = $true)][hashtable]$SeenCharges,
    [Parameter(Mandatory = $true)][int[]]$Core,
    [Parameter(Mandatory = $true)][string]$PickupPlayer,
    [int]$WaitSeconds = 120
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  $activeCharge = $null
  $activeChargeLocation = $null
  $activeCarrier = $null
  $configuredPickupCharge = $null
  $configuredHolderCharge = $null
  $activePickupPlayer = $null
  $activeHolder = $null
  $deliveredPattern = 'END_RIFT_CARRIER_DELIVERED.*charge=' + $DeliveryNumber + '/3'
  while ((Get-Date) -lt $deadline) {
    Assert-LocalBotsStillRunning
    $tail = Get-LogTail -Offset $AfterOffset
    # A larger roster can pick a replacement charge before the first one is
    # delivered.  The objective's delivery counter is authoritative; do not
    # keep waiting on a stale UUID after the server has advanced.
    if ($tail -match $deliveredPattern) {
      # Verify both client mode transitions after the objective has completed.
      # Waiting before teleport can consume the full 10-second charge lifetime.
      if ($null -ne $activePickupPlayer) {
        Wait-PlayerBotModeAcknowledgement -Name $activePickupPlayer -Mode 'CARRIER_PICKUP'
      }
      if ($null -ne $activeHolder) {
        Wait-PlayerBotModeAcknowledgement -Name $activeHolder -Mode 'CORE_DELIVER'
      }
      Set-PlayerBotMode -Mode 'ACTIVE'
      return $tail
    }

    $carrierMatches = [Regex]::Matches($tail,
      'END_RIFT_CARRIER_SELECTED[^\r\n]*entity=([0-9a-fA-F-]{36})[^\r\n]*')
    # Get-LogTail intentionally keeps the whole pre-delivery history so a
    # same-tick charge marker cannot be missed.  The newest selected carrier
    # is the only live target; replaying older matches makes the holder
    # teleport back and forth on every poll.
    if ($carrierMatches.Count -gt 0) {
      $match = $carrierMatches[$carrierMatches.Count - 1]
      $candidate = $match.Groups[1].Value
      if ($candidate -ne $activeCarrier) {
        $hadDroppedCharge = $null -ne $activeCharge
        $activeCarrier = $candidate
        if ($hadDroppedCharge) {
          # The old charge expired and the server selected another carrier.
          # Resume real attacks before following this new mob.
          $activeCharge = $null
          $activeChargeLocation = $null
          $configuredPickupCharge = $null
          $configuredHolderCharge = $null
          $activePickupPlayer = $null
          $activeHolder = $null
          Set-PlayerBotMode -Mode 'ACTIVE'
        }
        if ($null -eq $activeCharge) {
          $carrierLocation = Parse-LoggedLocation $match.Value
          if ($null -eq $carrierLocation -or @($carrierLocation).Count -lt 3) {
            throw "Could not parse the server location for Wave 1 carrier $candidate."
          }
          # A selected carrier can be pinned behind the recovered arena's
          # geometry. Move only this probe's designated picker beside that
          # target; it still sends a vanilla attack and the server validates
          # the hit before creating a charge.
          Teleport-Player $PickupPlayer `
            ($carrierLocation[0] + 1.0D) $carrierLocation[1] ($carrierLocation[2] + 1.0D)
          Write-Evidence "CURRENT_WAVE1_CARRIER_HUNT_POSITION name=$PickupPlayer target=$candidate location=$($carrierLocation -join ',')"
        }
      }
    }

    $chargeMatches = [Regex]::Matches($tail,
      'END_RIFT_CARRIER_CHARGE_CREATED[^\r\n]*charge=([0-9a-fA-F-]{36})[^\r\n]*location=[^\s]+\s+-?\d+(?:\.\d+)?,-?\d+(?:\.\d+)?,-?\d+(?:\.\d+)?')
    foreach ($match in $chargeMatches) {
      $candidate = $match.Groups[1].Value
      if (-not $SeenCharges.ContainsKey($candidate)) {
        $SeenCharges[$candidate] = $true
        $activeCharge = $candidate
        $activeChargeLocation = Parse-LoggedLocation $match.Value
        $activeHolder = $null
        break
      }
    }

    if ($null -ne $activeCharge) {
      $chargeCreatedPattern = 'END_RIFT_CARRIER_CHARGE_CREATED[^\r\n]*charge=' +
        [Regex]::Escape($activeCharge) + '[^\r\n]*'
      $chargeCreatedMatch = [Regex]::Match($tail, $chargeCreatedPattern)
      $timeoutPattern = 'END_RIFT_CARRIER_TIMEOUT_(?:TRANSFER|REPLACEMENT)[^\r\n]*delivered=' +
        ($DeliveryNumber - 1) + '\b'
      $timeoutMatch = $null
      if ($chargeCreatedMatch.Success) {
        # Match returns the first historical timeout in the whole log tail.
        # Search all markers and select the first one after this charge instead,
        # so later charges can recover after their own timeout transfer.
        $timeoutMatches = [Regex]::Matches($tail, $timeoutPattern)
        foreach ($candidateTimeout in $timeoutMatches) {
          if ($candidateTimeout.Index -gt $chargeCreatedMatch.Index) {
            $timeoutMatch = $candidateTimeout
            break
          }
        }
      }
      if ($null -ne $timeoutMatch) {
        # A timeout transfer changes the selected carrier without emitting a
        # second CARRIER_SELECTED marker. Drop the stale item target and resume
        # normal attacks so the next carrier can produce a fresh charge.
        $activeCharge = $null
        $activeChargeLocation = $null
        $activeCarrier = $null
        $configuredPickupCharge = $null
        $configuredHolderCharge = $null
        $activePickupPlayer = $null
        $activeHolder = $null
        Set-PlayerBotMode -Mode 'ACTIVE'
        Write-Evidence "CURRENT_WAVE1_CHARGE_TIMEOUT_RECOVERED delivery=$DeliveryNumber"
      }
    }

    $pickedPattern = if ($null -ne $activeCharge) {
      'END_RIFT_CARRIER_PICKED_UP.*charge=' + [Regex]::Escape($activeCharge)
    } else {
      $null
    }
    if ($null -ne $pickedPattern -and $tail -match $pickedPattern) {
      # The server chooses the nearest living participant, which is not
      # necessarily the roster slot used to drive this charge.  Read the
      # authoritative holder UUID from the pickup marker and move only that
      # player.  Keeping the other clients where they are prevents vanilla
      # player collision from pushing the real holder out of the delivery
      # radius before the next server tick.
      $pickedHolderPattern = 'END_RIFT_CARRIER_PICKED_UP(?=[^\r\n]*charge=' + [Regex]::Escape($activeCharge) + ')[^\r\n]*player_name=([A-Za-z0-9_]{1,16})'
      $pickedHolderMatch = [Regex]::Match($tail, $pickedHolderPattern)
      if (-not $pickedHolderMatch.Success) {
        throw "Pickup marker for charge $activeCharge did not contain an authoritative player name."
      }
      $holderName = $pickedHolderMatch.Groups[1].Value
      if ($holderName -notin $playerNames) {
        throw 'Wave 1 charge was picked up by a non-probe participant; refusing to move that player.'
      }
      # Every charge can be picked up by a different participant.  Move only
      # the authoritative holder for this charge; never stack the whole roster
      # on the core and never leave later holders outside the delivery radius.
      if ($configuredHolderCharge -ne $activeCharge -or $activeHolder -ne $holderName) {
        Set-PlayerBotMode -Mode 'PASSIVE'
        Set-PlayerBotModeForName -Name $holderName -Mode 'CORE_DELIVER'
        Teleport-Player $holderName `
          ($Core[0] + 0.5D) ($Core[1] + 1.0D) ($Core[2] + 0.5D)
        $configuredHolderCharge = $activeCharge
        $activeHolder = $holderName
      }
    } elseif ($null -ne $activeCharge) {
      if ($null -eq $activeChargeLocation) {
        throw "Could not parse the server location for Wave 1 charge $activeCharge."
      }
      if ($configuredPickupCharge -ne $activeCharge) {
        # Proximity pickup is intentionally disabled in Wave 1. Keep other
        # probes still, move one disposable client beside the charge, then
        # have that client send the explicit right-click-air interaction.
        Write-Evidence "CURRENT_WAVE1_CHARGE_TRACKED delivery=$DeliveryNumber charge=$activeCharge location=$($activeChargeLocation -join ',') pickup=$PickupPlayer"
        Set-PlayerBotMode -Mode 'PASSIVE'
        $activePickupPlayer = $PickupPlayer
        Set-PlayerBotModeForName -Name $PickupPlayer -Mode 'CARRIER_PICKUP'
        Teleport-Player $PickupPlayer $activeChargeLocation[0] $activeChargeLocation[1] $activeChargeLocation[2]
        $configuredPickupCharge = $activeCharge
      }
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for Wave 1 carrier delivery $DeliveryNumber/3. See $paperLog"
}

function Get-Status {
  return (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
}

function Get-Core {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Status did not expose Core coordinates: $Status" }
  return [int[]]@([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
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

function Parse-LoggedLocation {
  param([Parameter(Mandatory = $true)][string]$Text)
  $match = [Regex]::Match($Text,
    'location=[^\s]+\s+(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)')
  if (-not $match.Success) { return $null }
  return ,([double[]]@(
    [double]$match.Groups[1].Value,
    [double]$match.Groups[2].Value,
    [double]$match.Groups[3].Value))
}

function Teleport-Player {
  param([string]$Name, [double]$X, [double]$Y, [double]$Z)
  $null = Invoke-LocalRcon ("tp $Name $(Format-Coordinate $X) $(Format-Coordinate $Y) $(Format-Coordinate $Z) 180 0")
}

function Teleport-PlayersToPads {
  param([Parameter(Mandatory = $true)][object[]]$Pads)
  if ($Pads.Count -lt $playerNames.Count) { throw 'Not enough persisted runes for the local roster.' }
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    # Windows PowerShell can add one extra array wrapper when a nested
    # coordinate tuple crosses a scriptblock parameter boundary.  Unwrap the
    # tuple and each scalar before arithmetic; otherwise a valid local run
    # fails with Object[] op_Addition during the ritual/transition action.
    $pad = $Pads[$index]
    while ($pad -is [Array] -and $pad.Count -eq 1) { $pad = $pad[0] }
    if ($pad -isnot [Array] -or $pad.Count -lt 3) {
      throw "Rune coordinate tuple was malformed for player $($playerNames[$index]): $pad"
    }
    $x = $pad[0]
    $y = $pad[1]
    $z = $pad[2]
    while ($x -is [Array] -and $x.Count -eq 1) { $x = $x[0] }
    while ($y -is [Array] -and $y.Count -eq 1) { $y = $y[0] }
    while ($z -is [Array] -and $z.Count -eq 1) { $z = $z[0] }
    Teleport-Player $playerNames[$index] ([double]$x + 0.5D) ([double]$y) ([double]$z + 0.5D)
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

# The local probe runs under Windows PowerShell/.NET Framework, where
# Environment.TickCount64 is unavailable.  TickCount is sufficient for these
# bounded runs; its 24-day wraparound is far outside any probe lifetime.
$script:lastCombatRecoveryCheckAt = 0L

function Get-PlayerPosition {
  param([Parameter(Mandatory = $true)][string]$Name)
  $text = [string](Invoke-LocalRcon ("data get entity $Name Pos"))
  $match = [Regex]::Match($text,
    '\[(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\]')
  if (-not $match.Success) { return $null }
  return [double[]]@(
    [double]$match.Groups[1].Value,
    [double]$match.Groups[2].Value,
    [double]$match.Groups[3].Value)
}

function Teleport-PlayersIfOutsideCombatArea {
  param([Parameter(Mandatory = $true)][object]$Core)
  # Wait-Log invokes its action every 500ms.  Check positions less often so
  # recovery cannot become an RCON flood, and never reposition a player that
  # is still inside the arena and legitimately walking toward a target.
  $now = [Environment]::TickCount
  if ($now - $script:lastCombatRecoveryCheckAt -lt 2000L) { return }
  $script:lastCombatRecoveryCheckAt = $now

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
  $coreX = [double]$coordinates[0]
  $coreY = [double]$coordinates[1]
  $coreZ = [double]$coordinates[2]
  $points = @(
    [pscustomobject]@{ X = $coreX + 6.0D; Y = $coreY; Z = $coreZ + 0.5D },
    [pscustomobject]@{ X = $coreX - 6.0D; Y = $coreY; Z = $coreZ + 0.5D },
    [pscustomobject]@{ X = $coreX + 0.5D; Y = $coreY; Z = $coreZ + 6.0D },
    [pscustomobject]@{ X = $coreX + 0.5D; Y = $coreY; Z = $coreZ - 6.0D },
    [pscustomobject]@{ X = $coreX - 5.0D; Y = $coreY; Z = $coreZ + 5.0D },
    [pscustomobject]@{ X = $coreX + 5.0D; Y = $coreY; Z = $coreZ - 5.0D }
  )
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $position = Get-PlayerPosition -Name $playerNames[$index]
    if ($null -eq $position) { continue }
    $dx = $position[0] - ($coreX + 0.5D)
    $dy = $position[1] - $coreY
    $dz = $position[2] - ($coreZ + 0.5D)
    if ($dx * $dx + $dz * $dz -gt 20.0D * 20.0D -or [Math]::Abs($dy) -gt 8.0D) {
      $point = $points[$index % $points.Count]
      Teleport-Player $playerNames[$index] $point.X $point.Y $point.Z
    }
  }
}

$script:lastWaveCombatRecoveryCheckAt = 0L

function Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange {
  # Test-only positioning aid for the real combat packet path. The recovered
  # local arena can pin a disposable Mineflayer client while it walks toward a
  # valid mob. Resolve only this event's tagged living mobs and teleport a
  # client only when the target is outside the bot's melee range. The client
  # still sends the vanilla use_entity attack and the server still validates
  # reach, aim, phase and damage.
  $now = [Environment]::TickCount
  if ($now - $script:lastWaveCombatRecoveryCheckAt -lt 2000L) { return }
  $script:lastWaveCombatRecoveryCheckAt = $now
  $meleeRangeSquared = 2.75D * 2.75D

  foreach ($name in $playerNames) {
    $playerPosition = Get-PlayerPosition -Name $name
    if ($null -eq $playerPosition) { continue }
    $targetCandidates = @()
    foreach ($entityType in @('enderman', 'skeleton', 'spider', 'warden', 'ravager', 'vex')) {
      $targetText = Invoke-LocalRcon (
        "execute positioned $(Format-Coordinate $playerPosition[0]) $(Format-Coordinate $playerPosition[1]) $(Format-Coordinate $playerPosition[2]) run data get entity @e[tag=copimine_end_event,type=$entityType,sort=nearest,limit=1,distance=..32] Pos")
      $match = [Regex]::Match([string]$targetText,
        '\[(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\s*,\s*(-?\d+(?:\.\d+)?)[dD]?\]')
      if (-not $match.Success) { continue }
      $targetPosition = [double[]]@(
        [double]$match.Groups[1].Value,
        [double]$match.Groups[2].Value,
        [double]$match.Groups[3].Value)
      $dx = $targetPosition[0] - $playerPosition[0]
      $dy = $targetPosition[1] - $playerPosition[1]
      $dz = $targetPosition[2] - $playerPosition[2]
      $targetCandidates += [pscustomobject]@{
        Position = $targetPosition
        DistanceSquared = $dx * $dx + $dy * $dy + $dz * $dz
      }
    }
    $target = $targetCandidates | Sort-Object DistanceSquared | Select-Object -First 1
    if ($null -eq $target -or $target.DistanceSquared -le $meleeRangeSquared) {
      continue
    }
    Teleport-Player $name $target.Position[0] $target.Position[1] $target.Position[2]
  }
}

function Teleport-PlayersToObeliskRing {
  param([Parameter(Mandatory = $true)][object]$Core)
  # Wave 4 is a directional reflection test.  Keeping both clients on the
  # east/west pair makes the north/south obelisks depend on a long diagonal
  # return path and can leave a valid live run waiting indefinitely.  Place
  # the clients on deterministic points one block inside the authored
  # obelisk ring, so every active source has a nearby real player and the
  # server still validates the normal use_entity reflection packet.
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
  # Calculate the scalar once.  PowerShell parses division inside an array
  # literal as an operation on the whole expression in some invocation
  # contexts, which can turn the five-player lane list into Object[].
  $halfPi = [Math]::PI / 2.0D
  $obeliskRingAngles = if ($playerNames.Count -le 2) {
    @(-$halfPi, $halfPi)
  } else {
    # Use one client lane per active obelisk.  The four-lane layout that was
    # previously used for every roster left the two diagonal towers outside
    # the bot reflection radius at the 5-obelisk (8-15 players) scale.
    $laneCount = Get-ObeliskCount $playerNames.Count
    @(
      for ($lane = 0; $lane -lt $laneCount; $lane++) {
        -$halfPi + (2.0D * [Math]::PI * $lane / $laneCount)
      }
    )
  }
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $angle = $obeliskRingAngles[$index % $obeliskRingAngles.Count]
    $pointX = $x + 0.5D + [Math]::Cos($angle) * 8.0D
    $pointZ = $z + 0.5D + [Math]::Sin($angle) * 8.0D
    Teleport-Player $playerNames[$index] $pointX $y $pointZ
  }
}

$script:lastObeliskRecoveryCheckAt = 0L

function Teleport-PlayersToObeliskRingIfDisplaced {
  param([Parameter(Mandatory = $true)][object]$Core)
  # Poll callbacks run every 500ms.  A conditional, throttled recovery keeps
  # knockback from stranding a client without fighting ordinary movement or
  # resetting the same player before a real use_entity reflection packet.
  $now = [Environment]::TickCount
  if ($now - $script:lastObeliskRecoveryCheckAt -lt 2000L) { return }
  $script:lastObeliskRecoveryCheckAt = $now

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
  $halfPi = [Math]::PI / 2.0D
  $obeliskRingAngles = if ($playerNames.Count -le 2) {
    @(-$halfPi, $halfPi)
  } else {
    $laneCount = Get-ObeliskCount $playerNames.Count
    @(
      for ($lane = 0; $lane -lt $laneCount; $lane++) {
        -$halfPi + (2.0D * [Math]::PI * $lane / $laneCount)
      }
    )
  }
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $angle = $obeliskRingAngles[$index % $obeliskRingAngles.Count]
    $targetX = $x + 0.5D + [Math]::Cos($angle) * 8.0D
    $targetZ = $z + 0.5D + [Math]::Sin($angle) * 8.0D
    $position = Get-PlayerPosition -Name $playerNames[$index]
    if ($null -eq $position) { continue }
    $dx = $position[0] - $targetX
    $dy = $position[1] - $y
    $dz = $position[2] - $targetZ
    if ($dx * $dx + $dz * $dz -gt 3.0D * 3.0D -or [Math]::Abs($dy) -gt 4.0D) {
      Teleport-Player $playerNames[$index] $targetX $y $targetZ
    }
  }
}

function Teleport-PlayersToWave4ReflectionLaneIfDue {
  param([Parameter(Mandatory = $true)][object]$Core)
  $obeliskCount = Get-ObeliskCount $playerNames.Count
  if ($playerNames.Count -ge $obeliskCount) {
    Teleport-PlayersToObeliskRingIfDisplaced -Core $Core
    return
  }

  # Every obelisk fires toward a participant. Keep a short roster around the
  # arena center so fireballs travel far enough to fall into the player's
  # interaction reach before arrival. Rotating to the outer ring put one
  # player under each launch socket; those initial fireballs were too high to
  # reflect toward their source and the bot treated its unconfirmed swing as
  # success.
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
  $halfPi = [Math]::PI / 2.0D
  $smallRosterRadius = 2.5D
  $laneTolerance = 0.75D
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    # Opposite points on the same axis let one bot stand directly in the
    # other bot's reflected-fireball path. Use perpendicular lanes for two
    # players while keeping both close enough to receive and reflect shots.
    $angle = if ($playerNames.Count -eq 2 -and $index -eq 1) {
      0.0D
    } elseif ($playerNames.Count -eq 2) {
      -$halfPi
    } else {
      -$halfPi + (2.0D * [Math]::PI * $index / $playerNames.Count)
    }
    $targetX = $x + 0.5D + [Math]::Cos($angle) * $smallRosterRadius
    $targetZ = $z + 0.5D + [Math]::Sin($angle) * $smallRosterRadius
    $position = Get-PlayerPosition -Name $playerNames[$index]
    if ($null -eq $position) { continue }
    $dx = $position[0] - $targetX
    $dy = $position[1] - $y
    $dz = $position[2] - $targetZ
    if ($dx * $dx + $dz * $dz -gt $laneTolerance * $laneTolerance -or [Math]::Abs($dy) -gt 4.0D) {
      Teleport-Player $playerNames[$index] $targetX $y $targetZ
    }
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

function Set-PlayerBotMode {
  param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('PASSIVE', 'ACTIVE', 'ACTIVE_WAVE7', 'CARRIER_PICKUP', 'CORE_DELIVER')]
    [string]$Mode
  )
  foreach ($name in $playerNames) {
    Set-PlayerBotModeForName -Name $name -Mode $Mode
  }
}

function Wait-PlayerBotModeAcknowledgement {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)]
    [ValidateSet('CARRIER_PICKUP', 'CORE_DELIVER')]
    [string]$Mode,
    [int]$WaitSeconds = 5
  )
  $modePath = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $controlDirectory ($Name + '.mode')) -LocalRuntimeRoot $runtimeRoot -MustExist
  $safeModeAckPath = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $controlDirectory ($Name + '.mode.ack')) -LocalRuntimeRoot $runtimeRoot
  $modeRequestId = $script:botModeRequestIds["$Name|$Mode"]
  if ([string]::IsNullOrWhiteSpace($modeRequestId)) {
    throw "Probe player $Name has no correlated mode request for $Mode."
  }
  $acknowledgement = "$Mode|$modeRequestId"
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Path -LiteralPath $safeModeAckPath -PathType Leaf) {
      $botAcknowledgements = Get-Content -LiteralPath $safeModeAckPath -ErrorAction SilentlyContinue
      if ($botAcknowledgements -contains $acknowledgement) {
        Write-Evidence "CURRENT_WAVE1_BOT_MODE_ACK name=$Name mode=$Mode"
        return
      }
    }
    Start-Sleep -Milliseconds 100
  }
  throw "Probe player $Name did not acknowledge objective mode $Mode within $WaitSeconds seconds. Expected $acknowledgement at $safeModeAckPath."
}

function Set-PlayerBotModeForName {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)]
    [ValidateSet('PASSIVE', 'ACTIVE', 'ACTIVE_WAVE7', 'CARRIER_PICKUP', 'CORE_DELIVER')]
    [string]$Mode
  )
  if ($Name -notin $playerNames) {
    throw 'Wave 1 interaction mode is restricted to disposable local probe players.'
  }
  $modePath = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $controlDirectory ($Name + '.mode')) -LocalRuntimeRoot $runtimeRoot
  $controlValue = $Mode
  if ($Mode -in @('CARRIER_PICKUP', 'CORE_DELIVER')) {
    $modeRequestId = [guid]::NewGuid().ToString('N')
    $controlValue = "$Mode|$modeRequestId"
    $script:botModeRequestIds["$Name|$Mode"] = $modeRequestId
  }
  Set-Content -LiteralPath $modePath `
    -Value $controlValue -NoNewline -Encoding ASCII
  $writtenMode = (Get-Content -LiteralPath $modePath -Raw).Trim()
  if ($writtenMode -ne $controlValue) {
    throw "Failed to set probe player $Name mode. Expected $Mode, found $writtenMode."
  }
  if ($Mode -in @('CARRIER_PICKUP', 'CORE_DELIVER')) {
    Write-Evidence "CURRENT_WAVE1_BOT_MODE_REQUEST name=$Name mode=$Mode"
  }
}

function Start-PlayerBot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][int[]]$Core)
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $out = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $botLogDirectory ($Name + '.log')) -LocalRuntimeRoot $runtimeRoot
  $err = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $botLogDirectory ($Name + '.err.log')) -LocalRuntimeRoot $runtimeRoot
  $safeControlDirectory = Assert-EndRiftLocalRuntimePath `
    -Path $controlDirectory -LocalRuntimeRoot $runtimeRoot -MustExist
  $interactionRange = 20
  $attackIntervalMilliseconds = 900
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $node
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  Set-EndRiftLocalBotChildEnvironment -StartInfo $startInfo -BotHost '127.0.0.1' -BotPort 25566
  Set-EndRiftLocalBotChildEnvironmentOverrides -StartInfo $startInfo -Overrides @{
    END_RIFT_BOT_CONTROL_DIRECTORY = $safeControlDirectory
    END_RIFT_BOT_SKIP_AUTH = '1'
    END_RIFT_BOT_SKIP_REGISTER = '1'
    # This local test bot answers only the 26.3 CopiMine mod challenge on
    # 127.0.0.1:25566. Production listeners and mod checks stay unchanged.
    END_RIFT_TEST_CLIENT_MOD_HANDSHAKE = '1'
    END_RIFT_TEST_CLIENT_MOD_VERSION = '0.1.3+26.3'
    # Both clients must reflect: Wave 7 launches independent chamber-local
    # projectiles and authorizes only the player assigned to each chamber.
    END_RIFT_REFLECT_ENABLED = [string]$env:END_RIFT_REFLECT_ENABLED
    END_RIFT_REFLECT_START_MS = [string]$env:END_RIFT_REFLECT_START_MS
    END_RIFT_REFLECT_DIAGNOSTICS = [string]$env:END_RIFT_REFLECT_DIAGNOSTICS
    END_RIFT_OBELISK_TARGETS = [string]$env:END_RIFT_OBELISK_TARGETS
    END_RIFT_WAVE7_CHAMBERS = [string]$env:END_RIFT_WAVE7_CHAMBERS
    END_RIFT_GUARDIAN_PROBE_NAMES = [string]$env:END_RIFT_GUARDIAN_PROBE_NAMES
    END_RIFT_ATTACK_INTERVAL_MS = [string]$env:END_RIFT_ATTACK_INTERVAL_MS
  }
  if ($null -ne $startInfo.ArgumentList) {
    foreach ($argument in @(
        $botScript, $Name, [string]($BotDurationSeconds * 1000),
        (Format-Coordinate ($Core[0] + 0.5D)), (Format-Coordinate $Core[1]),
        (Format-Coordinate ($Core[2] + 0.5D)), [string]$interactionRange,
        [string]$attackIntervalMilliseconds, $safeControlDirectory)) {
      $startInfo.ArgumentList.Add([string]$argument)
    }
  } else {
    $startInfo.Arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
      (Format-Coordinate ($Core[0] + 0.5D)) + ' ' + (Format-Coordinate $Core[1]) + ' ' +
      (Format-Coordinate ($Core[2] + 0.5D)) + ' ' + [string]$interactionRange + ' ' +
      [string]$attackIntervalMilliseconds + ' "' + $safeControlDirectory + '"'
  }
  # The runner polls these files for mode acknowledgements while Node keeps
  # writing bot output. Allow concurrent readers so those polls do not fail
  # with a Windows sharing violation.
  $stdoutStream = [IO.FileStream]::new(
    $out, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite)
  $stderrStream = [IO.FileStream]::new(
    $err, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite)
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process | Add-Member -NotePropertyName EndRiftBotName -NotePropertyValue $Name -Force
  try {
    if (-not $process.Start()) { throw "Could not start disposable local bot $Name." }
    $stdoutCopy = $process.StandardOutput.BaseStream.CopyToAsync($stdoutStream)
    $stderrCopy = $process.StandardError.BaseStream.CopyToAsync($stderrStream)
    $process | Add-Member -NotePropertyName EndRiftOutputCapture -NotePropertyValue @{
      StdoutTask = $stdoutCopy; StderrTask = $stderrCopy
      StdoutStream = $stdoutStream; StderrStream = $stderrStream
    } -Force
  } catch {
    $stdoutStream.Dispose()
    $stderrStream.Dispose()
    $process.Dispose()
    throw
  }
  $script:processes += $process
}

function Assert-LocalBotsStillRunning {
  foreach ($process in $processes) {
    if ($null -eq $process) { continue }
    try {
      $process.Refresh()
      if (-not $process.HasExited) { continue }
    } catch { continue }

    $diagnosticParts = @()
    $capture = $process.EndRiftOutputCapture
    if ($null -ne $capture) {
      foreach ($entry in @(
          @{ Name = 'stdout'; Task = $capture.StdoutTask },
          @{ Name = 'stderr'; Task = $capture.StderrTask }
        )) {
        try {
          if (-not $entry.Task.Wait(5000)) {
            $diagnosticParts += ($entry.Name + ' capture did not finish within five seconds')
          }
        } catch {
          $diagnosticParts += ($entry.Name + ' capture failed: ' + $_.Exception.GetBaseException().Message)
        }
        if ($entry.Task.IsFaulted -and $null -ne $entry.Task.Exception) {
          $diagnosticParts += ($entry.Name + ' capture failed: ' + $entry.Task.Exception.GetBaseException().Message)
        }
      }
      foreach ($streamEntry in @(
          @{ Name = 'stdout'; Stream = $capture.StdoutStream },
          @{ Name = 'stderr'; Stream = $capture.StderrStream }
        )) {
        try { $streamEntry.Stream.Flush() }
        catch { $diagnosticParts += ($streamEntry.Name + ' capture flush failed: ' + $_.Exception.GetBaseException().Message) }
      }
    }
    $name = if (-not [string]::IsNullOrWhiteSpace([string]$process.EndRiftBotName)) {
      [string]$process.EndRiftBotName
    } else {
      'unknown'
    }
    foreach ($suffix in @('.log', '.err.log')) {
      $logPath = Join-Path $botLogDirectory ($name + $suffix)
      if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) { continue }
      $readStream = $null
      try {
        # The async pipe copier still owns a write handle while it drains the
        # process's final bytes. Open a concurrent read handle explicitly so
        # Windows share-mode negotiation cannot hide the useful bot error.
        $readStream = [IO.FileStream]::new(
          $logPath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        $reader = [IO.StreamReader]::new($readStream, [Text.Encoding]::UTF8, $true)
        try { $content = $reader.ReadToEnd() }
        finally { $reader.Dispose() }
        if ($content.Length -gt 6000) { $content = $content.Substring($content.Length - 6000) }
        if (-not [string]::IsNullOrWhiteSpace($content)) {
          $diagnosticParts += ($suffix + ': ' + $content.Trim())
        }
      } catch {
        $diagnosticParts += ($suffix + ' could not be read: ' + $_.Exception.GetBaseException().Message)
      } finally {
        if ($null -ne $readStream) { $readStream.Dispose() }
      }
    }
    $diagnostic = if ($diagnosticParts.Count -gt 0) {
      $diagnosticParts -join [Environment]::NewLine
    } else {
      'No bot stdout or stderr was captured.'
    }
    throw "Local player bot $name exited with code $($process.ExitCode) before probe completion. $diagnostic"
  }
}

function Wait-PlayersOnline {
  for ($attempt = 0; $attempt -lt 100; $attempt++) {
    Assert-LocalBotsStillRunning
    $list = Invoke-LocalRcon 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) { return }
    Assert-LocalBotsStillRunning
    Start-Sleep -Milliseconds 500
  }
  throw "Local players did not join: $($playerNames -join ', ')"
}

function Wait-ClientBridgeV4Ready {
  param([Parameter(Mandatory = $true)][int64]$AfterOffset)
  $deadline = (Get-Date).AddSeconds(30)
  while ((Get-Date) -lt $deadline) {
    Assert-LocalBotsStillRunning
    $paperText = Get-LogTail -Offset $AfterOffset
    foreach ($name in $playerNames) {
      $rejected = [Regex]::Match($paperText,
        ('Rejected ' + [Regex]::Escape($name) + ' during CopiMineClient check: ([^\r\n]+)'))
      if ($rejected.Success) {
        throw "Local test client $name failed the authenticated V4 bridge handshake: $($rejected.Groups[1].Value)."
      }
    }
    $readyCount = 0
    foreach ($name in $playerNames) {
      $botLog = Join-Path $botLogDirectory ($name + '.log')
      if ((Test-Path -LiteralPath $botLog -PathType Leaf) -and
          ((Get-Content -LiteralPath $botLog -Raw) -match
            ('TEST_CLIENT_BRIDGE_V4_READY ' + [Regex]::Escape($name) + '\b'))) {
        $readyCount++
      }
    }
    if ($readyCount -eq $playerNames.Count) {
      Write-Evidence "CURRENT_CLIENT_BRIDGE_V4_PASS players=$readyCount"
      return
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for authenticated V4 bridge readiness for: $($playerNames -join ', ')."
}

function Configure-Players {
  foreach ($name in $playerNames) {
    $null = Invoke-LocalRcon ("gamemode survival $name")
    $null = Invoke-LocalRcon ("clear $name")
    $null = Invoke-LocalRcon ("attribute $name minecraft:max_health base set 1000")
    $null = Invoke-LocalRcon ("attribute $name minecraft:knockback_resistance base set 1")
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
  # Once a wave finishes, the bots must stop combat navigation and return to
  # their own start runes before the server can commit its continuous hold.
  # Waiting for readiness first leaves them active in the combat ring forever.
  Set-PlayerBotMode -Mode 'PASSIVE'
  Teleport-PlayersToPads -Pads $Pads
  Wait-Log -AfterOffset $offset -Pattern ('END_RIFT_TRANSITION_RUNES_READY.*completed_wave=' + $CompletedWave) -WaitSeconds 120 | Out-Null
  Teleport-PlayersToPads -Pads $Pads
  $next = $CompletedWave + 1
  Wait-Log -AfterOffset $offset -Pattern ('END_RIFT_WAVE_STARTED.*wave=' + $next + '\b') -WaitSeconds 120 -Action { Teleport-PlayersToPads -Pads $Pads } | Out-Null
  Set-PlayerBotMode -Mode 'ACTIVE'
  Write-Evidence "CURRENT_TRANSITION_PASS completed_wave=$CompletedWave next_wave=$next pads=$($Pads.Count) hold_ms=10000" | Out-Null
  # The next objective's first marker may share the exact tick with
  # END_RIFT_WAVE_STARTED. Preserve the cursor from before the transition.
  return [int64]$offset
}

function Wait-WaveComplete {
  param(
    [int]$Wave,
    [int[]]$Core,
    [int]$Seconds = $TimeoutSeconds,
    [int64]$AfterOffset = -1,
    [scriptblock]$Action
  )
  $offset = if ($AfterOffset -ge 0) { $AfterOffset } else { Get-LogLength }
  $completionAction = $Action
  if ($null -eq $completionAction) {
    $completionAction = { Teleport-PlayersIfOutsideCombatArea -Core $Core }
  }
  Wait-Log -AfterOffset $offset -Pattern ('END_RIFT_WAVE_COMPLETED.*wave=' + $Wave + '\b') -WaitSeconds $Seconds -Action $completionAction | Out-Null
  # Completion and the intermission transition marker are committed by the
  # same server tick. Return the pre-completion cursor to the caller so the
  # following Wait-Transition cannot skip that marker.
  return $offset
}

function Wait-CoreRestoration {
  param([int64]$AfterOffset)
  Wait-Log -AfterOffset $AfterOffset -Pattern 'END_RIFT_CORE_RESTORATION_STARTED.*next_wave=5' -WaitSeconds 60 | Out-Null
  Wait-Log -AfterOffset $AfterOffset -Pattern 'END_RIFT_CORE_RESTORATION_COMPLETED.*next_phase=INTERMISSION_4.*hold_ms=10000' -WaitSeconds 30 | Out-Null
  Write-Evidence 'CURRENT_CORE_RESTORATION_PASS duration_ms=6000 next_phase=INTERMISSION_4' | Out-Null
  # The Wave 4 rune transition is a separate required 10-second hold.
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
    return [double[]]@([double]$match.Groups[1].Value, [double]$match.Groups[2].Value, [double]$match.Groups[3].Value)
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
    # Wave 7 can leave one official elite/guardian alive.  Its custom name is
    # not a reliable protocol type, so query every LivingEntity type used by
    # the chamber autopilot and choose the nearest same-chamber target. Leaving
    # Warden, Ravager or Vex out can strand a client behind the closed chamber
    # boundary while the matching trial stays alive.
    $targetCandidates = @()
    foreach ($entityType in @('enderman', 'skeleton', 'spider', 'warden', 'ravager', 'vex')) {
      $targetText = Invoke-LocalRcon ("execute positioned $(Format-Coordinate $playerPosition[0]) $(Format-Coordinate $playerPosition[1]) $(Format-Coordinate $playerPosition[2]) run data get entity @e[tag=copimine_end_event,type=$entityType,sort=nearest,limit=1,distance=..24] Pos")
      $targetPosition = Parse-Position $targetText
      if ($null -eq $targetPosition) { continue }
      if ((Get-ChamberIndex $targetPosition[0] $targetPosition[2] $wave7ChamberCount) -ne $playerChamber) { continue }
      $dx = $targetPosition[0] - $playerPosition[0]
      $dy = $targetPosition[1] - $playerPosition[1]
      $dz = $targetPosition[2] - $playerPosition[2]
      $targetCandidates += [pscustomobject]@{
        Position = $targetPosition
        DistanceSquared = $dx * $dx + $dy * $dy + $dz * $dz
      }
    }
    $targetCandidate = $targetCandidates | Sort-Object DistanceSquared | Select-Object -First 1
    if ($null -eq $targetCandidate) { continue }
    $targetPosition = $targetCandidate.Position
    if ($targetCandidate.DistanceSquared -gt 18.0D) {
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
    return [double[]]@([double]$match.Groups[1].Value, [double]$match.Groups[2].Value, [double]$match.Groups[3].Value)
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

$null = Assert-EndRiftLocalAuthMeDisabled -PluginListOutput (Invoke-LocalRcon 'plugins')

function Get-LocalWhitelistNames {
  $whitelistPath = Assert-EndRiftLocalRuntimePath `
    -Path (Join-Path $serverDir 'whitelist.json') -LocalRuntimeRoot $runtimeRoot -MustExist
  try {
    # Windows PowerShell 5.1 does not enumerate ConvertFrom-Json output, so
    # @(<pipeline> | ConvertFrom-Json) yields a single nested array element.
    $parsed = Get-Content -LiteralPath $whitelistPath -Raw | ConvertFrom-Json -ErrorAction Stop
    $entries = @($parsed)
  } catch {
    throw "Could not safely read the isolated test whitelist: $($_.Exception.Message)"
  }
  return @($entries | ForEach-Object { [string]$_.name } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Add-LocalProbeWhitelistEntries {
  foreach ($name in $playerNames) {
    if ((Get-LocalWhitelistNames) -contains $name) { continue }
    # Track before the RCON mutation so finally can remove a partially applied
    # entry even if the subsequent confirmation read fails.
    $script:temporaryProbeWhitelistNames.Add($name)
    $null = Invoke-LocalRcon ("whitelist add $name")
    if ((Get-LocalWhitelistNames) -notcontains $name) {
      throw "The isolated server did not persist temporary probe whitelist entry $name."
    }
  }
  Write-Evidence "LOCAL_AUTH_MODE whitelist=enabled temporary_probe_accounts=$($script:temporaryProbeWhitelistNames.Count)"
}

function Remove-LocalProbeWhitelistEntries {
  $remaining = [System.Collections.Generic.List[string]]::new()
  foreach ($name in @($script:temporaryProbeWhitelistNames)) {
    try {
      $null = Invoke-LocalRcon ("whitelist remove $name")
      if ((Get-LocalWhitelistNames) -contains $name) {
        throw 'The server whitelist still contains the temporary probe name.'
      }
    } catch {
      $remaining.Add($name)
      Write-Warning "Could not remove a temporary local probe whitelist entry: $name ($($_.Exception.Message))"
    }
  }
  $script:temporaryProbeWhitelistNames.Clear()
  foreach ($name in $remaining) { $script:temporaryProbeWhitelistNames.Add($name) }
  if ($remaining.Count -eq 0) {
    Write-Evidence 'LOCAL_AUTH_MODE temporary_probe_accounts_removed=true'
  } else {
    throw "Temporary local probe whitelist cleanup remains incomplete for $($remaining.Count) test account(s)."
  }
}

try {
  $null = Assert-EndRiftLocalBotProtocolGateway -BotPort 25566
  Add-LocalProbeWhitelistEntries
  $safeBotLogDirectory = Assert-EndRiftLocalRuntimePath `
    -Path $botLogDirectory -LocalRuntimeRoot $runtimeRoot
  $safeControlDirectory = Assert-EndRiftLocalRuntimePath `
    -Path $controlDirectory -LocalRuntimeRoot $runtimeRoot
  New-Item -ItemType Directory -Path $safeBotLogDirectory -Force | Out-Null
  New-Item -ItemType Directory -Path $safeControlDirectory -Force | Out-Null
  $safeControlDirectory = Assert-EndRiftLocalRuntimePath `
    -Path $controlDirectory -LocalRuntimeRoot $runtimeRoot -MustExist
  Write-Evidence 'LOCAL_AUTH_MODE authme=disabled bot_auth_commands=skipped'
  Get-ChildItem -LiteralPath $safeControlDirectory -Filter '*.mode' -File -ErrorAction SilentlyContinue | ForEach-Object {
    $safeModePath = Assert-EndRiftLocalRuntimePath `
      -Path $_.FullName -LocalRuntimeRoot $runtimeRoot -MustExist
    Remove-Item -LiteralPath $safeModePath -Force
  }
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
$env:END_RIFT_REFLECT_DIAGNOSTICS = '1'
  $env:END_RIFT_OBELISK_TARGETS = (Get-ObeliskTargets -Core $core -Count (Get-ObeliskCount $playerNames.Count) | ConvertTo-Json -Compress)
  $wave7ChamberCount = if ($playerNames.Count -le 3) { $playerNames.Count } else { 4 }
  $env:END_RIFT_WAVE7_CHAMBERS = [string]$wave7ChamberCount
  # Last Seal is intentionally shielded while permanent guardian hitboxes are
  # alive. Designate the real bots to attack those hitboxes, then let the
  # normal boss path resume once the shield is legitimately removed.
  $env:END_RIFT_GUARDIAN_PROBE_NAMES = $playerNames -join ','
  $env:END_RIFT_ATTACK_INTERVAL_MS = '200'
  foreach ($name in $playerNames) {
    # Probe players must not chase ambient mobs while they are being placed on
    # the start runes. Enable combat only after the official Wave 1 marker.
    Set-PlayerBotModeForName -Name $name -Mode 'PASSIVE'
    Start-PlayerBot -Name $name -Core $core
    Start-Sleep -Milliseconds 250
  }
  Wait-PlayersOnline
  Wait-ClientBridgeV4Ready -AfterOffset $runLogOffset
  Configure-Players
  # Rune occupancy can complete the ritual in the same command batch as the
  # pad teleports. Capture the cursor before that mutation, otherwise the
  # reader can start after RITUAL_STARTED and lose the whole transition.
  $ritualOffset = $runLogOffset
  Teleport-PlayersToPads -Pads $pads
  # The bots are passive during the start ritual and were placed above. Repeated
  # teleports while Paper measures the continuous ten-second hold can make the
  # client resend stale movement and evict it from the rune.
  Wait-Log -AfterOffset $ritualOffset -Pattern ('RITUAL_STARTED.*event=' + $eventId) -WaitSeconds 30 | Out-Null
  Wait-Log -AfterOffset $ritualOffset -Pattern ('RITUAL_COMPLETED.*event=' + $eventId) -WaitSeconds 100 | Out-Null
  Write-Evidence "CURRENT_RITUAL_PASS event=$eventId players=$($playerNames.Count)"

  # RITUAL_COMPLETED, the state transition, and WAVE_STARTED are emitted by
  # the same server tick. Keep the pre-ritual cursor so a reader that observes
  # RITUAL_COMPLETED cannot skip the already-written W1 marker.
  $waveOffset = $ritualOffset
  # Keep one cursor before WAVE_STARTED and the first charge marker. The
  # objective can create the first carrier in the same tick as wave start.
  $waveOneObjectiveOffset = $waveOffset
  Wait-Log -AfterOffset $waveOffset -Pattern ('END_RIFT_WAVE_STARTED.*event=' + $eventId + '.*wave=1\b') -WaitSeconds 30 | Out-Null
  Set-PlayerBotMode -Mode 'ACTIVE'
  $waveOneCompletionOffset = $null
  $seenCarrierCharges = @{}
  $carrierOffset = $waveOneObjectiveOffset
  Wait-CarrierDelivery -AfterOffset $carrierOffset -DeliveryNumber 1 `
    -SeenCharges $seenCarrierCharges -Core $core -PickupPlayer $playerNames[0] -WaitSeconds 180 | Out-Null
  Wait-CarrierDelivery -AfterOffset $carrierOffset -DeliveryNumber 2 `
    -SeenCharges $seenCarrierCharges -Core $core -PickupPlayer $playerNames[1 % $playerNames.Count] -WaitSeconds 180 | Out-Null
  # The final delivery and W1 completion are committed synchronously by the
  # same server tick.  Capture the offset before that delivery so the
  # completion marker cannot be missed by a post-delivery reader.
  $waveOneCompletionOffset = $carrierOffset
  Wait-CarrierDelivery -AfterOffset $carrierOffset -DeliveryNumber 3 `
    -SeenCharges $seenCarrierCharges -Core $core -PickupPlayer $playerNames[2 % $playerNames.Count] -WaitSeconds 180 | Out-Null
  Wait-Log -AfterOffset $waveOneCompletionOffset -Pattern 'END_RIFT_WAVE_COMPLETED.*wave=1\b' -WaitSeconds 300 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  $waveOneTransitionOffset = $waveOneCompletionOffset
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=1 objective=RIFT_CARRIERS"
  if ($StopAfterWave -eq 1) { return }

  $pads = Get-Pads
  $waveTwoStartOffset = Wait-Transition -CompletedWave 1 -Pads $pads -AfterOffset $waveOneTransitionOffset
  $waveTwoTransitionOffset = Wait-WaveComplete -Wave 2 -Core $core -Seconds 300 -Action {
    Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
  }
  Wait-Log -AfterOffset $waveTwoStartOffset -Pattern 'ELITE_ARROW_RAIN_RELEASE.*player_only=true' -WaitSeconds 45 | Out-Null
  Write-Evidence "CURRENT_WAVE2_ELITE_SPELL_PASS spell=arrow_rain projectiles=5 player_only=true"
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=2 objective=RIFT_HUNT"
  if ($StopAfterWave -eq 2) { return }

  $pads = Get-Pads
  $waveThreeStartOffset = Wait-Transition -CompletedWave 2 -Pads $pads -AfterOffset $waveTwoTransitionOffset
  Set-PlayerBotMode -Mode 'PASSIVE'
  $portalOffset = $waveThreeStartOffset
  Wait-Log -AfterOffset $portalOffset -Pattern 'END_RIFT_PORTALS_READY.*count=3.*sequential=true' -WaitSeconds 60 -Action { Teleport-PlayersToCombatRing -Core $core } | Out-Null
  for ($portalIndex = 0; $portalIndex -lt 3; $portalIndex++) {
    $angle = -[Math]::PI / 2.0D + (2.0D * [Math]::PI * $portalIndex / 3.0D)
    $portalX = $core[0] + 0.5D + 8.0D * [Math]::Cos($angle)
    $portalZ = $core[2] + 0.5D + 8.0D * [Math]::Sin($angle)
    # Portal capture is an any-player objective.  Move one authoritative
    # participant onto the active portal instead of stacking the entire
    # roster at one point, which caused five-player clients to collide and
    # then leave the capture radius before the next server tick.
    $capturePlayer = $playerNames[$portalIndex % $playerNames.Count]
    Teleport-Player $capturePlayer $portalX $core[1] $portalZ
    $actionX = $portalX
    $actionZ = $portalZ
    $portalCompletionPattern = if ($portalIndex -eq 2) {
      # The final capture and objective transition are allowed to commit on
      # one server tick.  In that case the progress line can stop at 88% and
      # the authoritative objective-complete marker is the only completion
      # evidence.  Accept both forms without weakening the earlier portals.
      'PORTAL_CAPTURE_PROGRESS.*index=2.*completed=true|WAVE_OBJECTIVE_COMPLETE.*wave=3.*portals=3'
    } else {
      'PORTAL_CAPTURE_PROGRESS.*index=' + $portalIndex + '.*completed=true'
    }
    Wait-Log -AfterOffset $portalOffset -Pattern $portalCompletionPattern -WaitSeconds 45 -Action {
      Teleport-Player $capturePlayer $actionX $core[1] $actionZ
    } | Out-Null
  }
  # The final portal capture and Wave 3 completion may be committed in the
  # same Paper tick.  Keep the pre-portal cursor so a fast multi-player run
  # cannot advance past END_RIFT_WAVE_COMPLETED before this wait starts.
  Wait-Log -AfterOffset $portalOffset `
    -Pattern 'WAVE3_PORTAL_ELITE_SPAWNED.*portal=1.*mode=(EMERGE|FALLBACK)' `
    -WaitSeconds 45 | Out-Null
  Write-Evidence "CURRENT_WAVE3_PORTAL_ELITE_PASS source=portal-two mode=emerge-or-captured-anchor"
  Set-PlayerBotMode -Mode 'ACTIVE'
  $waveThreeTransitionOffset = Wait-WaveComplete -Wave 3 -Core $core -Seconds 300 -AfterOffset $portalOffset
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=3 objective=RIFT_GATES portals=3"
  if ($StopAfterWave -eq 3) { return }

  $pads = Get-Pads
  $waveFourStartOffset = Wait-Transition -CompletedWave 3 -Pads $pads -AfterOffset $waveThreeTransitionOffset
  $obeliskCount = Get-ObeliskCount $playerNames.Count
  $w4Offset = $waveFourStartOffset
  # The marker may already exist before Wait-Log is called.  In that case its
  # action callback is intentionally not invoked, so position the probes once
  # before each observation as well as while polling.
  # Wave 4 is a reflection objective.  Keep the real clients' combat loop
  # passive while they perform that packet interaction; otherwise nearby
  # assault mobs make Mineflayer chase them out of the reflection lanes.
  Set-PlayerBotMode -Mode PASSIVE
  Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core
  Wait-Log -AfterOffset $w4Offset -Pattern ('END_RIFT_OBELISK_ASSAULT_READY.*obelisks=' + $obeliskCount + '.*real_blocks=true') -WaitSeconds 150 -Action { Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core } | Out-Null
  Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core
  Wait-LogCount -AfterOffset $w4Offset -Pattern 'END_RIFT_OBELISK_ACTIVE ' -Minimum $obeliskCount -WaitSeconds 90 -Action { Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core } | Out-Null
  Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core
  Wait-Log -AfterOffset $w4Offset -Pattern 'RIFT_FIREBALL_LAUNCH ' -WaitSeconds 120 -Action { Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core } | Out-Null
  Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core
  $waveFourTransitionOffset = Wait-WaveComplete -Wave 4 -Core $core -Seconds 600 -Action { Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core }
  Set-PlayerBotMode -Mode ACTIVE
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=4 objective=OBELISK_ASSAULT obelisks=$obeliskCount"
  if ($StopAfterWave -eq 4) { return }

  $waveFourRestorationOffset = Wait-CoreRestoration -AfterOffset $waveFourTransitionOffset
  $pads = Get-Pads
  $waveFiveStartOffset = Wait-Transition -CompletedWave 4 -Pads $pads -AfterOffset $waveFourRestorationOffset
  $fogOffset = $waveFiveStartOffset
  for ($cycle = 1; $cycle -le 3; $cycle++) {
    Wait-Log -AfterOffset $fogOffset -Pattern ('END_RIFT_FOG_SAFE_START.*cycle=' + $cycle + '/3') -WaitSeconds 180 -Action {
      Teleport-PlayersIfOutsideCombatArea -Core $core
      Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
    } | Out-Null
    Wait-Log -AfterOffset $fogOffset -Pattern ('END_RIFT_FOG_START.*cycle=' + $cycle + '/3') -WaitSeconds 90 -Action {
      Teleport-PlayersIfOutsideCombatArea -Core $core
      Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
    } | Out-Null
  }
  Wait-Log -AfterOffset $fogOffset -Pattern 'END_RIFT_FOG_COMPLETE.*cycles=3' -WaitSeconds 180 -Action {
    Teleport-PlayersIfOutsideCombatArea -Core $core
    Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
  } | Out-Null
  # FOG_COMPLETE and WAVE_COMPLETED are intentionally emitted by the same
  # server tick. Reuse the objective cursor or the completion marker can be
  # skipped before Wait-WaveComplete captures its default cursor.
  $waveFiveTransitionOffset = Wait-WaveComplete -Wave 5 -Core $core -Seconds 600 -AfterOffset $fogOffset -Action {
    Teleport-PlayersIfOutsideCombatArea -Core $core
    Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
  }
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=5 objective=BLACK_FOG cycles=3"
  if ($StopAfterWave -eq 5) { return }

  $pads = Get-Pads
  $waveSixStartOffset = Wait-Transition -CompletedWave 5 -Pads $pads -AfterOffset $waveFiveTransitionOffset
  $ritualOffset = $waveSixStartOffset
  $ritualPattern = 'WAVE6_RITUAL_SPHERE_READY.*casters=' + $ritualProfile.Casters `
    + '.*guards=' + $ritualProfile.Guards `
    + '.*projectiles=' + $ritualProfile.Projectiles `
    + '.*zones=' + $ritualProfile.Zones `
    + '.*authority=server'
  $ritualReady = Wait-Log -AfterOffset $ritualOffset `
    -Pattern $ritualPattern `
    -WaitSeconds 180 -Action {
      Teleport-PlayersIfOutsideCombatArea -Core $core
      Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
    }
  if ($ritualReady -match 'END_RIFT_RINGS_READY|WAVE_6_PAIR_SPAWNED') {
    throw 'Legacy Collapse Rings appeared in the live Wave 6 log.'
  }
  $ritualPrisonerName = $playerNames[0]
  Teleport-PlayersToCombatRing -Core $core
  Teleport-Player -Name $ritualPrisonerName -X ($core[0] + 0.5D) -Y $core[1] -Z ($core[2] + 0.5D)
  $prisonerCapture = Wait-Log -AfterOffset $ritualOffset `
    -Pattern 'WAVE6_RITUAL_PRISONER_CAPTURED[^\r\n]*player=[0-9a-fA-F-]{32,36}' -WaitSeconds 90 -Action {
    Teleport-PlayersIfOutsideCombatArea -Core $core
    Teleport-Player -Name $ritualPrisonerName -X ($core[0] + 0.5D) -Y $core[1] -Z ($core[2] + 0.5D)
  }
  if ($prisonerCapture -notmatch 'WAVE6_RITUAL_PRISONER_CAPTURED') {
    throw 'Wave 6 did not capture the player who entered the Ritual Sphere.'
  }
  # The Ritual Sphere ready/drain and Wave 6 completion can share a tick.
  # Keep the ritual cursor so the completion marker remains observable.
  $waveSixTransitionOffset = Wait-WaveComplete -Wave 6 -Core $core -Seconds 1200 -AfterOffset $ritualOffset -Action {
    Teleport-PlayersIfOutsideCombatArea -Core $core
    Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange
  }
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=6 objective=RITUAL_SPHERE casters=$($ritualProfile.Casters) guards=$($ritualProfile.Guards) projectiles=$($ritualProfile.Projectiles) zones=$($ritualProfile.Zones) prisoner_capture=true prisoner_drain=disabled"
  if ($StopAfterWave -eq 6) { return }

  $pads = Get-Pads
  $waveSevenStartOffset = Wait-Transition -CompletedWave 6 -Pads $pads -AfterOffset $waveSixTransitionOffset
  $chamberOffset = $waveSevenStartOffset
  Wait-Log -AfterOffset $chamberOffset -Pattern 'END_RIFT_CHAMBERS_ASSIGNED' -WaitSeconds 180 | Out-Null
  Teleport-PlayersToChambers
  foreach ($name in $playerNames) {
    Set-PlayerBotModeForName -Name $name -Mode 'ACTIVE_WAVE7'
  }
  Wait-Log -AfterOffset $chamberOffset -Pattern 'END_RIFT_CHAMBERS_COMPLETE' -WaitSeconds 900 -Action { Teleport-PlayersToNearestChamberMob } | Out-Null
  # Chamber completion and Wave 7 completion may share a tick as well.
  $waveSevenTransitionOffset = Wait-WaveComplete -Wave 7 -Core $core -Seconds 900 -AfterOffset $chamberOffset
  foreach ($name in $playerNames) {
    Set-PlayerBotModeForName -Name $name -Mode 'ACTIVE'
  }
  Write-Evidence "CURRENT_WAVE_PASS event=$eventId wave=7 objective=REALITY_SPLIT chambers=local"
  if ($StopAfterWave -eq 7) { return }

  # The 40-second timer and boss gateway share the Wave 7 completion cursor.
  $bossOffset = $waveSevenTransitionOffset
  Wait-Log -AfterOffset $bossOffset -Pattern 'END_RIFT_BOSS_GATEWAY_HANDOFF.*elapsed_ms=40000.*duration_ms=40000.*single_fire=true' -WaitSeconds 180 | Out-Null
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
  if ($null -eq $oldReflectDiagnostics) { Remove-Item Env:END_RIFT_REFLECT_DIAGNOSTICS -ErrorAction SilentlyContinue } else { $env:END_RIFT_REFLECT_DIAGNOSTICS = $oldReflectDiagnostics }
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
  foreach ($process in $processes) {
    $capture = if ($process) { $process.EndRiftOutputCapture } else { $null }
    if ($null -ne $capture) {
      try { $capture.StdoutTask.Wait(5000) | Out-Null } catch { }
      try { $capture.StderrTask.Wait(5000) | Out-Null } catch { }
      try { $capture.StdoutStream.Dispose() } catch { }
      try { $capture.StderrStream.Dispose() } catch { }
    }
  }
  try { Remove-LocalProbeWhitelistEntries } catch { Write-Warning $_.Exception.Message }
  try { Invoke-LocalRcon 'cmend wave clear' | Out-Null } catch { }
  try { Invoke-LocalRcon 'cmend boss kill cleanup' | Out-Null } catch { }
  if ($null -ne $originalCore -and $originalRequiredPlayers -ge 2 -and $originalRequiredPlayers -le 20) {
    try {
      Invoke-LocalRcon 'cmend core remove confirm' | Out-Null
      Invoke-LocalRcon ("cmend core setat $($originalCore[0]) $($originalCore[1]) $($originalCore[2]) $originalRequiredPlayers") | Out-Null
    } catch { Write-Warning "Local event configuration restore failed: $($_.Exception.Message)" }
  }
}
