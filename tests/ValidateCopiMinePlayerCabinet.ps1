$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$bootstrapSource = Join-Path $root 'admin-web\frontend\assets\app.js'
$authPageSource = Join-Path $root 'admin-web\frontend\assets\js\auth\auth-page.js'
$routesSource = Join-Path $root 'admin-web\frontend\assets\js\shared\app-routes.js'
$legacyFrontendSource = Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js'
$styleSource = Join-Path $root 'admin-web\frontend\assets\style.css'
$legacyStyleSource = Join-Path $root 'admin-web\frontend\assets\css\legacy.css'
$indexSource = Join-Path $root 'admin-web\frontend\index.html'
$signinSource = Join-Path $root 'admin-web\frontend\signin.html'
$registerSource = Join-Path $root 'admin-web\frontend\register.html'

$backend = Get-Content -Raw -Encoding UTF8 $backendSource
$bootstrap = Get-Content -Raw -Encoding UTF8 $bootstrapSource
$authPage = Get-Content -Raw -Encoding UTF8 $authPageSource
$routes = Get-Content -Raw -Encoding UTF8 $routesSource
$legacyFrontend = Get-Content -Raw -Encoding UTF8 $legacyFrontendSource
$style = Get-Content -Raw -Encoding UTF8 $styleSource
$legacyStyle = Get-Content -Raw -Encoding UTF8 $legacyStyleSource
$index = Get-Content -Raw -Encoding UTF8 $indexSource
$signin = Get-Content -Raw -Encoding UTF8 $signinSource
$register = Get-Content -Raw -Encoding UTF8 $registerSource
$frontend = @($bootstrap, $authPage, $routes, $legacyFrontend) -join "`n"
$allStyles = @($style, $legacyStyle) -join "`n"

$markers = @(
  '/api/player/register',
  '/api/player/login',
  '/api/session/me',
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
  '/cabinet/cabinet.html',
  '/signin.html',
  '/register.html',
  'data-page-kind="signin"',
  'data-page-kind="register"',
  'id="minecraftNameGroup"'
)

foreach ($marker in $markers) {
  $present = $backend.Contains($marker) -or $frontend.Contains($marker) -or $allStyles.Contains($marker) -or $index.Contains($marker) -or $signin.Contains($marker) -or $register.Contains($marker)
  if (-not $present) {
    throw "Missing player cabinet marker: $marker"
  }
}

if ($index -match 'data-auth-role="player"' -or $signin -match 'data-auth-role="player"' -or $register -match 'data-auth-role="player"') {
  throw 'Player cabinet must not depend on client-side role choice markers anymore.'
}

Write-Host 'Player cabinet validation passed: separate auth pages, player cabinet navigation, link flow, and bank actions are wired.'
