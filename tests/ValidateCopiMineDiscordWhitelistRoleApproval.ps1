. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$discord = Read-Utf8 $Paths.Discord

Require-Contains $discord 'WHITELIST_APPROVER_ROLE_NAMES' 'Discord bot must configure an explicit whitelist approver role set.'
Require-Contains $discord 'def can_approve_whitelist(member: Any) -> bool:' 'Discord bot must gate whitelist approval behind a dedicated helper.'
Require-Contains $discord 'if not can_approve_whitelist(member):' 'Discord bot must refuse whitelist approvals from users without the approver role.'

Throw-IfErrors 'ValidateCopiMineDiscordWhitelistRoleApproval'
