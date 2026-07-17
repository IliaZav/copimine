$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$election = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$catalog = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\items.yml')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @(
  'grantTaxClockExemption',
  'president_tax_exemptions',
  'TAX_CLOCK_EXEMPTION',
  'expires_at',
  'plusMonths(',
  'isTaxClockExempt',
  'vremya_platit_nalogi_clock'
)) {
  if ($election -notmatch [regex]::Escape($marker) -and $artifacts -notmatch [regex]::Escape($marker) -and $catalog -notmatch [regex]::Escape($marker)) {
    $errors.Add("Missing tax clock contract marker: $marker")
  }
}

if ($artifacts -match 'showTaxClockStatus') {
  $errors.Add('Tax clock must not remain an informational-only status handler.')
}

if ($errors.Count -gt 0) {
  throw ("Tax clock contract validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineTaxClockExemptionContract passed.'
