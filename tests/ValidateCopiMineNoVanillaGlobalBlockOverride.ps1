$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$packRoot = Join-Path $root 'resourcepacks\src\assets\minecraft\textures\block'
$forbidden = @('lectern.png', 'emerald_block.png', 'gold_block.png', 'paper.png')
$hits = @()
foreach ($name in $forbidden) {
  $path = Join-Path $packRoot $name
  if (Test-Path -LiteralPath $path) {
    $hits += $path
  }
}
if ($hits.Count -gt 0) {
  throw ("ValidateCopiMineNoVanillaGlobalBlockOverride failed:`n - Unexpected global overrides: " + ($hits -join ', '))
}
Write-Host 'ValidateCopiMineNoVanillaGlobalBlockOverride passed.'
