param(
    [string]$ProjectRoot = ""
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

function Write-Utf8NoBomJson {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($LiteralPath, $Content + [Environment]::NewLine, $utf8NoBom)
}

if (Test-Path -LiteralPath $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}

New-Item -ItemType Directory -Force -Path (Join-Path $stage "mods") | Out-Null

$files = @(
    "thirdparty\client-mods\CopiMineClient-0.1.0.jar",
    "thirdparty\client-mods\emotecraft-for-MC1.21.1-2.4.12-fabric.jar",
    "thirdparty\client-mods\fabric-api-0.116.11+1.21.1.jar",
    "thirdparty\client-mods\voicechat-fabric-1.21.1-2.6.16.jar",
    "thirdparty\client-mods\iris-fabric-1.8.8+mc1.21.1.jar",
    "thirdparty\client-mods\sodium-fabric-0.6.13+mc1.21.1.jar"
)

foreach ($relative in $files) {
    $source = Join-Path $ProjectRoot $relative
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing file for modpack: $relative"
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $stage "mods") -Force
}

foreach ($relative in @(
    "thirdparty\README_RU.txt",
    "thirdparty\VOICE_CHAT_OFFICIAL_DOWNLOAD.txt",
    "thirdparty\checksums.txt",
    "thirdparty\modpack_manifest.json"
)) {
    $source = Join-Path $ProjectRoot $relative
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing file for modpack: $relative"
    }
    Copy-Item -LiteralPath $source -Destination $stage -Force
}

if (Test-Path -LiteralPath $zip) {
    Remove-Item -LiteralPath $zip -Force
}
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$zipSha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $zip).Hash.ToLowerInvariant()
$zipSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant()
Set-Content -LiteralPath $sha -Value $zipSha1 -Encoding ascii
Set-Content -LiteralPath $sha256 -Value $zipSha256 -Encoding ascii

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
