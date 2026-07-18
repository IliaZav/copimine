$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..'
$database = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java') -Raw -Encoding UTF8
$plugin = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java') -Raw -Encoding UTF8
$cauldron = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java') -Raw -Encoding UTF8

if ($database -notmatch 'public boolean hasAsyncCapacity\(\)' -or $database -notmatch 'future\.completeExceptionally\(error\)' -or $database -match 'CompletableFuture\.runAsync\(') {
    throw 'Narcotics database work must report queue rejection as a failed future and expose bounded-capacity backpressure.'
}

if ($plugin -notmatch 'database\.hasAsyncCapacity\(\)' -or $cauldron -notmatch 'database\.hasAsyncCapacity\(\)') {
    throw 'Finished-item consumption and cauldron ingredient use must be refused before inventory changes when the database queue is saturated.'
}

Write-Host 'Narcotics database backpressure contract OK'
