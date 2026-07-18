$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

if ($source -notmatch 'Map<String, Long> actionCooldowns' -or $source -notmatch 'private String actionCooldownKey\(Player player, CopiMineArtifacts\.CatalogItem item\)') {
    throw 'Artifact ability cooldowns must be stored separately for each player and item.'
}

if ($source -match 'actionCooldowns\.getOrDefault\([^\r\n]*getUniqueId\(\)' -or $source -match 'actionCooldowns\.put\([^\r\n]*getUniqueId\(\)') {
    throw 'No artifact ability may use a player-wide shared cooldown.'
}

Write-Host 'Artifact cooldown scope contract OK'
