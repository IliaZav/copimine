$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\CopiMineClient.java')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
foreach ($needle in @('literal("copimineclient")','literal("visual")','literal("test")','literal("clear")')) {
  if ($client -notmatch [regex]::Escape($needle)) { throw "Client visual test command marker missing: $needle" }
}
foreach ($needle in @('/cmclient visualtest','/cmclient fallbacktest','visualtest','fallbacktest')) {
  if ($bridge -notmatch [regex]::Escape($needle)) { throw "Server visual bridge command marker missing: $needle" }
}
Write-Host 'CopiMine client visual-test-command validation passed.'
