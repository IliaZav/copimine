$ErrorActionPreference = 'Stop'

$modelPath = Join-Path $PSScriptRoot '..\resourcepacks\src\assets\copimine\models\item\artifacts\ne_segodnya_suka_shield.json'
$builderPath = Join-Path $PSScriptRoot '..\resourcepacks\build-resourcepack.py'
$model = Get-Content -LiteralPath $modelPath -Raw -Encoding UTF8 | ConvertFrom-Json
$builder = Get-Content -LiteralPath $builderPath -Raw -Encoding UTF8

if ($model.parent -ne 'minecraft:item/generated') {
    throw 'The custom shield icon model must use a generated model.'
}

if ($builder -notmatch 'material\s*==\s*["'']shield["'']' -or $builder -notmatch 'builtin/entity' -or $builder -notmatch 'shield_blocking') {
    throw 'The resource-pack builder must preserve vanilla shield entity and blocking rendering.'
}

Write-Host 'Shield resource-pack model contract OK'
