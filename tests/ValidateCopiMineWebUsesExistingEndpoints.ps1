. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$siteData = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-data.js')
$bundle = Read-FrontendBundle

foreach ($route in @(
  '/api/public/config',
  '/api/public/status',
  '/api/public/modpack',
  '/api/public/president-budget',
  '/api/public/president-budget/history?limit=${Number(limit) || 6}',
  '/api/public/president',
  '/api/public/shop/ar-items',
  '/api/public/shop/donation-items',
  '/api/session/me'
)) {
  Require-Contains $siteData $route "Public page data layer must keep using existing endpoint $route"
}

Require-NotContains $siteData '/api/auth/me' 'Public page data layer should use the unified /api/session/me endpoint instead of legacy /api/auth/me.'

foreach ($route in @(
  '/api/player/me',
  '/api/player/donation/balance',
  '/api/player/donation/history',
  '/api/player/shop/donation-items',
  '/api/player/shop/purchase-intent',
  '/api/admin/plugins/registry',
  '/api/resourcepack/status',
  '/api/artifacts/catalog',
  '/api/player/artifacts'
)) {
  Require-Contains $bundle $route "Frontend authenticated runtime must keep using existing endpoint $route"
}

Throw-IfErrors 'ValidateCopiMineWebUsesExistingEndpoints'
