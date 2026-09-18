[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftAiProbe',
  [ValidateRange(30, 600)]
  [int]$BotDurationSeconds = 180,
  [ValidateRange(10, 600)]
  [int]$TimeoutSeconds = 60
)

# Local AI smoke. It samples every current wave adapter, the current boss
# brain, bounded target selection and the teleport containment listener. The
# probe never starts a second server and never writes a production endpoint.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$controlDirectory = Join-Path $runtimeRoot 'ai-current-bot-control'
$controlFile = Join-Path $controlDirectory ($BotName + '.mode')
$botOutput = Join-Path $runtimeRoot 'ai-current-bot.log'
$process = $null
$cleanupFailures = [System.Collections.Generic.List[string]]::new()

$config = Get-Content -LiteralPath $configPath -Raw
if ($config -notmatch '(?m)^\s*schema-version:\s*4\s*$' -or
    $config -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: AI smoke requires the current local configuration.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused Git branch '$branch'."
}
$properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Refused: AI smoke requires isolated local ports.'
}
foreach ($path in @($serverDir, $rconScript, $botScript, $paperLog)) {
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing local AI probe input: $path" }
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "Local RCON failed: $CommandText $result" }
  return $result.Trim()
}

function Read-Log { return [string](Get-Content -LiteralPath $paperLog -Raw) }
function Log-Length { return [int64](Read-Log).Length }
function Log-Tail([int64]$Offset) {
  $text = Read-Log
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}

function Wait-Log {
  param([string]$Pattern, [int64]$AfterOffset, [int]$Seconds = $TimeoutSeconds)
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Log-Tail $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'."
}

function Wait-Until {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Condition,
    [Parameter(Mandatory = $true)][string]$Description,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $result = & $Condition
      if ($result) { return $result }
    } catch {
      # A state query can race entity spawn/despawn.  The condition remains
      # fail-closed and the last error is reported by the timeout.
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for condition: $Description"
}

function Get-AiJson {
  $raw = (Invoke-LocalRcon 'cmend debug ai --json') -replace '\u00A7.', ''
  try {
    $snapshot = $raw | ConvertFrom-Json -ErrorAction Stop
  } catch {
    throw "Structured AI diagnostics were not valid JSON: $raw"
  }
  if ($snapshot.type -ne 'END_RIFT_AI_DIAGNOSTICS') {
    throw "Structured AI diagnostics had an unexpected type: $raw"
  }
  return $snapshot
}

function Get-Core([string]$Status) {
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core is missing from status: $Status" }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Wait-Online {
  Wait-Until -Description "AI probe player online: $BotName" -Seconds 45 -Condition {
    return (Invoke-LocalRcon 'list') -match [Regex]::Escape($BotName)
  } | Out-Null
}

function Assert-AiDiagnostics {
  param(
    [string]$Label,
    [int]$MinimumMobile = 1,
    [int]$ExpectedCasterCount = 0,
    [string]$ExpectedCasterState = ''
  )
  $snapshot = Get-AiJson
  $casters = if ($null -eq $snapshot.casters) { @() } else { @($snapshot.casters) }
  $mobile = [int]$snapshot.mobile
  $enabled = [int]$snapshot.aiEnabled
  $outside = [int]$snapshot.outside
  $onCore = [int]$snapshot.onCore
  $casterCount = [int]$snapshot.ritualCasters
  $passiveCasters = @($casters | Where-Object { -not [bool]$_.nativeAiEnabled }).Count
  $casterTargets = [int]$snapshot.ritualCastersTargeted
  $ritualGuards = [int]$snapshot.ritualGuards
  $expectedEnabled = $mobile
  if ($ExpectedCasterCount -gt 0) {
    if ($casterCount -ne $ExpectedCasterCount -or $casters.Count -ne $ExpectedCasterCount) {
      throw "$Label expected $ExpectedCasterCount complete caster records: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
    }
    if (-not [bool]$snapshot.ritualGuardOwnershipValid) {
      throw "$Label reported invalid caster/guard ownership: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
    }
    if ($ExpectedCasterState -and @($casters | Where-Object { $_.state -ne $ExpectedCasterState }).Count -gt 0) {
      throw "$Label did not report caster state ${ExpectedCasterState}: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
    }
    if ($ExpectedCasterState -eq 'GUARDED_CASTING' -and
        @($casters | Where-Object { [bool]$_.nativeAiEnabled -or [bool]$_.canTargetPlayers -or $null -ne $_.target }).Count -gt 0) {
      throw "$Label guarded casters were not passive and target-free: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
    }
    $expectedEnabled = $mobile - $passiveCasters
  }
  if ($mobile -lt $MinimumMobile -or $enabled -ne $expectedEnabled -or $outside -ne 0 -or $onCore -ne 0) {
    throw "$Label violated bounded AI diagnostics: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
  }
  Write-Output "LIVE_CURRENT_AI_DIAGNOSTICS label=$Label mobile=$mobile enabled=$enabled ritual_casters=$casterCount passive_casters=$passiveCasters caster_targets=$casterTargets guards=$ritualGuards expected_enabled=$expectedEnabled ownership=$([bool]$snapshot.ritualGuardOwnershipValid) outside=$outside on_core=$onCore"
  return $snapshot
}

