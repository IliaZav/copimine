$ErrorActionPreference = 'Stop'

$javaPath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$itemsPath = Join-Path $PSScriptRoot '..\copimine-artifacts\items.yml'
$java = Get-Content -Raw -Encoding UTF8 $javaPath
$items = Get-Content -Raw -Encoding UTF8 $itemsPath

if ($java -notmatch 'PlayerShieldDisableEvent') {
  throw 'Custom shields must handle Paper''s shield-disable event.'
}
if ($java -notmatch 'setCancelled\(true\)') {
  throw 'Custom shield disable handling must cancel axe knockback.'
}
if ($java -notmatch 'Enchantment\.UNBREAKING') {
  throw 'Custom shield must carry the Unbreaking enchantment.'
}
if ($java -notmatch 'status IN \(''DELIVERED'',''ACTIVE'',''DELIVERING'',''PENDING_DELIVERY''\)') {
  throw 'Live shield bindings must include in-flight delivery states.'
}
$shield = [regex]::Match($items, '(?ms)^    - item-id: ne_segodnya_suka_shield\r?\n.*?(?=^    - item-id:|\z)')
if (-not $shield.Success -or $shield.Value -notmatch 'effect-profile-id:\s*NOT_TODAY_SHIELD') {
  throw 'Shield catalog entry is missing.'
}

Write-Host 'Shield resilience contract OK'
