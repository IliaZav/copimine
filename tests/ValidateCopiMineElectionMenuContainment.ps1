$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$click = [regex]::Match($source, '(?s)public void onInventoryClick\(InventoryClickEvent event\) \{.*?(?=\r?\n\s*@EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = true\)\r?\n\s*public void onInventoryDrag)')
$drag = [regex]::Match($source, '(?s)public void onInventoryDrag\(InventoryDragEvent event\) \{.*?(?=\r?\n\s*@EventHandler\(priority = EventPriority\.MONITOR\))')

if (-not $click.Success -or $click.Value -notmatch 'view\.getTopInventory\(\)\.getHolder\(\) instanceof MenuHolder holder' -or $click.Value -notmatch 'event\.getRawSlot\(\) >= view\.getTopInventory\(\)\.getSize\(\)') {
    throw 'Election menus must cancel bottom-inventory clicks before they can move items into a MenuHolder.'
}

if (-not $drag.Success -or $drag.Value -notmatch 'view\.getTopInventory\(\)\.getHolder\(\) instanceof MenuHolder') {
    throw 'Election menus must cancel every drag that targets an open MenuHolder.'
}

Write-Host 'Election menu containment contract OK'
