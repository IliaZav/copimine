. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$bootstrap = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\bootstrap.js')
$legacy = Read-Utf8 $Paths.FrontendLegacy

foreach ($route in @('mods', 'cabinet-zones')) {
  Require-Contains $bootstrap $route "bootstrap.js must treat '$route' as a public route."
  Require-Contains $legacy $route "legacy guest route handling must keep '$route' reachable without auth."
}

Require-Contains $bootstrap 'presidentbudgetshowcase' "bootstrap.js must treat 'presidentBudgetShowcase' as a public route."
Require-Contains $bootstrap 'treasuryhistorysection' "bootstrap.js must treat 'treasuryHistorySection' as a public route."
Require-Contains $legacy 'presidentBudgetShowcase' "legacy guest route handling must keep 'presidentBudgetShowcase' reachable without auth."
Require-Contains $legacy 'treasuryHistorySection' "legacy guest route handling must keep 'treasuryHistorySection' reachable without auth."

Require-Contains $legacy 'PUBLIC_GUEST_HASH_ROUTES' 'Legacy runtime must centralize public guest route handling.'

Throw-IfErrors 'ValidateCopiMineWebPublicGuestRoutesSynced'
