$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifacts = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($artifacts, $admin)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing source: $path") }
}
if (Test-Path $artifacts) {
  $java = Get-Content -Raw -Encoding UTF8 $artifacts
  foreach ($bad in @('bank:deposit','bank:withdraw','deposit-hand','deposit-all','UPDATE cmv4_bank_accounts','INSERT INTO cmv4_bank_ledger')) {
    if ($java.Contains($bad)) { $errors.Add("Artifacts must not expose/write bank operation: $bad") }
  }
  foreach ($marker in @('BridgeTxnResult','artifact_purchase','idempotency')) {
    if (-not $java.Contains($marker)) { $errors.Add("Artifacts missing bridge purchase marker: $marker") }
  }
  if ($java -notmatch 'bridge\s*\.\s*charge') { $errors.Add('Artifacts missing bridge purchase marker: bridge.charge') }
}
if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  foreach ($marker in @('ArtifactsBridge','artifactBankTxn','cmv4_bank_ledger','cmv4_bank_accounts')) {
    if (-not $java.Contains($marker)) { $errors.Add("AdminPlus missing bank/bridge marker: $marker") }
  }
}
if ($errors.Count -gt 0) { throw ("Bank/shop separation validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Bank/shop separation validation passed.'
