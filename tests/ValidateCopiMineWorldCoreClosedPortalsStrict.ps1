. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$source = Read-Utf8 (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')

Require-Contains $source '@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)' 'WorldCore portal guards must run at highest priority.'
Require-Contains $source 'public void onPortal(PlayerPortalEvent event)' 'WorldCore must handle PlayerPortalEvent.'
Require-Contains $source 'public void onTeleport(PlayerTeleportEvent event)' 'WorldCore must handle PlayerTeleportEvent.'
Require-Contains $source 'if (isBlockedWorld(targetWorld, true))' 'WorldCore must block direct portal entry into closed worlds.'
Require-Contains $source 'if (isBlockedWorld(targetWorld, portalTeleport))' 'WorldCore must block teleport-based closed-world entry.'
Require-Contains $source 'redirectPlayer(event.getPlayer(), accessFor(to.getWorld()), blockedMessage(to.getWorld()));' 'WorldCore must eject players who end up inside blocked worlds.'

Throw-IfErrors 'ValidateCopiMineWorldCoreClosedPortalsStrict'
