. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'bootstrap.js')

Require-Contains $bootstrap 'import "./public/homepage.js";' 'Public homepage runtime must stay as the base bootstrap import.'
Require-Contains $bootstrap 'import("./legacy/app-legacy.js")' 'Legacy SPA must be loaded through a deferred dynamic import.'
Require-NotContains $bootstrap 'import "./legacy/app-legacy.js";' 'Bootstrap must not eager-load the legacy SPA on every public page hit.'
Require-Contains $bootstrap 'copimine:legacy-runtime-request' 'Bootstrap must expose an explicit signal for loading the legacy runtime on demand.'
Require-Contains $bootstrap 'needsLegacyRuntime' 'Bootstrap must distinguish between public hashes and cabinet/admin hashes before loading legacy runtime.'

Throw-IfErrors 'ValidateCopiMineWebLegacyBootstrapDeferred'
