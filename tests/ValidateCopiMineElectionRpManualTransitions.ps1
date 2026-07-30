$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$start = $backend.IndexOf('if action == "select":')
$end = $backend.IndexOf('elif action == "stage":', $start)
if ($start -lt 0 -or $end -lt 0) {
  throw 'Could not isolate the RP candidate-selection control branch.'
}

$selection = $backend.Substring($start, $end - $start)
if ($selection -match "current_stage='DEBATES'" -or $selection -match "status='DEBATES'") {
  throw 'Saving the candidate roster must not automatically open the debates stage.'
}
if ($selection -notmatch 'UPDATE elections SET updated_at=%s WHERE id=%s') {
  throw 'Saving the candidate roster must still persist an update timestamp.'
}
if ($selection -notmatch '"stage": current_stage') {
  throw 'Candidate selection must report the unchanged manual stage.'
}

Write-Output 'Election RP manual transition contract: PASS'
