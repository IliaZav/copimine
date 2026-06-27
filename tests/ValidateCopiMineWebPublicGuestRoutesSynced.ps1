. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$bootstrap = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\bootstrap.js')
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $bootstrap 'LEGACY_PUBLIC_REDIRECTS' 'bootstrap.js must centralize legacy public hash redirects.'
Require-Contains $bootstrap 'window.location.replace' 'bootstrap.js must redirect old guest hashes to the new public pages.'

foreach ($route in @(
  @{ bootstrap = 'mods'; legacy = 'mods' },
  @{ bootstrap = 'cabinet-zones'; legacy = 'cabinet-zones' },
  @{ bootstrap = 'presidentbudgetshowcase'; legacy = 'presidentBudgetShowcase' },
  @{ bootstrap = 'treasuryhistorysection'; legacy = 'treasuryHistorySection' },
  @{ bootstrap = 'shops'; legacy = 'shops' },
  @{ bootstrap = 'servers'; legacy = 'servers' }
)) {
  Require-Contains $bootstrap $route.bootstrap "bootstrap.js must keep legacy guest route '$($route.bootstrap)' synced with the multipage public site."
  Require-Contains $legacy $route.legacy "legacy guest route handling must keep '$($route.legacy)' reachable without auth."
}

Require-Contains $legacy 'window.location.href = "index.html"' 'Legacy guest pages action must route back to the public home page.'
Require-Contains $legacy 'PUBLIC_GUEST_HASH_ROUTES' 'Legacy runtime must centralize public guest route handling.'

Throw-IfErrors 'ValidateCopiMineWebPublicGuestRoutesSynced'
