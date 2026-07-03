$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tabConfigPath = Join-Path $root 'minecraft\server\plugins\TAB\config.yml'
$fontConfigPath = Join-Path $root 'resourcepacks\src\assets\minecraft\font\default.json'
$bannerTexturePath = Join-Path $root 'resourcepacks\src\assets\copimine\textures\gui\tab\minecraft_title.png'
$zipPath = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'

if (-not (Test-Path $tabConfigPath)) { throw 'TAB config is missing.' }
if (-not (Test-Path $fontConfigPath)) { throw 'Minecraft font config is missing.' }
if (-not (Test-Path $bannerTexturePath)) { throw 'TAB banner texture is missing from resource-pack sources.' }
if (-not (Test-Path $zipPath)) { throw 'Built resource-pack zip is missing.' }

$tabConfig = Get-Content -Raw -Encoding UTF8 $tabConfigPath
$fontConfig = Get-Content -Raw -Encoding UTF8 $fontConfigPath

if ($tabConfig -notmatch '\\uE300|') { throw 'TAB config does not reference the CopiMine banner glyph.' }
if ($fontConfig -notmatch 'copimine:gui/tab/minecraft_title.png') { throw 'Resource-pack font does not point TAB glyph to minecraft_title.png.' }
if ($fontConfig -notmatch '\\uE300') { throw 'Resource-pack font does not map glyph U+E300.' }

$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $zipEntries = $zip.Entries.FullName
    if ($zipEntries -notcontains 'assets/copimine/textures/gui/tab/minecraft_title.png') {
        throw 'Built resource-pack zip does not contain the TAB banner texture.'
    }
    if ($zipEntries -notcontains 'assets/minecraft/font/default.json') {
        throw 'Built resource-pack zip does not contain the Minecraft font mapping.'
    }
}
finally {
    $zip.Dispose()
}

Write-Host 'ValidateCopiMineTabBannerConfigured passed.'
