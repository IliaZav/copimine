$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$builderPath = Join-Path $PSScriptRoot '..\resourcepacks\build-resourcepack.py'
$builder = Get-Content -LiteralPath $builderPath -Raw -Encoding UTF8
$items = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\copimine-artifacts\items.yml') -Raw -Encoding UTF8
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\resourcepacks\models_manifest.json') -Raw -Encoding UTF8
$sources = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\resourcepacks\item_texture_sources.json') -Raw -Encoding UTF8

if ($items -notmatch '(?ms)^    - item-id: ne_segodnya_suka_shield\r?\n.*?custom-texture-mode-allowed:\s*false.*?custom-model-data:\s*0') {
    throw 'The shield catalog entry must explicitly use vanilla rendering.'
}
if ($manifest -match 'ne_segodnya_suka_shield' -or $sources -match 'ne_segodnya_suka_shield') {
    throw 'The vanilla shield must not be present in custom resource-pack mappings.'
}
if ($builder -match 'write_shield_icon|shield_blocking|custom_overrides\s*=\s*custom_overrides\s*\+\s*overrides') {
    throw 'The resource-pack builder must not create a custom shield override.'
}

$stageShield = Join-Path $root 'resourcepacks\build\_stage\assets\minecraft\models\item\shield.json'
if (Test-Path $stageShield) { throw 'The build stage must inherit the vanilla shield model instead of overriding it.' }

Write-Host 'Vanilla shield resource-pack contract OK'
