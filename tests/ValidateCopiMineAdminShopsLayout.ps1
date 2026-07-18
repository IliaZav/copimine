$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$match = [regex]::Match($source, '(?s)private void openAdminShops\(Player var1\) \{.*?(?=\r?\n   public void openAdminShopHub)')
if (-not $match.Success) {
    throw 'Could not locate the artifact shop administration menu.'
}

$menu = $match.Value

foreach ($slot in 1, 4, 7) {
    if ($menu -notmatch "setAction\(var3, var2, $slot,") {
        throw "Admin shop command is missing from reserved top-row slot $slot."
    }
}

foreach ($slot in 10, 13, 16) {
    if ($menu -match "setAction\(var3, var2, $slot,") {
        throw "Shop cards must not overwrite the action registered in slot $slot."
    }
}

if ($menu -notmatch 'int var4 = 9;') {
    throw 'Shop cards must begin below the top-row commands.'
}

if ($menu -match 'var3\.setItem\(\s*var4,') {
    throw 'Every visible shop card must be registered with setAction, not setItem.'
}

if ($menu -notmatch '(?s)setAction\(\s*var3,\s*var2,\s*var4,.*?"shop:detail:" \+ var6\.shopId\(\)') {
    throw 'Each visible shop card must open its own shop detail screen.'
}

Write-Host 'Artifact shop administration layout contract OK'
