$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$app = Join-Path $root 'admin-web\frontend\assets\app.js'
$backend = Join-Path $root 'admin-web\backend\main.py'
$guide = Join-Path $root 'FULL_MANUAL_TEST_MATRIX.md'
$errors = New-Object System.Collections.Generic.List[string]
foreach ($path in @($app, $backend, $guide)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing file: $path") }
}
if (Test-Path $app) {
  . "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
  $js = Read-FrontendBundle
  foreach ($marker in @('loadArtifacts','/api/artifacts/health','/api/artifacts/catalog','/api/artifacts/shops','/api/artifacts/purchases','/api/artifacts/pending','/api/artifacts/repairs','/api/artifacts/suspicious','artifact-catalog','artifact-shops','artifact-purchases','artifact-pending','artifact-repairs','artifact-suspicious')) {
    if (-not $js.Contains($marker)) { $errors.Add("Artifacts admin UI missing marker: $marker") }
  }
}
if (Test-Path $backend) {
  $py = Get-Content -Raw -Encoding UTF8 $backend
  foreach ($marker in @('artifacts_catalog','artifacts_shops','artifacts_purchases','artifacts_repairs','artifacts_suspicious','artifacts_pending','artifacts_health','artifact_health_sync')) {
    if (-not $py.Contains($marker)) { $errors.Add("Artifacts backend missing marker: $marker") }
  }
}
if (Test-Path $guide) {
  $text = Get-Content -Raw -Encoding UTF8 $guide
  foreach ($marker in @('/cmartifacts','artifact_shops','artifact_items_catalog','artifact_purchases','artifact_pending_deliveries','artifact_repairs')) {
    if (-not $text.Contains($marker)) { $errors.Add("Manual matrix missing artifacts marker: $marker") }
  }
}
if ($errors.Count -gt 0) { throw ("Artifacts admin/settings validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts admin/settings validation passed.'
