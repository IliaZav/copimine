[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [string] $Version = '1.0.0',
    [switch] $SkipPackaging
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$project = Join-Path $repoRoot 'CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj'
$publishRoot = Join-Path $repoRoot "artifacts/launcher/$Configuration/publish"
$packageRoot = Join-Path $repoRoot "artifacts/launcher/$Configuration/packages"

if (-not (Test-Path -LiteralPath $project -PathType Leaf)) {
    throw "Launcher project was not found: $project"
}

if (Test-Path -LiteralPath $publishRoot) {
    Remove-Item -LiteralPath $publishRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $publishRoot -Force | Out-Null

dotnet publish $project `
    --configuration $Configuration `
    --runtime win-x64 `
    --self-contained true `
    --property:Version=$Version `
    --output $publishRoot
if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish failed with exit code $LASTEXITCODE"
}

$publishedExe = Join-Path $publishRoot 'CopiMineLauncher.App.exe'
if (-not (Test-Path -LiteralPath $publishedExe -PathType Leaf)) {
    throw "Published Launcher executable was not produced: $publishedExe"
}

if ($SkipPackaging) {
    Write-Output "PUBLISH_OUTPUT=$publishRoot"
    exit 0
}

$vpk = Get-Command vpk -ErrorAction SilentlyContinue
$vpkPath = if ($null -ne $vpk) {
    $vpk.Source
} else {
    $localVpk = Join-Path $repoRoot 'artifacts/tools/vpk/vpk.exe'
    if (Test-Path -LiteralPath $localVpk -PathType Leaf) { $localVpk } else { $null }
}
if ([string]::IsNullOrWhiteSpace($vpkPath)) {
    throw 'vpk 1.2.0 is required for packaging. Install the pinned Velopack CLI or place it at artifacts/tools/vpk/vpk.exe before running without -SkipPackaging.'
}

if (Test-Path -LiteralPath $packageRoot) {
    Remove-Item -LiteralPath $packageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null

& $vpkPath pack `
    --packId CopiMineLauncher `
    --packTitle 'CopiMine Launcher' `
    --packVersion $Version `
    --packDir $publishRoot `
    --mainExe 'CopiMineLauncher.App.exe' `
    --outputDir $packageRoot
if ($LASTEXITCODE -ne 0) {
    throw "vpk packaging failed with exit code $LASTEXITCODE"
}

$setupSource = Join-Path $packageRoot 'CopiMineLauncher-win-Setup.exe'
$installerPath = Join-Path $packageRoot "CopiMineLauncherSetup-$Version.exe"
if (-not (Test-Path -LiteralPath $setupSource -PathType Leaf)) {
    throw "Velopack did not produce the expected setup bundle: $setupSource"
}
Copy-Item -LiteralPath $setupSource -Destination $installerPath -Force

Write-Output "PUBLISH_OUTPUT=$publishRoot"
Write-Output "PACKAGE_OUTPUT=$packageRoot"
Write-Output "INSTALLER_OUTPUT=$installerPath"
