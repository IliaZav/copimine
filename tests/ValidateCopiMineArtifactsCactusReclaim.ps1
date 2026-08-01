$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Require([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { throw $message }
}

# A dropped item touching a cactus is discarded by the server's block-contact
# path. That path is not guaranteed to produce EntityDamageEvent for Item, so
# the plugin must journal it from EntityInsideBlockEvent before vanilla removes
# the entity.
Require 'EntityInsideBlockEvent' 'Cactus recovery must observe the block-contact event for item entities.'
Require 'onDonationItemInsideBlock(EntityInsideBlockEvent event)' 'Cactus recovery handler is missing.'
Require 'Material.CACTUS' 'Cactus recovery must explicitly recognize cactus blocks.'
Require 'recordDonationLossOnce(ref, "cactus")' 'Cactus recovery must use the idempotent loss journal.'
Require 'item.remove();' 'Cactus recovery must remove the physical entity only after journaling.'
Require 'case DEATH, DESPAWN, DROP, ENTER_BLOCK, EXPLODE, HIT, MERGE, OUT_OF_WORLD, PLUGIN, DISCARD' 'Fallback removal handling must include block-entry removal causes.'

Write-Output 'Artifacts cactus reclaim contract: PASS'
