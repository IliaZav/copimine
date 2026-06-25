$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$forbidden = Join-Path $root 'resourcepacks\src\assets\minecraft\textures\block'
if (Test-Path -LiteralPath $forbidden) {
  throw 'Global vanilla block override folder must not exist in resourcepacks/src/assets/minecraft/textures/block'
}
Write-Host 'No global vanilla override validation passed.'
