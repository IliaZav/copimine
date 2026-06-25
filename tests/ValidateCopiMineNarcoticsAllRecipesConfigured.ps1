$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
foreach ($marker in @('feta:','kola:','girion:','sbp:','sos:','drun:','chups:','borshevik:','zhuzevo:','potion:WEAKNESS','potion:SPEED')) {
  if ($config -notmatch [regex]::Escape($marker)) { throw "Recipe config marker missing: $marker" }
}
Write-Host 'All recipes configured validation passed.'
