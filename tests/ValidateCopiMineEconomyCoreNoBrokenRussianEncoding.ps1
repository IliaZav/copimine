$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java')
$replacement = [string][char]0xFFFD
if ($source.Contains($replacement)) {
  throw 'CopiMineEconomyCore.java still contains broken Russian encoding markers.'
}
Write-Host 'ValidateCopiMineEconomyCoreNoBrokenRussianEncoding passed.'
