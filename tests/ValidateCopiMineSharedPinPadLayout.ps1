. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')

Require-Contains $election 'Map<Integer, Integer> digits = Map.of(' 'ElectionCore PIN pad must define a shared terminal-style layout.'
foreach ($needle in @('20, 1','21, 2','22, 3','29, 4','30, 5','31, 6','38, 7','39, 8','40, 9','48, 0','"&cCancel"','"&eClear"','"&aEnter"')) {
  Require-Contains $election $needle "ElectionCore PIN layout marker missing: $needle"
}
Require-Contains $admin 'Object[][] digits={{20,1},{21,2},{22,3},{29,4},{30,5},{31,6},{38,7},{39,8},{40,9},{48,0}};' 'AdminPlus ATM PIN pad must use the same terminal layout.'
foreach ($needle in @('"&cCancel"','"&eClear"','"&aEnter"')) {
  Require-Contains $admin $needle "AdminPlus PIN layout marker missing: $needle"
}
Require-Contains $artifacts 'int[] slots = {20,21,22,29,30,31,38,39,40,48};' 'Artifacts PIN pad must define the shared terminal-style layout.'
foreach ($needle in @('"&cCancel"','"&eClear"','"&aEnter"','"pin:cancel"','"pin:clear"','"pin:submit"')) {
  Require-Contains $artifacts $needle "Artifacts PIN layout marker missing: $needle"
}

Throw-IfErrors 'ValidateCopiMineSharedPinPadLayout'
