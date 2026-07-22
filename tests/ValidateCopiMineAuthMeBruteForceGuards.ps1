$ErrorActionPreference = 'Stop'
$path = Join-Path (Split-Path -Parent $PSScriptRoot) 'minecraft/server/plugins/AuthMe/config.yml'
$text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
foreach ($pattern in @(
    '(?m)^\s*maxLoginPerIp:\s*[1-9][0-9]*\s*$',
    '(?m)^\s*maxJoinPerIp:\s*[1-9][0-9]*\s*$',
    '(?m)^\s*useCaptcha:\s*true\s*$',
    '(?m)^\s*enableTempban:\s*true\s*$',
    '(?m)^\s*minPasswordLength:\s*12\s*$',
    '(?m)^\s*passwordHash:\s*BCRYPT\s*$',
    '(?m)^\s*-\s*''SHA256''\s*$',
    '(?m)^\s*BCRYPT.*$'
)) {
    if ($text -notmatch $pattern) { throw "AuthMe brute-force guard missing: $pattern" }
}
Write-Output 'CopiMine AuthMe brute-force guards: OK'
