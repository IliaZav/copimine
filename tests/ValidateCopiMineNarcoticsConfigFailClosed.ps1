$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$reload = [regex]::Match($source, '(?s)public void reload\(\) \{.*?(?=\r?\n\s*public Map<String, NarcoticDefinition> items\(\))')

if (-not $reload.Success) {
    throw 'Could not locate narcotics configuration reload.'
}

foreach ($marker in @('loadedItems', 'loadedVisualEffectIds', 'requiredItemMaterial', 'validateRecipe', 'validateCustomModelData')) {
    if ($reload.Value -notmatch [regex]::Escape($marker)) {
        throw "Narcotics reload must validate a temporary catalog before replacing live state: $marker"
    }
}

if ($reload.Value -match 'items\.clear\(\).*?for \(String itemId' -or $source -match 'return material == null \? fallback : material') {
    throw 'Narcotics configuration must not clear live items or silently substitute an invalid material before validation succeeds.'
}

$effects = [regex]::Match($source, '(?s)private List<ConfiguredEffect> parseEffects\(String itemId, String listName, List<String> raw\) \{.*?(?=\r?\n\s*private int parseInt)')
if (-not $effects.Success -or $effects.Value -notmatch 'Invalid potion effect' -or $effects.Value -notmatch 'durationSeconds <= 0') {
    throw 'Narcotics effects must reject unknown types and non-positive durations during configuration reload.'
}

Write-Host 'Narcotics configuration fail-closed contract OK'
