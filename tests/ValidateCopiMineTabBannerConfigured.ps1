$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tabConfigPath = Join-Path $root 'minecraft\server\plugins\TAB\config.yml'
$fontConfigPath = Join-Path $root 'resourcepacks\src\assets\minecraft\font\default.json'
$copimineBannerTexturePath = Join-Path $root 'resourcepacks\src\assets\copimine\textures\gui\tab\copimine_logo.png'
$minecraftBannerTexturePath = Join-Path $root 'resourcepacks\src\assets\minecraft\textures\gui\copimine_tab.png'
$zipPath = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'

if (-not (Test-Path $tabConfigPath)) { throw 'TAB config is missing.' }
if (-not (Test-Path $fontConfigPath)) { throw 'Minecraft font config is missing.' }
if (-not (Test-Path $copimineBannerTexturePath)) { throw 'Legacy TAB banner texture is missing from resource-pack sources.' }
if (-not (Test-Path $minecraftBannerTexturePath)) { throw 'Minecraft TAB banner texture is missing from resource-pack sources.' }
if (-not (Test-Path $zipPath)) { throw 'Built resource-pack zip is missing.' }

$tabConfig = Get-Content -Raw -Encoding UTF8 $tabConfigPath
$fontConfig = Get-Content -Raw -Encoding UTF8 $fontConfigPath

$legacyGlyph = [string][char]0xE300
$minecraftGlyph = [string][char]0xE000
$usesLegacyGlyph = $tabConfig -match '\\uE300' -or $tabConfig.Contains($legacyGlyph)
$usesMinecraftGlyph = $tabConfig -match '\\uE000' -or $tabConfig.Contains($minecraftGlyph)

if (-not $usesLegacyGlyph -and -not $usesMinecraftGlyph) {
    throw 'TAB config does not reference a supported CopiMine banner glyph.'
}
if ($usesLegacyGlyph -and ($fontConfig -notmatch 'copimine:gui/tab/copimine_logo.png' -or $fontConfig -notmatch '\\uE300')) {
    throw 'Resource-pack font does not map the legacy TAB glyph U+E300 to copimine_logo.png.'
}
if ($usesMinecraftGlyph -and ($fontConfig -notmatch 'minecraft:gui/copimine_tab.png' -or $fontConfig -notmatch '\\uE000')) {
    throw 'Resource-pack font does not map the TAB glyph U+E000 to minecraft:gui/copimine_tab.png.'
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $zipEntries = $zip.Entries.FullName
    if ($zipEntries -notcontains 'assets/copimine/textures/gui/tab/copimine_logo.png') {
        throw 'Built resource-pack zip does not contain the TAB banner texture.'
    }
    if ($zipEntries -notcontains 'assets/minecraft/font/default.json') {
        throw 'Built resource-pack zip does not contain the Minecraft font mapping.'
    }
    if ($zipEntries -notcontains 'assets/minecraft/textures/gui/copimine_tab.png') {
        throw 'Built resource-pack zip does not contain the minecraft TAB banner texture.'
    }
}
finally {
    $zip.Dispose()
}

Write-Host 'ValidateCopiMineTabBannerConfigured passed.'
