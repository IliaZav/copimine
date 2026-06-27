. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$css = Read-FrontendStyles
$html = Read-Utf8 $Paths.FrontendIndex

Require-Contains $css '--bg: #eef3e8;' 'Current redesign must keep the light default theme token.'
Require-Contains $css '--accent: #2d7d3f;' 'Current redesign must keep the CopiMine green accent token.'
Require-Contains $css ':root[data-theme="dark"]' 'Current redesign must keep a dedicated dark theme override.'
Require-Contains $css '.public-status-grid' 'Homepage must keep the public status grid styles.'
Require-Contains $css '.public-status-card' 'Homepage must style the public status cards.'
Require-Contains $html 'id="publicStatusGrid"' 'Guest homepage must expose the public status grid container.'
Require-Contains $html 'id="publicOnlineBoard"' 'Guest homepage must expose the public online/status board container.'
Require-Contains $html 'data-theme-toggle="true"' 'Homepage shell must expose the theme toggle.'

Throw-IfErrors 'ValidateCopiMineWebGreenTheme'
