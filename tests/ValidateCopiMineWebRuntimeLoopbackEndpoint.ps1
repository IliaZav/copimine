$ErrorActionPreference = "Stop"

$path = Join-Path $PSScriptRoot "..\admin-web\backend\main.py"
$text = Get-Content -Raw -Encoding UTF8 $path

$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains {
  param(
    [string]$content,
    [string]$needle,
    [string]$message
  )
  if ($content -notmatch [regex]::Escape($needle)) {
    $errors.Add($message)
  }
}

Require-Contains $text '@app.get("/api/runtime")' 'main.py must expose /api/runtime.'
Require-Contains $text 'async def runtime(request: Request, authorization: str = Header(default=""))' '/api/runtime must accept the Request and optional Authorization header.'
Require-Contains $text 'def request_is_direct_loopback(request: Request) -> bool:' 'Loopback bypass must be explicitly limited to direct, unproxied requests.'
Require-Contains $text 'forwarding_headers = ("forwarded", "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-forwarded-origin", "x-real-ip")' 'Loopback bypass must reject proxy forwarding headers.'

$runtime = [regex]::Match($text, '(?s)@app\.get\("/api/runtime"\).*?(?=\r?\n@app\.)').Value
Require-Contains $runtime 'if not request_is_direct_loopback(request):' '/api/runtime must require authentication for proxied requests.'
Require-Contains $runtime 'require_panel_admin(request, authorization)' '/api/runtime must still require panel admin outside direct loopback.'
Require-Contains $runtime 'managed_runtime_snapshot(PROJECT_ROOT, APP_ROOT)' '/api/runtime must return the managed runtime snapshot.'
if ($runtime -match 'if not is_loopback_request\(request\):') {
  $errors.Add('/api/runtime must not bypass authentication based only on the reverse-proxy peer address.')
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineWebRuntimeLoopbackEndpoint failed:`n - " + ($errors -join "`n - "))
}

Write-Host "ValidateCopiMineWebRuntimeLoopbackEndpoint passed."
