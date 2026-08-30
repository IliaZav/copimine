$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
# The cooldown assertion only needs the handler boundary, not a particular
# inventory-transfer cancellation policy. Donation stacks use vanilla
# transfer/drop/pickup behavior and are not quarantined by this plugin.
$handler = [regex]::Match($source, '(?s)public void onShopBreak\(BlockBreakEvent var1\) \{.*?(?=\r?\n\s*@EventHandler\(\r?\n\s*priority = EventPriority\.HIGHEST,\r?\n\s*ignoreCancelled = (?:true|false)\r?\n\s*\)\r?\n\s*public void onInventoryClick)')

if (-not $handler.Success -or $handler.Value -notmatch 'boolean activated = false;' -or $handler.Value -notmatch 'if \(activated && var4\.cooldownSeconds\(\) > 0\)' -or $handler.Value -match 'Math\.max\(2, var4\.cooldownSeconds\(\)\)') {
    throw 'Tool abilities must only start a configured cooldown after an ability activates; a zero-second cooldown must remain zero.'
}

Write-Host 'Artifact tool cooldown behavior contract OK'
