. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$style = Read-Utf8 $Paths.FrontendStyle
$tokens = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'tokens.css')
$themes = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'themes.css')

Require-Contains $style '@import url("./css/tokens.css");' 'Frontend stylesheet must import the shared theme tokens.'
Require-Contains $style '@import url("./css/themes.css");' 'Frontend stylesheet must import the theme override layer.'
Require-Contains $tokens ':root[data-theme="light"]' 'Theme tokens must define a light-theme root.'
Require-Contains $themes ':root[data-theme="dark"]' 'Theme overrides must define a dark-theme root.'
Require-Contains $tokens '--body-gradient:' 'Theme tokens must define the shared body gradient.'
Require-Contains $themes '--body-gradient:' 'Dark theme overrides must redefine the body gradient.'

Throw-IfErrors 'ValidateCopiMineWebsiteCssVariablesThemes'
