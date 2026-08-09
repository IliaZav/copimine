$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

$refresh = [regex]::Match($source, '(?s)private void openAdminShopsFromDatabaseAsync\(Player var1\)\s*\{.*?(?=\r?\n\s*private void openAdminShops\()')
if (-not $refresh.Success) {
    throw 'The admin shop GUI must have a database-backed refresh entry point.'
}
if ($refresh.Value -notmatch 'loadShopsFromPostgres\(' -or $refresh.Value -notmatch 'openAdminShops\(var1\)') {
    throw 'The admin shop GUI refresh must reload artifact_shops before rendering.'
}

$hub = [regex]::Match($source, '(?s)public void openAdminShopHub\(Player player\)\s*\{.*?(?=\r?\n\s*private String nextGeneratedShopId\()')
if (-not $hub.Success -or $hub.Value -notmatch 'openAdminShopsFromDatabaseAsync\(player\)') {
    throw 'The AdminHub shop entry point must use the database-backed shop refresh.'
}

$refreshCase = [regex]::Match($source, '(?s)case ADMIN_SHOPS:\s*(.*?)(?=\r?\n\s*case ADMIN_CATALOG:)')
if (-not $refreshCase.Success -or $refreshCase.Value -notmatch 'openAdminShopsFromDatabaseAsync\(var1\)') {
    throw 'Refreshing the admin shop GUI must reload command-created shops from PostgreSQL.'
}

$list = [regex]::Match($source, '(?s)private void openAdminShopListAsync\(Player player\)\s*\{.*?(?=\r?\n\s*private boolean hasSeniorShopPermission\()')
if (-not $list.Success -or $list.Value -notmatch 'loadShopsFromPostgres\(') {
    throw 'The detailed admin shop list must reload command-created shops from PostgreSQL before calculating statistics.'
}

Write-Host 'Artifact shop GUI database refresh contract OK'
