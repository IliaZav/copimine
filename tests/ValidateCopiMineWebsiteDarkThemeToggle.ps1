. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$index = Read-Utf8 $Paths.FrontendIndex
$toggle = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'theme\theme-toggle.js')
$components = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'components.css')

Require-Contains $index 'data-theme-toggle="true"' 'Frontend must expose a theme toggle button in the UI shell.'
Require-Contains $toggle 'toggleTheme()' 'Theme toggle runtime must be able to switch between light and dark.'
Require-Contains $toggle 'copimine:theme-change' 'Theme toggle runtime must rebroadcast theme changes.'
Require-Contains $toggle 'theme-toggle-label' 'Theme toggle runtime must render accessible button text.'
Require-Contains $components '.theme-toggle' 'Theme toggle must have dedicated component styling.'

Throw-IfErrors 'ValidateCopiMineWebsiteDarkThemeToggle'
