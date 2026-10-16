[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [string]$LogPath = '',
  [string]$ErrPath = '',
  # A cold local Paper boot with all pinned plugins can legitimately take
  # several minutes while ImageFrame, TAB and the database-backed plugins
  # initialize. Keep the fail-closed cleanup, but do not kill a healthy
  # server just before it reaches the Done marker.
  [int]$ReadyTimeoutSeconds = 360
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$worktreeRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $worktreeRoot 'local-runtime')).Path
. (Join-Path $scriptRoot 'EndRiftLocalAuthMode.ps1')
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $localRuntimeRoot 'end-rift-server'
}
$ServerDir = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $ServerDir -ErrorAction Stop).ProviderPath)
$ServerDir = Test-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $localRuntimeRoot -MustExist
Assert-EndRiftLocalServerTreeIsolated `
  -ServerDir $ServerDir `
  -LocalRuntimeRoot $localRuntimeRoot | Out-Null

$propertiesPath = Join-Path $ServerDir 'server.properties'
$propertiesPath = Test-EndRiftLocalRuntimePath -Path $propertiesPath -LocalRuntimeRoot $localRuntimeRoot -MustExist
$eventConfigPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\config.yml'
$eventConfigPath = Test-EndRiftLocalRuntimePath -Path $eventConfigPath -LocalRuntimeRoot $localRuntimeRoot -MustExist
$configSourcePath = Join-Path $worktreeRoot 'copimine-end-event\config.yml'
$envPath = Join-Path $localRuntimeRoot 'end-rift.env'

function Get-LocalSha256 {
  param([Parameter(Mandatory = $true)][string]$LiteralPath)
  $hasher = [Security.Cryptography.SHA256]::Create()
  $stream = $null
  try {
    $stream = [IO.File]::OpenRead($LiteralPath)
    return (([BitConverter]::ToString($hasher.ComputeHash($stream)) -replace '-', '').ToLowerInvariant())
  } finally {
    if ($stream) { $stream.Dispose() }
    $hasher.Dispose()
  }
}

function Sync-LocalResourcePack {
  $sourcePackDir = Join-Path $worktreeRoot 'resourcepacks\build'
  $sourcePack = Join-Path $sourcePackDir 'CopiMineResourcePack.zip'
  if (-not (Test-Path -LiteralPath $sourcePack -PathType Leaf)) {
    throw "Current local resource pack is missing: $sourcePack"
  }
  $propertiesText = [IO.File]::ReadAllText($propertiesPath)
  if ($propertiesText -notmatch '(?m)^resource-pack-sha1=.*$') {
    throw 'Local server.properties is missing resource-pack-sha1.'
  }
  $targetPackDir = Join-Path $ServerDir 'resourcepacks\build'
  $targetPackDir = Assert-EndRiftLocalRuntimePath -Path $targetPackDir -LocalRuntimeRoot $localRuntimeRoot
  New-Item -ItemType Directory -Path $targetPackDir -Force | Out-Null
  $targetPack = Join-Path $targetPackDir 'CopiMineResourcePack.zip'
  $targetPack = Assert-EndRiftLocalRuntimePath -Path $targetPack -LocalRuntimeRoot $localRuntimeRoot
  $nextPack = $targetPack + '.next'
  $nextPack = Assert-EndRiftLocalRuntimePath -Path $nextPack -LocalRuntimeRoot $localRuntimeRoot
  Copy-Item -LiteralPath $sourcePack -Destination $nextPack -Force
  $sha256 = Get-LocalSha256 -LiteralPath $sourcePack
  if ((Get-LocalSha256 -LiteralPath $nextPack) -ne $sha256) {
    throw 'Local HTTP resource-pack copy failed SHA-256 verification.'
  }
  if (Test-Path -LiteralPath $targetPack -PathType Leaf) {
    [IO.File]::Replace($nextPack, $targetPack, [NullString]::Value)
  } else {
    [IO.File]::Move($nextPack, $targetPack)
  }
  $sha1Hasher = [Security.Cryptography.SHA1]::Create()
  $sha1Stream = [IO.File]::OpenRead($targetPack)
  try {
    $sha1 = ([BitConverter]::ToString($sha1Hasher.ComputeHash($sha1Stream)) -replace '-', '').ToLowerInvariant()
  } finally {
    $sha1Stream.Dispose()
    $sha1Hasher.Dispose()
  }
  [IO.File]::WriteAllText((Join-Path $targetPackDir 'CopiMineResourcePack.sha1'), $sha1 + "`n")
  [IO.File]::WriteAllText((Join-Path $targetPackDir 'CopiMineResourcePack.sha256'), $sha256 + "`n")
  $propertiesText = [regex]::Replace($propertiesText, '(?m)^resource-pack-sha1=[^\r\n]*', 'resource-pack-sha1=' + $sha1)
  [IO.File]::WriteAllText($propertiesPath, $propertiesText, [Text.UTF8Encoding]::new($false))
  Write-Output "Verified local HTTP resource pack SHA1=$sha1 SHA256=$sha256"
}

