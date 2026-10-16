[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,

    [Parameter(Mandatory = $true)]
    [string]$TargetRoot
)

$ErrorActionPreference = 'Stop'
$sourceRootPath = (Resolve-Path -LiteralPath $SourceRoot).Path
$targetRootPath = (Resolve-Path -LiteralPath $TargetRoot).Path
$builder = Join-Path $sourceRootPath 'resourcepacks\build-resourcepack.ps1'
$sync = Join-Path $sourceRootPath 'scripts\InstallEndRiftLocalArtifacts.ps1'

if (-not (Test-Path -LiteralPath $builder -PathType Leaf)) {
    throw "End Rift resource pack builder is missing: $builder"
}
if (-not (Test-Path -LiteralPath $sync -PathType Leaf)) {
    throw "End Rift resource pack sync script is missing: $sync"
}

Write-Host "Building End Rift resource pack from $sourceRootPath"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $builder
if ($LASTEXITCODE -ne 0) {
    throw "Resource pack build failed with exit code $LASTEXITCODE"
}

Write-Host "Verifying and installing End Rift resource pack into $targetRootPath"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $sync -SourceRoot $sourceRootPath -TargetRoot $targetRootPath
if ($LASTEXITCODE -ne 0) {
    throw "Resource pack sync failed with exit code $LASTEXITCODE"
}
