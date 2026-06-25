$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('enabled: false','mode: FALLBACK','allow_overlay_mode: true','allow_shader_mode: true','if (!configService.visualsEnabled())','return NarcoticsConfigService.VisualMode.FALLBACK')) {
  if (($config + $runtime) -notmatch [regex]::Escape($marker)) { throw "Visual default-safety marker missing: $marker" }
}
Write-Host 'Visuals disabled-by-default validation passed.'
