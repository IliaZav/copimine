$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($needle in @(
    'normalizeImageFrameCreateCommand',
    'imageframe create',
    'isImageFrameUrl',
    'isPositiveImageFrameDimension',
    'imageFrameGeneratedName',
    'URL-first forms',
    'Name/URL form',
    'namespaced label',
    'imageFrameToken',
    'isImageFrameDithering',
    'PlayerCommandPreprocessEvent'
)) {
    if (-not $source.Contains($needle)) { $errors.Add("ImageFrame admin command compatibility is missing: $needle") }
}

if ($errors.Count -gt 0) {
    throw ('ValidateCopiMineImageFrameAdminCommand failed:' + "`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineImageFrameAdminCommand passed.'
