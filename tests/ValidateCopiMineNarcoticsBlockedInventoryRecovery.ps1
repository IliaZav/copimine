$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @(
  'isRecoveryExtraction',
  'clickedBlocked && clicked == top',
  'case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY -> true',
  'InventoryMoveItemEvent',
  'InventoryPickupItemEvent',
  'PrepareItemCraftEvent',
  'BlockDispenseEvent'
)) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Blocked-inventory recovery marker missing: $marker" }
}
Write-Host 'Blocked inventory recovery validation passed.'
