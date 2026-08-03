$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Require-Contains([string]$path, [string]$needle, [string]$message) {
  $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
  if ($text -notmatch [regex]::Escape($needle)) { throw $message }
}

$backend = Join-Path $root 'admin-web\backend\main.py'
$common = Join-Path $root 'deploy\shared\common.sh'
$verify = Join-Path $root 'deploy\ubuntu\verify.sh'
$httpsTemplate = Join-Path $root 'admin-web\deploy\nginx-copimine-admin-https.conf'
$envExample = Join-Path $root 'admin-web\.env.example'

Require-Contains $backend 'resolve_http_auth_setting' 'Backend must resolve HTTP auth mode from the configured public URL when the setting is omitted.'
Require-Contains $backend 'ALLOW_INSECURE_HTTP_AUTH = resolve_http_auth_setting' 'Backend must use the URL-aware HTTP auth setting.'
Require-Contains $common 'COPIMINE_TLS_ENABLED' 'Ubuntu deployment must branch its public checks on TLS state.'
Require-Contains $common 'copimine_verify_public_endpoints' 'Ubuntu live verification must exercise the configured public HTTP/HTTPS endpoints.'
Require-Contains $common 'panel_is_https' 'TLS configuration must keep the public panel URL on HTTPS as well as the admin URL.'
Require-Contains $verify 'copimine_verify_runtime' 'Ubuntu live verification must call the runtime endpoint checks.'
Require-Contains $httpsTemplate 'listen 443 ssl' 'HTTPS nginx template must expose TLS.'
Require-Contains $httpsTemplate 'proxy_pass http://127.0.0.1:8090;' 'HTTPS nginx template must proxy directly to the loopback backend.'
if ((Get-Content -LiteralPath $httpsTemplate -Raw -Encoding UTF8) -match '(?m)\blisten\s+(?:\[::\]:)?(?:80|18080)\b') { throw 'HTTPS nginx template must not expose port 80 or the legacy 18080 relay.' }
Require-Contains $envExample 'ALLOW_INSECURE_HTTP_AUTH=0' 'HTTP login must be disabled by default; operators may opt in explicitly on a trusted LAN.'

Write-Host 'CopiMine HTTP/HTTPS contract OK'
