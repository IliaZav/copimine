$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$service = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java')
foreach ($marker in @(
  'mode: VANILLA',
  'root.getString("textures.mode", "VANILLA")',
  'return TextureMode.VANILLA;'
)) {
  if (($config + $service) -notmatch [regex]::Escape($marker)) {
    throw "Texture default VANILLA marker missing: $marker"
  }
}
Write-Host 'Narcotics texture default VANILLA validation passed.'
