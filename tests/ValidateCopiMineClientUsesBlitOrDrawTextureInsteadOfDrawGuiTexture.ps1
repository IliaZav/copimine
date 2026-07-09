$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$code = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java')
if ($code -match 'drawGuiTexture\s*\(') {
  throw 'ClientVisualManager should not use drawGuiTexture for fullscreen narcotics overlays.'
}
if ($code -notmatch 'context\.fill\s*\(' -or $code -notmatch 'drawNoiseGrid\s*\(') {
  throw 'ClientVisualManager must use procedural fill/noise rendering for fullscreen fallback effects.'
}
Write-Host 'CopiMineClient procedural overlay render path validation passed.'
