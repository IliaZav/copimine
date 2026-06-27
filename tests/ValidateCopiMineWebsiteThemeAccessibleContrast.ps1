. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$tokens = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'tokens.css')
$themes = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'themes.css')
$styles = Read-FrontendStyles

Require-Contains $tokens '--bg: #eef3e8;' 'Light theme must define a light background token.'
Require-Contains $tokens '--ink: #182419;' 'Light theme must define a dark readable foreground token.'
Require-Contains $themes '--bg: #06110b;' 'Dark theme must define a dark background token.'
Require-Contains $themes '--ink: #eff8f0;' 'Dark theme must define a light readable foreground token.'
Require-Contains $styles '.btn-primary' 'Frontend styles must keep primary button styling for contrast checks.'
Require-Contains $styles 'color: var(--ink);' 'Theme styles must actually consume readable foreground tokens.'
Require-Contains $styles 'background: var(--body-gradient);' 'Theme styles must apply semantic background tokens instead of hardcoded inversion.'
Require-NotContains $themes 'filter: invert(' 'Dark theme must not rely on CSS invert filters.'

Throw-IfErrors 'ValidateCopiMineWebsiteThemeAccessibleContrast'
