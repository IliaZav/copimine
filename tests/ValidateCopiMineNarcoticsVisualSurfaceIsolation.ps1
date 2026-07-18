$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$apply = [regex]::Match($source, '(?s)private void applyServerOverlay\(Player player, String effectId, int durationSeconds, boolean overdose\) \{.*?(?=\r?\n\s*private void applyOverlay)')
$clear = [regex]::Match($source, '(?s)private void clearServerVisualSurface\(Player player\) \{.*?(?=\r?\n\s*private boolean hasAllOverlayAssets)')

if (-not $apply.Success -or $apply.Value -match 'clearTitle|sendActionBar' -or -not $clear.Success -or $clear.Value -match 'clearTitle|sendActionBar') {
    throw 'Narcotics visuals must not erase titles or action bars owned by other plugins.'
}

Write-Host 'Narcotics visual surface isolation contract OK'
