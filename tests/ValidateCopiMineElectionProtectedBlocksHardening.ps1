. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

foreach ($needle in @(
  'EntityExplodeEvent',
  'BlockFromToEvent',
  'BlockFadeEvent',
  'BlockIgniteEvent',
  'onProtectedEntityExplode',
  'onProtectedFlow',
  'onProtectedFade',
  'onProtectedIgnite'
)) {
  Require-Contains $text $needle "Protected-block hardening marker missing: $needle"
}

Throw-IfErrors 'ValidateCopiMineElectionProtectedBlocksHardening'
