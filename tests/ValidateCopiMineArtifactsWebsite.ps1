$ErrorActionPreference = 'Stop'
$backend = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\admin-web\backend\main.py'))
$frontend = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\admin-web\frontend\assets\app.js'))
$style = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\admin-web\frontend\assets\style.css'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('artifact_items_catalog','artifact_purchases','artifact_pending_deliveries','artifact_suspicious_events','artifact_repairs')) {
  if ($backend -notmatch [regex]::Escape($marker)) { $errors.Add("Backend missing artifact marker: $marker") }
}
foreach ($marker in @('/api/artifacts/health','/api/player/artifacts','public_artifact_row','artifact_health_sync','CopiMineArtifacts.jar','CopiMineUltimateAdminPlus.jar')) {
  if ($backend -notmatch [regex]::Escape($marker)) { $errors.Add("Backend missing artifact API marker: $marker") }
}
foreach ($marker in @('loadArtifacts','loadPlayerArtifacts','artifactStatusTone','/api/artifacts/health','/api/player/artifacts','artifact-pending','Bridge','PostgreSQL','artifacts: loadArtifacts','artifacts: loadPlayerArtifacts')) {
  if ($frontend -notmatch [regex]::Escape($marker)) { $errors.Add("Frontend missing artifact marker: $marker") }
}
foreach ($marker in @('public-hero','showcase-card','feature-tab','feature-panel','image-rendering: pixelated','--sidebar','--surface-strong')) {
  if ($style -notmatch [regex]::Escape($marker)) { $errors.Add("Minecraft UI style marker missing: $marker") }
}
if ($style -match 'radial-gradient|backdrop-filter') { $errors.Add('Minecraft UI must not use radial blobs or glass blur.') }
foreach ($secret in @('player_uuid','unique_item_id','bank_tx_id','idempotency_key')) {
  if ($backend -notmatch "hidden[\s\S]*`"$secret`"") { $errors.Add("Player artifact API must hide $secret.") }
}
if ($errors.Count -gt 0) { throw ("Artifacts website validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts website validation passed.'
