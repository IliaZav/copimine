$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

if ($source -notmatch 'Arrays\.copyOfRange\(var2, 2, var2\.length\)') {
    throw 'The shop command must treat arguments after the id as the visible shop title.'
}

$generatedId = [regex]::Match($source, '(?s)private String nextGeneratedShopId\(\) \{.*?(?=\r?\n\s*private )')
if (-not $generatedId.Success -or $generatedId.Value -notmatch 'shopsByLocation\.values\(\)' -or $generatedId.Value -notmatch 'shopId\(\)') {
    throw 'Generated shop ids must be checked against existing shops before use.'
}

foreach ($method in 'handleShopCommand', 'createAdminShopFromTarget') {
    $block = [regex]::Match($source, "(?s)private (?:boolean|void) $method\(.*?(?=\r?\n\s*private )")
    if (-not $block.Success -or $block.Value -notmatch 'spawnShopTitleDisplay\(') {
        throw "Shop creation path $method must create the visible title independently of the block model."
    }
}

$removal = [regex]::Match($source, '(?s)private void removeShopWithCleanup\(.*?(?=\r?\n\s*private )')
if (-not $removal.Success -or $removal.Value -notmatch 'cleanupShopTitleDisplay\(') {
    throw 'Removing a shop must remove its visible title even when no block model exists.'
}

if ($source -notmatch 'private void repairShopTitleDisplays\(' -or
    $source -notmatch 'this\.repairShopTitleDisplays\(event\.getWorld\(\)\.getName\(\), event\.getChunk\(\)\.getX\(\), event\.getChunk\(\)\.getZ\(\)\)') {
    throw 'Loaded chunks must restore missing shop titles independently of custom block visuals.'
}

Write-Host 'Artifact shop naming and title display contract OK'
