. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'replaced_law_id' 'Law replacement flow must track the replaced law id.'
Require-Contains $text 'slot_no' 'Law replacement flow must preserve and reuse law slot numbers.'
Require-Contains $text "status='REPLACED'" 'Previous law must only become REPLACED after approval of the new text.'

Throw-IfErrors 'ValidateCopiMineElectionLawReplacementKeepsSlot'
