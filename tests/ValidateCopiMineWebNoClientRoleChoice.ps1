. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$authPage = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\auth\auth-page.js')
$legacyApp = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js')
$publicSiteData = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\public\site-data.js')
$signin = Read-Utf8 $Paths.FrontendSignin
$register = Read-Utf8 (Join-Path $root 'admin-web\frontend\register.html')
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy '/api/session/me' 'Backend must expose a unified session identity endpoint.'
Require-Contains $authPage '/api/session/me' 'Auth page must resolve the active session through /api/session/me.'
Require-NotContains $authPage 'copimineLastRole' 'Auth page must not read or write client-side remembered roles.'
Require-NotContains $authPage '/api/auth/me' 'Auth page must not probe admin identity directly anymore.'
Require-NotContains $authPage '/api/player/me' 'Auth page must not probe player identity directly anymore.'

Require-Contains $legacyApp '/api/session/me' 'Legacy cabinet runtime must resolve the active session through /api/session/me.'
Require-NotContains $legacyApp 'getStoredUiState("copimineLastRole"' 'Legacy cabinet runtime must not derive session role from local storage.'
Require-Contains $publicSiteData '/api/session/me' 'Public site data must resolve auth state through /api/session/me.'
Require-NotContains $publicSiteData '/api/auth/me' 'Public site data must not probe admin identity directly anymore.'
Require-NotContains $publicSiteData '/api/player/me' 'Public site data must not probe player identity directly anymore.'

foreach ($pageText in @($signin, $register)) {
  Require-NotContains $pageText '>Команда сервера<' 'Auth pages must not contain a manual server-team role choice button.'
  Require-NotContains $pageText '>Игрок<' 'Auth pages must not contain a manual player role choice button.'
}

Throw-IfErrors 'ValidateCopiMineWebNoClientRoleChoice'
