$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$factory = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java')
foreach ($marker in @(
  'texture mode <vanilla|custom>',
  'configService.setTextureMode(mode)',
  'TextureMode.CUSTOM',
  'meta.setCustomModelData(definition.customModelData())'
)) {
  if (($main + $factory) -notmatch [regex]::Escape($marker)) {
    throw "Texture custom-mode marker missing: $marker"
  }
}
Write-Host 'Narcotics texture custom mode validation passed.'
