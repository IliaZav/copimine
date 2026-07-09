$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

foreach ($marker in @('purchaseInFlightId','purchaseInFlightId.isBlank()','artifact-purchase-','idempotency_key','CREATE UNIQUE INDEX IF NOT EXISTS idx_artifact')) {
  if (($artifacts + (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'db\migrations\20260611_002_copimine_artifacts.sql'))) -notmatch [regex]::Escape($marker)) {
    $errors.Add("Artifacts anti-duplication marker missing: $marker")
  }
}
foreach ($marker in @('shouldBlockInventoryClick','InventoryAction.COLLECT_TO_CURSOR','HOTBAR_SWAP','SWAP_OFFHAND','cooldownUntil')) {
  if ($narcotics -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics anti-duplication marker missing: $marker") }
}

if ($errors.Count -gt 0) { throw ("Double click/duplication validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Double click/duplication validation passed.'
