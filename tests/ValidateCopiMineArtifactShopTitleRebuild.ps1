$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

$menu = [regex]::Match($source, '(?s)private void openAdminShops\(Player var1\)\s*\{.*?(?=\r?\n\s*public void openAdminShopHub)')
if (-not $menu.Success -or $menu.Value -notmatch '(?s)setAction\(\s*var3,\s*var2,\s*47,.*?"admin:shop:refresh_titles"') {
    throw 'The admin shop GUI must expose a title-rebuild button.'
}

if ($source -notmatch '"admin:shop:refresh_titles"' -or $source -notmatch 'rebuildAdminShopTitlesAsync\(var1\)') {
    throw 'The title-rebuild GUI action must route to the asynchronous rebuild method.'
}

$rebuild = [regex]::Match($source, '(?s)private void rebuildAdminShopTitlesAsync\(Player player\)\s*\{.*?(?=\r?\n\s*private )')
if (-not $rebuild.Success) {
    throw 'The title-rebuild action must have a dedicated implementation.'
}
foreach ($marker in @('loadShopsFromPostgres\(', 'runAsync\(', 'runSync\(')) {
    if ($rebuild.Value -notmatch $marker) {
        throw "Title rebuild is missing required marker: $marker"
    }
}
if ($rebuild.Value -notmatch 'removeLoadedShopTitleDisplays\(' -or $rebuild.Value -notmatch 'createLoadedShopTitleDisplays\(') {
    throw 'Title rebuild must remove managed displays before creating DB-backed displays.'
}
if ($rebuild.Value -match '(?i)\b(INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER)\b') {
    throw 'Title rebuild must not mutate the database.'
}

$remove = [regex]::Match($source, '(?s)private int removeLoadedShopTitleDisplays\(\)\s*\{.*?(?=\r?\n\s*private )')
if (-not $remove.Success -or $remove.Value -notmatch 'SHOP_TITLE_DISPLAY') {
    throw 'Title rebuild must remove only plugin-owned SHOP_TITLE_DISPLAY entities.'
}

$create = [regex]::Match($source, '(?s)private int createLoadedShopTitleDisplays\(\)\s*\{.*?(?=\r?\n\s*private )')
if (-not $create.Success -or $create.Value -notmatch 'spawnShopTitleDisplay\(') {
    throw 'Title rebuild must recreate titles through the managed spawn helper.'
}

$chunkRepair = [regex]::Match($source, '(?s)private void repairShopTitleDisplays\(String worldName, int chunkX, int chunkZ\)\s*\{.*?(?=\r?\n\s*private void repairProtectedBlockVisuals\()')
if (-not $chunkRepair.Success -or $chunkRepair.Value -notmatch 'removeOrphanShopTitleDisplays\(') {
    throw 'Chunk-load repair must remove orphaned shop titles before recreating registered titles.'
}

Write-Host 'Artifact shop title rebuild contract OK'
