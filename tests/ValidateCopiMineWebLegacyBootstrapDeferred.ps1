. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'bootstrap.js')

Require-Contains $bootstrap 'import("./legacy/app-legacy.js")' 'Legacy SPA must be loaded through a deferred dynamic import.'
Require-NotContains $bootstrap 'import "./legacy/app-legacy.js";' 'Bootstrap must not eager-load the legacy SPA on every public page hit.'
Require-Contains $bootstrap 'copimine:legacy-runtime-request' 'Bootstrap must expose an explicit signal for loading the legacy runtime on demand.'
Require-Contains $bootstrap 'function pageKind()' 'Bootstrap must branch by concrete page kind before loading legacy runtime.'
Require-Contains $bootstrap 'if (pageKind() === "cabinet")' 'Bootstrap must only request legacy runtime for cabinet pages.'
Require-Contains $bootstrap 'normalizeLegacyPublicHash()' 'Bootstrap must keep public legacy hash redirects separated from cabinet/admin runtime loading.'

Throw-IfErrors 'ValidateCopiMineWebLegacyBootstrapDeferred'
