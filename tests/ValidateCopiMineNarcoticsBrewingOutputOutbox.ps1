. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$db = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')
$plugin = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$cauldron = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')

Require-Contains $db 'narcotics_pending_outputs' 'Completed brews need a dedicated durable world-output row.'
Require-Contains $db 'completePendingBrewingOutput(String id)' 'World-output pickup needs an idempotent completion step.'
Require-Contains $db 'WORLD_DROPPED' 'Completed brews need a durable world-delivery state.'
Require-Contains $db 'state_version=? AND deleted=FALSE' 'Brew completion must use an exact-version tombstone CAS.'
Require-Contains $db 'brewing-completion.journal' 'Completion writes need a local crash-recovery journal.'
Require-Contains $cauldron 'dropCompletedBrewingOutput(dropLocation, definition, outputId)' 'Finished brews must be dropped beside the cauldron.'
Require-Contains $plugin 'pending_brewing_output_id' 'Output delivery needs an item-level idempotency marker.'
Require-Contains $plugin 'onPendingOutputPickup' 'A public world output needs a pickup acknowledgement.'
Require-Contains $plugin 'database.completePendingBrewingOutput(outputId)' 'Pickup must close the public output row without an owner check.'
Require-Contains $plugin 'onPendingOutputDrop' 'Pending output must be protected from player drops.'
Require-Contains $plugin 'isPendingOutputItem(event.getItem())' 'Pending output must be protected from hopper transfers.'

if ($plugin.Contains('pending_brewing_output_owner') -or $plugin.Contains('pendingOutputOwner')) {
    $errors.Add('Brewing outputs must not carry or enforce a player owner marker.')
}
if ($plugin.Contains('getInventory().addItem(output)')) {
    $errors.Add('Brewing outputs must never be delivered through a player inventory mailbox.')
}
if ($cauldron.Contains('requestPendingBrewingOutputDelivery')) {
    $errors.Add('Brewing completion must not request owner mailbox delivery.')
}

$refundStart = $plugin.IndexOf('private void processPendingRefunds')
$refundEnd = $plugin.IndexOf('private void markPendingRefund', $refundStart)
if ($refundStart -lt 0 -or $refundEnd -le $refundStart) {
    $errors.Add('Unable to locate processPendingRefunds for refund-policy check.')
} else {
    $refundSection = $plugin.Substring($refundStart, $refundEnd - $refundStart)
    if ($refundSection.Contains('NARCOTIC:')) {
        $errors.Add('Narcotic products must never be issued through the ingredient refund mailbox.')
    }
    Require-Contains $refundSection 'INGREDIENT:' 'Only unused ingredient compensation may enter the refund mailbox.'
}

Throw-IfErrors 'ValidateCopiMineNarcoticsBrewingOutputOutbox'
