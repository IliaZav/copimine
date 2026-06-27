. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$css = Read-FrontendStyles
$html = Read-Utf8 $Paths.FrontendIndex

Require-Contains $css '--bg: #06110b;' 'Green redesign must define the dark CopiMine background token.'
Require-Contains $css '--accent: #35f07f;' 'Green redesign must define the bright CopiMine accent token.'
Require-Contains $css '.public-status-grid' 'Green redesign must add the public status grid styles.'
Require-Contains $css '.public-status-card' 'Green redesign must style the new public status cards.'
Require-Contains $html 'id="publicStatusGrid"' 'Guest homepage must expose the public status grid container.'
Require-Contains $html 'id="publicOnlineBoard"' 'Guest homepage must expose the public online/status board container.'

Throw-IfErrors 'ValidateCopiMineWebGreenTheme'
