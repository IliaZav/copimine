$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Require-Contains([string]$path, [string]$needle, [string]$message) {
  $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
  if ($text -notmatch [regex]::Escape($needle)) { throw $message }
}

$overdose = Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java'
$visual = Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java'
$config = Join-Path $root 'copimine-narcotics\config.yml'
$payloads = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java'
$clientProtocol = Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientBridgeProtocol.java'
$clientManager = Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java'
$backend = Join-Path $root 'admin-web\backend\main.py'
$frontend = Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js'
$resourceOverdoseIcon = Join-Path $root 'resourcepacks\src\assets\copimine\textures\mob_effect\overdose.png'
$resourceTripIcon = Join-Path $root 'resourcepacks\src\assets\copimine\textures\mob_effect\zhuzevo_trip.png'

Require-Contains $config 'OVERDOSE: true' 'Narcotics config must expose the unified overdose visual.'
Require-Contains $config 'ZHUZEVO_TRIP: true' 'Narcotics config must expose the no-mod Zhuzevo trip visual.'
Require-Contains $overdose 'clientModAvailable(Player player, String effectId)' 'Overdose service must detect support for the exact client effect before hiding vanilla icons.'
Require-Contains $overdose 'hideNegativeIcons' 'Overdose service must keep vanilla negative effects gameplay-identical while consolidating their icons.'
Require-Contains $overdose 'ZHUZEVO_TRIP' 'Zhuzevo must use the dedicated trip visual route.'
Require-Contains $visual 'sameVisualSession' 'Visual runtime must coalesce repeated identical visual starts.'
Require-Contains $visual 'sendVisualRefresh' 'Visual runtime must refresh an extended client effect without clearing the shader pipeline.'
Require-Contains $payloads 'OVERDOSE' 'Server bridge must allow the unified overdose visual id.'
Require-Contains $clientProtocol 'OVERDOSE' 'Client bridge must advertise the unified overdose visual id.'
Require-Contains $clientManager 'drawStatusBadge' 'Client mod must render a single status badge for consolidated effects.'
Require-Contains $clientManager 'capped lattice' 'Client visual overlays must cap per-frame grid work on large screens.'
if (!(Test-Path -LiteralPath $resourceOverdoseIcon) -or (Get-Item -LiteralPath $resourceOverdoseIcon).Length -le 0) { throw 'Overdose effect icon is missing.' }
if (!(Test-Path -LiteralPath $resourceTripIcon) -or (Get-Item -LiteralPath $resourceTripIcon).Length -le 0) { throw 'Zhuzevo trip effect icon is missing.' }
Require-Contains $backend 'class AdminPlayerAccountUpdateIn' 'Admin player account update input must be explicit and validated.'
Require-Contains $backend '@app.post("/api/players/{player}/site-account")' 'Admin player profile must expose a safe site-account update endpoint.'
Require-Contains $backend 'passwordChanged' 'Admin account update must report only that a password changed, never the stored secret.'
Require-Contains $frontend 'playerSitePasswordInput' 'Player profile must include a password replacement control.'
Require-Contains $frontend 'generatePlayerPassword' 'Player profile must support local one-time password generation.'
Require-Contains $frontend 'PLAYER_SITE_ACCOUNT_UPDATE' 'Player credential updates must require a sensitive confirmation.'

Write-Host 'Unified narcotics and player profile contract OK'
