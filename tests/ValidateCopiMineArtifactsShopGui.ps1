$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('ViewType.MAIN','ViewType.CATEGORY','ViewType.DETAIL','ViewType.CONFIRM','ViewType.PIN','soonIcon','InventoryDragEvent','isShiftClick','getHotbarButton')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing GUI marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts shop GUI validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts shop GUI validation passed.'
