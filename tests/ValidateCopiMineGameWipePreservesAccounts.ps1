. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$root = Split-Path -Parent $PSScriptRoot
$sql = Read-Utf8 (Join-Path $root 'db\runtime\reset_game_state_preserve_accounts.sql')
$script = Read-Utf8 (Join-Path $root 'deploy\ubuntu\reset_game_state_preserve_accounts.sh')

$wipeList = [regex]::Match($sql, '(?s)wipe_names constant text\[\] := ARRAY\[(.*?)\];').Groups[1].Value
if (-not $wipeList) { $errors.Add('The SQL wipe list cannot be located.') }
foreach ($table in @('site_accounts','cm_admin_users','cm_admin_sessions','minecraft_account_links','whitelist_account_links','whitelist_requests')) {
  if ($wipeList -match "'${table}'") { $errors.Add("Protected table appears in the wipe list: $table") }
}
foreach ($marker in @('votes','candidate_applications','elections','president_terms','cmv4_bank_accounts','artifact_purchases','donation_purchases')) {
  if ($sql -notmatch [regex]::Escape("'${marker}'")) { $errors.Add("Game-state table is missing from the wipe list: $marker") }
}
foreach ($marker in @('COPIMINE_CONFIRM_GAME_WIPE','pg_dump','copimine-before-wipe.dump','accounts_before','whitelist_before','--wipe-worlds','realpath -e','mktemp -p /tmp','install -m 600')) {
  if ($script -notmatch [regex]::Escape($marker)) { $errors.Add("Wipe wrapper lacks guard/backup marker: $marker") }
}
if ($sql -match 'SET LOCAL search_path') { $errors.Add('Gameplay reset must keep the configured schema for the full psql session; SET LOCAL would be cleared.') }
if ($sql -notmatch 'SET search_path TO') { $errors.Add('Gameplay reset does not set the configured PostgreSQL schema.') }
$installer = Read-Utf8 (Join-Path $root 'deploy\ubuntu\install_release.sh')
foreach ($marker in @('reset_gameplay_state_if_requested','COPIMINE_CONFIRM_GAME_WIPE=YES','reset_game_state_preserve_accounts.sh')) {
  if ($installer -notmatch [regex]::Escape($marker)) { $errors.Add("Release installer does not invoke the guarded gameplay reset: $marker") }
}
Throw-IfErrors 'ValidateCopiMineGameWipePreservesAccounts'
