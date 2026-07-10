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
Require-Contains $text 'if not is_loopback_request(request):' '/api/runtime must special-case trusted loopback probes.'
Require-Contains $text 'require_panel_admin(request, authorization)' '/api/runtime must still require panel admin outside loopback.'
Require-Contains $text 'managed_runtime_snapshot(PROJECT_ROOT, APP_ROOT)' '/api/runtime must return the managed runtime snapshot.'

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineWebRuntimeLoopbackEndpoint failed:`n - " + ($errors -join "`n - "))
}

Write-Host "ValidateCopiMineWebRuntimeLoopbackEndpoint passed."
