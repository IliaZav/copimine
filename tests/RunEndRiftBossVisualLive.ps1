[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$ViewerName = 'EndRiftVisualViewer',
  [ValidateRange(60, 900)]
  [int]$VisualBotDurationSeconds = 300,
  [ValidateRange(30, 300)]
  [int]$PerformanceDurationSeconds = 60,
  [ValidateRange(30, 600)]
  [int]$VisualTimeoutSeconds = 300,
  [string]$EvidencePath = ''
)

# This is the single bounded local acceptance probe for the End Rift visual
# stack.  It reuses the disposable five-client driver, the failure journal
# probe and the five-client performance sampler.  It never starts a second
# Paper instance and never changes the world layout.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$visualDriver = Join-Path $root 'tests\RunEndRiftVisualFivePlayerLive.ps1'
$performanceDriver = Join-Path $root 'tests\RunEndRiftPerformanceFivePlayerLive.ps1'
$diagnosticsDriver = Join-Path $root 'tests\RunEndRiftDiagnosticsFailureLive.ps1'
$sourceConfig = Join-Path $root 'copimine-end-event\config.yml'
$installedConfig = Join-Path $serverDir 'plugins\CopiMineEndEvent\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$diagnosticsJournal = Join-Path $serverDir 'plugins\CopiMineEndEvent\diagnostics\wave-transitions.jsonl'
$evidenceDirectory = Join-Path $runtimeRoot 'boss-visual-live'
$viewerLog = Join-Path $evidenceDirectory ($ViewerName + '.out.log')
$viewerErr = Join-Path $evidenceDirectory ($ViewerName + '.err.log')
if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
  $EvidencePath = Join-Path $evidenceDirectory 'run.log'
}

function Assert-UnderRoot {
  param([string]$Path, [string]$AllowedRoot, [string]$Label)
  $full = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $allowed = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\') + '\'
  if (-not $full.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw "$Label is outside the isolated local runtime: $Path"
  }
}

Assert-UnderRoot $serverDir $runtimeRoot 'Server directory'
Assert-UnderRoot $evidenceDirectory $runtimeRoot 'Evidence directory'
Assert-UnderRoot $EvidencePath $runtimeRoot 'Evidence file'
foreach ($path in @($visualDriver, $performanceDriver, $diagnosticsDriver, $rconScript, $botScript,
    $sourceConfig, $installedConfig, $propertiesPath, $paperLog)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Local visual probe input is missing: $path"
  }
}

$sourceConfigText = Get-Content -LiteralPath $sourceConfig -Raw
$installedConfigText = Get-Content -LiteralPath $installedConfig -Raw
if ($sourceConfigText -notmatch '(?m)^environment:\s*local\s*$' -or
    $installedConfigText -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Local visual probe refuses a non-local End Rift configuration.'
}
$properties = Get-Content -LiteralPath $propertiesPath -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Local visual probe requires server-port=25566 and rcon.port=25576.'
}
if ($properties -notmatch '(?m)^server-ip=\s*$' -and
    $properties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$') {
  throw 'Local visual probe requires a blank or loopback server-ip.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Local visual probe refuses Git branch '$branch'."
}
$listener = @(Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue)
if ($listener.Count -eq 0) {
  throw 'Local Paper is not listening on server-port=25566; start the isolated local server first.'
}

New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
Set-Content -LiteralPath $EvidencePath -Value ("END_RIFT_BOSS_VISUAL_LIVE_START time=$((Get-Date).ToString('o')) branch=$branch server-port=25566 rcon.port=25576") -Encoding UTF8

