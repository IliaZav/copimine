$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$code = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java')
if ($code -match 'drawGuiTexture\s*\(') {
  throw 'ClientVisualManager should not use drawGuiTexture for fullscreen narcotics overlays.'
}
if ($code -notmatch 'drawTexture\s*\(') {
  throw 'ClientVisualManager must use drawTexture/blit style rendering for fullscreen overlays.'
}
Write-Host 'CopiMineClient overlay render path validation passed.'
