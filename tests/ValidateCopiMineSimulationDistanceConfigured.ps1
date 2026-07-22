. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.ServerProperties
Require-Regex $server '(?m)^simulation-distance=5$' 'server.properties must keep the release simulation-distance of 5.'
Throw-IfErrors 'ValidateCopiMineSimulationDistanceConfigured'
