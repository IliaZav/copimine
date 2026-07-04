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

if ($admin -notmatch '(?:d|normalizedData)\.set\(arKey\("batch_id"\),org\.bukkit\.persistence\.PersistentDataType\.STRING,"official-ar-stack"\);') {
    throw "Missing AR stack normalization batch marker."
}

Write-Host "ValidateCopiMineArStackNormalization passed."
