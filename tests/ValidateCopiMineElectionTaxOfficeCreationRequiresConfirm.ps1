$ErrorActionPreference = 'Stop'
$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

if ($source -notmatch '(?s)action\.equals\("apply:tax:create-office"\).*?player\.sendMessage') {
    throw 'President tax-office creation must be explicitly disabled.'
}
$adminMenu = [regex]::Match($source, '(?s)private void openPresidentAdminMenu\(Player player, int selectedPeriodHours\) \{.*?(?=\r?\n\s*private void openPresidentMandateMenu)')
if (-not $adminMenu.Success -or $adminMenu.Value -match '"tax:create-office"\);') {
    throw 'President admin GUI must not expose a tax-office creation action.'
}

Write-Host 'President tax-office creation disabled contract OK'
