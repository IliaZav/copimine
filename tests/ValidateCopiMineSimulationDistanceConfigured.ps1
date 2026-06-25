. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.ServerProperties
Require-Regex $server '(?m)^simulation-distance=6$|(?m)^simulation-distance=7$|(?m)^simulation-distance=8$' 'server.properties must keep simulation-distance in the approved 6..8 range.'
Throw-IfErrors 'ValidateCopiMineSimulationDistanceConfigured'
