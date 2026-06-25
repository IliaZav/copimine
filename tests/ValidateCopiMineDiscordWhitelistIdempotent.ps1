. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$discord = Read-Utf8 $Paths.Discord
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $discord 'if status == "APPROVED":' 'Discord approval helper must treat repeated whitelist approvals as idempotent.'
Require-Contains $discord 'if str(item.get("status") or "").upper() == "APPROVED":' 'Discord approval helper must short-circuit repeated whitelist approvals without re-running side effects.'
Require-Contains $discord 'FOR UPDATE' 'Discord approval path must lock the whitelist request row before changing its state.'
Require-Contains $mainPy 'FOR UPDATE' 'Web approval path must also lock whitelist requests before approving them.'

Throw-IfErrors 'ValidateCopiMineDiscordWhitelistIdempotent'
