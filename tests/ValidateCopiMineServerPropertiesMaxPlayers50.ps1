. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.ServerProperties
Require-Regex $server '(?m)^max-players=50$' 'server.properties must set max-players=50.'
Throw-IfErrors 'ValidateCopiMineServerPropertiesMaxPlayers50'
