[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'RiftSpellTest',
  [int]$BotDurationSeconds = 120
)

# Local-only spell/music smoke. It talks to the isolated Paper RCON endpoint,
# never to the production address, and removes only disposable test entities.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLog = Join-Path $runtimeRoot 'spell-matrix-live-bot.log'
$botErr = Join-Path $runtimeRoot 'spell-matrix-live-bot.err.log'

$config = Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw
if ($config -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: spell matrix requires environment: local.'
}
if (-not (Test-Path -LiteralPath $serverDir -PathType Container)) {
  throw "Isolated Paper directory is missing: $serverDir"
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local protocol bot is missing: $botScript"
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

function Get-LogLength {
  return ([string](Get-Content -LiteralPath $paperLog -Raw)).Length
}

function Get-NewLogText {
  param([Parameter(Mandatory = $true)][int64]$PreviousLength)
  $current = [string](Get-Content -LiteralPath $paperLog -Raw)
  if ($current.Length -le $PreviousLength) {
    return ''
  }
  return $current.Substring($PreviousLength)
}

function Get-CoreCoordinates {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) {
    throw "Core coordinates were not exposed by local status:`n$Status"
  }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Wait-LocalBot {
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    if ((Invoke-LocalRcon -CommandText 'list') -match [Regex]::Escape($BotName)) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Local spell-matrix bot did not join in time: $BotName"
}

function Wait-LogMarker {
  param(
    [Parameter(Mandatory = $true)][int64]$PreviousLength,
    [Parameter(Mandatory = $true)][string]$Pattern,
    [int]$TimeoutSeconds = 8
  )
  for ($attempt = 0; $attempt -lt ($TimeoutSeconds * 4); $attempt++) {
    $delta = Get-NewLogText -PreviousLength $PreviousLength
    if ($delta -match $Pattern) {
      return $delta
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for local log marker '$Pattern'."
}

function Assert-BossSpell {
  param(
    [Parameter(Mandatory = $true)][string]$Spell,
    [Parameter(Mandatory = $true)][int64]$PreviousLength
  )
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "BOSS_SPELL_TELEGRAPH.*spell=$Spell" -TimeoutSeconds 5
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "BOSS_SPELL_FLIGHT.*spell=$Spell" -TimeoutSeconds 5
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "BOSS_SPELL_CAST.*spell=$Spell" -TimeoutSeconds 5
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "SPELL_IMPACT_VISUAL spell=$Spell" -TimeoutSeconds 5
  Write-Output "LIVE_SPELL_PASS spell=$Spell telegraph=1 flight=1 cast=1 impact=1"
}

$botProcess = $null
$stdoutTask = $null
$stderrTask = $null
try {
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $core = Get-CoreCoordinates (Invoke-LocalRcon -CommandText 'cmend status')
  $matrixLogStart = Get-LogLength

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $node
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if ($null -ne $startInfo.ArgumentList) {
    $startInfo.ArgumentList.Add($botScript)
    $startInfo.ArgumentList.Add($BotName)
    $startInfo.ArgumentList.Add(([string]($BotDurationSeconds * 1000)))
  } else {
    $startInfo.Arguments = '"' + $botScript + '" ' + $BotName + ' ' + ([string]($BotDurationSeconds * 1000))
  }
  $botProcess = [Diagnostics.Process]::new()
  $botProcess.StartInfo = $startInfo
  $botProcess.Start() | Out-Null
  $stdoutTask = $botProcess.StandardOutput.ReadToEndAsync()
  $stderrTask = $botProcess.StandardError.ReadToEndAsync()
  Wait-LocalBot
  $null = Invoke-LocalRcon -CommandText ("gamemode survival $BotName")
  # Keep the disposable probe alive long enough to observe every spell.  This
  # changes only the temporary local test player, never event configuration or
  # persistent player data on a production server.
  $null = Invoke-LocalRcon -CommandText ("attribute $BotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon -CommandText ("heal $BotName")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("tp $BotName $($core[0] + 6) $($core[1]) $($core[2]) 180 0")
  Start-Sleep -Seconds 1

  # Manual phase playback is explicitly local-only. Outside combat it must
  # produce only the TEST marker; no automatic event track may start.
  $phaseKeys = @(
    'wave-1', 'wave-2', 'wave-3', 'wave-4', 'wave-5',
    'intermission-1', 'intermission-2', 'intermission-3', 'intermission-4',
    'boss-cinematic', 'final-drain', 'final-ritual', 'final-wave', 'boss-finish'
  )
  foreach ($key in $phaseKeys) {
    $before = Get-LogLength
    # The RCON response crosses two PowerShell processes and can be decoded
    # with the host code page.  Use the plugin's UTF-8 log marker as the
    # authoritative result instead of comparing localized chat text.
    $null = Invoke-LocalRcon -CommandText ("cmend test music $key $BotName")
    $delta = Wait-LogMarker -PreviousLength $before `
      -Pattern "END_EVENT_MUSIC_TEST.*track=copimine:end_rift/$($key.Replace('-', '_'))" -TimeoutSeconds 3
    if ($delta -match 'END_EVENT_MUSIC track=') {
      throw "Automatic event music started during manual phase test $key."
    }
    Write-Output "LIVE_MUSIC_PASS phase=$key manual_test=1 automatic_outside_event=0"
  }

  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn'
  Start-Sleep -Seconds 1
  foreach ($spell in @(
    'void_blast', 'rift_projectile', 'rift_arrows', 'void_mark',
    'summon_servants', 'arena_inferno'
  )) {
    $before = Get-LogLength
    $response = Invoke-LocalRcon -CommandText ("cmend boss spell $spell")
    if ($response -match '(?i)refused|missing|not created') {
      throw "Boss spell $spell was refused:`n$response"
    }
    Assert-BossSpell -Spell $spell -PreviousLength $before
    Start-Sleep -Milliseconds 250
  }
  $before = Get-LogLength
  $response = Invoke-LocalRcon -CommandText ("cmend boss spell control_reverse $BotName")
  if ($response -match '(?i)refused|missing') {
    throw "Boss control spell was refused:`n$response"
  }
  Assert-BossSpell -Spell 'will_distortion' -PreviousLength $before

  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $beforeMini = Get-LogLength
  $response = Invoke-LocalRcon -CommandText 'cmend test wave 3'
  if ($response -match '(?i)refused|missing|event world') {
    throw "Mini-boss spell wave was refused:`n$response"
  }
  # Wave III has four stable elite spell assignments. The first casts are
  # scheduled immediately; allow a small bounded window for all telegraphs and
  # flights, never an unbounded sleep.
  foreach ($spell in @('rift_step', 'void_snare', 'echo_pulse', 'arrow_salvo')) {
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "MINIBOSS_SPELL_CAST.*spell=$spell" -TimeoutSeconds 22 | Out-Null
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "MINIBOSS_SPELL_FLIGHT.*spell=$spell" -TimeoutSeconds 22 | Out-Null
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "SPELL_IMPACT_VISUAL spell=$spell" -TimeoutSeconds 22 | Out-Null
    Write-Output "LIVE_MINIBOSS_SPELL_PASS spell=$spell telegraph_flight_cast_impact=1"
  }
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  $mobMatch = [Regex]::Match($status, 'event-mobs=.*?(\d+)')
  if (-not $mobMatch.Success -or [int]$mobMatch.Groups[1].Value -ne 0) {
    throw "Disposable spell-matrix wave cleanup left event mobs:`n$status"
  }
  $matrixLogDelta = Get-NewLogText -PreviousLength $matrixLogStart
  if ($matrixLogDelta -match 'generated an exception') {
    throw "Spell matrix produced a Paper task exception:`n$matrixLogDelta"
  }
  Write-Output 'LIVE_SPELL_MATRIX_PASS boss=7 mini=4 music_phases=14 cleanup=1'
} finally {
  try { Invoke-LocalRcon -CommandText 'cmend wave clear' | Out-Null } catch { }
  try { Invoke-LocalRcon -CommandText 'cmend boss kill cleanup' | Out-Null } catch { }
  if ($botProcess) {
    if (-not $botProcess.HasExited) {
      try { $botProcess.Kill() } catch { }
      $botProcess.WaitForExit(5000) | Out-Null
    }
    if ($stdoutTask) {
      try { [IO.File]::WriteAllText($botLog, $stdoutTask.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { }
    }
    if ($stderrTask) {
      try { [IO.File]::WriteAllText($botErr, $stderrTask.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { }
    }
  }
}
