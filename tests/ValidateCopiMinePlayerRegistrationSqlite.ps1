$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/backend/main.py')
$expected = 'if int(row_get(same_ip, "c", 0) or 0) >= 5:'

if ($source -notmatch [regex]::Escape($expected)) {
  throw 'Player registration must read the SQLite count row through row_get.'
}

Write-Host 'ValidateCopiMinePlayerRegistrationSqlite passed.'
