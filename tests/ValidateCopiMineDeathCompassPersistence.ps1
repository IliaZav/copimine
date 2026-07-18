$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$death = [regex]::Match($source, '(?s)public void onPlayerDeath\(PlayerDeathEvent var1\) \{.*?(?=\r?\n\s*@EventHandler)')
$compass = [regex]::Match($source, '(?s)private boolean pointCompassToLastDeath\(Player var1, ItemStack var2\) \{.*?(?=\r?\n\s*private boolean triggerEternalBoost)')

if ($source -notmatch 'keyLastDeathWorld' -or $source -notmatch 'keyLastDeathX' -or $source -notmatch 'keyLastDeathY' -or $source -notmatch 'keyLastDeathZ') {
    throw 'Death compass persistence keys are required.'
}

if (-not $death.Success -or $death.Value -notmatch 'keyLastDeathWorld' -or $death.Value -notmatch 'PersistentDataType\.INTEGER') {
    throw 'A player death must persist the world and block coordinates for the compass.'
}

if (-not $compass.Success -or $compass.Value -notmatch 'persistedLastDeathLocation\(var1\)') {
    throw 'The death compass must recover the saved location after a restart.'
}

Write-Host 'Death compass persistence contract OK'
