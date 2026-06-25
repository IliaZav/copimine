$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
if ($config -notmatch [regex]::Escape('material:JUNGLE_LEAVES')) {
  throw 'Kola recipe is missing the jungle leaves ingredient.'
}
Write-Host 'Jungle leaves ingredient validation passed.'
