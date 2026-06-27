. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$index = Read-Utf8 $Paths.FrontendIndex
$app = Read-Utf8 $Paths.FrontendApp
$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'bootstrap.js')

Require-Contains $index '<script type="module" src="/assets/app.js"></script>' 'Frontend shell must load the modular app entrypoint.'
Require-Contains $app 'import "./js/bootstrap.js";' 'assets/app.js must stay a thin bootstrap wrapper.'
Require-Contains $bootstrap 'import "./public/homepage.js";' 'Bootstrap must keep the public homepage split out of the legacy SPA.'
Require-NotContains $app 'fetch(' 'assets/app.js must not become a monolithic runtime with direct network logic.'
Require-NotContains $index 'fetch(' 'index.html must stay a shell and not embed business logic.'

Throw-IfErrors 'ValidateCopiMineWebNoSingleIndexApp'
