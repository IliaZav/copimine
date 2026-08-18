$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $projectRoot "admin-web\backend\main.py"
$indexHtml = Join-Path $projectRoot "admin-web\frontend\index.html"
$modsHtml = Join-Path $projectRoot "admin-web\frontend\mods.html"
$renderer = Join-Path $projectRoot "admin-web\frontend\assets\js\public\site-render.js"
$zipPath = Join-Path $projectRoot "thirdparty\CopiMineMods.zip"
$manifestPath = Join-Path $projectRoot "thirdparty\modpack_manifest.json"

foreach ($path in @($backend, $indexHtml, $modsHtml, $renderer, $zipPath, $manifestPath)) {
    if (-not (Test-Path $path)) {
        throw "Required modpack foundation path is missing: $path"
    }
}

$backendText = Get-Content $backend -Raw -Encoding UTF8
$indexText = Get-Content $indexHtml -Raw -Encoding UTF8
$modsText = Get-Content $modsHtml -Raw -Encoding UTF8
$rendererText = Get-Content $renderer -Raw -Encoding UTF8
$manifestText = Get-Content $manifestPath -Raw -Encoding UTF8

if ($backendText -notmatch '/downloads/CopiMineMods\.zip') {
    throw "Backend no longer exposes /downloads/CopiMineMods.zip"
}

if ($backendText -notmatch '@app\.get\("/api/public/modpack"\)') {
    throw "Backend missing /api/public/modpack endpoint"
}

if ($modsText -notmatch '/launcher\.html' -or $modsText -notmatch 'meta http-equiv="refresh"') {
    throw "Legacy mods page must redirect to the canonical Launcher page"
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
    "mods/fabric-api-0.116.11+1.21.1.jar",
    "mods/modmenu-11.0.4.jar",
    "mods/voicechat-fabric-1.21.1-2.6.16.jar",
    "mods/iris-fabric-1.8.8+mc1.21.1.jar",
    "mods/sodium-fabric-0.6.13+mc1.21.1.jar",
    "mods/CustomSkinLoader_Fabric-14.26.1.jar"
)) {
    if ($zipEntries -notcontains $required) {
        throw "CopiMineMods.zip is missing required entry: $required"
    }
}

if ($manifestText -notmatch '"requiredExternal"\s*:\s*\[\s*\]') {
    throw "modpack_manifest.json must state that no external client mods are required"
}

if ($manifestText -notmatch 'Simple Voice Chat') {
    throw "modpack_manifest.json must document Simple Voice Chat"
}

if ($manifestText -notmatch 'Mod Menu' -or $manifestText -notmatch '11\.0\.4' -or $manifestText -notmatch 'https://modrinth.com/mod/modmenu') {
    throw "modpack_manifest.json must document the official Mod Menu 11.0.4 client mod"
}

if ($manifestText -notmatch 'CustomSkinLoader' -or $manifestText -notmatch '14\.26\.1') {
    throw "modpack_manifest.json must document CustomSkinLoader Fabric 14.26.1"
}

$manifestData = $manifestText | ConvertFrom-Json
$requiredComponents = @(
    "CopiMineClient",
    "CustomSkinLoader",
    "Emotecraft",
    "Fabric API",
    "Simple Voice Chat",
    "Iris",
    "Sodium"
)
foreach ($component in $requiredComponents) {
    $entry = @($manifestData.files | Where-Object { $_.component -eq $component })
    if ($entry.Count -ne 1 -or $entry[0].required -ne $true) {
        throw "modpack_manifest.json must mark $component as required"
    }
}
$modMenuEntry = @($manifestData.files | Where-Object { $_.component -eq "Mod Menu" })
if ($modMenuEntry.Count -ne 1 -or $modMenuEntry[0].required -ne $false) {
    throw "modpack_manifest.json must keep Mod Menu optional"
}
$manifestPaths = @($manifestData.files | ForEach-Object { $_.path })
if ($manifestPaths | Where-Object { $_ -match '(?i)tl.?skin' }) {
    throw "modpack_manifest.json must not list the replaced TL Skin mod as an included file"
}
if ($zipEntries | Where-Object { $_ -match '(?i)tl.?skin' }) {
    throw "CopiMineMods.zip must not contain the replaced TL Skin mod"
}

if ($manifestText -notmatch 'Iris' -or $manifestText -notmatch 'Sodium') {
    throw "modpack_manifest.json must document Iris and Sodium in the bundled client set"
}

Write-Output "ValidateCopiMineWebModpackDownloadFoundation passed."
