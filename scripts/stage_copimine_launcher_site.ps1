[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallerPath,
    [Parameter(Mandatory = $true)]
    [string] $MsiPath,
    [Parameter(Mandatory = $true)]
    [string] $MetadataPath,
    [Parameter(Mandatory = $true)]
    [string] $OutputRoot,
    [string] $InstanceReleaseRoot = ""
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$frontendRoot = Join-Path $repoRoot 'admin-web/frontend'
$sourceInstaller = (Resolve-Path -LiteralPath $InstallerPath -ErrorAction Stop).Path
$sourceMsi = (Resolve-Path -LiteralPath $MsiPath -ErrorAction Stop).Path
$sourceMetadata = (Resolve-Path -LiteralPath $MetadataPath -ErrorAction Stop).Path
$packageRoot = Split-Path -Parent $sourceInstaller
$sourceInstance = if ([string]::IsNullOrWhiteSpace($InstanceReleaseRoot)) { $null } else { (Resolve-Path -LiteralPath $InstanceReleaseRoot -ErrorAction Stop).Path }
$destination = [System.IO.Path]::GetFullPath($OutputRoot)

if (-not (Test-Path -LiteralPath $frontendRoot -PathType Container)) { throw "Frontend root is missing: $frontendRoot" }
if (-not (Test-Path -LiteralPath $sourceInstaller -PathType Leaf)) { throw "Installer is missing: $sourceInstaller" }
if (-not (Test-Path -LiteralPath $sourceMsi -PathType Leaf)) { throw "MSI is missing: $sourceMsi" }
if (-not (Test-Path -LiteralPath $sourceMetadata -PathType Leaf)) { throw "Metadata is missing: $sourceMetadata" }

function Get-Sha256Lower([string] $Path) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.IO.File]::ReadAllBytes($Path)
        return ([BitConverter]::ToString($sha256.ComputeHash($bytes)).Replace('-', '')).ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

$metadata = Get-Content -Raw -LiteralPath $sourceMetadata | ConvertFrom-Json
$installerInfo = Get-Item -LiteralPath $sourceInstaller -ErrorAction Stop
$metadataFilename = [string]$metadata.filename
if ([string]::IsNullOrWhiteSpace($metadataFilename) -or $metadataFilename -ne $installerInfo.Name) {
    throw "Launcher metadata filename does not match the installer: $metadataFilename / $($installerInfo.Name)"
}
if ([long]$metadata.sizeBytes -ne [long]$installerInfo.Length) {
    throw "Launcher metadata size mismatch: metadata=$($metadata.sizeBytes), actual=$($installerInfo.Length)"
}
$installerHash = Get-Sha256Lower $sourceInstaller
if ([string]$metadata.sha256 -ine $installerHash) {
    throw "Launcher metadata SHA-256 mismatch: metadata=$($metadata.sha256), actual=$installerHash"
}

$msiInfo = Get-Item -LiteralPath $sourceMsi -ErrorAction Stop
$metadataMsiFilename = [string]$metadata.msiFilename
if ([string]::IsNullOrWhiteSpace($metadataMsiFilename) -or $metadataMsiFilename -ne $msiInfo.Name) {
    throw "MSI metadata filename does not match the MSI: $metadataMsiFilename / $($msiInfo.Name)"
}
if ([long]$metadata.msiSizeBytes -ne [long]$msiInfo.Length) {
    throw "MSI metadata size mismatch: metadata=$($metadata.msiSizeBytes), actual=$($msiInfo.Length)"
}
$msiHash = Get-Sha256Lower $sourceMsi
if ([string]$metadata.msiSha256 -ine $msiHash) {
    throw "MSI metadata SHA-256 mismatch: metadata=$($metadata.msiSha256), actual=$msiHash"
}

$feedFileNames = @('RELEASES', 'releases.win.json', 'assets.win.json')
foreach ($feedFileName in $feedFileNames) {
    $feedFile = Join-Path $packageRoot $feedFileName
    if (-not (Test-Path -LiteralPath $feedFile -PathType Leaf)) {
        throw "Velopack feed artifact is missing: $feedFile"
    }
}
$fullPackages = @(Get-ChildItem -LiteralPath $packageRoot -File -Filter '*-full.nupkg')
if ($fullPackages.Count -eq 0) {
    throw "Velopack full package is missing from the package directory: $packageRoot"
}

