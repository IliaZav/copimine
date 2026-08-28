[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftAiPhases',
  [int]$BotDurationSeconds = 100
)

# Local-only Paper smoke. It creates disposable event entities in the isolated
# runtime and removes only those entities in finally; it never touches the
# production server, launcher, website, world reset, or PostgreSQL data.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLog = Join-Path $runtimeRoot 'ai-phases-live-bot.log'
$botErr = Join-Path $runtimeRoot 'ai-phases-live-bot.err.log'

if ((Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: AI phase smoke requires environment: local.'
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

function Get-AiDiagnostics {
  param([Parameter(Mandatory = $true)][string]$Text)
  $match = [Regex]::Match($Text, 'AI_DIAGNOSTICS.*stage=([A-Z_]+).*mobile=(\d+)\s+aiEnabled=(\d+)\s+targeted=(\d+)\s+outside=(\d+)\s+onCore=(\d+)')
  if (-not $match.Success) {
    throw "AI diagnostics did not contain a complete controller snapshot:`n$Text"
  }
  return [pscustomobject]@{
    Mobile = [int]$match.Groups[2].Value
    AiEnabled = [int]$match.Groups[3].Value
    Targeted = [int]$match.Groups[4].Value
    Outside = [int]$match.Groups[5].Value
    OnCore = [int]$match.Groups[6].Value
    Stage = $match.Groups[1].Value
    Raw = $Text
  }
}

function Get-BossUuid {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) {
    throw "Boss UUID was not exposed by local status:`n$Status"
  }
  return $match.Groups[1].Value
}

function Get-CoreCoordinates {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) {
    throw "Core coordinates were not exposed by local status:`n$Status"
  }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Assert-AiSnapshot {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$Text,
    [int]$MinimumMobile = 1,
    [string]$ExpectedStage = ''
  )
  $snapshot = Get-AiDiagnostics $Text
  if (($snapshot.Mobile -lt $MinimumMobile) -or
      ($snapshot.AiEnabled -ne $snapshot.Mobile) -or
      ($snapshot.Targeted -lt 1) -or
      ($snapshot.Outside -ne 0) -or
      ($snapshot.OnCore -ne 0)) {
    throw "$Label failed bounded AI invariant:`n$($snapshot.Raw)"
  }
  if (-not [string]::IsNullOrWhiteSpace($ExpectedStage) -and $snapshot.Stage -ne $ExpectedStage) {
    throw "$Label expected stage $ExpectedStage but received $($snapshot.Stage):`n$($snapshot.Raw)"
  }
  Write-Output "LIVE_AI_PASS label=$Label mobile=$($snapshot.Mobile) targeted=$($snapshot.Targeted) outside=$($snapshot.Outside) onCore=$($snapshot.OnCore) stage=$($snapshot.Stage)"
  return $snapshot
}

function Wait-LocalBot {
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if ($list -match [Regex]::Escape($BotName)) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Local AI phase bot did not join in time: $BotName"
}

$botProcess = $null
$stdoutTask = $null
$stderrTask = $null
$bossUuid = $null
try {
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $initialStatus = Invoke-LocalRcon -CommandText 'cmend status'
  $core = Get-CoreCoordinates $initialStatus

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
  $null = Invoke-LocalRcon -CommandText ("attribute $BotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon -CommandText ("heal $BotName")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("tp $BotName $($core[0] + 6) $($core[1]) $($core[2]) 180 0")
  Start-Sleep -Seconds 2

  foreach ($wave in 1..5) {
    $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
    $response = Invoke-LocalRcon -CommandText ("cmend test wave $wave")
    if ($response -match '(?i)not created|refused|event world') {
      throw "Local test wave $wave was refused:`n$response"
    }
    Start-Sleep -Seconds 3
    Assert-AiSnapshot -Label ("wave-$wave") -Text (Invoke-LocalRcon -CommandText 'cmend debug ai') | Out-Null
  }
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $response = Invoke-LocalRcon -CommandText 'cmend test wave final'
  if ($response -match '(?i)not created|refused|event world') {
    throw "Local final test wave was refused:`n$response"
  }
  Start-Sleep -Seconds 3
  Assert-AiSnapshot -Label 'final-wave' -Text (Invoke-LocalRcon -CommandText 'cmend debug ai') | Out-Null
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'

  $response = Invoke-LocalRcon -CommandText 'cmend boss spawn'
  if ($response -match '(?i)missing|refused|core') {
    throw "Local test boss was refused:`n$response"
  }
  $bossUuid = Get-BossUuid (Invoke-LocalRcon -CommandText 'cmend status')
  Start-Sleep -Seconds 2
  Assert-AiSnapshot -Label 'boss-awakening' -Text (Invoke-LocalRcon -CommandText 'cmend debug ai') -ExpectedStage 'AWAKENING' | Out-Null

  $bossStageChecks = @(
    # The live sequence intentionally exercises both command forms:
    # cmend boss damage 500 and cmend boss damage 250.
    @{ Label = 'boss-hunter'; Damage = 500; Expected = 'HUNTER'; Wait = 1 },
    @{ Label = 'boss-distortion'; Damage = 500; Expected = 'DISTORTION'; Wait = 1 },
    @{ Label = 'boss-half-threshold'; Damage = 250; Expected = 'DISTORTION'; Wait = 1 },
    @{ Label = 'boss-absorption'; Damage = 250; Expected = 'ABSORPTION'; Wait = 6 },
    @{ Label = 'boss-catastrophe'; Damage = 500; Expected = 'CATASTROPHE'; Wait = 1 },
    @{ Label = 'boss-judgment'; Damage = 250; Expected = 'CATASTROPHE'; Wait = 1 }
  )
  foreach ($check in $bossStageChecks) {
    $null = Invoke-LocalRcon -CommandText ("cmend boss damage $($check.Damage)")
    Start-Sleep -Seconds $check.Wait
    $debug = Invoke-LocalRcon -CommandText 'cmend debug ai'
    $snapshot = Assert-AiSnapshot -Label $check.Label -Text $debug -ExpectedStage $check.Expected
    if ($check.Label -eq 'boss-judgment' -and $debug -notmatch 'bossCast=JUDGMENT_CAST') {
      throw "Judgment stage did not enter its bounded cast state:`n$debug"
    }
  }

  $log = Get-Content -LiteralPath $paperLog -Raw
  $bossLog = ($log -split "`r?`n") | Where-Object { $_ -match ('boss=' + [Regex]::Escape($bossUuid)) }
  foreach ($marker in @(
    'BOSS_AI_TARGET', 'BOSS_STAGE_TRANSITION', 'BOSS_CAST_STATE.*ABSORPTION_CHANNEL',
    'BOSS_CAST_STATE.*JUDGMENT_CAST'
  )) {
    if (-not ($bossLog -match $marker)) {
      throw "Local boss log for $bossUuid did not contain $marker."
    }
  }
  Write-Output "LIVE_AI_PHASES_PASS boss=$bossUuid waves=1,2,3,4,5,final stages=AWAKENING,HUNTER,DISTORTION,ABSORPTION,CATASTROPHE,JUDGMENT"
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
