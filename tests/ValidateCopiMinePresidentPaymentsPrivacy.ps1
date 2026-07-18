$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

$handler = [regex]::Match($source, '(?s)if \(action\.equals\("mandate:payments"\)\) \{.*?(?=\r?\n\s*if \(action\.startsWith\("taxpay:bank:"\))')
if (-not $handler.Success -or $handler.Value -notmatch '!isPresident\(player\) && !hasElectionAdmin\(player\)') {
    throw 'Payment list action must re-check president or administrator access.'
}

$payments = [regex]::Match($source, '(?s)private void openPresidentPaymentsMenu\(Player player, int page\) \{.*?(?=\r?\n\s*private void openLiveMenu)')
if (-not $payments.Success -or $payments.Value -notmatch '!isPresident\(player\) && !hasElectionAdmin\(player\)') {
    throw 'Payment list screen must reject ordinary players even when opened from stale GUI state.'
}

$taxOffice = [regex]::Match($source, '(?s)private void openTaxOfficeMenu\(Player player, String taxId, String mode, String pinBuffer\) \{.*?(?=\r?\n\s*private void renderPinPad)')
if (-not $taxOffice.Success -or $taxOffice.Value -match '"mandate:payments"') {
    throw 'Public tax office must not expose the president payment list action.'
}

Write-Host 'President payments privacy contract OK'
