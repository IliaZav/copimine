$ErrorActionPreference = 'Stop'

$javaPath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$itemsPath = Join-Path $PSScriptRoot '..\copimine-artifacts\items.yml'
$java = Get-Content -Raw -Encoding UTF8 $javaPath
$items = Get-Content -Raw -Encoding UTF8 $itemsPath

foreach ($marker in @(
  'entity == player',
  'setUseItemInHand(Event.Result.DENY)',
  'getRelative(0, -1, 0)',
  'refreshEquippedArtifactBindingsAsync',
  'refreshOfficialBindingAsync',
  'ignoreCancelled = false'
)) {
  if ($java -notmatch [regex]::Escape($marker)) { throw "Missing artifact regression guard: $marker" }
}

$miner = [regex]::Match($items, '(?ms)^  - id: copimine_miner_pickaxe\r?\n.*?(?=^  - id:|^donation-catalog:|\z)')
if (-not $miner.Success -or $miner.Value -notmatch 'cooldown_seconds:\s*1') {
  throw 'The 3x3 miner must not be locked for several minutes after one use.'
}
$zmei = [regex]::Match($items, '(?ms)^  - id: zmei_gorynych\r?\n.*?(?=^  - id:|^donation-catalog:|\z)')
if (-not $zmei.Success -or $zmei.Value -notmatch 'effect_chance_percent:\s*100') {
  throw 'Zmei Gorynych must apply its configured hit effect deterministically.'
}
$shield = [regex]::Match($items, '(?ms)^    - item-id: ne_segodnya_suka_shield\r?\n.*?(?=^    - item-id:|\z)')
if (-not $shield.Success -or $shield.Value -notmatch 'proc-chance:\s*1(?:\.0+)?') {
  throw 'The revenge shield must not silently fail on its attacker effect.'
}

Write-Host 'Artifacts ability regression validation passed.'
