$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('artifact_pending_deliveries','createPendingDelivery','claimPending(','claimOne(','markPendingClaimed','pendingCount(','PENDING_DELIVERY')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing pending-delivery marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts pending delivery validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts pending delivery validation passed.'
