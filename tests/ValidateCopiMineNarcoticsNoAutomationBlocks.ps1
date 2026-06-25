$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('InventoryMoveItemEvent','BlockDispenseEvent','HOPPER','DROPPER','DISPENSER','CRAFTER')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Automation block guard missing: $marker" }
}
Write-Host 'Automation block guard validation passed.'
