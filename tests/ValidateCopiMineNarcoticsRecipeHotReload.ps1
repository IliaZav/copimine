$root = Split-Path -Parent $PSScriptRoot
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$frontend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\admin\narcotics-recipe-pages.js')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
$legacy = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js')

foreach ($marker in @(
  '"recipes_reloaded"',
  '"reload": reload_result',
  'const reload = result.reload || {}',
  'const reloadMessage = mode === "save"'
)) {
  if (($backend + $frontend + $runtime + $legacy) -notmatch [regex]::Escape($marker)) {
    throw "Narcotics recipe hot-reload marker missing: $marker"
  }
}

foreach ($wrapper in @($runtime, $legacy)) {
  if ($wrapper -notmatch 'adminRecipeSave:\s*\(\.\.\.args\)\s*=>\s*getAdminNarcoticsRecipePages\(\)\.adminRecipeSave\(\.\.\.args\)') {
    throw 'Recipe apply mode must be forwarded from data-click to the recipe page handler.'
  }
}

if ($backend -notmatch 'reloadCommand": f"systemctl restart \{MINECRAFT_SERVICE\}"' -or
    $backend -match 'rcon_quick\("cmnarcotics reload"\)' -or
    $backend -match 'applyMode": "plugin-reload"') {
  throw 'Applying recipes must restart the Minecraft service instead of only reloading through RCON.'
}

Write-Host 'Narcotics recipe editor hot reload validation passed.'
