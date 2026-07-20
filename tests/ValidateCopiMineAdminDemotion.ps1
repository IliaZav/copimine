$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$frontend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
if ($backend -notmatch 'revoke_minecraft_admin_access') { throw 'Admin demotion does not revoke Minecraft access.' }
if ($backend -notmatch 'deop \{username\}' -or $backend -notmatch 'parent remove admin' -or $backend -notmatch 'parent remove junior_admin') { throw 'Demotion is missing OP/LuckPerms revocation.' }
if ($backend -notmatch 'demotionMessage' -or $backend -notmatch 'auth\.demoted_player_login') { throw 'Demoted-player login flow is missing.' }
if ($frontend -notmatch 'revokeAdmin\(' -or $frontend -notmatch 'restoreAdmin\(') { throw 'Admin panel has no demote/restore controls.' }
if (-not (Test-Path (Join-Path $root 'admin-web\frontend\cabinet\demoted.html'))) { throw 'Demotion landing page is missing.' }
Write-Host 'Admin demotion/restore validation passed.'
