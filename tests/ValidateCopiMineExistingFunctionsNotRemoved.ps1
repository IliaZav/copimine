. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$index = Read-Utf8 $Paths.FrontendIndex
$server = Read-Utf8 $Paths.FrontendServer
$mods = Read-Utf8 $Paths.FrontendMods
$dashboard = Read-Utf8 $Paths.FrontendCabinetDashboard
$bundle = Read-FrontendBundle
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $server 'id="presidentBudgetCounter"' 'Frontend removed the public treasury counter mount from server.html'
Require-Contains $server 'id="publicTreasuryHistory"' 'Frontend removed the public treasury history mount from server.html'
Require-Contains $mods 'id="modpackFileGrid"' 'Frontend removed the modpack file mount from mods.html'
Require-Contains $index 'id="publicCabinetBtn"' 'Frontend removed the guest cabinet shortcut from index.html'
Require-Contains $index 'id="mobileNavToggle"' 'Frontend removed the mobile nav toggle contract from index.html'
Require-Contains $dashboard 'id="logout"' 'Frontend removed the authenticated logout control from cabinet/dashboard.html'

foreach ($needle in @(
  '/api/player/donation/sbp/session',
  '/api/player/shop/ar-items',
  '/api/player/shop/donation-items',
  '/api/player/bank/treasury',
  '/api/admin/donation/overview',
  '/api/admin/plugins/registry'
)) {
  Require-Contains $mainPy $needle "Backend no longer exposes required commerce/admin endpoint $needle"
}

Require-Contains $bundle 'PUBLIC_GUEST_HASH_ROUTES' 'Frontend must preserve explicit public guest route handling.'
Require-Contains $bundle 'copimine:legacy-runtime-request' 'Frontend must preserve the compatibility hook for loading legacy runtime on demand.'

Throw-IfErrors 'ValidateCopiMineExistingFunctionsNotRemoved'
