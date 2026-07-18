$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$serverConfig = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'minecraft\server\plugins\CopiMineNarcotics\config.yml')
$service = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java')
foreach ($marker in @(
  'root.getString("textures.mode", "VANILLA")',
  'return TextureMode.VANILLA;'
)) {
  if ($service -notmatch [regex]::Escape($marker)) {
    throw "Texture default VANILLA marker missing: $marker"
  }
}
foreach ($releaseConfig in @($config, $serverConfig)) {
  if ($releaseConfig -notmatch [regex]::Escape('mode: CUSTOM')) {
    throw 'The release narcotics config must enable models bundled in the resource pack.'
  }
}
Write-Host 'Narcotics texture default VANILLA validation passed.'
