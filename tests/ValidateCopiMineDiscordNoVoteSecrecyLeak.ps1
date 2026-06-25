. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$discord = Read-Utf8 $Paths.Discord

Require-NotContains $discord 'voter_uuid' 'Discord bot must not expose voter UUIDs.'
Require-NotContains $discord 'voter_name' 'Discord bot must not expose voter names.'
Require-NotRegex $discord 'ballot_id[\s\S]{0,200}candidate' 'Discord bot must not publish ballot-to-candidate linkage.'

Throw-IfErrors 'ValidateCopiMineDiscordNoVoteSecrecyLeak'
