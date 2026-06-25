$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$migration = Join-Path $root 'db\migrations\20260612_004_gui_bank_shop_full_fix.sql'
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($admin, $migration)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing file: $path") }
}
if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  if ($java -match 'INSERT\s+INTO\s+atm_audit\s*\(\s*time\s*,') { $errors.Add('Code still inserts into atm_audit.time.') }
  if ($java -notmatch 'atm_audit\(id BIGSERIAL PRIMARY KEY,created_at BIGINT') { $errors.Add('Code must create atm_audit.created_at BIGINT.') }
  if ($java -notmatch 'UPDATE atm_audit SET created_at=time') { $errors.Add('Code must copy old atm_audit.time values into created_at.') }
}
if (Test-Path $migration) {
  $sql = Get-Content -Raw -Encoding UTF8 $migration
  foreach ($marker in @('CREATE TABLE IF NOT EXISTS atm_audit','created_at BIGINT','UPDATE atm_audit SET created_at = time','idx_atm_audit_created_at')) {
    if (-not $sql.Contains($marker)) { $errors.Add("Migration missing marker: $marker") }
  }
}
if ($errors.Count -gt 0) { throw ("ATM audit schema validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ATM audit schema validation passed.'
