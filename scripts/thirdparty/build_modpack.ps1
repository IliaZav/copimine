param(
    [string]$ProjectRoot = "",
    # Normal archive construction uses the pinned staged inputs.  This
    # engineering-only opt-in promotes a freshly built first-party client,
    # updates its local integrity metadata, then lets the usual checksum gate
    # validate the complete archive.
    [switch]$SyncBuiltClient
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
}

$stage = Join-Path $ProjectRoot "thirdparty\_modpack_stage"
$zip = Join-Path $ProjectRoot "thirdparty\CopiMineMods.zip"
$sha = Join-Path $ProjectRoot "thirdparty\CopiMineMods.sha1"
$sha256 = Join-Path $ProjectRoot "thirdparty\CopiMineMods.sha256"
$frontendPublicDataDir = Join-Path $ProjectRoot "admin-web\frontend\assets\public-data"
$frontendSnapshot = Join-Path $frontendPublicDataDir "modpack_snapshot.json"
$checksumsPath = Join-Path $ProjectRoot "thirdparty\checksums.txt"

function Write-Utf8NoBomJson {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($LiteralPath, $Content + [Environment]::NewLine, $utf8NoBom)
}

function Get-Sha256Lower {
    param([Parameter(Mandatory = $true)][string]$LiteralPath)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $LiteralPath).Hash.ToLowerInvariant()
}

function Sync-BuiltClientArtifact {
    $sourceClientJar = Join-Path $ProjectRoot "CopiMineClient\build\libs\CopiMineClient-0.1.1.jar"
    $stagedClientJar = Join-Path $ProjectRoot "thirdparty\client-mods\CopiMineClient-0.1.1.jar"
    if (-not (Test-Path -LiteralPath $sourceClientJar -PathType Leaf)) {
        throw "Cannot sync a missing built client artifact: $sourceClientJar"
    }
    if (-not (Test-Path -LiteralPath (Split-Path -Parent $stagedClientJar) -PathType Container)) {
        throw "Missing staged client-mods directory: $(Split-Path -Parent $stagedClientJar)"
    }

    Copy-Item -LiteralPath $sourceClientJar -Destination $stagedClientJar -Force
    $stagedSha256 = Get-Sha256Lower -LiteralPath $stagedClientJar
    $sourceSha256 = Get-Sha256Lower -LiteralPath $sourceClientJar
    if ($stagedSha256 -ne $sourceSha256) {
        throw "Client JAR SHA-256 mismatch after staging synchronization."
    }

    $manifestPath = Join-Path $ProjectRoot "thirdparty\thirdparty_manifest.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $clientRows = @($manifest.artifacts.clientMods | Where-Object {
        $_.path -eq "thirdparty/client-mods/CopiMineClient-0.1.1.jar"
    })
    if ($clientRows.Count -ne 1) {
        throw "Expected exactly one staged CopiMineClient manifest row; found $($clientRows.Count)."
    }
    $clientRows[0].sha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $stagedClientJar).Hash.ToLowerInvariant()
    $clientRows[0].sha256 = $stagedSha256
    Write-Utf8NoBomJson -LiteralPath $manifestPath -Content ($manifest | ConvertTo-Json -Depth 12)

    $checksumRelativePath = "thirdparty/client-mods/CopiMineClient-0.1.1.jar"
    $checksumPattern = '^SHA256\s+' + [regex]::Escape($checksumRelativePath) + '\s+[0-9a-fA-F]{64}$'
    $checksumLines = @(Get-Content -LiteralPath $checksumsPath -Encoding ascii)
    $checksumMatches = @($checksumLines | Where-Object { $_ -match $checksumPattern })
    if ($checksumMatches.Count -ne 1) {
        throw "Expected exactly one SHA-256 pin for $checksumRelativePath; found $($checksumMatches.Count)."
    }
    $updatedChecksumLine = "SHA256  $checksumRelativePath  $stagedSha256"
    $updatedChecksumLines = $checksumLines | ForEach-Object {
        if ($_ -match $checksumPattern) { $updatedChecksumLine } else { $_ }
    }
    Set-Content -LiteralPath $checksumsPath -Value $updatedChecksumLines -Encoding ascii
    Write-Host "Synchronized staged CopiMineClient JAR: sha256=$stagedSha256"
}

