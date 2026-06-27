. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$index = Read-Utf8 $Paths.FrontendIndex
$responsive = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'responsive.css')
$layout = Read-Utf8 (Join-Path $Paths.FrontendAssetsCss 'layout.css')
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $index '<meta name="viewport" content="width=device-width, initial-scale=1"' 'Frontend must keep the responsive viewport meta tag.'
Require-Contains $index 'id="mobileNavToggle"' 'Frontend shell must keep the mobile navigation toggle.'
Require-Contains $responsive '@media (max-width: 720px)' 'Responsive stylesheet must define the narrow-screen breakpoint.'
Require-Contains $layout '@media (max-width: 980px)' 'Layout stylesheet must collapse treasury/public grids on smaller screens.'
Require-Contains $legacy 'mobileNavToggle' 'Legacy shell runtime must keep binding the mobile navigation toggle.'

Throw-IfErrors 'ValidateCopiMineWebsiteMobileResponsive'
