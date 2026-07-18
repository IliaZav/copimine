$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$handler = [regex]::Match($source, '(?s)public void onShopBreak\(BlockBreakEvent var1\) \{.*?(?=\r?\n\s*@EventHandler\(\r?\n\s*priority = EventPriority\.HIGHEST,\r?\n\s*ignoreCancelled = true\r?\n\s*\)\r?\n\s*public void onInventoryClick)')

if (-not $handler.Success -or $handler.Value -notmatch 'boolean activated = false;' -or $handler.Value -notmatch 'if \(activated && var4\.cooldownSeconds\(\) > 0\)' -or $handler.Value -match 'Math\.max\(2, var4\.cooldownSeconds\(\)\)') {
    throw 'Tool abilities must only start a configured cooldown after an ability activates; a zero-second cooldown must remain zero.'
}

Write-Host 'Artifact tool cooldown behavior contract OK'
