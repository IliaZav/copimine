$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('InventoryAction.MOVE_TO_OTHER_INVENTORY','InventoryAction.COLLECT_TO_CURSOR','InventoryAction.HOTBAR_SWAP','InventoryAction.HOTBAR_MOVE_AND_READD','ClickType.NUMBER_KEY','click.isShiftClick()','event.getHotbarButton()')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Shift/hotbar bypass guard missing: $marker" }
}
Write-Host 'Shift-click and hotbar bypass validation passed.'
