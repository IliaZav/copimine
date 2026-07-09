$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java")

if ($admin -match 'if\(now\(\)>=0L\)') {
    throw "AR normalization still contains the always-true emergency branch."
}

if ($admin -notmatch 'meta\.setDisplayName\(c\("&b.*AR"\)\);') {
    throw "Missing AR stack normalization display name marker."
}

if ($admin -notmatch 'meta\.setLore\(List\.of\(\)\);') {
    throw "Missing AR stack normalization lore reset."
}

foreach ($marker in @(
    'd.set(arKey("type"),org.bukkit.persistence.PersistentDataType.STRING,"certified");',
    'd.remove(arKey("source"));',
    'd.remove(arKey("owner_uuid"));',
    'd.remove(arKey("owner_name"));',
    'd.remove(arKey("batch_id"));',
    'd.remove(arKey("asset_id"));'
)) {
    if ($admin -notmatch [regex]::Escape($marker)) {
        throw "Missing AR stack-safe PDC normalization marker: $marker"
    }
}

Write-Host "ValidateCopiMineArStackNormalization passed."
