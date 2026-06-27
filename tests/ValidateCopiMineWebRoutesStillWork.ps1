. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$index = Read-Utf8 $Paths.FrontendIndex
$server = Read-Utf8 (Join-Path $root 'admin-web\frontend\server.html')
$shops = Read-Utf8 (Join-Path $root 'admin-web\frontend\shops.html')
$mods = Read-Utf8 (Join-Path $root 'admin-web\frontend\mods.html')
$signin = Read-Utf8 $Paths.FrontendSignin
$register = Read-Utf8 $Paths.FrontendRegister
$dashboard = Read-Utf8 $Paths.FrontendCabinetDashboard

foreach ($route in @(
  '/api/auth/me',
  '/api/session/login',
  '/api/player/bank',
  '/api/player/donation/balance',
  '/api/player/shop/purchase-intent',
  '/api/admin/plugins/registry',
  '/api/resourcepack/status',
  '/api/public/president-budget',
  '/api/public/modpack'
)) {
  Require-Contains $mainPy $route "Backend route contract missing: $route"
}

foreach ($page in @(
  'admin-web\frontend\index.html',
  'admin-web\frontend\server.html',
  'admin-web\frontend\shops.html',
  'admin-web\frontend\mods.html',
  'admin-web\frontend\signin.html',
  'admin-web\frontend\register.html',
  'admin-web\frontend\cabinet\dashboard.html',
  'admin-web\frontend\cabinet\bank.html',
  'admin-web\frontend\cabinet\donation-balance.html',
  'admin-web\frontend\cabinet\donation-shop.html',
  'admin-web\frontend\cabinet\donation-items.html'
)) {
  if (-not (Test-Path -LiteralPath (Join-Path $root $page))) {
    $errors.Add("Frontend page missing after public split: $page")
  }
}

foreach ($link in @(
  'href="/server.html"',
  'href="/shops.html"',
  'href="/mods.html"',
  'href="/signin.html"'
)) {
  Require-Contains $index $link "Public home must link to $link"
}

Require-Contains $server 'id="presidentBudgetCounter"' 'server.html must keep the president budget counter.'
Require-Contains $server 'id="publicTreasuryHistory"' 'server.html must keep the public treasury history mount.'
Require-Contains $server 'id="publicOnlineBoard"' 'server.html must keep the online board mount.'

Require-Contains $shops 'id="publicArShopPreview"' 'shops.html must keep the AR shop preview mount.'
Require-Contains $shops 'id="publicDonationShopPreview"' 'shops.html must keep the donation shop preview mount.'

Require-Contains $mods 'id="modpackMetaGrid"' 'mods.html must keep the modpack meta mount.'
Require-Contains $mods 'id="modpackFileGrid"' 'mods.html must keep the modpack file mount.'

Require-Contains $signin 'id="signin"' 'signin.html must keep the public sign-in route mount.'
Require-Contains $signin 'id="loginForm"' 'signin.html must keep the login form.'
Require-Contains $register 'id="loginForm"' 'register.html must keep the registration form.'
Require-Contains $dashboard 'id="app" class="app"' 'cabinet/dashboard.html must keep the authenticated app shell.'

Throw-IfErrors 'ValidateCopiMineWebRoutesStillWork'
