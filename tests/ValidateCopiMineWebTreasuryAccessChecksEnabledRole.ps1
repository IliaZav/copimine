. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$web = Read-Utf8 $Paths.MainPy

Require-Contains $web 'if not meta or not bool(meta.get("enabled", True)):' 'Treasury-capable site admin check must reject disabled admin accounts.'
Require-Contains $web 'if not is_panel_admin_role(role):' 'Treasury-capable site admin check must require an allowed panel role.'
Require-Contains $web 'access_ok, _ = minecraft_access_ok(real_username)' 'Treasury-capable site admin check must require live Minecraft access.'
Require-NotContains $web '_, meta = resolve_admin_user(username)' 'Treasury-capable site admin check must not trust any matching admin record blindly.'
Require-NotContains $web 'return bool(meta)' 'Treasury-capable site admin check must not grant access from metadata presence alone.'

Throw-IfErrors 'ValidateCopiMineWebTreasuryAccessChecksEnabledRole'
