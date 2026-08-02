. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$db = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')
$plugin = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$cauldron = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')

Require-Contains $db 'narcotics_pending_outputs' 'Completed brews need a dedicated durable output mailbox.'
Require-Contains $db 'PendingBrewingOutput' 'Output mailbox rows need a typed DTO.'
Require-Contains $db 'reservePendingBrewingOutputs' 'Output mailbox needs an atomic reservation step.'
Require-Contains $db 'completePendingBrewingOutput' 'Output mailbox needs an idempotent completion step.'
Require-Contains $db 'state_version=? AND deleted=FALSE' 'Brew completion must use an exact-version tombstone CAS.'
Require-Contains $db 'brewing-completion.journal' 'Completion writes need a local crash-recovery journal.'
Require-Contains $cauldron 'requestPendingBrewingOutputDelivery' 'Finished brew output must use the durable mailbox.'
Require-Contains $plugin 'pending_brewing_output_id' 'Output delivery needs an item-level idempotency marker.'
Require-Contains $plugin 'onPendingOutputDrop' 'Pending output must be protected from player drops.'
Require-Contains $plugin 'isPendingOutputItem(event.getItem())' 'Pending output must be protected from hopper transfers.'

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
