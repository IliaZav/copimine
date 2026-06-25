$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('artifact_item_id','artifact_unique_item_id','artifact_category','artifact_rarity','artifact_owner_uuid','artifact_purchase_id','authenticCatalogItem','artifact_suspicious_events','suspiciousSeen')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing PDC/security marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts PDC/security validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts PDC/security validation passed.'
