[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [string]$LogPath = '',
  [string]$ErrPath = '',
  [string]$ResourcePackSourcePath = '',
  [string]$PluginArtifactPath = '',
  [string]$EnvironmentFilePath = '',
  [string]$JavaHomePath = '',
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
$profileLockPath = Join-Path $worktreeRoot 'tools\minecraft-26.3\profile.lock.json'
if (-not (Test-Path -LiteralPath $profileLockPath -PathType Leaf)) {
  throw "Pinned Minecraft 26.3 profile lock is missing: $profileLockPath"
}
$profileLock = Get-Content -LiteralPath $profileLockPath -Raw | ConvertFrom-Json
if ($profileLock.minecraftVersion -ne '26.3' -or
    $null -eq $profileLock.resourcePackMigration) {
  throw 'Pinned profile lock does not describe the Minecraft 26.3 resource-pack migration.'
}
$expectedLegacyPackSha256 = ([string]$profileLock.resourcePackMigration.legacyPackSha256).ToLowerInvariant()
$expectedMigrationPackSha256 = ([string]$profileLock.resourcePackMigration.candidateSha256).ToLowerInvariant()
$expectedPaperSha256 = ([string]$profileLock.paper.sha256).ToLowerInvariant()
if ($expectedLegacyPackSha256 -notmatch '^[0-9a-f]{64}$' -or
    $expectedMigrationPackSha256 -notmatch '^[0-9a-f]{64}$' -or
    $expectedPaperSha256 -notmatch '^[0-9a-f]{64}$') {
  throw 'Pinned Paper or resource-pack SHA-256 values are invalid.'
}
$defaultResourcePackSourcePath = Join-Path $worktreeRoot 'build\minecraft-26.3\CopiMineResourcePack-26.3.zip'
$selectedResourcePackSourcePath = if ([string]::IsNullOrWhiteSpace($ResourcePackSourcePath)) {
  $defaultResourcePackSourcePath
} else {
  $ResourcePackSourcePath
}
if (-not (Test-Path -LiteralPath $selectedResourcePackSourcePath -PathType Leaf)) {
  throw "Selected local resource-pack source is missing: $selectedResourcePackSourcePath"
}
$sourcePackPath = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $selectedResourcePackSourcePath).ProviderPath)
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
$defaultEnvironmentFilePath = Join-Path $localRuntimeRoot 'end-rift.env'
$selectedEnvironmentFilePath = if ([string]::IsNullOrWhiteSpace($EnvironmentFilePath)) {
  $defaultEnvironmentFilePath
} else {
  $EnvironmentFilePath
}
$envPath = Test-EndRiftLocalRuntimePath `
  -Path ([IO.Path]::GetFullPath($selectedEnvironmentFilePath)) `
  -LocalRuntimeRoot $localRuntimeRoot
if (-not [string]::IsNullOrWhiteSpace($EnvironmentFilePath) -and
    -not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
  throw "Selected local environment file is missing: $envPath"
}

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
  if (-not (Test-Path -LiteralPath $sourcePackPath -PathType Leaf)) {
    throw "Selected local resource pack is missing: $sourcePackPath"
  }
  $legacyPackPath = Join-Path $worktreeRoot 'resourcepacks\build\CopiMineResourcePack.zip'
  if (-not (Test-Path -LiteralPath $legacyPackPath -PathType Leaf) -or
      (Get-LocalSha256 -LiteralPath $legacyPackPath) -ne $expectedLegacyPackSha256) {
    throw 'Built legacy resource pack SHA-256 differs from the pinned source.'
  }
  $sourceSha256 = Get-LocalSha256 -LiteralPath $sourcePackPath
  if ($sourceSha256 -notin @($expectedLegacyPackSha256, $expectedMigrationPackSha256)) {
    throw 'Selected resource pack source SHA-256 is not pinned.'
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
  Copy-Item -LiteralPath $sourcePackPath -Destination $nextPack -Force
  $sha256 = $sourceSha256
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

$pluginLockPath = Join-Path $worktreeRoot 'tools\minecraft-26.3\server-plugins.lock.json'
if (-not (Test-Path -LiteralPath $pluginLockPath -PathType Leaf)) {
  throw "Pinned server-plugin lock is missing: $pluginLockPath"
}
$pluginLock = Get-Content -LiteralPath $pluginLockPath -Raw | ConvertFrom-Json
$endEventEntries = @($pluginLock.unchangedBaseline | Where-Object { $_.pluginName -ceq 'CopiMineEndEvent' })
if ($endEventEntries.Count -ne 1 -or $null -eq $endEventEntries[0].candidate) {
  throw 'Pinned server-plugin lock must contain exactly one CopiMineEndEvent candidate.'
}
$endEventCandidate = $endEventEntries[0].candidate
$expectedPluginSha256 = ([string]$endEventCandidate.sha256).ToLowerInvariant()
if ($endEventCandidate.runtimeEligible -ne $true -or $expectedPluginSha256 -notmatch '^[0-9a-f]{64}$') {
  throw 'Pinned CopiMineEndEvent candidate is not eligible or has an invalid SHA-256.'
}
$defaultPluginSourcePath = Join-Path $worktreeRoot ([string]$endEventCandidate.buildArtifact)
$selectedPluginSourcePath = if ([string]::IsNullOrWhiteSpace($PluginArtifactPath)) {
  $defaultPluginSourcePath
} else {
  $PluginArtifactPath
}
$pluginSourcePath = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $selectedPluginSourcePath -ErrorAction Stop).ProviderPath)
$pluginTargetPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent.jar'
if (-not (Test-Path -LiteralPath $pluginSourcePath -PathType Leaf)) {
  throw "Selected local End Rift plugin build is missing: $pluginSourcePath"
}
if (-not (Test-Path -LiteralPath (Split-Path $pluginTargetPath -Parent) -PathType Container)) {
  New-Item -ItemType Directory -Path (Split-Path $pluginTargetPath -Parent) -Force | Out-Null
}
$sourcePluginHash = Get-LocalSha256 -LiteralPath $pluginSourcePath
if ($sourcePluginHash -ne $expectedPluginSha256) {
  throw "Selected local End Rift plugin SHA-256 differs from its pinned candidate: expected=$expectedPluginSha256 actual=$sourcePluginHash"
}
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
$defaultJavaHomePath = Join-Path $localRuntimeRoot 'toolchains\temurin-25.0.4.1\jdk-25.0.4.1+1'
$selectedJavaHomePath = if ([string]::IsNullOrWhiteSpace($JavaHomePath)) {
  $defaultJavaHomePath
} else {
  [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $JavaHomePath -ErrorAction Stop).ProviderPath)
}
$javaHomePath = Test-EndRiftLocalRuntimePath `
  -Path $selectedJavaHomePath `
  -LocalRuntimeRoot $localRuntimeRoot -MustExist
