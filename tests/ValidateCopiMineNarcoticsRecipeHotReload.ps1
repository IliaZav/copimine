$root = Split-Path -Parent $PSScriptRoot
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$frontend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\admin\narcotics-recipe-pages.js')

foreach ($marker in @(
  'rcon_quick("cmnarcotics reload")',
  '"recipes_reloaded"',
  '"reload": reload_result',
  'const reload = result.reload || {}',
  'const reloadMessage = reload.reloaded'
)) {
  if (($backend + $frontend) -notmatch [regex]::Escape($marker)) {
    throw "Narcotics recipe hot-reload marker missing: $marker"
  }
}

Write-Host 'Narcotics recipe editor hot reload validation passed.'
