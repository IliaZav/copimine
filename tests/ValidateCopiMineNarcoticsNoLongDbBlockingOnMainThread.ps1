$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = @(Get-ChildItem -Path (Join-Path $root 'copimine-narcotics\src') -Recurse -Filter *.java)
$blockingPatterns = @(
  '(?s)\b(?:CompletableFuture|Future)\s*<[^;{}]+>\s+\w+\s*;?[^;{}]*?\.\s*(?:get|join)\s*\(',
  '\.toCompletableFuture\(\)\s*\.\s*(?:get|join)\s*\(',
  '\b(?:future|databaseFuture|dbFuture|completionStage)\s*\.\s*(?:get|join)\s*\(',
  'CompletableFuture\.allOf\([^;]*\)\s*\.\s*(?:get|join)\s*\('
)
foreach ($file in $files) {
  $source = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
  foreach ($pattern in $blockingPatterns) {
    if ($source -match $pattern) { throw "Blocking database future pattern found in $($file.Name): $pattern" }
  }
}
Write-Host 'No long DB blocking markers found on the main runtime path.'