function Assert-ReleaseChecksum {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    if (-not (Test-Path -LiteralPath $checksumsPath)) {
        throw "Missing SHA-256 checksum manifest: $checksumsPath"
    }
    $normalized = $RelativePath.Replace('\', '/')
    $line = Get-Content -LiteralPath $checksumsPath -Encoding ascii |
        Where-Object { $_ -match '^SHA256\s+' -and ($_ -split '\s+')[1] -eq $normalized } |
        Select-Object -First 1
    if (-not $line) { throw "Missing SHA-256 pin for $normalized" }
    $expected = ($line -split '\s+')[2].ToLowerInvariant()
    if ($expected -notmatch '^[0-9a-f]{64}$') { throw "Malformed SHA-256 pin for $normalized" }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $ProjectRoot $RelativePath)).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { throw "SHA-256 mismatch for $normalized" }
}

if ($SyncBuiltClient) {
    Sync-BuiltClientArtifact
}

if (Test-Path -LiteralPath $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}

New-Item -ItemType Directory -Force -Path (Join-Path $stage "mods") | Out-Null

$files = @(
    "thirdparty\client-mods\CopiMineClient-0.1.1.jar",
    "thirdparty\client-mods\CustomSkinLoader_Fabric-14.26.1.jar",
    "thirdparty\client-mods\emotecraft-for-MC1.21.1-2.4.12-fabric.jar",
    "thirdparty\client-mods\fabric-api-0.116.12+1.21.1.jar",
    "thirdparty\client-mods\voicechat-fabric-1.21.1-2.6.16.jar",
    "thirdparty\client-mods\iris-fabric-1.8.8+mc1.21.1.jar",
    "thirdparty\client-mods\sodium-fabric-0.6.13+mc1.21.1.jar"
)

foreach ($relative in $files) {
    $source = Join-Path $ProjectRoot $relative
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing file for modpack: $relative"
    }
    Assert-ReleaseChecksum -RelativePath $relative
    Copy-Item -LiteralPath $source -Destination (Join-Path $stage "mods") -Force
}

# Keep the payload safe to extract directly into a client directory: the
# archive is deliberately a pure Minecraft mods directory. Release metadata,
# checksums and installation notes remain beside the archive and are served by
# the website; putting them at the ZIP root would pollute the game directory.

if (Test-Path -LiteralPath $zip) {
    Remove-Item -LiteralPath $zip -Force
}
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$zipSha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $zip).Hash.ToLowerInvariant()
$zipSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant()
Set-Content -LiteralPath $sha -Value $zipSha1 -Encoding ascii
Set-Content -LiteralPath $sha256 -Value $zipSha256 -Encoding ascii

# The public archive metadata is an integrity contract too.  Keep it in the
# same transaction as the ZIP build so a fresh archive can never retain an
# obsolete download hash.
$thirdpartyManifestPath = Join-Path $ProjectRoot "thirdparty\thirdparty_manifest.json"
$thirdpartyManifest = Get-Content -LiteralPath $thirdpartyManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($thirdpartyManifest.clientArchive.path -ne "thirdparty/CopiMineMods.zip") {
    throw "Unexpected client archive manifest path: $($thirdpartyManifest.clientArchive.path)"
}
$thirdpartyManifest.clientArchive.sha1 = $zipSha1
$thirdpartyManifest.clientArchive.sha256 = $zipSha256
Write-Utf8NoBomJson -LiteralPath $thirdpartyManifestPath -Content ($thirdpartyManifest | ConvertTo-Json -Depth 12)

if (-not (Test-Path -LiteralPath $frontendPublicDataDir)) {
    New-Item -ItemType Directory -Force -Path $frontendPublicDataDir | Out-Null
}

$manifestPath = Join-Path $ProjectRoot "thirdparty\modpack_manifest.json"
$manifestData = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$zipItem = Get-Item -LiteralPath $zip
$modifiedUnix = [DateTimeOffset]::new($zipItem.LastWriteTimeUtc).ToUnixTimeSeconds()
$snapshot = [ordered]@{
    available = $true
    filename = $zipItem.Name
    downloadUrl = "/downloads/CopiMineMods.zip"
    size = [int64]$zipItem.Length
    sha1 = $zipSha1
    sha256 = $zipSha256
    modified = $modifiedUnix
    manifest = $manifestData
}
Write-Utf8NoBomJson -LiteralPath $frontendSnapshot -Content ($snapshot | ConvertTo-Json -Depth 12)

Write-Host "Built modpack:"
Write-Host "  zip: $zip"
Write-Host "  sha1: $zipSha1"
Write-Host "  sha256: $zipSha256"
