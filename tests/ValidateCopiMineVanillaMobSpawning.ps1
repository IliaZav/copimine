. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$entityClearer = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\EntityClearer\config.yml')
$farmControl = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\FarmControl\config.yml')
$pufferfish = Read-Utf8 (Join-Path $root 'minecraft\server\pufferfish.yml')
$packaging = Read-Utf8 (Join-Path $root 'scripts\package_full_release.ps1')

Require-Regex $entityClearer '(?m)^global-interval:\s*0\s*$' 'EntityClearer must be disabled so natural and farm mobs are never periodically removed.'
Require-Regex $entityClearer '(?ms)^low-tps:\s*\r?\n(?:\s*#.*\r?\n)*\s+enabled:\s*false\b' 'EntityClearer low-TPS cleanup must be disabled for vanilla mob spawning.'
Require-Regex $farmControl '(?ms)^\s+proactive:\s*\[\s*\]\s*$' 'FarmControl proactive profiles must be empty to preserve vanilla animal farms.'
Require-Regex $farmControl '(?ms)^\s+reactive:\s*\[\s*\]\s*$' 'FarmControl reactive profiles must be empty to preserve vanilla animal farms.'
Require-Regex $pufferfish '(?m)^enable-async-mob-spawning:\s*false\s*$' 'Async mob spawning must be disabled so the vanilla spawn path is used.'
Require-Contains $packaging 'minecraft\server\plugins\EntityClearer\config.yml' 'The vanilla EntityClearer config must be included in release archives.'
Require-Contains $packaging 'minecraft\server\plugins\FarmControl\config.yml' 'The vanilla FarmControl config must be included in release archives.'

Throw-IfErrors 'ValidateCopiMineVanillaMobSpawning'
