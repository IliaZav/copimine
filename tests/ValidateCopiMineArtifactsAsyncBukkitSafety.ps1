$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Require([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { throw $message }
}

Require 'String targetWorld = "";' 'Shop creation must reserve a world value on the Bukkit thread.'
Require 'if (target != null) {' 'Shop creation must read target coordinates only while the block is present.'
Require 'targetWorld = target.getWorld().getName();' 'Shop creation must capture the target world on the Bukkit thread.'
Require 'targetX = target.getX();' 'Shop creation must capture the target X coordinate before async persistence.'
Require 'targetY = target.getY();' 'Shop creation must capture the target Y coordinate before async persistence.'
Require 'targetZ = target.getZ();' 'Shop creation must capture the target Z coordinate before async persistence.'
Require 'final String finalTargetWorld = targetWorld;' 'The captured shop world must be immutable before async persistence.'
Require 'final int finalTargetX = targetX;' 'The captured shop X coordinate must be immutable before async persistence.'
Require 'final int finalTargetY = targetY;' 'The captured shop Y coordinate must be immutable before async persistence.'
Require 'final int finalTargetZ = targetZ;' 'The captured shop Z coordinate must be immutable before async persistence.'
if ($source -notmatch 'new CopiMineArtifacts\.Shop\(generatedId,[\s\S]{0,80}finalTargetWorld, finalTargetX, finalTargetY, finalTargetZ, true\)') {
  throw 'Async shop persistence must use captured primitive location values.'
}
if ($source.Contains('target.getWorld().getName(), target.getX(), target.getY(), target.getZ(), true')) {
  throw 'Async shop persistence must not call Bukkit Block accessors.'
}

Require 'UUID playerUuid = var1.getPlayer().getUniqueId();' 'Join handling must capture the player UUID before starting database work.'
Require 'int var2 = this.pendingCount(playerUuid.toString());' 'Join-time database work must use the captured UUID.'
Require 'Player player = Bukkit.getPlayer(playerUuid);' 'Join-time notification must reacquire the online player on the Bukkit thread.'

Write-Output 'Artifacts async Bukkit-safety contract: PASS'
