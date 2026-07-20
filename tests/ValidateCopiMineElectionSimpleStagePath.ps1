. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$stateMachine = Method-Body $election 'private final class ElectionStateMachine {'

if ($null -eq $stateMachine) {
  $errors.Add('ElectionStateMachine block not found.')
} else {
  Require-Contains $stateMachine 'if (to == ElectionStage.VOTING)' 'Review stage must allow the optional-debates shortcut to voting.'
  Require-Contains $stateMachine 'activeCandidates < 2' 'The simplified voting path must still require two approved candidates.'
  Require-Contains $stateMachine 'stations < 1' 'The simplified voting path must still require an active polling station.'
}

Require-Contains $election 'OPTIONAL_DEBATES_STAGE' 'Debates lore must explain that the stage is optional.'
Require-Contains $election 'REVIEW_CAN_SKIP_DEBATES' 'Review lore must explain the shorter process.'
Require-Contains $election 'requireStationForElection' 'Administrator-issued election items must validate the selected station.'
if ($election -notmatch '(?s)issueApplicationBookByAdmin\([\s\S]*?requireStationForElection\(stationId, electionId\)') {
  $errors.Add('Application issuance must validate the selected station against the active election.')
}
if ($election -notmatch '(?s)issueBallotByAdmin\([\s\S]*?requireStationForElection\(stationId, electionId\)') {
  $errors.Add('Ballot issuance must validate the selected station against the active election.')
}

Throw-IfErrors 'ValidateCopiMineElectionSimpleStagePath'
