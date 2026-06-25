. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$docPath = Join-Path $root 'docs\THIRD_PARTY_PLUGINS.md'
$commandsPath = Join-Path $root 'docs\COREPROTECT_ADMIN_COMMANDS_RU.md'
$doc = Read-Utf8 $docPath
$commands = Read-Utf8 $commandsPath

Require-Contains $doc 'CoreProtect' 'THIRD_PARTY_PLUGINS.md must document CoreProtect.'
Require-Contains $doc 'https://hangar.papermc.io/CORE/CoreProtect' 'CoreProtect Hangar source must be documented.'
Require-Contains $doc 'https://github.com/PlayPro/CoreProtect' 'CoreProtect GitHub source must be documented.'
Require-Contains $doc 'PostgreSQL' 'CoreProtect docs must mention PostgreSQL separation explicitly.'
Require-Regex $doc '(?is)CoreProtect.+PostgreSQL' 'CoreProtect docs must clearly tie the plugin note to separate PostgreSQL storage guidance.'
Require-Contains $commands '/co rollback' 'COREPROTECT_ADMIN_COMMANDS_RU.md must document /co rollback.'
Require-Contains $commands '/co restore' 'COREPROTECT_ADMIN_COMMANDS_RU.md must document /co restore.'
Require-Contains $commands '/co lookup' 'COREPROTECT_ADMIN_COMMANDS_RU.md must document /co lookup.'

Throw-IfErrors 'ValidateCopiMineThirdPartyCoreProtectDocumented'