function Start-ProbeBot([int[]]$Core) {
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $err = Join-Path $runtimeRoot 'ai-current-bot.err.log'
  $args = '"' + $botScript + '" ' + $BotName + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 0.5D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 900 "' + $controlDirectory + '"'
  return Start-Process -FilePath $node -ArgumentList $args -WorkingDirectory $root -RedirectStandardOutput $botOutput -RedirectStandardError $err -WindowStyle Hidden -PassThru
}

function Set-ProbeBotMode([ValidateSet('PASSIVE', 'ACTIVE', 'ACTIVE_WAVE7')][string]$Mode) {
  if (-not (Test-Path -LiteralPath $controlDirectory)) {
    New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null
  }
  Set-Content -LiteralPath $controlFile -Value $Mode -NoNewline -Encoding ascii
  Wait-Until -Description "AI probe bot mode $Mode" -Seconds 15 -Condition {
    if (-not (Test-Path -LiteralPath $botOutput)) { return $false }
    $botLog = Get-Content -LiteralPath $botOutput -Raw -ErrorAction SilentlyContinue
    return $botLog -match ("BOT_" + [Regex]::Escape($Mode) + " " + [Regex]::Escape($BotName))
  } | Out-Null
}

try {
  if (-not (Test-Path -LiteralPath $controlDirectory)) {
    New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null
  }
  Set-Content -LiteralPath $controlFile -Value 'ACTIVE' -NoNewline -Encoding ascii
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  $core = Get-Core $status
  $process = Start-ProbeBot $core
  Wait-Online
  $null = Invoke-LocalRcon ("gamemode survival $BotName")
  $null = Invoke-LocalRcon ("attribute $BotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon ("effect give $BotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon ("effect give $BotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon ("tp $BotName $($core[0] + 6) $($core[1]) $($core[2]) 180 0")

  foreach ($wave in 1..7) {
    $null = Invoke-LocalRcon 'cmend wave clear'
    $offset = Log-Length
    $response = Invoke-LocalRcon ("cmend test wave $wave")
    if ($response -match '(?i)refused|missing|event world') { throw "Wave $wave test refused: $response" }
    Wait-Log -Pattern ('WAVE_TEST_STARTED.*wave=' + $wave + '\b') -AfterOffset $offset -Seconds 15 | Out-Null
    if ($wave -eq 4) {
      Wait-Log -Pattern 'END_RIFT_OBELISK_MOBS_STARTED' -AfterOffset $offset -Seconds 15 | Out-Null
    }
    if ($wave -eq 6) {
      Wait-Until -Description 'Wave 6 guarded caster diagnostics' -Condition {
        try {
          $snapshot = Get-AiJson
          $casters = if ($null -eq $snapshot.casters) { @() } else { @($snapshot.casters) }
          return $casters.Count -eq 4 -and [int]$snapshot.ritualGuards -eq 12 `
            -and [bool]$snapshot.ritualGuardOwnershipValid `
            -and @($casters | Where-Object { $_.state -ne 'GUARDED_CASTING' }).Count -eq 0
        } catch {
          return $false
        }
      } | Out-Null
      Assert-AiDiagnostics -Label ("wave-$wave") -ExpectedCasterCount 4 `
        -ExpectedCasterState 'GUARDED_CASTING' | Out-Null
      Write-Output 'LIVE_W6_CASTER_GUARDED_PASS casters=4 guards=12 passive=true target=none ownership=true'
    } else {
      Wait-Until -Description ("Wave $wave AI diagnostics") -Condition {
        try { return [int](Get-AiJson).mobile -ge 1 } catch { return $false }
      } | Out-Null
      Assert-AiDiagnostics -Label ("wave-$wave") | Out-Null
    }
    $delta = Log-Tail $offset
    if ($delta -notmatch 'WAVE_AI_TARGET|WAVE_AI_PATH|WAVE_AI_TACTIC|WAVE_SKELETON_BEHAVIOR|END_RIFT_OBELISK_ACTIVE|WAVE_6_PAIR_SPAWNED|END_RIFT_CHAMBERS_ASSIGNED') {
      throw "Wave $wave did not emit a current AI decision marker."
    }
    Write-Output "LIVE_CURRENT_WAVE_AI_PASS wave=$wave decision_marker=1 bounded=1"
  }

  Set-ProbeBotMode -Mode PASSIVE
  $null = Invoke-LocalRcon 'cmend wave clear'
  $offset = Log-Length
  $response = Invoke-LocalRcon 'cmend test ai'
  if ($response -match '(?i)refused|missing|event world') { throw "Current AI test refused: $response" }
  Wait-Log -Pattern 'TEST_AI_STARTED' -AfterOffset $offset -Seconds 20 | Out-Null
  Wait-Log -Pattern 'BOSS_AI_TARGET|BOSS_BRAIN_DECISION|WAVE_AI_TARGET' -AfterOffset $offset -Seconds 20 | Out-Null
  Wait-Until -Description 'test boss AI diagnostics' -Condition {
    try {
      $snapshot = Get-AiJson
      return [int]$snapshot.mobile -ge 1 -and [bool]$snapshot.bossPresent
    } catch { return $false }
  } | Out-Null
  Assert-AiDiagnostics -Label 'boss-brain' -MinimumMobile 1 | Out-Null
  $aiText = (Invoke-LocalRcon 'cmend debug ai') -replace '\u00A7.', ''
  if ($aiText -notmatch 'AI_PROFILE.*stage=AWAKENING.*abilityState=NONE') {
    throw "Current boss profile was not exposed: $aiText"
  }
  Write-Output 'LIVE_CURRENT_BOSS_AI_PASS target_selection=1 brain_decision=1 profile=AWAKENING'

  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend test wave 6'
  Wait-Until -Description 'Wave 6 lifecycle fixture' -Condition {
    try {
      $snapshot = Get-AiJson
      return [int]$snapshot.ritualCasters -eq 4 -and [int]$snapshot.ritualGuards -eq 12
    } catch { return $false }
  } | Out-Null
  $guardedResponse = Invoke-LocalRcon 'cmend test ritual caster guarded'
  if ($guardedResponse -notmatch '(?i)state=guarded') {
    throw "Guarded caster test hook was not acknowledged: $guardedResponse"
  }
  Wait-Until -Description 'guarded caster state' -Condition {
    try { return @((Get-AiJson).casters | Where-Object { $_.state -eq 'GUARDED_CASTING' }).Count -eq 4 } catch { return $false }
  } | Out-Null
  Write-Output 'LIVE_W6_CASTER_GUARDED_PASS casters=4 guards=12 passive=true target=none ownership=true'

  $exposedResponse = Invoke-LocalRcon 'cmend test ritual caster exposed'
  if ($exposedResponse -notmatch '(?i)state=exposed') {
    throw "Exposed caster test hook was not acknowledged: $exposedResponse"
  }
  Wait-Until -Description 'exposed caster state' -Condition {
    try {
      $casters = @((Get-AiJson).casters)
      return @($casters | Where-Object { $_.state -eq 'EXPOSED_CASTING' -and [int]$_.guardCount -eq 0 `
          -and -not [bool]$_.nativeAiEnabled -and $null -eq $_.target }).Count -eq 1
    } catch { return $false }
  } | Out-Null
  Write-Output 'LIVE_W6_CASTER_EXPOSED_PASS caster_count=1 guards=0 passive=true target=none'

  $awakenedResponse = Invoke-LocalRcon 'cmend test ritual caster awakened'
  if ($awakenedResponse -notmatch '(?i)state=awakened') {
    throw "Awakened caster test hook was not acknowledged: $awakenedResponse"
  }
  Wait-Until -Description 'awakened caster state' -Condition {
    try {
      $snapshot = Get-AiJson
      $casters = @($snapshot.casters)
      return @($casters | Where-Object { $_.state -eq 'AWAKENED_ATTACKING' `
          -and [bool]$_.nativeAiEnabled -and [bool]$_.canTargetPlayers }).Count -eq 1
    } catch { return $false }
  } | Out-Null
  $mixedSnapshot = Assert-AiDiagnostics -Label 'wave-6-mixed-caster-state' -MinimumMobile 1 -ExpectedCasterCount 4
  if (@($mixedSnapshot.casters | Where-Object { $_.state -eq 'AWAKENED_ATTACKING' }).Count -ne 1) {
    throw "Mixed Wave 6 diagnostics did not retain exactly one awakened caster."
  }
  Write-Output 'LIVE_W6_CASTER_AWAKENED_PASS caster_count=1 native_ai=true target_allowed=true mixed_state=true'

  $phaseCommands = @('awakening', 'hunt', 'rift', 'overload', 'rage', 'last_seal')
  foreach ($phaseCommand in $phaseCommands) {
    $expectedStage = $phaseCommand.ToUpperInvariant()
    $phaseOffset = Log-Length
    $phaseResponse = Invoke-LocalRcon ("cmend boss phase $phaseCommand")
    if ($phaseResponse -notmatch '(?i)Boss phase') {
      throw "Boss phase command was not acknowledged for ${phaseCommand}: $phaseResponse"
    }
    Wait-Until -Description ("boss phase $expectedStage") -Condition {
      try { return (Get-AiJson).stage -eq $expectedStage } catch { return $false }
    } | Out-Null
    $phaseText = (Invoke-LocalRcon 'cmend debug ai') -replace '\u00A7.', ''
    if ($phaseText -notmatch ("AI_PROFILE.*stage=" + [Regex]::Escape($expectedStage))) {
      throw "Boss phase profile was not exposed for ${expectedStage}: $phaseText"
    }
    $phaseMarker = Wait-Log -Pattern ('TEST_BOSS_STAGE_TRANSITION.*to=' + $expectedStage) `
      -AfterOffset $phaseOffset -Seconds 10
    if ($phaseMarker -notmatch ('to=' + [Regex]::Escape($expectedStage))) {
      throw "Boss phase transition marker was incomplete for ${expectedStage}: $phaseMarker"
    }
    Write-Output "LIVE_CURRENT_BOSS_PHASE_PASS phase=$expectedStage profile=true transition=true"
  }

  foreach ($kind in @('wave', 'boss')) {
    $offset = Log-Length
    $response = Invoke-LocalRcon ("cmend test teleport $kind")
    if ($response -match 'no_entity') { throw "Teleport guard had no $kind entity: $response" }
    $guard = Wait-Log -Pattern ('TEST_TELEPORT_GUARD.*kind=' + $kind) -AfterOffset $offset -Seconds 10
    if ($guard -notmatch 'outside=false') { throw "Teleport guard allowed an out-of-bounds ${kind}: $guard" }
    Write-Output "LIVE_CURRENT_TELEPORT_GUARD_PASS kind=$kind outside=false"
  }
  Write-Output 'LIVE_CURRENT_AI_PASS waves=1,2,3,4,5,6,7 boss_phases_verified=6 caster_lifecycle_verified=3 teleport_guards=2'
}
finally {
  try {
    Invoke-LocalRcon 'cmend wave clear' | Out-Null
  } catch {
    $cleanupFailures.Add('cmend wave clear: ' + $_.Exception.Message)
  }
  try {
    Invoke-LocalRcon 'cmend boss kill cleanup' | Out-Null
  } catch {
    $cleanupFailures.Add('cmend boss kill cleanup: ' + $_.Exception.Message)
  }
  if ($process -and -not $process.HasExited) {
    try {
      $process.Kill()
    } catch {
      $cleanupFailures.Add('AI probe bot kill: ' + $_.Exception.Message)
    }
    try {
      $process.WaitForExit(5000) | Out-Null
    } catch {
      $cleanupFailures.Add('AI probe bot wait: ' + $_.Exception.Message)
    }
  }
  try {
    $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
    $snapshot = Get-AiJson
    $objectives = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
    if ($status -notmatch 'event-mobs=\s*0' -or [int]$snapshot.mobile -ne 0 `
        -or [bool]$snapshot.bossPresent -or [int]$snapshot.ritualCasters -ne 0 `
        -or [int]$snapshot.ritualGuards -ne 0 -or $objectives -notmatch 'visuals=\s*0') {
      throw "cleanup residue status=$status ai=$($snapshot | ConvertTo-Json -Depth 12 -Compress) objectives=$objectives"
    }
    Write-Output 'LIVE_CURRENT_CLEANUP_PASS event_mobs=0 mobile=0 boss=false casters=0 guards=0 visuals=0'
  } catch {
    $cleanupFailures.Add('cleanup zero-state query: ' + $_.Exception.Message)
  }
  if ($cleanupFailures.Count -gt 0) {
    throw ('AI probe cleanup failed: ' + ($cleanupFailures -join ' | '))
  }
}
