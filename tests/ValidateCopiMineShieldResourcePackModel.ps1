$ErrorActionPreference = 'Stop'

$modelPath = Join-Path $PSScriptRoot '..\resourcepacks\src\assets\copimine\models\item\artifacts\ne_segodnya_suka_shield.json'
$builderPath = Join-Path $PSScriptRoot '..\resourcepacks\build-resourcepack.py'
$model = Get-Content -LiteralPath $modelPath -Raw -Encoding UTF8 | ConvertFrom-Json
$builder = Get-Content -LiteralPath $builderPath -Raw -Encoding UTF8

if ($model.parent -ne 'minecraft:builtin/entity') {
    throw 'The custom shield model must use Minecraft''s shield entity renderer instead of rendering its UV layout as a flat icon.'
}

if ($builder -notmatch 'material\s*==\s*["'']shield["'']' -or $builder -notmatch 'minecraft:builtin/entity') {
    throw 'The resource-pack builder must preserve the shield entity renderer when it generates item overrides.'
}

Write-Host 'Shield resource-pack model contract OK'
