$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..'
$recipes = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\recipe\NarcoticsRecipeService.java') -Raw -Encoding UTF8
$entry = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\recipe\IngredientEntry.java') -Raw -Encoding UTF8

$cauldronEntry = [regex]::Match($recipes, '(?s)public IngredientEntry cauldronIngredientEntry\(ItemStack stack\) \{.*?(?=\r?\n\s*public NarcoticDefinition matchExact)')
if (-not $cauldronEntry.Success -or $cauldronEntry.Value -notmatch '!isRoundTripSafeIngredient\(stack\)' -or $recipes -notmatch 'private boolean isRoundTripSafeIngredient\(ItemStack stack\)' -or $recipes -notmatch 'BlockStateMeta') {
    throw 'Cauldrons must reject custom, container, and otherwise non-round-trippable ingredients before consuming them.'
}

$deserialize = [regex]::Match($entry, '(?s)public static IngredientEntry deserialize\(String raw\) \{.*?(?=\r?\n\s*public static IngredientEntry fromLegacyKey)')
if (-not $deserialize.Success -or $deserialize.Value -notmatch 'catch \(IllegalArgumentException') {
    throw 'Malformed persisted ingredient payloads must be rejected per-entry instead of aborting all brewing-state recovery.'
}

Write-Host 'Narcotics ingredient safety contract OK'
