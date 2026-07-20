$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Get-Content -Raw -LiteralPath (Join-Path $root 'admin-web/backend/main.py')

$start = $source.IndexOf('def resolve_admin_user(')
$end = $source.IndexOf('def read_sessions(', $start)
if ($start -lt 0 -or $end -lt 0) { throw 'Could not locate admin user resolution function.' }
$body = $source.Substring($start, $end - $start)
if ($body -notmatch 'status_code=503') { throw 'Database/auth-store failures are not converted to a stable 503 response.' }
if ($source -notmatch 'credentialsVisible') { throw 'Admin credential visibility contract is missing.' }
if ($source -notmatch 'passwordSet') { throw 'Admin password reset/display state is missing.' }

Write-Host 'Invalid-login handling and safe admin credential contract checks passed.'
