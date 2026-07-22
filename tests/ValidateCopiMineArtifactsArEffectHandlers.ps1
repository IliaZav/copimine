$ErrorActionPreference = "Stop"

$javaFile = "D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java"
$content = Get-Content -Raw $javaFile

$requiredEffectIds = @(
  "HASTE_BURST_LONG",
  "FORESTER_CHAIN",
  "TRENCH_BONUS",
  "FARMER_SWEEP",
  "DUTY_ARGUMENT",
  "ETERNAL_BOOST",
  "BATIN_REMEN",
  "NAKOPAL_PICKAXE",
  "NALOGOVAYA_KOSA",
  "PRORAB_HELMET",
  "TANK_VEST",
  "NOT_TODAY_SHIELD",
  "DEBUFF_AMULET",
  "TAX_CLOCK",
  "LOOT_COMPASS"
)

foreach ($id in $requiredEffectIds) {
  if ($content -notmatch [regex]::Escape($id)) {
    throw "Missing artifact runtime effect id: $id"
  }
}

if ($content -notmatch "tryFarmerSweep") { throw "Missing farmer sweep helper." }
if ($content -notmatch "tryForesterChain") { throw "Missing forester chain helper." }
if ($content -notmatch "grantTrenchBonus") { throw "Missing trench bonus helper." }
if ($content -notmatch "pointCompassToLastDeath") { throw "Missing loot compass helper." }
if ($content -notmatch "EntityResurrectEvent") { throw "Missing eternal totem resurrect hook." }
if ($content -notmatch "INFINITE_TOTEM") { throw "Missing eternal totem runtime guard." }

$items = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot '..\copimine-artifacts\items.yml')
$smena = [regex]::Match($items, '(?ms)^  - id: smena_bez_perekura_pickaxe\r?\n.*?(?=^  - id:|\z)')
$miner = [regex]::Match($items, '(?ms)^  - id: copimine_miner_pickaxe\r?\n.*?(?=^  - id:|\z)')
if (-not $smena.Success -or $smena.Value -notmatch 'effect:\s*HASTE_BURST_LONG' -or -not $miner.Success -or $miner.Value -notmatch 'effect:\s*MINER_3X3') {
  throw "Each mining pickaxe must match its stated ability: shiftless haste for Smena and 3x3 mining for CopiMine Miner."
}

$taxClock = [regex]::Match($items, '(?ms)^    - item-id: vremya_platit_nalogi_clock\r?\n.*?(?=^    - item-id:|\z)')
if (-not $taxClock.Success -or $taxClock.Value -notmatch 'effect-profile-id:\s*TAX_CLOCK' -or $taxClock.Value -notmatch 'proc-chance:\s*1(?:\.0+)?') {
  throw "Tax clock activation is deterministic and its catalog must not advertise a random proc chance."
}

Write-Host "PASS"
