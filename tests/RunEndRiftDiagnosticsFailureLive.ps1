[CmdletBinding()]
param(
  [ValidateRange(1, 5)]
  [int]$Wave = 2,
  [ValidateRange(5, 60)]
  [int]$TimeoutSeconds = 20
)

# Local-only failure probe for the real wave-transition diagnostics boundary.
# The plugin injects an exception before a test wave can spawn anything.  The
# probe checks the asynchronous JSONL record and then confirms that official
# state, mobs, boss and rune occupancy were not changed.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$pluginData = Join-Path $serverDir 'plugins\CopiMineEndEvent'
$sourceConfig = Join-Path $root 'copimine-end-event\config.yml'
$installedConfig = Join-Path $pluginData 'config.yml'
$diagnosticsPath = Join-Path $pluginData 'diagnostics\wave-transitions.jsonl'
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'

function Assert-LocalScope {
  foreach ($path in @($sourceConfig, $installedConfig, $rconScript)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      throw "Diagnostics probe input is missing: $path"
    }
  }
  foreach ($path in @($sourceConfig, $installedConfig)) {
    if ((Get-Content -LiteralPath $path -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
      throw "Diagnostics probe refused a non-local End Rift configuration: $path"
    }
  }
  $properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
      $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'Diagnostics probe requires isolated 25566/25576 ports.'
  }
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "Diagnostics probe refused Git branch '$branch'."
  }
  $localPrefix = $runtimeRoot.TrimEnd('\') + '\'
  if (-not $serverDir.StartsWith($localPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Diagnostics probe refused a server outside this worktree local-runtime directory.'
  }
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Diagnostics probe RCON failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Get-FileOffset {
  if (-not (Test-Path -LiteralPath $diagnosticsPath -PathType Leaf)) {
    return 0L
  }
  return [int64](Get-Item -LiteralPath $diagnosticsPath).Length
}

function Read-JsonLinesSince {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  if (-not (Test-Path -LiteralPath $diagnosticsPath -PathType Leaf)) {
    return @()
  }
  $stream = [IO.File]::Open($diagnosticsPath, [IO.FileMode]::Open,
    [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    if ($Offset -gt $stream.Length) { $Offset = 0L }
    $stream.Seek($Offset, [IO.SeekOrigin]::Begin) | Out-Null
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false), $true)
    try {
      $text = $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  } finally {
    $stream.Dispose()
  }
  $entries = @()
  foreach ($line in ($text -split '\r?\n')) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $entries += ,($line | ConvertFrom-Json) } catch { }
  }
  return $entries
}

function Assert-CleanOfficialState {
  $status = Invoke-LocalRcon 'cmend status'
  $plain = $status -replace '\u00A7.', ''
  if ($plain -notmatch 'state=.*READY_FOR_PLAYERS' -or
      $plain -notmatch 'event-mobs=0' -or
      $plain -notmatch 'boss=none' -or
      $plain -notmatch 'pads=0/2') {
    throw "Diagnostics probe changed or interrupted official state.`n$plain"
  }
  return $plain
}

Assert-LocalScope
$offset = Get-FileOffset
$response = Invoke-LocalRcon ("cmend test diagnostics fail $Wave")
if ($response -notmatch 'WAVE_DIAGNOSTICS_FAILURE_INJECTED') {
  throw "Diagnostics failure command did not report injection.`n$response"
}

$started = $null
$failed = $null
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
  foreach ($entry in (Read-JsonLinesSince -Offset $offset)) {
    if ($entry.event -eq 'WAVE_TRANSITION_STARTED' -and
        [int]$entry.toWave -eq $Wave) {
      $started = $entry
    }
    if ($entry.event -eq 'WAVE_TRANSITION_FAILED' -and
        [int]$entry.wave -eq $Wave -and
        $entry.errorMessage -match 'local diagnostics failure probe') {
      $failed = $entry
    }
  }
  if ($null -ne $started -and $null -ne $failed) { break }
  Start-Sleep -Milliseconds 250
}

if ($null -eq $started) {
  throw "Diagnostics journal did not record WAVE_TRANSITION_STARTED for wave $Wave."
}
if ($null -eq $failed) {
  throw "Diagnostics journal did not record the injected WAVE_TRANSITION_FAILED for wave $Wave."
}
if ([string]::IsNullOrWhiteSpace([string]$failed.stackTrace) -or
    [string]$failed.stackTrace -notmatch 'local diagnostics failure probe') {
  throw 'Diagnostics failure record did not contain the full injected stack trace.'
}
foreach ($property in @('eventId', 'generation', 'activeTasks', 'operationMillis',
    'errorType', 'errorMessage', 'stackTrace')) {
  if ($null -eq $failed.PSObject.Properties[$property]) {
    throw "Diagnostics failure record is missing '$property'."
  }
}

$cleanStatus = Assert-CleanOfficialState
Write-Output ("LIVE_DIAGNOSTICS_FAILURE_PASS event={0} generation={1} wave={2} activeTasks={3} operationMillis={4} stackTrace=true officialState=READY_FOR_PLAYERS eventMobs=0 boss=none pads=0/2" -f
  $failed.eventId, $failed.generation, $Wave, $failed.activeTasks,
  $failed.operationMillis)
Write-Output ($cleanStatus -replace '\r?\n', ' ')
