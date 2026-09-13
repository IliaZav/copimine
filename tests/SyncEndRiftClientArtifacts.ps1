[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$SourceClientJar,
  [Parameter(Mandatory = $true)][string]$SourceResourcePack,
  [Parameter(Mandatory = $true)][string]$ClientGameDirectory,
  [switch]$SkipClientProcessCheck
)

$ErrorActionPreference = 'Stop'

function Resolve-ExistingFile {
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Label)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "$Label is not a file: $Path"
  }
  return (Resolve-Path -LiteralPath $Path).Path
}

function Resolve-ExistingDirectory {
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Label)
  if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
    throw "$Label is not a directory: $Path"
  }
  return (Resolve-Path -LiteralPath $Path).Path
}

function Assert-UnderRoot {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$Label
  )
  $separator = [IO.Path]::DirectorySeparatorChar
  $candidate = [IO.Path]::GetFullPath($Path).TrimEnd($separator) + $separator
  $allowed = [IO.Path]::GetFullPath($Root).TrimEnd($separator) + $separator
  if (-not $candidate.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw "$Label is outside the selected Minecraft profile: $Path"
  }
}

function Get-FileSha256 {
  param([Parameter(Mandatory = $true)][string]$Path)
  $algorithm = [Security.Cryptography.SHA256]::Create()
  try {
    $stream = [IO.File]::OpenRead($Path)
    try {
      return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    } finally {
      $stream.Dispose()
    }
  } finally {
    $algorithm.Dispose()
  }
}

function Test-FabricMinecraftClientRunning {
  return @(
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
      Where-Object {
        $_.Name -ieq 'javaw.exe' -and $_.CommandLine -and
        $_.CommandLine -match '(?i)net\.fabricmc\.loader\.impl\.launch\.knot\.KnotClient'
      }
  ).Count -gt 0
}

function New-BackupPath {
  param(
    [Parameter(Mandatory = $true)][string]$Directory,
    [Parameter(Mandatory = $true)][string]$Name
  )
  $candidate = Join-Path $Directory $Name
  $suffix = 1
  while (Test-Path -LiteralPath $candidate) {
    $candidate = Join-Path $Directory (([IO.Path]::GetFileNameWithoutExtension($Name)) + "-$suffix" + [IO.Path]::GetExtension($Name))
    $suffix++
  }
  return $candidate
}

$clientJar = Resolve-ExistingFile -Path $SourceClientJar -Label 'Source CopiMineClient JAR'
$resourcePack = Resolve-ExistingFile -Path $SourceResourcePack -Label 'Source resource pack'
$profile = Resolve-ExistingDirectory -Path $ClientGameDirectory -Label 'Minecraft game directory'
$mods = Resolve-ExistingDirectory -Path (Join-Path $profile 'mods') -Label 'Minecraft mods directory'
$resourcePacks = Resolve-ExistingDirectory -Path (Join-Path $profile 'resourcepacks') -Label 'Minecraft resource-packs directory'
$targetClientJar = Join-Path $mods 'CopiMineClient-0.1.1.jar'
$targetResourcePack = Join-Path $resourcePacks 'CopiMineResourcePack.zip'
Assert-UnderRoot -Path $targetClientJar -Root $profile -Label 'Client JAR target'
Assert-UnderRoot -Path $targetResourcePack -Root $profile -Label 'Resource-pack target'

if ([IO.Path]::GetFullPath($clientJar) -eq [IO.Path]::GetFullPath($targetClientJar) -or
    [IO.Path]::GetFullPath($resourcePack) -eq [IO.Path]::GetFullPath($targetResourcePack)) {
  throw 'Refused to use a target-profile artifact as the source artifact.'
}

$sourceClientHash = Get-FileSha256 -Path $clientJar
$sourcePackHash = Get-FileSha256 -Path $resourcePack
$copiMineClientJars = @(Get-ChildItem -LiteralPath $mods -Filter 'CopiMineClient-*.jar' -File | Sort-Object Name)
$needsClientSync = $copiMineClientJars.Count -ne 1 -or
  -not (Test-Path -LiteralPath $targetClientJar -PathType Leaf) -or
  (Get-FileSha256 -Path $targetClientJar) -ne $sourceClientHash
$needsPackSync = -not (Test-Path -LiteralPath $targetResourcePack -PathType Leaf) -or
  (Get-FileSha256 -Path $targetResourcePack) -ne $sourcePackHash

if (($needsClientSync -or $needsPackSync) -and -not $SkipClientProcessCheck -and
    (Test-FabricMinecraftClientRunning)) {
  throw 'Minecraft Fabric client is running with artifacts that need replacement. Close and restart Minecraft before synchronizing End Rift client assets.'
}

if ($needsClientSync) {
  $clientBackups = Join-Path $mods 'copimineclient-backups'
  New-Item -ItemType Directory -Path $clientBackups -Force | Out-Null
  Assert-UnderRoot -Path $clientBackups -Root $profile -Label 'Client JAR backup directory'
  $timestamp = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
  foreach ($oldJar in $copiMineClientJars) {
    $backupName = ([IO.Path]::GetFileNameWithoutExtension($oldJar.Name)) + "-$timestamp" + [IO.Path]::GetExtension($oldJar.Name)
    $backup = New-BackupPath -Directory $clientBackups -Name $backupName
    Move-Item -LiteralPath $oldJar.FullName -Destination $backup
  }
  Copy-Item -LiteralPath $clientJar -Destination $targetClientJar
}

if ((Get-FileSha256 -Path $targetClientJar) -ne $sourceClientHash) {
  throw 'CopiMineClient SHA-256 mismatch after local profile synchronization.'
}

if ($needsPackSync) {
  $packBackups = Join-Path $resourcePacks 'copimine-resourcepack-backups'
  New-Item -ItemType Directory -Path $packBackups -Force | Out-Null
  Assert-UnderRoot -Path $packBackups -Root $profile -Label 'Resource-pack backup directory'
  if (Test-Path -LiteralPath $targetResourcePack -PathType Leaf) {
    $backup = New-BackupPath -Directory $packBackups -Name ('CopiMineResourcePack-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff') + '.zip')
    Move-Item -LiteralPath $targetResourcePack -Destination $backup
  }
  Copy-Item -LiteralPath $resourcePack -Destination $targetResourcePack
}

if ((Get-FileSha256 -Path $targetResourcePack) -ne $sourcePackHash) {
  throw 'Resource-pack SHA-256 mismatch after local profile synchronization.'
}

Write-Output ('END_RIFT_CLIENT_ARTIFACTS_SYNCED client_sha256=' + $sourceClientHash +
  ' pack_sha256=' + $sourcePackHash + ' profile=' + $profile)
