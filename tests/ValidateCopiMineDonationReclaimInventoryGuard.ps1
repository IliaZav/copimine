$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = New-Object System.Collections.Generic.List[string]

if ($artifacts -notmatch '(?s)private boolean customShopItemsAreVanilla\(\).*?return true;') {
  $errors.Add('Custom shop items must use the ordinary-item lifecycle boundary.')
}
$reclaim = [regex]::Match($artifacts, '(?s)private void reclaimDonationItemSafe\(Player var1, String var2\).*')
if (-not $reclaim.Success -or $reclaim.Value -notmatch '(?s)customShopItemsAreVanilla\(\).*?return;') {
  $errors.Add('The reclaim action must be disabled after reclaim retirement.')
}
$open = [regex]::Match($artifacts, '(?s)private void openDonationReclaim\(Player var1\).*')
if (-not $open.Success -or $open.Value -notmatch '(?s)customShopItemsAreVanilla\(\).*?return;') {
  $errors.Add('The reclaim screen must be disabled after reclaim retirement.')
}

if ($errors.Count -gt 0) {
  throw ("Donation reclaim inventory validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Donation reclaim inventory validation passed.'
