$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$roster = [regex]::Match($source, '(?s)private void openPresidentTaxRosterMenu\(Player player, boolean paid, int page\) \{.*?(?=\r?\n\s*private )')

if (-not $roster.Success -or $roster.Value -notmatch 'runTaskAsynchronously\(this, \(\) ->' -or $roster.Value -match 'activeTax\(|queryList\(|dueTaxAmount\(|isTaxClockExempt\(') {
    throw 'Opening a tax roster must return immediately and perform all database work asynchronously.'
}

$taxOffice = [regex]::Match($source, '(?s)private void openTaxOfficeMenu\(Player player, String taxId, String mode, String pinBuffer\) \{.*?(?=\r?\n\s*private )')
if (-not $taxOffice.Success -or $taxOffice.Value -notmatch 'loadTaxOfficeMenuAsync\(' -or $taxOffice.Value -match 'requireActiveTaxRecord\(|dueTaxAmount\(|paidTaxAmount\(|activeTaxClockExemption\(') {
    throw 'Opening a tax office must return immediately and perform all database work asynchronously.'
}

$taxOfficeLoader = [regex]::Match($source, '(?s)private void loadTaxOfficeMenuAsync\([^)]*\) \{.*?(?=\r?\n\s*private )')
if (-not $taxOfficeLoader.Success -or $taxOfficeLoader.Value -notmatch 'runAsync\(\(\) ->' -or $taxOfficeLoader.Value -notmatch 'runSync\(\(\) ->') {
    throw 'The tax office loader must fetch data asynchronously and render the menu on the Bukkit thread.'
}

foreach ($marker in @(
    'private void loadTaxOfficeMenuAsync',
    'private void renderTaxOfficeMenu',
    'requireActiveTaxRecord(taxId)',
    'dueTaxAmount(playerUuid, actualTaxId, tax)',
    'activeTaxClockExemption(playerUuid)'
)) {
    if ($source -notmatch [regex]::Escape($marker)) {
        throw "Tax office async contract missing: $marker"
    }
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
