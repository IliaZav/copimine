$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'admin-web\frontend\assets\js\shared\app-routes.js'
$source = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)

if ($source -notmatch 'const\s+separator\s*=\s*href\.includes\("\?"\)\s*\?\s*"&"\s*:\s*"\?"') {
  throw 'appRouteHref must choose & when the route already contains a query string.'
}
if ($source -notmatch 'if\s*\(\s*!query\s*\)\s*return\s+href') {
  throw 'appRouteHref must keep the route unchanged when no parameters are supplied.'
}
if ($source -notmatch 'return\s+`\$\{href\}\$\{separator\}\$\{query\}`') {
  throw 'appRouteHref must append parameters without replacing the existing route query.'
}

Write-Output 'ValidateCopiMineWebAppRouteQueryMerge passed.'