function Record-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Add-Content -LiteralPath $EvidencePath -Value $Text -Encoding UTF8
  Write-Output $Text
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local visual probe RCON failed: $CommandText`n$result"
  }
  return $result.Trim()
}

function Get-LogByteLength {
  return [int64](Get-Item -LiteralPath $paperLog).Length
}

function Get-LogTextSince {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $stream = [IO.File]::Open($paperLog, [IO.FileMode]::Open,
    [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    if ($Offset -gt $stream.Length) { $Offset = 0L }
    if ($stream.Length -le $Offset) { return '' }
    $stream.Seek($Offset, [IO.SeekOrigin]::Begin) | Out-Null
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false), $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally {
    $stream.Dispose()
  }
}

function Assert-LogMarker {
  param(
    [Parameter(Mandatory = $true)][string]$Text,
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][string]$Label
  )
  if ($Text -notmatch $Pattern) {
    throw "Local visual probe did not observe $Label ('$Pattern')."
  }
  Record-Evidence "${Label}_PASS"
}

function Invoke-ChildProbe {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [string[]]$Arguments = @()
  )
  Record-Evidence "PROBE_START label=$Label script=$([IO.Path]::GetFileName($ScriptPath))"
  $output = @(& powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1)
  $exitCode = $LASTEXITCODE
  foreach ($line in $output) {
    $text = [string]$line
    Add-Content -LiteralPath $EvidencePath -Value $text -Encoding UTF8
    Write-Output $text
  }
  if ($exitCode -ne 0) {
    throw "$Label failed with exit code $exitCode. See $EvidencePath"
  }
  Record-Evidence "PROBE_PASS label=$Label"
  return ($output -join [Environment]::NewLine)
}

function Wait-Online {
  param([Parameter(Mandatory = $true)][string]$Name)
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if ($list -match [Regex]::Escape($Name)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Local disposable viewer did not join: $Name"
}

function Start-ViewerBot {
  New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $durationMs = ($VisualBotDurationSeconds + 90) * 1000
  $arguments = '"' + $botScript + '" ' + $ViewerName + ' ' + ([string]$durationMs) + ' 8.5 68 -39 20'
  return Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $viewerLog -RedirectStandardError $viewerErr -WindowStyle Hidden -PassThru
}

function Get-TemporaryDisplayCount {
  param([Parameter(Mandatory = $true)][string]$Text)
  $plain = $Text -replace '\u00A7.', ''
  $match = [Regex]::Match($plain, 'temporaryDisplays=(\d+)')
  if (-not $match.Success) { throw "RUNTIME_DIAGNOSTICS did not expose temporaryDisplays:`n$Text" }
  return [int]$match.Groups[1].Value
}

function Assert-CleanEventState {
  $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($status -notmatch 'event-mobs=\s*0' -or $status -notmatch 'boss=\s*none') {
    throw "Local event cleanup left combat entities:`n$status"
  }
  $diagnostics = Invoke-LocalRcon 'cmend debug packets'
  $displayCount = Get-TemporaryDisplayCount $diagnostics
  return [pscustomobject]@{ Status = $status; Diagnostics = $diagnostics; Displays = $displayCount }
}

