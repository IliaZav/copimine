$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
foreach ($marker in @('ASH','HAPPY_VILLAGER','TRIAL_SPAWNER_DETECTION_OMINOUS','ITEM_SLIME','PORTAL','END_ROD','SPLASH','SQUID_INK','WITCH')) {
  if ($manifest -notmatch [regex]::Escape($marker)) { throw "Fallback mapping missing: $marker" }
}
Write-Host 'Narcotics fallback mapping validation passed.'