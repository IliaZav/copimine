$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$db = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')
if ($db -notmatch [regex]::Escape('DELETE FROM narcotics_brewing_states')) { throw 'Narcotics reset must clear narcotics tables.' }
foreach ($forbidden in @('DELETE FROM elections','DELETE FROM bank_accounts','DELETE FROM candidates','DELETE FROM users','DELETE FROM president_')) {
  if ($db -match [regex]::Escape($forbidden)) { throw "Reset touches non-narcotics data: $forbidden" }
}
Write-Host 'Narcotics DB reset scope validation passed.'
