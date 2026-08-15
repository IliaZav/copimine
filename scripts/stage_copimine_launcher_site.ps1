[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallerPath,
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
$sourceMetadata = (Resolve-Path -LiteralPath $MetadataPath -ErrorAction Stop).Path
$sourceInstance = if ([string]::IsNullOrWhiteSpace($InstanceReleaseRoot)) { $null } else { (Resolve-Path -LiteralPath $InstanceReleaseRoot -ErrorAction Stop).Path }
$destination = [System.IO.Path]::GetFullPath($OutputRoot)

if (-not (Test-Path -LiteralPath $frontendRoot -PathType Container)) { throw "Frontend root is missing: $frontendRoot" }
if (-not (Test-Path -LiteralPath $sourceInstaller -PathType Leaf)) { throw "Installer is missing: $sourceInstaller" }
if (-not (Test-Path -LiteralPath $sourceMetadata -PathType Leaf)) { throw "Metadata is missing: $sourceMetadata" }

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
$metadata = Get-Content -Raw -LiteralPath $sourceMetadata | ConvertFrom-Json
$filename = [System.IO.Path]::GetFileName([string]$metadata.filename)
if ([string]::IsNullOrWhiteSpace($filename) -or $filename -ne $metadata.filename -or $filename -notmatch '\.exe$') {
    throw "Metadata filename is not a safe installer filename: $($metadata.filename)"
}
Copy-Item -LiteralPath $sourceInstaller -Destination (Join-Path $downloadDirectory $filename) -Force
Copy-Item -LiteralPath $sourceMetadata -Destination (Join-Path $metadataDirectory 'latest.json') -Force

if ($null -ne $sourceInstance) {
    $instanceManifest = Join-Path $sourceInstance 'instance-manifest.json'
    $instanceSignature = Join-Path $sourceInstance 'instance-manifest.sig'
    $instanceFiles = Join-Path $sourceInstance 'files'
    foreach ($required in @($instanceManifest, $instanceSignature, $instanceFiles)) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Instance release artifact is missing: $required" }
    }
    $instanceDestination = Join-Path $destination 'launcher/stable'
    $instanceFilesDestination = Join-Path $destination 'launcher/files'
    New-Item -ItemType Directory -Path $instanceDestination,$instanceFilesDestination -Force | Out-Null
    Copy-Item -LiteralPath $instanceManifest -Destination (Join-Path $instanceDestination 'instance-manifest.json') -Force
    Copy-Item -LiteralPath $instanceSignature -Destination (Join-Path $instanceDestination 'instance-manifest.sig') -Force
    Get-ChildItem -LiteralPath $instanceFiles -File | Copy-Item -Destination $instanceFilesDestination -Force
}

Write-Output "SITE_OUTPUT=$destination"
Write-Output "SITE_INSTALLER=$(Join-Path $downloadDirectory $filename)"
Write-Output "SITE_METADATA=$(Join-Path $metadataDirectory 'latest.json')"
if ($null -ne $sourceInstance) { Write-Output "SITE_INSTANCE_MANIFEST=$(Join-Path $destination 'launcher/stable/instance-manifest.json')" }
