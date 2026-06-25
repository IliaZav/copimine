$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
foreach ($marker in @('require_client_mod: false','kick_if_missing_client: false','enabled: true')) {
  if ($config -notmatch [regex]::Escape($marker)) { throw "Optional bridge default marker missing: $marker" }
}
Write-Host 'Client bridge optional-by-default validation passed.'