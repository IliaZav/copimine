$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$modelPath = Join-Path $PSScriptRoot '..\resourcepacks\src\assets\copimine\models\item\artifacts\ne_segodnya_suka_shield.json'
$builderPath = Join-Path $PSScriptRoot '..\resourcepacks\build-resourcepack.py'
$model = Get-Content -LiteralPath $modelPath -Raw -Encoding UTF8 | ConvertFrom-Json
$builder = Get-Content -LiteralPath $builderPath -Raw -Encoding UTF8

if ($model.parent -ne 'minecraft:item/generated') {
    throw 'The custom shield icon model must use a generated model.'
}

if ($model.textures.layer0 -ne 'copimine:item/artifacts/ne_segodnya_suka_shield_icon') {
    throw 'The custom shield must use the compact icon texture, not the unfolded UV sheet.'
}

if ($builder -notmatch 'write_shield_icon' -or $builder -notmatch 'material\s*==\s*["'']shield["'']' -or $builder -notmatch 'builtin/entity' -or $builder -notmatch 'shield_blocking') {
    throw 'The resource-pack builder must preserve vanilla shield entity and blocking rendering.'
}

$icon = Join-Path $root 'resourcepacks\build\_stage\assets\copimine\textures\item\artifacts\ne_segodnya_suka_shield_icon.png'
if (-not (Test-Path $icon)) { throw 'The generated shield icon is missing from the build stage.' }

Write-Host 'Shield resource-pack model contract OK'
