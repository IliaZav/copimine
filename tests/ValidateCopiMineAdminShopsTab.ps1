$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('"shops",', 'async function loadShops()', 'shops: loadShops', '/api/admin/shop/ar-items', '/api/admin/shop/donation-items')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { $errors.Add("Missing admin shops tab marker: $marker") }
}
if ($errors.Count) { throw ("Admin shops tab validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineAdminShopsTab passed.'
