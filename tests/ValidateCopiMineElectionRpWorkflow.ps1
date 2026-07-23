. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$main = Read-Utf8 $Paths.MainPy
$frontend = Read-FrontendBundle
$migration = Read-Utf8 (Join-Path $root 'db\migrations\20260723_013_rp_election_workflow.sql')
$guide = Read-Utf8 (Join-Path $root 'docs\ELECTIONS_RP_GUIDE_RU.md')

Require-Contains $main 'class PlayerElectionApplicationIn' 'Player application model is missing.'
Require-Contains $main '@app.post("/api/player/elections/application")' 'Player application route is missing.'
Require-Contains $main 'action not in {"start", "select", "stage", "finish", "remove", "resign"}' 'RP action allow-list is incomplete.'
Require-Contains $main '2 <= len(ids) <= 4' 'Candidate selection must require two to four applications.'
Require-Contains $main 'requested_deadline > max_deadline' 'Voting deadline must be capped at 72 hours.'
Require-Contains $main 'resignation_reason' 'President removal reason is not recorded.'
Require-Contains $main 'CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_voter_round' 'Backend must protect one vote per player and round.'
Require-Contains $main 'alreadyAtStage' 'Repeated stage actions must be idempotent.'

Require-Contains $election 'openDirectVoteMenu' 'Direct voting menu is missing.'
Require-Contains $election 'confirmDirectVote' 'Direct vote confirmation is missing.'
Require-Contains $election 'action.startsWith("direct-vote:")' 'Direct vote GUI actions must be accepted by the menu dispatcher.'
Require-Contains $election 'action.startsWith("apply:direct-vote:")' 'Direct vote confirmation actions must be accepted by the menu dispatcher.'
Require-Contains $election 'voting_deadline_at' 'The plugin must enforce the voting deadline.'
Require-Contains $election 'uq_votes_voter_round' 'The plugin must enforce one vote per player and round.'
Require-Contains $election 'reloadProtectedBlocksSafe' 'Web-created blocks must be picked up without a restart.'
Require-Contains $election 't + 7L * 86_400_000L' 'The RP president term must be exactly seven days.'
Require-Contains $election 'apply:president:resign' 'President self-resignation action is missing.'
Require-Contains $election 'resigned-by-president' 'President self-resignation must be audited separately.'

Require-Contains $frontend 'submitPlayerElectionApplication' 'Player application UI is missing.'
Require-Contains $frontend 'data-click="rpElectionControl(''stage'',''VOTING'')"' 'Admin voting control is missing.'
Require-Contains $frontend 'data-click="rpElectionControl(''remove'')"' 'Admin president removal control is missing.'
Require-Contains $frontend 'data-rp-application' 'Admin candidate selection UI is missing.'

Require-Contains $migration 'election_voting_blocks' 'Voting block migration is missing.'
Require-Contains $migration 'voting_deadline_at' 'Voting deadline migration is missing.'
Require-Contains $guide '# RP-' 'Russian election guide is missing.'
Require-Contains $guide '24' 'Guide must describe the 24-hour minimum.'
Require-Contains $guide '72' 'Guide must describe the 72-hour maximum.'
Require-Contains $guide '7' 'Guide must describe the president term.'

Throw-IfErrors 'ValidateCopiMineElectionRpWorkflow'
