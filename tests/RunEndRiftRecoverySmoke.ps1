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
if ([string]::IsNullOrWhiteSpace($LogPath)) {
  $LogPath = Join-Path $localRuntimeRoot 'end-rift-paper-recovery.out.log'
}

$helper = Join-Path $scriptRoot 'InvokeEndRiftLocalRcon.ps1'
$plugins = & powershell -NoProfile -ExecutionPolicy Bypass -File $helper -ServerDir $ServerDir -RconPort $RconPort -CommandText 'plugins'
$pluginsNormalized = ($plugins -join [Environment]::NewLine) -replace '\s+', ''
if ($pluginsNormalized -notmatch 'CopiMineEndEvent' -or $pluginsNormalized -notmatch 'CopiMineWorldCore' -or $pluginsNormalized -notmatch 'CopiMineArtifacts') {
  throw "Recovery smoke did not find the typed plugin set: $plugins"
}
$status = & powershell -NoProfile -ExecutionPolicy Bypass -File $helper -ServerDir $ServerDir -RconPort $RconPort -CommandText 'cmend status'
$statusText = $status -join [Environment]::NewLine
foreach ($expected in @('UNLOCKED', 'endUnlocked=.*true', 'VICTORY_COMPLETE')) {
  if ($statusText -notmatch $expected) {
    throw "Recovery smoke expected '$expected'. Actual status: $statusText"
  }
  Write-Host "PASS recovery $expected"
}
if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
  $log = Get-Content -LiteralPath $LogPath -Tail 2500
  $logText = $log -join [Environment]::NewLine
  if ($logText -match 'CopiMineEndEvent failed closed') {
    throw 'Recovery smoke found failed-closed End Event bootstrap.'
  }
  $hasPersistedPhase = $logText -match 'persistent phase=[A-Z_]+'
  $hasForcedUnlock = $logText -match 'END_EVENT_STATE forced=UNLOCKED'
  $hasAlreadyUnlocked = $logText -match 'WorldCore already reports End unlocked; preserving active event'
  if (-not $hasPersistedPhase -or (-not $hasForcedUnlock -and -not $hasAlreadyUnlocked)) {
    throw 'Recovery smoke did not observe persisted phase plus a durable WorldCore unlock path.'
  }
  if ($hasForcedUnlock) {
    Write-Host 'PASS recovery persisted phase and forced unlock transition'
  } else {
    Write-Host 'PASS recovery persisted phase and idempotent already-unlocked transition'
  }
}
Write-Host 'End Rift durable recovery smoke passed.'
