$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = New-Object System.Collections.Generic.List[string]

if ($artifacts -notmatch '(?s)if \(this\.playerHasOfficialInstance\(var1, var6\.oldUniqueItemId\(\), var6\.itemId\(\), var1\.getUniqueId\(\)\)\).*?rollbackDonationReclaim') {
  $errors.Add('Reclaim must re-check that the original official item is absent before adding a replacement.')
}
if ($artifacts -notmatch '(?s)playerHasOfficialInstance\(.*?getArmorContents\(\)') {
  $errors.Add('Reclaim inventory guard must include armor slots.')
}
if ($artifacts -notmatch '(?s)INSERT INTO artifact_item_instances[\s\S]*?var7\.setString\(4, ""\)') {
  $errors.Add('Replacement instances must be marked as reclaim records, not linked to the original purchase delivery.')
}

if ($errors.Count -gt 0) {
  throw ("Donation reclaim inventory validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Donation reclaim inventory validation passed.'
