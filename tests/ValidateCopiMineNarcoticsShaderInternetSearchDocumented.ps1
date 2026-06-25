$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$doc = Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md'
if (-not (Test-Path $doc)) { throw 'Missing THIRD_PARTY_NARCOTICS_VISUALS.md' }
$text = Get-Content -Raw -Encoding UTF8 $doc
foreach ($marker in @('OpenGameArt','Minecraft Wiki','No hotlinking','No runtime external downloads','self-made assets','font glyph overlays')) {
  if ($text -notmatch [regex]::Escape($marker)) { throw "Asset search documentation marker missing: $marker" }
}
Write-Host 'Shader internet search documentation validation passed.'
