$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$handler = [regex]::Match($source, '(?s)private void handleMenuAction\(Player player, String action, ClickType click, MenuHolder holder\) throws Exception \{.*?(?=\r?\n\s*if \(action\.equals\("close"\))')

if (-not $handler.Success -or $handler.Value -notmatch 'enforceCurrentMenuAccess\(player, action\);') {
    throw 'Every election GUI action must re-check the player authorization before dispatch.'
}

if ($source -notmatch 'private void enforceCurrentMenuAccess\(Player player, String action\)' -or $source -notmatch 'requiresElectionAdminAction\(action\)' -or $source -notmatch 'requiresPresidentOrElectionAdminAction\(action\)') {
    throw 'Election menu authorization must distinguish administrator actions from president actions.'
}

foreach ($marker in @('"manage:"', '"apply:results:"', '"application:"', '"apply:tax:"', '"mandate:"')) {
    if ($source -notmatch [regex]::Escape($marker)) {
        throw "Expected protected action marker is missing: $marker"
    }
}

Write-Host 'Election menu authorization contract OK'
