$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)private List<Map<String, Object>> currentTaxPayments\(\) \{.*?(?=\r?\n\s*private Map<String, Object> activeTaxClockExemption)')

if (-not $method.Success -or $method.Value -notmatch 'Map<String, Object> tax = activeTax\(\)' -or $method.Value -notmatch 'WHERE tax_id=\?' -or $method.Value -notmatch 'WHERE term_id=\?') {
    throw 'President payment history must be scoped to the active tax and current presidential term.'
}

if ($method.Value -notmatch 'created_at>=\?') {
    throw 'Shop revenue shown to a president must start at the current term, not include previous terms.'
}

Write-Host 'Election current-term payments contract OK'