if (-not [string]::IsNullOrWhiteSpace($JavaHomePath)) {
  $serverJavaPrefix = $ServerDir + [IO.Path]::DirectorySeparatorChar
  if (-not $javaHomePath.StartsWith($serverJavaPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'An explicit local End Rift Java home must be inside the isolated server directory.'
  }
}
$java = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $javaHomePath 'bin\java.exe') `
  -LocalRuntimeRoot $localRuntimeRoot -MustExist
$previousErrorActionPreference = $ErrorActionPreference
try {
  # Java prints its version on stderr; Windows PowerShell treats that as an
  # error record under Stop unless this one probe temporarily allows it.
  $ErrorActionPreference = 'Continue'
  $javaVersion = (& $java -version 2>&1 | Out-String)
  $javaVersionExitCode = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $previousErrorActionPreference
}
if ($javaVersionExitCode -ne 0 -or $javaVersion -notmatch 'openjdk version "25\.0\.4\.1') {
  throw "Local End Rift requires the pinned Temurin 25.0.4.1 runtime: $java"
}
# Keep the bounded local probe heap above HotSpot's zero-based compressed-oops
# range and leave room for the desktop and the user's separate server. The
# machine ran out of commit even with a 1 GiB heap, so this local-only probe
# uses a 512 MiB heap and stops tiered compilation at C1 to avoid C2 arenas.
$paperJarPath = Join-Path $ServerDir 'paper-26.3-205.jar'
if (-not (Test-Path -LiteralPath $paperJarPath -PathType Leaf)) {
  $paperJarPath = Join-Path $ServerDir 'purpur.jar'
}
$paperJarPath = Assert-EndRiftLocalRuntimePath -Path $paperJarPath -LocalRuntimeRoot $localRuntimeRoot -MustExist
$paperJarHash = Get-LocalSha256 -LiteralPath $paperJarPath
if ($paperJarHash -ne $expectedPaperSha256) {
  throw "Selected local Paper server SHA-256 differs from the pinned 26.3 build: expected=$expectedPaperSha256 actual=$paperJarHash"
}
$paper = Start-Process -FilePath $java `
  -ArgumentList @('-Xms128M', '-Xmx512M', '-Xss512k', '-XX:CICompilerCount=2', '-XX:TieredStopAtLevel=1', '-XX:HeapBaseMinAddress=34g', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled', '-Dfile.encoding=UTF-8', '-jar', ('"' + $paperJarPath + '"'), 'nogui') `
  -WorkingDirectory $ServerDir `
  -RedirectStandardOutput $LogPath `
  -RedirectStandardError $ErrPath `
  -WindowStyle Hidden `
  -PassThru

$ready = $false
for ($second = 0; $second -lt $ReadyTimeoutSeconds; $second++) {
  $paper.Refresh()
  if ($paper.HasExited) {
    if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
      Get-Content -LiteralPath $LogPath -Tail 120
    }
    if (Test-Path -LiteralPath $ErrPath -PathType Leaf) {
      Get-Content -LiteralPath $ErrPath -Tail 60
    }
    throw "Local Paper PID $($paper.Id) exited during startup with code $($paper.ExitCode)."
  }
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
