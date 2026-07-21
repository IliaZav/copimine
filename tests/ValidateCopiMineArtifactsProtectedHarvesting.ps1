$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$harvest = [regex]::Match($source, '(?s)private boolean harvestAndReplantCrop\(Block var1, Player var2, ItemStack var3\) \{.*?(?=\r?\n\s*private void removeOneFromDrops)')
$forester = [regex]::Match($source, '(?s)private void tryForesterChain\(Player var1, Block var2, ItemStack var3\) \{.*?(?=\r?\n\s*private void grantTrenchBonus)')

if (-not $harvest.Success -or $harvest.Value -notmatch 'new BlockBreakEvent\(var1, var2\)' -or $harvest.Value -notmatch 'Bukkit\.getPluginManager\(\)\.callEvent' -or $harvest.Value -notmatch 'isCancelled\(\)') {
    throw 'The farmer sweep must dispatch and respect a normal block-break protection check before changing crops.'
}

if (-not $forester.Success -or $forester.Value -match '\.breakNaturally\(' -or $forester.Value -notmatch 'var1\.breakBlock\(var14\)') {
    throw 'The forester chain must use the player block-break path instead of bypassing protection hooks.'
}

Write-Host 'Artifact protected harvesting contract OK'
