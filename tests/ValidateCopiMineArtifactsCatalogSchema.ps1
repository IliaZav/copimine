$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$javaPath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$mainPyPath = Join-Path $root 'admin-web\backend\main.py'
$migrationPath = Join-Path $root 'db\migrations\20260623_009_copimine_artifacts_phase2.sql'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in @($javaPath,$mainPyPath,$migrationPath)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing schema file: $path") }
}

if (Test-Path $javaPath) {
  $java = Get-Content -Raw -Encoding UTF8 $javaPath
  foreach ($marker in @(
    'custom_model_data INTEGER NOT NULL DEFAULT 0',
    'effect_chance_percent INTEGER NOT NULL DEFAULT 100',
    'visual_effect_id TEXT NOT NULL DEFAULT ''''',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id'
  )) {
    if ($java -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts Java schema marker missing: $marker") }
  }
}

if (Test-Path $mainPyPath) {
  $mainPy = Get-Content -Raw -Encoding UTF8 $mainPyPath
  foreach ($marker in @(
    'custom_model_data INTEGER NOT NULL DEFAULT 0',
    'effect_chance_percent INTEGER NOT NULL DEFAULT 100',
    'visual_effect_id TEXT NOT NULL DEFAULT ''''',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data INTEGER NOT NULL DEFAULT 0',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent INTEGER NOT NULL DEFAULT 100',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id TEXT NOT NULL DEFAULT '''''
  )) {
    if ($mainPy -notmatch [regex]::Escape($marker)) { $errors.Add("Admin web schema marker missing: $marker") }
  }
}

if (Test-Path $migrationPath) {
  $migration = Get-Content -Raw -Encoding UTF8 $migrationPath
  foreach ($marker in @(
    'CREATE TABLE IF NOT EXISTS artifact_items_catalog',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent',
    'ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id',
    'CREATE INDEX IF NOT EXISTS idx_artifact_items_catalog_category',
    'CREATE INDEX IF NOT EXISTS idx_artifact_items_catalog_enabled'
  )) {
    if ($migration -notmatch [regex]::Escape($marker)) { $errors.Add("Migration marker missing: $marker") }
  }
}

if ($errors.Count -gt 0) {
  throw ("Artifacts catalog schema validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Artifacts catalog schema validation passed: Java, web backend, and SQL migration are aligned.'
