$ErrorActionPreference = 'Stop'

$artifactsPath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$adminPath = Join-Path $PSScriptRoot '..\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$artifacts = Get-Content -LiteralPath $artifactsPath -Raw -Encoding UTF8
$admin = Get-Content -LiteralPath $adminPath -Raw -Encoding UTF8

$hub = [regex]::Match($admin, '(?s)private boolean openArtifactsShopHub\(Player p\)\{.*?(?=\r?\n    private )')
if (-not $hub.Success -or $hub.Value -notmatch 'getMethod\("openAdminShopHub",Player\.class\)' -or
    $hub.Value -match 'getDeclaredMethod\(' -or $hub.Value -match 'setAccessible\(') {
    throw 'AdminHub must invoke the public artifact shop API without private reflection.'
}

$isAdmin = [regex]::Match($artifacts, '(?s)private boolean isArtifactsAdmin\(Player var1\) \{.*?(?=\r?\n\s*private boolean hasArtifactPermission)')
if (-not $isAdmin.Success -or $isAdmin.Value -notmatch 'isRestrictedJuniorArtifactsAdmin\(var1\)' ) {
    throw 'Artifact administration must explicitly deny restricted junior admins.'
}

foreach ($permission in 'copimine.admin', 'copimine.ultra.admin', 'copimine.election.admin', 'copimine.election.cik', 'copimine.economy.admin', 'copimine.players.admin') {
    if ($isAdmin.Value -notmatch [regex]::Escape($permission)) {
        throw "Artifact shop access must recognise the existing admin role $permission."
    }
}

$permissionMethod = [regex]::Match($artifacts, '(?s)private boolean hasArtifactPermission\(Player var1, String var2\) \{.*?(?=\r?\n\s*private void noPermission)')
if (-not $permissionMethod.Success -or $permissionMethod.Value -notmatch 'isRestrictedJuniorArtifactsAdmin\(var1\)') {
    throw 'A junior admin cannot bypass restrictions through a direct artifact permission.'
}

if ($admin -match 'private boolean isCurator\(Player p\)\{return false;\}') {
    throw 'The CIK role marker must not be a permanent false stub.'
}

Write-Host 'Admin shop hub API and role permission contract OK'
