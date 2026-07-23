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

$recipient = [regex]::Match($source, '(?s)private TaxRecipient taxRecipient\(Connection connection, String taxId\) throws Exception \{.*?(?=\r?\n\s*private )')
if (-not $recipient.Success -or $recipient.Value -notmatch "t\.status='ACTIVE'" -or $recipient.Value -notmatch "pt\.status='ACTIVE'" -or $recipient.Value -notmatch 'throw new IllegalStateException') {
    throw 'Tax payments must fail closed when a stale tax id no longer belongs to the active presidential term.'
}

Write-Host 'Election current-term payments contract OK'
