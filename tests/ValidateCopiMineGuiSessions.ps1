$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$economy = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java')
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

foreach ($marker in @('InventoryDragEvent','onQuit(PlayerQuitEvent','sessions.remove','purchaseInFlightId','purchaseInFlightId = ""','firstEmpty() < 0')) {
  if ($artifacts -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts GUI session marker missing: $marker") }
}
foreach ($marker in @('atmPinSessions.remove','onQuit(PlayerQuitEvent','InventoryClickEvent')) {
  if ($economy -notmatch [regex]::Escape($marker)) { $errors.Add("EconomyCore bank GUI session marker missing: $marker") }
}
foreach ($marker in @('InventoryDragEvent','onQuit(PlayerQuitEvent','visualRuntime.clear','overdoseService.clearActiveEffects')) {
  if ($narcotics -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics GUI session marker missing: $marker") }
}
foreach ($legacy in @('pendingPurchases.remove','pinBuffers.remove')) {
  if ($narcotics -match [regex]::Escape($legacy)) { $errors.Add("Narcotics still owns legacy bank GUI state: $legacy") }
}

if ($errors.Count -gt 0) { throw ("GUI sessions validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'GUI sessions validation passed: drag, quit cleanup and inventory race checks are present.'
