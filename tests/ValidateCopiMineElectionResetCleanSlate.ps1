. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

foreach ($needle in @(
  '"round_candidates"',
  '"elections"',
  'DELETE FROM protected_blocks',
  'DELETE FROM text_display_links',
  'removeOfficialItemsFromPlayer(online, "CIK_SEAL")',
  'removeOfficialItemsFromPlayer(online, "APPLICATION_BOOK")',
  'removeOfficialItemsFromPlayer(online, "BALLOT")',
  'removeOfficialItemsFromPlayer(online, "PRESIDENT_MANDATE")'
)) {
  Require-Contains $text $needle "Reset clean-slate marker missing: $needle"
}
Require-NotContains $text "status='RESET'" 'resetElections() must delete election state instead of only setting RESET status.'

Throw-IfErrors 'ValidateCopiMineElectionResetCleanSlate'
