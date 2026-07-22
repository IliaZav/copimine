$ErrorActionPreference = 'Stop'
$path = Join-Path (Split-Path -Parent $PSScriptRoot) 'minecraft/server/plugins/ImageFrame/config.yml'
$text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
if ($text -notmatch '(?m)^\s*Enabled:\s*true\s*$') { throw 'ImageFrame URL restriction must be enabled.' }
foreach ($allowedHost in @('https://avatars.mds.yandex.net/','https://photos.anysex.com/')) {
    if ($text -notmatch [regex]::Escape($allowedHost)) { throw "ImageFrame allowlist missing $allowedHost" }
}
if ($text -match '(?m)^\s*Whitelist:\s*\[\s*\]\s*$') { throw 'ImageFrame URL allowlist must not be empty.' }
Write-Output 'CopiMine ImageFrame URL allowlist: OK'
