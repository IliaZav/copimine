$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientConfig.java')
$manager = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java')
if ($config -notmatch 'render_when_hud_hidden') { throw 'Client config key render_when_hud_hidden missing.' }
if ($manager -notmatch 'config\.renderWhenHudHidden\(\)') { throw 'Visual manager must respect render_when_hud_hidden.' }
if ($manager -notmatch 'render_when_hud_hidden=') { throw 'Status output must include render_when_hud_hidden.' }
Write-Host 'CopiMineClient HUD-hidden config validation passed.'
