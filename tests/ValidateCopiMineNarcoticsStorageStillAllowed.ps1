$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
foreach ($marker in @('allow_player_inventory: true','allow_chest: true','allow_barrel: true','allow_shulker_box: true','allow_ender_chest: true')) {
  if ($config -notmatch [regex]::Escape($marker)) { throw "Allowed storage marker missing: $marker" }
}
Write-Host 'Allowed storage validation passed.'
