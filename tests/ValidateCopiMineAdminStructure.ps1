$source = Join-Path $PSScriptRoot '..\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 $source

$hubMatch = [regex]::Match($text, 'private void openHub\(Player p\)\{(?<body>.*?)p\.openInventory\(m\.inv\);', 'Singleline')
if (-not $hubMatch.Success) {
  throw 'openHub(Player p) not found'
}

$hub = $hubMatch.Groups['body'].Value
$hubActions = @([regex]::Matches($hub, '"(?<action>open:[^"]+|old)"') | ForEach-Object { $_.Groups['action'].Value })
$expectedHub = @('open:elections', 'open:economy', 'open:players', 'open:admin-map')
$unexpectedHub = @($hubActions | Where-Object { $_ -notin $expectedHub })
$missingHub = @($expectedHub | Where-Object { $_ -notin $hubActions })

if ($missingHub.Count -gt 0) {
  throw "Hub is missing required top-level sections: $($missingHub -join ', ')"
}

if ($unexpectedHub.Count -gt 0) {
  throw "Hub must only route to elections/economy/players/system; unexpected actions: $($unexpectedHub -join ', ')"
}

$playerMenuMatch = [regex]::Match($text, 'private void openPlayer\(Player admin,String name\).*?"open:p-pranks:', 'Singleline')
if (-not $playerMenuMatch.Success) {
  throw 'Player detail menu must contain the pranks tab'
}

if ($text -notmatch 'private void openPPranks\(Player a,String n\)') {
  throw 'Pranks tab method is missing from player branch'
}

if ($text -match '"ar:reset"') {
  throw 'Economy contains destructive ar:reset action'
}

if ($text -match 'DELETE FROM cmv7_ar_(events|balances|scan_reports)') {
  throw 'Economy code deletes AR economy tables; this must not be reachable from admin UI'
}

if ($text -notmatch 'new NamespacedKey\("copiminear", key\)') {
  throw 'AR economy does not use stable PDC keys for certified AR items'
}

if ($text -notmatch 'BlockDropItemEvent[\s\S]*tagArItem') {
  throw 'Mined AR drops are not certified before entering the economy'
}

if ($text -notmatch 'BlockPlaceEvent[\s\S]*recordArPlacedBlock') {
  throw 'Placed diamond ore is not tracked, so fake/recycled AR blocks can be certified again'
}

if ($text -notmatch 'countArItem[\s\S]*isOfficialAr') {
  throw 'AR balances do not prefer official certified AR item tags'
}

if ($text -notmatch 'cmv7_ar_placed_blocks') {
  throw 'AR placed-block guard table is missing'
}

if ($text -notmatch 'private void staffNotify\(String t\)') {
  throw 'Admin-only server notification helper is missing'
}

if ($text -notmatch 'for\(Player p:Bukkit\.getOnlinePlayers\(\)\)if\(hasAnyAdmin\(p\)\)msg\(p,t\)') {
  throw 'staffNotify must send admin server messages only to online admins'
}

if ($text -notmatch 'startCheck[\s\S]*staffNotify\(') {
  throw 'Check start is not announced to admins through staffNotify'
}

if ($text -notmatch 'stopCheck[\s\S]*staffNotify\(') {
  throw 'Check stop is not announced to admins through staffNotify'
}

if ($text -notmatch 'toggleFreeze[\s\S]*staffNotify\([\s\S]*staffNotify\(') {
  throw 'Freeze changes are not announced only to admins'
}

Write-Host 'Admin structure validation passed: hub=4 sections, pranks under players, economy safe, AR certified, staff notices private.'
