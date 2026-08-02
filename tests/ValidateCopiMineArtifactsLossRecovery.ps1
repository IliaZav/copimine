. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

# AR and donation instances share the same durable instance table, but AR
# items use a different PDC source/type. Both identities must enter the loss
# journal before Paper or another plugin can remove the physical stack.
Require-Contains $artifacts 'boolean arItem = this.isArCatalogItem(var6) && !this.isAdminOnlyCatalogItem(var6);' 'AR instances must be recognized by the loss/reclaim identity parser.'
Require-Contains $artifacts 'AR_SHOP_ITEM' 'AR PDC item type must be accepted by the loss/reclaim identity parser.'
Require-Contains $artifacts 'boolean arItem = this.isArCatalogItem(itemId) && !this.isAdminOnlyCatalogItem(itemId);' 'Raw AR identities must be accepted after restart.'
Require-Contains $artifacts 'recordDonationLossOnce' 'Destructive artifact paths must use the durable loss journal.'
Require-Contains $artifacts 'LOST_RECLAIMABLE' 'Loss reconciliation must create a reclaimable database state.'
Require-Contains $artifacts 'CONSUMED' 'Durability breaks must remain terminal and must not mint free replacements.'
Require-Contains $artifacts 'void onDonationItemInsideBlock' 'Cactus/block destruction must be covered.'
Require-Contains $artifacts 'public void onDonationItemDestroyed' 'Explosion/void/fire entity damage must be covered.'
Require-Contains $artifacts 'public void onDonationItemRemoved' 'Silent plugin/creative removal must be covered.'
Require-Contains $artifacts 'boolean reclaimAllowed = var4 == null' 'Reclaim must allow normal AR rows without a donation catalog row.'
Require-Contains $artifacts 'this.isArCatalogItem(var3.itemId()) && !this.isAdminOnlyCatalogItem(var3.itemId())' 'AR reclaim must remain limited to user-facing AR catalog items.'
Require-NotContains $artifacts 'if (var4 == null || !"LOSS_ONLY".equalsIgnoreCase' 'AR reclaim must not be rejected merely because it has no donation row.'
Require-Contains $artifacts 'removeDonationInstanceFromOnlineInventories(uniqueId)' 'Every physical duplicate must be removed after durable journal append.'

Throw-IfErrors 'ValidateCopiMineArtifactsLossRecovery'
