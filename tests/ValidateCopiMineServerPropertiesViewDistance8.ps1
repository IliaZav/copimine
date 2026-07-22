. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.ServerProperties
Require-Regex $server '(?m)^view-distance=10$' 'server.properties must keep the vanilla view-distance of 10.'
Throw-IfErrors 'ValidateCopiMineServerPropertiesViewDistance8'
