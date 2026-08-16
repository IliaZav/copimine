[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [string] $Version = '1.0.0',
    [string] $InstanceReleaseRoot = '',
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

$bootstrapSource = if ([string]::IsNullOrWhiteSpace($InstanceReleaseRoot)) {
    # instance-current is the promoted local release pointer. The older
    # instance directory is retained only as historical evidence and must
    # never silently become the bootstrap source for a new installer.
    Join-Path $repoRoot 'artifacts/launcher/Release/instance-current'
} else {
    (Resolve-Path -LiteralPath $InstanceReleaseRoot -ErrorAction Stop).Path
}
$bootstrapFiles = Join-Path $bootstrapSource 'files'
foreach ($required in @(
    (Join-Path $bootstrapSource 'instance-manifest.json'),
    (Join-Path $bootstrapSource 'instance-manifest.sig'),
    $bootstrapFiles
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Launcher bootstrap artifact is missing: $required. Build/stage the signed instance release before packaging the installer."
    }
}

$bootstrapDestination = Join-Path $publishRoot 'launcher-bootstrap'
New-Item -ItemType Directory -Path (Join-Path $bootstrapDestination 'files') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $bootstrapSource 'instance-manifest.json') -Destination $bootstrapDestination -Force
Copy-Item -LiteralPath (Join-Path $bootstrapSource 'instance-manifest.sig') -Destination $bootstrapDestination -Force
Get-ChildItem -LiteralPath $bootstrapFiles -File | Copy-Item -Destination (Join-Path $bootstrapDestination 'files') -Force

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
    --msi `
    --instLocation Either `
    --outputDir $packageRoot
if ($LASTEXITCODE -ne 0) {
    throw "vpk packaging failed with exit code $LASTEXITCODE"
}

$setupSource = Join-Path $packageRoot 'CopiMineLauncher-win-Setup.exe'
$installerPath = Join-Path $packageRoot "CopiMineLauncherSetup-$Version.exe"
$msiSource = Join-Path $packageRoot 'CopiMineLauncher-win.msi'
$msiPath = Join-Path $packageRoot "CopiMineLauncherSetup-$Version.msi"
if (-not (Test-Path -LiteralPath $setupSource -PathType Leaf)) {
    throw "Velopack did not produce the expected setup bundle: $setupSource"
}
if (-not (Test-Path -LiteralPath $msiSource -PathType Leaf)) {
    throw "Velopack did not produce the expected folder-selecting MSI: $msiSource"
}
Copy-Item -LiteralPath $setupSource -Destination $installerPath -Force
Copy-Item -LiteralPath $msiSource -Destination $msiPath -Force

Write-Output "PUBLISH_OUTPUT=$publishRoot"
Write-Output "PACKAGE_OUTPUT=$packageRoot"
Write-Output "INSTALLER_OUTPUT=$installerPath"
Write-Output "MSI_OUTPUT=$msiPath"
