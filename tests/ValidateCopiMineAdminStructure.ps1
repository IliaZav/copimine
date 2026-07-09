$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$text = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')

if ($text -notmatch 'public void openHub\(Player p\)') {
  throw 'openHub(Player p) public bridge not found'
}
if ($text -notmatch 'private void openMainHub\(Player p\)[\s\S]*"open:elections"[\s\S]*"open:economy"[\s\S]*"open:worlds"[\s\S]*"open:players"') {
  throw 'Main admin hub must expose elections, economy, worlds and players.'
}
foreach ($marker in @('openAdminMap','openElections','openPlayers','economy.openAdminEconomyHub(p)','new NamespacedKey("copiminear", key)','BlockDropItemEvent','tagArItem','BlockPlaceEvent','recordArPlacedBlock','countArItem','isOfficialArItem','private void staffNotify(String t)')) {
  if ($text -notmatch [regex]::Escape($marker)) {
    throw "Admin structure marker missing: $marker"
  }
}

Write-Host 'Admin structure validation passed: modular hub, delegated economy, AR certification and staff-private notices are wired.'
