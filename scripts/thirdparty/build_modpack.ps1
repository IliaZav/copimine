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

if (Test-Path -LiteralPath $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}

New-Item -ItemType Directory -Force -Path (Join-Path $stage "mods") | Out-Null

$files = @(
    "thirdparty\client-mods\CopiMineClient-0.1.0.jar",
    "thirdparty\client-mods\emotecraft-for-MC1.21.1-2.4.12-fabric.jar",
    "thirdparty\client-mods\fabric-api-0.116.12+1.21.1.jar"
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
Set-Content -LiteralPath $sha -Value $zipSha1 -Encoding UTF8

Write-Host "Built modpack:"
Write-Host "  zip: $zip"
Write-Host "  sha1: $zipSha1"
