$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root "copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java")
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java")

foreach ($needle in @(
    'case "clear", "clearoverdose" -> handleClearPlayer(sender, args);',
    'private boolean handleClearPlayer(CommandSender sender, String[] args)',
    'clientBridge.visuals().clearVisuals(target, "admin-clear")',
    'overdoseService.clearPlayer(target);'
)) {
    if ($narcotics -notmatch [regex]::Escape($needle)) {
        throw "Missing narcotics clear marker: $needle"
    }
}

if ($admin -notmatch 'cmnarcotics clear ') {
    throw "Admin panel cleanse action still does not call the full narcotics clear command."
}

Write-Host "ValidateCopiMineNarcoticsClearCommand passed."
