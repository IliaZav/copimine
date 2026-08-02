$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$installer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'deploy\ubuntu\install_release.sh')
if ($installer -notmatch 'ARCHIVE_PATH.*--cleanup-legacy') {
  throw 'Installer must expose an explicit legacy cleanup mode.'
}
if ($installer -notmatch "home_root='/home/qwerty'") {
  throw 'Legacy cleanup must be scoped to the operator home path.'
}
if ($installer -notmatch 'game-wipe-\*') {
  throw 'Legacy cleanup must preserve the newest pre-wipe backup.'
}
if ($installer -notmatch 'copimine_prune_daily_backups 3') {
  throw 'Legacy cleanup must apply the three-daily-backup retention policy.'
}
Write-Output 'ValidateCopiMineLegacyCleanup passed.'
