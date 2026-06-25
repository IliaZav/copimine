$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 $source
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'sealBallotChoice' 'Voting must first seal the selected candidate into the physical ballot.'
Require-Contains 'depositSealedBallotAtStation' 'Voting must require depositing the sealed ballot into a polling station.'
Require-Contains 'vote-seal:' 'Candidate confirmation must seal the ballot instead of immediately casting a vote.'
Require-Contains 'vote-deposit:' 'Polling station UI must expose a ballot deposit action.'
Require-Contains 'selected_candidate' 'The chosen candidate must be stored on the ballot item before deposit.'
Require-Contains 'selected_candidate_name' 'The selected candidate display name must be stored on the ballot item.'
Require-Regex 'openVoteConfirm[\s\S]*vote-seal:[\s\S]*depositSealedBallotAtStation' 'Vote confirmation must route to sealing and station deposit, not direct ledger insert.'
Require-Regex 'onInteract[\s\S]*isPollingStationBlock[\s\S]*depositSealedBallotAtStation[\s\S]*openPollingStationHub' 'Right-clicking a station with a sealed ballot must deposit before opening the station hub.'
Require-Regex 'depositSealedBallotAtStation[\s\S]*INSERT INTO cmv731_votes[\s\S]*UPDATE cmv7_ballot_issues SET used=1' 'Deposit must write the vote ledger and mark the ballot used.'

Require-Contains 'requireElectionItemOwner' 'Official election items must enforce owner binding.'
Require-Regex 'onBook[\s\S]*requireElectionItemOwner\(p,old,"application_book"\)' 'Application books must be usable only by the assigned owner.'
Require-Regex 'openBallotCandidateHub[\s\S]*requireElectionItemOwner\(p,ballot,"ballot"\)' 'Ballot item opening must enforce item ownership.'
Require-Regex 'sealBallotChoice[\s\S]*findOwnedUnusedBallot' 'Sealing a vote must find an unused ballot owned by that player.'
Require-Regex 'depositSealedBallotAtStation[\s\S]*requireElectionItemOwner\(p,ballot,"ballot"\)' 'Depositing a ballot must re-check owner binding.'

Require-Contains 'isProtectedCustomItem' 'All non-AR custom official items must share one protection predicate.'
Require-Contains 'InventoryPickupItemEvent' 'Hoppers and similar inventories must not pick up protected custom items.'
Require-Contains 'ItemDespawnEvent' 'Protected custom items must not disappear by despawn if they ever enter the world.'
Require-Contains 'ItemMergeEvent' 'Protected custom items must not merge into world item stacks.'
Require-Regex 'onProtectedItemDamage[\s\S]*EntityDamageEvent[\s\S]*isProtectedCustomItem' 'Protected custom items must not be burned, exploded, or otherwise damaged in the world.'
Require-Contains 'isPresidentMandate' 'The president mandate must be recognized as a destroy-on-Q official item.'
Require-Contains 'handleDestroyableOfficialDrop' 'Q for CIK seal and president mandate must destroy the item instead of dropping it.'
Require-Regex 'onDrop[\s\S]*handleDestroyableOfficialDrop[\s\S]*isProtectedCustomItem[\s\S]*setCancelled\(true\)' 'Dropping protected items must be cancelled except destroy-on-Q official items.'
Require-Regex 'isProtectedOfficialItem[\s\S]*isProtectedCustomItem[\s\S]*!isOfficialArItem' 'AR items must stay excluded from the custom item storage guard.'

if ($errors.Count -gt 0) {
  throw ("Official item / ballot deposit validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Official item / ballot deposit validation passed: ownership, station deposit, and item guards are wired.'
