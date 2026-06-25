. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$config = Read-Utf8 (Join-Path $root 'thirdparty\config\CoreProtect\config.yml.example')

Require-Regex $config '(?m)^donation-key:\s*""\s*$' 'CoreProtect example config must not contain a real donation key.'
Require-Regex $config '(?m)^mysql-password:\s*""\s*$' 'CoreProtect example config must not contain a real password.'
Require-Regex $config '(?m)^use-mysql:\s*false\s*$' 'CoreProtect example config should default to separate local storage, not a shared SQL connection.'
Require-NotRegex $config '(?i)postgres|pghost|pguser|pgpassword|postgresql' 'CoreProtect example config must not point to CopiMine PostgreSQL directly.'

Throw-IfErrors 'ValidateCopiMineCoreProtectNoSecretConfig'
