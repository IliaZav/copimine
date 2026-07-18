. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$admin = Read-Utf8 $Paths.Admin
$adminYml = Read-Utf8 $Paths.AdminPluginYml
$narcotics = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$overdose = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java')
$worldCore = Read-Utf8 (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')
$worldPluginYml = Read-Utf8 (Join-Path $root 'copimine-world-core\plugin.yml')
$worldDefaultConfig = Read-Utf8 (Join-Path $root 'copimine-world-core\config.yml')
$worldActiveConfig = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\CopiMineWorldCore\config.yml')

Require-Contains $adminYml 'copimine.database.maintenance:' 'AdminPlus must declare the exact database-maintenance permission.'
Require-Contains $adminYml 'copimine.players.check:' 'AdminPlus must declare the exact player-check permission.'
Require-Contains $worldPluginYml 'copimine.world.admin:' 'WorldCore must own the exact permission required for world administration.'
Require-Contains $admin 'private boolean hasWorldCoreAdmin(CommandSender s)' 'WorldCore delegation must use a dedicated authorization helper.'
Require-Contains $admin 'private boolean hasDatabaseMaintenancePermission(CommandSender s)' 'Database maintenance must use a dedicated authorization helper.'
Require-Contains $admin 'private boolean hasPlayerCheckPermission(CommandSender s)' 'Player checks must use a dedicated authorization helper.'

$worldBridge = Method-Body $admin 'private boolean openWorldCoreHub(Player p)'
if ($null -eq $worldBridge -or $worldBridge -notmatch 'hasWorldCoreAdmin\(p\)') {
    $errors.Add('AdminPlus must require copimine.world.admin before opening the WorldCore bridge.')
}
$databaseHealth = Method-Body $admin 'private void openDatabaseHealthAsync(Player p)'
if ($null -eq $databaseHealth -or $databaseHealth -notmatch 'hasDatabaseMaintenancePermission\(p\)') {
    $errors.Add('Database health screen must require the dedicated maintenance permission.')
}
$databaseMaintenance = Method-Body $admin 'private void runDatabaseMaintenanceAsync(Player p,boolean checkpoint)'
if ($null -eq $databaseMaintenance -or $databaseMaintenance -notmatch 'hasDatabaseMaintenancePermission\(p\)') {
    $errors.Add('ANALYZE/checkpoint must require the dedicated maintenance permission.')
}
$playerCheckMenu = Method-Body $admin 'private void openPCheck(Player a,String n)'
if ($null -eq $playerCheckMenu -or $playerCheckMenu -notmatch 'hasPlayerCheckPermission\(a\)') {
    $errors.Add('The player-check menu must require the dedicated check permission.')
}
$playerActions = Method-Body $admin 'private void playerAction(Player admin, ClickType click, String a) throws Exception'
if ($null -eq $playerActions -or $playerActions -notmatch 'act\.startsWith\("check-"\).*hasPlayerCheckPermission\(admin\)') {
    $errors.Add('Player-check actions must re-authorize the caller before changing a check session.')
}
if ($null -eq $playerActions -or $playerActions -notmatch '"freeze"\.equals\(act\).*hasPlayerCheckPermission\(admin\)') {
    $errors.Add('The standalone freeze action must require the dedicated player-check permission.')
}
$freeze = Method-Body $admin 'private void toggleFreeze(Player a,Player t)'
if ($null -eq $freeze -or $freeze -notmatch 'hasPlayerCheckPermission\(a\)') {
    $errors.Add('Freeze/unfreeze must re-authorize the caller inside the state-changing helper.')
}
$unfreezeAll = Method-Body $admin 'private int unfreezeAllPlayers(Player admin)'
if ($null -eq $unfreezeAll -or $unfreezeAll -notmatch 'hasPlayerCheckPermission\(admin\)') {
    $errors.Add('Bulk unfreeze must re-authorize the caller inside the state-changing helper.')
}
if ($null -eq $playerActions -or $playerActions -notmatch '"cleanse"\.equals\(act\).*hasNarcoticsClearPermission\(admin\)') {
    $errors.Add('AdminPlus must not route a generic player role through console to clear a narcotics overdose.')
}

$narcoticsQuit = [regex]::Match($narcotics, '(?s)public void onQuit\(PlayerQuitEvent event\) \{.*?(?=\r?\n\s*@EventHandler)')
if (-not $narcoticsQuit.Success -or $narcoticsQuit.Value -notmatch 'overdoseService\.releasePlayerSession\(event\.getPlayer\(\)\)') {
    $errors.Add('Narcotics must invalidate the loaded session when a player leaves so a later join reloads and restores an active overdose.')
}
Require-Contains $overdose 'public void releasePlayerSession(Player player)' 'OverdoseService must provide a player-session invalidation path.'
Require-Contains $overdose 'readyStates.remove(playerUuid);' 'A disconnect must clear the overdose ready-state cache.'
Require-Contains $overdose 'states.remove(playerUuid);' 'A disconnect must discard stale cached overdose state before the next load.'
Require-Contains $overdose 'sessionEpoch' 'Late database callbacks must not revive a released player session.'
$stopVisuals = [regex]::Match($narcotics, '(?s)private boolean handleStopVisuals\(CommandSender sender, String\[\] args\) \{.*?(?=\r?\n\s*private boolean)')
if (-not $stopVisuals.Success -or $stopVisuals.Value -match 'overdoseService\.clearActiveEffects') {
    $errors.Add('The visuals-only stop command must not remove gameplay overdose effects.')
}

Require-NotContains $worldCore 'boolean denyOnlyMode = true;' 'Closed worlds must evacuate players instead of only warning them.'
$redirect = Method-Body $worldCore 'private void redirectPlayer(Player player, WorldAccess access, String message)'
if ($null -eq $redirect -or $redirect -notmatch 'player\.teleport\(safe\)') {
    $errors.Add('Closed-world enforcement must teleport players to the configured safe world.')
}
$worldHub = Method-Body $worldCore 'public void openAdminWorldHub(Player player)'
if ($null -eq $worldHub -or $worldHub -notmatch 'player\.hasPermission\("copimine\.world\.admin"\)') {
    $errors.Add('The public WorldCore GUI bridge must enforce the WorldCore permission itself.')
}
$worldClicks = Method-Body $worldCore 'public void onInventoryClick(InventoryClickEvent event)'
if ($null -eq $worldClicks -or $worldClicks -notmatch 'player\.hasPermission\("copimine\.world\.admin"\)') {
    $errors.Add('WorldCore GUI clicks must re-check the WorldCore permission.')
}

foreach ($config in @($worldDefaultConfig, $worldActiveConfig)) {
    Require-Contains $config '- CopiMine' 'WorldCore overworld configuration must target the production CopiMine world.'
    Require-Contains $config 'redirect_world: CopiMine' 'Closed Nether/End worlds must redirect to the production CopiMine overworld.'
    Require-Contains $config '- world_nether' 'Nether environment matching must keep its configured default name.'
    Require-Contains $config '- world_the_end' 'End environment matching must keep its configured default name.'
}

Throw-IfErrors 'ValidateCopiMineAuditLeastPrivilegeAndWorldSafety'
