. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$source = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$frontend = Read-FrontendBundle

$root = [regex]::Match($source, '(?s)private void openElectionRoot\(Player player, int page\) \{.*?\n\s*\}\r?\n\r?\n\s*private void openLegacyElectionRoot')
if (-not $root.Success) { $errors.Add('Election root menu cannot be located.') }
else {
  foreach ($legacy in @('stage:PREPARATION','stage:APPLICATIONS','stage:REVIEW','stage:COUNTING','stage:SECOND_ROUND','open:cik','open:results')) {
    if ($root.Value.Contains($legacy)) { $errors.Add("Legacy action remains in the new election root: $legacy") }
  }
}

$manage = [regex]::Match($source, '(?s)private void openManagementMenu\(Player player\) \{.*?\n\s*\}\r?\n\r?\n\s*private void openRpManagementMenu')
if (-not $manage.Success) { $errors.Add('Election management menu cannot be located.') }
else {
  foreach ($legacy in @('stage:PREPARATION','stage:APPLICATIONS','stage:REVIEW','stage:COUNTING','stage:SECOND_ROUND','stage:FINISHED')) {
    if ($manage.Value.Contains($legacy)) { $errors.Add("Legacy stage button remains in management menu: $legacy") }
  }
}

foreach ($marker in @('openRpElectionHub','rp:debates','rp:voting','rp:block:create','rp:finish','rp:select')) {
  Require-Contains $source $marker "Simplified RP marker is missing: $marker"
}
Require-Contains $source 't + 7L * 86_400_000L' 'The seven-day president term must remain exact.'
Require-Contains $admin 'openAdminElectionHub' 'AdminPlus must delegate elections to ElectionCore.'
$adminHubEntry = [regex]::Match($source, '(?s)public void openAdminElectionHub\(Player player\) \{(.*?)\}')
if (-not $adminHubEntry.Success -or $adminHubEntry.Groups[1].Value.Contains('openElectionRoot')) {
  $errors.Add('AdminHub entry still opens the retired election root.')
}
Require-Regex $source 'if \(action\.equals\("open:root"\)\) \{\s*openRpElectionHub\(player\);' 'Legacy root route must redirect to the RP hub.'
Require-Regex $source 'if \(action\.equals\("open:manage"\)\) \{\s*openRpManagementMenu\(player\);' 'Legacy management route must redirect to the RP manager.'
Require-Contains $source 'if (!isRpStation(station)) {' 'Legacy physical stations must not expose the retired gameplay path.'
Require-Contains $frontend 'canSubmitApplication' 'Player application UI must close after debates.'

Throw-IfErrors 'ValidateCopiMineElectionSimplifiedGui'
