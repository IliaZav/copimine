. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts

foreach ($needle in @(
  'visualEntityTypeKey',
  'visualKindKey',
  'visualLinkedIdKey',
  'visualModelIdKey',
  'PROTECTED_BLOCK_VISUAL'
)) {
  Require-Contains $election $needle "Missing protected block visual PDC marker: $needle"
  Require-Contains $admin $needle "AdminPlus is missing protected block visual PDC marker: $needle"
  Require-Contains $artifacts $needle "CopiMineArtifacts is missing protected block visual PDC marker: $needle"
}

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsHavePdc'
