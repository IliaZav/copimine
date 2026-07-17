$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$python = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$treasury = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\player\treasury-pages.js')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @(
  'CREATE TABLE IF NOT EXISTS president_tax_exemptions',
  'active_president_tax_exemption',
  'TAX_CLOCK_EXEMPTION',
  'taxExemption',
  'expires_at>%s'
)) {
  if ($python -notmatch [regex]::Escape($marker)) { $errors.Add("Web tax clock marker is missing: $marker") }
}
if ($python -notmatch 'tax_exemption\s*=\s*active_president_tax_exemption') { $errors.Add('Web tax profile must load the active exemption.') }
if ($python -notmatch 'if tax_exemption:\s*\r?\n\s+raise HTTPException') { $errors.Add('Web tax payment must reject an active exemption.') }
foreach ($marker in @('formatTaxExemptionRemaining', 'taxExemptionCountdown', 'setTimeout', '60000')) {
  if ($treasury -notmatch [regex]::Escape($marker)) { $errors.Add("Bank countdown marker is missing: $marker") }
}
if ($treasury -match 'setInterval\(render,\s*1000\)') { $errors.Add('Bank countdown must not render every second.') }
if ($treasury -match 'сек\.') { $errors.Add('Bank countdown must not expose seconds.') }
if ($errors.Count) { throw ("Web tax clock validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineWebTaxClockExemption passed.'
