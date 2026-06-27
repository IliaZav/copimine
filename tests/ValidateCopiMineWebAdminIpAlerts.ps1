. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$appJs = Read-FrontendBundle

Require-Contains $mainPy '@app.get("/api/admin/security/ip-alerts")' 'Web backend must expose the admin IP alerts endpoint.'
Require-Contains $mainPy 'def read_ip_alerts_sync(limit: int = 100) -> list[dict[str, Any]]:' 'Web backend must load IP alerts through a dedicated helper.'
Require-Contains $appJs '/api/admin/security/ip-alerts?limit=60' 'Frontend security view must request IP alerts.'
Require-Contains $appJs 'IP-alerts' 'Frontend security view must render the IP alerts panel.'

Throw-IfErrors 'ValidateCopiMineWebAdminIpAlerts'