if ((-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) -or (-not (Test-Path -LiteralPath $eventConfigPath -PathType Leaf))) {
  throw 'Local End Rift server.properties or plugin config is missing.'
}
if ((Get-Item -LiteralPath $propertiesPath).Length -gt 1048576) {
  throw 'Local End Rift startup refused an unexpectedly large server.properties file.'
}
if (-not (Test-Path -LiteralPath $configSourcePath -PathType Leaf)) {
  throw "Current local End Rift config is missing: $configSourcePath"
}
$sourceConfigText = Get-Content -LiteralPath $configSourcePath -Raw
if ($sourceConfigText -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused to synchronize a non-local End Rift configuration.'
}
$sourceConfigHash = Get-LocalSha256 -LiteralPath $configSourcePath
$targetConfigHash = Get-LocalSha256 -LiteralPath $eventConfigPath
if ($sourceConfigHash -ne $targetConfigHash) {
  Copy-Item -LiteralPath $configSourcePath -Destination $eventConfigPath -Force
  Write-Output "Synchronized local End Rift config from current source: $eventConfigPath"
}
$verifiedConfigHash = Get-LocalSha256 -LiteralPath $eventConfigPath
if ($sourceConfigHash -ne $verifiedConfigHash) {
  throw "Local End Rift config hash mismatch after synchronization: source=$sourceConfigHash target=$verifiedConfigHash"
}
if ((Get-Content -LiteralPath $eventConfigPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused to start a non-local End Rift configuration.'
}

$pluginSourcePath = Join-Path $worktreeRoot 'copimine-end-event\CopiMineEndEvent.jar'
$pluginTargetPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent.jar'
if (-not (Test-Path -LiteralPath $pluginSourcePath -PathType Leaf)) {
  throw "Current local End Rift plugin build is missing: $pluginSourcePath"
}
if (-not (Test-Path -LiteralPath (Split-Path $pluginTargetPath -Parent) -PathType Container)) {
  New-Item -ItemType Directory -Path (Split-Path $pluginTargetPath -Parent) -Force | Out-Null
}
$sourcePluginHash = Get-LocalSha256 -LiteralPath $pluginSourcePath
$targetPluginHash = if (Test-Path -LiteralPath $pluginTargetPath -PathType Leaf) {
  Get-LocalSha256 -LiteralPath $pluginTargetPath
} else {
  ''
}
if ($sourcePluginHash -ne $targetPluginHash) {
  Copy-Item -LiteralPath $pluginSourcePath -Destination $pluginTargetPath -Force
  Write-Output "Synchronized local End Rift plugin from current build: $pluginTargetPath"
}
$verifiedPluginHash = Get-LocalSha256 -LiteralPath $pluginTargetPath
if ($sourcePluginHash -ne $verifiedPluginHash) {
  throw "Local End Rift plugin hash mismatch after synchronization: source=$sourcePluginHash target=$verifiedPluginHash"
}
Write-Output "Verified local End Rift plugin SHA256=$verifiedPluginHash"

$serverPropertiesLines = [IO.File]::ReadAllLines($propertiesPath)
Test-EndRiftLocalServerBind -ServerPropertiesLines $serverPropertiesLines | Out-Null
Assert-EndRiftLocalConfiguredPorts -ServerPropertiesLines $serverPropertiesLines -ServerPort 25566 -RconPort 25576 | Out-Null
$null = Assert-EndRiftLocalPortsAvailable -Ports @(25566, 25576)

# The HTTP process serves this runtime copy, not the repository build file.
# Synchronize its bytes and the declared digest together before Paper boots.
Sync-LocalResourcePack

if (Test-Path -LiteralPath $envPath -PathType Leaf) {
  $envPath = Test-EndRiftLocalRuntimePath -Path $envPath -LocalRuntimeRoot $localRuntimeRoot -MustExist
  Import-EndRiftLocalEnvironment -Path $envPath
}

$localLogRoot = $localRuntimeRoot
if ([string]::IsNullOrWhiteSpace($LogPath)) {
  $LogPath = Join-Path $localLogRoot 'end-rift-paper-start.out.log'
}
if ([string]::IsNullOrWhiteSpace($ErrPath)) {
  $ErrPath = Join-Path $localLogRoot 'end-rift-paper-start.err.log'
}
foreach ($logCandidatePath in @($LogPath, $ErrPath)) {
  $validatedLogPath = Assert-EndRiftLocalOutputPath -Path $logCandidatePath -LocalRuntimeRoot $localRuntimeRoot
  $logDirectory = Split-Path -Parent $validatedLogPath
  $null = Assert-EndRiftLocalRuntimePath -Path $logDirectory -LocalRuntimeRoot $localRuntimeRoot
  if (-not (Test-Path -LiteralPath $logDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
  }
}
$LogPath = Assert-EndRiftLocalOutputPath -Path $LogPath -LocalRuntimeRoot $localRuntimeRoot
$ErrPath = Assert-EndRiftLocalOutputPath -Path $ErrPath -LocalRuntimeRoot $localRuntimeRoot
$java = (Get-Command java -ErrorAction Stop).Source
# Leave headroom for Fabric's native buffers on this desktop. With a 1G
# initial heap the 3G server also exhausted Windows commit during client
# connection; the local probe does not need that much committed idle heap.
$paper = Start-Process -FilePath $java `
  -ArgumentList @('-Xms256M', '-Xmx2G', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled', '-Dfile.encoding=UTF-8', '-jar', ('"' + (Assert-EndRiftLocalRuntimePath -Path (Join-Path $ServerDir 'purpur.jar') -LocalRuntimeRoot $localRuntimeRoot -MustExist) + '"'), 'nogui') `
  -WorkingDirectory $ServerDir `
  -RedirectStandardOutput $LogPath `
  -RedirectStandardError $ErrPath `
  -WindowStyle Hidden `
  -PassThru

$ready = $false
for ($second = 0; $second -lt $ReadyTimeoutSeconds; $second++) {
  if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
    $ready = (Get-Content -LiteralPath $LogPath -Raw) -match 'Done \([^\r\n]*\)!'
    if ($ready) { break }
  }
  Start-Sleep -Seconds 1
}
if (-not $ready) {
  if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
    Get-Content -LiteralPath $LogPath -Tail 120
  }
  if (-not $paper.HasExited) {
    try { $paper.Kill() } catch { }
    $paper.WaitForExit(10000) | Out-Null
  }
  throw "Local Paper PID $($paper.Id) did not become ready within $ReadyTimeoutSeconds seconds."
}

