$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()

$markers = @(
  'createPendingDeliveryRecovery(',
  'purchase_recovered_to_pending',
  "UPDATE artifact_purchases SET status='PENDING_DELIVERY',delivery_mode='PENDING'",
  "UPDATE artifact_item_instances SET status='PENDING_DELIVERY'"
)

foreach ($marker in $markers) {
  if ($text -notmatch [regex]::Escape($marker)) {
    $errors.Add("Missing stranded delivery recovery marker: $marker")
  }
}

$pattern = '(?s)reconcileStrandedDeliveries\(Player var1, List<CopiMineArtifacts\.PendingDeliveryRow> var2, List<CopiMineArtifacts\.DeliveringInstanceRow> var3\).*?createPendingDeliveryRecovery\(var4, var10\.purchaseId\(\), var10\.uniqueItemId\(\), var10\.itemId\(\)\)'
if ($text -notmatch $pattern) {
  $errors.Add('Stranded artifact purchases without a physical item must be converted into safe pending delivery during reconcile.')
}

if ($errors.Count -gt 0) {
  throw ("Artifacts stranded delivery recovery validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Artifacts stranded delivery recovery validation passed.'
