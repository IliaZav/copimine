$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

foreach ($marker in @('InventoryDragEvent','onQuit(PlayerQuitEvent','sessions.remove','purchaseInFlightId','state.purchaseInFlightId = ""','firstEmpty() < 0')) {
  if ($artifacts -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts GUI session marker missing: $marker") }
}
foreach ($marker in @('InventoryDragEvent','onQuit(PlayerQuitEvent','pendingPurchases.remove','pinBuffers.remove','cooldownUntil.remove','recentUses.remove','firstEmpty() < 0')) {
  if ($narcotics -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics GUI session marker missing: $marker") }
}

if ($errors.Count -gt 0) { throw ("GUI sessions validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'GUI sessions validation passed: drag, quit cleanup and inventory race checks are present.'
