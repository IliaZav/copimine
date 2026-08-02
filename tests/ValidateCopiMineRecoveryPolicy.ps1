. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$narcotics = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$narcoticsDb = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')
$adminPlus = Read-Utf8 $Paths.Admin

$donationIdentityMatch = [regex]::Match($artifacts, '(?s)private (?:CopiMineArtifacts\.)?OfficialDonationRef rawDonationIdentity\(ItemStack (?:var1|stack)\).*?(?=\n\s+private )')
if (-not $donationIdentityMatch.Success) {
    $donationIdentityMatch = [regex]::Match($artifacts, '(?s)private (?:CopiMineArtifacts\.)?OfficialDonationRef officialDonationRef\(ItemStack (?:var1|stack|var1)\).*?(?=\n\s+private )')
}
$donationIdentity = $donationIdentityMatch.Value
if (-not $donationIdentityMatch.Success) {
    $errors.Add('Donation identity method could not be located for policy validation.')
}
Require-Contains $donationIdentity 'isDonationCatalogItem' 'Donation identity must accept only donation catalog items.'
Require-NotContains $donationIdentity 'isArCatalogItem' 'AR items must never enter the donation reclaim identity path.'
Require-NotContains $donationIdentity 'AR_SHOP_ITEM' 'AR PDC types must never be accepted by donation reclaim.'

Require-NotContains $narcotics 'queueNarcoticRecovery' 'Narcotic item loss must not create a recoverable refund.'
Require-NotContains $narcotics 'onNarcoticDespawn' 'Narcotic despawn must remain terminal, not recoverable.'
Require-NotContains $narcotics 'case "recover", "restore"' 'Narcotics must not expose a manual item-recovery command.'
Require-Contains $narcoticsDb 'Only failed cauldron ingredients may be queued for compensation.' 'Only cauldron ingredients may use the pending compensation journal.'
Require-Contains $narcotics 'Discarded legacy narcotic refund row' 'Legacy narcotic refund rows must be consumed without issuing an item.'
Require-NotContains $narcoticsDb 'source_instance_id' 'Narcotics pending compensation must not track recoverable item instances.'

$legacyReturnMatch = [regex]::Match($adminPlus, '(?s)private void restorePendingOfficialItems\(Player p,String reason\).*?(?=\n\s+private )')
Require-Contains $legacyReturnMatch.Value 'isOfficialArItem(item)' 'Compatibility recovery must not reissue stale AR entries.'
Require-Contains $legacyReturnMatch.Value 'discardedNonRecoverable' 'Stale non-recoverable entries must be explicitly discarded and audited.'

Throw-IfErrors 'ValidateCopiMineRecoveryPolicy'
