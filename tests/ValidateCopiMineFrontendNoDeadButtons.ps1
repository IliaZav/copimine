$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$bootstrapPath = Join-Path $root 'admin-web/frontend/assets/js/bootstrap.js'
$legacyPath = Join-Path $root 'admin-web/frontend/assets/js/legacy/app-legacy.js'

$bootstrap = Get-Content -LiteralPath $bootstrapPath -Raw -Encoding UTF8
$legacy = Get-Content -LiteralPath $legacyPath -Raw -Encoding UTF8

function Assert-Contains {
  param(
    [string]$Content,
    [string]$Needle,
    [string]$Message
  )

  if (-not $Content.Contains($Needle)) {
    throw $Message
  }
}

Assert-Contains $bootstrap 'const LEGACY_PUBLIC_REDIRECTS = new Map([' 'Legacy public redirect map is missing from bootstrap.js.'
@(
  '"start"',
  '"presidentbudgetshowcase"',
  '"register"'
) | ForEach-Object {
  Assert-Contains $bootstrap $_ "Public bootstrap route $_ is missing."
}

Assert-Contains $bootstrap 'window.addEventListener("hashchange"' 'Hashchange route glue is missing.'
Assert-Contains $bootstrap 'initThemeToggle();' 'Theme toggle bootstrap is missing.'
Assert-Contains $bootstrap 'requestLegacyRuntime();' 'Legacy runtime lazy-loading hook is missing.'

Assert-Contains $legacy 'Object.assign(dataClickHandlers, {' 'Legacy dataClickHandlers map is missing.'
Assert-Contains $legacy 'const adminLoaders = {' 'Admin loader map is missing.'
Assert-Contains $legacy 'const playerLoaders = {' 'Player loader map is missing.'

@(
  'dashboard: loadDashboard',
  'sources: loadSources',
  '"donation-balance": loadPlayerDonationBalance',
  '"donation-shop": loadPlayerDonationShop',
  '"donation-items": loadPlayerDonationItems',
  'settings: loadPlayerSettings',
  'security: loadPlayerSecurity',
  'support: loadPlayerSupport'
) | ForEach-Object {
  Assert-Contains $legacy $_ "Route loader mapping $_ is missing."
}

@(
  'playerCreateDonationSession: fromWindow("playerCreateDonationSession")',
  'playerRefreshDonationSession: fromWindow("playerRefreshDonationSession")',
  'playerForgetDonationSession: fromWindow("playerForgetDonationSession")',
  'playerBuyDonationItem: fromWindow("playerBuyDonationItem")',
  'adminDonationAddBalance: fromWindow("adminDonationAddBalance")',
  'adminDonationMarkPaid: fromWindow("adminDonationMarkPaid")',
  'adminDonationCancelSession: fromWindow("adminDonationCancelSession")',
  'adminDonationTestPurchase: fromWindow("adminDonationTestPurchase")',
  'pluginRegistryApply: fromWindow("pluginRegistryApply")',
  'pluginRegistryBackup: fromWindow("pluginRegistryBackup")',
  'pluginRegistryReload: fromWindow("pluginRegistryReload")',
  'pluginRegistrySelect: fromWindow("pluginRegistrySelect")',
  'pluginRegistryValidate: fromWindow("pluginRegistryValidate")',
  'snapshotInventoryFromInput: fromWindow("snapshotInventoryFromInput")'
) | ForEach-Object {
  Assert-Contains $legacy $_ "UI handler binding $_ is missing."
}

Write-Host 'ValidateCopiMineFrontendNoDeadButtons passed.'
