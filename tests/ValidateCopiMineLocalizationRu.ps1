$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$files = @(
  'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java',
  'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java',
  'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java',
  'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java'
)

foreach ($rel in $files) {
  $text = Get-Content -Raw -Encoding UTF8 (Join-Path $root $rel)
  $cyrillicCount = ([regex]::Matches($text, '[\u0400-\u04FF]')).Count
  if ($cyrillicCount -lt 5) { $errors.Add("$rel has too little Russian UI text.") }
}

if ($errors.Count -gt 0) { throw ("Russian localization validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Russian localization validation passed.'
