$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tabConfig = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'minecraft\server\plugins\TAB\config.yml')
$fontConfig = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\minecraft\font\default.json')
if ($tabConfig -notmatch '\\uE300|') { throw 'TAB config does not reference the CopiMine banner glyph.' }
if ($fontConfig -notmatch 'copimine:gui/tab/minecraft_title.png') { throw 'Resource-pack font does not point TAB glyph to minecraft_title.png.' }
if ($fontConfig -notmatch '\\uE300') { throw 'Resource-pack font does not map glyph U+E300.' }
Write-Host 'ValidateCopiMineTabBannerConfigured passed.'
