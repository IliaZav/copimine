[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallerPath,
    [string] $CustomInstallerPath = "",
    [Parameter(Mandatory = $true)]
    [string] $MsiPath,
    [Parameter(Mandatory = $true)]
    [string] $MetadataPath,
    [Parameter(Mandatory = $true)]
    [string] $OutputRoot,
    [string] $InstanceReleaseRoot = "",
    [switch] $ServerHostedRuntimeOnly
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$frontendRoot = Join-Path $repoRoot 'admin-web/frontend'
$sourceInstaller = (Resolve-Path -LiteralPath $InstallerPath -ErrorAction Stop).Path
$sourceCustomInstaller = $null
if (-not [string]::IsNullOrWhiteSpace($CustomInstallerPath)) {
    $sourceCustomInstaller = (Resolve-Path -LiteralPath $CustomInstallerPath -ErrorAction Stop).Path
}
$sourceMsi = (Resolve-Path -LiteralPath $MsiPath -ErrorAction Stop).Path
$sourceMetadata = (Resolve-Path -LiteralPath $MetadataPath -ErrorAction Stop).Path
$sourceFeedIndex = Join-Path $frontendRoot 'launcher-feed-index.html'
$packageRoot = Split-Path -Parent $sourceInstaller
$publishRoot = Join-Path (Split-Path -Parent $packageRoot) 'publish'
$offlineRoot = Join-Path $publishRoot 'launcher-bootstrap'
$offlineMetadata = Join-Path $offlineRoot 'offline-minecraft-baseline.json'
$offlineArchive = Join-Path $offlineRoot 'offline-minecraft-baseline.zip'
$webView2Standalone = Join-Path $publishRoot 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe'
$sourceInstance = if ([string]::IsNullOrWhiteSpace($InstanceReleaseRoot)) { $null } else { (Resolve-Path -LiteralPath $InstanceReleaseRoot -ErrorAction Stop).Path }
$destination = [System.IO.Path]::GetFullPath($OutputRoot)

if (-not (Test-Path -LiteralPath $frontendRoot -PathType Container)) { throw "Frontend root is missing: $frontendRoot" }
if (-not (Test-Path -LiteralPath $sourceFeedIndex -PathType Leaf)) { throw "Launcher feed index is missing: $sourceFeedIndex" }
if (-not (Test-Path -LiteralPath $sourceInstaller -PathType Leaf)) { throw "Installer is missing: $sourceInstaller" }
if ($null -ne $sourceCustomInstaller -and -not (Test-Path -LiteralPath $sourceCustomInstaller -PathType Leaf)) { throw "Custom installer is missing: $sourceCustomInstaller" }
if (-not (Test-Path -LiteralPath $sourceMsi -PathType Leaf)) { throw "MSI is missing: $sourceMsi" }
if (-not (Test-Path -LiteralPath $sourceMetadata -PathType Leaf)) { throw "Metadata is missing: $sourceMetadata" }

function Get-Sha256Lower([string] $Path) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $stream = $null
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        return ([BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '')).ToLowerInvariant()
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
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
$metadataCustomFilename = [string]$metadata.customInstallerFilename
if (-not [string]::IsNullOrWhiteSpace($metadataCustomFilename)) {
    if ($null -eq $sourceCustomInstaller) { throw 'Metadata contains a custom folder installer but CustomInstallerPath was not provided.' }
    $customInfo = Get-Item -LiteralPath $sourceCustomInstaller -ErrorAction Stop
    $expectedCustomUrl = "/downloads/launcher/$metadataCustomFilename"
    if ([string]$metadata.customInstallerDownloadUrl -ne $expectedCustomUrl) {
        throw "Custom installer metadata URL is not tied to its filename: $($metadata.customInstallerDownloadUrl)"
    }
    if ($metadataCustomFilename -ne $customInfo.Name -or [long]$metadata.customInstallerSizeBytes -ne [long]$customInfo.Length) {
        throw "Custom installer metadata does not match the artifact: $metadataCustomFilename / $($customInfo.Name)"
    }
    $customHash = Get-Sha256Lower $sourceCustomInstaller
    if ([string]$metadata.customInstallerSha256 -ine $customHash) {
        throw "Custom installer metadata SHA-256 mismatch: metadata=$($metadata.customInstallerSha256), actual=$customHash"
    }
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
$legacyFeedAliases = @('releases.json', 'release.win.json', 'releases.windows.json')
foreach ($feedFileName in $feedFileNames) {
    $feedFile = Join-Path $packageRoot $feedFileName
    if (-not (Test-Path -LiteralPath $feedFile -PathType Leaf)) {
        throw "Velopack feed artifact is missing: $feedFile"
    }
}
$assetFeedDocument = Get-Content -Raw -LiteralPath (Join-Path $packageRoot 'assets.win.json') | ConvertFrom-Json
$assetFeed = @($assetFeedDocument)
while ($assetFeed.Count -eq 1 -and $assetFeed[0] -is [System.Array]) {
    $assetFeed = @($assetFeed[0])
}
foreach ($asset in $assetFeed) {
    $assetFileName = [string]$asset.RelativeFileName
    if ([string]::IsNullOrWhiteSpace($assetFileName) -or $assetFileName -notmatch '^[A-Za-z0-9._-]+$') {
        throw "Velopack asset filename is unsafe: $assetFileName"
    }
    $assetSource = Join-Path $packageRoot $assetFileName
    if (-not (Test-Path -LiteralPath $assetSource -PathType Leaf)) {
        throw "Velopack asset listed in assets.win.json is missing: $assetSource"
    }
}
$fullPackages = @(Get-ChildItem -LiteralPath $packageRoot -File -Filter '*-full.nupkg')
if ($fullPackages.Count -eq 0) {
    throw "Velopack full package is missing from the package directory: $packageRoot"
}
if (-not $ServerHostedRuntimeOnly) {
    foreach ($requiredOffline in @($offlineMetadata, $offlineArchive, $webView2Standalone)) {
        if (-not (Test-Path -LiteralPath $requiredOffline -PathType Leaf)) {
            throw "Offline Launcher artifact is missing: $requiredOffline"
        }
    }
    $offlineDocument = Get-Content -Raw -LiteralPath $offlineMetadata | ConvertFrom-Json
    $offlineInfo = Get-Item -LiteralPath $offlineArchive
    if ($offlineDocument.schemaVersion -ne 1 -or $offlineDocument.minecraftVersion -ne '1.21.1' -or $offlineDocument.fabricLoaderVersion -ne '0.19.3') {
        throw "Offline Minecraft metadata is invalid: $offlineMetadata"
    }
    if ([long]$offlineDocument.sizeBytes -ne [long]$offlineInfo.Length -or [string]$offlineDocument.sha256 -ine (Get-Sha256Lower $offlineArchive)) {
        throw "Offline Minecraft metadata does not match the archive: $offlineArchive"
    }
}
if (-not (Test-Path -LiteralPath $webView2Standalone -PathType Leaf)) {
    throw "WebView2 standalone artifact is missing: $webView2Standalone"
}
if ((Get-Item -LiteralPath $webView2Standalone).Length -lt 100MB) {
    throw "WebView2 standalone artifact is unexpectedly small: $webView2Standalone"
}
<#
The server-hosted release omits the duplicate offline-baseline copy from the
MSI. The signed minecraftRuntime archive remains once in launcher/files as a
verified bundled fallback and is also downloaded from CopiMine storage on
first repair/play when the local copy is unavailable.
#>

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
    if ($null -ne $sourceInstanceManifest.minecraftRuntime) { $instanceArtifacts += $sourceInstanceManifest.minecraftRuntime }
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
# The checkout may contain historical installer archives and an older launcher
# runtime under these two directories. They are release inputs, not website
# source. Copying them first made every new staging tree several gigabytes
# larger and let stale download links survive beside the current release.
Get-ChildItem -LiteralPath $frontendRoot -Force |
    Where-Object { $_.Name -notin @('downloads', 'launcher') } |
    Copy-Item -Destination $destination -Recurse -Force

$downloadDirectory = Join-Path $destination 'downloads/launcher'
$metadataDirectory = Join-Path $destination 'assets/public-data/launcher'
New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $metadataDirectory -Force | Out-Null
$filename = [System.IO.Path]::GetFileName([string]$metadata.filename)
if ([string]::IsNullOrWhiteSpace($filename) -or $filename -ne $metadata.filename -or $filename -notmatch '\.exe$') {
    throw "Metadata filename is not a safe installer filename: $($metadata.filename)"
}
Copy-Item -LiteralPath $sourceInstaller -Destination (Join-Path $downloadDirectory $filename) -Force
if ($null -ne $sourceCustomInstaller) {
    $customFilename = [System.IO.Path]::GetFileName($sourceCustomInstaller)
    if ($customFilename -notmatch '^[A-Za-z0-9._-]+\.exe$') {
        throw "Custom installer filename is unsafe: $customFilename"
    }
    Copy-Item -LiteralPath $sourceCustomInstaller -Destination (Join-Path $downloadDirectory $customFilename) -Force
}
Copy-Item -LiteralPath $sourceMsi -Destination (Join-Path $downloadDirectory ([System.IO.Path]::GetFileName($sourceMsi))) -Force
Copy-Item -LiteralPath $sourceMetadata -Destination (Join-Path $metadataDirectory 'latest.json') -Force
# A few older Launcher builds requested the public release metadata from the
# download directory instead of the canonical assets path. Keep that request
# as a compatibility alias so an already installed client does not turn a
# harmless feed migration into SELF_UPDATE_CHECK_FAILED (404).
Copy-Item -LiteralPath $sourceMetadata -Destination (Join-Path $downloadDirectory 'latest.json') -Force

# Velopack self-update checks the feed in this same directory.  Publishing
# only the public installer would make the Launcher install successfully but
# leave its in-app update path unable to discover a newer full package.
foreach ($feedFileName in $feedFileNames) {
    $feedFile = Join-Path $packageRoot $feedFileName
    Copy-Item -LiteralPath $feedFile -Destination (Join-Path $downloadDirectory $feedFileName) -Force
}
# Velopack 1.2 asks for releases.<channel>.json when the Launcher supplies
# its product channel (stable). vpk also emits releases.win.json for the
# Windows RID, so publish the same verified document under both names. This
# keeps existing installs from failing with a 404 while the public feed stays
# a single immutable release.
Copy-Item -LiteralPath (Join-Path $packageRoot 'releases.win.json') -Destination (Join-Path $downloadDirectory 'releases.stable.json') -Force
# Older Velopack clients used one of these generic Windows feed names. Keep
# them as immutable aliases of the same verified feed so an existing install
# does not surface SELF_UPDATE_CHECK_FAILED after an upgrade of the website.
foreach ($legacyFeedAlias in $legacyFeedAliases) {
    Copy-Item -LiteralPath (Join-Path $packageRoot 'releases.win.json') -Destination (Join-Path $downloadDirectory $legacyFeedAlias) -Force
}
$assetFeed | ForEach-Object {
    $assetFileName = [string]$_.RelativeFileName
    Copy-Item -LiteralPath (Join-Path $packageRoot $assetFileName) -Destination (Join-Path $downloadDirectory $assetFileName) -Force
}
# Keep the stable-channel alias in the public asset allowlist as well. The
# production FastAPI download route derives its safe filenames from this
# document, so an unlisted alias would still return 404 even when the file is
# present on disk.
$stagedAssetsFeedPath = Join-Path $downloadDirectory 'assets.win.json'
$stagedAssetsFeed = [System.Collections.Generic.List[object]]::new()
foreach ($asset in $assetFeed) {
    $stagedAssetsFeed.Add([ordered]@{
        RelativeFileName = [string]$asset.RelativeFileName
        Type = [string]$asset.Type
    })
}
$stagedAssetsFeed.Add([ordered]@{
    RelativeFileName = 'releases.stable.json'
    Type = 'ReleaseFeed'
})
$stagedAssetsFeed.Add([ordered]@{
    RelativeFileName = 'latest.json'
    Type = 'LauncherMetadataCompatibilityAlias'
})
foreach ($legacyFeedAlias in $legacyFeedAliases) {
    $stagedAssetsFeed.Add([ordered]@{
        RelativeFileName = $legacyFeedAlias
        Type = 'ReleaseFeedCompatibilityAlias'
    })
}
$stagedAssetsFeedJson = $stagedAssetsFeed | ConvertTo-Json -Depth 4 -Compress
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($stagedAssetsFeedPath, $stagedAssetsFeedJson + [Environment]::NewLine, $utf8NoBom)
$fullPackages | Copy-Item -Destination $downloadDirectory -Force
Copy-Item -LiteralPath $sourceFeedIndex -Destination (Join-Path $downloadDirectory 'index.html') -Force

$offlineDestination = Join-Path $destination 'launcher-bootstrap'
$webView2Destination = Join-Path $destination 'Assets/WebView2'
New-Item -ItemType Directory -Path $webView2Destination -Force | Out-Null
if (-not $ServerHostedRuntimeOnly) {
    New-Item -ItemType Directory -Path $offlineDestination -Force | Out-Null
    Copy-Item -LiteralPath $offlineMetadata -Destination (Join-Path $offlineDestination 'offline-minecraft-baseline.json') -Force
    Copy-Item -LiteralPath $offlineArchive -Destination (Join-Path $offlineDestination 'offline-minecraft-baseline.zip') -Force
}
Copy-Item -LiteralPath $webView2Standalone -Destination (Join-Path $webView2Destination 'MicrosoftEdgeWebView2RuntimeInstallerX64.exe') -Force

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
if ($null -ne $sourceCustomInstaller) { Write-Output "SITE_CUSTOM_INSTALLER=$(Join-Path $downloadDirectory ([System.IO.Path]::GetFileName($sourceCustomInstaller)))" }
Write-Output "SITE_MSI=$(Join-Path $downloadDirectory ([System.IO.Path]::GetFileName($sourceMsi)))"
Write-Output "SITE_METADATA=$(Join-Path $metadataDirectory 'latest.json')"
if ($null -ne $sourceInstance) { Write-Output "SITE_INSTANCE_MANIFEST=$(Join-Path $destination 'launcher/stable/instance-manifest.json')" }
