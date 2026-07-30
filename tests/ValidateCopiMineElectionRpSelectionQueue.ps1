$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Require([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { throw $message }
}

Require 'rpCandidateSelectionCampaigns' 'Candidate drafts must remember which RP campaign they belong to.'
Require 'enqueueRpCandidateAction' 'Candidate clicks and save must use one per-player queue.'
Require 'enqueueRpCandidateAction(player, "toggle candidate"' 'Candidate toggle action is not serialized.'
Require 'enqueueRpCandidateAction(player, "save candidates"' 'Candidate save action is not serialized behind pending clicks.'
Require 'rp:finish:early' 'The admin menu must expose an explicit early campaign finish action.'
Require 'setButton(holder, 32' 'The early finish button slot must be visible in the admin menu.'
Require 'stopRpElectionEarly' 'Early finish before voting must close the campaign safely.'
Require 'PlayerQuitEvent' 'Election temporary state must be cleaned when a player leaves.'
Require 'protectedInteractAt.remove(playerId);' 'Protected-block click debounce entries must not accumulate for departed players.'
Require 'rpCandidateSelections.remove(playerId);' 'Candidate-picker drafts must not survive a player disconnect.'
Require 'rpCandidateSelectionCampaigns.remove(playerId);' 'Candidate-picker campaign bindings must not survive a player disconnect.'

Write-Output 'Election RP candidate queue contract: PASS'
