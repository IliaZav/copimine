. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$parts = @(
  (Read-Utf8 $Paths.Election)
  (Read-Utf8 $Paths.Admin)
  (Read-Utf8 $Paths.MainPy)
  (Read-Utf8 $Paths.Discord)
  (Read-Utf8 $Paths.FrontendApp)
  (Read-Utf8 (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
  (Read-Utf8 (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'))
)
$combined = $parts -join "`n"

foreach ($needle in @(
  'fixed status',
  'debug menu',
  'test status',
  'new fixed panel',
  'AI-like',
  'will be implemented',
  'old route'
)) {
  Require-NotContains $combined $needle "User-facing debug/placeholder text remains: $needle"
}

Throw-IfErrors 'ValidateCopiMineNoAiDebugText'
