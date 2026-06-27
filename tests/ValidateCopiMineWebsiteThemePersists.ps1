. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'theme\theme-bootstrap.js')
$browserState = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'shared\browser-state.js')

Require-Contains $bootstrap 'window.localStorage.getItem(KEY)' 'Theme bootstrap must restore the stored theme preference.'
Require-Contains $bootstrap 'window.localStorage.setItem(KEY, next)' 'Theme bootstrap must persist the selected theme.'
Require-Contains $browserState '"copimine.theme"' 'Browser state allowlist must explicitly permit only the theme preference key.'

Throw-IfErrors 'ValidateCopiMineWebsiteThemePersists'
