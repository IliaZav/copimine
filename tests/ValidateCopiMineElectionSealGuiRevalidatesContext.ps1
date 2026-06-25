. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'holder.data().put("seal_id"' 'Seal target GUI must store seal_id in holder data.'
Require-Contains $text 'holder.data().put("station_id"' 'Seal target GUI must store station_id in holder data.'
Require-Contains $text 'holder.data().put("election_id"' 'Seal target GUI must store election_id in holder data.'
Require-Contains $text 'holder.data().put("chair_uuid"' 'Seal target GUI must store chair_uuid in holder data.'
Require-Contains $text 'validateSealUsage(player.getInventory().getItemInMainHand(), player)' 'Seal target actions must re-validate the held seal against DB.'
Require-Contains $text 'issueApplicationBook(target, player, sealContext)' 'Seal target GUI must pass the validated SealContext into issueApplicationBook().'
Require-Contains $text 'issueBallot(target, player, sealContext)' 'Seal target GUI must pass the validated SealContext into issueBallot().'

Throw-IfErrors 'ValidateCopiMineElectionSealGuiRevalidatesContext'
