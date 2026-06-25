. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$main = Read-Utf8 $Paths.MainPy
$controlBody = Method-Body $main 'def election_control_command(action: str) -> str:'
$emergencyBody = Method-Body $main 'def election_emergency_command(action: str, data: ElectionEmergencyIn) -> str:'

if (-not $controlBody) {
  $errors.Add('Backend must keep the election control redirect helper.')
} else {
  Require-Contains $controlBody 'status_code=410' 'Election control helper must return HTTP 410.'
  Require-Contains $controlBody 'CopiMineElectionCore' 'Election control helper must redirect admins to the ElectionCore GUI.'
}

if (-not $emergencyBody) {
  $errors.Add('Backend must keep the election emergency redirect helper.')
} else {
  Require-Contains $emergencyBody 'status_code=410' 'Election emergency helper must return HTTP 410.'
  Require-Contains $emergencyBody 'GUI' 'Election emergency helper must point admins to an in-game GUI instead of executing web actions.'
}

Throw-IfErrors 'ValidateCopiMineWebElectionEmergency410'
