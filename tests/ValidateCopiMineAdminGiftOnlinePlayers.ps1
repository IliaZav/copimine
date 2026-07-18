$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)private void openAdminGiftPlayersAsync\(Player player\) \{.*?(?=\r?\n\s*private void openAdminGiftPlayers\()')
if (-not $method.Success) {
    throw 'Could not locate the admin gift player picker.'
}

$body = $method.Value
$onlineSnapshot = $body.IndexOf('Bukkit.getOnlinePlayers()')
$backgroundLookup = $body.IndexOf('this.runAsync(')
if ($onlineSnapshot -lt 0 -or $backgroundLookup -lt 0 -or $onlineSnapshot -gt $backgroundLookup) {
    throw 'Online players must be captured on the server thread before database lookup starts.'
}

if ($body -notmatch 'Map<String, CopiMineArtifacts\.GiftTarget>.*targetsByUuid' -or
    $body -notmatch 'targetsByUuid\.put\(' -or
    $body -notmatch 'new ArrayList<>\(targetsByUuid\.values\(\)\)') {
    throw 'Online and offline players must be merged by UUID before rendering the picker.'
}

if ($body -notmatch 'targets\.sort\(') {
    throw 'The merged player picker must use a stable readable ordering.'
}

if ($body -match 'LIMIT 36') {
    throw 'The player picker must not silently hide most known players behind a 36-player database limit.'
}

Write-Host 'Admin gift online and offline player picker contract OK'
