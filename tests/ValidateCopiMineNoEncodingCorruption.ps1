$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$scanRoots = @(
  'copimine-admin-plugin',
  'copimine-artifacts',
  'copimine-narcotics',
  'minecraft\server\plugins\AuthEffects',
  'db\migrations',
  'tests'
)
$files = foreach ($rel in $scanRoots) {
  $path = Join-Path $root $rel
  if (Test-Path -LiteralPath $path) {
    Get-ChildItem -LiteralPath $path -Recurse -Include '*.java','*.yml','*.yaml','*.md','*.ps1','*.sql' |
      Where-Object { -not $_.PSIsContainer -and $_.Extension -in @('.java','.yml','.yaml','.md','.ps1','.sql') -and $_.FullName -notmatch '\\(build\\classes|target\\classes)\\' }
  }
}

$badRegexes = @(
  [string][char]0xFFFD,
  '\u00C2\u00A7',
  '\u00D0[\u0080-\u00BF]',
  '\u00D1[\u0080-\u00BF]'
)

foreach ($file in $files) {
  $text = Get-Content -Raw -Encoding UTF8 $file.FullName
  foreach ($pattern in $badRegexes) {
    if ($text -match $pattern) { $errors.Add("Encoding corruption marker in $($file.FullName): $pattern") }
  }
}

if ($errors.Count -gt 0) { throw ("Encoding validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Encoding validation passed: no common UTF-8 mojibake markers.'
