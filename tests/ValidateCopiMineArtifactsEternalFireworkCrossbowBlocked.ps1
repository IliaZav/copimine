$ErrorActionPreference = "Stop"

$javaFile = "D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java"
$content = Get-Content -Raw -Encoding UTF8 $javaFile

if ($content -notmatch "EntityShootBowEvent") {
  throw "Missing bow/crossbow event hook for eternal firework."
}
if ($content -notmatch "onCrossbowArtifactShot") {
  throw "Missing eternal firework crossbow guard handler."
}
if ($content -notmatch "ETERNAL_BOOST") {
  throw "Missing eternal firework effect id."
}
if ($content -notmatch "setCancelled\(true\)") {
  throw "Missing explicit eternal firework crossbow guard flow."
}

Write-Host "PASS"
