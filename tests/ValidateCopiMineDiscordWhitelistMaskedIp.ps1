. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$discord = Read-Utf8 $Paths.Discord

Require-Contains $discord 'def masked_ip' 'Discord whitelist embed must use IP masking helper.'
Require-Contains $discord 'self.masked_ip(row.get("request_ip"))' 'Discord whitelist embed must mask request IP instead of exposing it raw.'
Require-NotContains $discord 'embed.add_field(name="IP", value=short(row.get("request_ip"), 128), inline=True)' 'Discord whitelist embed must not expose raw request_ip.'

Throw-IfErrors 'ValidateCopiMineDiscordWhitelistMaskedIp'
