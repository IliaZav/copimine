. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'bootstrap.js')

Require-NotContains $bootstrap 'legacy/app-legacy.js' 'Retired legacy SPA must not be reachable from the modern bootstrap.'
Require-NotContains $bootstrap 'copimine:legacy-runtime-request' 'Retired legacy runtime event must not be wired into the modern bootstrap.'
Require-Contains $bootstrap 'function pageKind()' 'Bootstrap must branch by concrete page kind before loading legacy runtime.'
Require-Contains $bootstrap 'if (pageKind() === "cabinet")' 'Bootstrap must only request legacy runtime for cabinet pages.'
Require-Contains $bootstrap 'normalizeLegacyPublicHash()' 'Bootstrap must keep public legacy hash redirects separated from cabinet/admin runtime loading.'

Throw-IfErrors 'ValidateCopiMineWebLegacyBootstrapDeferred'
