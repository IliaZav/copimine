. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$text = Read-Utf8 $Paths.Election
$methods = @(
  'private void openStationsMenu',
  'private void openApplicationsMenu',
  'private void openChairBallotsMenu',
  'private void countCurrentRound',
  'private void resetElections',
  'private void refreshSnapshotAndPush'
)

Write-Host 'ValidateCopiMineElectionMainThreadSqlRiskReport report:'
foreach ($signature in $methods) {
  $body = Method-Body $text $signature
  $risk = if ($body -match 'queryList\(|queryOne\(|scalarLong\(|tx\(') { 'SQL_TOUCHES_METHOD' } else { 'NO_SQL_DETECTED' }
  Write-Host (" - {0}: {1}" -f $signature, $risk)
}