$sourceInstanceManifest = $null
if ($null -ne $sourceInstance) {
    $instanceManifest = Join-Path $sourceInstance 'instance-manifest.json'
    $instanceSignature = Join-Path $sourceInstance 'instance-manifest.sig'
    $instancePublicKey = Join-Path $sourceInstance 'public-key.hex'
    $instanceFiles = Join-Path $sourceInstance 'files'
    foreach ($required in @($instanceManifest, $instanceSignature, $instanceFiles)) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Instance release artifact is missing: $required" }
    }
    if ((Get-Item -LiteralPath $instanceSignature).Length -le 0) {
        throw "Instance release signature is empty: $instanceSignature"
    }
    try {
        $sourceInstanceManifest = Get-Content -Raw -LiteralPath $instanceManifest | ConvertFrom-Json
    } catch {
        throw "Instance release manifest is not valid JSON: $instanceManifest"
    }
    if ([int]$sourceInstanceManifest.schemaVersion -ne 2 -or [string]$sourceInstanceManifest.channel -ne 'stable') {
        throw "Instance release manifest is not a stable schema-2 manifest: $instanceManifest"
    }
    if ([string]::IsNullOrWhiteSpace([string]$sourceInstanceManifest.releaseId) -or [long]$sourceInstanceManifest.releaseSequence -le 0 -or [string]::IsNullOrWhiteSpace([string]$sourceInstanceManifest.publicKeyId)) {
        throw "Instance release manifest is missing release identity: $instanceManifest"
    }
    $instanceArtifacts = @($sourceInstanceManifest.files)
    if ($null -ne $sourceInstanceManifest.javaRuntime) { $instanceArtifacts += $sourceInstanceManifest.javaRuntime }
    if ($instanceArtifacts.Count -eq 0) { throw "Instance release manifest has no artifacts: $instanceManifest" }
    foreach ($artifact in $instanceArtifacts) {
        $digest = [string]$artifact.sha256
        if ($digest -notmatch '^[0-9a-fA-F]{64}$') { throw "Instance artifact SHA-256 is invalid: $digest" }
        $artifactPath = Join-Path $instanceFiles $digest
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) { throw "Instance artifact is missing: $artifactPath" }
        $expectedSize = if ($artifact.PSObject.Properties.Name -contains 'size') { [long]$artifact.size } else { [long]$artifact.sizeBytes }
        $artifactInfo = Get-Item -LiteralPath $artifactPath
        if ($expectedSize -le 0 -or [long]$artifactInfo.Length -ne $expectedSize) {
            throw "Instance artifact size mismatch: $artifactPath"
        }
        $actualArtifactHash = Get-Sha256Lower $artifactPath
        if ($actualArtifactHash -ine $digest.ToLowerInvariant()) {
            throw "Instance artifact SHA-256 mismatch: $artifactPath"
        }
    }
}

if (Test-Path -LiteralPath $destination) {
    $resolvedDestination = (Resolve-Path -LiteralPath $destination).Path
    if (-not $resolvedDestination.StartsWith((Join-Path $repoRoot 'artifacts/launcher'), [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace a staging directory outside artifacts/launcher: $resolvedDestination"
    }
    Remove-Item -LiteralPath $resolvedDestination -Recurse -Force
}
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Get-ChildItem -LiteralPath $frontendRoot -Force | Copy-Item -Destination $destination -Recurse -Force

$downloadDirectory = Join-Path $destination 'downloads/launcher'
$metadataDirectory = Join-Path $destination 'assets/public-data/launcher'
New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $metadataDirectory -Force | Out-Null
$filename = [System.IO.Path]::GetFileName([string]$metadata.filename)
if ([string]::IsNullOrWhiteSpace($filename) -or $filename -ne $metadata.filename -or $filename -notmatch '\.exe$') {
    throw "Metadata filename is not a safe installer filename: $($metadata.filename)"
}
Copy-Item -LiteralPath $sourceInstaller -Destination (Join-Path $downloadDirectory $filename) -Force
Copy-Item -LiteralPath $sourceMsi -Destination (Join-Path $downloadDirectory ([System.IO.Path]::GetFileName($sourceMsi))) -Force
Copy-Item -LiteralPath $sourceMetadata -Destination (Join-Path $metadataDirectory 'latest.json') -Force

# Velopack self-update checks the feed in this same directory.  Publishing
# only the public installer would make the Launcher install successfully but
# leave its in-app update path unable to discover a newer full package.
foreach ($feedFileName in $feedFileNames) {
    $feedFile = Join-Path $packageRoot $feedFileName
    Copy-Item -LiteralPath $feedFile -Destination (Join-Path $downloadDirectory $feedFileName) -Force
}
$fullPackages | Copy-Item -Destination $downloadDirectory -Force

if ($null -ne $sourceInstance) {
    $instanceFiles = Join-Path $sourceInstance 'files'
    $instanceDestination = Join-Path $destination 'launcher/stable'
    $instanceFilesDestination = Join-Path $destination 'launcher/files'
    New-Item -ItemType Directory -Path $instanceDestination,$instanceFilesDestination -Force | Out-Null
    Copy-Item -LiteralPath $instanceManifest -Destination (Join-Path $instanceDestination 'instance-manifest.json') -Force
    Copy-Item -LiteralPath $instanceSignature -Destination (Join-Path $instanceDestination 'instance-manifest.sig') -Force
    if (Test-Path -LiteralPath $instancePublicKey -PathType Leaf) {
        Copy-Item -LiteralPath $instancePublicKey -Destination (Join-Path $instanceDestination 'public-key.hex') -Force
    }
    Get-ChildItem -LiteralPath $instanceFiles -File | Copy-Item -Destination $instanceFilesDestination -Force
}

Write-Output "SITE_OUTPUT=$destination"
Write-Output "SITE_INSTALLER=$(Join-Path $downloadDirectory $filename)"
Write-Output "SITE_MSI=$(Join-Path $downloadDirectory ([System.IO.Path]::GetFileName($sourceMsi)))"
Write-Output "SITE_METADATA=$(Join-Path $metadataDirectory 'latest.json')"
if ($null -ne $sourceInstance) { Write-Output "SITE_INSTANCE_MANIFEST=$(Join-Path $destination 'launcher/stable/instance-manifest.json')" }
