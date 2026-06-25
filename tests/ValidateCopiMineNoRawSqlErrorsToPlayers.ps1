$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$sources = Get-ChildItem -LiteralPath $root -Recurse -Include '*.java' |
  Where-Object { -not $_.PSIsContainer -and $_.FullName -match '\\(copimine-admin-plugin|copimine-artifacts|copimine-narcotics|AuthEffects)\\' }

foreach ($file in $sources) {
  $text = Get-Content -Raw -Encoding UTF8 $file.FullName
  foreach ($bad in @(
    'charge.message()',
    'repair.message()',
    'sendMessage("SQLException',
    'sendMessage("PSQLException',
    'openError(player, item, "&c'
  )) {
    if ($bad -eq 'openError(player, item, "&c') { continue }
    if ($text.Contains($bad)) { $errors.Add("Raw internal error marker can reach player in $($file.FullName): $bad") }
  }
}

if ($errors.Count -gt 0) { throw ("No raw SQL errors to players validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'No raw SQL errors to players validation passed.'
