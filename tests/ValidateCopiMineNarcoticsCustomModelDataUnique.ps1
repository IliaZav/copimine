$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\models_manifest.json')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$expected = @('810001','810002','810003','810004','810005','810006','810007','810008','810009')
foreach ($cmd in $expected) {
  if ($manifest -notmatch [regex]::Escape("""custom_model_data"": $cmd")) { throw "Manifest missing narcotics CMD: $cmd" }
  if ($config -notmatch [regex]::Escape("custom_model_data: $cmd")) { throw "Config missing narcotics CMD: $cmd" }
}
Write-Host 'Narcotics custom model data uniqueness validation passed.'
