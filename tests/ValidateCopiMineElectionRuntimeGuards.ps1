$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"

$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$backend = Read-Utf8 $Paths.MainPy

$candidateMenu = Method-Body $election 'private void openRpCandidatesMenu(Player player, int page) {'
$candidateToggle = Method-Body $election 'private void toggleRpCandidateSelection(UUID playerUuid, String applicationId) throws Exception {'
$candidateApply = Method-Body $election 'private void applyRpCandidateSelection(String actor, UUID playerUuid) throws Exception {'
$voting = Method-Body $election 'private void setRpVoting(String actor, int hours) throws Exception {'
$start = Method-Body $election 'private String startRpElection(String actor) throws Exception {'
$directVote = Method-Body $election 'private void confirmDirectVote(Player player, String stationId, String candidateUuid) {'
$directMenu = Method-Body $election 'private void openDirectVoteMenuAsync(Player player, String stationId) {'
$adminActions = Method-Body $election 'private boolean requiresElectionAdminAction(String action) {'
$presidentActions = Method-Body $election 'private boolean requiresPresidentOrElectionAdminAction(String action) {'
$mandateMenu = Method-Body $election 'private void openPresidentMandateMenu(Player player, int selectedPeriodHours) {'

Require-Contains $candidateMenu 'if (draftCampaign == null || !electionId.equals(draftCampaign))' 'Opening the candidate menu must initialise a newly-created draft from the persisted current-campaign selection.'
Require-Contains $candidateToggle 'String draftCampaign = rpCandidateSelectionCampaigns.get(playerUuid);' 'Candidate toggles must read the campaign recorded for the administrator draft.'
Require-Contains $candidateToggle 'if (!electionId.equals(draftCampaign))' 'Candidate toggles must reject a stale draft after the campaign changes.'
Require-Contains $candidateApply 'String electionId = activeRpElectionId();' 'Saving candidates must re-read the active campaign.'
Require-Contains $candidateApply 'if (electionId.isBlank() || !electionId.equals(draftCampaign))' 'Saving candidates must reject a draft from another campaign.'

Require-Contains $voting 'if (candidates < 2 || candidates > 4)' 'Voting must reject corrupted or legacy rosters outside the approved two-to-four candidate limit.'
Require-Regex $voting 'if \(oldDeadline > 0L && started \+ hours \* 60L \* 60L \* 1000L < oldDeadline\)' 'Reopening voting with a shorter duration must be rejected instead of reporting a false success.'
Require-Regex $voting 'if \(oldDeadline > 0L && started \+ hours \* 60L \* 60L \* 1000L < oldDeadline\) \{\s*throw new IllegalStateException' 'The shorter-duration guard must return an actionable state error instead of a false success.'

Require-Contains $start 'lockRpElectionLifecycle(connection);' 'Starting a campaign must take the same transaction lock as the web control path.'
Require-Contains $election 'private void lockRpElectionLifecycle(Connection connection) throws Exception {' 'ElectionCore must provide a PostgreSQL transaction-scoped lifecycle lock.'
Require-Contains $election "SELECT pg_advisory_xact_lock(hashtext('copimine-rp-election'))" 'ElectionCore lifecycle lock must serialize with the existing web election lock.'

Require-NotContains $directVote 'sendUserError(player, error' 'A normal vote-state conflict must be shown to a player as an actionable message, not a generic bug report.'
Require-NotContains $directMenu 'sendUserError(player, error' 'A station menu that becomes stale while loading must show an actionable message, not a generic bug report.'

if ([regex]::Matches($backend, 'if count < 2 or count > 4(?: or blocks < 1)?:').Count -lt 2) {
    $errors.Add('The web control must enforce the same two-to-four candidate limit for both debates and voting.')
}

Require-NotContains $adminActions 'action.equals("president:resign")' 'A president must be able to resign without also holding an administrator role.'
Require-NotContains $adminActions 'action.equals("apply:president:resign")' 'The confirmation of a president resignation must not require administrator permission.'
Require-Contains $presidentActions 'action.equals("president:resign")' 'The resignation menu action must require an active president or administrator context.'
Require-Contains $presidentActions 'action.equals("apply:president:resign")' 'The resignation confirmation must require an active president or administrator context.'
Require-Contains $mandateMenu '"president:resign"' 'The president mandate menu must expose the approved voluntary-resignation button.'

Throw-IfErrors 'ValidateCopiMineElectionRuntimeGuards'
