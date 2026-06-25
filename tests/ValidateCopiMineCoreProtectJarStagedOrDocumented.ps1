. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$stagedJar = Join-Path $root 'thirdparty\server-plugins\CoreProtect-CE-23.0.jar'
$serverJar = Join-Path $root 'minecraft\server\plugins\CoreProtect-CE-23.0.jar'
$scriptPs = Join-Path $root 'scripts\thirdparty\prepare_coreprotect.ps1'
$scriptSh = Join-Path $root 'scripts\thirdparty\prepare_coreprotect.sh'
$manifest = Join-Path $root 'thirdparty\thirdparty_manifest.json'
$doc = Read-Utf8 (Join-Path $root 'docs\THIRD_PARTY_PLUGINS.md')

if (-not (Test-Path $stagedJar) -and -not (Test-Path $serverJar)) {
  $errors.Add('CoreProtect jar must be staged in thirdparty/server-plugins or already copied into minecraft/server/plugins.')
}
if (-not (Test-Path $scriptPs) -or -not (Test-Path $scriptSh)) {
  $errors.Add('prepare_coreprotect scripts must exist for PowerShell and shell.')
}
if (Test-Path $manifest) {
  $manifestText = Read-Utf8 $manifest
  Require-Contains $manifestText 'CoreProtect-CE-23.0.jar' 'thirdparty_manifest.json must mention the staged CoreProtect jar.'
} else {
  $errors.Add('thirdparty_manifest.json must exist.')
}
Require-Contains $doc 'thirdparty/server-plugins/CoreProtect-CE-23.0.jar' 'Docs must mention the staging path for CoreProtect.'

Throw-IfErrors 'ValidateCopiMineCoreProtectJarStagedOrDocumented'
