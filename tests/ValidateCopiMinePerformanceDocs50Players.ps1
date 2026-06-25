. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$doc = Read-Utf8 (Join-Path $root 'docs\PERFORMANCE_TEST_50_PLAYERS_RU.md')

Require-Contains $doc '50' 'Performance test doc must target 50 players.'
Require-Contains $doc 'MSPT' 'Performance test doc must explain MSPT expectations.'
Require-Contains $doc '/spark profiler start' 'Performance test doc must include spark profiler start.'
Require-Contains $doc '/spark profiler stop' 'Performance test doc must include spark profiler stop.'
Require-Regex $doc '(?is)view-distance.*8' 'Performance test doc must mention the target view-distance 8.'
Require-Regex $doc '(?is)simulation-distance.*6' 'Performance test doc must mention the safe starting simulation-distance 6.'

Throw-IfErrors 'ValidateCopiMinePerformanceDocs50Players'