$paperJarPath = Assert-EndRiftLocalRuntimePath -Path (Join-Path $ServerDir 'purpur.jar') -LocalRuntimeRoot $localRuntimeRoot -MustExist
try {
  $bindEvidence = Assert-EndRiftLocalServerBind -ServerDir $ServerDir -ServerPort 25566 -RconPort 25576 -ExpectedProcessId $paper.Id
  Write-EndRiftLocalServerReceipt -ProcessId $paper.Id -ServerDir $ServerDir -PaperJarPath $paperJarPath -LocalRuntimeRoot $localRuntimeRoot | Out-Null
  $serverReceipt = Get-EndRiftLocalServerReceipt -ServerDir $ServerDir -LocalRuntimeRoot $localRuntimeRoot
  $identityMatches = Test-EndRiftLocalServerProcessIdentity `
    -ExpectedProcessId $serverReceipt.ProcessId `
    -ExpectedProcessStartTimeUtcTicks $serverReceipt.ProcessStartTimeUtcTicks `
    -ServerDir $ServerDir `
    -PaperJarPath $serverReceipt.PaperJarPath `
    -ExpectedWorkingDirectory $serverReceipt.WorkingDirectory `
    -LocalRuntimeRoot $localRuntimeRoot
  if (-not $identityMatches) { throw 'Started Paper process did not match its isolated server receipt.' }
} catch {
  if (-not $paper.HasExited) {
    try { $paper.Kill() } catch { }
    $paper.WaitForExit(10000) | Out-Null
  }
  Remove-Item -LiteralPath (Join-Path $ServerDir '.end-rift-process-receipt.json') -Force -ErrorAction SilentlyContinue
  throw
}

Write-Output "Started isolated local Paper PID=$($paper.Id) server=$ServerDir"
Write-Output 'Local End Rift ports: server=25566 rcon=25576'
