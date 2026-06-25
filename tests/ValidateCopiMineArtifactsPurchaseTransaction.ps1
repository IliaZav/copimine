$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('persistPaidPurchase','setAutoCommit(false)','commit()','rollback()','createPendingDelivery','markPurchaseDelivered','bridge.refund','artifact-purchase-','PAID','PENDING_DELIVERY')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing purchase transaction marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts purchase transaction validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts purchase transaction validation passed.'
