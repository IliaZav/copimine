$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('open:shops', 'openAdminShopHub', 'createAdminShopFromTarget', 'nextGeneratedShopId', 'openAdminShopListAsync', 'COUNT(DISTINCT player_uuid)', 'SUM(price_ar)', 'shop:delete:confirm', 'removeShopWithCleanup')) {
  if ($artifacts -notmatch [regex]::Escape($marker) -and $admin -notmatch [regex]::Escape($marker)) { $errors.Add("Missing shop marker: $marker") }
}
if ($artifacts -notmatch 'hasArtifactPermission\(.*copimine\.artifacts\.shop\.remove') { $errors.Add('Server-side shop removal permission gate is missing.') }
if ($errors.Count) { throw ("Admin shops validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineAdminShopsFlow passed.'
