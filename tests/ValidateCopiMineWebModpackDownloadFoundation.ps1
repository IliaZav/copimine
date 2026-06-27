$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $projectRoot "admin-web\backend\main.py"
$indexHtml = Join-Path $projectRoot "admin-web\frontend\index.html"
$renderer = Join-Path $projectRoot "admin-web\frontend\assets\js\public\site-render.js"
$zipPath = Join-Path $projectRoot "thirdparty\CopiMineMods.zip"
$manifestPath = Join-Path $projectRoot "thirdparty\modpack_manifest.json"

foreach ($path in @($backend, $indexHtml, $renderer, $zipPath, $manifestPath)) {
    if (-not (Test-Path $path)) {
        throw "Required modpack foundation path is missing: $path"
    }
}

$backendText = Get-Content $backend -Raw -Encoding UTF8
$indexText = Get-Content $indexHtml -Raw -Encoding UTF8
$rendererText = Get-Content $renderer -Raw -Encoding UTF8
$manifestText = Get-Content $manifestPath -Raw -Encoding UTF8

if ($backendText -notmatch '/downloads/CopiMineMods\.zip') {
    throw "Backend no longer exposes /downloads/CopiMineMods.zip"
}

if ($backendText -notmatch '@app\.get\("/api/public/modpack"\)') {
    throw "Backend missing /api/public/modpack endpoint"
}

if ($indexText -notmatch '/downloads/CopiMineMods\.zip') {
    throw "Public index no longer links to CopiMineMods.zip"
}

if ($rendererText -notmatch 'CopiMineMods\.zip' -or $rendererText -notmatch 'renderModpack') {
    throw "Public renderer no longer integrates real modpack data"
}

if ($manifestText -notmatch '"files"\s*:') {
    throw "modpack_manifest.json does not contain files list"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $zipEntries = $archive.Entries | ForEach-Object { ($_.FullName -replace '\\','/').Trim() }
} finally {
    $archive.Dispose()
}

foreach ($required in @(
    "mods/CopiMineClient-0.1.0.jar",
    "mods/emotecraft-for-MC1.21.1-2.4.12-fabric.jar",
    "mods/fabric-api-0.116.12+1.21.1.jar",
    "modpack_manifest.json",
    "README_RU.txt",
    "VOICE_CHAT_OFFICIAL_DOWNLOAD.txt"
)) {
    if ($zipEntries -notcontains $required) {
        throw "CopiMineMods.zip is missing required entry: $required"
    }
}

if ($manifestText -notmatch '"requiredExternal"\s*:') {
    throw "modpack_manifest.json must describe official external downloads"
}

if ($manifestText -notmatch 'Simple Voice Chat') {
    throw "modpack_manifest.json must document Simple Voice Chat"
}

Write-Output "ValidateCopiMineWebModpackDownloadFoundation passed."
