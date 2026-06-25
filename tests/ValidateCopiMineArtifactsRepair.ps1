$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('openRepair(','executeRepair(','artifact_repairs','repairPrice(','repair:confirm:','artifact_repair_refund')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing repair marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts repair validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts repair validation passed.'
