$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$handler = [regex]::Match($source, '(?s)if \(action\.startsWith\("president:tax-roster:"\)\) \{.*?(?=\r?\n\s*if \(action\.startsWith\("president:mark-paid:")')
$markPaidHandler = [regex]::Match($source, '(?s)if \(action\.startsWith\("president:mark-paid:"\)\) \{.*?(?=\r?\n\s*if \(action\.equals\("apply:manage:stop"\))')
$request = [regex]::Match($source, '(?s)private void openPresidentTaxRosterMenu\(Player player, boolean paid, int page\) \{.*?(?=\r?\n\s*private )')
$menu = [regex]::Match($source, '(?s)private void openPresidentTaxRosterMenu\(Player player, boolean paid, int page, PresidentTaxRoster roster\) \{.*?(?=\r?\n\s*private PresidentTaxRoster)')

if (-not $handler.Success -or $handler.Value -notmatch 'parseInt\(parts\[1\], 0\)') {
    throw 'Tax roster actions must retain the requested page.'
}

if (-not $request.Success -or $request.Value -notmatch 'runTaskAsynchronously\(this, \(\) ->' -or -not $menu.Success -or $menu.Value -notmatch 'roster\.rows\(\)\.get\(i\)' -or $menu.Value -notmatch 'pageButtons\(holder, inv, page, roster\.totalRows\(\), 21') {
    throw 'Tax roster must paginate the paid and unpaid player lists in 21-slot pages.'
}

if ($menu.Value -match 'int start = Math\.max\(0, page\) \* 21;') {
    throw 'Tax roster pages must be selected in the database, without loading prior pages into the game thread.'
}

if (-not $markPaidHandler.Success -or $markPaidHandler.Value -notmatch 'int page = rosterPage\(' -or $markPaidHandler.Value -notmatch '"apply:president:mark-paid:" \+ uuid \+ ":" \+ page') {
    throw 'Marking a tax payment must retain the currently open roster page.'
}

if ($markPaidHandler.Value -notmatch 'openPresidentTaxRosterMenu\(player, false, rosterPage\(') {
    throw 'The confirmation action must return to the same unpaid roster page.'
}

Write-Host 'President tax roster pagination contract OK'
