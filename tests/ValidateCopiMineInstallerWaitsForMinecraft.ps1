$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$installer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'deploy\ubuntu\install_release.sh')
if ($installer -notmatch 'wait_for_runtime_service') {
  throw 'Release installer must wait for services after the guarded gameplay reset.'
}
if ($installer -notmatch 'COPIMINE_MINECRAFT_START_TIMEOUT_SECONDS:-360') {
  throw 'Minecraft verification must allow fresh-world startup longer than the web service.'
}
if ($installer -notmatch 'journalctl -u "\$service" -n 120') {
  throw 'A service readiness failure must include the relevant journal in installer output.'
}
Write-Output 'ValidateCopiMineInstallerWaitsForMinecraft passed.'
