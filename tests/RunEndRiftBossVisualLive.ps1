[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$ViewerName = 'EndRiftVisualA',
  [ValidateRange(60, 900)]
  [int]$VisualBotDurationSeconds = 120,
  [ValidateRange(15, 900)]
  [int]$PerformanceDurationSeconds = 15,
  [ValidateRange(30, 600)]
  [int]$VisualTimeoutSeconds = 180,
  [string]$EvidencePath = ''
)

# Current local acceptance wrapper. The child probes own their disposable
# clients and transient event objects; this wrapper only composes evidence and
# checks the final local diagnostics state.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$visualDriver = Join-Path $root 'tests\RunEndRiftVisualFivePlayerLive.ps1'
$performanceDriver = Join-Path $root 'tests\RunEndRiftPerformanceFivePlayerLive.ps1'
$diagnosticsDriver = Join-Path $root 'tests\RunEndRiftDiagnosticsFailureLive.ps1'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$installedConfigPath = Join-Path $serverDir 'plugins\CopiMineEndEvent\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$evidenceDirectory = Join-Path $runtimeRoot 'current-visual-acceptance'
if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
  $EvidencePath = Join-Path $evidenceDirectory 'run.log'
}

function Assert-UnderRoot {
  param([string]$Path, [string]$AllowedRoot, [string]$Label)
  $full = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $allowed = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\') + '\'
  if (-not $full.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw "$Label is outside local-runtime: $Path"
  }
}

Assert-UnderRoot $serverDir $runtimeRoot 'Server directory'
Assert-UnderRoot $EvidencePath $runtimeRoot 'Evidence file'
foreach ($path in @($visualDriver, $performanceDriver, $diagnosticsDriver,
    $rconScript, $configPath, $installedConfigPath, $propertiesPath, $paperLog)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Current visual acceptance input is missing: $path"
  }
}
$sourceConfig = Get-Content -LiteralPath $configPath -Raw
$installedConfig = Get-Content -LiteralPath $installedConfigPath -Raw
if ($sourceConfig -notmatch '(?m)^environment:\s*local\s*$' -or
    $installedConfig -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Current visual acceptance refuses a non-local configuration.'
}
$properties = Get-Content -LiteralPath $propertiesPath -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Current visual acceptance requires isolated local Paper ports.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Current visual acceptance refused Git branch '$branch'."
}
if (@(Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue).Count -eq 0) {
  throw 'Local Paper is not listening on 25566.'
}

New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
Set-Content -LiteralPath $EvidencePath -Value ("END_RIFT_CURRENT_VISUAL_ACCEPTANCE_START time=$((Get-Date).ToString('o')) branch=$branch") -Encoding UTF8

function Record-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Add-Content -LiteralPath $EvidencePath -Value $Text -Encoding UTF8
  Write-Output $Text
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "Local RCON failed: $CommandText`n$result" }
  return $result.Trim()
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
    Add-Content -LiteralPath $EvidencePath -Value ([string]$line) -Encoding UTF8
    Write-Output ([string]$line)
  }
  if ($exitCode -ne 0) { throw "$Label failed with exit code $exitCode." }
  Record-Evidence "PROBE_PASS label=$Label"
  return ($output -join [Environment]::NewLine)
}

function Assert-Contains {
  param([string]$Text, [string]$Pattern, [string]$Label)
  if ($Text -notmatch $Pattern) { throw "Missing ${Label}: $Pattern" }
  Record-Evidence "${Label}_PASS"
}

$success = $false
try {
  $visual = Invoke-ChildProbe -Label 'current-five-client-visual' -ScriptPath $visualDriver `
    -Arguments @('-ViewerName', $ViewerName,
      '-BotDurationSeconds', ([string]$VisualBotDurationSeconds),
      '-TimeoutSeconds', ([string]$VisualTimeoutSeconds))
  Assert-Contains $visual 'CURRENT_VISUAL_FIVE_PLAYER_PASS' 'CURRENT_VISUAL_FIVE_PLAYER'

  $diagnostics = Invoke-ChildProbe -Label 'transition-failure-journal' -ScriptPath $diagnosticsDriver `
    -Arguments @('-Wave', '2', '-TimeoutSeconds', '30')
  Assert-Contains $diagnostics 'LIVE_DIAGNOSTICS_FAILURE_PASS' 'DIAGNOSTICS_FAILURE'

  $performance = Invoke-ChildProbe -Label 'current-five-client-performance' -ScriptPath $performanceDriver `
    -Arguments @('-DurationSeconds', ([string]$PerformanceDurationSeconds), '-SampleSeconds', '3')
  Assert-Contains $performance 'PERF_FIVE_PASS' 'PERF_FIVE'

  $packets = Invoke-LocalRcon 'cmend debug packets'
  $ai = Invoke-LocalRcon 'cmend debug ai'
  $perf = Invoke-LocalRcon 'cmend debug perf'
  Assert-Contains $packets 'RUNTIME_DIAGNOSTICS' 'RUNTIME_DIAGNOSTICS'
  Assert-Contains $ai 'AI_DIAGNOSTICS' 'AI_DIAGNOSTICS'
  Assert-Contains $perf 'PERF_DIAGNOSTICS' 'PERF_DIAGNOSTICS'
  Record-Evidence ('PACKET_DIAGNOSTICS ' + ($packets -replace '\r?\n', ' '))
  Record-Evidence ('AI_DIAGNOSTICS ' + ($ai -replace '\r?\n', ' '))
  Record-Evidence ('PERF_DIAGNOSTICS ' + ($perf -replace '\r?\n', ' '))

  foreach ($artifact in @(
      (Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar'),
      (Join-Path $root 'CopiMineClient\build\libs\CopiMineClient-0.1.1.jar'),
      (Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip')
  )) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
      throw "Verified local artifact is missing: $artifact"
    }
    $hash = Get-FileHash -LiteralPath $artifact -Algorithm SHA256
    Record-Evidence ("ARTIFACT_SHA256 file={0} hash={1}" -f $hash.Path, $hash.Hash)
  }
  $success = $true
  Record-Evidence 'NATIVE_CLIENT_SCREENSHOT=NOT_VERIFIED'
  Record-Evidence 'NORMAL_GEAR_BALANCE=NOT_VERIFIED'
  Record-Evidence 'END_RIFT_BOSS_VISUAL_LIVE_PASS five_clients=true wave_front=true portals=true obelisks=true boss_cues=true diagnostics=true performance=true cleanup=true native=NOT_VERIFIED'
} finally {
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
  if (-not $success) { Record-Evidence 'END_RIFT_BOSS_VISUAL_LIVE_PASS=NOT_VERIFIED' }
}