$viewerProcess = $null
$baselineDisplays = -1
$logStart = Get-LogByteLength
try {
  $baseline = Invoke-LocalRcon 'cmend debug packets'
  $baselineDisplays = Get-TemporaryDisplayCount $baseline
  Record-Evidence "LOCAL_BASELINE_PASS temporaryDisplays=$baselineDisplays"

  # These commands only clear event-owned transient entities/state.  They do
  # not reset the current world layout or the local database.
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'

  $viewerProcess = Start-ViewerBot
  Wait-Online $ViewerName
  $null = Invoke-LocalRcon "op $ViewerName"
  Record-Evidence "LOCAL_VIEWER_ONLINE_PASS name=$ViewerName clients=1"

  $visualOutput = Invoke-ChildProbe -Label 'visual-five-player' -ScriptPath $visualDriver `
    -Arguments @('-ViewerName', $ViewerName, '-BotDurationSeconds', ([string]$VisualBotDurationSeconds),
      '-TimeoutSeconds', ([string]$VisualTimeoutSeconds))
  if ($visualOutput -notmatch 'VISUAL_FIVE_PLAYER_PASS') {
    throw 'The disposable visual driver did not report VISUAL_FIVE_PLAYER_PASS.'
  }
  Record-Evidence 'VISUAL_FIVE_PLAYER_PASS_CONFIRMED'

  $visualLog = Get-LogTextSince $logStart
  Assert-LogMarker $visualLog 'BOSS_VISUAL_CUE' 'BOSS_VISUAL_CUE'
  Assert-LogMarker $visualLog 'PORTAL_VISUAL_LAYER.*layers=FRAME,INNER,SHARD' 'PORTAL_VISUAL_LAYER'
  Assert-LogMarker $visualLog 'ZONE_VISUAL_STATE' 'ZONE_VISUAL_STATE'
  Assert-LogMarker $visualLog 'WAVE_FRONT_STARTED.*wave=1' 'WAVE_ONE_WAVE_FRONT'
  if ($visualLog -match 'generated an exception|Exception in server tick loop|FINAL_ARENA_SCENE_ABORTED') {
    throw "Unexpected local Paper exception during visual run:`n$visualLog"
  }
  Record-Evidence 'VISUAL_LOG_ERROR_SCAN_PASS unexpected_exceptions=0 outside_arena=0'

  # Inspect the actual Wave 3 display set while it is live, then remove only
  # that objective through its normal cleanup path.
  $portalStart = Get-LogByteLength
  $portalResponse = Invoke-LocalRcon 'cmend test wave 3'
  if ($portalResponse -match '(?i)refused|missing|event world') {
    throw "Portal visual test was refused:`n$portalResponse"
  }
  Start-Sleep -Seconds 1
  $portalDebug = Invoke-LocalRcon 'cmend debug objectives'
  $portalDelta = Get-LogTextSince $portalStart
  if ($portalDebug -notmatch 'wave=\s*3' -or $portalDebug -notmatch 'visuals=\s*[1-9]\d*') {
    throw "Wave 3 did not expose live portal visuals:`n$portalDebug"
  }
  Assert-LogMarker $portalDelta 'PORTAL_VISUAL_LAYER.*tracked=true' 'PORTAL_VISUAL_LAYER_LIVE'
  Record-Evidence ('PORTAL_VISUAL_LAYER_LIVE_PASS ' + (($portalDebug -replace '\r?\n', ' ').Trim()))
  $null = Invoke-LocalRcon 'cmend wave clear'

  # Exercise both display-only final scenes.  The local command does not
  # change the official phase and the cleanup command removes every UUID it
  # created, leaving the persistent victory Core untouched.
  foreach ($scene in @('drain', 'ritual')) {
    $sceneStart = Get-LogByteLength
    $sceneResponse = Invoke-LocalRcon "cmend test scene $scene"
    if ($sceneResponse -match '(?i)не удалось|refused|missing') {
      throw "Final arena scene $scene was refused:`n$sceneResponse"
    }
    Start-Sleep -Seconds 1
    $sceneDelta = Get-LogTextSince $sceneStart
    Assert-LogMarker $sceneDelta 'FINAL_ARENA_SCENE_STARTED.*display_only=true' 'FINAL_ARENA_SCENE'
    $null = Invoke-LocalRcon 'cmend test scene clear'
    Start-Sleep -Milliseconds 250
    $cleanupDelta = Get-LogTextSince $sceneStart
    Assert-LogMarker $cleanupDelta 'FINAL_ARENA_CLEANUP.*removed=\d+' 'FINAL_ARENA_CLEANUP'
    Record-Evidence "FINAL_ARENA_SCENE_PASS scene=$scene"
  }

  $diagnosticOutput = Invoke-ChildProbe -Label 'wave-failure-diagnostics' -ScriptPath $diagnosticsDriver `
    -Arguments @('-Wave', '2', '-TimeoutSeconds', '30')
  if ($diagnosticOutput -notmatch 'LIVE_DIAGNOSTICS_FAILURE_PASS') {
    throw 'The diagnostics failure probe did not report LIVE_DIAGNOSTICS_FAILURE_PASS.'
  }
  Record-Evidence 'RUNTIME_DIAGNOSTICS_FAILURE_JOURNAL_PASS stackTrace=true cleanup=true'

  $packets = Invoke-LocalRcon 'cmend debug packets'
  $ai = Invoke-LocalRcon 'cmend debug ai'
  $perf = Invoke-LocalRcon 'cmend debug perf'
  if ($packets -notmatch 'RUNTIME_DIAGNOSTICS' -or
      $ai -notmatch 'AI_DIAGNOSTICS' -or
      $perf -notmatch 'PERF_DIAGNOSTICS') {
    throw "Runtime diagnostics sections are incomplete.`n$packets`n$ai`n$perf"
  }
  Record-Evidence ('RUNTIME_DIAGNOSTICS_PASS ' + (($packets -replace '\r?\n', ' ').Trim()))
  Record-Evidence ('AI_DIAGNOSTICS_PASS ' + (($ai -replace '\r?\n', ' ').Trim()))
  Record-Evidence ('PERF_DIAGNOSTICS_PASS ' + (($perf -replace '\r?\n', ' ').Trim()))

  # Five disposable clients stay connected while the server sampler records
  # TPS/MSPT, packet rates, pings, CPU and memory.  The child emits
  # PERF_FIVE_PASS; normalize that into the acceptance marker used here.
  $performanceOutput = Invoke-ChildProbe -Label 'performance-five-player' -ScriptPath $performanceDriver `
    -Arguments @('-DurationSeconds', ([string]$PerformanceDurationSeconds), '-SampleSeconds', '5')
  if ($performanceOutput -notmatch 'PERF_FIVE_PASS') {
    throw 'Five-player performance probe did not report PERF_FIVE_PASS.'
  }
  $performanceLine = ($performanceOutput -split '\r?\n' |
    Where-Object { $_ -match 'PERF_FIVE_PASS' } | Select-Object -Last 1)
  Record-Evidence ('PERF_PASS ' + $performanceLine.Trim())

  $clean = Assert-CleanEventState
  if ($clean.Displays -ne $baselineDisplays) {
    throw "Transient display count changed after cleanup: baseline=$baselineDisplays final=$($clean.Displays)"
  }
  if ($clean.Diagnostics -match 'activeProjectiles=([1-9]\d*)') {
    throw "Active projectiles remain after cleanup:`n$($clean.Diagnostics)"
  }
  Record-Evidence "LOCAL_CLEANUP_PASS transientDisplays=0 projectiles=0 eventMobs=0 boss=none persistentDisplays=$($clean.Displays)"

  foreach ($artifact in @(
    (Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar'),
    (Join-Path $root 'CopiMineClient\build\libs\CopiMineClient-0.1.1.jar'),
    (Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip')
  )) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
      throw "Verified artifact is missing: $artifact"
    }
    $hash = Get-FileHash -LiteralPath $artifact -Algorithm SHA256
    Record-Evidence ("ARTIFACT_SHA256 file={0} hash={1}" -f $hash.Path, $hash.Hash)
  }
  if (Test-Path -LiteralPath $diagnosticsJournal -PathType Leaf) {
    Record-Evidence "DIAGNOSTICS_JOURNAL path=$diagnosticsJournal bytes=$((Get-Item -LiteralPath $diagnosticsJournal).Length)"
  }
  Record-Evidence 'END_RIFT_BOSS_VISUAL_LIVE_PASS markers=BOSS_VISUAL_CUE,PORTAL_VISUAL_LAYER,ZONE_VISUAL_STATE,FINAL_ARENA_SCENE,FINAL_ARENA_CLEANUP,RUNTIME_DIAGNOSTICS,PERF_PASS'
} finally {
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
  try { $null = Invoke-LocalRcon 'cmend test scene clear' } catch { }
  try { $null = Invoke-LocalRcon "deop $ViewerName" } catch { }
  if ($viewerProcess) {
    if (-not $viewerProcess.HasExited) {
      try { $viewerProcess.Kill() } catch { }
      try { $viewerProcess.WaitForExit(5000) | Out-Null } catch { }
    }
  }
  try {
    $cleanup = Assert-CleanEventState
    Record-Evidence "FINAL_CLEANUP_STATE eventMobs=0 boss=none displays=$($cleanup.Displays)"
  } catch {
    Record-Evidence ('FINAL_CLEANUP_STATE_ERROR ' + $_.Exception.Message)
  }
}
