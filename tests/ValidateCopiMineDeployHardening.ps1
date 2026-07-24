. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$root = Split-Path -Parent $PSScriptRoot
$validator = Join-Path $root 'deploy\shared\validate_archive.py'

if (-not (Test-Path -LiteralPath $validator -PathType Leaf)) {
  $errors.Add('Shared archive validator is missing.')
} else {
  $text = Read-Utf8 $validator
  foreach ($needle in @('isreg()', 'isdir()', 'issym()', 'islnk()', 'duplicate', 'PurePosixPath')) {
    if (-not $text.Contains($needle)) { $errors.Add("Archive validator lacks $needle protection.") }
  }
}

foreach ($relative in @(
  'deploy\ubuntu\copimine_auth_patch.sh',
  'deploy\ubuntu\copimine_full_replace.sh',
  'deploy\ubuntu\rollback.sh'
)) {
  $path = Join-Path $root $relative
  $text = Read-Utf8 $path
  if (-not $text.Contains('validate_archive.py')) { $errors.Add("$relative does not call the shared archive validator.") }
}

foreach ($relative in @('deploy\windows\send_copimine_release.ps1','deploy\windows\upload_release.ps1','scripts\windows\upload_release.ps1')) {
  $text = Read-Utf8 (Join-Path $root $relative)
  if ($text -notmatch 'unsafe|SafeUnixPath|Quote-Bash') { $errors.Add("$relative does not validate remote shell inputs.") }
}
$liveSmoke = Read-Utf8 (Join-Path $root 'admin-web\deploy\copimine-live-smoke.sh')
if ($liveSmoke -notmatch 'POSTGRES_SCHEMA.*\^\[A-Za-z_\]') { $errors.Add('Live smoke does not validate POSTGRES_SCHEMA.') }
$wepif = Read-Utf8 (Join-Path $root 'minecraft\server\wepif.yml')
if ($wepif -match '(?ms)default:\s*permissions:\s*\r?\n\s*-\s*worldedit') { $errors.Add('WEPIF default group still grants WorldEdit.') }

$fullReplace = Read-Utf8 (Join-Path $root 'deploy\ubuntu\copimine_full_replace.sh')
if ($fullReplace.Contains('WARNING: PostgreSQL backup skipped')) {
  $errors.Add('Full replacement still continues after a skipped PostgreSQL backup.')
}
if ($fullReplace -match 'systemctl stop \"\$service\" \|\| true') {
  $errors.Add('Full replacement still ignores service stop failures.')
}

$rollback = Read-Utf8 (Join-Path $root 'deploy\ubuntu\rollback.sh')
if ($rollback.IndexOf('rollback_armed=1') -gt $rollback.IndexOf('mv "$COPIMINE_ROOT" "$old_root"')) {
  $errors.Add('Rollback trap is armed after the live tree move.')
}

Throw-IfErrors 'ValidateCopiMineDeployHardening'
