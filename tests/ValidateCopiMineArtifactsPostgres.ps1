$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$sql = Get-Content -Raw (Join-Path $root 'db\migrations\20260611_002_copimine_artifacts.sql')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($table in @('artifact_items_catalog','artifact_item_instances','artifact_shops','artifact_purchases','artifact_repairs','artifact_suspicious_events','artifact_audit_log','artifact_pending_deliveries')) {
  if ($sql -notmatch [regex]::Escape($table)) { $errors.Add("Migration missing table $table.") }
  if ($main -notmatch [regex]::Escape($table)) { $errors.Add("Runtime missing table marker $table.") }
}
if ($main -match 'sqlite') { $errors.Add('SQLite marker found in CopiMineArtifacts runtime.') }
if ($main -notmatch 'PgPool') { $errors.Add('Connection pool marker missing.') }
if ($main -notmatch 'setAutoCommit\(false\)') { $errors.Add('Transaction marker missing.') }
if ($errors.Count -gt 0) { throw ("Artifacts postgres validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts postgres validation passed.'
