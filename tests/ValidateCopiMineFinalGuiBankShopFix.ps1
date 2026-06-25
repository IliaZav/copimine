$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$artifacts = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$narcotics = Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$migration = Join-Path $root 'db\migrations\20260611_003_bank_atm_audit_gui_fix.sql'
$migrationFull = Join-Path $root 'db\migrations\20260612_004_gui_bank_shop_full_fix.sql'
$messages = Join-Path $root 'copimine-admin-plugin\messages_ru.yml'
$guide = Join-Path $root 'FULL_MANUAL_TEST_MATRIX.md'
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($admin, $artifacts, $narcotics, $migration, $migrationFull, $messages)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing required final-fix file: $path") }
}

if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  if ($java -match 'INSERT\s+INTO\s+atm_audit\s*\(\s*time\s*,') { $errors.Add('AdminPlus must not insert into atm_audit.time; use created_at.') }
  if ($java -notmatch 'CREATE TABLE IF NOT EXISTS atm_audit\(id BIGSERIAL PRIMARY KEY,created_at BIGINT') { $errors.Add('AdminPlus must create atm_audit with created_at BIGINT.') }
  if ($java -notmatch 'UPDATE atm_audit SET created_at=time') { $errors.Add('AdminPlus must migrate old atm_audit.time values into created_at when present.') }
  foreach ($bad in @('ATM error:','GUI: "+ex.getClass','Error: "+e.getClass','warn(actor,"Ошибка сохранения: "+ex.getMessage())','warn(p,"Ошибка GUI:')) {
    if ($java.Contains($bad)) { $errors.Add("AdminPlus contains forbidden visible/error marker: $bad") }
  }
  foreach ($required in @('created_at,actor,action,details','bank-atms','bank-atm','atm-pin','bankpin:digit:','bankpin:confirm','V4_BANK_ATM_GAMEPLAY','root.equals("ar")||root.equals("cmbank")')) {
    if (-not $java.Contains($required)) { $errors.Add("AdminPlus missing bank/ATM marker: $required") }
  }
  foreach ($english in @('Deposit hand','Deposit all','Withdraw 64 AR','Transfer recipient','Invalid bank PIN','No economy permission','ATM PIN')) {
    if ($java.Contains($english)) { $errors.Add("AdminPlus still exposes English bank/ATM text: $english") }
  }
}

if (Test-Path $artifacts) {
  $java = Get-Content -Raw -Encoding UTF8 $artifacts
  if ($java -match 'bank:deposit|bank:withdraw|deposit bank|withdraw bank') { $errors.Add('Artifacts shop must not expose bank deposit/withdraw actions.') }
  if (-not $java.Contains('bridge.charge')) { $errors.Add('Artifacts purchases must charge through the official bank bridge.') }
  if ($java -match 'UPDATE\s+cmv4_bank_accounts|INSERT\s+INTO\s+cmv4_bank_ledger') { $errors.Add('Artifacts must not write bank accounts or ledger directly.') }
}

if (Test-Path $narcotics) {
  $java = Get-Content -Raw -Encoding UTF8 $narcotics
  if ($java -match 'PIN:\s*"\s*\+\s*maskedPin') { $errors.Add('Narcotics PIN GUI title must not contain the entered PIN or its mask.') }
  if (-not $java.Contains('maskedPin(player)')) { $errors.Add('Narcotics PIN GUI must show masked input inside the interface.') }
  if (-not $java.Contains('Bukkit.createInventory(holder, 27')) { $errors.Add('Narcotics PIN GUI inventory marker is missing.') }
}

if (Test-Path $migration) {
  $sql = Get-Content -Raw -Encoding UTF8 $migration
  foreach ($marker in @('atm_audit','created_at BIGINT','UPDATE atm_audit SET created_at = time','artifact_items_catalog','artifact_shops')) {
    if (-not $sql.Contains($marker)) { $errors.Add("Final SQL migration missing marker: $marker") }
  }
}
if (Test-Path $migrationFull) {
  $sql = Get-Content -Raw -Encoding UTF8 $migrationFull
  foreach ($marker in @('20260612_004_gui_bank_shop_full_fix','created_at BIGINT','artifact_items_catalog','artifact_shops','cmv4_schema_migrations')) {
    if (-not $sql.Contains($marker)) { $errors.Add("Full final SQL migration missing marker: $marker") }
  }
}

if (-not (Test-Path -LiteralPath $guide)) {
  $errors.Add('Missing FULL_MANUAL_TEST_MATRIX.md with empty factual-result columns.')
} else {
  $text = Get-Content -Raw -Encoding UTF8 $guide
  foreach ($marker in @('CopiMineUltimateAdminPlus','CopiMineArtifacts','CopiMineNarcotics','/cmultra','/cmbank','/cmartifacts','/cmnarcotics')) {
    if (-not $text.Contains($marker)) { $errors.Add("Manual test matrix missing marker: $marker") }
  }
  if (-not $text.Contains('What should happen') -and -not $text.Contains('---|---|---|---|---')) { $errors.Add('Manual test matrix must include acceptance-test tables.') }
}

if ($errors.Count -gt 0) {
  throw ("Final GUI/bank/shop validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Final GUI/bank/shop validation passed: ATM audit schema, safe GUI errors, shop/bank separation, and manual test matrix are present.'
