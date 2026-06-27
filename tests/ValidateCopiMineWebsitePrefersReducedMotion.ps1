. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$styles = Read-FrontendStyles
$renderer = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-render.js')

Require-Contains $styles '@media (prefers-reduced-motion: reduce)' 'Frontend styles must honour prefers-reduced-motion.'
Require-Contains $renderer 'prefers-reduced-motion: reduce' 'Homepage counter animation must short-circuit for reduced motion users.'

Throw-IfErrors 'ValidateCopiMineWebsitePrefersReducedMotion'
