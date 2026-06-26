. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'donation-loss-journal.tsv' 'Artifacts must keep a durable donation loss journal for external item loss.'
Require-Contains $artifacts 'recordDonationLossJournal(' 'Artifacts must persist external loss events before async DB reconciliation.'
Require-Contains $artifacts 'flushPendingDonationLossJournalAsync()' 'Artifacts must asynchronously reconcile pending loss journal entries back into PostgreSQL.'
Require-Contains $artifacts 'applyDonationLossJournalEntry(' 'Artifacts must replay pending loss journal entries on startup or retry.'

Throw-IfErrors 'ValidateCopiMineDonationLossJournal'
