$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$java = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\config.yml')
$errors = [System.Collections.Generic.List[string]]::new()
if ($config -notmatch 'custom-block-visuals:' -or $config -notmatch '(?m)^\s+enabled:\s+false\s*$') { $errors.Add('Artifact custom-block visuals must be disabled by default.') }
foreach ($marker in @('deactivateAllProtectedBlockVisualRowsAsync', 'cleanupAllProtectedBlockVisualEntities', 'PROTECTED_BLOCK_VISUAL', 'public void onChunkLoad(ChunkLoadEvent event)')) {
  if ($java -notmatch [regex]::Escape($marker)) { $errors.Add("Missing cleanup marker: $marker") }
}
$chunk = [regex]::Match($java, 'public void onChunkLoad\(ChunkLoadEvent event\)\s*\{(?<body>[\s\S]*?)(?=\n\s*@EventHandler)', 'Singleline').Groups['body'].Value
if ($chunk -notmatch 'customBlockVisualsEnabled\(\)') { $errors.Add('Chunk load must check custom-block-visuals.enabled before any repair.') }
if ($chunk -notmatch 'repairShopTitleDisplays\(event\.getWorld\(\)\.getName\(\), event\.getChunk\(\)\.getX\(\), event\.getChunk\(\)\.getZ\(\)\);') { $errors.Add('Chunk load must restore shop title displays by chunk.') }
if ($errors.Count) { throw ("Artifact visual cleanup validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineArtifactVisualCleanup passed.'
