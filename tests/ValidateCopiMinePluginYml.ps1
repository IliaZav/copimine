$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$files = @{
  'copimine-admin-plugin\plugin.yml' = @('name: CopiMineUltimateAdminPlus','commands:','permissions:','copimine.bank.admin','copimine.diagnostics')
  'copimine-election-core\plugin.yml' = @('name: CopiMineElectionCore','commands:','hidelive','copimine.election.admin')
  'copimine-artifacts\plugin.yml' = @('name: CopiMineArtifacts','depend:','CopiMineUltimateAdminPlus','cmartifacts','copimine.artifacts.admin')
  'copimine-narcotics\plugin.yml' = @('name: CopiMineNarcotics','version: ''2.1.0-client-bridge''','cmnarcotics','copimine.narcotics.admin','copimine.narcotics.give','copimine.narcotics.reload','copimine.narcotics.reset','copimine.narcotics.visuals')
  'minecraft\server\plugins\AuthEffects\src\main\resources\plugin.yml' = @('name: AuthEffects','softdepend:','nLogin')
}

foreach ($entry in $files.GetEnumerator()) {
  $path = Join-Path $root $entry.Key
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing plugin.yml: $($entry.Key)"); continue }
  $text = Get-Content -Raw -Encoding UTF8 $path
  foreach ($marker in $entry.Value) {
    if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("$($entry.Key) missing marker: $marker") }
  }
}

if ($errors.Count -gt 0) { throw ("plugin.yml validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'plugin.yml validation passed for admin hub, ElectionCore, artifacts, Phase 1 narcotics and AuthEffects.'
