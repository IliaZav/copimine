$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$plugin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\plugin.yml')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($permission in @(
  'copimine.narcotics.admin',
  'copimine.narcotics.give',
  'copimine.narcotics.reset',
  'copimine.narcotics.reload',
  'copimine.narcotics.clearoverdose',
  'copimine.narcotics.selfcheck',
  'copimine.narcotics.texture',
  'copimine.narcotics.visuals'
)) {
  if ($plugin -notmatch [regex]::Escape($permission)) { throw "Permission missing from plugin.yml: $permission" }
  if ($main -notmatch [regex]::Escape($permission)) { throw "Permission not enforced in command flow: $permission" }
}
Write-Host 'Strict narcotics command permissions validation passed.'
