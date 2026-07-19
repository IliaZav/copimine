$ErrorActionPreference = 'Stop'

$modelPath = Join-Path $PSScriptRoot '..\resourcepacks\src\assets\copimine\models\item\artifacts\ne_segodnya_suka_shield.json'
$builderPath = Join-Path $PSScriptRoot '..\resourcepacks\build-resourcepack.py'
$model = Get-Content -LiteralPath $modelPath -Raw -Encoding UTF8 | ConvertFrom-Json
$builder = Get-Content -LiteralPath $builderPath -Raw -Encoding UTF8

if ($model.parent -ne 'minecraft:item/generated') {
    throw 'The custom shield model must use a normal generated item icon so its texture is not shown as an unfolded UV layout.'
}

if ($builder -notmatch 'parent\s*=\s*["'']minecraft:item/generated["'']' -or $builder -match 'minecraft:builtin/entity') {
    throw 'The resource-pack builder must keep custom shield overrides on a generated item model.'
}

Write-Host 'Shield resource-pack model contract OK'
