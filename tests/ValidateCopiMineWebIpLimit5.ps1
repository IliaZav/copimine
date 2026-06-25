. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy "SELECT COUNT(*) AS c FROM site_accounts WHERE registration_ip=%s" "Registration flow must count accounts by registration IP."
Require-Contains $mainPy 'if int((same_ip or {}).get("c") or 0) >= 5:' "Registration flow must enforce the five-accounts-per-IP limit."
Require-Contains $mainPy 'await bg(create_ip_alert_sync, registration_ip, username, minecraft_name, "site-account-limit"' "Registration flow must create an IP alert when the limit is exceeded."
Require-Contains $mainPy 'raise HTTPException(status_code=400, detail=' "Registration flow must return a vague player-facing error when the IP limit is hit."

Throw-IfErrors "ValidateCopiMineWebIpLimit5"
