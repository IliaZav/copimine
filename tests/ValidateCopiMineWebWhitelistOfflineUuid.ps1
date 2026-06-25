. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$discord = Read-Utf8 $Paths.Discord

Require-Contains $mainPy 'def offline_uuid_for_name' 'Web backend must define offline UUID helper for cracked/offline whitelist flow.'
Require-Contains $mainPy 'resolve_minecraft_uuid' 'Whitelist flow must resolve UUID from name when stored UUID is empty.'
Require-NotContains $mainPy 'if not site_account_id or not minecraft_uuid or not valid_minecraft_name(minecraft_name):' 'Whitelist request must not require pre-existing minecraft_uuid.'
Require-Contains $mainPy 'minecraft_uuid = resolve_minecraft_uuid' 'Whitelist request and approval must use resolved UUID.'
Require-Contains $discord 'bytearray(hashlib.md5(source).digest())' 'Discord whitelist approval must derive offline UUID when legacy rows have empty minecraft_uuid.'

Throw-IfErrors 'ValidateCopiMineWebWhitelistOfflineUuid'
