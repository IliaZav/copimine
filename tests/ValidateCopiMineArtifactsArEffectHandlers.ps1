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
  "BROKEN_TOTEM",
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
if ($content -notmatch "EntityResurrectEvent") { throw "Missing donation totem resurrect hook." }
if ($content -notmatch "TOTEM_OF_UNDYING") { throw "Missing donation totem runtime guard." }

Write-Host "PASS"
