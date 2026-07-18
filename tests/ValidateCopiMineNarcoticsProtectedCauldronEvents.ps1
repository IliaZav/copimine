$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

foreach ($method in @('onBreak\(BlockBreakEvent event\)', 'onCauldronLevelChange\(CauldronLevelChangeEvent event\)')) {
    $match = [regex]::Match($source, '(?s)@EventHandler\([^\)]*\)\s+public void ' + $method)
    if (-not $match.Success -or $match.Value -notmatch 'priority = EventPriority\.MONITOR' -or $match.Value -notmatch 'ignoreCancelled = true') {
        throw "Cauldron cleanup must run only after protection plugins have had a chance to cancel: $method"
    }
}

Write-Host 'Narcotics protected cauldron event contract OK'
