$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$messages = Join-Path $root 'copimine-admin-plugin\messages_ru.yml'
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($admin, $messages)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing Russian UI file: $path") }
}
if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  foreach ($marker in @('bank-atm','bank-atms','atm-pin','openEconomy','openBankAtm','openAtmPinPad')) {
    if (-not $java.Contains($marker)) { $errors.Add("Missing GUI marker: $marker") }
  }
  foreach ($english in @('Deposit hand','Deposit all','Withdraw 64 AR','Transfer recipient','Invalid bank PIN','No economy permission','ATM PIN','Applications review','Ballot ledger','Election audit','Player tools','&b&lTimeline','"&bTimeline"')) {
    if ($java.Contains($english)) { $errors.Add("Visible GUI still contains English text: $english") }
  }
}
if (Test-Path $messages) {
  $yaml = Get-Content -Raw -Encoding UTF8 $messages
  foreach ($marker in @('gui:','bank:','atm:','shop:','pin:','errors:')) {
    if (-not $yaml.Contains($marker)) { $errors.Add("messages_ru.yml missing marker: $marker") }
  }
}
if ($errors.Count -gt 0) { throw ("Russian UI validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Russian UI validation passed.'
