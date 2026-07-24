. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'donation-loss-journal.tsv' 'Artifacts must keep a durable donation loss journal for external item loss.'
Require-Contains $artifacts 'recordDonationLossJournal(' 'Artifacts must persist external loss events before async DB reconciliation.'
Require-Contains $artifacts 'flushPendingDonationLossJournalAsync()' 'Artifacts must asynchronously reconcile pending loss journal entries back into PostgreSQL.'
Require-Contains $artifacts 'applyDonationLossJournalEntry(' 'Artifacts must replay pending loss journal entries on startup or retry.'
Require-Contains $artifacts 'public void onDonationItemRemoved(EntityRemoveEvent event)' 'Artifacts must catch plugin/discard item-entity removal that has no damage event.'
Require-Contains $artifacts 'EntityRemoveEvent.Cause.PLUGIN' 'Silent plugin item removal must be journaled for reclaim.'
Require-Contains $artifacts 'recordDonationLossOnce(' 'Loss handlers must deduplicate removal notifications for one instance.'
if ($artifacts -notmatch 'Keep the journal entry until the instance row becomes visible' -or $artifacts -notmatch '(?s)readDonationInstanceStatus\(var2, var1\.uniqueItemId\(\), var1\.itemId\(\)\).*?return false;') {
    $errors.Add('A loss journal entry must remain queued when its database instance is temporarily unavailable.')
}

Throw-IfErrors 'ValidateCopiMineDonationLossJournal'
