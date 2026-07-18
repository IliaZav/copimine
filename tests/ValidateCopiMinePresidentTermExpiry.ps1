$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

if ($source -notmatch 'runTaskAsynchronously\(this, this::expirePresidentTermsSafe\)' -or
    $source -notmatch 'runTaskTimerAsynchronously\(this, this::expirePresidentTermsSafe') {
    throw 'Expired president terms must be reconciled at startup and on a recurring background task.'
}

$activeTerm = [regex]::Match($source, '(?s)private Map<String, Object> activeTerm\(\) throws Exception \{.*?(?=\r?\n\s*public Map<String, Object> activePresidentRevenueProfile)')
if (-not $activeTerm.Success -or $activeTerm.Value -notmatch "ends_at>\?" -or $activeTerm.Value -notmatch 'now\(\)') {
    throw 'Active president term lookup must reject an expired end timestamp immediately.'
}

$isPresident = [regex]::Match($source, '(?s)private boolean isPresident\(Player player\) \{.*?(?=\r?\n\s*private boolean hasElectionAdmin)')
if (-not $isPresident.Success -or $isPresident.Value -notmatch "ends_at>\?" -or $isPresident.Value -match 'hasPermission\("copimine\.election\.president"\)') {
    throw 'President authority must come from an unexpired term, not a permanent permission fallback.'
}

$expiry = [regex]::Match($source, '(?s)private void expirePresidentTermsSafe\(\) \{.*?(?=\r?\n\s*private int currentRoundNumber)')
if (-not $expiry.Success) {
    throw 'President term expiry reconciliation is missing.'
}

foreach ($expected in @(
    "status='EXPIRED'",
    "president_taxes SET status='EXPIRED'",
    'removeOfficialItemsFromPlayer',
    'PRESIDENT_MANDATE',
    'ends_at<=?'
)) {
    if ($expiry.Value -notlike "*$expected*") {
        throw "President expiry reconciliation is missing: $expected"
    }
}

Write-Host 'President term expiry contract OK'
