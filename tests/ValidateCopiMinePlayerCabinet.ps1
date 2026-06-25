$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$frontendSource = Join-Path $root 'admin-web\frontend\assets\app.js'
$styleSource = Join-Path $root 'admin-web\frontend\assets\style.css'
$indexSource = Join-Path $root 'admin-web\frontend\index.html'

$backend = Get-Content -Raw -Encoding UTF8 $backendSource
$frontend = Get-Content -Raw -Encoding UTF8 $frontendSource
$style = Get-Content -Raw -Encoding UTF8 $styleSource
$index = Get-Content -Raw -Encoding UTF8 $indexSource

$markers = @(
  '/api/player/register',
  '/api/player/login',
  '/api/player/me',
  '/api/player/link/request',
  '/api/player/link/confirm',
  '/api/player/bank',
  '/api/player/bank/pin',
  '/api/player/bank/transfer',
  '/api/players/{player}/bank-pin/reset',
  'const playerNavGroups',
  'loadPlayerCabinet',
  'loadPlayerLink',
  'loadPlayerBank',
  'playerRequestLinkCode',
  'playerConfirmLinkCode',
  'playerSetPin',
  'playerTransfer',
  'playerResetBankPin',
  'bankPinState',
  'temporaryPin',
  'resolveAuthSession',
  'data-auth-role="player"',
  'data-auth-action="register"',
  'id="minecraftNameGroup"',
  '.auth-switch',
  '.auth-toggle.active'
)

foreach ($marker in $markers) {
  $present = $backend.Contains($marker) -or $frontend.Contains($marker) -or $style.Contains($marker) -or $index.Contains($marker)
  if (-not $present) {
    throw "Missing player cabinet marker: $marker"
  }
}

if ($frontend -notmatch 'loadPlayerCabinet' -or $index -notmatch 'data-auth-role="player"') {
  throw 'Player cabinet login mode must be visible in Russian frontend and login screen.'
}

Write-Host 'Player cabinet validation passed: dual-mode auth, player cabinet navigation, link flow, and bank actions are wired.'
