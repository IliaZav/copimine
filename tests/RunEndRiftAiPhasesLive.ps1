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
$process = $null

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

function Get-Core([string]$Status) {
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core is missing from status: $Status" }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Wait-Online {
  for ($attempt = 0; $attempt -lt 80; $attempt++) {
    if ((Invoke-LocalRcon 'list') -match [Regex]::Escape($BotName)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "AI probe player did not join: $BotName"
}

function Assert-AiDiagnostics {
  param([string]$Label, [int]$MinimumMobile = 1)
  $raw = (Invoke-LocalRcon 'cmend debug ai') -replace '\u00A7.', ''
  $match = [Regex]::Match($raw, 'AI_DIAGNOSTICS.*mobile=(\d+)\s+aiEnabled=(\d+)\s+targeted=(\d+)\s+coreObjective=(\d+)\s+outside=(\d+)\s+onCore=(\d+)\s+bossCast=([A-Z_]+)')
  if (-not $match.Success) { throw "$Label did not expose AI diagnostics: $raw" }
  $mobile = [int]$match.Groups[1].Value
  $enabled = [int]$match.Groups[2].Value
  $outside = [int]$match.Groups[5].Value
  $onCore = [int]$match.Groups[6].Value
  if ($mobile -lt $MinimumMobile -or $enabled -ne $mobile -or $outside -ne 0 -or $onCore -ne 0) {
    throw "$Label violated bounded AI diagnostics: $raw"
  }
  Write-Output "LIVE_CURRENT_AI_DIAGNOSTICS label=$Label mobile=$mobile enabled=$enabled outside=$outside on_core=$onCore"
}

function Start-ProbeBot([int[]]$Core) {
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $out = Join-Path $runtimeRoot 'ai-current-bot.log'
  $err = Join-Path $runtimeRoot 'ai-current-bot.err.log'
  $args = '"' + $botScript + '" ' + $BotName + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 0.5D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 900'
  return Start-Process -FilePath $node -ArgumentList $args -WorkingDirectory $root -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
}

try {
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
  Start-Sleep -Seconds 2

  foreach ($wave in 1..7) {
    $null = Invoke-LocalRcon 'cmend wave clear'
    $offset = Log-Length
    $response = Invoke-LocalRcon ("cmend test wave $wave")
    if ($response -match '(?i)refused|missing|event world') { throw "Wave $wave test refused: $response" }
    Wait-Log -Pattern ('WAVE_TEST_STARTED.*wave=' + $wave + '\b') -AfterOffset $offset -Seconds 15 | Out-Null
    if ($wave -eq 4) {
      # Wave 4 deliberately telegraphs the physical obelisks before its
      # pressure pack is released.  A fixed three-second sleep races the
      # 70-tick emergence timeline and can sample READY_FOR_PLAYERS with no
      # mobile entities even though the wave is healthy.  Wait for the
      # production marker emitted when the one-shot pack is actually started.
      Wait-Log -Pattern 'END_RIFT_OBELISK_MOBS_STARTED' -AfterOffset $offset -Seconds 15 | Out-Null
    } else {
      Start-Sleep -Seconds 3
    }
    Assert-AiDiagnostics -Label ("wave-$wave")
    $delta = Log-Tail $offset
    if ($delta -notmatch 'WAVE_AI_TARGET|WAVE_AI_PATH|WAVE_AI_TACTIC|WAVE_SKELETON_BEHAVIOR|END_RIFT_OBELISK_ACTIVE|WAVE_6_PAIR_SPAWNED|END_RIFT_CHAMBERS_ASSIGNED') {
      throw "Wave $wave did not emit a current AI decision marker."
    }
    Write-Output "LIVE_CURRENT_WAVE_AI_PASS wave=$wave decision_marker=1 bounded=1"
  }

  $null = Invoke-LocalRcon 'cmend wave clear'
  $offset = Log-Length
  $response = Invoke-LocalRcon 'cmend test ai'
  if ($response -match '(?i)refused|missing|event world') { throw "Current AI test refused: $response" }
  Wait-Log -Pattern 'TEST_AI_STARTED' -AfterOffset $offset -Seconds 20 | Out-Null
  Wait-Log -Pattern 'BOSS_AI_TARGET|BOSS_BRAIN_DECISION|WAVE_AI_TARGET' -AfterOffset $offset -Seconds 20 | Out-Null
  Assert-AiDiagnostics -Label 'boss-brain' -MinimumMobile 1
  $aiText = (Invoke-LocalRcon 'cmend debug ai') -replace '\u00A7.', ''
  if ($aiText -notmatch 'AI_PROFILE.*stage=AWAKENING.*abilityState=NONE') {
    throw "Current boss profile was not exposed: $aiText"
  }
  Write-Output 'LIVE_CURRENT_BOSS_AI_PASS target_selection=1 brain_decision=1 profile=AWAKENING'

  foreach ($kind in @('wave', 'boss')) {
    $offset = Log-Length
    $response = Invoke-LocalRcon ("cmend test teleport $kind")
    if ($response -match 'no_entity') { throw "Teleport guard had no $kind entity: $response" }
    $guard = Wait-Log -Pattern ('TEST_TELEPORT_GUARD.*kind=' + $kind) -AfterOffset $offset -Seconds 10
    if ($guard -notmatch 'outside=false') { throw "Teleport guard allowed an out-of-bounds ${kind}: $guard" }
    Write-Output "LIVE_CURRENT_TELEPORT_GUARD_PASS kind=$kind outside=false"
  }
  Write-Output 'LIVE_CURRENT_AI_PASS waves=1,2,3,4,5,6,7 boss_phases=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL teleport_guards=2'
}
finally {
  try { Invoke-LocalRcon 'cmend wave clear' | Out-Null } catch { }
  try { Invoke-LocalRcon 'cmend boss kill cleanup' | Out-Null } catch { }
  if ($process -and -not $process.HasExited) {
    try { $process.Kill() } catch { }
    try { $process.WaitForExit(5000) | Out-Null } catch { }
  }
}
