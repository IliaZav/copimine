$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = Get-ChildItem -Path (Join-Path $root 'copimine-narcotics\src') -Recurse -Filter *.java | Get-Content -Raw -Encoding UTF8
if ($files -match '(?<!String)\.join\(') { throw 'Blocking future pattern found: .join(' }
foreach ($forbidden in @('.get()', 'future.get(', 'CompletableFuture.allOf(')) {
  if ($files -match [regex]::Escape($forbidden)) { throw "Blocking future pattern found: $forbidden" }
}
Write-Host 'No long DB blocking markers found on the main runtime path.'
