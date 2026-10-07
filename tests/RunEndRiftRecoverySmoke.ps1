[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [int]$RconPort = 25576,
  [string]$LogPath = ''
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $scriptRoot '..\local-runtime\end-rift-server'
}
$ServerDir = (Resolve-Path $ServerDir).Path
$worktreeRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $worktreeRoot 'local-runtime')).Path
$localPrefix = $localRuntimeRoot.TrimEnd('\') + '\'
if (-not $ServerDir.StartsWith($localPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Recovery smoke refused a server outside this worktree local-runtime directory.'
}
$configPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\config.yml'
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Recovery smoke refused a non-local End Rift configuration.'
}
$statePath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\event-state.yml'
if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
  throw 'Recovery smoke did not find the durable End Rift event-state.yml snapshot.'
}
$stateText = Get-Content -LiteralPath $statePath -Raw
if ($stateText -notmatch '(?m)^\s*phase:\s*(?:UNLOCKED|UNCONFIGURED|COLLECTING|READY_FOR_PLAYERS)\s*$') {
  throw 'Recovery smoke found an unsafe or missing durable End Rift phase in event-state.yml.'
}
if ($stateText -notmatch '(?m)^\s*end-unlocked:\s*true\s*$') {
  throw 'Recovery smoke found event-state.yml without durable End unlock.'
}
if ([string]::IsNullOrWhiteSpace($LogPath)) {
  $LogPath = Join-Path $ServerDir 'logs\latest.log'
}

$helper = Join-Path $scriptRoot 'InvokeEndRiftLocalRcon.ps1'
$plugins = & powershell -NoProfile -ExecutionPolicy Bypass -File $helper -ServerDir $ServerDir -RconPort $RconPort -CommandText 'plugins'
$pluginsNormalized = ($plugins -join [Environment]::NewLine) -replace '\s+', ''
if ($pluginsNormalized -notmatch 'CopiMineEndEvent' -or $pluginsNormalized -notmatch 'CopiMineWorldCore' -or $pluginsNormalized -notmatch 'CopiMineArtifacts') {
  throw "Recovery smoke did not find the typed plugin set: $plugins"
}
$status = & powershell -NoProfile -ExecutionPolicy Bypass -File $helper -ServerDir $ServerDir -RconPort $RconPort -CommandText 'cmend status'
$statusText = $status -join [Environment]::NewLine
$safeState = $statusText -match 'state=.*(?:UNLOCKED|UNCONFIGURED|COLLECTING|READY_FOR_PLAYERS)'
$endUnlocked = $statusText -match 'endUnlocked=.*true'
if (-not $safeState -or -not $endUnlocked) {
  throw "Recovery smoke expected a safe unlocked state (UNLOCKED, UNCONFIGURED, COLLECTING, or READY_FOR_PLAYERS with endUnlocked=true). Actual status: $statusText"
}
Write-Host 'PASS recovery state=UNLOCKED|UNCONFIGURED|COLLECTING|READY_FOR_PLAYERS'
Write-Host 'PASS recovery endUnlocked=true'
Write-Host 'PASS recovery durable event-state phase and end-unlocked=true'
if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  function Read-GzipLogText {
    param([Parameter(Mandatory = $true)][string]$Path)
    $fileStream = [IO.File]::OpenRead($Path)
    $gzipStream = [IO.Compression.GzipStream]::new(
      $fileStream, [IO.Compression.CompressionMode]::Decompress)
    $reader = [IO.StreamReader]::new($gzipStream)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
      $gzipStream.Dispose()
      $fileStream.Dispose()
    }
  }

  # Paper rotates latest.log at midnight/size boundaries.  A restart record
  # can therefore be in the newest compressed sibling even while the active
  # latest.log contains only the post-startup runtime diagnostics.  Read the
  # active log plus a bounded set of rotated siblings so recovery evidence is
  # tied to the same isolated server without scanning unbounded history.
  $logFiles = [System.Collections.Generic.List[string]]::new()
  $logFiles.Add((Resolve-Path -LiteralPath $LogPath).Path)
  $logDirectory = Split-Path -Parent $LogPath
  if (Test-Path -LiteralPath $logDirectory -PathType Container) {
    Get-ChildItem -LiteralPath $logDirectory -Filter '*.gz' -File |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 8 |
      ForEach-Object { $logFiles.Add($_.FullName) }
  }
  $logText = (($logFiles | Select-Object -Unique | ForEach-Object {
        if ($_.EndsWith('.gz', [StringComparison]::OrdinalIgnoreCase)) {
          Read-GzipLogText -Path $_
        } else {
          Get-Content -LiteralPath $_ -Raw
        }
      }) -join [Environment]::NewLine)
  if ($logText -match 'NoClassDefFoundError|ClassNotFoundException|CopiMineEndEvent failed closed') {
    throw 'Recovery smoke found a typed dependency or failed-closed End Event bootstrap error.'
  }
  $hasPersistedPhase = $logText -match 'persistent phase=[A-Z_]+'
  $hasForcedUnlock = $logText -match 'END_EVENT_STATE forced=UNLOCKED'
  $hasForcedUnconfigured = $logText -match 'END_EVENT_STATE forced=UNCONFIGURED reason=WorldCore already reports End unlocked without Core'
  $hasForcedReady = $logText -match 'END_EVENT_STATE forced=READY_FOR_PLAYERS'
  $hasAlreadyUnlocked = $logText -match 'WorldCore already reports End unlocked; preserving active event'
  $hasPersistedSceneReindex = $logText -match 'END_EVENT_COMBAT_REINDEX event='
  $hasReadyServices = $logText -match 'CopiMineEndEvent services ready; phase=(?:COLLECTING|READY_FOR_PLAYERS|UNLOCKED)'
  # A configured local scene may already be safely unlocked.  In that case
  # startup preserves its persisted phase and reindexes owned visuals/entities
  # instead of emitting a second forced-unlock transition.
  $hasUnlockPath = $hasForcedUnlock -or $hasForcedUnconfigured -or $hasForcedReady -or
    $hasAlreadyUnlocked -or ($hasPersistedSceneReindex -and $hasReadyServices)
  if (-not $hasPersistedPhase -or -not $hasUnlockPath) {
    throw 'Recovery smoke did not observe persisted phase plus a durable WorldCore unlock path.'
  }
  if ($hasForcedUnlock) {
    Write-Host 'PASS recovery persisted phase and forced unlock transition'
  } elseif ($hasForcedUnconfigured) {
    Write-Host 'PASS recovery persisted phase and safe unconfigured transition'
  } elseif ($hasForcedReady) {
    Write-Host 'PASS recovery persisted phase and forced ready transition'
  } elseif ($hasPersistedSceneReindex -and $hasReadyServices) {
    Write-Host 'PASS recovery persisted phase and reindexed configured local scene'
  } else {
    Write-Host 'PASS recovery persisted phase and idempotent already-unlocked transition'
  }
  Write-Host ("PASS recovery startup logs current={0} rotated={1}" -f
    $LogPath, [Math]::Max(0, $logFiles.Count - 1))
}
Write-Host 'End Rift durable recovery smoke passed.'
