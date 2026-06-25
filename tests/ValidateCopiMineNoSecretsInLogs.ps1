$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$scanRoots = @(
  'copimine-admin-plugin',
  'copimine-artifacts',
  'copimine-narcotics',
  'minecraft\server\plugins\AuthEffects',
  'tests'
)
$sources = foreach ($rel in $scanRoots) {
  $path = Join-Path $root $rel
  if (Test-Path -LiteralPath $path) {
    Get-ChildItem -LiteralPath $path -Recurse -Include '*.java','*.ps1','*.yml','*.yaml' |
      Where-Object { -not $_.PSIsContainer -and $_.Extension -in @('.java','.ps1','.yml','.yaml') -and $_.FullName -notmatch '\\(build\\classes|target\\classes)\\' }
  }
}

foreach ($file in $sources) {
  $text = Get-Content -Raw -Encoding UTF8 $file.FullName
  if ($text -match 'getLogger\(\)\.(info|warning|severe)\([^;\r\n]*(\bpassword\b|\bPIN\b|\bpin\b|\.env|\bsecret\b|\btoken\b)[^;\r\n]*\+') {
    $errors.Add("Potential secret logged with concatenated data: $($file.FullName)")
  }
  if ($text -match '(?i)(password|secret|token)\s*=\s*["''][^"'']{8,}["'']') {
    $errors.Add("Potential hardcoded secret: $($file.FullName)")
  }
}

if ($errors.Count -gt 0) { throw ("No secrets in logs validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'No secrets in logs validation passed.'
