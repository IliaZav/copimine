. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'TRUSTED_PROXY_IPS' 'Web backend must declare trusted proxy IPs.'
Require-Contains $mainPy 'def get_client_ip' 'Web backend must use proxy-aware client IP resolver.'
Require-Contains $mainPy 'forwarded_for = str(request.headers.get("x-forwarded-for") or "").strip()' 'Client IP resolver must inspect X-Forwarded-For.'
Require-Contains $mainPy 'registration_ip = get_client_ip(request)' 'Registration IP limit must use proxy-aware IP detection.'
Require-Contains $mainPy 'create_whitelist_request_sync, account, get_client_ip(request)' 'Whitelist request flow must use proxy-aware IP detection.'
Require-Contains $mainPy 'audit_event(real_username, "auth.login", status="ok", details={"ip": get_client_ip(request)})' 'Login audit must use proxy-aware IP detection.'

Throw-IfErrors 'ValidateCopiMineWebProxyAwareIp'
