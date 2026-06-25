. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$discord = Read-Utf8 $Paths.Discord

Require-Contains $discord 'WHITELIST_CH = os.getenv("DISCORD_WHITELIST_CHANNEL_ID"' 'Discord bot must read the whitelist channel from configuration.'
Require-Contains $discord 'async def sync_whitelist_requests(self) -> None:' 'Discord bot must poll and sync whitelist requests into the whitelist channel.'
Require-Contains $discord 'self.state.setdefault("whitelist_messages", {})' 'Discord bot must persist whitelist message mapping for idempotent updates.'

Throw-IfErrors 'ValidateCopiMineDiscordWhitelistChannelConfig'
