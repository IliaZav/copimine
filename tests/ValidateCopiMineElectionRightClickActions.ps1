. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'event.getClick().isRightClick()' 'Inventory click handler must branch for right click.'
Require-Contains $text 'holder.rightActions().get(event.getRawSlot())' 'Right-click action lookup is missing.'
Require-Contains $text 'action = holder.actions().get(event.getRawSlot());' 'Left-click fallback action lookup is missing.'
Require-Contains $text 'holder.rightActions().put(slot, "vote:view-program:" + appId);' 'Ballot GUI must bind candidate program preview to right click.'

Throw-IfErrors 'ValidateCopiMineElectionRightClickActions'
