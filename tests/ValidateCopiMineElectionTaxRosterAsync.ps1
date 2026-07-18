$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$roster = [regex]::Match($source, '(?s)private void openPresidentTaxRosterMenu\(Player player, boolean paid, int page\) \{.*?(?=\r?\n\s*private )')

if (-not $roster.Success -or $roster.Value -notmatch 'runTaskAsynchronously\(this, \(\) ->' -or $roster.Value -match 'activeTax\(|queryList\(|dueTaxAmount\(|isTaxClockExempt\(') {
    throw 'Opening a tax roster must return immediately and perform all database work asynchronously.'
}

foreach ($marker in @(
    'private PresidentTaxRoster loadPresidentTaxRoster',
    'SUM(amount)',
    'GROUP BY player_uuid',
    'LIMIT ? OFFSET ?',
    'pageButtons(holder, inv, page, roster.totalRows()'
)) {
    if ($source -notmatch [regex]::Escape($marker)) {
        throw "Tax roster pagination/query contract missing: $marker"
    }
}

Write-Host 'Election tax roster async contract OK'
