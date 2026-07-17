param(
  [string]$Pattern = 'Validate*.ps1'
)

$ErrorActionPreference = 'Stop'
$tests = Get-ChildItem -LiteralPath $PSScriptRoot -Filter $Pattern -File |
  Where-Object { $_.Name -ne 'RunCopiMineValidators.ps1' } |
  Sort-Object Name
$failures = [System.Collections.Generic.List[string]]::new()
$passed = 0
$index = 0

foreach ($test in $tests) {
  $index++
  try {
    & $test.FullName *>&1 | Out-Null
    $passed++
  } catch {
    $failures.Add(('{0}: {1}' -f $test.Name, $_.Exception.Message))
  }
  if (($index % 25) -eq 0) {
    Write-Host "PROGRESS $index/$($tests.Count) passed=$passed failed=$($failures.Count)"
  }
}

Write-Host "VALIDATOR_SUMMARY total=$($tests.Count) passed=$passed failed=$($failures.Count)"
if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Host "FAIL $_" }
  exit 1
}
