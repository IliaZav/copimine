. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$doc = Read-Utf8 (Join-Path $root 'docs\PERFORMANCE_TEST_50_PLAYERS_RU.md')
$manual = Read-Utf8 (Join-Path $root 'tests\manual\COPIMINE_RELEASE_WITH_MODPACK_SMOKE_CHECKLIST.md')
$sparkJar = Get-ChildItem (Join-Path $root 'minecraft\server\plugins') -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^spark(\.jar|[-_].*\.jar)$' }

if (-not $sparkJar -and -not ($doc -match '/spark profiler start' -and $manual -match 'spark')) {
  $errors.Add('Either spark must be installed or the release docs/manual checklist must explicitly cover spark-based profiling.')
}

Throw-IfErrors 'ValidateCopiMineSparkOrPerformanceChecklist'
