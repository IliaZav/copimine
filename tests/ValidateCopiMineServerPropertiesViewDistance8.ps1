. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.ServerProperties
Require-Regex $server '(?m)^view-distance=8$' 'server.properties must set view-distance=8.'
Throw-IfErrors 'ValidateCopiMineServerPropertiesViewDistance8'
