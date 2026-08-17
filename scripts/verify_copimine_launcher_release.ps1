[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ArtifactRoot,
    [string] $MetadataPath = "",
    [string] $Version = "1.0.1",
    [switch] $RequireOfflineBundle
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $ArtifactRoot -ErrorAction Stop).Path
$metadata = if ([string]::IsNullOrWhiteSpace($MetadataPath)) {
    Join-Path $root 'latest.json'
} else {
    (Resolve-Path -LiteralPath $MetadataPath -ErrorAction Stop).Path
}

$payload = Get-Content -Raw -LiteralPath $metadata | ConvertFrom-Json
if ($payload.schemaVersion -ne 1 -or $payload.channel -ne 'stable') { throw 'launcher metadata schema/channel is invalid' }
if ($payload.version -ne $Version) { throw "metadata version mismatch: $($payload.version) != $Version" }
if ($payload.architecture -ne 'x64') { throw 'metadata architecture is not x64' }
if ($payload.sha256 -notmatch '^[0-9a-f]{64}$') { throw 'metadata sha256 is not lowercase 64-hex' }
if ($payload.downloadUrl -notmatch '^/downloads/launcher/[A-Za-z0-9._-]+\.exe$') { throw 'metadata downloadUrl is unsafe' }
if ($payload.releaseNotesUrl -notmatch '^/news/[A-Za-z0-9._-]+\.html$') { throw 'metadata releaseNotesUrl is unsafe' }

$installerName = [System.IO.Path]::GetFileName([string]$payload.filename)
$installer = Join-Path $root $installerName
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    $installer = Join-Path $root (Join-Path 'packages' $installerName)
}
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) { throw "installer is missing in artifact root or packages: $installerName" }
$bytes = [System.IO.File]::ReadAllBytes($installer)
if ($bytes.Length -lt 64 -or $bytes[0] -ne 0x4D -or $bytes[1] -ne 0x5A) { throw 'installer is not a PE file' }
$peOffset = [BitConverter]::ToInt32($bytes, 0x3C)
if ($peOffset -lt 64 -or $peOffset + 4 -gt $bytes.Length -or $bytes[$peOffset] -ne 0x50 -or $bytes[$peOffset + 1] -ne 0x45 -or $bytes[$peOffset + 2] -ne 0x00 -or $bytes[$peOffset + 3] -ne 0x00) { throw 'installer has no valid PE signature' }
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $digest = $sha256.ComputeHash($bytes)
} finally {
    $sha256.Dispose()
}
$hash = [BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()
if ($bytes.LongLength -ne [long]$payload.sizeBytes) { throw "installer size mismatch: $($bytes.LongLength) != $($payload.sizeBytes)" }
if ($hash -ne $payload.sha256) { throw "installer SHA-256 mismatch: $hash != $($payload.sha256)" }

if ($null -ne $payload.msiFilename) {
    if ([string]$payload.msiFilename -notmatch '^[A-Za-z0-9._-]+\.msi$') { throw 'metadata msiFilename is unsafe' }
    if ([string]$payload.msiDownloadUrl -notmatch '^/downloads/launcher/[A-Za-z0-9._-]+\.msi$') { throw 'metadata msiDownloadUrl is unsafe' }
    if ([string]$payload.msiInstallLocation -ne 'choose') { throw 'metadata msiInstallLocation must be choose' }
    if ([long]$payload.msiSizeBytes -le 0 -or [string]$payload.msiSha256 -notmatch '^[0-9a-f]{64}$') { throw 'metadata MSI integrity fields are invalid' }
    $msiName = [System.IO.Path]::GetFileName([string]$payload.msiFilename)
    $msi = Join-Path $root $msiName
    if (-not (Test-Path -LiteralPath $msi -PathType Leaf)) {
        $msi = Join-Path $root (Join-Path 'packages' $msiName)
    }
    if (-not (Test-Path -LiteralPath $msi -PathType Leaf)) { throw "MSI is missing in artifact root or packages: $msiName" }
    $msiBytes = [System.IO.File]::ReadAllBytes($msi)
    $msiSha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $msiDigest = $msiSha.ComputeHash($msiBytes)
    } finally {
        $msiSha.Dispose()
    }
    $msiHash = [BitConverter]::ToString($msiDigest).Replace('-', '').ToLowerInvariant()
    if ($msiBytes.LongLength -ne [long]$payload.msiSizeBytes) { throw "MSI size mismatch: $($msiBytes.LongLength) != $($payload.msiSizeBytes)" }
    if ($msiHash -ne [string]$payload.msiSha256) { throw "MSI SHA-256 mismatch: $msiHash != $($payload.msiSha256)" }
    Write-Output "MSI=$msi"
    Write-Output "MSI_SHA256=$msiHash"
    Write-Output "MSI_SIZE=$($msiBytes.LongLength)"
}

if ($RequireOfflineBundle) {
    $publishRoot = Join-Path $root 'publish'
    $offlineMetadataPath = Join-Path $publishRoot 'launcher-bootstrap/offline-minecraft-baseline.json'
    $offlineArchivePath = Join-Path $publishRoot 'launcher-bootstrap/offline-minecraft-baseline.zip'
    $webView2Path = Join-Path $publishRoot 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe'
    foreach ($required in @($offlineMetadataPath, $offlineArchivePath, $webView2Path)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "Required offline bundle file is missing: $required"
        }
    }

    $offlineMetadata = Get-Content -LiteralPath $offlineMetadataPath -Raw | ConvertFrom-Json
    if (($offlineMetadata.schemaVersion -ne 1) -or ($offlineMetadata.minecraftVersion -ne '1.21.1') -or ($offlineMetadata.fabricLoaderVersion -ne '0.19.3') -or ($offlineMetadata.archiveFileName -ne 'offline-minecraft-baseline.zip') -or ($offlineMetadata.sha256 -notmatch '^[0-9a-f]{64}$')) {
        throw 'Offline Minecraft baseline metadata is invalid.'
    }
    $offlineInfo = Get-Item -LiteralPath $offlineArchivePath
    if ([int64]$offlineInfo.Length -ne [int64]$offlineMetadata.sizeBytes) {
        throw "Offline Minecraft baseline size mismatch: $($offlineInfo.Length) != $($offlineMetadata.sizeBytes)"
    }
    $offlineHash = (Get-FileHash -LiteralPath $offlineArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($offlineHash -ne [string]$offlineMetadata.sha256) {
        throw "Offline Minecraft baseline SHA-256 mismatch: $offlineHash != $($offlineMetadata.sha256)"
    }
    $webViewInfo = Get-Item -LiteralPath $webView2Path
    if ($webViewInfo.Length -lt 100MB) {
        throw "WebView2 standalone installer is unexpectedly small: $($webViewInfo.Length) bytes"
    }
    Write-Output "OFFLINE_BASELINE_SHA256=$offlineHash"
    Write-Output "OFFLINE_BASELINE_SIZE=$($offlineInfo.Length)"
    Write-Output "WEBVIEW2_STANDALONE_SIZE=$($webViewInfo.Length)"
}

Write-Output "RELEASE_VERIFY=PASS"
Write-Output "INSTALLER=$installer"
Write-Output "SHA256=$hash"
Write-Output "SIZE=$($bytes.LongLength)"
