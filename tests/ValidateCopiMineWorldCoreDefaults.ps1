. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldConfig = Join-Path $root 'copimine-world-core\config.yml'
$config = Read-Utf8 $worldConfig

Require-Contains $config 'radius: 10000' 'WorldCore default overworld radius must be 10000.'
Require-Contains $config 'nether:' 'WorldCore config must declare Nether access section.'
Require-Contains $config 'end:' 'WorldCore config must declare End access section.'
Require-Regex $config 'nether:\s+enabled:\s+false' 'Nether must be closed by default.'
Require-Regex $config 'end:\s+enabled:\s+false' 'End must be closed by default.'

Throw-IfErrors 'ValidateCopiMineWorldCoreDefaults'
